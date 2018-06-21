package com.taobao.yugong.translator;

import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.Record;

import java.util.Arrays;
import java.util.List;

/**
 * 一个迁移的例子，提供源库一张表同步到目标多张表
 *
 * <pre>
 * 例子说明：
 * 1. 源库有一张表：yugong_example_two
 * 2. 目标库两张表：yugong_example_two_1,yugong_example_two_2,每张表都和yugong_example_two存在一个字段名不同，name字段分别映射到name_1,name_2
 *
 * 测试的表结构：
 * oralce:
 * create table yugong_example_two
 * (
 *       id NUMBER(11)  ,
 *       name  varchar(32) default ' ' not null,
 *       CONSTRAINT yugong_example_two_pk_id  PRIMARY   KEY (id)
 * );
 *
 * mysql:
 * create table test.yugong_example_two_1
 * (
 *       id bigint(20) unsigned auto_increment,
 *       name_1 varchar(32) ,
 *       CONSTRAINT yugong_example_two_1_pk_id  PRIMARY KEY (id)
 * );
 *
 *
 * create table test.yugong_example_two_2
 * (
 *       id bigint(20) unsigned auto_increment,
 *       name_2 varchar(32) ,
 *       CONSTRAINT yugong_example_two_2_pk_id  PRIMARY KEY (id)
 * );
 * </pre>
 *
 * @author agapple 2013-11-21 上午11:05:20
 */
public class YugongExampleTwoDataTranslator extends AbstractDataTranslator implements DataTranslator {

  @Override
  public List<Record> translator(List<Record> records) {
    List<Record> result = Lists.newArrayListWithCapacity(records.size());
    for (Record record : records) {
      result.addAll(translatorOne(record));
    }

    return result;
  }

  public List<Record> translatorOne(Record record) {
    Record record1 = record;
    Record record2 = record.clone();

    record1.setTableName("yugong_example_two_1");
    // 1. 字段名字不同
    ColumnValue name1Column = record.getColumnByName("name");
    if (name1Column != null) {
      name1Column
          .setColumn(new ColumnMeta("name_1", name1Column.getColumn().getType()));
    }

    record2.setTableName("yugong_example_two_2");
    // 1. 字段名字不同
    ColumnValue name2Column = record2.getColumnByName("name");
    if (name2Column != null) {
      name2Column
          .setColumn(new ColumnMeta("name_2", name2Column.getColumn().getType()));
    }
    return Arrays.asList(record1, record2);
  }

}
