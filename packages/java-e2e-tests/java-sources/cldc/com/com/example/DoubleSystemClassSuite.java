package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.Double 系统类实现的边界测试。
 *
 * 覆盖点：
 * - 实例方法：<init>(D)、toString()、doubleValue/floatValue/intValue/longValue/shortValue/byteValue、equals(Object)、isNaN()、isInfinite()、hashCode()
 * - 静态方法：toString(double)、doubleToLongBits/longBitsToDouble、isNaN(double)、isInfinite(double)、parseDouble(String)、valueOf(String)
 * - 重要边界：NaN、Infinity、-Infinity、舍入/截断、byte/short 掩码、不同字符串格式/空串/带空格导致的 NumberFormatException
 */
public class DoubleSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== instance_basic_values ===");
    testInstanceBasicValues();

    System.out.println("=== instance_equals_nan_inf ===");
    testInstanceEqualsNaNAndInf();

    System.out.println("=== static_bits_and_checks ===");
    testStaticBitsAndChecks();

    System.out.println("=== parseDouble_and_valueOf ===");
    testParseDoubleAndValueOf();
  }

  /**
   * 覆盖：<init>(D)、toString()、doubleValue/floatValue/intValue/longValue/shortValue/byteValue、hashCode()。
   */
  private static void testInstanceBasicValues() {
    Double d = new Double(2.5d);

    System.out.println("d_class=" + d.getClass().getName());
    System.out.println("d_toString=" + d.toString());
    System.out.println("d_doubleValue=" + d.doubleValue());
    System.out.println("d_floatValue=" + d.floatValue());
    System.out.println("d_intValue=" + d.intValue());
    System.out.println("d_longValue=" + d.longValue());
    System.out.println("d_shortValue=" + (int) d.shortValue());
    System.out.println("d_byteValue=" + (int) d.byteValue());
    System.out.println("d_hashCode=" + d.hashCode());

    // 负数与截断行为
    Double dn = new Double(-3.7d);
    System.out.println("dn_doubleValue=" + dn.doubleValue());
    System.out.println("dn_intValue=" + dn.intValue());
    System.out.println("dn_longValue=" + dn.longValue());
    System.out.println("dn_shortValue=" + (int) dn.shortValue());
    System.out.println("dn_byteValue=" + (int) dn.byteValue());
    System.out.println("dn_hashCode=" + dn.hashCode());
  }

  /**
   * 覆盖：equals(Object)、实例 isNaN()/isInfinite()，包含 NaN/Infinity/-Infinity 情况。
   */
  private static void testInstanceEqualsNaNAndInf() {
    Double d1 = new Double(1.0d);
    Double d2 = new Double(1.0d);
    Double d3 = new Double(2.0d);

    System.out.println("eq_same_value=" + d1.equals(d2));
    System.out.println("eq_diff_value=" + d1.equals(d3));
    System.out.println("eq_null=" + d1.equals(null));
    System.out.println("eq_nonDouble=" + d1.equals("1.0"));

    Double dn1 = new Double(Double.NaN);
    Double dn2 = new Double(Double.NaN);
    System.out.println("eq_nan_nan=" + dn1.equals(dn2)); // NaN 与 NaN 视为相等
    System.out.println("nan_isNaN=" + dn1.isNaN());

    Double di = new Double(Double.POSITIVE_INFINITY);
    Double diNeg = new Double(Double.NEGATIVE_INFINITY);
    System.out.println("inf_isInfinite_pos=" + di.isInfinite());
    System.out.println("inf_isInfinite_neg=" + diNeg.isInfinite());
    System.out.println("inf_isNaN=" + di.isNaN());
  }

  /**
   * 覆盖静态方法：toString(double)、doubleToLongBits/longBitsToDouble、isNaN(double)、isInfinite(double)。
   */
  private static void testStaticBitsAndChecks() {
    double v = 2.0d;
    System.out.println("static_toString_2.0=" + Double.toString(v));

    long bits = Double.doubleToLongBits(v);
    System.out.println("bits_of_2.0=" + bits);
    double back = Double.longBitsToDouble(bits);
    System.out.println("back_from_bits=" + back);

    System.out.println("static_isNaN_0=" + Double.isNaN(0.0d));
    System.out.println("static_isNaN_nan=" + Double.isNaN(Double.NaN));

    System.out.println(
      "static_isInfinite_pos=" + Double.isInfinite(Double.POSITIVE_INFINITY)
    );
    System.out.println(
      "static_isInfinite_neg=" + Double.isInfinite(Double.NEGATIVE_INFINITY)
    );
    System.out.println("static_isInfinite_0=" + Double.isInfinite(0.0d));
  }

  /**
   * 覆盖 parseDouble(String) / valueOf(String)：
   * - 合法数字字符串
   * - 带空格的字符串：应抛 NumberFormatException（系统实现中通过 trim 检查）
   * - 非数字字符串：应抛 NumberFormatException
   */
  private static void testParseDoubleAndValueOf() {
    // 合法字符串
    Double v1 = Double.valueOf("3.5");
    System.out.println("valueOf_3.5=" + v1.doubleValue());

    double p1 = Double.parseDouble("4.5");
    System.out.println("parseDouble_4.5=" + p1);

    // 带前后空格：根据实现，trim 后不等于原文本应抛 NumberFormatException
    try {
      Double.parseDouble(" 5.5");
      System.out.println("parseDouble_space_prefix:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("parseDouble_space_prefix:NumberFormatException");
    } catch (Throwable t) {
      System.out.println(
        "parseDouble_space_prefix:" + t.getClass().getName()
      );
    }

    try {
      Double.valueOf("6.5 ");
      System.out.println("valueOf_space_suffix:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("valueOf_space_suffix:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("valueOf_space_suffix:" + t.getClass().getName());
    }

    // 完全非法字符串
    try {
      Double.parseDouble("not-a-number");
      System.out.println("parseDouble_invalid:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("parseDouble_invalid:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("parseDouble_invalid:" + t.getClass().getName());
    }

    try {
      Double.valueOf("?");
      System.out.println("valueOf_invalid:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("valueOf_invalid:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("valueOf_invalid:" + t.getClass().getName());
    }
  }
}

