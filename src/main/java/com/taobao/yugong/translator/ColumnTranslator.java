package com.taobao.yugong.translator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.utils.YuGongUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.sql.Types;
import java.util.*;

@Slf4j
public class ColumnTranslator implements RecordTranslator {

  @Setter
  protected Collection<String> includeColumns = new HashSet<>();
  @Getter
  @Setter
  protected Collection<String> excludeColumns = new HashSet<>();
  @Setter
  protected Collection<String> checked = new HashSet<>();
  @Setter
  protected Collection<String> notChecked = new HashSet<>();
  @Setter
  @Getter
  protected Map<String, Set<String>> columnAlias = new HashMap<>();
  @Setter
  @Getter
  protected Map<String, String> columnReplace = Maps.newHashMap();
  @Setter
  @Getter
  protected Map<String, Map<String, Object>> newColumns = Maps.newHashMap();
  @Setter
  @Getter
  protected Map<String, Map<String, Object>> defaultColumns = Maps.newHashMap();
  @Setter
  @Getter
  protected Map<String, List<String>> jsonExtract = Maps.newHashMap();
  @Setter
  @Getter
  protected Map<String, Object> jsonExtractParam = Maps.newHashMap();
  @Setter
  @Getter
  protected Map<String, List<String>> jsonCompress = Maps.newHashMap();

