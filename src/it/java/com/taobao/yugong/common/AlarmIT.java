package com.taobao.yugong.common;

import com.taobao.yugong.common.alarm.AlarmMessage;
import com.taobao.yugong.common.alarm.MailAlarmService;

import org.junit.Test;

public class AlarmIT {

  @Test
  public void testEmail() {
    MailAlarmService alarm = new MailAlarmService();
    alarm.setEmailHost("smtp.163.com");
    alarm.setEmailUsername("test@163.com");
    alarm.setEmailPassword("test");
    alarm.start();

    AlarmMessage message = new AlarmMessage("this is ljh test; next line", "test@163.com");
    alarm.sendAlarm(message);
  }
}
