package com.com.example;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * 针对 jsjvm 中 java.util.Calendar 系统类实现的行为与边界测试。
 *
 * 覆盖范围：
 * - 静态工厂：getInstance()、getInstance(TimeZone)
 * - 时间相关：getTime()、getTimeInMillis()、setTime(Date)、setTimeInMillis(long)
 * - 时区相关：getTimeZone()、setTimeZone(TimeZone)
 * - 字段访问：get(int)、set(int,int)、computeFields()、computeTime()
 * - 比较与相等性：equals(Object)、before(Object)、after(Object)
 *
 * 所有验证均通过 System.out.println 打印标签化结果，只依赖公共 API，
 * 不访问或假设 Calendar 的内部实现细节。
 */
public class CalendarSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== constructors_and_timezones ===");
    testConstructorsAndTimeZones();

    System.out.println("=== time_getters_and_setters ===");
    testTimeGettersAndSetters();

    System.out.println("=== field_get_and_set ===");
    testFieldGetAndSet();

    System.out.println("=== equals_before_after ===");
    testEqualsBeforeAfter();

    System.out.println("=== compute_methods ===");
    testComputeMethods();
  }

  /**
   * 覆盖：
   * - Calendar.getInstance()
   * - Calendar.getInstance(TimeZone)
   * - Calendar.getTimeZone()
   * - Calendar.setTimeZone(TimeZone)
   * 并测试传入 null TimeZone 的边界行为（应抛出或忽略，行为由快照记录）。
   */
  private static void testConstructorsAndTimeZones() {
    // 基本 getInstance() 行为与时区信息
    Calendar c1 = Calendar.getInstance();
    TimeZone tz1 = c1.getTimeZone();
    System.out.println("c1_class=" + c1.getClass().getName());
    System.out.println("c1_tz_id=" + tz1.getID());
    System.out.println("c1_tz_rawOffset=" + tz1.getRawOffset());

    // 通过已有 TimeZone 调用 getInstance(TimeZone)，应复用该时区信息
    Calendar c2 = Calendar.getInstance(tz1);
    TimeZone tz2 = c2.getTimeZone();
    System.out.println("c2_tz_not_null=" + (tz2 != null));
    System.out.println("c2_tz_rawOffset=" + tz2.getRawOffset());

    // setTimeZone 应把 Calendar 的时区调整为给定 TimeZone
    Calendar c3 = Calendar.getInstance();
    TimeZone tz3Before = c3.getTimeZone();
    c3.setTimeZone(tz1);
    TimeZone tz3After = c3.getTimeZone();
    System.out.println("c3_tz_before_id=" + tz3Before.getID());
    System.out.println("c3_tz_after_id=" + tz3After.getID());
    System.out.println("c3_tz_after_rawOffset=" + tz3After.getRawOffset());

    // 边界：传入 null TimeZone，看 getInstance 的行为（通常期望使用默认时区或抛出异常）。
    try {
      Calendar cNull = Calendar.getInstance((TimeZone) null);
      System.out.println("getInstance_tz_null:NO_EXCEPTION_class=" + cNull.getClass().getName());
    } catch (NullPointerException e) {
      System.out.println("getInstance_tz_null:NullPointerException");
    } catch (Throwable t) {
      System.out.println("getInstance_tz_null:" + t.getClass().getName());
    }
  }

  /**
   * 覆盖：
   * - getTimeInMillis()
   * - getTime()
   * - setTimeInMillis(long)
   * - setTime(Date)
   * 并测试负时间戳与重复设置的覆盖情况。
   */
  private static void testTimeGettersAndSetters() {
    Calendar c = Calendar.getInstance();

    // 使用固定毫秒值，避免依赖系统当前时间。
    c.setTimeInMillis(123456789L);
    long millis1 = c.getTimeInMillis();
    Date d1 = c.getTime();
    System.out.println("setTimeInMillis_123456789_getTimeInMillis=" + millis1);
    System.out.println("setTimeInMillis_123456789_getTime=" + d1.getTime());

    // setTime(Date) 应该覆盖内部毫秒值
    Date d2 = new Date(7777777L);
    c.setTime(d2);
    long millis2 = c.getTimeInMillis();
    Date d3 = c.getTime();
    System.out.println("setTime_Date_7777777_getTimeInMillis=" + millis2);
    System.out.println("setTime_Date_7777777_getTime=" + d3.getTime());

    // 负毫秒值（1970 年之前时间）
    c.setTimeInMillis(-1000L);
    long millisNeg = c.getTimeInMillis();
    System.out.println("setTimeInMillis_negative_getTimeInMillis=" + millisNeg);

    // 多次连续 setTimeInMillis，验证没有溢出或状态混乱。
    c.setTimeInMillis(0L);
    c.setTimeInMillis(1L);
    c.setTimeInMillis(2L);
    System.out.println("setTimeInMillis_multiple_final=" + c.getTimeInMillis());
  }

  /**
   * 覆盖：
   * - get(int)
   * - set(int,int)
   * - computeFields()
   * - computeTime()
   *
   * 使用 Calendar.YEAR / MONTH / DATE / HOUR_OF_DAY / MINUTE / SECOND 等字段，
   * 测试 set 后 get 的一致性以及未知字段的返回值行为。
   */
  private static void testFieldGetAndSet() {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(0L); // 基线时间，避免当前时间干扰

    // 设置一组确定的字段值
    c.set(Calendar.YEAR, 2000);
    c.set(Calendar.MONTH, 1); // 2 月（0-based）
    c.set(Calendar.DATE, 15);
    c.set(Calendar.HOUR_OF_DAY, 10);
    c.set(Calendar.MINUTE, 30);
    c.set(Calendar.SECOND, 45);

    int year = c.get(Calendar.YEAR);
    int month = c.get(Calendar.MONTH);
    int date = c.get(Calendar.DATE);
    int hod = c.get(Calendar.HOUR_OF_DAY);
    int min = c.get(Calendar.MINUTE);
    int sec = c.get(Calendar.SECOND);
    System.out.println(
      "fields_after_set=" +
      year + "," + month + "," + date + "," + hod + "," + min + "," + sec
    );

    // 设置非法月份（溢出），由底层 Date 处理，主要观察行为而非断言具体值。
    c.set(Calendar.MONTH, 12);
    System.out.println("month_after_set_12=" + c.get(Calendar.MONTH));

    // get 未知字段：期望返回 0（根据系统实现）。
    // 标准 JDK 会抛出 ArrayIndexOutOfBoundsException，jsjvm 返回 0
    try {
      System.out.println("get_unknown_field_0=" + c.get(0));
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("get_unknown_field_0=exception:" + e.getClass().getName());
    }
    try {
      System.out.println("get_unknown_field_99=" + c.get(99));
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("get_unknown_field_99=exception:" + e.getClass().getName());
    }
  }

  /**
   * 覆盖：
   * - equals(Object)
   * - before(Object)
   * - after(Object)
   *
   * 包括：
   * - 不同/相同毫秒值下的 Calendar 比较
   * - 与 java.util.Date 比较
   * - 传入 null 和其他类型对象的边界行为
   */
  private static void testEqualsBeforeAfter() {
    Calendar c1 = Calendar.getInstance();
    Calendar c2 = Calendar.getInstance();
    c1.setTimeInMillis(1000L);
    c2.setTimeInMillis(2000L);

    // equals：同毫秒值同默认时区
    Calendar c1Eq = Calendar.getInstance();
    c1Eq.setTimeInMillis(1000L);
    System.out.println("equals_same_time=" + c1.equals(c1Eq));
    System.out.println("equals_diff_time=" + c1.equals(c2));
    System.out.println("equals_null=" + c1.equals(null));
    System.out.println("equals_other_class=" + c1.equals("notCalendar"));

    // before/after：两个 Calendar 之间
    System.out.println("before_c1_c2=" + c1.before(c2));
    System.out.println("before_c2_c1=" + c2.before(c1));
    System.out.println("after_c1_c2=" + c1.after(c2));
    System.out.println("after_c2_c1=" + c2.after(c1));

    Calendar cSame1 = Calendar.getInstance();
    Calendar cSame2 = Calendar.getInstance();
    cSame1.setTimeInMillis(5000L);
    cSame2.setTimeInMillis(5000L);
    System.out.println("before_equal_times=" + cSame1.before(cSame2));
    System.out.println("after_equal_times=" + cSame1.after(cSame2));

    // 与 java.util.Date 比较
    Calendar c3 = Calendar.getInstance();
    c3.setTimeInMillis(1500L);
    Date dEarlier = new Date(1000L);
    Date dLater = new Date(2000L);
    System.out.println("before_vs_Date_earlier=" + c3.before(dLater));
    System.out.println("before_vs_Date_later=" + c3.before(dEarlier));
    System.out.println("after_vs_Date_earlier=" + c3.after(dEarlier));
    System.out.println("after_vs_Date_later=" + c3.after(dLater));

    // 传入 null 与其它类型
    System.out.println("before_null=" + c3.before(null));
    System.out.println("after_null=" + c3.after(null));
    System.out.println("before_other_type=" + c3.before("str"));
    System.out.println("after_other_type=" + c3.after("str"));
  }

  /**
   * 覆盖 computeFields() / computeTime()：系统实现目前为空方法，
   * 这里仅验证调用不会抛出异常，且不破坏已有时间戳。
   */
  private static void testComputeMethods() {
    class TestCalendar extends Calendar {
      
      public void add(int field, int amount) {}
      
      public void roll(int field, boolean up) {}
      
      public int getMinimum(int field) { return 0; }
      
      public int getMaximum(int field) { return 0; }
      
      public int getGreatestMinimum(int field) { return 0; }
      
      public int getLeastMaximum(int field) { return 0; }
      
      protected void computeFields() {}
      
      protected void computeTime() {}

      public void testCompute() {
        computeFields();
        computeTime();
      }
    }

    TestCalendar c = new TestCalendar();
    c.setTimeInMillis(12345L);
    long before = c.getTimeInMillis();
    c.testCompute();
    long after = c.getTimeInMillis();
    System.out.println("compute_before=" + before);
    System.out.println("compute_after=" + after);
  }
}
