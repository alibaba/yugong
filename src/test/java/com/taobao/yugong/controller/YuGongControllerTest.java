package com.taobao.yugong.controller;

import org.joda.time.DateTime;
import org.junit.Test;

import static org.junit.Assert.*;

public class YuGongControllerTest {

  @Test
  public void timeParse() throws Exception {
    long time = DateTime.parse("2017-9-14T00:00:00").toDate().getTime();
    assertEquals(1505318400L * 1000, time);
  }
}