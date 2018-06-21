package com.taobao.yugong.translator.core;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.CaseFormat;
import com.taobao.yugong.conf.TranslatorConf;
import com.taobao.yugong.translator.DataTranslator;
import com.taobao.yugong.translator.NameStyleDataTranslator;
import com.taobao.yugong.translator.NameTableMetaTranslator;
import com.taobao.yugong.translator.TableMetaTranslator;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Assert;
import org.junit.Test;

public class TranslatorRegisterTest {
  
  @Test
  public void newTableMetaTranslator() throws Exception {
    String rawYaml = "class: com.taobao.yugong.translator.NameTableMetaTranslator\n"
        + "properties:\n"
        + "  column_case_format_from: UPPER_CAMEL\n"
        + "  column_case_format_to: LOWER_UNDERSCORE\n"
        + "  table_case_format_from: UPPER_CAMEL\n"
        + "  table_case_format_to: LOWER_UNDERSCORE\n";
    YAMLMapper mapper = new YAMLMapper();
    TranslatorConf conf = mapper.readValue(rawYaml, TranslatorConf.class);
    TableMetaTranslator translator = TranslatorRegister.newTableMetaTranslator(conf);
    Assert.assertThat(translator, new IsInstanceOf(NameTableMetaTranslator.class));
      Assert.assertEquals(CaseFormat.UPPER_CAMEL,
          ((NameTableMetaTranslator)translator).getColumnCaseFormatFrom());
      Assert.assertEquals(CaseFormat.LOWER_UNDERSCORE,
          ((NameTableMetaTranslator)translator).getTableCaseFormatTo());
  }

  @Test
  public void newDataTranslator() throws Exception {
    String rawYaml = "class: com.taobao.yugong.translator.NameStyleDataTranslator\n"
        + "properties:\n"
        + "  column_case_format_from: UPPER_CAMEL\n"
        + "  column_case_format_to: LOWER_UNDERSCORE\n"
        + "  table_case_format_from: UPPER_CAMEL\n"
        + "  table_case_format_to: LOWER_UNDERSCORE\n";
    YAMLMapper mapper = new YAMLMapper();
    TranslatorConf conf = mapper.readValue(rawYaml, TranslatorConf.class);
    DataTranslator translator = TranslatorRegister.newDataTranslator(conf);
    Assert.assertThat(translator, new IsInstanceOf(NameStyleDataTranslator.class));
    Assert.assertEquals(CaseFormat.UPPER_CAMEL,
        ((NameStyleDataTranslator)translator).getColumnCaseFormatFrom());
    Assert.assertEquals(CaseFormat.LOWER_UNDERSCORE,
        ((NameStyleDataTranslator)translator).getTableCaseFormatTo());
  }

}