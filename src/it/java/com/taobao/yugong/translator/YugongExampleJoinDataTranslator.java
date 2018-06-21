package com.taobao.yugong.translator;

import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.Record;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

/**
 * 一个迁移的例子，提供源库多张表join的例子
 *
 * <pre>
 * 例子说明：
 * 1. 源库有两张表：yugong_example_join和name_join，两张表为1:1模型，通过 yugong_example_join.id =
 * name_join.join_id进行关联
 * 2. 目标库一张表：yugong_example_join
 *
 * 测试的表结构：
 * oralce:
 * create table yugong_example_join
 * (
 *     id NUMBER(11)  ,
 *     alias_name  char(32) default ' ' not null,
 *     CONSTRAINT yugong_example_join_pk_id  PRIMARY   KEY (id)
 * );
 *
 * create table join_name
 * (
 *     id NUMBER(11)  ,
 *     name  char(32) default ' ' not null,
 *     join_id NUMBER(11)  ,
 *     CONSTRAINT join_name_join_pk_id  PRIMARY   KEY (id)
 * );
 *
 * mysql：
 * create table test.yugong_example_join
 * (
 *     id bigint(20) unsigned auto_increment,
 *     name varchar(32) ,
 *     alias_name varchar(32),
 *     CONSTRAINT yugong_example_join_pk_id  PRIMARY KEY (id)
 * );
 * </pre>
 *
 * @author agapple 2013-11-21 上午11:05:20
 */
public class YugongExampleJoinDataTranslator extends BackTableDataTranslator implements DataTranslator {

  public boolean translator(DataSource sourceDs, Record record) {
    ColumnValue idColum = record.getColumnByName("id");
    if (idColum != null) {
      // 需要根据test.id字段，和另一张表做join，提取到关联id记录的name字段，合并输出到一个目标表
      JdbcTemplate jdbcTemplate = new JdbcTemplate(sourceDs);
      String name_value = (String) jdbcTemplate.query("select NAME FROM JOIN_NAME WHERE JOIN_ID = "
          + idColum.getValue().toString(), new ResultSetExtractor() {

        public Object extractData(ResultSet rs) throws SQLException, DataAccessException {
          if (rs.next()) {
            return rs.getString("NAME");
          }

          return null;
        }
      });

      ColumnMeta nameMeta = new ColumnMeta("name", Types.VARCHAR);
      ColumnValue nameColumn = new ColumnValue(nameMeta, name_value);
      record.addColumn(nameColumn);
    }
    return super.translator(record);
  }
}
