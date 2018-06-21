package com.taobao.yugong.translator;

import com.google.common.collect.Maps;

import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;

public class ColumnTranslatorTest {

  @Test
  public void jodaMapper() throws Exception {
    ColumnTranslator columnTranslator = new ColumnTranslator();
    HashMap<String, Object> value = Maps.newHashMap();
    value.put("a", new Date(2017, 1, 1, 1, 1, 1));
    String s = columnTranslator.objectMapper.writeValueAsString(value);
    Assert.assertEquals("{\"a\":\"3917-01-31T17:01:01Z\"}", s);
  }
}