package com.taobao.yugong.extractor.oracle;

import com.google.common.collect.Lists;
import com.google.common.collect.MigrateMap;
import com.taobao.yugong.applier.AbstractRecordApplier.TableSqlUnit;
import com.taobao.yugong.common.YuGongConstants;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.db.meta.TableMetaGenerator;
import com.taobao.yugong.common.db.sql.SqlTemplates;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.model.ExtractStatus;
import com.taobao.yugong.common.model.ProgressStatus;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.position.Position;
import com.taobao.yugong.common.model.record.IncrementOpType;
import com.taobao.yugong.common.model.record.IncrementRecord;
import com.taobao.yugong.common.model.record.OracleIncrementRecord;
import com.taobao.yugong.common.model.record.OracleIncrementRecord.DiscardType;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.utils.thread.ExecutorTemplate;
import com.taobao.yugong.common.utils.thread.NamedThreadFactory;
import com.taobao.yugong.exception.YuGongException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 基于oracle物化视图的增量实现
 *
 * @author agapple 2013-9-16 下午4:15:58
 */
public class OracleMaterializedIncRecordExtractor extends AbstractOracleRecordExtractor {

  // private static final String MLOG_EXTRACT_FORMAT =
  // "select rowid,{0} from {1}.{2} where rownum <= ?";
  private static final String MLOG_EXTRACT_FORMAT = "select * from (select rowid,{0} from {1}.{2} order by sequence$$ asc) where rownum <= ?";
  // private static final String MASTER_FORMAT =
  // "select  /*+index(t {0})*/ {1} from {2}.{3} t where {4}=?";
  private static final String MASTER_MULTI_PK_FORMAT = "select {0} from {1}.{2} t where {3}";
  private static final String MLOG_CLEAN_FORMAT = "delete from {0}.{1} where rowid=?";
  private String mlogExtractSql;
  private String mlogCleanSql;
  private YuGongContext context;
  private Table mlogMeta;
  private ColumnMeta rowidColumn = new ColumnMeta("rowid", Types.ROWID);
  private ColumnMeta rowidColumnString = new ColumnMeta("rowid", Types.VARCHAR);
  private long sleepTime = 1000L;
  private Map<List<String>, TableSqlUnit> masterSqlCache;

  private boolean concurrent = true;
  private int threadSize = 5;
  private int splitSize = 1;
  private ThreadPoolExecutor executor;
  private String executorName;

  public OracleMaterializedIncRecordExtractor(YuGongContext context) {
    this.context = context;
  }

  public void start() {
    super.start();

    masterSqlCache = MigrateMap.makeMap();
    String schemaName = context.getTableMeta().getSchema();
    String tableName = context.getTableMeta().getName();

    // 后去mlog表名
    String mlogTableName = TableMetaGenerator.getMLogTableName(context.getSourceDs(), schemaName, tableName);
    if (StringUtils.isEmpty(mlogTableName)) {
      throw new YuGongException("not found mlog table for [" + schemaName + "." + tableName + "]");
    }
    // 获取mlog表结构
    mlogMeta = TableMetaGenerator.getTableMeta(DbType.ORACLE, context.getSourceDs(),
        context.getTableMeta().getSchema(),
        mlogTableName);

    // 构造mlog sql
    String colstr = SqlTemplates.COMMON.makeColumn(mlogMeta.getColumns());
    mlogExtractSql = new MessageFormat(MLOG_EXTRACT_FORMAT).format(new Object[]{colstr, schemaName, mlogTableName});
    mlogCleanSql = new MessageFormat(MLOG_CLEAN_FORMAT).format(new Object[]{schemaName, mlogTableName});

    executorName = this.getClass().getSimpleName() + "-" + context.getTableMeta().getFullName();
    if (executor == null) {
      executor = new ThreadPoolExecutor(threadSize,
          threadSize,
          60,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<Runnable>(threadSize * 2),
          new NamedThreadFactory(executorName),
          new ThreadPoolExecutor.CallerRunsPolicy());
    }

    tracer.update(context.getTableMeta().getFullName(), ProgressStatus.INCING);
  }

  public void stop() {
    super.stop();
    tracer.update(context.getTableMeta().getFullName(), ProgressStatus.SUCCESS);
  }

  public List extract() throws YuGongException {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getSourceDs());
    List<OracleIncrementRecord> mlogRecords = getMlogRecord(jdbcTemplate);
    if (mlogRecords.size() == 0) {
      setStatus(ExtractStatus.NO_UPDATE);
      logger.info("table[{}] now is {} ...", context.getTableMeta().getFullName(), status);
      tracer.update(context.getTableMeta().getFullName(), ProgressStatus.SUCCESS);
      try {
        Thread.sleep(sleepTime);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();// 传递下去
        return Lists.newArrayList();
      }
    } else if (mlogRecords.size() < context.getOnceCrawNum()) {
      setStatus(ExtractStatus.CATCH_UP);
      logger.info("table[{}] now is {} ...", context.getTableMeta().getFullName(), status);
    }

