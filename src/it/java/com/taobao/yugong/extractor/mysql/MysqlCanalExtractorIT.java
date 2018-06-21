package com.taobao.yugong.extractor.mysql;

import com.taobao.yugong.BaseDbIT;
import com.taobao.yugong.common.db.DataSourceFactory;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.db.meta.TableMetaGenerator;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.model.RunMode;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.stats.ProgressTracer;
import com.taobao.yugong.extractor.sqlserver.SqlServerFullRecordExtractor;

import lombok.extern.slf4j.Slf4j;

import org.joda.time.DateTime;
import org.junit.Test;

import java.util.List;

import javax.sql.DataSource;

import static org.junit.Assert.*;

@Slf4j
public class MysqlCanalExtractorIT extends BaseDbIT {

  @Test
  public void extract() throws Exception {
    YuGongContext context = new YuGongContext();
    DataSourceFactory dataSourceFactory = new DataSourceFactory();
    dataSourceFactory.start();
    DataSource dataSource = dataSourceFactory.getDataSource(getMysqlConfig());
    Table tableMeta = TableMetaGenerator.getTableMeta(DbType.MYSQL, dataSource, "bbs",
        "user_0");
    ProgressTracer progressTracer = new ProgressTracer(RunMode.INC, 1);
    context.setTableMeta(tableMeta);
    context.setSourceDs(dataSource);
    context.setOnceCrawNum(200);

    MysqlCanalExtractor extractor = new MysqlCanalExtractor(context, "192.168.38.99", 11111);
    extractor.setTracer(progressTracer);
    extractor.start();
    while (true) {
      List<Record> records = extractor.extract();
      log.info("recordes: ", records);
    }
  }

}