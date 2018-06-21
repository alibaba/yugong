package com.taobao.yugong.common.db.sql;

import com.taobao.yugong.common.db.meta.ColumnMeta;

import java.util.List;

/**
 * sql构造
 *
 * @author agapple 2013-9-10 下午6:10:10
 * @since 1.0.0
 */
public class SqlTemplate {

  private static final String DOT = ".";

  /**
   * 根据字段的列表顺序，拼写以 col1,col2,col3,....
   */
  public String makeColumn(List<ColumnMeta> columns) {
    StringBuilder str = new StringBuilder();
    int size = columns.size();
    for (int i = 0; i < size; i++) {
      str.append(getColumnName(columns.get(i)));
      if (i < (size - 1)) {
        str.append(",");
      }
    }
    return str.toString();
  }

  /**
   * 根据字段的列表顺序，拼写以 ?,?,?,....
   */
  public String makeInsert(List<ColumnMeta> columns) {
    StringBuilder str = new StringBuilder();
    int size = columns.size();
    for (int i = 0; i < size; i++) {
      str.append('?');
      if (i < (size - 1)) {
        str.append(",");
      }
    }
    return str.toString();
  }

  /**
   * 根据字段的列表顺序，拼写 column1=?,column2=?,column3=?,...
   */
  public String makeUpdate(List<ColumnMeta> columns) {
    StringBuilder str = new StringBuilder();
    int size = columns.size();
    for (int i = 0; i < size; i++) {
      str.append(getColumnName(columns.get(i)));
      str.append("=?");
      if (i < (size - 1)) {
        str.append(",");
      }
    }
    return str.toString();
  }

  /**
   * 根据字段列表，拼写column=? and column=? and ...字符串
   */
  public String makeWhere(List<ColumnMeta> columns) {
    StringBuilder sb = new StringBuilder("");
    int size = columns.size();
    for (int i = 0; i < size; i++) {
      sb.append(getColumnName(columns.get(i)));
      sb.append("=?");
      if (i != (size - 1)) {
        sb.append(" and ");
      }
    }
    return sb.toString();
  }

  /**
   * 根据字段列表，拼写column >= ? and column < ?
   */
  public String makeRange(ColumnMeta column) {
    return makeRange(column.getName());
  }

  /**
   * 根据字段列表，拼写 column >= ? and column < ?
   */
  public String makeRange(String columnName) {
    StringBuilder sb = new StringBuilder("");
    sb.append(getColumnName(columnName));
    sb.append(" >= ? and ");
    sb.append(getColumnName(columnName));
    sb.append(" <= ?");
    return sb.toString();
  }

  /**
   * 根据字段名和参数个数，拼写 column in (?,?,...) 字符串
   */
  public String makeIn(ColumnMeta column, int size) {
    return makeIn(column.getName(), size);
  }

  /**
   * 根据字段名和参数个数，拼写 column in (?,?,...) 字符串
   */
  public String makeIn(String columnName, int size) {
    StringBuilder sb = new StringBuilder("");
    sb.append(getColumnName(columnName));
    sb.append(" in (");
    for (int i = 0; i < size; i++) {
      sb.append("?");
      if (i != (size - 1)) {
        sb.append(",");
      }
    }
    sb.append(")");
    return sb.toString();
  }

  public String getSelectSql(String schemaName, String tableName, List<String> pkNames,
      List<String> colNames) {
    StringBuilder sql = new StringBuilder();
    sql.append("select ");
    String[] allColumns = buildAllColumns(pkNames.toArray(new String[0]),
        colNames.toArray(new String[0]));
    int size = allColumns.length;
    for (int i = 0; i < size; i++) {
      sql.append(getColumnName(allColumns[i])).append(splitCommea(size, i));
    }

    sql.append(" from ").append(makeFullName(schemaName, tableName)).append(" where ( ");
    if (pkNames.size() > 0) { // 可能没有主键
      makeColumnEquals(sql, pkNames.toArray(new String[0]), "and");
    } else {
      makeColumnEquals(sql, colNames.toArray(new String[0]), "and");
    }
    sql.append(" ) ");
    return sql.toString().intern();
  }

