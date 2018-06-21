package com.taobao.yugong.conf;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class YugongConfigurationIT {

  @Test
  public void testLoad() throws IOException {
    YAMLMapper yamlMapper = new YAMLMapper();
    YugongConfiguration configuration = yamlMapper.readValue(
        new File("src/main/resources/yugong.yaml"), YugongConfiguration.class);
    Assert.assertEquals(3, configuration.getTranslators().table.size());
    Assert.assertEquals("com.taobao.yugong.translator.NameTableMetaTranslator",
        configuration.getTranslators().table.get("*").get(0).getClazz());
  }
}