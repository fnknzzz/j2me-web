package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.Float 系统类实现的边界测试。
 *
 * 覆盖点：
 * - 实例方法：两个构造函数 <init>(D)、<init>(F)，floatValue/doubleValue/intValue/longValue/shortValue/byteValue、
 *   equals(Object)、isNaN()/isInfinite()、toString()、hashCode()
 * - 静态方法：toString(float)、floatToIntBits/intBitsToFloat、isNaN(float)、isInfinite(float)、
 *   parseFloat(String)、valueOf(String)
 * - 重要边界：NaN、Infinity/-Infinity、舍入/截断、byte/short 掩码、带空格/非法字符串触发 NumberFormatException
 */
public class FloatSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== instance_basic_values ===");
    testInstanceBasicValues();

    System.out.println("=== instance_equals_nan_inf ===");
    testInstanceEqualsNaNAndInf();

    System.out.println("=== static_bits_and_checks ===");
    testStaticBitsAndChecks();

    System.out.println("=== parseFloat_and_valueOf ===");
    testParseFloatAndValueOf();
  }

  /**
   * 覆盖：两个构造函数、基本数值转换和 hashCode。
   */
  private static void testInstanceBasicValues() {
    // 使用 double 构造函数
    Float fd = new Float(2.5d);
    System.out.println("fd_class=" + fd.getClass().getName());
    System.out.println("fd_toString=" + fd.toString());
    System.out.println("fd_floatValue=" + fd.floatValue());
    System.out.println("fd_doubleValue=" + fd.doubleValue());
    System.out.println("fd_intValue=" + fd.intValue());
    System.out.println("fd_longValue=" + fd.longValue());
    System.out.println("fd_shortValue=" + (int) fd.shortValue());
    System.out.println("fd_byteValue=" + (int) fd.byteValue());
    System.out.println("fd_hashCode=" + fd.hashCode());

    // 使用 float 构造函数，检查与 double 构造在值上的一致性
    Float ff = new Float(2.5f);
    System.out.println("ff_toString=" + ff.toString());
    System.out.println("ff_floatValue=" + ff.floatValue());
    System.out.println("ff_doubleValue=" + ff.doubleValue());
    System.out.println("ff_intValue=" + ff.intValue());
    System.out.println("ff_longValue=" + ff.longValue());
    System.out.println("ff_shortValue=" + (int) ff.shortValue());
    System.out.println("ff_byteValue=" + (int) ff.byteValue());
    System.out.println("ff_hashCode=" + ff.hashCode());

    // 负数与截断行为
    Float fn = new Float(-3.7f);
    System.out.println("fn_floatValue=" + fn.floatValue());
    System.out.println("fn_doubleValue=" + fn.doubleValue());
    System.out.println("fn_intValue=" + fn.intValue());
    System.out.println("fn_longValue=" + fn.longValue());
    System.out.println("fn_shortValue=" + (int) fn.shortValue());
    System.out.println("fn_byteValue=" + (int) fn.byteValue());
    System.out.println("fn_hashCode=" + fn.hashCode());
  }

  /**
   * 覆盖：equals(Object)、实例 isNaN()/isInfinite()，包含 NaN/Infinity/-Infinity 情况以及非 Float 对象。
   */
  private static void testInstanceEqualsNaNAndInf() {
    Float f1 = new Float(1.0f);
    Float f2 = new Float(1.0f);
    Float f3 = new Float(2.0f);

    System.out.println("eq_same_value=" + f1.equals(f2));
    System.out.println("eq_diff_value=" + f1.equals(f3));
    System.out.println("eq_null=" + f1.equals(null));
    System.out.println("eq_nonFloat=" + f1.equals("1.0"));

    // NaN 与 NaN 视为相等
    Float fn1 = new Float(Float.NaN);
    Float fn2 = new Float(Float.NaN);
    System.out.println("eq_nan_nan=" + fn1.equals(fn2));
    System.out.println("nan_isNaN=" + fn1.isNaN());

    // Infinity / -Infinity 的 isInfinite 与 isNaN 行为
    Float fiPos = new Float(Float.POSITIVE_INFINITY);
    Float fiNeg = new Float(Float.NEGATIVE_INFINITY);
    System.out.println("inf_isInfinite_pos=" + fiPos.isInfinite());
    System.out.println("inf_isInfinite_neg=" + fiNeg.isInfinite());
    System.out.println("inf_isNaN=" + fiPos.isNaN());
  }

  /**
   * 覆盖静态方法：toString(float)、floatToIntBits/intBitsToFloat、isNaN(float)、isInfinite(float)。
   */
  private static void testStaticBitsAndChecks() {
    float v = 2.0f;
    System.out.println("static_toString_2.0=" + Float.toString(v));

    int bits = Float.floatToIntBits(v);
    System.out.println("bits_of_2.0=" + bits);
    float back = Float.intBitsToFloat(bits);
    System.out.println("back_from_bits=" + back);

    System.out.println("static_isNaN_0=" + Float.isNaN(0.0f));
    System.out.println("static_isNaN_nan=" + Float.isNaN(Float.NaN));

    System.out.println("static_isInfinite_pos=" + Float.isInfinite(Float.POSITIVE_INFINITY));
    System.out.println("static_isInfinite_neg=" + Float.isInfinite(Float.NEGATIVE_INFINITY));
    System.out.println("static_isInfinite_0=" + Float.isInfinite(0.0f));
  }

  /**
   * 覆盖 parseFloat(String) / valueOf(String)：
   * - 合法数字字符串
   * - 带空格的字符串：应抛 NumberFormatException（系统实现中通过 trim 检查）
   * - 非数字字符串：应抛 NumberFormatException
   */
  private static void testParseFloatAndValueOf() {
    // 合法字符串
    Float vf = new Float(3.25f);
    System.out.println("valueOf_float_3.25=" + vf.floatValue());

    Float vs = Float.valueOf("3.5");
    System.out.println("valueOf_string_3.5=" + vs.floatValue());

    float p1 = Float.parseFloat("4.5");
    System.out.println("parseFloat_4.5=" + p1);

    // 带前后空格：根据实现，trim 后不等于原文本应抛 NumberFormatException
    try {
      Float.parseFloat(" 5.5");
      System.out.println("parseFloat_space_prefix:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("parseFloat_space_prefix:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("parseFloat_space_prefix:" + t.getClass().getName());
    }

    try {
      Float.valueOf("6.5 ");
      System.out.println("valueOf_space_suffix:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("valueOf_space_suffix:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("valueOf_space_suffix:" + t.getClass().getName());
    }

    // 完全非法字符串
    try {
      Float.parseFloat("not-a-number");
      System.out.println("parseFloat_invalid:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("parseFloat_invalid:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("parseFloat_invalid:" + t.getClass().getName());
    }

    try {
      Float.valueOf("?");
      System.out.println("valueOf_invalid:NO_EXCEPTION");
    } catch (NumberFormatException e) {
      System.out.println("valueOf_invalid:NumberFormatException");
    } catch (Throwable t) {
      System.out.println("valueOf_invalid:" + t.getClass().getName());
    }
  }
}
