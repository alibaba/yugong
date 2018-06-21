package com.taobao.yugong.common.db.meta;

/**
 * 代表一个具体字段的value
 *
 * @author agapple 2013-9-3 下午2:47:33
 * @since 1.0.0
 */
public class ColumnValue {

  private ColumnMeta column;
  private Object value;
  private boolean check = true; // 是否需要做数据对比

  public ColumnValue() {
  }

  public ColumnValue(ColumnMeta column, Object value) {
    this(column, value, true);
  }

  public ColumnValue(ColumnMeta column, Object value, boolean check) {
    this.value = value;
    this.column = column;
    this.check = check;
  }

  public ColumnMeta getColumn() {
    return column;
  }


  public void setColumn(ColumnMeta column) {
    this.column = column;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public boolean isCheck() {
    return check;
  }

  public void setCheck(boolean check) {
    this.check = check;
  }

  public ColumnValue clone() {
    ColumnValue column = new ColumnValue();
    column.setValue(this.value);
    column.setColumn(this.column.clone());
    return column;
  }

  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((column == null) ? 0 : column.hashCode());
    result = prime * result + ((value == null) ? 0 : value.hashCode());
    return result;
  }

  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ColumnValue other = (ColumnValue) obj;
    if (column == null) {
      if (other.column != null) return false;
    } else if (!column.equals(other.column)) return false;
    if (value == null) {
      if (other.value != null) return false;
    } else if (!value.equals(other.value)) return false;
    return true;
  }

  @Override
  public String toString() {
    return "ColumnValue [column=" + column + ", value=" + value + "]";
  }

}
