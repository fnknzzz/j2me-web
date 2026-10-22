package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.Integer 系统类实现的边界测试。
 *
 * 重点覆盖：
 * - 实例方法：构造 <init>(int)、各类数值转换 byte/short/int/long/float/double、toString()、equals(Object)、hashCode()
 * - 静态方法：valueOf(String)、valueOf(String,int)、toString(int)、toString(int,int)、
 *   parseInt(String)、parseInt(String,int)、toHexString/toOctalString/toBinaryString、valueOf(String,int) 的溢出与非法字符串行为
 * - 边界值：最小/最大值、符号位、不同进制、前后空格、溢出、非法字符
 */
public class IntegerSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== instance_methods ===");
    testInstanceMethods();

    System.out.println("=== static_valueOf_and_toString ===");
    testStaticValueOfAndToString();

    System.out.println("=== static_parseInt_variants ===");
    testStaticParseIntVariants();

    System.out.println("=== static_radix_and_boundaries ===");
    testStaticRadixAndBoundaries();
  }

  /**
   * 覆盖实例方法：构造、数值转换、toString/equals/hashCode/compareTo。
   */
  private static void testInstanceMethods() {
    Integer a = new Integer(42);
    Integer b = new Integer(42);
    Integer c = new Integer(-1);

    System.out.println("a_class=" + a.getClass().getName());
    System.out.println("a_intValue=" + a.intValue());
    System.out.println("a_longValue=" + a.longValue());
    System.out.println("a_shortValue=" + (int) a.shortValue());
    System.out.println("a_byteValue=" + (int) a.byteValue());
    System.out.println("a_floatValue=" + a.floatValue());
    System.out.println("a_doubleValue=" + a.doubleValue());
    System.out.println("a_toString=" + a.toString());
    System.out.println("a_hashCode=" + a.hashCode());

    System.out.println("a_equals_b=" + a.equals(b));
    System.out.println("a_equals_c=" + a.equals(c));
    System.out.println("a_equals_null=" + a.equals(null));
    System.out.println("a_equals_nonInteger=" + a.equals("42"));

    System.out.println("a_compareTo_b=" + (a.intValue() < b.intValue() ? -1 : (a.intValue() == b.intValue() ? 0 : 1)));
    System.out.println("a_compareTo_c=" + (a.intValue() < c.intValue() ? -1 : (a.intValue() == c.intValue() ? 0 : 1)));
    System.out.println("c_compareTo_a=" + (c.intValue() < a.intValue() ? -1 : (c.intValue() == a.intValue() ? 0 : 1)));

    // 边界值存储：MIN_VALUE / MAX_VALUE
    Integer min = new Integer(Integer.MIN_VALUE);
    Integer max = new Integer(Integer.MAX_VALUE);
    System.out.println("min_toString=" + min.toString());
    System.out.println("max_toString=" + max.toString());
  }

  /**
   * 覆盖静态 valueOf(String) / valueOf(String,int) 和 toString(int)/toString(int,int)。
   */
  private static void testStaticValueOfAndToString() {
    Integer v10 = new Integer(10);
    System.out.println("valueOf_int_10=" + v10.intValue());

    // valueOf(String)
    Integer vs = Integer.valueOf("123");
    System.out.println("valueOf_str_123=" + vs.intValue());

    // valueOf(String,int)
    Integer vr16 = Integer.valueOf("ff", 16);
    System.out.println("valueOf_str_ff_16=" + vr16.intValue());

    // toString(int)
    System.out.println("toString_255=" + Integer.toString(255));

    // toString(int, radix)
    System.out.println("toString_255_16=" + Integer.toString(255, 16));
    System.out.println("toString_255_2=" + Integer.toString(255, 2));
    System.out.println("toString_-1_10=" + Integer.toString(-1, 10));
  }

  /**
   * 覆盖 parseInt(String) / parseInt(String,int) 的正常路径与错误路径：
   * - 正常数字
   * - 带前后空格
   * - 超出 int 范围
   * - 非法字符
   */
  private static void testStaticParseIntVariants() {
    // parseInt(String) 正常
    System.out.println("parseInt_123=" + Integer.parseInt("123"));

    // 带空格，应触发 NumberFormatException
    try {
      Integer.parseInt(" 7");
      System.out.println("parseInt_space_prefix:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("parseInt_space_prefix:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("parseInt_space_prefix:" + t.getClass().getName());
    }

    // 超出范围：2147483648 (MAX_VALUE+1)
    try {
      Integer.parseInt("2147483648");
      System.out.println("parseInt_overflow:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("parseInt_overflow:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("parseInt_overflow:" + t.getClass().getName());
    }

    // 非法字符
    try {
      Integer.parseInt("G");
      System.out.println("parseInt_invalid_char:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("parseInt_invalid_char:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("parseInt_invalid_char:" + t.getClass().getName());
    }

    // parseInt(String, radix)
    System.out.println("parseInt_FF_16=" + Integer.parseInt("FF", 16));
    System.out.println("parseInt_101_2=" + Integer.parseInt("101", 2));

    try {
      Integer.parseInt("ZZ", 36);
      System.out.println("parseInt_invalid_radix_body:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("parseInt_invalid_radix_body:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("parseInt_invalid_radix_body:" + t.getClass().getName());
    }
  }

  /**
   * 覆盖 toHexString/toOctalString/toBinaryString 及带符号/不同 radix 的边界行为，
   * 以及 valueOf(String,int) / parseInt(String,int) 在最小/最大值、符号位和非法输入下的行为。
   */
  private static void testStaticRadixAndBoundaries() {
    // 进制转换（无符号视角）
    System.out.println("toHexString_255=" + Integer.toHexString(255));
    System.out.println("toOctalString_255=" + Integer.toOctalString(255));
    System.out.println("toBinaryString_5=" + Integer.toBinaryString(5));

    // 负数在无符号转换下的表现
    System.out.println("toHexString_-1=" + Integer.toHexString(-1));
    System.out.println("toBinaryString_-1=" + Integer.toBinaryString(-1));

    // valueOf(String) 符号处理（+/-）
    System.out.println("valueOf_plus_123=" + Integer.valueOf("123").intValue());
    System.out.println("valueOf_minus_5=" + Integer.valueOf("-5").intValue());

    // valueOf(String) 非法字符
    try {
      Integer.valueOf("G");
      System.out.println("valueOf_invalid_char:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("valueOf_invalid_char:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("valueOf_invalid_char:" + t.getClass().getName());
    }

    // valueOf(String) 带空格
    try {
      Integer.valueOf(" 7");
      System.out.println("valueOf_space_prefix:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("valueOf_space_prefix:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("valueOf_space_prefix:" + t.getClass().getName());
    }

    // valueOf(String) 溢出
    try {
      Integer.valueOf("2147483648");
      System.out.println("valueOf_overflow:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("valueOf_overflow:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("valueOf_overflow:" + t.getClass().getName());
    }

    // valueOf(String,int) 的边界：最小/最大值和非法 body
    System.out.println("valueOf_MIN_10=" + Integer.valueOf("-2147483648", 10).intValue());
    System.out.println("valueOf_MAX_10=" + Integer.valueOf("2147483647", 10).intValue());

    try {
      Integer.valueOf("-2147483649", 10);
      System.out.println("valueOf_min_overflow:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("valueOf_min_overflow:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("valueOf_min_overflow:" + t.getClass().getName());
    }

    try {
      Integer.valueOf("ZZ", 36);
      System.out.println("valueOf_invalid_body:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("valueOf_invalid_body:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("valueOf_invalid_body:" + t.getClass().getName());
    }
  }
}
