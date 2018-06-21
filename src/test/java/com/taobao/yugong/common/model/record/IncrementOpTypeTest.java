package com.taobao.yugong.common.model.record;

import com.alibaba.otter.canal.protocol.CanalEntry;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class IncrementOpTypeTest {
  @Test
  public void ofMysqlCancal() throws Exception {
    assertEquals(Optional.empty(), IncrementOpType.ofMysqlCancal(CanalEntry.EventType.CINDEX));
    assertEquals(Optional.of(IncrementOpType.I),
        IncrementOpType.ofMysqlCancal(CanalEntry.EventType.INSERT));
  }

}