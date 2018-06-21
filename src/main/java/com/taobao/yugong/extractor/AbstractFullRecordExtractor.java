package com.taobao.yugong.extractor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.ExtractStatus;
import com.taobao.yugong.common.model.ProgressStatus;
import com.taobao.yugong.common.model.RunMode;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.position.IdPosition;
import com.taobao.yugong.common.model.position.Position;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.utils.YuGongUtils;
import com.taobao.yugong.common.utils.thread.NamedThreadFactory;
import com.taobao.yugong.exception.YuGongException;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

// TODO convert to proxy
public abstract class AbstractFullRecordExtractor extends AbstractRecordExtractor {
  @Getter
  @Setter
  protected String getMinPkSql;
  @Getter
  @Setter
  protected String extractSql;
  @Getter
  protected Map<String, Integer> parameterIndexMap;
  protected YuGongContext context;
  @Getter
  protected Thread extractorThread;
  @Getter
  protected LinkedBlockingQueue<Record> queue;
  @VisibleForTesting
  @Getter
  private FullContinueExtractor fullContinueExtractor;

  public ColumnValue getColumnValue(ResultSet rs, String encoding, ColumnMeta col)
      throws SQLException {
    Object value = rs.getObject(col.getName().trim());
    return new ColumnValue(col.clone(), value);
  }
  
  public void init() { // TODO move to LifeCycle
  }

  @Override
  public void stop() {
    super.stop();

    extractorThread.interrupt();
    try {
      extractorThread.join(2 * 1000);
    } catch (InterruptedException e) {
      // ignore
    }
    tracer.update(context.getTableMeta().getFullName(), ProgressStatus.SUCCESS);
  }
  
  public void initContinueExtractor() {
    fullContinueExtractor = new FullContinueExtractor(this, context, queue);
  }

  @Override
  public void start() {
    super.start();
    this.init();
    this.initContinueExtractor();
    extractorThread = new NamedThreadFactory(
        this.getClass().getSimpleName() + "-" + context.getTableMeta().getFullName()
    ).newThread(fullContinueExtractor);
    extractorThread.start();
    tracer.update(context.getTableMeta().getFullName(), ProgressStatus.FULLING);
  }

  @Override
  public Position ack(List<Record> records) throws YuGongException {
    if (YuGongUtils.isNotEmpty(records)) {
      // 之前在选择Extractor时已经做过主键类型判断，一定是number类型
      Record record = records.get(records.size() - 1);
      IdPosition position = (IdPosition) context.getLastPosition();
      if (position == null || context.getRunMode() == RunMode.FULL) { // 如果是full模式，不记录历史
        position = new IdPosition();
      }
      position.setCurrentProgress(ProgressStatus.FULLING);
      List<ColumnValue> pks = record.getPrimaryKeys();
      if (YuGongUtils.isNotEmpty(pks)) {
        Object value = pks.get(0).getValue();
        //shilin 添加如果复合主键则获取原表 id
        if(pks.size()>1 && !CollectionUtils.isEmpty( record.getSourcePkeys())){
            value = record.getSourcePkeys().get(0).getValue();
        }

        if (value instanceof Number) {
          position.setId((Number) value);// 更新一下id
        }
      }
      return position;
    }

    return null;
  }

  /**
   * extract n record from queue data, n is {@link YuGongContext#getOnceCrawNum()}
   */
  public List<Record> extract() throws YuGongException {
    List<Record> records = Lists.newArrayListWithCapacity(context.getOnceCrawNum());
    for (int i = 0; i < context.getOnceCrawNum(); i++) {
      Record r = queue.poll();
      if (r != null) {
        records.add(r);
      } else if (status() == ExtractStatus.TABLE_END) {
        // 验证下是否已经结束了
        Record r1 = queue.poll();
        if (r1 != null) {
          records.add(r1);
        } else {
          // 已经取到低了，没有数据了
          break;
        }
      } else {
        // 没取到数据
        i--;
        continue;
      }
    }

    return records;
  }

}
