package com.taobao.yugong.applier;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.MigrateMap;
import com.taobao.yugong.common.YuGongConstants;
import com.taobao.yugong.common.db.IncrementRecordMerger;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.record.IncrementOpType;
import com.taobao.yugong.common.model.record.IncrementRecord;
import com.taobao.yugong.common.utils.YuGongUtils;
import com.taobao.yugong.common.utils.thread.ExecutorTemplate;
import com.taobao.yugong.common.utils.thread.NamedThreadFactory;
import com.taobao.yugong.exception.YuGongException;

import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 支持multi-thread处理
 *
 * @author agapple 2013-9-23 下午5:30:50
 */
public class MultiThreadIncrementRecordApplier extends IncrementRecordApplier {

  private int threadSize = 5;
  private int splitSize = 50;
  private ThreadPoolExecutor executor;
  private String executorName;

  public MultiThreadIncrementRecordApplier(YuGongContext context) {
    super(context);
  }

  public MultiThreadIncrementRecordApplier(YuGongContext context, int threadSize, int splitSize) {
    super(context);

    this.threadSize = threadSize;
    this.splitSize = splitSize;
  }

  public MultiThreadIncrementRecordApplier(YuGongContext context, int threadSize, int splitSize,
      ThreadPoolExecutor executor) {
    super(context);

    this.threadSize = threadSize;
    this.splitSize = splitSize;
    this.executor = executor;
  }

  public void start() {
    super.start();

    executorName = this.getClass().getSimpleName() + "-" + context.getTableMeta().getFullName();
    if (executor == null) {
      executor = new ThreadPoolExecutor(threadSize,
          threadSize,
          60,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue(threadSize * 2),
          new NamedThreadFactory(executorName),
          new ThreadPoolExecutor.CallerRunsPolicy());
    }
  }

  public void stop() {
    super.stop();

    executor.shutdownNow();
  }

  public void apply(List records) throws YuGongException {
    // no one,just return
    if (YuGongUtils.isEmpty(records)) {
      return;
    }

    // 进行数据合并
    List<IncrementRecord> mergeRecords = IncrementRecordMerger.merge(records);
    doApply(mergeRecords);
  }

