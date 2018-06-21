package com.taobao.yugong.translator;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.model.record.Record;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import org.apache.commons.beanutils.BeanUtils;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Data
public class NameStyleDataTranslator extends AbstractDataTranslator {

  private String schemaTo;
  private String tableTo;
  private CaseFormat columnCaseFormatFrom;
  private CaseFormat columnCaseFormatTo;
  private CaseFormat tableCaseFormatFrom;
  private CaseFormat tableCaseFormatTo;

  public NameStyleDataTranslator() {
    ColumnTranslator translator = new ColumnTranslator();
    super.setTranslator(translator);
  }

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
      input = input.replace("IP", "Ip"); // ugly solution
    }
    return columnCaseFormatFrom.to(columnCaseFormatTo, input);
  }

  @Override
  public boolean translator(Record record) {
    if (!Strings.isNullOrEmpty(this.getTableTo())) {
      record.setTableName(this.getTableTo());
    } else {
      record.setTableName(tableCaseConvert(record.getTableName()));
    }
    record.getColumns().forEach(x -> {
      ColumnMeta meta = new ColumnMeta(columnCaseConvert(x.getColumn().getName()),
          x.getColumn().getType());
      x.setColumn(meta);
        }
    );
    record.getPrimaryKeys().forEach(x -> {
      ColumnMeta columnMeta = new ColumnMeta(columnCaseConvert(x.getColumn().getName()),
          x.getColumn().getType());
      x.setColumn(columnMeta);
    });
    if (!Strings.isNullOrEmpty(this.schemaTo)) {
      record.setSchemaName(this.schemaTo);
    }
    return super.translator(record);
  }
}
