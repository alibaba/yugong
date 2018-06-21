package com.taobao.yugong.common.model.record;

import com.taobao.yugong.common.db.meta.ColumnValue;

import lombok.Data;
import lombok.Getter;

import java.util.List;

// TODO delete
@Deprecated
@Data
public class SqlServerIncrementRecord extends IncrementRecord {

//  private byte[] startLsn;
//  private byte[] seqval;
  private CdcOperation operation;
//  private byte[] updateMask;

  public SqlServerIncrementRecord(String schemaName, String tableName,
      List<ColumnValue> primaryKeys, List<ColumnValue> columns, CdcOperation operation) {
    super(schemaName, tableName, primaryKeys, columns);
//    this.startLsn = startLsn;
//    this.seqval = seqval;
    this.operation = operation;
//    this.updateMask = updateMask;
  }

  public enum CdcOperation {
    DELETE(1),
    INSERT(2),
    UPDATE_OLD_VALUE(3),
    UPDATE_NEW_VALUE(4);

    @Getter
    private int value;

    CdcOperation(int value) {
      this.value = value;
    }
    public static CdcOperation of(int value) {
      for (CdcOperation operation : values()) {
        if (operation.value != value) {
          continue;
        }
        return operation;
      }
      return null;
    }
  }
  
}
