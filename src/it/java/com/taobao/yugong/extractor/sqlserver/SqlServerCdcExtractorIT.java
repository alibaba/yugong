package com.taobao.yugong.extractor.sqlserver;

import com.taobao.yugong.BaseDbIT;
import com.taobao.yugong.common.db.DataSourceFactory;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.db.meta.TableMetaGenerator;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.model.ExtractStatus;
import com.taobao.yugong.common.model.RunMode;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.record.IncrementRecord;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.stats.ProgressTracer;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import javax.sql.DataSource;

public class SqlServerCdcExtractorIT extends BaseDbIT {

  public static final String SOURCE_SCHEMA = "HJ_Test3D";
  public static final String SOURCE_TABLE = "fruits";

  @Test
  public void fetchCdcRecord() throws Exception {
    YuGongContext context = new YuGongContext();
    DataSourceFactory dataSourceFactory = new DataSourceFactory();
    dataSourceFactory.start();
    DataSource dataSource = dataSourceFactory.getDataSource(getSqlServerConfig());
    Table tableMeta = TableMetaGenerator.getTableMeta(DbType.SQL_SERVER, dataSource, SOURCE_SCHEMA,
        SOURCE_TABLE);
    ProgressTracer progressTracer = new ProgressTracer(RunMode.CHECK, 1);


    context.setTableMeta(tableMeta);
    context.setSourceDs(dataSource);
    context.setOnceCrawNum(200);

    SqlServerCdcExtractor extractor = new SqlServerCdcExtractor(context,
        new DateTime(2017, 9, 12, 14, 3, 0), 1000, 60 * 10);
    extractor.setTracer(progressTracer);
    extractor.start();


    JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getSourceDs());
    List<IncrementRecord> records = extractor.fetchCdcRecord(
        jdbcTemplate, tableMeta.getPrimaryKeys(), tableMeta.getColumns(),
        new DateTime(2017, 9, 11, 14, 3, 0), new DateTime(2017, 9, 11, 14, 51, 0));
    Assert.assertTrue(records.size() > 5);

    dataSourceFactory.stop();
  }

  @Test
  public void extract() throws Exception {
    YuGongContext context = new YuGongContext();
    DataSourceFactory dataSourceFactory = new DataSourceFactory();
    dataSourceFactory.start();
    DataSource dataSource = dataSourceFactory.getDataSource(getSqlServerConfig());
    Table tableMeta = TableMetaGenerator.getTableMeta(DbType.SQL_SERVER, dataSource, SOURCE_SCHEMA,
        SOURCE_TABLE);
    ProgressTracer progressTracer = new ProgressTracer(RunMode.CHECK, 1);

    context.setTableMeta(tableMeta);
    context.setSourceDs(dataSource);
    context.setOnceCrawNum(60 * 10); // Second

    SqlServerCdcExtractor extractor = new SqlServerCdcExtractor(context,
        new DateTime(2017, 9, 12, 14, 3, 0), 1000, 60 * 10);

    extractor.setTracer(progressTracer);
    extractor.start();
    while (true) {
      if (extractor.getStatus() == ExtractStatus.NO_UPDATE) {
        break;
      }
      List<Record> extract = extractor.extract();
      extract.forEach(System.out::println);
      Thread.sleep(500);
    }

    dataSourceFactory.stop();
  }
}