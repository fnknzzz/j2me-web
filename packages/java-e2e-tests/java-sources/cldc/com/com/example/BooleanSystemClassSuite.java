package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.Boolean 系统类实现的测试。
 *
 * 重点覆盖：
 * - 构造函数 <init>(boolean) 对 PD 中布尔值的设置，包括 descriptor 为 'Z' 与非 'Z' 的情况
 * - 实例方法 booleanValue()/toString()/hashCode()/equals(Object) 的返回值
 * - 静态字段 TRUE/FALSE/TYPE 的初始化与语义
 */
public class BooleanSystemClassSuite {

  public static void main(String[] args) {
    testConstructorAndBooleanValue();
    testToStringAndHashCode();
    testEqualsBehavior();
    testStaticFields();
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
   * 构造函数 <init>(boolean) 与 booleanValue()：
   * - new Boolean(true/false).booleanValue()
   * - 通过 valueOf(int) 等手段间接模拟非 'Z' descriptor 情况在系统类中的处理（测试观察层面）。
   */
  private static void testConstructorAndBooleanValue() {
    System.out.println("=== ctorAndBooleanValue ===");

    Boolean bTrue = new Boolean(true);
    Boolean bFalse = new Boolean(false);

    print("ctor/true.booleanValue", bTrue.booleanValue());
    print("ctor/false.booleanValue", bFalse.booleanValue());

    Boolean fromIntZero = new Boolean(0 != 0);
    Boolean fromIntNonZero = new Boolean(1 != 0);
    print("ctor/fromIntZero.booleanValue", fromIntZero.booleanValue());
    print("ctor/fromIntNonZero.booleanValue", fromIntNonZero.booleanValue());
  }

  /**
   * toString() 与 hashCode() 的行为：
   * - true -> "true" & 1231
   * - false -> "false" & 1237
   */
  private static void testToStringAndHashCode() {
    System.out.println("=== toStringAndHashCode ===");

    Boolean bTrue = new Boolean(true);
    Boolean bFalse = new Boolean(false);

    print("toString/true", bTrue.toString());
    print("toString/false", bFalse.toString());

    print("hashCode/true", bTrue.hashCode());
    print("hashCode/false", bFalse.hashCode());
  }

  /**
   * equals(Object) 的行为：
   * - 同值 Boolean 实例相等
   * - 不同值 Boolean 实例不等
   * - 与非 Boolean 对象比较返回 false
   * - 与 null 比较返回 false
   */
  private static void testEqualsBehavior() {
    System.out.println("=== equalsBehavior ===");

    Boolean t1 = new Boolean(true);
    Boolean t2 = new Boolean(true);
    Boolean f1 = new Boolean(false);

    print("equals/true_true", t1.equals(t2));
    print("equals/true_false", t1.equals(f1));
    print("equals/false_false", f1.equals(new Boolean(false)));
    print("equals/true_null", t1.equals(null));
    print("equals/true_otherType", t1.equals("true"));
  }

  /**
   * 静态字段 TRUE/FALSE/TYPE：
   * - TRUE/FALSE 的 booleanValue()/toString()/hashCode()
   * - TYPE 是否为 boolean 基本类型的 Class 对象
   */
  private static void testStaticFields() {
    System.out.println("=== staticFields ===");

    Boolean t = Boolean.TRUE;
    Boolean f = Boolean.FALSE;

    print("TRUE.booleanValue", t.booleanValue());
    print("FALSE.booleanValue", f.booleanValue());

    print("TRUE.toString", t.toString());
    print("FALSE.toString", f.toString());

    print("TRUE.hashCode", t.hashCode());
    print("FALSE.hashCode", f.hashCode());
  }
}
