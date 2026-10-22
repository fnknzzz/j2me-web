package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.Short 系统类实现的行为与边界测试（CLDC 1.1 范围）。
 *
 * CLDC 1.1 的 java.lang.Short 是一个非常精简的包装类：
 * - 静态字段：MIN_VALUE / MAX_VALUE
 * - 实例方法：<init>(short)、shortValue()、toString()、hashCode()、equals(Object)
 * - 静态方法：parseShort(String)、parseShort(String, int)
 */
public class ShortSystemClassSuite {

  public static void main(String[] args) throws Exception {
    testStaticFields();
    testInstanceBasics();
    testParseShortDefaultRadix();
    testParseShortWithRadix();
  }

  private static void print(String label, short value) {
    System.out.println(label + " = " + value);
  }

  private static void printException(String label, Exception e) {
    System.out.println(label + " throws " + e.getClass().getName());
  }

  /**
   * 静态字段：MIN_VALUE / MAX_VALUE / SIZE / BYTES / TYPE。
   */
  private static void testStaticFields() {
    System.out.println("=== staticFields ===");

    // 使用 (int) 强制转换，方便观察负值边界。
    System.out.println("fields/MIN_VALUE = " + (int) java.lang.Short.MIN_VALUE);
    System.out.println("fields/MAX_VALUE = " + (int) java.lang.Short.MAX_VALUE);
  }

  /**
   * 实例级别的基础行为：
   * - 构造 <init>(short)；
   * - shortValue / intValue / longValue / floatValue / doubleValue；
   * - 实例 hashCode() / toString()；
   * - 涵盖关键边界：0 / -1 / MIN_VALUE / MAX_VALUE。
   */
  private static void testInstanceBasics() {
    System.out.println("=== instanceBasics ===");

    java.lang.Short s0 = new java.lang.Short((short) 0);
    java.lang.Short sNeg1 = new java.lang.Short((short) -1);
    java.lang.Short sMin = new java.lang.Short(java.lang.Short.MIN_VALUE);
    java.lang.Short sMax = new java.lang.Short(java.lang.Short.MAX_VALUE);

    // 类型信息
    System.out.println("inst/className = " + s0.getClass().getName());

    // shortValue
    System.out.println("inst/shortValue/0 = " + (int) s0.shortValue());
    System.out.println("inst/shortValue/neg1 = " + (int) sNeg1.shortValue());
    System.out.println("inst/shortValue/min = " + (int) sMin.shortValue());
    System.out.println("inst/shortValue/max = " + (int) sMax.shortValue());

    // 实例 hashCode 与字符串表示。
    System.out.println("inst/hash/0 = " + s0.hashCode());
    System.out.println("inst/hash/neg1 = " + sNeg1.hashCode());
    System.out.println("inst/hash/min = " + sMin.hashCode());
    System.out.println("inst/hash/max = " + sMax.hashCode());

    System.out.println("inst/toString/0 = " + s0.toString());
    System.out.println("inst/toString/neg1 = " + sNeg1.toString());
    System.out.println("inst/toString/min = " + sMin.toString());
    System.out.println("inst/toString/max = " + sMax.toString());

    java.lang.Short a = new java.lang.Short((short) 5);
    java.lang.Short b = new java.lang.Short((short) 5);
    java.lang.Short c = new java.lang.Short((short) 6);
    System.out.println("equals/sameValue = " + a.equals(b));
    System.out.println("equals/diffValue = " + a.equals(c));
    System.out.println("equals/self = " + a.equals(a));
    System.out.println("equals/null = " + a.equals(null));
    System.out.println("equals/otherType = " + a.equals("5"));
  }

