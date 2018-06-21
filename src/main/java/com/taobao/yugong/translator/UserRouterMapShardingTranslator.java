package com.taobao.yugong.translator;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.IncrementRecord;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.translator.modules.pass.UserRouterMapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class UserRouterMapShardingTranslator implements DataTranslator {

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
  Record buildRouteMapRecord(RouteMapType type, String input, int userId) {
    return UserRouterMapUtil.buildRouteMapRecord(type, input, userId);
  }

  @VisibleForTesting
  Record buildUserNameRecord(Record record) {
    ColumnValue inputColumn = record.getColumnByName("UserName");
    ColumnValue userIdColumn = record.getColumnByName("UserID");
    return UserRouterMapUtil.buildRouteMapRecord(RouteMapType.USER_NAME, (String) inputColumn.getValue(),
        (int) userIdColumn.getValue());
  }
  
  private List<Record> buildUserNameRecordCdc(IncrementRecord record) {
    ColumnValue inputColumn = record.getColumnByName("UserName");
    ColumnValue userIdColumn = record.getColumnByName("UserID");
    return UserRouterMapUtil.buildRouteMapRecordCdc(record.getOpType(),
        RouteMapType.USER_NAME, (String) inputColumn.getValue(),
        (int) userIdColumn.getValue());
  }

  @VisibleForTesting
  Optional<Record> buildUserEmailRecord(Record record) {
    ColumnValue inputColumn = record.getColumnByName("UserEmail");
    if (inputColumn == null) {
      return Optional.empty();
    }
    if (inputColumn.getValue() == null) {
      return Optional.empty();
    }
    if (Strings.isNullOrEmpty((String)inputColumn.getValue())) {
      return Optional.empty();
    }
    ColumnValue userIdColumn = record.getColumnByName("UserID");
    return Optional.of(UserRouterMapUtil.buildRouteMapRecord(RouteMapType.EMAIL, (String) inputColumn.getValue(),
        (int) userIdColumn.getValue()));
  }
  
  private List<Record> buildUserEmailRecordCdc(IncrementRecord record) {
    ColumnValue inputColumn = record.getColumnByName("UserEmail");
    if (inputColumn == null) {
      return ImmutableList.of();
    }
    if (inputColumn.getValue() == null) {
      return ImmutableList.of();
    }
    if (Strings.isNullOrEmpty((String)inputColumn.getValue())) {
      return ImmutableList.of();
    }
    ColumnValue userIdColumn = record.getColumnByName("UserID");
    return UserRouterMapUtil.buildRouteMapRecordCdc(
        record.getOpType(),
        RouteMapType.EMAIL, (String) inputColumn.getValue(),
        (int) userIdColumn.getValue());
  }

  @Override
  public List<Record> translator(List<Record> records) {
    ArrayList<Record> newRecords = Lists.newArrayList();
    records.forEach(record -> {
      if (record instanceof IncrementRecord) {
        IncrementRecord incrementRecord = (IncrementRecord) record;
        newRecords.addAll(buildUserNameRecordCdc(incrementRecord));
        newRecords.addAll(buildUserEmailRecordCdc(incrementRecord));
      } else {
        newRecords.add(buildUserNameRecord(record));
        Optional<Record> emailRecordOpt = buildUserEmailRecord(record);
        emailRecordOpt.ifPresent(newRecords::add);
      }
    });
    newRecords.addAll(records);
    return newRecords;
  }

}
