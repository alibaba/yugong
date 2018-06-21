package com.taobao.yugong.translator;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.translator.core.TranslatorRegister;

import lombok.Getter;
import lombok.Setter;

import java.util.stream.Collectors;

/**
 * contains in {@link NameStyleDataTranslator}
 */
@Deprecated
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class NameTableMetaTranslator implements TableMetaTranslator {

  @Getter
  @Setter
  private CaseFormat columnCaseFormatFrom;
  @Getter
  @Setter
  private CaseFormat columnCaseFormatTo;
  @Getter
  @Setter
  private CaseFormat tableCaseFormatFrom;
  @Getter
  @Setter
  private CaseFormat tableCaseFormatTo;

  @VisibleForTesting
  protected String tableCaseConvert(String input) {
    if (tableCaseFormatFrom == null || tableCaseFormatTo == null) {
      return input;
    }
    return tableCaseFormatFrom.to(tableCaseFormatTo, input);
  }

  @VisibleForTesting
  protected String columnCaseConvert(String input) {
    if (columnCaseFormatFrom == null || columnCaseFormatTo == null) {
      return input;
    }
    if (columnCaseFormatFrom == CaseFormat.UPPER_CAMEL) {
      input = input.replace("ID", "Id"); // ugly solution
    }
    return columnCaseFormatFrom.to(columnCaseFormatTo, input);
  }

  @Override
  public void translator(Table table) {
    // TODO 确认使用新建模式还是洋葱模式？
    //    Table newTable = SerializationUtils.clone(table);
    table.setName(tableCaseConvert(table.getName()));
    table.setColumns(table.getColumns().stream()
        .map(x -> new ColumnMeta(columnCaseConvert(x.getName()),
            x.getType())).collect(Collectors.toList()));
    
  }
}
