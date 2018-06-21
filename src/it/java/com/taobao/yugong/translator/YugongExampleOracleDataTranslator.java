package com.taobao.yugong.translator;

import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.Record;

import org.apache.commons.lang.ObjectUtils;

import java.sql.Types;
import java.util.Date;

/**
 * 一个迁移的例子，涵盖一些基本转换操作
 *
 * <pre>
 * 例子包含特性：
 * 1. schema/table名不同. oracle中为yugong_example_oracle，mysql中为test.yugong_example_mysql
 * 2. 字段名字不同.  oracle中的name字段，映射到mysql的display_name
 * 3. 字段逻辑处理.  mysql的display_name字段数据来源为oracle库的:name+'('alias_name+')'
 * 4. 字段类型不同.  oracle中的amount为number类型，映射到mysql的amount为varchar文本型
 * 5. 源库多一个字段. oracle中多了一个alias_name字段
 * 6. 目标库多了一个字段. mysql中多了一个gmt_move字段，(简单的用迁移时的当前时间进行填充)
 *
 * 测试的表结构：
 * // oracle表
 * create table yugong_example_oracle
 * (
 *     id NUMBER(11)  ,
 *     name varchar2(32) ,
 *     alias_name  char(32) default ' ' not null,
 *     amount number(11,2),
 *     score  number(20),
 *     text_b blob,
 *     text_c clob,
 *     gmt_create date not null,
 *     gmt_modified date not null,
 *     CONSTRAINT yugong_example_oracle_pk_id  PRIMARY   KEY (id)
 * );
 *
 * // mysql表
 * create table test.yugong_example_mysql
 * (
 *     id bigint(20) unsigned auto_increment,
 *     display_name varchar(128) ,
 *     amount varchar(32),
 *     score bigint(20) unsigned ,
 *     text_b blob,
 *     text_c text,
 *     gmt_create timestamp not null,
 *     gmt_modified timestamp not null,
 *     gmt_move timestamp not null,
 *     CONSTRAINT yugong_example_mysql_pk_id  PRIMARY KEY (id)
 * );
 * </pre>
 *
 * @author agapple 2013-10-10 下午3:28:33
 */
public class YugongExampleOracleDataTranslator extends AbstractDataTranslator implements DataTranslator {

  public boolean translator(Record record) {
    // 1. schema/table名不同
    // record.setSchemaName("test");
    record.setTableName("yugong_example_mysql");

    // 2. 字段名字不同
    ColumnValue nameColumn = record.getColumnByName("name");
    if (nameColumn != null) {
      nameColumn.setColumn(new ColumnMeta("display_name", nameColumn.getColumn().getType()));
    }

    // 3. 字段逻辑处理
    ColumnValue aliasNameColumn = record.getColumnByName("alias_name");
    if (aliasNameColumn != null) {
      StringBuilder displayNameValue = new StringBuilder(64);
      displayNameValue.append(ObjectUtils.toString(nameColumn.getValue()))
          .append('(')
          .append(ObjectUtils.toString(aliasNameColumn.getValue()))
          .append(')');
      nameColumn.setValue(displayNameValue.toString());
    }

    // 4. 字段类型不同
    ColumnValue amountColumn = record.getColumnByName("amount");
    if (amountColumn != null) {
      amountColumn
          .setColumn(new ColumnMeta(amountColumn.getColumn().getName(), Types.VARCHAR));
      amountColumn.setValue(ObjectUtils.toString(amountColumn.getValue()));
    }

    // 5. 源库多一个字段
    record.removeColumnByName("alias_name");

    // 6. 目标库多了一个字段
    ColumnMeta gmtMoveMeta = new ColumnMeta("gmt_move", Types.TIMESTAMP);
    ColumnValue gmtMoveColumn = new ColumnValue(gmtMoveMeta, new Date());
    gmtMoveColumn.setCheck(false);// 该字段不做对比
    record.addColumn(gmtMoveColumn);

    // ColumnValue text_c = record.getColumnByName("text_c");
    // try {
    // text_c.setValue(new String((byte[]) text_c.getValue(), "GBK"));
    // } catch (UnsupportedEncodingException e) {
    // e.printStackTrace();
    // }
    return super.translator(record);
  }
}
