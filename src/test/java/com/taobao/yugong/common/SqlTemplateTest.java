package com.taobao.yugong.common;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.sql.SqlTemplates;

import org.junit.Test;

import java.util.List;

/**
 * @author agapple 2014年2月25日 下午11:38:06
 * @since 1.0.0
 */
public class SqlTemplateTest {

  @Test
  public void testSimple() {
    List<ColumnMeta> columns = buildColumns("col", 10);
    System.out.println(SqlTemplates.COMMON.makeColumn(columns));
    System.out.println(SqlTemplates.COMMON.makeInsert(columns));
    System.out.println(SqlTemplates.COMMON.makeUpdate(columns));
    System.out.println(SqlTemplates.COMMON.makeWhere(columns));
    System.out.println(SqlTemplates.COMMON.makeRange("col"));
    System.out.println(SqlTemplates.COMMON.makeIn("col", 10));
  }

  @Test
  public void testMysql() {
    List<ColumnMeta> columns = buildColumns("col", 10);
    System.out.println(SqlTemplates.MYSQL.makeColumn(columns));
    System.out.println(SqlTemplates.MYSQL.makeInsert(columns));
    System.out.println(SqlTemplates.MYSQL.makeUpdate(columns));
    System.out.println(SqlTemplates.MYSQL.makeWhere(columns));
    System.out.println(SqlTemplates.MYSQL.makeRange("col"));
    System.out.println(SqlTemplates.MYSQL.makeIn("col", 10));

    System.out.println(SqlTemplates.MYSQL.getSelectSql("schema", "table", ImmutableList.of(),
        ImmutableList.of("cm1", "cm2")));
    // System.out.println(SqlTemplates.MYSQL.getSelectInSql("schema",
    // "table", new String[] {}, new String[] { "cm1",
    // "cm2" }, 5));
  }

  private List<ColumnMeta> buildColumns(String columnName, int size) {
    List<ColumnMeta> result = Lists.newArrayList();
    for (int i = 0; i < size; i++) {
      result.add(new ColumnMeta(columnName + i, java.sql.Types.VARCHAR));
    }

    return result;
  }
}
