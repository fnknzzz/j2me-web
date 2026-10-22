package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.NumberFormatException 系统类实现的测试。
 *
 * 覆盖点：
 * - 无参构造（initImpl）下的默认 message 行为
 * - 带 String 参数构造对 ThrowablePD.detailMessage 的设置
 * - 传入 null String 的边界情况
 * - 通过数值解析失败触发 NumberFormatException 时的类型与 message 行为
 */
public class NumberFormatExceptionSystemClassSuite {

  public static void main(String[] args) {
    testNoArgConstructor();
    testStringConstructor();
    testNullStringConstructor();
    testThrownByParse();
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
   * - 类型为 java.lang.NumberFormatException
   * - 默认 message（通常为 null）
   */
  private static void testNoArgConstructor() {
    System.out.println("=== noArgCtor ===");

    NumberFormatException e = new NumberFormatException();
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

    String msg = "bad number";
    NumberFormatException e = new NumberFormatException(msg);
    print("string/className", e.getClass().getName());
    print("string/message", e.getMessage());
    print("string/toString", e.toString());

    boolean contains = e.toString().indexOf("bad number") >= 0;
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

    NumberFormatException e = new NumberFormatException((String) null);
    print("nullString/className", e.getClass().getName());
    print("nullString/message", e.getMessage());
    print("nullString/toString", e.toString());
  }

  /**
   * 通过解析无效数字触发 NumberFormatException，验证：
   * - 实际抛出的类型为 java.lang.NumberFormatException
   * - message 中包含原始字符串或其他可用信息。
   */
  private static void testThrownByParse() {
    System.out.println("=== thrownByParse ===");

    try {
      Integer.parseInt("not_a_number");
      System.out.println("parseInt succeeded unexpectedly");
    } catch (NumberFormatException e) {
      print("parse/className", e.getClass().getName());
      print("parse/message", e.getMessage());
      print("parse/toString", e.toString());
    } catch (Exception e) {
      // 若特定实现下未抛 NumberFormatException，也记录实际异常类型
      print("parse/otherException", e.getClass().getName());
    }
  }
}

