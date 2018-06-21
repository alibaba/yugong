package com.taobao.yugong.positioner;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.taobao.yugong.common.model.position.IdPosition;
import com.taobao.yugong.common.model.position.Position;
import com.taobao.yugong.exception.YuGongException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于文件刷新的position实现
 *
 * <pre>
 * 策略：
 * 1. 先写内存，然后定时刷新数据到File
 * 2. 数据采取overwrite模式(只保留最后一次)
 * </pre>
 *
 * @author agapple 2013-9-22 下午3:37:13
 */
public class FileMixedRecordPositioner extends MemoryRecordPositioner implements RecordPositioner {

  private static final Logger logger = LoggerFactory.getLogger(FileMixedRecordPositioner.class);
  private static final Charset charset = Charset.forName("UTF-8");
  private File dataDir;
  private String dataFileName = "position.dat";
  private File dataFile;
  private ScheduledExecutorService executor;
  private long period = 100;                                                     // 单位ms

  private AtomicBoolean needFlush = new AtomicBoolean(false);
  private AtomicBoolean needReload = new AtomicBoolean(true);

  public void start() {
    super.start();

    Assert.notNull(dataDir);
    if (!dataDir.exists()) {
      try {
        FileUtils.forceMkdir(dataDir);
      } catch (IOException e) {
        throw new YuGongException(e);
      }
    }

    if (!dataDir.canRead() || !dataDir.canWrite()) {
      throw new YuGongException("dir[" + dataDir.getPath() + "] can not read/write");
    }

    dataFile = new File(dataDir, dataFileName);

    executor = Executors.newScheduledThreadPool(1);
    // 启动定时工作任务
    executor.scheduleAtFixedRate(new Runnable() {

      public void run() {
        try {
          // 定时将内存中的最新值刷到file中，多次变更只刷一次
          if (needFlush.compareAndSet(true, false)) {
            flushDataToFile(dataFile, getLatest());
          }
        } catch (Throwable e) {
          // ignore
          logger.error("period update position failed!", e);
        }
      }
    }, period, period, TimeUnit.MILLISECONDS);
  }

  public void stop() {
    super.stop();

    flushDataToFile(dataFile, super.getLatest());
    executor.shutdownNow();
  }

  public void persist(Position position) {
    needFlush.set(true);
    super.persist(position);
  }

  public Position getLatest() {
    if (needReload.compareAndSet(true, false)) {
      Position position = loadDataFromFile(dataFile);
      super.persist(position);
      return position;
    } else {
      return super.getLatest();
    }
  }

  // ============================ helper method ======================

  private void flushDataToFile(File dataFile, Position position) {
    if (position != null) {
      if(position instanceof IdPosition){
          ((IdPosition) position).setUpdateTimeString(DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
      }
      String json = JSON.toJSONString(position,
          SerializerFeature.WriteClassName,
          SerializerFeature.WriteNullListAsEmpty);
      try {
          FileUtils.writeStringToFile(dataFile, json);
      } catch (IOException e) {
        throw new YuGongException(e);
      }
    }
  }

  private Position loadDataFromFile(File dataFile) {
    try {
      if (!dataFile.exists()) {
        return null;
      }

      String json = FileUtils.readFileToString(dataFile, charset.name());
      return JSON.parseObject(json, Position.class);
    } catch (IOException e) {
      throw new YuGongException(e);
    }
  }

  public void setDataDir(File dataDir) {
    this.dataDir = dataDir;
  }

  public void setDataFileName(String dataFileName) {
    this.dataFileName = dataFileName;
  }

  public void setPeriod(long period) {
    this.period = period;
  }

}
