package com.taobao.yugong.extractor.sqlserver;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.sql.SqlTemplates;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.extractor.AbstractFullRecordExtractor;
import org.apache.commons.lang3.ArrayUtils;

import java.sql.Types;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class SqlServerFullRecordExtractor extends AbstractFullRecordExtractor {

  public static final String CONVERT_PHYSLOC_TO_BIGINT = "CONVERT(BIGINT, {0})";

  private static final String PHYSLOC = "%%physloc%%";

  private static final String MIN_PK_FORMAT = "select min([{0}]) from {1}.dbo.[{2}]";
  private static final String MIN_PK_FORMAT_WHIHOUT_ESCAPE = "select min({0}) from {1}.dbo.[{2}]";

  private static final String DEFALT_EXTRACT_SQL_FORMAT =
      "select TOP (?) {0} from {1}.dbo.[{2}] where [{3}] > ? order by [{3}] asc;";

  public static final String DEFAULT_EXTRACT_COMPOSITE_INDEXS_SQL_FORMAT =
          "select TOP (?) {0}, _physloc_pk=CONVERT(BIGINT, " + PHYSLOC + ") from {1}.dbo.[{2}]  where CONVERT(BIGINT, " + PHYSLOC + ") > ? order by _physloc_pk asc";

  private static Map<String, Integer> PARAMETER_INDEX_MAP = ImmutableMap.of("id", 2, "limit", 1);

  public SqlServerFullRecordExtractor(YuGongContext context) {
    this.context = context;
  }

  @Override
  public void init() {
    super.init();
    //重新处理主键
    if(context.getIgnorePkInspection().length > 0
            && ArrayUtils.contains(context.getIgnorePkInspection(), context.getTableMeta().getName())){

      String schemaName = context.getTableMeta().getSchema();
      String tableName = context.getTableMeta().getName();

      this.parameterIndexMap = PARAMETER_INDEX_MAP;

      //TODO: 暂不支持extractSql的自定义
      if (!Strings.isNullOrEmpty(extractSql))throw new IllegalArgumentException("指定主键的模式不支持extractSql的自定义");

      String colStr = SqlTemplates.COMMON.makeColumn(context.getTableMeta().getColumnsWithPrimary());
      if (context.getSpecifiedPks().containsKey(tableName)
          && context.getSpecifiedPks().get(tableName).length == 1) {  // Defined one pk
        String definedPramiryKey = context.getSpecifiedPks().get(tableName)[0];
        this.extractSql = MessageFormat.format(
            DEFALT_EXTRACT_SQL_FORMAT,
            colStr,
            schemaName,
            tableName,
            definedPramiryKey
        );
        this.getMinPkSql = MessageFormat.format(
            MIN_PK_FORMAT_WHIHOUT_ESCAPE,
            definedPramiryKey,
            schemaName,
            tableName
        );

        //上下文主键替换
        List<ColumnMeta> pks = context.getTableMeta().getPrimaryKeys();
        context.getTableMeta().getColumns().addAll(pks);
        pks.clear();
        if (tableName.equals("Hujiangid_WXunionid") && definedPramiryKey.equals("UnionId")) {
          pks.add(new ColumnMeta(definedPramiryKey, Types.VARCHAR));
        } else {
          pks.add(new ColumnMeta(definedPramiryKey, Types.BIGINT));
        }
        context.getTableMeta().setColumns(
         context.getTableMeta().getColumns().stream()
             .filter(x -> !x.getName().equals(definedPramiryKey))
             .collect(Collectors.toList())
        );
      } else { // TODO use physloc in where will be slow, only command in small tables
        this.extractSql = MessageFormat.format(
            DEFAULT_EXTRACT_COMPOSITE_INDEXS_SQL_FORMAT,
            colStr,
            schemaName,
            tableName,
            getConvertedPhysloc()
        );
        this.getMinPkSql = MessageFormat.format(
            MIN_PK_FORMAT_WHIHOUT_ESCAPE,
            getConvertedPhysloc(),
            schemaName,
            tableName
        );

        //上下文主键替换
        List<ColumnMeta> pks = context.getTableMeta().getPrimaryKeys();
        context.getTableMeta().getColumns().addAll(pks);
        pks.clear();
        pks.add(new ColumnMeta("_physloc_pk", Types.BIGINT));
      }
    } else {
      String primaryKey = context.getTableMeta().getPrimaryKeys().get(0).getName();
      String schemaName = context.getTableMeta().getSchema();
      String tableName = context.getTableMeta().getName();
      this.getMinPkSql = MessageFormat.format(MIN_PK_FORMAT, primaryKey, schemaName, tableName);
      this.parameterIndexMap = PARAMETER_INDEX_MAP;

      if (Strings.isNullOrEmpty(extractSql)) {
        String colStr = SqlTemplates.SQL_SERVER.makeColumn(context.getTableMeta().getColumnsWithPrimary());
        this.extractSql = MessageFormat.format(DEFALT_EXTRACT_SQL_FORMAT, colStr, schemaName,
                tableName, primaryKey);
      }
    }
    queue = new LinkedBlockingQueue<>(context.getOnceCrawNum() * 2);
  }


  /**
   * 获取转换后的伪例
   * @return
   */
  private String getConvertedPhysloc(){
    return MessageFormat.format(CONVERT_PHYSLOC_TO_BIGINT, PHYSLOC);
  }
}
