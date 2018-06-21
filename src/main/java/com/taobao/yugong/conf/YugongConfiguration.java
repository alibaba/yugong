package com.taobao.yugong.conf;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Data;

/**
 * Next generation for yugong configuration, based YAML
 */
@JsonNaming(value = PropertyNamingStrategy.SnakeCaseStrategy.class)
@Data
public class YugongConfiguration {
  private Datebases databases;
  private Table table;
  private Extractor extractor;
  private Appiler appiler;
  private Translators translators;
}
