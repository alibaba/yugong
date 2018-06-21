package com.taobao.yugong.common.model.record;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.taobao.yugong.exception.YuGongException;

import java.util.Map;
import java.util.Optional;

/**
 * 增量类型
 *
 * @author agapple 2013-9-16 下午4:21:27
 */
public enum IncrementOpType {
  I/* INSERT */, U/* UPDATE */, D/* DELETE */;
  
  public static Optional<IncrementOpType> ofSqlServerCdc(int operation) {
    Map<Integer, IncrementOpType> mapping = ImmutableMap.of(
        1, D,
        2, I,
        4, U
    );
    if (operation == 3) {
      return Optional.empty();
    }
    if (!mapping.containsKey(operation)) {
      throw new YuGongException(String.format("Do not support %s", operation));
    }
    return Optional.of(mapping.get(operation));
  }

  public static Optional<IncrementOpType> ofMysqlCancal(CanalEntry.EventType eventType) {
    ImmutableMap<CanalEntry.EventType, IncrementOpType> mapping = ImmutableMap
        .<CanalEntry.EventType, IncrementOpType>builder()
        .put(CanalEntry.EventType.INSERT, IncrementOpType.I)
        .put(CanalEntry.EventType.DELETE, IncrementOpType.D)
        .put(CanalEntry.EventType.UPDATE, IncrementOpType.U)
//        .put(CanalEntry.EventType.CREATE, null)
//        .put(CanalEntry.EventType.ALTER, null)
//        .put(CanalEntry.EventType.ERASE, null)
//        .put(CanalEntry.EventType.QUERY, null)
//        .put(CanalEntry.EventType.TRUNCATE, null)
//        .put(CanalEntry.EventType.RENAME, null)
//        .put(CanalEntry.EventType.CINDEX, null)
//        .put(CanalEntry.EventType.DINDEX, null)
        .build();

    if (!mapping.containsKey(eventType)) {
      return Optional.empty();
    }
    return Optional.of(mapping.get(eventType));
  }
}
