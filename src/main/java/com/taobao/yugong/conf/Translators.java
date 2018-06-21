package com.taobao.yugong.conf;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Data;

import java.util.List;
import java.util.Map;

@JsonNaming(value = PropertyNamingStrategy.SnakeCaseStrategy.class)
@Data
public class Translators {
  Map<String, List<TranslatorConf>> table;
  Map<String, List<TranslatorConf>> record;
}
