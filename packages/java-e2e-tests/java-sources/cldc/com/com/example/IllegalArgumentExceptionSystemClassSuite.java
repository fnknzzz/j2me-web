package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.IllegalArgumentException 系统类实现的测试。
 *
 * 覆盖点：
 * - 无参构造（initImpl）下的默认 message 行为
 * - 带 String 参数构造对 ThrowablePD.detailMessage 的设置
 * - 传入 null String 的边界情况
 * - 通过非法参数触发 IllegalArgumentException 时的类型与 message 行为
 */
public class IllegalArgumentExceptionSystemClassSuite {

  public static void main(String[] args) {
    testNoArgConstructor();
    testStringConstructor();
    testNullStringConstructor();
    testThrownByBadArgument();
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
   * 无参构造：仅调用 initImpl -> voidValue。
   * 验证：
   * - 类型为 java.lang.IllegalArgumentException
   * - 默认 message（通常为 null）
   */
  private static void testNoArgConstructor() {
    System.out.println("=== noArgCtor ===");

    IllegalArgumentException e = new IllegalArgumentException();
    print("noArg/className", e.getClass().getName());
    print("noArg/message", e.getMessage());
    print("noArg/toString", e.toString());
  }

  /**
   * 带字符串参数构造：TS 实现会将 msg 写入 ThrowablePD.detailMessage。
   * 这里通过 getMessage()/toString() 验证该字段是否正确生效。
   */
  private static void testStringConstructor() {
    System.out.println("=== stringCtor ===");

    String msg = "illegal argument";
    IllegalArgumentException e = new IllegalArgumentException(msg);
    print("string/className", e.getClass().getName());
    print("string/message", e.getMessage());
    print("string/toString", e.toString());

    boolean contains = e.toString().indexOf("illegal argument") >= 0;
    print("string/toStringContainsMsg", contains);
  }

  /**
   * 传入 null 字符串：TS 实现中只有在 msg 为非 null 且 String 类型时才设置 detailMessage。
   * 验证：
   * - 传入 null 不应导致崩溃
   * - getMessage() 行为与无参构造一致（通常为 null）
   */
  private static void testNullStringConstructor() {
    System.out.println("=== nullStringCtor ===");

    IllegalArgumentException e = new IllegalArgumentException((String) null);
    print("nullString/className", e.getClass().getName());
    print("nullString/message", e.getMessage());
    print("nullString/toString", e.toString());
  }

  /**
   * 通过非法参数触发 IllegalArgumentException，验证：
   * - 实际抛出的类型为 java.lang.IllegalArgumentException
   * - message 中包含可用信息。
   */
  private static void testThrownByBadArgument() {
    System.out.println("=== thrownByBadArgument ===");

    try {
      // 构造一个明显非法的参数场景：例如 String.substring 起始 > 结束
      String s = "abc";
      String sub = s.substring(2, 1);
      System.out.println("substring result = " + sub);
    } catch (IllegalArgumentException e) {
      print("badArg/className", e.getClass().getName());
      print("badArg/message", e.getMessage());
      print("badArg/toString", e.toString());
    } catch (Exception e) {
      // 若特定实现下未抛 IllegalArgumentException，也记录实际异常类型
      print("badArg/otherException", e.getClass().getName());
    }
  }
}

