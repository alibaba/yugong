package com.taobao.yugong.extractor.oracle;

import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.db.sql.SqlTemplates;
import com.taobao.yugong.common.model.ExtractStatus;
import com.taobao.yugong.common.model.ProgressStatus;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.position.Position;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.utils.thread.NamedThreadFactory;
import com.taobao.yugong.exception.YuGongException;

import lombok.Setter;

import org.apache.commons.lang.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 基于oracle的一次性任务
 *
 * @author agapple 2013-9-10 下午5:12:33
 * @since 3.0.0
 */
public class OracleOnceFullRecordExtractor extends AbstractOracleRecordExtractor {

  private static final String FORMAT = "select /*+parallel(t)*/ {0} from {1}.{2} t";
  @Setter
  private String extractSql;
  private LinkedBlockingQueue<Record> queue;
  private Thread extractorThread = null;
  private YuGongContext context;

  public OracleOnceFullRecordExtractor(YuGongContext context) {
    this.context = context;
  }

  public void start() {
    super.start();

    if (StringUtils.isEmpty(extractSql)) {
      String columns = SqlTemplates.COMMON.makeColumn(context.getTableMeta().getColumnsWithPrimary());
      extractSql = new MessageFormat(FORMAT).format(new Object[]{columns, context.getTableMeta().getSchema(),
          context.getTableMeta().getName()});
      // logger.info("table : {} \n\t extract sql : {}",
      // context.getTableMeta().getFullName(), extractSql);
    }

    // 启动异步线程
    extractorThread = new NamedThreadFactory(this.getClass().getSimpleName() + "-"
        + context.getTableMeta().getFullName()).newThread(new ContinueExtractor(context));
    extractorThread.start();

    queue = new LinkedBlockingQueue<Record>(context.getOnceCrawNum() * 2);
    tracer.update(context.getTableMeta().getFullName(), ProgressStatus.FULLING);
  }

  public void stop() {
    super.stop();

    extractorThread.interrupt();
    try {
      extractorThread.join(2 * 1000);
    } catch (InterruptedException e) {
      // ignore
    }
    tracer.update(context.getTableMeta().getFullName(), ProgressStatus.SUCCESS);
  }

  public Position ack(List<Record> records) throws YuGongException {
    return null;
  }

  public class ContinueExtractor implements Runnable {

    private JdbcTemplate jdbcTemplate;

    public ContinueExtractor(YuGongContext context) {
      jdbcTemplate = new JdbcTemplate(context.getSourceDs());
    }

    public void run() {
      jdbcTemplate.execute((StatementCallback) stmt -> {
        stmt.setFetchSize(200);
        stmt.execute(extractSql);
        ResultSet rs = stmt.getResultSet();
        while (rs.next()) {
          List<ColumnValue> cms = new ArrayList<>();
          List<ColumnValue> pks = new ArrayList<>();

          for (ColumnMeta pk : context.getTableMeta().getPrimaryKeys()) {
            ColumnValue cv = getColumnValue(rs, context.getSourceEncoding(), pk);
            pks.add(cv);
          }

          for (ColumnMeta col : context.getTableMeta().getColumns()) {
            ColumnValue cv = getColumnValue(rs, context.getSourceEncoding(), col);
            cms.add(cv);
          }

          Record re = new Record(context.getTableMeta().getSchema(),
              context.getTableMeta().getName(), pks, cms);
          try {
            queue.put(re);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 传递
            throw new YuGongException(e);
          }
        }

        setStatus(ExtractStatus.TABLE_END);
        rs.close();
        return null;
      });

    }
  }

}