  public String getSelectInSql(String schemaName, String tableName, String[] pkNames, String[] columnNames, int inSize) {
    StringBuilder sql = new StringBuilder("select ");
    String[] allColumns = buildAllColumns(pkNames, columnNames);
    int size = allColumns.length;
    for (int i = 0; i < size; i++) {
      sql.append(getColumnName(allColumns[i])).append(splitCommea(size, i));
    }

    sql.append(" from ").append(makeFullName(schemaName, tableName)).append(" where ( ");
    size = pkNames.length;
    if (size == 0) {
      size = columnNames.length;
    }

    for (int i = 0; i < size; i++) {
      if (pkNames.length > 0) {
        sql.append(getColumnName(pkNames[i])).append(splitCommea(size, i));
      } else {
        sql.append(getColumnName(columnNames[i])).append(splitCommea(size, i));
      }
    }
    sql.append(") in (");

    size = pkNames.length;
    if (size == 0) {
      size = columnNames.length;
    }
    for (int i = 0; i < inSize; i++) {
      sql.append('(');
      for (int j = 0; j < size; j++) {
        sql.append('?').append((j + 1 < size) ? " , " : "");
      }
      sql.append(')').append((i + 1 < inSize) ? " , " : "");
    }
    sql.append(")");
    return sql.toString();
  }

  public String getInsertSql(String schemaName, String tableName, String[] pkNames, String[] columnNames) {
    StringBuilder sql = new StringBuilder();
    sql.append("insert into ").append(makeFullName(schemaName, tableName)).append("(");
    String[] allColumns = buildAllColumns(pkNames, columnNames);
    int size = allColumns.length;
    for (int i = 0; i < size; i++) {
      sql.append(getColumnName(allColumns[i])).append(splitCommea(size, i));
    }

    sql.append(") values (");
    makeColumnQuestions(sql, allColumns);
    sql.append(")");
    return sql.toString().intern();
  }

  public String getUpdateSql(String schemaName, String tableName, String[] pkNames, String[] columnNames) {
    StringBuilder sql = new StringBuilder();
    sql.append("update ").append(makeFullName(schemaName, tableName)).append(" set ");
    makeColumnEquals(sql, columnNames, ",");
    sql.append(" where (");
    makeColumnEquals(sql, pkNames, "and");
    sql.append(")");
    return sql.toString().intern();
  }

  public String getDeleteSql(String schemaName, String tableName, String[] pkNames) {
    StringBuilder sql = new StringBuilder();
    sql.append("delete from ").append(makeFullName(schemaName, tableName)).append(" where ");
    makeColumnEquals(sql, pkNames, "and");
    // intern优化，避免出现大量相同的字符串
    return sql.toString().intern();
  }

  protected String makeFullName(String schemaName, String tableName) {
    String full = schemaName + DOT + tableName;
    return full.intern();
  }

  protected void makeColumnEquals(StringBuilder sql, String[] columns, String separator) {
    int size = columns.length;
    for (int i = 0; i < size; i++) {
      sql.append(" ").append(getColumnName(columns[i])).append(" = ").append("? ");
      if (i != size - 1) {
        sql.append(separator);
      }
    }
  }

  protected void makeColumnQuestions(StringBuilder sql, String[] columns) {
    int size = columns.length;
    for (int i = 0; i < size; i++) {
      sql.append("?").append(splitCommea(size, i));
    }
  }

  protected String getColumnName(String columName) {
    return columName;
  }

  protected String getColumnName(ColumnMeta column) {
    return column.getName();
  }

  protected String splitCommea(int size, int i) {
    return (i + 1 < size) ? " , " : "";
  }

  protected String splitAnd(int size, int i){
    return (i + 1 < size) ? " and " : "";
  }

  protected String[] buildAllColumns(String[] pkNames, String[] colNames) {
    String[] allColumns = new String[pkNames.length + colNames.length];
    System.arraycopy(colNames, 0, allColumns, 0, colNames.length);
    System.arraycopy(pkNames, 0, allColumns, colNames.length, pkNames.length);
    return allColumns;
  }

}
