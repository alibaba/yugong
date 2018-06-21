package com.taobao.yugong;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.taobao.yugong.common.version.VersionInfo;
import com.taobao.yugong.conf.YugongConfiguration;
import com.taobao.yugong.controller.YuGongController;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.exception.ExceptionUtils;

import java.io.File;
import java.io.IOException;

/**
 * Single jar app
 */
@Slf4j
public class YugongApp {
  
  @Parameter(names = {"-c", "--config"}, converter = FileConverter.class, required = true)
  private File configFile;

  @Parameter(names = {"-y", "--yaml"}, converter = FileConverter.class, required = true)
  private File configYamlFile;
  
  private static YAMLMapper yamlMapper = new YAMLMapper();

  public static void main(String[] args) {
    YugongApp yugongApp = new YugongApp();

    JCommander.newBuilder().addObject(yugongApp).build().parse(args);
    PropertiesConfiguration config = new PropertiesConfiguration();
    try {
      config.load(yugongApp.configFile);
    } catch (ConfigurationException e) {
      log.error("Configuration load error: {}", yugongApp.configFile.getPath());
      System.exit(0);
      return;
    }
    YugongConfiguration yugongConfiguration;
    try {
      yugongConfiguration = yamlMapper.readValue(yugongApp.configYamlFile,
          YugongConfiguration.class);
    } catch (IOException e) {
      log.error("YAML configuration load error: {}", yugongApp.configYamlFile.getPath());
      System.exit(0);
      return;
    }

    try {
      run(config, yugongConfiguration);
    } catch (Throwable e) {
      log.error("## Something goes wrong when starting up the YuGong:\n{}",
          ExceptionUtils.getFullStackTrace(e));
    }
  }


  private static void run(PropertiesConfiguration config,
      YugongConfiguration yugongConfiguration) throws InterruptedException {
    final YuGongController controller = new YuGongController(config, yugongConfiguration);
    log.info("## start the YuGong.");
    controller.start();
    log.info("## the YuGong is running now ......");
    log.info(VersionInfo.getBuildVersion());
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      if (!controller.isStart()) {
        return;
      }
      try {
        log.info("## Stop the YuGong");
        controller.stop();
      } catch (Throwable e) {
        log.warn("## Something goes wrong when stopping YuGong:\n{}",
            ExceptionUtils.getFullStackTrace(e));
      } finally {
        log.info("## YuGong is down.");
      }
    }));
    controller.waitForDone();// 如果所有都完成，则进行退出
    Thread.sleep(3 * 1000); // 等待3s，清理上下文
    log.info("## stop the YuGong");
    if (controller.isStart()) {
      controller.stop();
    }
    log.info("## YuGong is down.");
  }
}
