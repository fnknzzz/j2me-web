package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.IllegalAccessException 系统类实现的测试。
 *
 * 覆盖点：
 * - 无参构造（initImpl）下的默认 message 行为
 * - 带 String 参数构造对 ThrowablePD.detailMessage 的设置
 * - 传入 null String 的边界情况
 * - 通过反射非法访问触发 IllegalAccessException 时的类型与 message 行为
 */
public class IllegalAccessExceptionSystemClassSuite {

  public static void main(String[] args) {
    testNoArgConstructor();
    testStringConstructor();
    testNullStringConstructor();
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
   * - 类型为 java.lang.IllegalAccessException
   * - 默认 message（通常为 null）
   */
  private static void testNoArgConstructor() {
    System.out.println("=== noArgCtor ===");

    IllegalAccessException e = new IllegalAccessException();
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

    String msg = "illegal access";
    IllegalAccessException e = new IllegalAccessException(msg);
    print("string/className", e.getClass().getName());
    print("string/message", e.getMessage());
    print("string/toString", e.toString());

    boolean contains = e.toString().indexOf("illegal access") >= 0;
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

    IllegalAccessException e = new IllegalAccessException((String) null);
    print("nullString/className", e.getClass().getName());
    print("nullString/message", e.getMessage());
    print("nullString/toString", e.toString());
  }

}

