package com.taobao.yugong.common.db;

import com.google.common.base.Joiner;
import com.taobao.yugong.common.audit.RecordDumper;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.exception.YuGongException;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 记录对比
 *
 * @author agapple 2013-9-29 下午2:56:39
 */
public class RecordDiffer {

  private static final String SEP = SystemUtils.LINE_SEPARATOR;
  private static String RECORD_FORMAT = null;
  private static final Logger diffLogger = LoggerFactory.getLogger("check");

  static {
    RECORD_FORMAT = SEP + "-----------------" + SEP;
    RECORD_FORMAT += "- Schema: {0} , Table: {1}" + SEP;
    RECORD_FORMAT += "-----------------" + SEP;
    RECORD_FORMAT += "---Pks" + SEP;
    RECORD_FORMAT += "{2}" + SEP;
    RECORD_FORMAT += "---diff" + SEP;
    RECORD_FORMAT += "\t{3}" + SEP;
  }

  /**
   * diff two record, ignore column order
   */
  public static String diff(Record record1, Record record2) {
    String message = "";
    if (record2 == null) {
      message = diffMessage(record1.getSchemaName(),
          record1.getTableName(),
          record1.getPrimaryKeys(),
          "record not found");
      diffLogger.info(message);
      return message;
    }

    if (record1.getColumns().size() > record2.getColumns().size()) {
      Set<String> columnNames = record1.getColumns().stream()
          .map(x -> x.getColumn().getName()).collect(Collectors.toSet());
      columnNames.removeAll(record2.getColumns().stream()
              .map(x -> x.getColumn().getName()).collect(Collectors.toSet()));
      message = diffMessage(record1.getSchemaName(),
          record1.getTableName(),
          record1.getPrimaryKeys(),
          "column size " + record1.getColumns().size() + " is great than target column size"
              + record2.getColumns().size() + ", more: " + Joiner.on(", ").join(columnNames));
      diffLogger.info(message);
      return message;
    }

    StringBuilder diff = new StringBuilder();
    boolean same = true;
    int size = record1.getColumns().size();

    for (int i = 0; i < size; i++) {
      ColumnValue column = record1.getColumns().get(i);
      same &= compareOneColumn(column, getColumn(record2, column.getColumn().getName()), diff);
    }

    if (!same) {
      message = diffMessage(record1.getSchemaName(),
          record2.getTableName(),
          record1.getPrimaryKeys(),
          diff.toString());
      diffLogger.info(message);
      return message;
    }
    return message;
  }

  private static boolean compareOneColumn(ColumnValue column1, ColumnValue column2,
      StringBuilder diff) {
    if (!column1.isCheck() || !column2.isCheck()) {
      return true;// 可能不需要做对比，忽略
    }

    Object value1 = column1.getValue();
    Object value2 = column2.getValue();
    if (value1 == null && value2 == null) {
      return true;
    }

    StringBuilder message = new StringBuilder();
    message.append(column1.getColumn())
        .append(" , values : [")
        .append(ObjectUtils.toString(value1 == null ? "null" : value1))
        .append("] vs [")
        .append(ObjectUtils.toString(value2 == null ? "null" : value2))
        .append("]");
    if (value1 != null && value2 != null && (value1.getClass() != value2.getClass())) {
      message.append(", type : [")
          .append(value1.getClass())
          .append("] vs [")
          .append(value2.getClass())
          .append("]");
    }
    message.append("\n\t");

    if ((value1 == null && value2 != null) || (value1 != null && value2 == null)) {
      diff.append(message);
      return false;
    }

    if (!value1.equals(value2)) {
      // custom define
      if (value1 instanceof java.util.Date && value2 instanceof java.util.Date) {
        boolean result = dateValueEquals((java.util.Date) value1, (java.util.Date) value2);
        if (!result) {
          String v1 = value1.toString();
          String v2 = value2.toString();
          // 2012-02-02 02:02:02 与 2012-02-02 肯定是一种包含关系
          if (v1.contains(v2) || v2.contains(v1)) {
            return true;
          }
          // ignore difference in 1 second
          if (Math.abs(((Date)value1).getTime() - ((Date) value2).getTime()) < 1000) {
            // TODO add configurable
            return true;
          }
        } else {
          return true;
        }
      } else if (Number.class.isAssignableFrom(value1.getClass())
          && Number.class.isAssignableFrom(value2.getClass())) {

        String v1 = null;
        String v2 = null;
        v1 = getNumberString(value1);
        v2 = getNumberString(value2);
        boolean result = v1.equals(v2);
        if (result) {
          return true;
        }
      } else if (value1.getClass().isArray() && value2.getClass().isArray()) {
        boolean result = true;
        if (Array.getLength(value1) == Array.getLength(value2)) {
          int length = Array.getLength(value1);
          for (int i = 0; i < length; i++) {
            result &= ObjectUtils.equals(Array.get(value1, i), Array.get(value2, i));
          }

          if (result) {
            return true;
          }
        }
      }

      // 其他情况为false
      diff.append(message);
      return false;
    } else {
      return true;
    }
  }

  private static String getNumberString(Object value1) {
    String v1;
    if (value1 instanceof BigDecimal) {
      v1 = ((BigDecimal) value1).toPlainString();
      if (StringUtils.indexOf(v1, ".") != -1) {
        v1 = StringUtils.stripEnd(v1, "0");// 如果0是末尾，则删除之
        v1 = StringUtils.stripEnd(v1, ".");// 如果.是末尾，则删除之
      }
    } else {
      v1 = value1.toString();
    }
    return v1;
  }

  private static boolean dateValueEquals(Date source, Date target) {
    return source.getTime() == target.getTime();
  }

  private static ColumnValue getColumn(Record record, String columnName) {
    for (ColumnValue column : record.getColumns()) {
      if (StringUtils.equalsIgnoreCase(columnName, column.getColumn().getName())) {
        return column;
      }
    }

    throw new YuGongException("column[" + columnName + "] is not found.");
  }

  private static String diffMessage(String schemaName, String tableName,
      List<ColumnValue> primaryKeys, String message) {
    return MessageFormat.format(RECORD_FORMAT,
        schemaName,
        tableName,
        RecordDumper.dumpRecordColumns(primaryKeys),
        message);
  }
}
