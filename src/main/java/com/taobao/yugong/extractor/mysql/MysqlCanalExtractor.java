package com.taobao.yugong.extractor.mysql;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.model.ExtractStatus;
import com.taobao.yugong.common.model.ProgressStatus;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.position.Position;
import com.taobao.yugong.common.model.record.IncrementOpType;
import com.taobao.yugong.common.model.record.IncrementRecord;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.common.utils.SqlUtils;
import com.taobao.yugong.exception.YuGongException;
import com.taobao.yugong.extractor.sqlserver.AbstractSqlServerExtractor;

import org.joda.time.DateTime;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Not thread safe
 */
public class MysqlCanalExtractor extends AbstractMysqlExtractor {

  private final String schemaName;
  private final String tableName;
  private Table tableMeta;
  private List<ColumnMeta> primaryKeyMetas;
  private List<ColumnMeta> columnsMetas;
  private YuGongContext context;
  private String canalServerIp;
  private int canalServerPort;
  private String canalServerInstance;
  private CanalConnector connector;

  public MysqlCanalExtractor(YuGongContext context,
      String canalServerIp, int canalServerPort) {
    this.context = context;
    this.schemaName = context.getTableMeta().getSchema();
    this.tableName = context.getTableMeta().getName();
    this.canalServerIp = canalServerIp;
    this.canalServerPort = canalServerPort;
    this.canalServerInstance = this.schemaName + "_" + this.tableName;
    this.connector = CanalConnectors.newSingleConnector(
        new InetSocketAddress(canalServerIp, canalServerPort), canalServerInstance, "", "");
  }

  @Override
  public void start() {
    super.start();
    tableMeta = context.getTableMeta();
    primaryKeyMetas = tableMeta.getPrimaryKeys();
    columnsMetas = tableMeta.getColumns();
    tracer.update(context.getTableMeta().getFullName(), ProgressStatus.INCING);

    connector.connect();
    connector.subscribe();
//    connector.subscribe(String.format("%s\\.%s",
//        context.getTableMeta().getSchema(), context.getTableMeta().getName()));
  }

  @Override
  public List<Record> extract() {

//    logger.info("start {}, end {}", now, end);
    JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getSourceDs());
    List<IncrementRecord> records;
    records = fetchCanalRecord(jdbcTemplate, primaryKeyMetas,
        columnsMetas);
    if (records.size() == 0) {
      setStatus(ExtractStatus.CATCH_UP);
      tracer.update(context.getTableMeta().getFullName(), ProgressStatus.SUCCESS);
    }
    logger.info("size: {}, processed ids: {}", records.size(), Joiner.on(",").join(records.stream()
        .map(x -> Joiner.on("+").join(x.getPrimaryKeys().stream()
            .map(x1 -> x1.getValue().toString()).collect(Collectors.toList()))).collect(Collectors.toList())));

    return (List<Record>) (List<? extends Record>) records;
  }

  private List<IncrementRecord> fetchCanalRecord(JdbcTemplate jdbcTemplate,
      List<ColumnMeta> primaryKeyMetas, List<ColumnMeta> columnsMetas) {
    Message message = connector.getWithoutAck(10240, 1000L, TimeUnit.MILLISECONDS);
    long batchId = message.getId();
    int size = message.getEntries().size();
    if (batchId == -1 || size == 0) {
      return Lists.newArrayList();
    }

    List<IncrementRecord> records = message.getEntries().stream()
        .filter(x -> ImmutableList.of(
            CanalEntry.EventType.INSERT,
            CanalEntry.EventType.DELETE,
            CanalEntry.EventType.UPDATE
            ).contains(x.getHeader().getEventType())
        )
        .map(entry -> {
          CanalEntry.RowChange rowChange;
          try {
            rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
          } catch (InvalidProtocolBufferException e) {
            logger.error("parse entry failed, entry offset: {}", entry.getHeader().getLogfileOffset());
            throw new YuGongException("parse entry failed, stopped");
          }
          List<CanalEntry.RowData> rows = rowChange.getRowDatasList();
          // one entry contains multi row changes
          return rows.stream().map(rowData -> {
            List<ColumnValue> columnValues = Lists.newArrayList();
            List<ColumnValue> primaryKeys = Lists.newArrayList();
            List<CanalEntry.Column> effectsColumns;
            if (entry.getHeader().getEventType() == CanalEntry.EventType.INSERT ||
                entry.getHeader().getEventType() == CanalEntry.EventType.UPDATE) {
              effectsColumns =  rowData.getAfterColumnsList();
            } else { // DELETE
              effectsColumns =  rowData.getBeforeColumnsList();
            }
            Map<String, ColumnValue> columnMap = parseCanalRowDataList2ColumnValueMap(effectsColumns);

            for (ColumnMeta primaryKey : primaryKeyMetas) {
              if (!columnMap.containsKey(primaryKey.getName())) {
                logger.error("binlog pk should not be empty, column: {}", primaryKey.getName());
              }
              ColumnValue columnValue = columnMap.get(primaryKey.getName());
              primaryKeys.add(columnValue);
            }
            for (ColumnMeta columnMeta : columnsMetas) {
              if (!columnMap.containsKey(columnMeta.getName())) {
                logger.error("binlog column should not be empty, column: {}", columnMeta.getName());
              }
              ColumnValue columnValue = columnMap.get(columnMeta.getName());
              columnValues.add(columnValue);
            }

            Optional<IncrementOpType> operation = IncrementOpType.ofMysqlCancal(
                entry.getHeader().getEventType());

            return new IncrementRecord(
                context.getTableMeta().getSchema(),
                context.getTableMeta().getName(), primaryKeys, columnValues, operation.get());

          }).collect(Collectors.toList());
        })
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
    connector.ack(batchId);
    return records;
  }
  
  private Map<String, ColumnValue> parseCanalRowDataList2ColumnValueMap(
      List<CanalEntry.Column> columns) {
    return columns.stream().map(column -> {
      ColumnMeta columnMeta = new ColumnMeta(column.getName(), column.getSqlType());
      Object value = SqlUtils.stringToSqlValue(column.getValue(), columnMeta.getType(),
          false, false);
      return new ColumnValue(columnMeta, value);
    }).collect(Collectors.toMap(x -> x.getColumn().getName(), Function.identity()));
  }

  @Override
  public Position ack(List<Record> records) {
    return null;
  }

  @Override
  public void stop() {
    super.stop();
    tracer.update(context.getTableMeta().getFullName(), ProgressStatus.SUCCESS);
  }

}