  @VisibleForTesting
  protected static ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.setDateFormat(new ISO8601DateFormat());
    objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
  }


  /**
   * 包含的同步列
   */
  public ColumnTranslator include(String... columns) {
    //      includeColumns.add(StringUtils.upperCase(column));
    includeColumns.addAll(Arrays.asList(columns));
    return this;
  }

  /**
   * 排除的同步列
   */
  public ColumnTranslator exclude(String... columns) {
    //      excludeColumns.add(StringUtils.upperCase(column));
    excludeColumns.addAll(Arrays.asList(columns));
    return this;
  }

  /**
   * 需要检查的字段
   */
  public ColumnTranslator check(String... columns) {
    for (String column : columns) {
      checked.add(StringUtils.upperCase(column));
    }
    return this;
  }

  /**
   * 不需要检查的字段
   */
  public ColumnTranslator notCheck(String... columns) {
    for (String column : columns) {
      notChecked.add(StringUtils.upperCase(column));
    }
    return this;
  }

  /**
   * 别名定义
   */
  public ColumnTranslator alias(String srcColumn, String targetColumn) {
    String sourceColumn = StringUtils.upperCase(srcColumn);
    Set<String> targetColumnSet = columnAlias.computeIfAbsent(sourceColumn, k -> new HashSet<>(2));
    targetColumnSet.add(StringUtils.upperCase(targetColumn));
    return this;
  }

  public Record translator(Record record) {

    for (Map.Entry<String, List<String>> entry : jsonCompress.entrySet()) {
      Map<String, Object> jsonMap = Maps.newHashMap();
      for (String column : entry.getValue()) {
        jsonMap.put(column, record.getColumnByName(column).getValue());
        record.removeColumnByName(column);
      }
      String json;
      try {
        json = objectMapper.writeValueAsString(jsonMap);
      } catch (IOException e) {
        log.warn("JSON Write error", e);
        continue;
      }
      ColumnValue columnValue = new ColumnValue();
      // ugly judge
      columnValue.setColumn(new ColumnMeta(entry.getKey(), Types.VARCHAR));
      columnValue.setValue(json);
      record.getColumns().add(columnValue);
    }

    for (Map.Entry<String, List<String>> entry : jsonExtract.entrySet()) {
      ColumnValue column = record.getColumnByName(entry.getKey());
      if (column == null) {
        continue;
      }
      String jsonValue = (String) column.getValue();
      Map<String, Object> map;
      try {
        map = objectMapper.readValue(jsonValue, Map.class);
      } catch (IOException e) {
        log.warn("JSON Read error", e);
        map = Maps.newHashMap();
      }
      for (Map.Entry<String, Object> columnMap : map.entrySet()) {
        ColumnValue columnValue = new ColumnValue();
        // ugly judge
        String name = columnMap.getKey();
        if (jsonExtractParam.get("case_format_from") != null
            && jsonExtractParam.get("case_format_to") != null) {
          name = YuGongUtils.ofCaseFormat((String) jsonExtractParam.get("case_format_from"))
              .to(YuGongUtils.ofCaseFormat((String) jsonExtractParam.get("case_format_to")), name);
          Map<String, String> replaceMap = (Map<String, String>) this.jsonExtractParam
              .getOrDefault("replace", Maps.newHashMap());
          for (Map.Entry<String, String> replaceEntry : replaceMap.entrySet()) {
            name = name.replace(replaceEntry.getKey(), replaceEntry.getValue());
          }
          Map<String, String> aliasMap = (Map<String, String>) this.jsonExtractParam
              .getOrDefault("alias", Maps.newHashMap());
          if (aliasMap.containsKey(name)) {
            name = aliasMap.get(name);
          }
        }
        columnValue.setColumn(new ColumnMeta(name,
            columnMap instanceof Number ? Types.INTEGER : Types.VARCHAR));
        columnValue.setValue(columnMap.getValue());
        record.getColumns().add(columnValue);
      }
      record.removeColumnByName(entry.getKey());
    }

    if (excludeColumns != null && !excludeColumns.isEmpty()) {
      // 处理列排除
      for (String excludeColumn : excludeColumns) {
        record.removeColumnByName(excludeColumn, true);
      }
    }

    if (includeColumns != null && !includeColumns.isEmpty()) {
      // 检查一下所有列是否存在
      for (String name : includeColumns) {
        record.getColumnByName(name);
      }

      // 删除掉不需要的列
      List<ColumnValue> pks = record.getPrimaryKeys();
      List<ColumnValue> columns = record.getColumns();
      Set<String> allColumns = new HashSet<>();
      for (ColumnValue pk : pks) {
        allColumns.add(StringUtils.upperCase(pk.getColumn().getName()));
      }

      for (ColumnValue column : columns) {
        allColumns.add(StringUtils.upperCase(column.getColumn().getName()));
      }

      allColumns.removeAll(includeColumns);
      for (String name : allColumns) {
        record.removeColumnByName(name);
      }
    }

    if (notChecked != null && !notChecked.isEmpty()) {
      // 处理不检查列
      for (String notCheckColumn : notChecked) {
        ColumnValue column = record.getColumnByName(notCheckColumn);
        if (column != null) {
          column.setCheck(false);
        }
      }
    }

    if (checked != null && !checked.isEmpty()) {
      // 处理检查列
      for (String checkColumn : checked) {
        ColumnValue column = record.getColumnByName(checkColumn);
        if (column != null) {
          column.setCheck(true);
        }
      }
    }

    for (Map.Entry<String, Set<String>> entry : columnAlias.entrySet()) {
      String srcColumn = entry.getKey();
      Set<String> targetColumns = entry.getValue();

      ColumnValue column = record.getColumnByName(srcColumn);
      if (column != null && targetColumns.size() >= 1) {
        Iterator<String> iter = targetColumns.iterator();
        String columnName = iter.next();
        column.setColumn(new ColumnMeta(columnName, column.getColumn().getType()));
        if (iter.hasNext()) {
          ColumnValue newColumn = column.clone();
          newColumn.setColumn(new ColumnMeta(iter.next(), newColumn.getColumn().getType()));
          record.addColumn(newColumn);
        }
      }
    }

    for (Map.Entry<String, String> entry : columnReplace.entrySet()) {
      for (ColumnValue column : record.getColumns()) {
        String name = column.getColumn().getName().replace(entry.getKey(), entry.getValue());
        column.setColumn(new ColumnMeta(name, column.getColumn().getType()));
      }
      for (ColumnValue column : record.getPrimaryKeys()) {
        String name = column.getColumn().getName()
            .replace(entry.getKey(), entry.getValue());
        column.setColumn(new ColumnMeta(name, column.getColumn().getType()));
      }
    }

    for (Map.Entry<String, Map<String, Object>> entry : newColumns.entrySet()) {
      ColumnValue columnValue = new ColumnValue();
      columnValue.setColumn(new ColumnMeta(entry.getKey(), (Integer) entry.getValue().get("type")));
      columnValue.setValue(entry.getValue().get("value"));
      record.getColumns().add(columnValue);
    }
    //如果设置了defaultColumns，则根据配置的默认值来替换原来的null值
    for (Map.Entry<String, Map<String, Object>> entry : defaultColumns.entrySet()) {
      ColumnValue column = record.getColumnByName(entry.getKey());
      if (column.getValue() == null) {
         column.setValue(entry.getValue().get("value"));
      }
    }


    return record;
  }


}
