package com.taobao.yugong.translator;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.exception.YuGongException;
import com.taobao.yugong.translator.modules.pass.UserRouterMapUtil;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Sha1ShardingTranslator implements DataTranslator {

  @Getter
  @Setter
  private String shardingKeyName;

  @Override
  public String translatorSchema() {
    return null;
  }

  @Override
  public String translatorTable() {
    return null;
  }

  @Override
  public boolean translator(Record record) {
    return true;
  }

  @VisibleForTesting
  int calculateShardingKey(String input) {
    return UserRouterMapUtil.calculateShardingKey(input);
  }

  @Override
  public List<Record> translator(List<Record> records) {
    if (Strings.isNullOrEmpty(shardingKeyName)) {
      throw new YuGongException("shardingKeyName is not set");
    }

    return records.stream().peek(record -> {
      ColumnValue column = record.getColumnByName(shardingKeyName);
      String shardingValue;
      if (!(column.getValue() instanceof String)) {
        throw new YuGongException(String.format("shardingKey value is not BIGINT or INTEGER %s",
            column.getColumn().getType()));
      } else {
        shardingValue = (String) column.getValue();
      }
      int shardingPartition = calculateShardingKey(shardingValue);
      record.setTableName(record.getTableName() + "_" + shardingPartition);
    }).collect(Collectors.toList());
  }

}
