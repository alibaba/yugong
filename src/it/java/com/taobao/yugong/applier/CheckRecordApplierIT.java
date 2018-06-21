package com.taobao.yugong.applier;

import com.google.common.base.CaseFormat;
import com.taobao.yugong.BaseDbIT;
import com.taobao.yugong.common.db.DataSourceFactory;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.db.meta.TableMetaGenerator;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.model.RunMode;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.stats.ProgressTracer;
import com.taobao.yugong.extractor.sqlserver.SqlServerFullRecordExtractor;
import com.taobao.yugong.translator.NameStyleDataTranslator;
import com.taobao.yugong.translator.NameTableMetaTranslator;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import javax.sql.DataSource;

public class CheckRecordApplierIT extends BaseDbIT {

  @Test
  public void doApply() throws Exception {
    YuGongContext context = new YuGongContext();
    DataSourceFactory dataSourceFactory = new DataSourceFactory();
    dataSourceFactory.start();
    DataSource dataSource = dataSourceFactory.getDataSource(getSqlServerConfig());
    final String sourceSchema = "HJ_VIP";
    final String sourceTable = "ShopProduct";
    //    final String sourceTable = "CategoryProperty";
//    final String sourceTable = "FrontCategory";
    //    final String sourceTable = "FrontCategoryMapping";
    //    final String sourceTable = "ProductCategory";
    //    final String sourceTable = "ProductOperationLog";
//    final String sourceTable = "ProductProperty";
//    final String sourceTable = "ProductRequestLog";
//    final String sourceTable = "Property";
//    final String sourceTable = "PropertyValue";
//    final String sourceTable = "Seller"; // TODO
//    final String sourceTable = "SellerMapping";
//    final String sourceTable = "SellerRequestLog";
//    final String sourceTable = "ShopMultiProduct";
//    final String sourceTable = "ShopServiceProduct";
    String targetSchema = "hj_product";
    Table tableMeta = TableMetaGenerator.getTableMeta(DbType.SQL_SERVER, dataSource, sourceSchema,
        sourceTable);
    ProgressTracer progressTracer = new ProgressTracer(RunMode.CHECK, 1);
    context.setTableMeta(tableMeta);
    context.setSourceDs(dataSource);
    context.setOnceCrawNum(200);

    SqlServerFullRecordExtractor extractor = new SqlServerFullRecordExtractor(context);
    extractor.setTracer(progressTracer);
//    extractor.initContinueExtractor();
    extractor.start();

    List<Record> records = extractor.extract();

    YuGongContext applierContext = new YuGongContext();
    DataSource applierDataSource = dataSourceFactory.getDataSource(getMysqlConfig());
    applierContext.setTargetDs(applierDataSource);
    applierContext.setOnceCrawNum(200);
    CheckRecordApplier applier = new CheckRecordApplier(applierContext);

    NameStyleDataTranslator translator = new NameStyleDataTranslator() { // TODO configurable
      @Override
      public boolean translator(Record record) {
//        if (record.getSchemaName().equals(sourceSchema)) {
//          record.setSchemaName(targetSchema);
//        }
        if (record.getTableName().equals("ProductOperationLog")) {
          record.getColumnByName("Editdate").setColumn(new ColumnMeta("EditDate",
              record.getColumnByName("Editdate").getColumn().getType()));
        }
        if (record.getTableName().equals("FrontCategory")) {
          record.getColumnByName("BussinessID")
              .setColumn(new ColumnMeta("BusinessID", record.getColumnByName("BussinessID").getColumn().getType()));
        }
        if (record.getTableName().equals("FrontCategory")) {
          record.getColumnByName("IsHighLight")
              .setColumn(new ColumnMeta("IsHightlight", record.getColumnByName("IsHighLight").getColumn().getType()));
        }
        if (record.getTableName().equals("ProductRequestLog")) {
          record.getColumnByName("Indate")
              .setColumn(new ColumnMeta("InDate", record.getColumnByName("Indate").getColumn().getType()));
        }
        if (record.getTableName().equals("SellerRequestLog")) {
          record.getColumnByName("Indate")
              .setColumn(new ColumnMeta("InData", record.getColumnByName("Indate").getColumn().getType()));
        }
        
        return super.translator(record);
      }
    };
    translator.setTableCaseFormatFrom(CaseFormat.UPPER_CAMEL);
    translator.setTableCaseFormatTo(CaseFormat.LOWER_UNDERSCORE);
    translator.setColumnCaseFormatFrom(CaseFormat.UPPER_CAMEL);
    translator.setColumnCaseFormatTo(CaseFormat.LOWER_UNDERSCORE);

    NameTableMetaTranslator tableMetaTranslator = new NameTableMetaTranslator();
    tableMetaTranslator.setTableCaseFormatFrom(CaseFormat.UPPER_CAMEL);
    tableMetaTranslator.setTableCaseFormatTo(CaseFormat.LOWER_UNDERSCORE);
    tableMetaTranslator.setColumnCaseFormatFrom(CaseFormat.UPPER_CAMEL);
    tableMetaTranslator.setColumnCaseFormatTo(CaseFormat.LOWER_UNDERSCORE);
    applier.setTableMetaTranslator(tableMetaTranslator);

    applier.start();
    List<String> diffs = applier.doApply(translator.translator(records));
    Assert.assertEquals(diffs.toString(), 0, diffs.size());
  }

}