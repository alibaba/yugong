package com.taobao.yugong.applier;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.MigrateMap;
import com.taobao.yugong.common.db.RecordDiffer;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.db.meta.TableMetaGenerator;
import com.taobao.yugong.common.db.sql.SqlTemplates;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.utils.YuGongUtils;
import com.taobao.yugong.exception.YuGongException;

/**
 * 增加数据对比
 *
 * @author agapple 2013-9-29 下午1:14:03
 */
public class CheckRecordApplier extends AbstractRecordApplier {

  protected static final Logger logger = LoggerFactory.getLogger(CheckRecordApplier.class);
  protected Map<List<String>, TableSqlUnit> selectSqlCache;
  protected Map<List<String>, Table> tableCache;
  protected YuGongContext context;
  protected DbType dbType;

  public CheckRecordApplier(YuGongContext context) {
    this.context = context;
  }

  public void start() {
    super.start();

    dbType = YuGongUtils.judgeDbType(context.getTargetDs());
    tableCache = MigrateMap.makeComputingMap(names -> {
      if (names.size() != 2) {
        throw new YuGongException("names[" + names.toString() + "] is not valid");
      }

      return TableMetaGenerator.getTableMeta(dbType, context.getTargetDs(),
          context.isIgnoreSchema() ? null : names.get(0),
          names.get(1));
    });

    selectSqlCache = MigrateMap.makeMap();
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

  /**
   * 根据 record 的信息查询 Target DB 的 Record，然后进行 diff
   * 无法 Diff Target DB 里面多余的 Record
   */
  protected List<String> doApply(List<Record> records) {
    Map<List<String>, List<Record>> buckets = MigrateMap.makeComputingMap(names -> Lists.newArrayList());
    List<String> diffResults = Lists.newArrayList();

    // 根据目标库的不同，划分为多个bucket
    for (Record record : records) {
      buckets.get(Arrays.asList(record.getSchemaName(), record.getTableName())).add(record);
    }

    JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getTargetDs());
    for (final List<Record> batchRecords : buckets.values()) {
      List<Record> queryRecords;
      if (context.isBatchApply()) {
        queryRecords = queryByBatch(jdbcTemplate, batchRecords);
      } else {
        queryRecords = queryOneByOne(jdbcTemplate, batchRecords);
      }

      diffResults.addAll(diff(batchRecords, queryRecords));
    }
    return diffResults;
  }

