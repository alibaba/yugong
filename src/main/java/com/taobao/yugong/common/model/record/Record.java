package com.taobao.yugong.common.model.record;

import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.utils.YuGongToStringStyle;
import com.taobao.yugong.exception.YuGongException;

import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.List;

/**
 * 代表一条记录
 *
 * @author agapple 2013-9-3 下午2:50:21
 * @since 3.0.0
 */
public class Record {
  //当目标库复合主键时，原库的主键信息
  private List<ColumnValue> sourcePkeys;
  private String schemaName;
  private String tableName;
  private List<ColumnValue> primaryKeys = Lists.newArrayList();
  private List<ColumnValue> columns = Lists.newArrayList();
  //for check mode only
  private List<String> checkCompositeKeys = Lists.newArrayList();

  private boolean enableCompositeIndexes;

  public Record() {

  }

  public Record(String schemaName, String tableName, List<ColumnValue> primaryKeys, List<ColumnValue> columns) {
    this.schemaName = schemaName;
    this.tableName = tableName;
    this.primaryKeys = primaryKeys;
    this.columns = columns;
  }

  public void addCheckCompositeKey(String key) {
    this.checkCompositeKeys.add(key);
  }

  /**
   * Gets checkCompositeKeys.
   *
   * @return Value of checkCompositeKeys.
   */
  public List<String> getCheckCompositeKeys() {
    return checkCompositeKeys;
  }

  /**
   * Sets new checkCompositeKeys.
   *
   * @param checkCompositeKeys New value of checkCompositeKeys.
   */
  public void setCheckCompositeKeys(List<String> checkCompositeKeys) {
    this.checkCompositeKeys = checkCompositeKeys;
  }

  /**
   * Sets new enableCompositeIndexes.
   *
   * @param enableCompositeIndexes New value of enableCompositeIndexes.
   */
  public void setEnableCompositeIndexes(boolean enableCompositeIndexes) {
    this.enableCompositeIndexes = enableCompositeIndexes;
  }

  /**
   * Gets enableCompositeIndexes.
   *
   * @return Value of enableCompositeIndexes.
   */
  public boolean isEnableCompositeIndexes() {
    return enableCompositeIndexes;
  }

  public List<ColumnValue> getPrimaryKeys() {
    return primaryKeys;
  }

  public void setPrimaryKeys(List<ColumnValue> primaryKeys) {
    this.primaryKeys = primaryKeys;
  }

  public void addPrimaryKey(ColumnValue primaryKey) {
    if (getColumnByName(primaryKey.getColumn().getName(), true) != null) {
      throw new YuGongException("dup column[" + primaryKey.getColumn().getName() + "]");
    }
    primaryKeys.add(primaryKey);
  }

  public List<ColumnValue> getColumns() {
    return columns;
  }

  public void addColumn(ColumnValue column) {
    if (getColumnByName(column.getColumn().getName(), true) != null) {
      throw new YuGongException("dup column[" + column.getColumn().getName() + "]");
    }
    columns.add(column);
  }

  /**
   * 建议直接使用getColumnByName
   */
  @Deprecated
  public ColumnValue getPrimaryKeyByName(String pkName) {
    return getPrimaryKeyByName(pkName, false);
  }

  /**
   * 建议直接使用getColumnByName
   */
  @Deprecated
  public ColumnValue getPrimaryKeyByName(String pkName, boolean returnNullNotExist) {
    for (ColumnValue pk : primaryKeys) {
      if (pk.getColumn().getName().equalsIgnoreCase(pkName)) {
        return pk;
      }
    }

    if (returnNullNotExist) {
      return null;
    } else {
      throw new YuGongException("not found column[" + pkName + "]");
    }
  }

  /**
   * 根据列名查找对应的字段信息(包括主键中的字段)
   */
  public ColumnValue getColumnByName(String columnName) {
    return getColumnByName(columnName, false);
  }

