package com.taobao.yugong.common.db.sql;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class MysqlSqlTemplateTest {
  @Test
  public void getMergeSql() throws Exception {
    MysqlSqlTemplate template = new MysqlSqlTemplate();
    String sql = template.getMergeSql("sss", "ttt", new String[]{"id"},
        new String[]{"c1", "c2"}, false);
    Assert.assertEquals("insert into sss.ttt(`c1` , `c2` , `id`) values (? , ? , ?) on duplicate key update `c1`=values(`c1`) , `c2`=values(`c2`)", sql);
  }

}