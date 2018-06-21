package com.taobao.yugong.applier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MigrateMap;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.db.meta.TableMetaGenerator;
import com.taobao.yugong.common.db.sql.SqlTemplate;
import com.taobao.yugong.common.db.sql.SqlTemplates;
import com.taobao.yugong.common.db.sql.TypeMapping;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.record.IncrementRecord;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.utils.YuGongUtils;
import com.taobao.yugong.exception.YuGongException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 增量数据同步
 *
 * <pre>
 * 1. 不支持主键变更
 * 2. 行记录同步，简单处理
 * </pre>
 *
 * @author agapple 2013-9-17 下午2:15:58
 * @since 5.1.0
 */
public class IncrementRecordApplier extends AbstractRecordApplier {

  protected static final Logger logger = LoggerFactory.getLogger(IncrementRecordApplier.class);
  protected Map<List<String>, TableSqlUnit> insertSqlCache;
  protected Map<List<String>, TableSqlUnit> updateSqlCache;
  protected Map<List<String>, TableSqlUnit> deleteSqlCache;
  protected boolean useMerge = true;
  protected YuGongContext context;
  protected DbType sourceDbType;
  protected DbType targetDbType;
  // TODO use configuration
  private ImmutableList<String> noAutoIncrementTables = ImmutableList.of("User_FinanceAuth",
      "user_ext", "user_level", "Hujiangid_WXunionid", "uc_QQ", "uc_Sina", "uc_Profile");

  public IncrementRecordApplier(YuGongContext context) {
    this.context = context;
  }

  public void start() {
    super.start();
    sourceDbType = YuGongUtils.judgeDbType(context.getSourceDs());
    targetDbType = YuGongUtils.judgeDbType(context.getTargetDs());
    insertSqlCache = MigrateMap.makeMap();
    updateSqlCache = MigrateMap.makeMap();
    deleteSqlCache = MigrateMap.makeMap();
  }

  public void stop() {
    super.stop();
  }

  public void apply(List<Record> records) throws YuGongException {
    // no one,just return
    if (YuGongUtils.isEmpty(records)) {
      return;
    }

    doApply(records);
  }

