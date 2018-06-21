package com.taobao.yugong.translator.modules.pass;

import org.junit.Test;

import static org.junit.Assert.*;

public class UserRouterMapUtilTest {
  @Test
  public void calculateShardingKey() throws Exception {
    assertEquals(13, UserRouterMapUtil.calculateShardingKey("18217036697"));
    assertEquals(1, UserRouterMapUtil.calculateShardingKey("siva"));
  }

}