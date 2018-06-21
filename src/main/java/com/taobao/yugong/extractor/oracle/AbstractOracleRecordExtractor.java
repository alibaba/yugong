package com.taobao.yugong.extractor.oracle;

import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.utils.YuGongUtils;
import com.taobao.yugong.extractor.AbstractFullRecordExtractor;
import com.taobao.yugong.extractor.AbstractRecordExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public abstract class AbstractOracleRecordExtractor extends AbstractFullRecordExtractor {

  /**
   * 从oracle的resultset中得到value
   *
   * <pre>
   * 1. 对于DATE类型特殊处理成TIMESTAMP类型，否则复制过去会丢掉时间部分
   * 2.  如果为字符串类型，并且需要进行转码，那么进行编码转换。
   * </pre>
   */
  @Override
  public ColumnValue getColumnValue(ResultSet resultSet, String encoding, ColumnMeta colmnMeta)
      throws SQLException {
    ColumnValue columnValue = YuGongUtils.getColumnValue(resultSet, encoding, colmnMeta);
    // 使用clone对象，避免translator修改了引用
    return new ColumnValue(colmnMeta.clone(), columnValue);
  }
}
