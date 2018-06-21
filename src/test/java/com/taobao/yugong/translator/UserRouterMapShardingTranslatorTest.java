package com.taobao.yugong.translator;

import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.translator.modules.pass.UserRouterMapUtil;

import org.junit.Test;

import static org.junit.Assert.*;

public class UserRouterMapShardingTranslatorTest {
  
  @Test
  public void calculateShardingKey() throws Exception {
    UserRouterMapShardingTranslator translator = new UserRouterMapShardingTranslator();
    assertEquals(32, UserRouterMapUtil.calculateShardingKey("1122334455"));
    assertEquals(35, UserRouterMapUtil.calculateShardingKey("aaa_kao"));
    assertEquals(34, UserRouterMapUtil.calculateShardingKey("hello"));
  }

  @Test
  public void newRouteMapRecord() throws Exception {
    UserRouterMapShardingTranslator translator = new UserRouterMapShardingTranslator();
    Record record = translator.buildRouteMapRecord(RouteMapType.USER_NAME, "hello", 123);
    assertEquals("hello", record.getColumnByName("Content").getValue());
  }

}