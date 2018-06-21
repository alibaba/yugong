package com.taobao.yugong.translator;

import com.google.common.collect.Lists;
import com.taobao.yugong.common.model.record.Record;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author agapple 2014年2月25日 下午11:38:06
 * @since 1.0.0
 */
public class AbstractDataTranslator implements DataTranslator {

  @Getter
  @Setter
  protected RecordTranslator translator;

  /**
   * 转换schemaName,如果返回null,则以每条数据Record的转义为准
   */
  public String translatorSchema() {
    return null;
  }

  /**
   * 转换tableName,如果返回null,则以每条数据Record的转义为准
   */
  public String translatorTable() {
    return null;
  }

  public boolean translator(Record record) {
    return true;
  }

  public List<Record> translator(List<Record> records) {
    List<Record> result = Lists.newArrayList();
    for (Record record : records) {
      String schema = translatorSchema();
      if (schema != null) {
        record.setSchemaName(schema);
      }

      String table = translatorTable();
      if (table != null) {
        record.setTableName(table);
      }

      if (translator != null) {
        record = translator.translator(record);
      }

      if (record != null && translator(record)) {
        result.add(record);
      }
    }

    return result;
  }

}
