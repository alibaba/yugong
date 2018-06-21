package com.taobao.yugong.translator;

import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.IncrementOpType;
import com.taobao.yugong.common.model.record.IncrementRecord;
import com.taobao.yugong.common.model.record.Record;

import org.junit.Test;

import java.sql.Types;
import java.util.List;

import static org.junit.Assert.*;

public class UserRouterMapMobileShardingTranslatorTest {
  @Test
  public void newMobileNumRecord() throws Exception {
    UserRouterMapMobileShardingTranslator translator = new UserRouterMapMobileShardingTranslator();

    ColumnMeta isBindColumn = new ColumnMeta("IsBinded", Types.VARCHAR);
    ColumnMeta columnMobileNum = new ColumnMeta("MobileNum", Types.VARCHAR);
    ColumnMeta userIdColumn = new ColumnMeta("UserId", Types.INTEGER);
    List<ColumnValue> primaryKeys = Lists.newArrayList(
        new ColumnValue(userIdColumn, 123456)
    );
    List<ColumnValue> columns = Lists.newArrayList(
        new ColumnValue(columnMobileNum, "18612345678"),
        new ColumnValue(isBindColumn, true)
    );
    Record record = new Record();
    record.setTableName("User");
    record.setPrimaryKeys(primaryKeys);
    record.setColumns(columns);
    
    assertEquals("+86 18612345678",
        translator.newMobileNumRecord(record).get().getColumnByName("Content").getValue());
  }

  @Test
  public void newMobileNumRecordCdc() throws Exception {
    UserRouterMapMobileShardingTranslator translator = new UserRouterMapMobileShardingTranslator();

    ColumnMeta isBindColumn = new ColumnMeta("IsBinded", Types.VARCHAR);
    ColumnMeta columnMobileNum = new ColumnMeta("MobileNum", Types.VARCHAR);
    ColumnMeta userIdColumn = new ColumnMeta("UserId", Types.INTEGER);
    List<ColumnValue> primaryKeys = Lists.newArrayList(
        new ColumnValue(userIdColumn, 123456)
    );
    List<ColumnValue> columns = Lists.newArrayList(
        new ColumnValue(columnMobileNum, "18612345678"),
        new ColumnValue(isBindColumn, true)
    );
    IncrementRecord record = new IncrementRecord();
    record.setTableName("User");
    record.setPrimaryKeys(primaryKeys);
    record.setColumns(columns);
    record.setOpType(IncrementOpType.I);

    assertEquals("+86 18612345678",
        translator.newMobileNumRecordCdc(record).get(0).getColumnByName("Content").getValue());
  }

}