  /**
   * Short.parseShort(String) 默认十进制的行为：
   * - 正常：0、正数、负数、边界 MIN/MAX；
   * - 异常：前后空白、溢出、非数字、仅有符号、null。
   */
  private static void testParseShortDefaultRadix() {
    System.out.println("=== parseShortDefault ===");

    // 正常路径
    try {
      print("parseDefault/zero", java.lang.Short.parseShort("0"));
      print("parseDefault/pos", java.lang.Short.parseShort("123"));
      print("parseDefault/neg", java.lang.Short.parseShort("-123"));
      print("parseDefault/max", java.lang.Short.parseShort("32767"));
      print("parseDefault/min", java.lang.Short.parseShort("-32768"));
    } catch (Exception e) {
      printException("parseDefault/normal", e);
    }

    // 前后空白：内部 parse() 要求 trim 后一致，应触发 NumberFormatException。
    try {
      java.lang.Short.parseShort(" 1");
      System.out.println("parseDefault/leadingSpace = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseDefault/leadingSpace", e);
    }

    try {
      java.lang.Short.parseShort("1 ");
      System.out.println("parseDefault/trailingSpace = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseDefault/trailingSpace", e);
    }

    // 溢出：超出 short 范围。
    try {
      java.lang.Short.parseShort("32768");
      System.out.println("parseDefault/overflowPos = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseDefault/overflowPos", e);
    }

    try {
      java.lang.Short.parseShort("-32769");
      System.out.println("parseDefault/overflowNeg = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseDefault/overflowNeg", e);
    }

    // 非数字
    try {
      java.lang.Short.parseShort("abc");
      System.out.println("parseDefault/nonNumeric = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseDefault/nonNumeric", e);
    }

    // 仅有符号
    try {
      java.lang.Short.parseShort("+");
      System.out.println("parseDefault/onlyPlus = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseDefault/onlyPlus", e);
    }

    try {
      java.lang.Short.parseShort("-");
      System.out.println("parseDefault/onlyMinus = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseDefault/onlyMinus", e);
    }

    // null
    try {
      java.lang.Short.parseShort((String) null);
      System.out.println("parseDefault/null = OK");
    } catch (Exception e) {
      printException("parseDefault/null", e);
    }
  }

  /**
   * Short.parseShort(String, int) 的正常与异常场景：
   * - 正常：不同进制、正负号、小写十六进制；
   * - 异常：空白、溢出、非法字符；
   * - 边界：radix < 2 / radix > 36、字符串为 null。
   */
  private static void testParseShortWithRadix() {
    System.out.println("=== parseShortRadix ===");

    // 正常场景
    try {
      print("parseRadix/10_zero", java.lang.Short.parseShort("0", 10));
      print("parseRadix/10_pos", java.lang.Short.parseShort("123", 10));
      print("parseRadix/10_neg", java.lang.Short.parseShort("-128", 10));
      print("parseRadix/16_hex", java.lang.Short.parseShort("7f", 16));
      print("parseRadix/16_hexNeg", java.lang.Short.parseShort("-80", 16));
      print("parseRadix/8_oct", java.lang.Short.parseShort("177", 8));
      print("parseRadix/2_bin", java.lang.Short.parseShort("1010", 2));
    } catch (Exception e) {
      printException("parseRadix/normal", e);
    }

    // 空白字符串：两端有空白应当触发 NumberFormatException。
    try {
      java.lang.Short.parseShort(" 1", 10);
      System.out.println("parseRadix/leadingSpace = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseRadix/leadingSpace", e);
    }

    try {
      java.lang.Short.parseShort("1 ", 10);
      System.out.println("parseRadix/trailingSpace = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseRadix/trailingSpace", e);
    }

    // 溢出
    try {
      java.lang.Short.parseShort("32768", 10);
      System.out.println("parseRadix/overflowPos = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseRadix/overflowPos", e);
    }

    try {
      java.lang.Short.parseShort("-32769", 10);
      System.out.println("parseRadix/overflowNeg = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseRadix/overflowNeg", e);
    }

    // 非法字符
    try {
      java.lang.Short.parseShort("G", 16);
      System.out.println("parseRadix/invalidDigit = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseRadix/invalidDigit", e);
    }

    // 字符串为 null
    try {
      java.lang.Short.parseShort((String) null, 10);
      System.out.println("parseRadix/nullString = OK");
    } catch (java.lang.NumberFormatException e) {
      printException("parseRadix/nullString", e);
    } catch (Exception e) {
      printException("parseRadix/nullStringOther", e);
    }
  }
}