  protected void doApply(List records) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getTargetDs());

    Map<List<String>, Map<IncrementOpType, List<IncrementRecord>>> buckets = buildBucket(records);
    for (Map<IncrementOpType, List<IncrementRecord>> bucket : buckets.values()) {
      if (context.isBatchApply()) {
        // 优先处理delete
        applyBatch(bucket.get(IncrementOpType.D), jdbcTemplate, IncrementOpType.D);
        // 处理insert/update
        applyBatch(bucket.get(IncrementOpType.I), jdbcTemplate, IncrementOpType.I);
        applyBatch(bucket.get(IncrementOpType.U), jdbcTemplate, IncrementOpType.U);
      } else {
        applyOneByOne(bucket.get(IncrementOpType.D), jdbcTemplate);
        applyOneByOne(bucket.get(IncrementOpType.I), jdbcTemplate);
        applyOneByOne(bucket.get(IncrementOpType.U), jdbcTemplate);
      }
    }
  }

  /**
   * 划分为table + I/U/D类型
   */
  protected Map<List<String>, Map<IncrementOpType, List<IncrementRecord>>> buildBucket(List records) {
    Map<List<String>, Map<IncrementOpType, List<IncrementRecord>>> buckets = MigrateMap.makeComputingMap(names -> MigrateMap.makeComputingMap(new Function<IncrementOpType, List<IncrementRecord>>() {

      public List<IncrementRecord> apply(IncrementOpType opType) {
        return Lists.newArrayList();
      }
    }));

    // 先按照I/U/D进行划分下
    for (Object record : records) {
      IncrementRecord incRecord = (IncrementRecord) record;
      List<String> names = Arrays.asList(incRecord.getSchemaName(), incRecord.getTableName());
      buckets.get(names).get(incRecord.getOpType()).add(incRecord);
    }
    return buckets;
  }

  /**
   * 并发处理batch
   */
  protected void applyBatch(List<IncrementRecord> incRecords, final JdbcTemplate jdbcTemplate,
      final IncrementOpType opType) {
    if (incRecords.size() > splitSize) {// 超过一定大小才进行多线程处理
      ExecutorTemplate template = new ExecutorTemplate(executor);
      try {
        int index = 0;// 记录下处理成功的记录下标
        int size = incRecords.size();
        // 全量复制时，无顺序要求，数据可以随意切割，直接按照splitSize切分后提交到多线程中进行处理
        for (; index < size; ) {
          int end = (index + splitSize > size) ? size : (index + splitSize);
          final List<IncrementRecord> subList = incRecords.subList(index, end);
          template.submit(new Runnable() {

            public void run() {
              String name = Thread.currentThread().getName();
              try {
                MDC.put(YuGongConstants.MDC_TABLE_SHIT_KEY, context.getTableMeta().getFullName());
                Thread.currentThread().setName(executorName);
                applyBatch0(subList, jdbcTemplate, opType);
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
      applyBatch0(incRecords, jdbcTemplate, opType);
    }
  }

  /**
   * batch处理支持
   */
  protected void applyBatch0(final List<IncrementRecord> incRecords, JdbcTemplate jdbcTemplate, IncrementOpType opType) {
    if (YuGongUtils.isEmpty(incRecords)) {
      return;
    }

    boolean redoOneByOne = false;
    try {
      TableSqlUnit sqlUnit = getSqlUnit(incRecords.get(0));
      String applierSql = sqlUnit.applierSql;
      final Map<String, Integer> indexs = sqlUnit.applierIndexs;
      jdbcTemplate.execute(applierSql, new PreparedStatementCallback() {

        public Object doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {

          for (IncrementRecord incRecord : incRecords) {
            int count = 0;

            // 需要先加字段
            List<ColumnValue> cvs = incRecord.getColumns();
            for (ColumnValue cv : cvs) {
              Integer index = getIndex(indexs, cv, true); // 考虑delete的目标库主键，可能在源库的column中
              if (index != null) {
                ps.setObject(index, cv.getValue(), cv.getColumn().getType());
                count++;
              }
            }

            // 添加主键
            List<ColumnValue> pks = incRecord.getPrimaryKeys();
            for (ColumnValue pk : pks) {
              Integer index = getIndex(indexs, pk, true);// 考虑delete的目标库主键，可能在源库的column中
              if (index != null) {
                ps.setObject(index, pk.getValue(), pk.getColumn().getType());
                count++;
              }
            }

            if (count != indexs.size()) {
              processMissColumn(incRecord, indexs);
            }

            ps.addBatch();
          }

          return ps.executeBatch();
        }
      });
    } catch (Exception e) {
      // catch the biggest exception,no matter how, rollback it;
      redoOneByOne = true;
      // conn.rollback();
    }

    // batch cannot pass the duplicate entry exception,so
    // if executeBatch throw exception,rollback it, and
    // redo it one by one
    if (redoOneByOne) {
      // 如果出错，强制使用单线程处理
      super.applyOneByOne(incRecords, jdbcTemplate);
    }

  }

  protected void applyOneByOne(List<IncrementRecord> incRecords, final JdbcTemplate jdbcTemplate) {
    if (incRecords.size() > 1) {
      ExecutorTemplate template = new ExecutorTemplate(executor);
      try {
        int index = 0;// 记录下处理成功的记录下标
        int size = incRecords.size();
        // 全量复制时，无顺序要求，数据可以随意切割，直接按照splitSize切分后提交到多线程中进行处理
        for (; index < size; ) {
          int end = (index + 1 > size) ? size : (index + 1);
          final List<IncrementRecord> subList = incRecords.subList(index, end);
          template.submit(new Runnable() {

            public void run() {
              String name = Thread.currentThread().getName();
              try {
                MDC.put(YuGongConstants.MDC_TABLE_SHIT_KEY, context.getTableMeta().getFullName());
                Thread.currentThread().setName(executorName);
                applyOneByOne(subList, jdbcTemplate);
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
      super.applyOneByOne(incRecords, jdbcTemplate);
    }
  }
}
