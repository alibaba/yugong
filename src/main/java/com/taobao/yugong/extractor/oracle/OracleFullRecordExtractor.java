package com.taobao.yugong.extractor.oracle;

import com.google.common.collect.ImmutableMap;
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
import com.taobao.yugong.extractor.AbstractFullRecordExtractor;
import com.taobao.yugong.extractor.FullContinueExtractor;

import org.apache.commons.lang.StringUtils;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * oracle单数字主键的提取
 *
 * @author agapple 2013-9-12 下午4:49:25
 */
public class OracleFullRecordExtractor extends AbstractOracleRecordExtractor {

  // private static final String FORMAT =
  // "select /*+index(t {0})*/ {1} from {2}.{3} t where {4} > ? and rownum <= ? order by {4} asc";
  private static final String FORMAT = "select * from (select {0} from {1}.{2} t where {3} > ? order by {3} asc) where rownum <= ?";
  private static final String MIN_PK_FORMAT = "select min({0}) from {1}.{2}";

  private static Map<String, Integer> PARAMETER_INDEX_MAP = ImmutableMap.of("id", 1, "limit", 2);

  public OracleFullRecordExtractor(YuGongContext context) {
    super();
    this.context = context;
  }

  @Override
  public void init() {
    super.init();
    this.parameterIndexMap = PARAMETER_INDEX_MAP;
    String primaryKey = context.getTableMeta().getPrimaryKeys().get(0).getName();
    String schemaName = context.getTableMeta().getSchema();
    String tableName = context.getTableMeta().getName();

    if (StringUtils.isEmpty(extractSql)) {
      // 获取索引
      // Map<String, String> index =
      // TableMetaGenerator.getTableIndex(context.getSourceDs(),
      // schemaName, tableName);
      String colStr = SqlTemplates.COMMON.makeColumn(context.getTableMeta().getColumnsWithPrimary());
      this.extractSql = new MessageFormat(FORMAT).format(new Object[]{colStr, schemaName, tableName, primaryKey});
      // logger.info("table : {} \n\t extract sql : {}",
      // context.getTableMeta().getFullName(), extractSql);
    }

    if (getMinPkSql == null && StringUtils.isNotBlank(primaryKey)) {
      this.getMinPkSql = new MessageFormat(MIN_PK_FORMAT).format(new Object[]{primaryKey, schemaName, tableName});
    }
  }
}
