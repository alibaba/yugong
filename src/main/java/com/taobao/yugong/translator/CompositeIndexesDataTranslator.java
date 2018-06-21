package com.taobao.yugong.translator;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.Record;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 处理复合索引的Translator
 * @author caobin
 * @version 1.0 2017.07.07
 * <p>
 *
 *     - class: com.taobao.yugong.translator.CompositeIndexesDataTranslator
 *       properties:
 *       # # targe field
 *       composite_indexes:
 *       - OrderID
 *       - ProductID
 *       - MultiProductID
 *
 * </p>
 */

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Data
public class CompositeIndexesDataTranslator extends AbstractDataTranslator {

    private List<String> compositeIndexes = new ArrayList<>();

    public CompositeIndexesDataTranslator() {
        ColumnTranslator translator = new ColumnTranslator();
        super.setTranslator(translator);
    }

    @Override
    public boolean translator(Record record) {

        if(!compositeIndexes.isEmpty()){
            //System.out.println(">>>" + record);
            //标记Record为联合索引处理
            record.setEnableCompositeIndexes(true);
            List<ColumnValue> primaryKeys = record.getPrimaryKeys();
            //将主键作为普通列处理
            record.getColumns().addAll(primaryKeys);
            //shilin 保存原有的主键
            List<ColumnValue> copyPrimaryKeys = new ArrayList<>();
            record.setSourcePkeys(copyPrimaryKeys);
            primaryKeys.stream().forEach(k -> {
                copyPrimaryKeys.add(k.clone());
            });
            //清空原有主键
            primaryKeys.clear();
            //重设索引键
            compositeIndexes.stream().forEach(k -> {
                ColumnValue columnValue = record.getColumnByName(k);
                primaryKeys.add(columnValue);
                record.getColumns().remove(columnValue);
                //for check mode only
                record.addCheckCompositeKey(k);
            });
        }

        return super.translator(record);
    }
}
