package com.taobao.yugong.common.db.meta;

import com.taobao.yugong.common.utils.YuGongToStringStyle;

import lombok.Data;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.sql.JDBCType;

/**
 * 代表一个字段的信息
 *
 * @author agapple 2013-9-3 下午2:46:32
 * @since 3.0.0
 */
@Data
public class ColumnMeta {

//  private String rawName;
  private final String name;
  private final int type;

  public ColumnMeta(String columnName, int columnType) {
//    this.rawName = columnName;
    this.name = columnName;
//    this.name = StringUtils.upperCase(columnName);// 统一为大写
    this.type = columnType;
  }


  public ColumnMeta clone() {
//    return new ColumnMeta(this.rawName, this.type);
    return new ColumnMeta(this.name, this.type);
  }

  public String toString() {
    return String.format("ColumnMeta[name=%s,type=%s]", this.name, JDBCType.valueOf(this.type));
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + type;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ColumnMeta other = (ColumnMeta) obj;
    if (name == null) {
      if (other.name != null) return false;
    } else if (!name.equals(other.name)) return false;
    if (type != other.type) return false;
    return true;
  }

}
