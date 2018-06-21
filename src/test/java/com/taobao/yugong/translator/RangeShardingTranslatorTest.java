package com.taobao.yugong.translator;

import org.junit.Test;

import static org.junit.Assert.*;

public class RangeShardingTranslatorTest {

  @Test
  public void calculateShardingKey() throws Exception {
    RangeShardingTranslator translator = new RangeShardingTranslator ();
    translator.setRangeSize(10000);
    assertEquals(0, translator.calculateShardingKey(1));
    assertEquals(0, translator.calculateShardingKey(9999));
    assertEquals(1, translator.calculateShardingKey(10000));
    assertEquals(1, translator.calculateShardingKey(10001));
  }
}