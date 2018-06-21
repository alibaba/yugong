package com.taobao.yugong.translator;

import com.taobao.yugong.common.YuGongConstants;
import com.taobao.yugong.common.audit.RecordDumper;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.utils.thread.ExecutorTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/**
 * 根据bill_out_id关联查询bill_out表获取数据
 *
 * @author agapple 2014-5-13 下午1:13:18
 */
public class MidBillOutDetailDataTranslator extends BackTableDataTranslator implements DataTranslator {

  private static final Logger ignoreLogger = LoggerFactory.getLogger("ignore");
  private int splitSize = 50;

  @Override
  public List<Record> translator(final DataSource sourceDs, final DataSource targetDs, final List<Record> records,
      final ExecutorTemplate template) {
    if (template != null) {
      final List<Record> result = Collections.synchronizedList(new ArrayList<Record>());
      final String spiltKey = MDC.get(YuGongConstants.MDC_TABLE_SHIT_KEY);
      if (records.size() > splitSize) {
        try {
          int index = 0;// 记录下处理成功的记录下标
          int size = records.size();
          // 全量复制时，无顺序要求，数据可以随意切割，直接按照splitSize切分后提交到多线程中进行处理
          for (; index < size; ) {
            int end = (index + splitSize > size) ? size : (index + splitSize);
            final List<Record> subList = records.subList(index, end);
            template.submit(new Runnable() {

              public void run() {
                MDC.put(YuGongConstants.MDC_TABLE_SHIT_KEY, spiltKey);
                result.addAll(doTranslator(sourceDs, subList));
              }
            });
            index = end;// 移动到下一批次
          }

          // 等待所有结果返回
          template.waitForResult();
        } finally {
          template.clear();
        }
      } else {
        result.addAll(doTranslator(sourceDs, records));
      }

      return result;
    } else {
      return super.translator(sourceDs, targetDs, records, template);
    }
  }

  public List<Record> doTranslator(DataSource sourceDs, List<Record> records) {
    // 构建in sql
    int size = records.size();
    StringBuilder builder = new StringBuilder("select BILL_OUT_ID,REF_USER_ID,TO_REF_USER_ID,BILL_TYPE FROM BILL_OUT WHERE BILL_OUT_ID in");
    builder.append("(");
    for (int i = 0; i < size; i++) {
      builder.append('?');
      if (i < size - 1) {
        builder.append(',');
      }
    }
    builder.append(')');

    Object args[] = new Object[size];
    int i = 0;
    for (Record record : records) {
      ColumnValue idColum = record.getColumnByName("BILL_OUT_ID");
      args[i++] = idColum.getValue();
    }

    JdbcTemplate jdbcTemplate = new JdbcTemplate(sourceDs);
    List datas = jdbcTemplate.query(builder.toString(), args, new ColumnMapRowMapper());
    Map<String, Map<String, Object>> joinDatas = new HashMap<String, Map<String, Object>>();
    for (Object data : datas) {
      Map map = (Map) data;
      Object number = map.get("BILL_OUT_ID");
      joinDatas.put(number.toString(), map);
    }

    List<Record> result = new ArrayList<Record>();
    i = 0;
    for (Record record : records) {
      record.setTableName("MID_BILL_OUT_DETAIL_TEST");
      Map map = joinDatas.get(args[i++].toString());
      if (map == null) {
        ignoreLogger.info("{}", RecordDumper.dumpRecord(record));
      } else {
        ColumnMeta refEntId = new ColumnMeta("REF_ENT_ID", Types.VARCHAR);
        ColumnValue refEntIdColumn = new ColumnValue(refEntId, map.get("REF_USER_ID"));
        record.addPrimaryKey(refEntIdColumn);

        ColumnMeta toRefUserId = new ColumnMeta("TO_REF_USER_ID", Types.VARCHAR);
        ColumnValue toRefUserIdColumn = new ColumnValue(toRefUserId, map.get("TO_REF_USER_ID"));
        record.addColumn(toRefUserIdColumn);

        ColumnMeta billType = new ColumnMeta("BILL_TYPE", Types.DECIMAL);
        ColumnValue billTypeColumn = new ColumnValue(billType, map.get("BILL_TYPE"));
        record.addColumn(billTypeColumn);
        result.add(record);
      }
    }

    return result;
  }

  @Override
  public boolean translator(DataSource sourceDs, Record record) {
    return true;
  }
}
