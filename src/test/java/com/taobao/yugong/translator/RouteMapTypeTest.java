package com.taobao.yugong.translator;

import org.junit.Test;

import static org.junit.Assert.*;

public class RouteMapTypeTest {
  @Test
  public void getValue() throws Exception {
    assertEquals(0, RouteMapType.USER_NAME.getValue());
    assertEquals(1, RouteMapType.EMAIL.getValue());
    assertEquals(2, RouteMapType.MOBILE.getValue());
  }

}