package com.taobao.yugong.translator;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.IncrementOpType;
import com.taobao.yugong.common.model.record.IncrementRecord;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.translator.modules.pass.UserRouterMapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class UserRouterMapMobileShardingTranslator implements DataTranslator {

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
  Optional<Record> newMobileNumRecord(Record record) {
    ColumnValue mobileNumColumn = record.getColumnByName("MobileNum");
    ColumnValue isBindColumn = record.getColumnByName("IsBinded");
    if (isBindColumn == null) {
      return Optional.empty();
    }
    if (isBindColumn.getValue() == null) {
      return Optional.empty();
    }
    if (!(boolean)isBindColumn.getValue()) {
      return Optional.empty();
    }
    if (mobileNumColumn == null) {
      return Optional.empty();
    }
    if (mobileNumColumn.getValue() == null) {
      return Optional.empty();
    }
    ColumnValue userIdColumn = record.getColumnByName("UserId");
    return Optional.of(UserRouterMapUtil.buildRouteMapRecord(RouteMapType.MOBILE,
        "+86 " + mobileNumColumn.getValue(), (int) userIdColumn.getValue()));
  }

  @VisibleForTesting
  List<Record> newMobileNumRecordCdc(IncrementRecord record) {
    ColumnValue mobileNumColumn = record.getColumnByName("MobileNum");
    ColumnValue isBindColumn = record.getColumnByName("IsBinded");
    if (isBindColumn == null) {
      return ImmutableList.of();
    }
    if (isBindColumn.getValue() == null) {
      return ImmutableList.of();
    }
    if (mobileNumColumn == null) {
      return ImmutableList.of();
    }
    if (mobileNumColumn.getValue() == null) {
      return ImmutableList.of();
    }
    ColumnValue userIdColumn = record.getColumnByName("UserId");
    if ((boolean)isBindColumn.getValue()) {
      return UserRouterMapUtil.buildRouteMapRecordCdc(record.getOpType(), RouteMapType.MOBILE,
          "+86 " + mobileNumColumn.getValue(), (int) userIdColumn.getValue());
    } else { // unbind mobile, remove Old LoginRouteMap Mobile Num
      if (record.getOpType() == IncrementOpType.U) {
        return UserRouterMapUtil.buildRouteMapRecordCdcRemoveMobile(
            "+86 " + mobileNumColumn.getValue(), (int)userIdColumn.getValue());
      }
      else {
        return ImmutableList.of();
      }
    }
  }


  @Override
  public List<Record> translator(List<Record> records) {
    ArrayList<Record> newRecords = Lists.newArrayList();
    records.forEach(record -> {
      if (record instanceof IncrementRecord) {
        IncrementRecord incrementRecord = (IncrementRecord) record;
        newRecords.addAll(newMobileNumRecordCdc(incrementRecord));
      } else {
        Optional<Record> mobileNumRecordOpt = newMobileNumRecord(record);
        mobileNumRecordOpt.ifPresent(newRecords::add);
      }
    });
    newRecords.addAll(records);
    return newRecords;
  }

}
