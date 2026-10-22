package com.com.example;

/**
 * 针对 java.lang.Math 系统类实现的行为与边界测试。
 *
 * 覆盖点（全部来自当前 MathDecl 声明的静态方法）：
 * - abs(int/long/float/double)
 * - max/min(int/long/float/double)
 * - floor/ceil(double)
 * - sqrt(double)
 * - sin/cos/tan/asin/acos/atan(double)
 * - atan2(double, double)
 * - toDegrees/toRadians(double)
 *
 * 说明：
 * - 不在 Java 侧做数值精度断言，仅打印结果供快照比对；
 * - 尽量选择典型与边界参数（0、正数、负数、NaN、Infinity 等）。
 */
public class MathSuite {

  public static void main(String[] args) throws Exception {
    testAbs();
    testMaxMin();
    testFloorAndCeil();
    testPowAndSqrt();
    testTrig();
    testAtan2();
    testDegreesAndRadians();
  }

  private static void print(String label, Object value) {
    System.out.println(label + " = " + String.valueOf(value));
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
   * abs(int/long/float/double) 边界测试：
   * - 0、正、负；
   * - float/double 的 NaN 与 Infinity 行为。
   */
  private static void testAbs() {
    System.out.println("=== abs ===");

    // int
    print("abs/int/0", Math.abs(0));
    print("abs/int/pos", Math.abs(123));
    print("abs/int/neg", Math.abs(-123));

    // long
    print("abs/long/0", Math.abs(0L));
    print("abs/long/pos", Math.abs(123456789L));
    print("abs/long/neg", Math.abs(-123456789L));

    // float
    print("abs/float/0", Math.abs(0.0f));
    print("abs/float/neg", Math.abs(-1.5f));
    print("abs/float/NaN", Math.abs(Float.NaN));
    print("abs/float/+Inf", Math.abs(Float.POSITIVE_INFINITY));
    print("abs/float/-Inf", Math.abs(Float.NEGATIVE_INFINITY));

    // double
    print("abs/double/0", Math.abs(0.0));
    print("abs/double/neg", Math.abs(-2.5));
    print("abs/double/NaN", Math.abs(Double.NaN));
    print("abs/double/+Inf", Math.abs(Double.POSITIVE_INFINITY));
    print("abs/double/-Inf", Math.abs(Double.NEGATIVE_INFINITY));
  }

  /**
   * max/min 各类型：
   * - 涵盖正负数、相等、NaN 参与时的行为。
   */
  private static void testMaxMin() {
    System.out.println("=== maxMin ===");

    // int
    print("max/int/posNeg", Math.max(1, -1));
    print("min/int/posNeg", Math.min(1, -1));
    print("max/int/equal", Math.max(5, 5));
    print("min/int/equal", Math.min(5, 5));

    // long
    print("max/long/posNeg", Math.max(1L, -1L));
    print("min/long/posNeg", Math.min(1L, -1L));

    // float
    print("max/float/posNeg", Math.max(1.0f, -1.0f));
    print("min/float/posNeg", Math.min(1.0f, -1.0f));
    print("max/float/NaN", Math.max(Float.NaN, 1.0f));
    print("min/float/NaN", Math.min(Float.NaN, 1.0f));

    // double
    print("max/double/posNeg", Math.max(2.0, -2.0));
    print("min/double/posNeg", Math.min(2.0, -2.0));
    print("max/double/NaN", Math.max(Double.NaN, 2.0));
    print("min/double/NaN", Math.min(Double.NaN, 2.0));
  }

  /**
   * floor / ceil：
   * - 正数、负数、小数边界；
   * - NaN / Infinity。
   */
  private static void testFloorAndCeil() {
    System.out.println("=== floorAndCeil ===");

    double[] values = { 0.0, 1.2, 1.8, -1.2, -1.8, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
    for (int i = 0; i < values.length; i++) {
      double v = values[i];
      print("floor/" + i, Math.floor(v));
      print("ceil/" + i, Math.ceil(v));
    }
  }

  /**
   * sqrt：
   * - 0、1、NaN、负数开方等边界行为。
   */
  private static void testPowAndSqrt() {
    System.out.println("=== powAndSqrt ===");

    print("sqrt/4", Math.sqrt(4.0));
    print("sqrt/2", Math.sqrt(2.0));
    print("sqrt/-1", Math.sqrt(-1.0));
    print("sqrt/NaN", Math.sqrt(Double.NaN));
  }

  /**
   * 三角函数：sin/cos/tan/asin/acos/atan。
   * - 典型角度：0、π/2、π、-π/2；
   * - asin/acos 的边界 -1、0、1；
   * - NaN 传播行为。
   */
  private static void testTrig() {
    System.out.println("=== trig ===");

    double[] angles = { 0.0, Math.PI / 2, Math.PI, -Math.PI / 2 };
    for (int i = 0; i < angles.length; i++) {
      double a = angles[i];
      print("sin/" + i, Math.sin(a));
      print("cos/" + i, Math.cos(a));
      print("tan/" + i, Math.tan(a));
    }

    double[] asinInput = { -1.0, 0.0, 1.0, Double.NaN };
    for (int i = 0; i < asinInput.length; i++) {
      double v = asinInput[i];
      print("asin/" + i, Math.asin(v));
      print("acos/" + i, Math.acos(v));
      print("atan/" + i, Math.atan(v));
    }
  }

  /**
   * atan2(y, x)：
   * - 不同象限组合与 x/y 为 0 或负值；
   * - y/x 含 NaN 与 Infinity。
   */
  private static void testAtan2() {
    System.out.println("=== atan2 ===");

    print("atan2/1_1", Math.atan2(1.0, 1.0));
    print("atan2/1_-1", Math.atan2(1.0, -1.0));
    print("atan2/-1_1", Math.atan2(-1.0, 1.0));
    print("atan2/-1_-1", Math.atan2(-1.0, -1.0));

    print("atan2/0_1", Math.atan2(0.0, 1.0));
    print("atan2/1_0", Math.atan2(1.0, 0.0));
    print("atan2/0_-1", Math.atan2(0.0, -1.0));
    print("atan2/-1_0", Math.atan2(-1.0, 0.0));

    print("atan2/NaN_1", Math.atan2(Double.NaN, 1.0));
    print("atan2/1_NaN", Math.atan2(1.0, Double.NaN));
    print("atan2/Inf_1", Math.atan2(Double.POSITIVE_INFINITY, 1.0));
    print("atan2/1_Inf", Math.atan2(1.0, Double.POSITIVE_INFINITY));
  }

  /**
   * 角度与弧度转换：toDegrees / toRadians。
   * - 0、π/2、π、负角度与 NaN。
   */
  private static void testDegreesAndRadians() {
    System.out.println("=== degreesAndRadians ===");

    double[] radians = { 0.0, Math.PI / 2, Math.PI, -Math.PI / 2, Double.NaN };
    for (int i = 0; i < radians.length; i++) {
      double r = radians[i];
      print("toDegrees/" + i, Math.toDegrees(r));
    }

    double[] degrees = { 0.0, 90.0, 180.0, -90.0, Double.NaN };
    for (int i = 0; i < degrees.length; i++) {
      double d = degrees[i];
      print("toRadians/" + i, Math.toRadians(d));
    }
  }
}