  protected void doApply(List records) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getTargetDs());
    // 增量处理，为保证顺序，只能串行处理
    applyOneByOne(records, jdbcTemplate);
  }

  /**
   * 一条条记录串行处理
   */
  protected void applyOneByOne(List<IncrementRecord> incRecords, JdbcTemplate jdbcTemplate) {
    for (final IncrementRecord incRecord : incRecords) {
      TableSqlUnit sqlUnit = getSqlUnit(incRecord);
      String applierSql = sqlUnit.applierSql;
      final Map<String, Integer> indexs = sqlUnit.applierIndexs;
      jdbcTemplate.execute(applierSql, (PreparedStatementCallback) ps -> {

        int count = 0;
        // 字段
        List<ColumnValue> cvs = incRecord.getColumns();
        for (ColumnValue cv : cvs) {
          Integer index = getIndex(indexs, cv, true); // 考虑delete的目标库主键，可能在源库的column中
          if (index != null) {
            int type = TypeMapping.map(sourceDbType, targetDbType, cv.getColumn().getType());
            ps.setObject(index, cv.getValue(), type);
            count++;
          }
        }

        // 添加主键
        List<ColumnValue> pks = incRecord.getPrimaryKeys();
        for (ColumnValue pk : pks) {
          Integer index = getIndex(indexs, pk, true);// 考虑delete的目标库主键，可能在源库的column中
          if (index != null) {
            int type = TypeMapping.map(sourceDbType, targetDbType, pk.getColumn().getType());
            ps.setObject(index, pk.getValue(), type);
            count++;
          }
        }

        if (!incRecord.isSkipCheckColumnsCount()) {
          if (count != indexs.size()) {
            processMissColumn(incRecord, indexs);
          }
        }

        try {
          ps.execute();
        } catch (SQLException e) {
          if (context.isSkipApplierException()) {
            logger.error("skiped Record Data : " + incRecord.toString(), e);
          } else {
            throw new SQLException("failed Record Data : " + incRecord.toString(), e);
          }
        }

        return null;
      });
    }
  }

  protected TableSqlUnit getSqlUnit(IncrementRecord incRecord) {
    switch (incRecord.getOpType()) {
      case I:
        return getInsertSqlUnit(incRecord);
      case U:
        return getUpdateSqlUnit(incRecord);
      case D:
        return getDeleteSqlUnit(incRecord);
      default:
        break;
    }

    throw new YuGongException("unknow opType " + incRecord.getOpType());
  }

  protected TableSqlUnit getInsertSqlUnit(IncrementRecord record) {
    List<String> names = Arrays.asList(record.getSchemaName(), record.getTableName());
    TableSqlUnit sqlUnit = insertSqlCache.get(names);
    if (sqlUnit == null) {
      synchronized (this) {
        sqlUnit = insertSqlCache.get(names);
        if (sqlUnit == null) { // double-check
          sqlUnit = new TableSqlUnit();
          String applierSql = null;
          Table meta = tableMetaGeneratorGetTableMeta(names.get(0), names.get(1));

          String[] primaryKeys = getPrimaryNames(record);
          String[] columns = getColumnNames(record);
          if (useMerge && YuGongUtils.isNotEmpty(meta.getColumns())) {
            // merge sql必须不是全主键
            if (targetDbType == DbType.MYSQL) {
              applierSql = SqlTemplates.MYSQL.getMergeSql(meta.getSchema(),
                  meta.getName(),
                  primaryKeys,
                  columns,
                  true);
            } else if (targetDbType == DbType.DRDS) {
              applierSql = SqlTemplates.MYSQL.getMergeSql(meta.getSchema(),
                  meta.getName(),
                  primaryKeys,
                  columns,
                  false);
            } else if (targetDbType == DbType.ORACLE) {
              applierSql = SqlTemplates.ORACLE.getMergeSql(meta.getSchema(),
                  meta.getName(),
                  primaryKeys,
                  columns);
            } else if (targetDbType == DbType.SQL_SERVER) {
              boolean identityInsertMode = true;
              if (noAutoIncrementTables.contains(meta.getName())) {
                identityInsertMode = false;
              }
              applierSql = SqlTemplates.SQL_SERVER.getMergeSql(meta.getSchema(),
                  meta.getName(),
                  primaryKeys,
                  columns,
                  identityInsertMode);
            }
          } else {
            if (YuGongUtils.isEmpty(meta.getColumns()) && targetDbType == DbType.MYSQL) {
              // 如果mysql，全主键时使用insert ignore
              applierSql = SqlTemplates.MYSQL.getInsertIgnoreSql(meta.getSchema(),
                  meta.getName(),
                  primaryKeys,
                  columns);
            } else {
              applierSql = SqlTemplates.COMMON.getInsertSql(meta.getSchema(),
                  meta.getName(),
                  primaryKeys,
                  columns);
            }
          }

          int index = 1;
          Map<String, Integer> indexs = new HashMap<String, Integer>();
          for (String column : columns) {
            indexs.put(column, index);
            index++;
          }

          for (String column : primaryKeys) {
            indexs.put(column, index);
            index++;
          }
          // 检查下是否少了列
          checkIndexColumns(meta, indexs);

          sqlUnit.applierSql = applierSql;
          sqlUnit.applierIndexs = indexs;
          insertSqlCache.put(names, sqlUnit);
        }
      }
    }

    return sqlUnit;
  }

  protected TableSqlUnit getUpdateSqlUnit(IncrementRecord record) {
    List<String> names = Arrays.asList(record.getSchemaName(), record.getTableName());
    TableSqlUnit sqlUnit = updateSqlCache.get(names);
    if (sqlUnit == null) {
      synchronized (this) {
        sqlUnit = updateSqlCache.get(names);
        if (sqlUnit == null) { // double-check
          sqlUnit = new TableSqlUnit();
          String applierSql = null;
          Table meta = tableMetaGeneratorGetTableMeta(names.get(0), names.get(1));

          String[] primaryKeys = getPrimaryNames(record);
          String[] columns = getColumnNames(record);
          if (useMerge && YuGongUtils.isNotEmpty(meta.getColumns())) {
            // merge sql必须不是全主键
            if (targetDbType == DbType.MYSQL) {
              applierSql = SqlTemplates.MYSQL.getMergeSql(meta.getSchema(),
                  meta.getName(),
                  primaryKeys,
                  columns,
                  true);
            } else if (targetDbType == DbType.DRDS) {
              applierSql = SqlTemplates.MYSQL.getMergeSql(meta.getSchema(),
                  meta.getName(),
                  primaryKeys,
                  columns,
                  false);
            } else if (targetDbType == DbType.SQL_SERVER) {
              boolean identityInsertMode = true;
              if (noAutoIncrementTables.contains(meta.getName())) {
                identityInsertMode = false;
              }
              applierSql = SqlTemplates.SQL_SERVER.getMergeSql(meta.getSchema(),
                  meta.getName(),
                  primaryKeys,
                  columns,
                  identityInsertMode);
            } else if (targetDbType == DbType.ORACLE) {
              applierSql = SqlTemplates.ORACLE.getMergeSql(meta.getSchema(),
                  meta.getName(),
                  primaryKeys,
                  columns);
            }
          } else {
            applierSql = SqlTemplates.COMMON.getUpdateSql(meta.getSchema(),
                meta.getName(),
                primaryKeys,
                columns);
          }

          int index = 1;
          Map<String, Integer> indexs = new HashMap<String, Integer>();
          for (String column : columns) {
            indexs.put(column, index);
            index++;
          }

          for (String column : primaryKeys) {
            indexs.put(column, index);
            index++;
          }
          // 检查下是否少了列
          checkIndexColumns(meta, indexs);

          sqlUnit.applierSql = applierSql;
          sqlUnit.applierIndexs = indexs;
          updateSqlCache.put(names, sqlUnit);
        }
      }
    }

    return sqlUnit;
  }

  protected TableSqlUnit getDeleteSqlUnit(IncrementRecord record) {
    List<String> names = Arrays.asList(record.getSchemaName(), record.getTableName());
    TableSqlUnit sqlUnit = deleteSqlCache.get(names);
    if (sqlUnit == null) {
      synchronized (this) {
        sqlUnit = deleteSqlCache.get(names);
        if (sqlUnit == null) { // double-check
          sqlUnit = new TableSqlUnit();
          String applierSql = null;
          Table meta = tableMetaGeneratorGetTableMeta(names.get(0), names.get(1));

          String[] primaryKeys = getPrimaryNames(record);
          if (targetDbType == DbType.SQL_SERVER) {
            applierSql = SqlTemplates.SQL_SERVER.getDeleteSql(meta.getSchema(), meta.getName(),
                primaryKeys);
          } else {
            applierSql = SqlTemplates.COMMON.getDeleteSql(meta.getSchema(), meta.getName(),
                primaryKeys);
          }

          int index = 1;
          Map<String, Integer> indexs = new HashMap<String, Integer>();
          for (String column : primaryKeys) {
            indexs.put(column, index);
            index++;
          }
          // 检查下是否少了列
          checkIndexColumns(meta, indexs);

          sqlUnit.applierSql = applierSql;
          sqlUnit.applierIndexs = indexs;
          deleteSqlCache.put(names, sqlUnit);
        }
      }
    }

    return sqlUnit;
  }

  Table tableMetaGeneratorGetTableMeta(String schema, String table) {
    return TableMetaGenerator.getTableMeta(targetDbType, context.getTargetDs(),
        context.isIgnoreSchema() ? null : schema, table);
  }

  protected void processMissColumn(final IncrementRecord incRecord, final Map<String, Integer> indexs) {
    // 如果数量不同，则认为缺少主键
    List<String> allNames = new ArrayList<String>(indexs.keySet());
    for (ColumnValue cv : incRecord.getColumns()) {
      Integer index = getIndex(indexs, cv, true);
      if (index != null) {
        allNames.remove(cv.getColumn().getName());
      }
    }

    for (ColumnValue pk : incRecord.getPrimaryKeys()) {
      Integer index = getIndex(indexs, pk, true);
      if (index != null) {
        allNames.remove(pk.getColumn().getName());
      }
    }

    throw new YuGongException("miss columns" + allNames + " and failed Record Data : " + incRecord.toString());
  }
}
