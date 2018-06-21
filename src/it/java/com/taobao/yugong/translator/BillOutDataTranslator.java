package com.taobao.yugong.translator;

import com.taobao.yugong.common.model.record.Record;

/**
 * @author agapple 2014年2月25日 下午11:38:06
 * @since 1.0.0
 */
public class BillOutDataTranslator extends AbstractDataTranslator implements DataTranslator {

  public boolean translator(Record record) {
    record.setTableName("BILL_OUT_TEST");
    // 将分库键做为主键
    record.addPrimaryKey(record.removeColumnByName("REF_USER_ID"));
    return super.translator(record);
  }

}
