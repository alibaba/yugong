package com.taobao.yugong.extractor.oracle;

import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.Getter;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.db.sql.SqlTemplates;
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
import com.taobao.yugong.extractor.ContinueExtractor;

/**
 * oracle单数字主键的提取
 *
 * @author agapple 2013-9-12 下午4:49:25
 */
public class OracleFullRecordExtractor extends AbstractOracleRecordExtractor {

    // private static final String FORMAT =
    // "select /*+index(t {0})*/ {1} from {2}.{3} t where {4} > ? and rownum <= ? order by {4} asc";
    private static final String         FORMAT          = "select * from (select {0} from {1}.{2} t where {3} > ? order by {3} asc) where rownum <= ?";
    private static final String         MIN_PK_FORMAT   = "select min({0}) from {1}.{2}";
    private YuGongContext               context;
    private LinkedBlockingQueue<Record> queue;
    @Getter
    private String                      extractSql;
    private Thread                      extractorThread = null;
    @Getter
    private String                      getMinPkSql;

    public OracleFullRecordExtractor(YuGongContext context){
        this.context = context;
    }

    @Override
    public void start() {
        super.start();
        String primaryKey = context.getTableMeta().getPrimaryKeys().get(0).getName();
        String schemaName = context.getTableMeta().getSchema();
        String tableName = context.getTableMeta().getName();

        if (StringUtils.isEmpty(extractSql)) {
            // 获取索引
            // Map<String, String> index =
            // TableMetaGenerator.getTableIndex(context.getSourceDs(),
            // schemaName, tableName);
            String colStr = SqlTemplates.COMMON.makeColumn(context.getTableMeta().getColumnsWithPrimary());
            this.extractSql = new MessageFormat(FORMAT).format(new Object[] { colStr, schemaName, tableName, primaryKey });
            // logger.info("table : {} \n\t extract sql : {}",
            // context.getTableMeta().getFullName(), extractSql);
        }

        if (getMinPkSql == null && StringUtils.isNotBlank(primaryKey)) {
            this.getMinPkSql = new MessageFormat(MIN_PK_FORMAT).format(new Object[] { primaryKey, schemaName, tableName });
        }

        queue = new LinkedBlockingQueue<>(context.getOnceCrawNum() * 2);

        extractorThread = new NamedThreadFactory(this.getClass().getSimpleName() + "-"
                                                 + context.getTableMeta().getFullName()).newThread(new ContinueExtractor(this,
            context,
            queue));
        extractorThread.start();

        tracer.update(context.getTableMeta().getFullName(), ProgressStatus.FULLING);
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
                if (value instanceof Number) {
                    position.setId((Number) value);// 更新一下id
                }
            }
            return position;
        }

        return null;
    }

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

    public void setExtractSql(String extractSql) {
        // if (StringUtils.isNotEmpty(extractSql) &&
        // !StringUtils.contains(extractSql, "{3}")) {
        // throw new YuGongException("extracSql is not valid . eg :" + FORMAT);
        // }
        this.extractSql = extractSql;
    }
}
