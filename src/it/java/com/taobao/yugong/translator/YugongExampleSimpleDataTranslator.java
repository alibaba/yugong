package com.taobao.yugong.translator;

/**
 * 一个简易转换逻辑的写法,包含常用的转换处理
 *
 * @author agapple 2016年6月7日 上午12:40:45
 * @since 1.0.3
 * use {@link ColumnFixDataTranslator}
 */
@Deprecated
public class YugongExampleSimpleDataTranslator extends AbstractDataTranslator {

  public YugongExampleSimpleDataTranslator() {
    ColumnTranslator translator = new ColumnTranslator();
    // 包含指定的字段的配置
    translator.include("id", "name", "amount", "score", "gmt_create", "gmt_modified");

    // 去掉指定的字段的配置
    translator.exclude("alias_name");

    // 字段重命名的配置
    translator.alias("amount", "amount_alias");
    // 保存
    super.setTranslator(translator);
  }

  @Override
  public String translatorSchema() {
    return null;
  }

  @Override
  public String translatorTable() {
    return "yugong_example_drds";
  }
}
