package com.taobao.yugong.translator;

import com.google.common.base.CaseFormat;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.Table;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameTableMetaTranslatorTest {
  @Test
  public void translator() throws Exception {
    Table table = new Table("SQLSERVER", "scheme", "SomeTable");
    table.addColumn(new ColumnMeta("ID", 1));
    table.addColumn(new ColumnMeta("Name", 1));
    table.addColumn(new ColumnMeta("ShopProduct", 1));
    
    NameTableMetaTranslator tableMetaTranslator = new NameTableMetaTranslator();
    tableMetaTranslator.setTableCaseFormatFrom(CaseFormat.UPPER_CAMEL);
    tableMetaTranslator.setTableCaseFormatTo(CaseFormat.LOWER_UNDERSCORE);
    tableMetaTranslator.setColumnCaseFormatFrom(CaseFormat.UPPER_CAMEL);
    tableMetaTranslator.setColumnCaseFormatTo(CaseFormat.LOWER_UNDERSCORE);
    
    tableMetaTranslator.translator(table);
    Assert.assertEquals("some_table", table.getName());
    Assert.assertEquals("id", table.getColumns().get(0).getName());
    Assert.assertEquals("name", table.getColumns().get(1).getName());
    Assert.assertEquals("shop_product", table.getColumns().get(2).getName());
//    Assert.assertEquals("id", table.getColumns().get(0).getRawName());
//    Assert.assertEquals("name", table.getColumns().get(1).getRawName());
//    Assert.assertEquals("shop_product", table.getColumns().get(2).getRawName());
  }

}