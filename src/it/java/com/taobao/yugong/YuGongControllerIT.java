package com.taobao.yugong;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Lists;
import com.taobao.yugong.conf.YugongConfiguration;
import com.taobao.yugong.controller.YuGongController;
import com.taobao.yugong.translator.ColumnFixDataTranslator;
import com.taobao.yugong.translator.NameTableMetaTranslator;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author agapple 2014年2月25日 下午11:38:06
 * @since 1.0.0
 */
public class YuGongControllerIT {

  @Test
  public void testSimple() throws Exception {
    PropertiesConfiguration config = new PropertiesConfiguration();
    config.load(YuGongControllerIT.class.getClassLoader().getResourceAsStream("yugong.properties"));
    
    YAMLMapper yamlMapper = new YAMLMapper();
    YugongConfiguration configuration = yamlMapper.readValue(
        new File("src/main/resources/yugong.yaml"), YugongConfiguration.class);

    YuGongController controller = new YuGongController(config, configuration);
    controller.start();
    controller.waitForDone();
    Thread.sleep(3 * 1000); // 等待3s，清理上下文
    controller.stop();
  }
  
  @Test
  public void getObjectViaYaml() throws ClassNotFoundException, IOException {
    String clazzName = "com.taobao.yugong.translator.NameTableMetaTranslator";
    String rawYaml = "column_case_format_from: UPPER_CAMEL\n"
        + "column_case_format_to: LOWER_UNDERSCORE\n"
        + "table_case_format_from: UPPER_CAMEL\n"
        + "table_case_format_to: LOWER_UNDERSCORE";
    YAMLMapper mapper = new YAMLMapper();
//    NameTableMetaTranslator translator = mapper.readValue(rawYaml, NameTableMetaTranslator.class);
    Class<NameTableMetaTranslator> nameTableMetaTranslatorClass = NameTableMetaTranslator.class;
    NameTableMetaTranslator translator = mapper.readValue(rawYaml, NameTableMetaTranslator.class);
    Assert.assertEquals(CaseFormat.UPPER_CAMEL, translator.getColumnCaseFormatFrom());
    Assert.assertEquals(CaseFormat.LOWER_UNDERSCORE, translator.getTableCaseFormatTo());
  }

  @Test
  public void getObjectViaYaml2() throws ClassNotFoundException, IOException {
    String rawYaml = "column_alias:\n"
        + "  BussinessID:\n"
        + "    - business_id";
    YAMLMapper mapper = new YAMLMapper();
    ColumnFixDataTranslator translator = mapper.readValue(rawYaml, ColumnFixDataTranslator.class);
    Assert.assertThat(translator, new IsInstanceOf(ColumnFixDataTranslator.class));
    Assert.assertEquals(1, translator.getColumnAlias().size());
    Assert.assertEquals("business_id", Lists.newArrayList(translator.getColumnAlias().get
        ("BussinessID")).get(0));
//    Assert.assertEquals(CaseFormat.LOWER_UNDERSCORE, translator.getTableCaseFormatTo());
  }
}
