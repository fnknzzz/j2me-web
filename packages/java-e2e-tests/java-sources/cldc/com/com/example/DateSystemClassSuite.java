package com.com.example;

import java.util.Date;

/**
 * 针对 jsjvm 中 java.util.Date 系统类实现的测试。
 *
 * 覆盖点：
 * - 构造方法：Date() 与 Date(long)
 * - 基本行为：getTime/setTime/equals/hashCode/toString
 * - equals 的边界：null、不同类型、不同时间戳
 * - setTime 对已有实例 PD 的更新效果
 * - toString 的格式化逻辑（包含时区偏移与固定的星期/月缩写数组）
 *
 * 注意：为避免依赖当前系统时间，测试主要使用固定的 long 时间戳，
 * 默认构造只用于自反性等不依赖具体毫秒值的性质。
 */
public class DateSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== ctor_and_getTime ===");
    testConstructorsAndGetTime();

    System.out.println("=== equals_and_hashCode ===");
    testEqualsAndHashCode();

    System.out.println("=== setTime_and_getTime ===");
    testSetTimeAndGetTime();

    System.out.println("=== toString_format ===");
    testToStringFormat();
  }

  private static void print(String label, Object value) {
    System.out.println(label + "=" + String.valueOf(value));
  }

  private static void print(String label, int value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, long value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, boolean value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, double value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, float value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, byte value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, short value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, char value) {
    System.out.println(label + " = " + value);
  }

  /**
   * 覆盖：
   * - new Date()：仅验证对象可用，自反 equals 与 getTime 的基本性质，不打印实际时间戳。
   * - new Date(long)：验证 getTime 是否精确返回构造时的毫秒值，包括 0、正数、负数。
   */
  private static void testConstructorsAndGetTime() {
    Date now = new Date();
    print("ctor_now_class", now.getClass().getName());
    print("ctor_now_equals_self", now.equals(now));
    print("ctor_now_getTime_stable", now.getTime() == now.getTime());

    Date zero = new Date(0L);
    Date pos = new Date(123456789L);
    Date neg = new Date(-1000L);

    print("ctor_zero_time", zero.getTime());
    print("ctor_pos_time", pos.getTime());
    print("ctor_neg_time", neg.getTime());
  }

  /**
   * 覆盖 equals/hashCode：
   * - 同一时间戳的不同实例应相等，hashCode 相同。
   * - 不同时间戳的实例应不相等，hashCode 通常不同（只要求行为稳定）。
   * - 与 null、其他类型、子类比较应返回 false。
   */
  private static void testEqualsAndHashCode() {
    Date a1 = new Date(42L);
    Date a2 = new Date(42L);
    Date b = new Date(1000L);

    print("eq_a1_a2", a1.equals(a2));
    print("eq_a1_b", a1.equals(b));
    print("eq_a1_null", a1.equals(null));
    print("eq_a1_string", a1.equals("42"));

    // 使用子类验证 mm.getObjectClassName 不为 "java.util.Date" 的路径
    class MyDate extends Date {
      MyDate(long t) { super(t); }
    }
    Date sub = new MyDate(42L);
    print("eq_a1_subclass", a1.equals(sub));

    int a1Hash = a1.hashCode();
    int a2Hash = a2.hashCode();
    int bHash = b.hashCode();
    print("hash_a1", a1Hash);
    print("hash_a2", a2Hash);
    print("hash_b", bHash);
    print("hash_a1_eq_a2", a1Hash == a2Hash);
    print("hash_a1_eq_b", a1Hash == bHash);
  }

  /**
   * 覆盖 setTime(long)：
   * - 多次调用 setTime 会更新 PD 中的 time 值，getTime 反映最新值。
   * - 包含 0、正数、负数等边界。
   */
  private static void testSetTimeAndGetTime() {
    Date d = new Date(0L);
    print("setTime_initial", d.getTime());

    d.setTime(1L);
    print("setTime_after_1", d.getTime());

    d.setTime(999999999L);
    print("setTime_after_large", d.getTime());

    d.setTime(-500L);
    print("setTime_after_negative", d.getTime());
  }

  /**
   * 覆盖 toString() 的格式化逻辑：
   * - 使用固定时间戳 0L 和若干特定值，避免依赖当前系统时间。
   * - 输出完整字符串以验证星期、月份缩写、时分秒、年份、以及固定的 "CST" 标签。
   */
  private static void testToStringFormat() {
    Date epoch = new Date(0L);
    Date sample1 = new Date(60L * 60L * 1000L); // 1970-01-01 01:00:00 GMT
    Date sample2 = new Date(24L * 60L * 60L * 1000L); // 1970-01-02 00:00:00 GMT

    print("toString_epoch", epoch.toString());
    print("toString_sample1", sample1.toString());
    print("toString_sample2", sample2.toString());
  }
}

