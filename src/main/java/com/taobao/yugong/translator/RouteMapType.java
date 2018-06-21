package com.taobao.yugong.translator;

public enum RouteMapType {
//  USER_NAME("USER_NAME"),
//  EMAIL("EMAIL"),
//  MOBILE("MOBILE");
    USER_NAME(0),
    EMAIL(1),
    MOBILE(2);

  private final int value;

  RouteMapType(int value) {
    this.value = value;
  }

  public int getValue() {
    return this.value;
  }
}
