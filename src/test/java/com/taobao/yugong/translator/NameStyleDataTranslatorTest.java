package com.taobao.yugong.translator;

import com.google.common.base.CaseFormat;

import org.junit.Assert;
import org.junit.Test;

public class NameStyleDataTranslatorTest {
  @Test
  public void tableCaseConvert() throws Exception {
    NameStyleDataTranslator translator = new NameStyleDataTranslator();
    translator.setTableCaseFormatFrom(CaseFormat.UPPER_CAMEL);
    translator.setTableCaseFormatTo(CaseFormat.LOWER_UNDERSCORE);
    translator.setColumnCaseFormatFrom(CaseFormat.UPPER_CAMEL);
    translator.setColumnCaseFormatTo(CaseFormat.LOWER_UNDERSCORE);
    Assert.assertEquals("some_table", translator.tableCaseConvert("SomeTable"));
    Assert.assertEquals("product_id", translator.columnCaseConvert("ProductID"));
  }

  @Test
  public void columnCaseConvert() throws Exception {
  }

  @Test
  public void tableCaseConvertSMS() throws Exception {
    NameStyleDataTranslator translator = new NameStyleDataTranslator();
    translator.setTableCaseFormatFrom(CaseFormat.UPPER_CAMEL);
    translator.setTableCaseFormatTo(CaseFormat.LOWER_UNDERSCORE);
    translator.setColumnCaseFormatFrom(CaseFormat.UPPER_CAMEL);
    translator.setColumnCaseFormatTo(CaseFormat.LOWER_UNDERSCORE);
    Assert.assertEquals("s_m_s_level", translator.columnCaseConvert("SMSLevel"));
  }

}