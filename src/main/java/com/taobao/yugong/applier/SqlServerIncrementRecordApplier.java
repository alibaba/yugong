package com.taobao.yugong.applier;

import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.sql.MysqlSqlTemplate;
import com.taobao.yugong.common.db.sql.OracleSqlTemplate;
import com.taobao.yugong.common.db.sql.SqlServerTemplate;
import com.taobao.yugong.common.db.sql.SqlTemplate;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.model.record.SqlServerIncrementRecord;
import com.taobao.yugong.common.utils.YuGongUtils;
import com.taobao.yugong.exception.YuGongException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import java.util.List;


public class SqlServerIncrementRecordApplier extends AbstractRecordApplier {

  protected YuGongContext context;
  private SqlTemplate sqlTemplate;
  private DbType dbType;

  public SqlServerIncrementRecordApplier(YuGongContext context) {
    this.context = context;
  }

  @Override
  public void start() {
    super.start();
    dbType = YuGongUtils.judgeDbType(context.getTargetDs());
    if (dbType == DbType.MYSQL) {
      sqlTemplate = new MysqlSqlTemplate();
    } else if (dbType == DbType.ORACLE) {
      sqlTemplate = new OracleSqlTemplate();
    } else if (dbType == DbType.SQL_SERVER) {
      sqlTemplate = new SqlServerTemplate();
    } else {
      throw new YuGongException("Unsupported dbtype: " + dbType);
    }
  }

  @Override
  public void stop() {
    super.stop();
  }

  @Override
  public void apply(List<Record> records) throws YuGongException {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getTargetDs());
    for (Record record : records) {
      if (!(record instanceof SqlServerIncrementRecord)) {
        logger.info("Record {} is not SqlServerIncrementRecord", record);
        continue;
      }
      applyOneByOne((SqlServerIncrementRecord)record, jdbcTemplate);
    }
  }

  private void applyOneByOne(SqlServerIncrementRecord records, JdbcTemplate jdbcTemplate) {
    String sql = "";
    switch (records.getOperation()) {
      case INSERT:
        sql = sqlTemplate.getInsertSql("", "", new String[]{"a"},
            Lists.newArrayList("").toArray(new String[]{}));
        break;
      case DELETE:
        sql = sqlTemplate.getDeleteSql("", "", new String[]{"a"});
        break;
      case UPDATE_OLD_VALUE:
        break;
      case UPDATE_NEW_VALUE:
        sql = sqlTemplate.getUpdateSql("", "", new String[]{"a"},
            Lists.newArrayList("").toArray(new String[]{}));
        break;
    }
    jdbcTemplate.execute(sql, (PreparedStatementCallback) ps-> {
      return null;
    });
    
  }
}
