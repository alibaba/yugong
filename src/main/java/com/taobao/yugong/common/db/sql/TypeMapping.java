package com.taobao.yugong.common.db.sql;

import com.google.common.collect.ImmutableMap;
import com.taobao.yugong.common.model.DbType;
import com.taobao.yugong.exception.YuGongException;

import java.sql.Types;
import java.util.Map;
import java.util.stream.Collectors;

public class TypeMapping {
  private static Map<Integer, Integer> SQL_SERVER_TO_MYSQL = ImmutableMap.<Integer, Integer>builder()
      .put(Types.NVARCHAR, Types.VARCHAR)
      .put(Types.NCHAR, Types.VARCHAR)
      .put(Types.LONGNVARCHAR, Types.VARCHAR)
      .build();
  private static Map<Integer, Integer> MYSQL_TO_SQL_SERVER = ImmutableMap.<Integer, Integer>builder()
      .put(Types.VARCHAR, Types.NVARCHAR)
      .build();

  public static int map(DbType source, DbType target, int type) {
    if (source == DbType.SQL_SERVER && target == DbType.MYSQL) {
      return SQL_SERVER_TO_MYSQL.getOrDefault(type, type);
    } else if (source == DbType.MYSQL && target == DbType.SQL_SERVER) {
      return MYSQL_TO_SQL_SERVER.getOrDefault(type, type);
    }
    return type;
  }
}
