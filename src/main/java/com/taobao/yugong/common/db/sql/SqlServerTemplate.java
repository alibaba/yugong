package com.taobao.yugong.common.db.sql;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.taobao.yugong.common.db.meta.ColumnMeta;

import org.apache.commons.lang3.text.StrSubstitutor;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SqlServerTemplate extends SqlTemplate {
  
  @Override
  public String getInsertSql(String schemaName, String tableName, String[] pkNames, String[] columnNames) {
    StringBuilder sql = new StringBuilder();
    sql.append(String.format("SET IDENTITY_INSERT [%s] ON;", tableName));
    sql.append("INSERT INTO ").append(makeFullName(schemaName, tableName)).append(" (");
    String[] allColumns = buildAllColumns(pkNames, columnNames);
    int size = allColumns.length;
    for (int i = 0; i < size; i++) {
      sql.append(getColumnName(allColumns[i])).append(splitCommea(size, i));
    }

    sql.append(") VALUES (");
    makeColumnQuestions(sql, allColumns);
    sql.append(")");
    return sql.toString().intern();
  }

  @Override
  public String getSelectSql(String schemaName, String tableName, List<String> pkNames, List<String> colNames) {
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT ");
    String[] allColumns = buildAllColumns(pkNames.toArray(new String[0]),
        colNames.toArray(new String[0]));
    int size = allColumns.length;
    for (int i = 0; i < size; i++) {
      sql.append(getColumnName(allColumns[i])).append(splitCommea(size, i));
    }

    sql.append(" FROM ").append(makeFullName(schemaName, tableName)).append(" WHERE ( ");
    if (pkNames.size() > 0) { // 可能没有主键
      makeColumnEquals(sql, pkNames.toArray(new String[0]), "AND");
    } else {
      makeColumnEquals(sql, colNames.toArray(new String[0]), "AND");
    }
    sql.append(" ) ");
    return sql.toString().intern();
  }

  public String getMergeSql(String schemaName, String tableName, String[] pkNames,
      String[] colNames, boolean identityInsertMode) {
    HashMap<String, String> params = Maps.newHashMap();

    String identityInsert = "";

    if(identityInsertMode){
      identityInsert = "SET IDENTITY_INSERT ${tableName} ON;\n";
    }

    String sqlTemplate = identityInsert
        + "MERGE ${tableName} AS target\n"
        + "USING (values (${questions})) AS source (${allColumns})\n"
        + "ON ${conditionPrimaryEqualString}\n"
        + "WHEN MATCHED THEN\n"
        + "   UPDATE SET ${setColumnString}\n"
        + "WHEN NOT MATCHED THEN\n"
        + "   INSERT (${allColumns}) VALUES (${allColumnsWithSource});";
    String[] allColumns = buildAllColumns(pkNames, colNames);
    int size = allColumns.length;
    params.put("tableName", makeFullName(schemaName, tableName));
    params.put("questions", Joiner.on(", ").join(IntStream.range(0, size).boxed()
        .map(x -> "?").collect(Collectors.toList())));
    params.put("allColumns", Joiner.on(", ").join(allColumns));

    params.put("conditionPrimaryEqualString", Joiner.on(" AND ").join(
        Lists.newArrayList(pkNames).stream()
            .map(x -> String.format("target.%s = source.%s", x, x)).collect(Collectors.toList())
    ));


    params.put("allColumnsWithSource", Joiner.on(", ").join(
        Lists.newArrayList(allColumns).stream()
            .map(x -> String.format("source.%s", x)).collect(Collectors.toList())
    ));
    params.put("setColumnString", Joiner.on(", ").join(
        Lists.newArrayList(colNames).stream()
            .map(x -> String.format("%s = source.%s", x, x)).collect(Collectors.toList())
    ));
    return StrSubstitutor.replace(sqlTemplate, params).intern();
  }
  

  @Override
  public String makeColumn(List<ColumnMeta> columns) {
    StringBuilder str = new StringBuilder();
    int size = columns.size();
    for (int i = 0; i < size; i++) {
      str.append("[" + getColumnName(columns.get(i)) + "]");
      if (i < (size - 1)) {
        str.append(",");
      }
    }
    return str.toString();
  }

  @Override
  protected String makeFullName(String schemaName, String tableName) {
    String full = "[" + schemaName + "].dbo.[" + tableName + "]";
    return full.intern();
  }

  @Override
  public String getDeleteSql(String schemaName, String tableName, String[] pkNames) {
    StringBuilder sql = new StringBuilder();
    sql.append("delete from ").append(makeFullName(schemaName, tableName)).append(" where ");
    makeColumnEquals(sql, pkNames, "and");
    // intern优化，避免出现大量相同的字符串
    return sql.toString().intern();
  }
}
