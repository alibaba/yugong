package com.taobao.yugong.extractor.sqlserver;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.taobao.yugong.BaseDbIT;
import com.taobao.yugong.common.db.DataSourceFactory;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.db.meta.TableMetaGenerator;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.model.RunMode;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.stats.ProgressTracer;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

public class SqlServerFullRecordExtractorIT extends BaseDbIT {

  private final MetricRegistry metrics = new MetricRegistry();
//  private final Timer timer = metrics.timer("consume");
  private final Meter meter = metrics.meter("consume-meter");


  @Before
  public void setUp() {
    ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .build();
    reporter.start(1, TimeUnit.SECONDS);
  }

  @Test
  public void run() throws Exception {
    YuGongContext context = new YuGongContext();
    DataSourceFactory dataSourceFactory = new DataSourceFactory();
    dataSourceFactory.start();
    DataSource dataSource = dataSourceFactory.getDataSource(getSqlServerConfig());
    Table tableMeta = TableMetaGenerator.getTableMeta(DbType.SQL_SERVER, dataSource, "HJ_VIP",
        "ShopProduct");
    ProgressTracer progressTracer = new ProgressTracer(RunMode.CHECK, 1);
    context.setTableMeta(tableMeta);
    context.setSourceDs(dataSource);
    context.setOnceCrawNum(200);

    SqlServerFullRecordExtractor sqlServerFullRecordExtractor = new SqlServerFullRecordExtractor(context);
    sqlServerFullRecordExtractor.setTracer(progressTracer);
    sqlServerFullRecordExtractor.start();

    Executors.newSingleThreadExecutor().execute(() -> {
      while (true) {
        try {
          Record record = sqlServerFullRecordExtractor.getQueue().take();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        meter.mark();
      }
    });
    sqlServerFullRecordExtractor.getExtractorThread().join();
  }

}