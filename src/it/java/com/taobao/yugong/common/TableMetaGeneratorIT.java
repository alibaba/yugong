package com.taobao.yugong.common;

import com.taobao.yugong.BaseDbIT;
import com.taobao.yugong.common.db.DataSourceFactory;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.db.meta.TableMetaGenerator;
import com.taobao.yugong.common.model.DbType;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

import javax.sql.DataSource;

/**
 * @author agapple 2014年2月25日 下午11:38:06
 * @since 1.0.0
 */
public class TableMetaGeneratorIT extends BaseDbIT {

  @Test
  public void testOracle() {
    DataSourceFactory dataSourceFactory = new DataSourceFactory();
    dataSourceFactory.start();

    String schemaName = "test";
    String tableName = "test_all_target";
    DataSource oracle = dataSourceFactory.getDataSource(getOracleConfig());
    Table table = TableMetaGenerator.getTableMeta(DbType.ORACLE, oracle, schemaName, tableName);
    System.out.println(table);

    Map<String, String> index = TableMetaGenerator.getTableIndex(DbType.ORACLE, oracle, schemaName, 
        tableName);
    System.out.println(index);

    String mlogName = TableMetaGenerator.getMLogTableName(oracle, schemaName, tableName);
    System.out.println(mlogName);

    Table mtable = TableMetaGenerator.getTableMeta(DbType.SQL_SERVER, oracle, schemaName, mlogName);
    System.out.println(mtable);

    Map<String, String> mindex = TableMetaGenerator.getTableIndex(DbType.ORACLE, oracle, 
        schemaName, mlogName);
    System.out.println(mindex);
    dataSourceFactory.stop();
  }

  @Test
  public void testMysql() {
    DataSourceFactory dataSourceFactory = new DataSourceFactory();
    dataSourceFactory.start();

    String schemaName = "hj_product";
    String tableName = "shop_product";
    DataSource mysql = dataSourceFactory.getDataSource(getMysqlConfig());
    Table table = TableMetaGenerator.getTableMeta(DbType.MYSQL, mysql, schemaName, tableName);
    System.out.println(table);

    Map<String, String> index = TableMetaGenerator.getTableIndex(DbType.MYSQL, mysql, schemaName, 
        tableName);
    System.out.println(index);
    dataSourceFactory.stop();
  }

  @Test
  public void testSqlServer() {
    DataSourceFactory dataSourceFactory = new DataSourceFactory();
    dataSourceFactory.start();

    String schemaName = "HJ_VIP";
    String tableName = "Activities";
    DataSource sqlserver = dataSourceFactory.getDataSource(getSqlServerConfig());
    Table table = TableMetaGenerator.getTableMeta(DbType.SQL_SERVER, sqlserver, schemaName, tableName);
    Assert.assertEquals(tableName, table.getName());

    Map<String, String> index = TableMetaGenerator.getTableIndex(DbType.SQL_SERVER, sqlserver, 
        schemaName, tableName);
    Assert.assertTrue(index.size() > 0);
    dataSourceFactory.stop();
    Assert.assertEquals("StartDateTime", table.getColumns().get(0).getName());
    Assert.assertEquals("Id", table.getPrimaryKeys().get(0).getName());
//    Assert.assertEquals("StartDateTime", table.getColumns().get(0).getRawName());
  }
}
