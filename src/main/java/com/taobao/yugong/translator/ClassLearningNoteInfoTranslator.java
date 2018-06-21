package com.taobao.yugong.translator;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.Record;

import java.sql.Types;
import java.util.List;


@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ClassLearningNoteInfoTranslator implements DataTranslator {

  public static final String IS_ACTIVE = "is_active";
  public static final String IS_DELETED = "is_deleted";
  public static final String SECOND_POINT = "second_point";
  public static final String IS_MEDIA = "is_media";

  @Override
    public String translatorSchema() {
        return null;
    }

    @Override
    public String translatorTable() {
        return null;
    }

    @Override
    public boolean translator(Record record) {
        return true;
    }

    @Override
    public List<Record> translator(List<Record> records) {
      records
          .forEach(
              record -> {
                  ColumnValue isActive = record.getColumnByName(IS_ACTIVE);
                  ColumnValue isDeleted = new ColumnValue();
                  ColumnMeta isDeletedMeta = new ColumnMeta(IS_DELETED, Types.BOOLEAN);
                  isDeleted.setColumn(isDeletedMeta);
                  isDeleted.setValue(!(Boolean) isActive.getValue());
                  record.addColumn(isDeleted);
                  record.removeColumnByName(IS_ACTIVE);

                  ColumnValue secondPoint = record.getColumnByName(SECOND_POINT);
                  ColumnValue isMedia = new ColumnValue();
                  ColumnMeta meta = new ColumnMeta(IS_MEDIA, Types.BOOLEAN);
                  isMedia.setColumn(meta);
                  if (secondPoint.getValue() == null || secondPoint.getValue().equals(-1)) {
                      isMedia.setValue(0);
                  } else {
                      isMedia.setValue(1);
                  }
                  record.addColumn(isMedia);
              });
        return records;
    }

}