    buildMasterRecord(jdbcTemplate, mlogRecords);
    return mlogRecords;
  }

  public Position ack(final List<Record> records) throws YuGongException {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getSourceDs());
    jdbcTemplate.execute(mlogCleanSql, new PreparedStatementCallback() {

      @Override
      public Object doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
        for (Record record : records) {
          OracleIncrementRecord incRecord = (OracleIncrementRecord) record;
          ps.setObject(1, incRecord.getRowId().getValue(), incRecord.getRowId().getColumn().getType());
          ps.addBatch();
        }

        ps.executeBatch();
        return null;
      }
    });

    return null;
  }

  private List<OracleIncrementRecord> getMlogRecord(JdbcTemplate jdbcTemplate) {
    return (List<OracleIncrementRecord>) jdbcTemplate.execute(mlogExtractSql, new PreparedStatementCallback() {

      public Object doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
        ps.setInt(1, context.getOnceCrawNum());

        List<OracleIncrementRecord> result = Lists.newArrayList();
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
          IncrementOpType opType = getDmlType(rs);
          List<ColumnValue> primaryKeys = new ArrayList<ColumnValue>();
          List<ColumnValue> columns = new ArrayList<ColumnValue>();

          for (ColumnMeta column : context.getTableMeta().getPrimaryKeys()) {
            if (pkListHave(mlogMeta.getColumns(), column.getName())) {
              ColumnValue pk = getColumnValue(rs, context.getSourceEncoding(), column);
              primaryKeys.add(pk);
            }
          }

          for (ColumnMeta column : context.getTableMeta().getColumns()) {
            if (pkListHave(mlogMeta.getColumns(), column.getName())) {
              ColumnValue col = getColumnValue(rs, context.getSourceEncoding(), column);
              // 针对非主键的列,比如拆分字段,一起当做数据主键
              // 扩展列可能会发生变化,反查时不能带这个字段
              primaryKeys.add(col);
            }
          }

          if (primaryKeys.size() == 0) {
            // 如果是删除操作，直接将判断字段作为主键
            if (opType.toString().equals("D")) {

            }
            ColumnValue col = new ColumnValue(rowidColumnString, rs.getObject("M_ROW$$"));
            primaryKeys.add(col);
          }

          ColumnValue rowId = new ColumnValue(rowidColumn, rs.getObject("rowid"));
          OracleIncrementRecord record = new OracleIncrementRecord(context.getTableMeta().getSchema(),
              context.getTableMeta().getName(),
              primaryKeys,
              columns);
          record.setRowId(rowId);
          record.setOpType(opType);
          result.add(record);
        }

        rs.close();
        return result;
      }
    });
  }

  private void buildMasterRecord(final JdbcTemplate jdbcTemplate, final List<OracleIncrementRecord> records) {
    if (concurrent && records.size() > splitSize) {
      ExecutorTemplate template = new ExecutorTemplate(executor);
      try {
        int index = 0;// 记录下处理成功的记录下标
        int size = records.size();
        // 全量复制时，无顺序要求，数据可以随意切割，直接按照splitSize切分后提交到多线程中进行处理
        for (; index < size; ) {
          int end = (index + splitSize > size) ? size : (index + splitSize);
          final List<OracleIncrementRecord> subList = records.subList(index, end);
          template.submit(new Runnable() {

            public void run() {
              String name = Thread.currentThread().getName();
              try {
                MDC.put(YuGongConstants.MDC_TABLE_SHIT_KEY, context.getTableMeta().getFullName());
                Thread.currentThread().setName(executorName);
                buildMasterRecordOneByOne(jdbcTemplate, subList);
              } finally {
                Thread.currentThread().setName(name);
              }
            }
          });
          index = end;// 移动到下一批次
        }

        // 等待所有结果返回
        template.waitForResult();
      } finally {
        template.clear();
      }
    } else {
      buildMasterRecordOneByOne(jdbcTemplate, records);
    }
  }

  /**
   * 目前为1条条查询，后续可优化为批查询，然后根据pk检索对应的记录
   */
  private void buildMasterRecordOneByOne(JdbcTemplate jdbcTemplate, final List<OracleIncrementRecord> records) {
    if (records.isEmpty()) {
      return;
    }

    TableSqlUnit sqlUnit = getMasterSqlUnit(records.get(0));
    String applierSql = sqlUnit.applierSql;
    jdbcTemplate.execute(applierSql, new PreparedStatementCallback() {

      @Override
      public Object doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
        for (OracleIncrementRecord record : records) {
          int i = 1;
          for (ColumnValue pk : record.getPrimaryKeys()) {
            ps.setObject(i, pk.getValue(), pk.getColumn().getType());
            i++;
          }

          try {
            ResultSet rs = ps.executeQuery();
            // 一条日志对应一条主表记录
            List<ColumnValue> columns = new ArrayList<ColumnValue>();
            boolean exist = false;
            if (rs.next()) {
              exist = true;

              if ("ROWID".equals(context.getMViewLogType())) {
                String[] pks = context.getTablepks().get(record.getTableName());
                List<ColumnValue> primaryKeys = new ArrayList<ColumnValue>();
                // 反查获取到完整行记录 并重设主键
                for (ColumnMeta col : context.getTableMeta().getColumns()) {
                  ColumnValue cv = getColumnValue(rs, context.getSourceEncoding(), col);
                  if (Arrays.asList(pks).contains(col.getName())) {
                    primaryKeys.add(cv);
                  } else {
                    columns.add(cv);
                  }
                }
                record.setPrimaryKeys(primaryKeys);
              } else {
                // 反查获取到完整行记录
                for (ColumnMeta col : context.getTableMeta().getColumns()) {
                  ColumnValue cv = getColumnValue(rs, context.getSourceEncoding(), col);
                  columns.add(cv);
                }
              }

            }

            if (!columns.isEmpty()) {
              record.setColumns(columns);
            }

            if ((record.getOpType() == IncrementOpType.I || record.getOpType() == IncrementOpType.U)
                && !exist) {
              // 记录已经被删除,忽略该记录同步，由后续日志进行删除
              record.setDiscardType(DiscardType.DELTE_AFTER_IU);
            } else if (exist && record.getOpType() == IncrementOpType.D) {
              // 说明后续肯定有一个insert操作,因为mysql那边使用replace,所以这种情况下也不需要做事
              record.setDiscardType(DiscardType.IU_AFTER_DELETE);
            } else {
              record.setDiscardType(DiscardType.NONE);
            }

            rs.close();
          } catch (SQLException e) {
            throw new SQLException("failed Record Data : " + record.toString(), e);
          }
        }

        return null;
      }
    });
  }

  protected TableSqlUnit getMasterSqlUnit(IncrementRecord record) {
    List<String> names = Arrays.asList(record.getSchemaName(), record.getTableName());
    TableSqlUnit sqlUnit = masterSqlCache.get(names);
    if (sqlUnit == null) {
      synchronized (this) {
        sqlUnit = masterSqlCache.get(names);
        if (sqlUnit == null) { // double-check
          sqlUnit = new TableSqlUnit();
          String applierSql = null;
          // 构造master sql
          String colstr = SqlTemplates.COMMON.makeColumn(context.getTableMeta().getColumnsWithPrimary());
          List<ColumnMeta> primaryMetas = Lists.newArrayList();
          for (ColumnValue col : record.getPrimaryKeys()) {
            primaryMetas.add(col.getColumn());
          }
          String priStr = SqlTemplates.COMMON.makeWhere(primaryMetas);
          applierSql = new MessageFormat(MASTER_MULTI_PK_FORMAT).format(new Object[] { colstr,
              record.getSchemaName(), record.getTableName(), priStr });

          sqlUnit.applierSql = applierSql;
          masterSqlCache.put(names, sqlUnit);
        }
      }
    }

    return sqlUnit;
  }

  private boolean pkListHave(List<ColumnMeta> pks, String mayBePk) {
    for (ColumnMeta pk : pks) {
      if (pk.getName().equalsIgnoreCase(mayBePk)) {
        return true;
      }
    }

    return false;
  }

  private IncrementOpType getDmlType(ResultSet rs) throws SQLException {
    String dmlType = rs.getString("DMLTYPE$$");
    // 针对主键或者拆分条件的变更,会表现为OLD_NEW=O + N,但是拆分条件的变更DMLTYPE=U,需要强制修改为D操作
    String oldNew = rs.getString("OLD_NEW$$");
    if (oldNew.equalsIgnoreCase("O")) {
      return IncrementOpType.D;
    } else {
      return IncrementOpType.valueOf(dmlType);
    }
  }

  public void setSleepTime(long sleepTime) {
    this.sleepTime = sleepTime;
  }

  public void setThreadSize(int threadSize) {
    this.threadSize = threadSize;
  }

  public void setSplitSize(int splitSize) {
    this.splitSize = splitSize;
  }

  public void setConcurrent(boolean concurrent) {
    this.concurrent = concurrent;
  }

  public void setExecutor(ThreadPoolExecutor executor) {
    this.executor = executor;
  }

}