  protected List<Record> queryByBatch(JdbcTemplate jdbcTemplate, final List<Record> batchRecords) {
    if (batchRecords.size() == 0) {
      return Lists.newArrayList();
    }

    Record sampleRecord = batchRecords.get(0);
    Table table = tableCache.get(ImmutableList.of(
        sampleRecord.getSchemaName(), sampleRecord.getTableName()));
    TableSqlUnit sqlUnit = getSqlUnit(sampleRecord);
    final String schemaName = table.getSchema();
    final String tableName = table.getName();
    final Map<String, Integer> indexs = sqlUnit.applierIndexs; // FIXME use applier data
    final List<ColumnMeta> primaryKeys = table.getPrimaryKeys();
    final List<ColumnMeta> columns = table.getColumns();

    String selectSql = null;
    if (dbType == DbType.MYSQL) {
      selectSql = SqlTemplates.MYSQL.getSelectInSql(table.getSchema(),
          table.getName(),
          YuGongUtils.getColumnNameArray(primaryKeys),
          YuGongUtils.getColumnNameArray(columns),
          batchRecords.size());
    } else if (dbType == DbType.ORACLE) {
      selectSql = SqlTemplates.ORACLE.getSelectInSql(table.getSchema(),
          table.getName(),
          YuGongUtils.getColumnNameArray(primaryKeys),
          YuGongUtils.getColumnNameArray(columns),
          batchRecords.size());
    } else {
      throw new YuGongException("unsupport " + dbType);
    }

    Object results = jdbcTemplate.execute(selectSql, (PreparedStatementCallback) ps -> {
      // 批量查询，根据pks in 语法
      int size = table.getPrimaryKeys().size();
      int i = 0;
      for (Record record : batchRecords) {
        int count = 0;
        for (ColumnValue pk : record.getPrimaryKeys()) {
          // 源库和目标的库主键信息可能不一致
          Integer index = getIndex(indexs, pk, true);
          if (index != null) {
            ps.setObject(size * i + index, pk.getValue(), pk.getColumn().getType());
            count++;
          }
        }

        for (ColumnValue col : record.getColumns()) {
          // 源库和目标的库主键信息可能不一致
          Integer index = getIndex(indexs, col, true);
          if (index != null) {
            ps.setObject(size * i + index, col.getValue(), col.getColumn().getType());
            count++;
          }
        }

        if (count != indexs.size()) {
          processMissColumn(record, indexs);
        }

        i++;
      }

      List<Record> result = Lists.newArrayList();
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        List<ColumnValue> cms = new ArrayList<>();
        List<ColumnValue> pks = new ArrayList<>();
        // 需要和源库转义后的record保持相同的primary/column顺序，否则对比会失败
        for (ColumnMeta pk : primaryKeys) {
          ColumnValue cv = YuGongUtils.getColumnValue(rs, getTargetEncoding(), pk);
          pks.add(cv);
        }

        for (ColumnMeta col : columns) {
          ColumnValue cv = YuGongUtils.getColumnValue(rs, getTargetEncoding(), col);
          cms.add(cv);
        }

        Record re = new Record(schemaName, tableName, pks, cms);
        result.add(re);
      }

      return result;
    });
    return (List<Record>) results;
  }

  /**
   * 一条条记录串行处理
   */
  protected List<Record> queryOneByOne(JdbcTemplate jdbcTemplate, final List<Record> records) {
    if (records.size() == 0) {
      return Lists.newArrayList();
    }

    Record sampleRecord = records.get(0);
    Table table = tableCache.get(ImmutableList.of(sampleRecord.getSchemaName(),
        sampleRecord.getTableName()));
    TableSqlUnit sqlUnit = getSqlUnit(sampleRecord);
    String selectSql = sqlUnit.applierSql;
    final Map<String, Integer> indexs = sqlUnit.applierIndexs;
        final List<ColumnMeta> primaryKeys = getPrimaryMetas(sampleRecord);
    //final List<ColumnMeta> primaryKeys = table.getPrimaryKeys();
        final List<ColumnMeta> columns = getColumnMetas(records.get(0));
    //final List<ColumnMeta> columns = table.getColumns();

    //处理联合索引情况
    if(sampleRecord.isEnableCompositeIndexes()){
      //logger.info(">> 处理联合索引，正在重新分配主键和列");

      //如有主键则合并到列中，因为目标主键可能也不被指定
      if(!primaryKeys.isEmpty()){
        columns.addAll(primaryKeys);
        //logger.info(">> columns: {}, add pks: {}", new Object[]{columns, primaryKeys});
        primaryKeys.clear();
        //logger.info(">> columns: {}", new Object[]{columns});
      }


      sampleRecord.getCheckCompositeKeys().forEach(key -> {
        Optional<ColumnMeta> columnMetaOptional = columns.stream().filter(c -> c.getName().equals(key)).findFirst();
        if(columnMetaOptional.isPresent()) {
          primaryKeys.add(columns.remove(columns.indexOf(columnMetaOptional.get())));
        } else {
          logger.error(">> [{}]无法匹配到合适的的索引, columns: {}", new Object[]{key, columns});
          throw new RuntimeException(">> 无法匹配到合适的的索引，请检查<CompositeIndexesDataTranslator>的配置");
        }
      });
    }


    Object results = jdbcTemplate.execute(selectSql, (PreparedStatementCallback) ps -> {
      List<Record> result = Lists.newArrayList();
      for (Record record : records) {

        int count = 0;
        for (ColumnValue pk : record.getPrimaryKeys()) {
          // 源库和目标的库主键信息可能不一致
          Integer index = getIndex(indexs, pk, true);
          if (index != null) {
            ps.setObject(index, pk.getValue(), pk.getColumn().getType());
            count++;
          }
        }

        for (ColumnValue col : record.getColumns()) {
          // 源库和目标的库主键信息可能不一致
          Integer index = getIndex(indexs, col, true);
          if (index != null) {
            ps.setObject(index, col.getValue(), col.getColumn().getType());
            count++;
          }
        }

        if (count != indexs.size()) {
          processMissColumn(record, indexs);
        }

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
          List<ColumnValue> columnValues = new ArrayList<>();
          List<ColumnValue> pks = new ArrayList<>();
          // 需要和源库转义后的record保持相同的primary/column顺序，否则对比会失败
          for (ColumnMeta pk : primaryKeys) {
            ColumnValue columnValue = YuGongUtils.getColumnValue(rs, getTargetEncoding(), pk);
            pks.add(columnValue);
          }

          for (ColumnMeta col : columns) {
            ColumnValue columnValue = YuGongUtils.getColumnValue(rs, getTargetEncoding(), col);
            columnValues.add(columnValue);
          }

          Record re = new Record(record.getSchemaName(), record.getTableName(), pks, columnValues);
          result.add(re);
        }
      }
      return result;
    });
    return (List<Record>) results;
  }

  protected String getTargetEncoding() {
    if (dbType.isOracle()) {
      return context.getTargetEncoding();
    } else {
      return null;
    }
  }


  /**
   * @param records1 源库的数据
   * @param records2 目标库的数据
   */
  protected List<String> diff(List<Record> records1, List<Record> records2) {
    List<String> diffResults = Lists.newArrayList();

    Map<List<String>, Record> recordMap2 = new HashMap<>();
    for (Record record : records2) {
      List<String> primaryKeys2 = Lists.newArrayList();
      for (ColumnValue pk : record.getPrimaryKeys()) {
        primaryKeys2.add(ObjectUtils.toString(pk.getValue()));
      }

      recordMap2.put(primaryKeys2, record);
    }

    // 以records1为准
    for (Record record : records1) {

      List<String> primaryKeys1 = Lists.newArrayList();

      for (ColumnValue pk : record.getPrimaryKeys()) {
        primaryKeys1.add(ObjectUtils.toString(pk.getValue()));
      }

      String diff = RecordDiffer.diff(record, recordMap2.remove(primaryKeys1));
      if (!Strings.isNullOrEmpty(diff)) {
        diffResults.add(diff);
      }
    }

    // 比对record2多余的数据
    for (Record record2 : recordMap2.values()) {
      //record2不为null的话，必抛NPE，:(
      String diff = RecordDiffer.diff(null, record2);
      if (!Strings.isNullOrEmpty(diff)) {
        diffResults.add(diff);
      }
    }
    return diffResults;
  }

  protected TableSqlUnit getSqlUnit(Record record) {
    // TODO 获取 target SQL 相关内容，不应该依赖 Record
    List<String> names = Arrays.asList(record.getSchemaName(), record.getTableName());
    TableSqlUnit sqlUnit = selectSqlCache.get(names);
    if (sqlUnit != null) {
      return sqlUnit;
    }
    synchronized (this) {
      sqlUnit = selectSqlCache.get(names);
      if (sqlUnit == null) { // double-check
        sqlUnit = new TableSqlUnit();
        String applierSql = null;
        Table meta = TableMetaGenerator.getTableMeta(dbType, context.getTargetDs(),
            context.isIgnoreSchema() ? null : names.get(0),
            names.get(1));

        List<String> columns = meta.getColumns().stream().map(ColumnMeta::getName)
            .collect(Collectors.toList());
        List<String> primaryKeys = meta.getPrimaryKeys().stream().map(ColumnMeta::getName)
            .collect(Collectors.toList());
        if (dbType == DbType.MYSQL) {

          //处理复合索引情况
          if(record.isEnableCompositeIndexes()){
            primaryKeys.clear();
            primaryKeys.addAll(record.getCheckCompositeKeys());
            columns.removeAll(primaryKeys);
          }

          applierSql = SqlTemplates.MYSQL.getSelectSql(meta.getSchema(),
              meta.getName(),
              primaryKeys,
              columns);
        } else if (dbType == DbType.ORACLE) {
          applierSql = SqlTemplates.ORACLE.getSelectSql(meta.getSchema(),
              meta.getName(),
              primaryKeys,
              columns);
        } else if (dbType == DbType.SQL_SERVER) {
          //处理复合索引情况
          if(record.isEnableCompositeIndexes()){
            primaryKeys.clear();
            primaryKeys.addAll(record.getCheckCompositeKeys());
            columns.removeAll(primaryKeys);
          }

          applierSql = SqlTemplates.SQL_SERVER.getSelectSql(meta.getSchema(),
              meta.getName(),
              primaryKeys,
              columns);
        } else {
          throw new YuGongException("unsupport " + dbType);
        }

        int index = 1;
        Map<String, Integer> indexs = new HashMap<>();
        for (String column : primaryKeys) {
          indexs.put(column, index);
          index++;
        }

        if (index == 1) { // 没有主键
          for (String column : columns) {
            indexs.put(column, index);
            index++;
          }
        }

        // 检查下是否少了列
        checkIndexColumns(meta, indexs); // TODO add translator

        sqlUnit.applierSql = applierSql;
        sqlUnit.applierIndexs = indexs;
        selectSqlCache.put(names, sqlUnit);
      }
    }

    return sqlUnit;
  }

  protected void processMissColumn(final Record record, final Map<String, Integer> indexs) {
    // 如果数量不同，则认为缺少主键
    List<String> allNames = new ArrayList<>(indexs.keySet());
    for (ColumnValue cv : record.getColumns()) {
      Integer index = getIndex(indexs, cv, true);
      if (index != null) {
        allNames.remove(cv.getColumn().getName());
      }
    }

    for (ColumnValue pk : record.getPrimaryKeys()) {
      Integer index = getIndex(indexs, pk, true);
      if (index != null) {
        allNames.remove(pk.getColumn().getName());
      }
    }

    throw new YuGongException("miss columns" + allNames + " and failed Record Data : " + record.toString());
  }

}
