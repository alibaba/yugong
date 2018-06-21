package com.taobao.yugong.extractor;

import com.taobao.yugong.BaseDbIT;
import com.taobao.yugong.common.db.DataSourceFactory;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.db.meta.TableMetaGenerator;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.extractor.sqlserver.SqlServerFullRecordExtractor;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;

import javax.sql.DataSource;

public class FullContinueExtractorIT extends BaseDbIT {
  
  @Test
  public void queryAndSaveToQueueSQLServer() throws Exception {
    YuGongContext context = new YuGongContext();
    DataSourceFactory dataSourceFactory = new DataSourceFactory();
    dataSourceFactory.start();
    DataSource dataSource = dataSourceFactory.getDataSource(getSqlServerConfig());
    Table tableMeta = TableMetaGenerator.getTableMeta(DbType.SQL_SERVER, dataSource, "HJ_VIP", 
        "Activities");

    context.setTableMeta(tableMeta);
    context.setSourceDs(dataSource);
    context.setOnceCrawNum(200);
    
    FullContinueExtractor extractor = new FullContinueExtractor(
        new SqlServerFullRecordExtractor(context), context, new LinkedBlockingQueue<>());
    extractor.queryAndSaveToQueue();
    Assert.assertTrue(extractor.getQueue().size() >= 17);
    Record record = extractor.getQueue().poll();
//    Assert.assertEquals("StartDateTime", record.getColumns().get(0).getColumn().getRawName());
    Assert.assertEquals("StartDateTime", record.getColumns().get(0).getColumn().getName());

    dataSourceFactory.stop();
  }

  @Test
  public void queryAndSaveToQueueSQLServerHugeData() throws Exception {
    YuGongContext context = new YuGongContext();
    DataSourceFactory dataSourceFactory = new DataSourceFactory();
    dataSourceFactory.start();
    DataSource dataSource = dataSourceFactory.getDataSource(getSqlServerConfig());
    Table tableMeta = TableMetaGenerator.getTableMeta(DbType.SQL_SERVER, dataSource, "HJ_VIP",
        "ShopProduct");

    context.setTableMeta(tableMeta);
    context.setSourceDs(dataSource);
    context.setOnceCrawNum(200);

    FullContinueExtractor extractor = new FullContinueExtractor(
        new SqlServerFullRecordExtractor(context), context, new LinkedBlockingQueue<>());
    extractor.queryAndSaveToQueue();
    Assert.assertEquals(200, extractor.getQueue().size());

    dataSourceFactory.stop();
  }

}