package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.ArithmeticException 系统类实现的测试。
 *
 * 重点覆盖：
 * - 无参构造与带字符串参数构造的行为（尤其是 detailMessage 的设置）
 * - 传入 null 字符串时的边界行为
 * - 通过算术运算触发 ArithmeticException 时的类型与 message 可见性
 */
public class ArithmeticExceptionSystemClassSuite {

  public static void main(String[] args) {
    testNoArgConstructor();
    testStringConstructor();
    testNullStringConstructor();
    testThrownByDivision();
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
   * 无参构造：系统类使用 initImpl，预期：
   * - 类型为 java.lang.ArithmeticException
   * - message 默认为 null
   */
  private static void testNoArgConstructor() {
    System.out.println("=== noArgCtor ===");

    ArithmeticException e = new ArithmeticException();
    print("noArg/className", e.getClass().getName());
    print("noArg/message", e.getMessage());
    print("noArg/toString", e.toString());
  }

  /**
   * 带字符串参数构造：TS 实现中会将 msg 写入 ThrowablePD.detailMessage。
   * 这里通过 getMessage()/toString() 验证该字段是否正确生效。
   */
  private static void testStringConstructor() {
    System.out.println("=== stringCtor ===");

    String msg = "custom arithmetic error";
    ArithmeticException e = new ArithmeticException(msg);
    print("string/className", e.getClass().getName());
    print("string/message", e.getMessage());
    print("string/toString", e.toString());

    // 简单验证同一个字符串内容在 message 中可见
    boolean contains = e.toString().indexOf("custom arithmetic error") >= 0;
    print("string/toStringContainsMsg", contains);
  }

  /**
   * 传入 null 字符串：TS 侧实现只有在 msg 为非 null 且 String 类型时才设置 detailMessage。
   * 这里验证：
   * - 传入 null 时不应导致崩溃
   * - getMessage() 结果与无参构造一致（通常为 null）
   */
  private static void testNullStringConstructor() {
    System.out.println("=== nullStringCtor ===");

    ArithmeticException e = new ArithmeticException((String) null);
    print("nullString/className", e.getClass().getName());
    print("nullString/message", e.getMessage());
    print("nullString/toString", e.toString());
  }

  /**
   * 通过算术运算（除以 0）触发 ArithmeticException，验证：
   * - 抛出的实际类型确实是 java.lang.ArithmeticException
   * - getMessage() 中的内容可见（通常类似 " / by zero"）。
   *
   * 虽然触发异常的字节码属于算术指令，但最终仍然依赖本系统类的构造逻辑
   * 来承载 message，因此认为仍在当前系统类的测试范围内。
   */
  private static void testThrownByDivision() {
    System.out.println("=== thrownByDivision ===");

    try {
      int x = 1 / 0;
      System.out.println("division/result = " + x);
    } catch (ArithmeticException e) {
      print("division/className", e.getClass().getName());
      print("division/message", e.getMessage());
      print("division/toString", e.toString());
    }
  }
}