  /**
   * 根据列名查找对应的字段信息(包括主键中的字段)
   */
  public ColumnValue getColumnByName(String columnName, boolean returnNullNotExist) {
    for (ColumnValue column : columns) {
      if (column.getColumn().getName().equalsIgnoreCase(columnName)) {
        return column;
      }
    }

    for (ColumnValue pk : primaryKeys) {
      if (pk.getColumn().getName().equalsIgnoreCase(columnName)) {
        return pk;
      }
    }

    if (returnNullNotExist) {
      return null;
    } else {
      throw new YuGongException("not found column[" + columnName + "]");
    }
  }

  /**
   * 根据列名删除对应的字段信息(包括主键中的字段)
   */
  public ColumnValue removeColumnByName(String columnName) {
    return removeColumnByName(columnName, false);
  }

  /**
   * 根据列名删除对应的字段信息(包括主键中的字段)
   */
  public ColumnValue removeColumnByName(String columnName, boolean returnNullNotExist) {
    ColumnValue remove = null;
    for (ColumnValue pk : primaryKeys) {
      if (pk.getColumn().getName().equalsIgnoreCase(columnName)) {
        remove = pk;
        break;
      }
    }

    if (remove != null && this.primaryKeys.remove(remove)) {
      return remove;
    } else {
      for (ColumnValue column : columns) {
        if (column.getColumn().getName().equalsIgnoreCase(columnName)) {
          remove = column;
          break;
        }
      }

      if (remove != null && this.columns.remove(remove)) {
        return remove;
      }
    }

    if (returnNullNotExist) {
      return null;
    } else {
      throw new YuGongException("not found column[" + columnName + "] to remove");
    }
  }

  public void setColumns(List<ColumnValue> columns) {
    this.columns = columns;
  }

  public String getSchemaName() {
    return schemaName;
  }

  public void setSchemaName(String schemaName) {
    this.schemaName = schemaName;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public List<ColumnValue> getSourcePkeys() {
    return sourcePkeys;
  }

  public void setSourcePkeys(List<ColumnValue> sourcePkeys) {
    this.sourcePkeys = sourcePkeys;
  }

  public Record clone() {
    Record record = new Record();
    record.setTableName(this.tableName);
    record.setSchemaName(this.schemaName);
    record.setSourcePkeys(this.sourcePkeys);
    for (ColumnValue column : primaryKeys) {
      record.addPrimaryKey(column.clone());
    }

    for (ColumnValue column : columns) {
      record.addColumn(column.clone());
    }
    return record;
  }

  public void clone(Record record) {
    record.setTableName(this.tableName);
    record.setSchemaName(this.schemaName);
    record.setSourcePkeys(this.sourcePkeys);
    for (ColumnValue column : primaryKeys) {
      record.addPrimaryKey(column.clone());
    }

    for (ColumnValue column : columns) {
      record.addColumn(column.clone());
    }
  }

  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((columns == null) ? 0 : columns.hashCode());
    result = prime * result + ((primaryKeys == null) ? 0 : primaryKeys.hashCode());
    result = prime * result + ((schemaName == null) ? 0 : schemaName.hashCode());
    result = prime * result + ((tableName == null) ? 0 : tableName.hashCode());
    return result;
  }

  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Record other = (Record) obj;
    if (columns == null) {
      if (other.columns != null) return false;
    } else if (!columns.equals(other.columns)) return false;
    if (primaryKeys == null) {
      if (other.primaryKeys != null) return false;
    } else if (!primaryKeys.equals(other.primaryKeys)) return false;
    if (schemaName == null) {
      if (other.schemaName != null) return false;
    } else if (!schemaName.equals(other.schemaName)) return false;
    if (tableName == null) {
      if (other.tableName != null) return false;
    } else if (!tableName.equals(other.tableName)) return false;
    return true;
  }

  public String toString() {
    return ToStringBuilder.reflectionToString(this, YuGongToStringStyle.DEFAULT_STYLE);
  }



}
