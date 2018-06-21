package com.taobao.yugong.common.model;

/**
 * @author agapple 2011-9-2 上午11:36:21
 */
public enum DbType {

  MYSQL("com.mysql.jdbc.Driver"),
  DRDS("com.mysql.jdbc.Driver"),
  ORACLE("oracle.jdbc.driver.OracleDriver"),
  SQL_SERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver");

  private String driver;

  DbType(String driver) {
    this.driver = driver;
  }

  public String getDriver() {
    return driver;
  }

  public boolean isMysql() {
    return this.equals(DbType.MYSQL);
  }

  public boolean isDRDS() {
    return this.equals(DbType.DRDS);
  }

  public boolean isOracle() {
    return this.equals(DbType.ORACLE);
  }

  public boolean isSqlServer() {
    return this.equals(DbType.SQL_SERVER);
  }

}
