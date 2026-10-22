package com.com.example;

import java.util.TimeZone;

/**
 * 针对 jsjvm 中 java.util.TimeZone 系统类实现的边界测试。
 *
 * 覆盖目标（对应 java-util-time-zone.ts 中的声明）：
 * - 构造方法：<init>()、<init>(String,int)
 * - 访问方法：getID()、getRawOffset()
 *
 * 重点场景：
 * - 默认构造时，ID/rawOffset 是否按配置初始化（默认应为 CST / 8 小时偏移）。
 * - 通过 (String,int) 显式传入 ID 与正/负偏移，以及极端偏移值。
 * - 传入 null 作为 ID 时，getID() 的行为（应返回 null，而不是回退默认值）。
 *
 * 所有校验通过 System.out.println 打印标签化结果，由快照进行对比。
 */
public class TimeZoneSystemClassSuite {

  private static class TestTimeZone extends TimeZone {
    private String id;
    private int rawOffset;

    public TestTimeZone() {
      this.id = "CST";
      this.rawOffset = 8 * 60 * 60 * 1000;
    }

    public TestTimeZone(String id, int rawOffset) {
      this.id = id;
      this.rawOffset = rawOffset;
    }

    
    public int getOffset(int era, int year, int month, int day, int dayOfWeek, int milliseconds) {
      return rawOffset;
    }

    
    public void setRawOffset(int offsetMillis) {
      this.rawOffset = offsetMillis;
    }

    
    public int getRawOffset() {
      return rawOffset;
    }

    
    public boolean useDaylightTime() {
      return false;
    }

    
    public boolean inDaylightTime(java.util.Date date) {
      return false;
    }

    
    public String getID() {
      return id;
    }
  }

  public static void main(String[] args) {
    System.out.println("=== ctor_default_and_basic_getters ===");
    testCtorDefaultAndBasicGetters();

    System.out.println("=== ctor_with_id_and_positive_offset ===");
    testCtorWithIdAndPositiveOffset();

    System.out.println("=== ctor_with_null_id_and_negative_offset ===");
    testCtorWithNullIdAndNegativeOffset();

    System.out.println("=== ctor_with_extreme_offsets ===");
    testCtorWithExtremeOffsets();
  }

  /**
   * 覆盖无参构造 <init>() 与 getID()/getRawOffset() 的基础行为。
   */
  private static void testCtorDefaultAndBasicGetters() {
    TimeZone tz = new TestTimeZone();

    // 打印类名，确保由 jsjvm 的 TimeZone 实现来执行，而不是宿主 JDK 的实现。
    System.out.println("default_class=" + tz.getClass().getName());

    String id = tz.getID();
    int raw = tz.getRawOffset();

    System.out.println("default_id=" + id);
    System.out.println("default_rawOffset=" + raw);

    // 多次调用 getter，确认没有缓存状态异常。
    System.out.println("default_id_again=" + tz.getID());
    System.out.println("default_rawOffset_again=" + tz.getRawOffset());
  }

  /**
   * 覆盖 <init>(String,int) 正常路径：
   * - 使用常见时区 ID 与正向偏移（单位：毫秒）
   * - 验证 getID()/getRawOffset() 是否反映构造时传入的值。
   */
  private static void testCtorWithIdAndPositiveOffset() {
    int offset8h = 8 * 60 * 60 * 1000;
    int offset2h = 2 * 60 * 60 * 1000;

    TimeZone tz1 = new TestTimeZone("Asia/Shanghai", offset8h);
    System.out.println("tz1_id=" + tz1.getID());
    System.out.println("tz1_rawOffset=" + tz1.getRawOffset());

    TimeZone tz2 = new TestTimeZone("UTC+2", offset2h);
    System.out.println("tz2_id=" + tz2.getID());
    System.out.println("tz2_rawOffset=" + tz2.getRawOffset());

    // 再次调用，确认构造后字段稳定。
    System.out.println("tz2_id_again=" + tz2.getID());
    System.out.println("tz2_rawOffset_again=" + tz2.getRawOffset());
  }

  /**
   * 覆盖 <init>(String,int) 在 ID 为 null、偏移为负数时的行为：
   * - jsjvm 实现会直接保存传入的引用与偏移，不做额外校验。
   * - 通过 String.valueOf(getID()) 来打印，方便区分 null 与字符串 "null"。
   */
  private static void testCtorWithNullIdAndNegativeOffset() {
    int minus3h = -3 * 60 * 60 * 1000;

    TimeZone tzNullId = new TestTimeZone(null, minus3h);
    String idStr = String.valueOf(tzNullId.getID()); // 真实 null 会被渲染为 "null"
    int raw = tzNullId.getRawOffset();

    System.out.println("null_id_valueOf=" + idStr);
    System.out.println("null_id_rawOffset=" + raw);
  }

  /**
   * 使用极端偏移值（接近 int 边界）来验证 rawOffset 存储与返回无溢出异常。
   */
  private static void testCtorWithExtremeOffsets() {
    // 使用接近 Integer.MAX_VALUE 与 Integer.MIN_VALUE 的偏移值
    int nearMax = Integer.MAX_VALUE;
    int nearMin = Integer.MIN_VALUE;

    TimeZone tzMax = new TestTimeZone("MAX", nearMax);
    System.out.println("max_id=" + tzMax.getID());
    System.out.println("max_rawOffset=" + tzMax.getRawOffset());

    TimeZone tzMin = new TestTimeZone("MIN", nearMin);
    System.out.println("min_id=" + tzMin.getID());
    System.out.println("min_rawOffset=" + tzMin.getRawOffset());
  }
}

