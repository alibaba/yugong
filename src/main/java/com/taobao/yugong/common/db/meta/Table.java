package com.taobao.yugong.common.db.meta;

import com.google.common.collect.Lists;
import com.taobao.yugong.common.utils.YuGongToStringStyle;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.io.Serializable;
import java.util.List;

/**
 * 代表一张数据表
 *
 * @author agapple 2013-9-3 下午2:51:56
 * @since 3.0.0
 */
@Data
public class Table implements Serializable {

  private final String type;
  private final String schema;
  private String name;

  private List<ColumnMeta> primaryKeys = Lists.newArrayList();
  private List<ColumnMeta> columns = Lists.newArrayList();

  // 增量必带的扩展字段,比如DRDS模式下的拆分键
  private String extKey;

  public Table(String type, String schema, String name) {
    this.type = type;
    this.schema = schema;
    this.name = name;
  }

  public Table(String type, String schema, String name, List<ColumnMeta> primaryKeys, List<ColumnMeta> columns) {
    this.type = type;
    this.schema = schema;
    this.name = name;
    this.primaryKeys = primaryKeys;
    this.columns = columns;
  }

  public void addPrimaryKey(ColumnMeta primaryKey) {
    this.primaryKeys.add(primaryKey);
  }

  public void addColumn(ColumnMeta column) {
    this.columns.add(column);
  }

  /**
   * 返回所有字段信息，包括主键
   */
  public List<ColumnMeta> getColumnsWithPrimary() {
    List<ColumnMeta> result = Lists.newArrayList(primaryKeys);
    result.addAll(columns);
    return result;
  }

  public boolean isPrimaryKey(String columnName) {
    for (ColumnMeta col : primaryKeys) {
      if (StringUtils.equalsIgnoreCase(col.getName(), columnName)) {
        return true;
      }
    }

    return false;
  }

  /**
   * 返回schema.name
   */
  public String getFullName() {
    return schema + "." + name;
  }

  public String toString() {
    return ToStringBuilder.reflectionToString(this, YuGongToStringStyle.DEFAULT_STYLE);
  }

  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((schema == null) ? 0 : schema.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }

  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Table other = (Table) obj;
    if (name == null) {
      if (other.name != null) return false;
    } else if (!name.equals(other.name)) return false;
    if (schema == null) {
      if (other.schema != null) return false;
    } else if (!schema.equals(other.schema)) return false;
    if (type == null) {
      if (other.type != null) return false;
    } else if (!type.equals(other.type)) return false;
    return true;
  }

}
