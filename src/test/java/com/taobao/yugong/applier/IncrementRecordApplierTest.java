package com.taobao.yugong.applier;

import com.google.common.collect.Lists;
import com.google.common.collect.MigrateMap;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.db.meta.Table;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.common.model.YuGongContext;
import com.taobao.yugong.common.model.record.IncrementOpType;
import com.taobao.yugong.common.model.record.IncrementRecord;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.sql.Types;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class IncrementRecordApplierTest {
  private IncrementRecord record;

  //  @InjectMocks

  @Before
  public void setUp() throws Exception {
    //MockitoAnnotations.initMocks(this);
  }

  private IncrementRecord newRecordInstance() {
    ColumnMeta columnId = new ColumnMeta("id", Types.INTEGER);
    ColumnMeta columnName = new ColumnMeta("name", Types.VARCHAR);
    ArrayList<ColumnValue> primaryKeys = Lists.newArrayList(
        new ColumnValue(columnId, 1));
    ArrayList<ColumnValue> columns = Lists.newArrayList(
        new ColumnValue(columnName, "Apple"));
    record = new IncrementRecord();
    record.setPrimaryKeys(primaryKeys);
    record.setColumns(columns);
    return record;
  }

  @Test
  public void getInsertSqlUnit() throws Exception {
    IncrementRecordApplier applier = Mockito.spy(new IncrementRecordApplier(new YuGongContext()));
    applier.targetDbType = DbType.MYSQL;

    applier.insertSqlCache = MigrateMap.makeMap();
    IncrementRecord record = newRecordInstance();
    record.setOpType(IncrementOpType.I);

    Mockito.doReturn(new Table(DbType.SQL_SERVER.name(), "schema", "table",
        record.getPrimaryKeys().stream().map(ColumnValue::getColumn).collect(Collectors.toList()),
        record.getColumns().stream().map(ColumnValue::getColumn).collect(Collectors.toList()))
    ).when(applier).tableMetaGeneratorGetTableMeta(Mockito.anyString(), Mockito.anyString());

    AbstractRecordApplier.TableSqlUnit sqlUnit = applier.getInsertSqlUnit(record);
    assertNotNull(sqlUnit);
    assertEquals("insert into schema.table(`name` , `id`) values (? , ?) on duplicate key update `name`=values(`name`) , `id`=values(`id`)",
        sqlUnit.applierSql);
  }

  @Test
  public void getUpdateSqlUnit() throws Exception {
    IncrementRecordApplier applier = Mockito.spy(new IncrementRecordApplier(new YuGongContext()));
    applier.targetDbType = DbType.SQL_SERVER;

    applier.updateSqlCache = MigrateMap.makeMap();
    IncrementRecord record = newRecordInstance();
    record.setOpType(IncrementOpType.U);

    Mockito.doReturn(new Table(DbType.SQL_SERVER.name(), "schema", "table",
        record.getPrimaryKeys().stream().map(ColumnValue::getColumn).collect(Collectors.toList()),
        record.getColumns().stream().map(ColumnValue::getColumn).collect(Collectors.toList()))
    ).when(applier).tableMetaGeneratorGetTableMeta(Mockito.anyString(), Mockito.anyString());

    AbstractRecordApplier.TableSqlUnit sqlUnit = applier.getUpdateSqlUnit(record);
    assertNotNull(sqlUnit);
    assertEquals("SET IDENTITY_INSERT [schema].dbo.[table] ON;\n"
            + "MERGE [schema].dbo.[table] AS target\n"
            + "USING (values (?, ?)) AS source (name, id)\n"
            + "ON target.id = source.id\n"
            + "WHEN MATCHED THEN\n"
            + "   UPDATE SET name = source.name\n"
            + "WHEN NOT MATCHED THEN\n"
            + "   INSERT (name, id) VALUES (source.name, source.id);",
        sqlUnit.applierSql);
  }
}
