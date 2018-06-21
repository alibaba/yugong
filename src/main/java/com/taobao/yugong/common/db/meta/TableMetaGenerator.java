package com.taobao.yugong.common.db.meta;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.utils.LikeUtil;
import com.taobao.yugong.exception.YuGongException;

import org.apache.commons.lang.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

/**
 * 基于mysql的table meta获取
 * TODO ugly world here, refactory to 3 DB types implements
 *
 * @author agapple 2013-9-9 下午2:45:30
 * @since 3.0.0
 */
public class TableMetaGenerator {

  private static final String mlogQuerySql = "select master,log_table from all_mview_logs where master = ?";
  private static final String mlogSchemaQuerySql = "select master,log_table from all_mview_logs where master = ? and log_owner = ?";
  private static final String queryShardKey = "show partitions from ?";

  /**
   * 获取对应的table meta信息，精确匹配
   */
  public static Table getTableMeta(final DbType dbType, final DataSource dataSource, final String 
      schemaName, final String tableName) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    Object result = jdbcTemplate.execute((ConnectionCallback) conn -> {
      DatabaseMetaData metaData = conn.getMetaData();
      String schemaNameIdentifier = getIdentifierName(schemaName, metaData);
      String tableNameIdentifier = getIdentifierName(tableName, metaData);

      ResultSet rs;
      if (dbType == DbType.MYSQL || dbType == DbType.ORACLE || dbType == DbType.DRDS) {
        rs = metaData.getTables(schemaNameIdentifier, schemaNameIdentifier, tableNameIdentifier, new String[]{"TABLE"});
      } else if (dbType == DbType.SQL_SERVER) {
        rs = metaData.getTables(schemaNameIdentifier, null, tableNameIdentifier, new String[]{"TABLE"});
      } else {
        throw new YuGongException("unknown db type");
      }
      Table table = null;
      while (rs.next()) {
        String catlog = rs.getString(1);
        String schema = rs.getString(2);
        String name = rs.getString(3);
        String type = rs.getString(4);
        //if (ImmutableList.of("__$start_lsn", "__$end_lsn", "__$seqval", "__$operation",
            //"__$update_mask").contains(name)) {
          //continue;
        //}

        if ((schemaNameIdentifier == null || LikeUtil.isMatch(schemaNameIdentifier, catlog) || LikeUtil.isMatch(schemaNameIdentifier, schema))
            && LikeUtil.isMatch(tableNameIdentifier, name)) {
          table = new Table(type, StringUtils.isEmpty(catlog) ? schema : catlog, name);
          break;
        }
      }
      rs.close();

      if (table == null) {
        throw new YuGongException("table[" + schemaName + "." + tableName + "] is not found");
      }

      List<ColumnMeta> columnList = queryColumns(dbType, dataSource, table);

      // 查询主键信息
      List<String> primaryKeys = new ArrayList<>();
      if (dbType == DbType.MYSQL || dbType == DbType.ORACLE || dbType == DbType.DRDS) {
        rs = metaData.getPrimaryKeys(schemaNameIdentifier, schemaNameIdentifier, tableNameIdentifier);
      } else if (dbType == DbType.SQL_SERVER) {
        rs = metaData.getPrimaryKeys(schemaNameIdentifier, null, tableNameIdentifier);
      } else {
        throw new YuGongException("unknown db type");
      }
      while (rs.next()) {
        String catlog = rs.getString(1);
        String schema = rs.getString(2);
        String name = rs.getString(3);
        if ((schemaNameIdentifier == null || LikeUtil.isMatch(schemaNameIdentifier, catlog) || LikeUtil.isMatch(schemaNameIdentifier, schema))
            && LikeUtil.isMatch(tableNameIdentifier, name)) {
          //          primaryKeys.add(StringUtils.upperCase(rs.getString(4)));
          primaryKeys.add(rs.getString(4));
        }
      }
      rs.close();

      List<String> uniqueKeys = new ArrayList<>();
      if (primaryKeys.isEmpty()) {
        String lastIndexName = null;
        if (dbType == DbType.MYSQL || dbType == DbType.ORACLE || dbType == DbType.DRDS) {
          rs = metaData.getIndexInfo(schemaNameIdentifier, schemaNameIdentifier, tableNameIdentifier, true, true);
        } else if (dbType == DbType.SQL_SERVER) {
          rs = metaData.getIndexInfo(schemaNameIdentifier, null, tableNameIdentifier, true, true);
        } else {
          throw new YuGongException("unknown db type");
        }
        while (rs.next()) {
          String catlog = rs.getString(1);
          String schema = rs.getString(2);
          String name = rs.getString(3);
          if ((schemaNameIdentifier == null || LikeUtil.isMatch(schemaNameIdentifier, catlog) || LikeUtil.isMatch(schemaNameIdentifier, schema))
              && LikeUtil.isMatch(tableNameIdentifier, name)) {
            //            String indexName = StringUtils.upperCase(rs.getString(6));
            String indexName = rs.getString(6);
            if ("PRIMARY".equals(indexName)) {
              continue;
            }

            if (lastIndexName == null) {
              lastIndexName = indexName;
            } else if (!lastIndexName.equals(indexName)) {
              break;
            }

            //            uniqueKeys.add(StringUtils.upperCase(rs.getString(9)));
            uniqueKeys.add(rs.getString(9));
          }
        }
        rs.close();

        // 如果无主键，使用唯一键
        primaryKeys.addAll(uniqueKeys);
      }

      Set<ColumnMeta> columns = new HashSet<>();
      Set<ColumnMeta> pks = new HashSet<>();
      for (ColumnMeta columnMeta : columnList) {
        if (primaryKeys.contains(columnMeta.getName())) {
          pks.add(columnMeta);
        } else {
          columns.add(columnMeta);
        }
      }

      table.getColumns().addAll(columns);
      table.getPrimaryKeys().addAll(pks);
      return table;
    });
    return (Table) result;
  }

  /**
   * 查询所有的表，不返回表中的字段
   * NOTE: scan all tables mode only support Oracle
   */
  @Deprecated
  public static List<Table> getTableMetasWithoutColumn(final DataSource dataSource, final String schemaName,
      final String tableName) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    Object metaResult = jdbcTemplate.execute((ConnectionCallback) conn -> {
      DatabaseMetaData metaData = conn.getMetaData();
      List<Table> result = Lists.newArrayList();
      String databaseName = metaData.getDatabaseProductName();
      String schemaNameIdentifier = getIdentifierName(schemaName, metaData);
      String tableNameIdentifier = getIdentifierName(tableName, metaData);
      ResultSet rs = null;
      Table table = null;
      if (StringUtils.startsWithIgnoreCase(databaseName, "oracle") && StringUtils.isEmpty(schemaName)
          && StringUtils.isEmpty(tableName)) {
        // 针对oracle，只查询用户表，忽略系统表
        Statement stmt = conn.createStatement();
        rs = stmt.executeQuery("SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS SCHEMA_NAME , TABLE_NAME FROM USER_TABLES T , USER_USERS U WHERE U.USERNAME = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')");

        while (rs.next()) {
          String schema = rs.getString(1);
          String name = rs.getString(2);
          if (!StringUtils.startsWithIgnoreCase(name, "MLOG$_") && !StringUtils.startsWithIgnoreCase(name, "RUPD$_")) {
            table = new Table("TABLE", schema, name);
            result.add(table);
          }
        }

        rs.close();
        stmt.close();
        return result;
      } else {
        rs = metaData.getTables(schemaNameIdentifier, schemaNameIdentifier, tableNameIdentifier, new String[]{"TABLE"});
        while (rs.next()) {
          String catlog = rs.getString(1);
          String schema = rs.getString(2);
          String name = rs.getString(3);
          String type = rs.getString(4);

          if (!StringUtils.startsWithIgnoreCase(name, "MLOG$_") && !StringUtils.startsWithIgnoreCase(name, "RUPD$_")) {
            table = new Table(type, StringUtils.isEmpty(catlog) ? schema : catlog, name);
            result.add(table);
          }
        }

        rs.close();
        return result;
      }
    });
    return (List<Table>) metaResult;
  }

  /**
   * 返回就诶过 key:column name , value=index name
   */
  public static Map<String, String> getTableIndex(final DbType dbType, final DataSource dataSource,
      final String schemaName, final String tableName) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    Object result = jdbcTemplate.execute((ConnectionCallback) conn -> {
      DatabaseMetaData metaData = conn.getMetaData();
      String schemaNameIdentifier = getIdentifierName(schemaName, metaData);
      String tableNameIdentifier = getIdentifierName(tableName, metaData);

      ResultSet rs;
      if (dbType == DbType.MYSQL || dbType == DbType.ORACLE || dbType == DbType.DRDS) {
        rs = metaData.getIndexInfo(schemaNameIdentifier, schemaNameIdentifier, tableNameIdentifier, false, true);
      } else if (dbType == DbType.SQL_SERVER) {
        rs = metaData.getIndexInfo(schemaNameIdentifier, null, tableNameIdentifier, false, true);
      } else {
        throw new YuGongException("unknown db type");
      }
      Map<String, String> indexes = new HashMap<>();
      while (rs.next()) {
        String columnName = rs.getString(9);
        String indexName = rs.getString(6);
        if (columnName != null && indexName != null) {
          indexes.put(columnName, indexName);
        }
      }

      rs.close();
      return indexes;
    });
    return (Map<String, String>)result;
  }

  /**
   * <pre>
   * 常见的物化视图创建语句：
   * 1. CREATE MATERIALIZED VIEW LOG ON test_all_target with primary key;
   *
   * 本方法，主要提取生成物化视图的表名
   * </pre>
   */
  public static String getMLogTableName(final DataSource dataSource, final String schemaName, final String tableName) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    String sql = StringUtils.isNotEmpty(schemaName) ? mlogSchemaQuerySql : mlogQuerySql;
    return (String) jdbcTemplate.execute(sql, new PreparedStatementCallback() {

      public Object doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
        DatabaseMetaData metaData = ps.getConnection().getMetaData();
        String schemaNameIdentifier = getIdentifierName(schemaName, metaData);
        String tableNameIdentifier = getIdentifierName(tableName, metaData);
        ps.setString(1, tableNameIdentifier);
        if (StringUtils.isNotEmpty(schemaName)) {
          ps.setString(2, schemaNameIdentifier);
        }
        ResultSet rs = ps.executeQuery();
        String log = null;
        if (rs.next()) {
          log = rs.getString("log_table");
        }

        rs.close();
        return log;
      }
    });
  }
  
  void doQueryColumn(DbType dbType, DataSource dataSource,
      final Table table, ConnectionCallback conn) {
    
  }

  public static List<ColumnMeta> queryColumns(DbType dbType, DataSource dataSource,
      final Table table) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    Object result = jdbcTemplate.execute((ConnectionCallback) conn -> {
      List<ColumnMeta> columns = new ArrayList<>();
      DatabaseMetaData metaData = conn.getMetaData();
      String schemaNameIdentifier = getIdentifierName(table.getSchema(), metaData);
      String tableNameIdentifier = getIdentifierName(table.getName(), metaData);
      // 查询所有字段
      ResultSet rs;
      if (dbType == DbType.MYSQL || dbType == DbType.ORACLE || dbType == DbType.DRDS) {
        rs = metaData.getColumns(schemaNameIdentifier, schemaNameIdentifier, tableNameIdentifier, null);
      } else if (dbType == DbType.SQL_SERVER) {
        rs = metaData.getColumns(schemaNameIdentifier, null, tableNameIdentifier, null);
      } else {
        throw new YuGongException("unknown db type");
      }
      while (rs.next()) {
        String catlog = rs.getString(1);
        String schema = rs.getString(2);
        String name = rs.getString(3);
        if ((schemaNameIdentifier == null || LikeUtil.isMatch(schemaNameIdentifier, catlog)
            || LikeUtil.isMatch(schemaNameIdentifier, schema))
            && LikeUtil.isMatch(tableNameIdentifier, name)) {
          String columnName = rs.getString(4); // COLUMN_NAME
          int columnType = rs.getInt(5);
          String typeName = rs.getString(6);
          columnType = convertSqlType(columnType, typeName);
          ColumnMeta col = new ColumnMeta(columnName, columnType);
          columns.add(col);
        }
      }
      rs.close();
      return columns;
    });
    return (List<ColumnMeta>) result;
  }

  /**
   * 获取DRDS下表的拆分字段, 返回格式为 id,name
   */
  public static String getShardKeyByDRDS(final DataSource dataSource, final String schemaName, final String tableName) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    try {
      return (String) jdbcTemplate.execute(queryShardKey, (PreparedStatementCallback) ps -> {
        DatabaseMetaData metaData = ps.getConnection().getMetaData();
        // String sName = getIdentifierName(schemaName, metaData);
        String tableNameIdentifier = getIdentifierName(tableName, metaData);

        ps.setString(1, tableNameIdentifier);
        ResultSet rs = ps.executeQuery();
        String log = null;
        if (rs.next()) {
          log = rs.getString("KEYS");
        }

        rs.close();
        return log;
      });
    } catch (DataAccessException e) {
      // 兼容下oracle源库和目标库DRDS表名不一致的情况,识别一下表名不存在
      Throwable cause = e.getRootCause();
      if (cause instanceof SQLException) {
        // ER_NO_SUCH_TABLE
        if (((SQLException) cause).getErrorCode() == 1146) {
          return null;
        }
      }

      throw e;
    }
  }

  /**
   * 根据{@linkplain DatabaseMetaData}获取正确的表名
   *
   * <pre>
   * metaData中的storesUpperCaseIdentifiers，storesUpperCaseQuotedIdentifiers，storesLowerCaseIdentifiers,
   * storesLowerCaseQuotedIdentifiers,storesMixedCaseIdentifiers,storesMixedCaseQuotedIdentifiers
   * </pre>
   */
  private static String getIdentifierNameBackup(String name, DatabaseMetaData metaData) throws 
      SQLException {
    if (metaData.storesMixedCaseIdentifiers()) {
      return name; // 保留原始名
    } else if (metaData.storesUpperCaseIdentifiers()) {
      return StringUtils.upperCase(name);
    } else if (metaData.storesLowerCaseIdentifiers()) {
      return StringUtils.lowerCase(name);
    } else {
      return name;
    }
  }

  /**
   * return pure name
   */
  private static String getIdentifierName(String name, DatabaseMetaData metaData) throws 
      SQLException {
    return name;
  }

  private static int convertSqlType(int columnType, String typeName) {
    String[] typeSplit = typeName.split(" ");
    if (typeSplit.length > 1) {
      if (columnType == Types.INTEGER && StringUtils.equalsIgnoreCase(typeSplit[1], "UNSIGNED")) {
        columnType = Types.BIGINT;
      }
    }

    if (columnType == Types.OTHER) {
      if (StringUtils.equalsIgnoreCase(typeName, "NVARCHAR")
          || StringUtils.equalsIgnoreCase(typeName, "NVARCHAR2")) {
        columnType = Types.VARCHAR;
      }

      if (StringUtils.equalsIgnoreCase(typeName, "NCLOB")) {
        columnType = Types.CLOB;
      }

      if (StringUtils.startsWithIgnoreCase(typeName, "TIMESTAMP")) {
        columnType = Types.TIMESTAMP;
      }
    }
    return columnType;
  }

}
