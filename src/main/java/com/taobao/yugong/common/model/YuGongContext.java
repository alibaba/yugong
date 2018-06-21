package com.taobao.yugong.common.model;

import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.model.position.Position;
import lombok.Data;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * yugong数据处理上下文
 *
 * @author agapple 2013-9-12 下午5:04:57
 */
@Data
public class YuGongContext {

  // 具体每张表的同步
  private Position lastPosition;                  // 最后一次同步的position记录
  private Table tableMeta;                     // 对应的meta
  private boolean ignoreSchema = false;  // 同步时是否忽略schema，oracle迁移到mysql可能schema不同，可设置为忽略

  // 全局共享
  private RunMode runMode;
  private int onceCrawNum;                   // 每次提取的记录数
  private int tpsLimit = 0;      // <=0代表不限制
  private DataSource sourceDs;                      // 源数据库链接
  private DataSource targetDs;                      // 目标数据库链接
  private boolean batchApply = false;
  private boolean skipApplierException = false;  // 是否允许跳过applier异常
  private String sourceEncoding = "UTF-8";
  private String targetEncoding = "UTF-8";
  private String[] ignorePkInspection;  //忽略源表pk检查的表，多表用英文逗号分隔
  private String mViewLogType = "";            // 创建物化视图日志的类型
  private Map<String, String[]> specifiedPks = new HashMap<>(); //每张表指定的主键或联合主键
  private Map<String, String[]> tablepks = new HashMap(); // 没有主键

  public YuGongContext cloneGlobalContext() {
    YuGongContext context = new YuGongContext();
    context.setRunMode(runMode);
    context.setBatchApply(batchApply);
    context.setSourceDs(sourceDs);
    context.setTargetDs(targetDs);
    context.setSourceEncoding(sourceEncoding);
    context.setTargetEncoding(targetEncoding);
    context.setOnceCrawNum(onceCrawNum);
    context.setTpsLimit(tpsLimit);
    context.setIgnoreSchema(ignoreSchema);
    context.setSkipApplierException(skipApplierException);
    context.setIgnorePkInspection(ignorePkInspection);
    context.setSpecifiedPks(specifiedPks);
    context.setMViewLogType(mViewLogType);
    context.setTablepks(tablepks);
    return context;
  }

}
