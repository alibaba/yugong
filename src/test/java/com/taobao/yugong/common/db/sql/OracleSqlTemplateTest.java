package com.taobao.yugong.common.db.sql;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class OracleSqlTemplateTest {
  @Test
  public void getMergeSql() throws Exception {
    OracleSqlTemplate template = new OracleSqlTemplate();
    String sql = template.getMergeSql("sss", "ttt", new String[]{"id"},
        new String[]{"c1", "c2"});
    Assert.assertEquals("merge /*+ use_nl(a b)*/ into sss.ttt a using (select ? as c1 , ? as c2 , ? as id from dual) b on (a.id=b.id) when matched then update set a.c1=b.c1 , a.c2=b.c2 when not matched then insert (a.c1 , a.c2 , a.id ) values (b.c1 , b.c2 , b.id )", sql);
  }

}