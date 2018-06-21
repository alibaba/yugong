package com.taobao.yugong.translator;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ColumnFixDataTranslator extends AbstractDataTranslator {

  private final ColumnTranslator translator;

  public ColumnFixDataTranslator() {
    translator = new ColumnTranslator();
    super.setTranslator(translator);
  }

  public void setColumnAlias(Map<String, Set<String>> columnAlias) {
    this.translator.setColumnAlias(columnAlias);
  }

  public Map<String, Set<String>> getColumnAlias() {
    return this.translator.getColumnAlias();
  }

  public void setColumnReplace(Map<String, String> columnReplace) {
    this.translator.setColumnReplace(columnReplace);
  }

  public Map<String, String> getColumnReplace() {
    return this.translator.getColumnReplace();
  }

  public void setExcludeColumns(Collection<String> excludeAlias) {
    this.translator.setExcludeColumns(excludeAlias);
  }

  public Collection<String> getExcludeColumns() {
    return this.translator.getExcludeColumns();
  }

  public void setNewColumns(Map<String, Map<String, Object>> newColumns) {
    this.translator.setNewColumns(newColumns);
  }

  public Map<String, Map<String, Object>> getNewColumns() {
    return this.translator.getNewColumns();
  }
  public Map<String, Map<String, Object>> getDefaultColumns() {
    return this.translator.getDefaultColumns();
  }
  public void setDefaultColumns(Map<String, Map<String, Object>> defaultColumns) {
     this.translator.setDefaultColumns(defaultColumns);
  }

  public void setJsonExtract(Map<String, List<String>> jsonExtract) {
    this.translator.setJsonExtract(jsonExtract);
  }

  public Map<String, List<String>> getJsonExtract() {
    return this.translator.getJsonExtract();
  }

  public void setJsonCompress(Map<String, List<String>> jsonCompress) {
    this.translator.setJsonCompress(jsonCompress);
  }

  public Map<String, List<String>> getJsonCompress() {
    return this.translator.getJsonCompress();
  }

  public void setJsonExtractParam(Map<String, Object> jsonExtractParam) {
    this.translator.setJsonExtractParam(jsonExtractParam);
  }

  public Map<String, Object> getJsonExtractParam() {
    return this.translator.getJsonExtractParam();
  }

}

