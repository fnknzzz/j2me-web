package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.StringIndexOutOfBoundsException 系统类实现的测试。
 *
 * 覆盖点：
 * - 无参构造（initImpl）下的默认 message 行为
 * - 带 String 参数构造对 ThrowablePD.detailMessage 的设置
 * - 传入 null String 的边界情况（不应设置 detailMessage）
 * - 带 int 参数构造时，将 index 转为字符串写入 detailMessage 的行为（正数、0、负数）
 * - 通过 java.lang.String 的越界操作抛出的异常类型与 message 行为
 */
public class StringIndexOutOfBoundsExceptionSystemClassSuite {

  public static void main(String[] args) {
    testNoArgConstructor();
    testStringConstructor();
    testNullStringConstructor();
    testIntConstructorPositive();
    testIntConstructorZero();
    testIntConstructorNegative();
    testThrownByCharAtPositiveIndex();
    testThrownBySubstringNegativeIndex();
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
   * - 类型为 java.lang.StringIndexOutOfBoundsException
   * - 默认 message（通常为 null）
   */
  private static void testNoArgConstructor() {
    System.out.println("=== noArgCtor ===");

    StringIndexOutOfBoundsException e = new StringIndexOutOfBoundsException();
    print("noArg/className", e.getClass().getName());
    print("noArg/message", e.getMessage());
    print("noArg/toString", e.toString());
  }

  /**
   * 带字符串参数构造：TS 实现会在 msg 为非 null 且 String 类型时，将其写入
   * ThrowablePD.detailMessage，对应 Java 层的 getMessage()/toString() 行为。
   */
  private static void testStringConstructor() {
    System.out.println("=== stringCtor ===");

    String msg = "string index error";
    StringIndexOutOfBoundsException e = new StringIndexOutOfBoundsException(msg);
    print("string/className", e.getClass().getName());
    print("string/message", e.getMessage());
    print("string/toString", e.toString());

    boolean contains = e.toString().indexOf("string index error") >= 0;
    print("string/toStringContainsMsg", contains);
  }

  /**
   * 传入 null String：TS 实现中 isObjectValue(msg) 为 false，不会设置 detailMessage。
   * 期望行为：
   * - 不崩溃
   * - getMessage() 行为与无参构造类似（通常为 null）
   */
  private static void testNullStringConstructor() {
    System.out.println("=== nullStringCtor ===");

    StringIndexOutOfBoundsException e = new StringIndexOutOfBoundsException((String) null);
    print("nullString/className", e.getClass().getName());
    print("nullString/message", e.getMessage());
    print("nullString/toString", e.toString());
  }

  /**
   * int 构造：TS 实现中会读取 idx.value 并转为 int，再用 String(n) 构造 message。
   * 这里验证：正数 index 在 message/toString 中是否可见。
   */
  private static void testIntConstructorPositive() {
    System.out.println("=== intCtorPositive ===");

    StringIndexOutOfBoundsException e = new StringIndexOutOfBoundsException(5);
    print("intPos/message", e.getMessage());
    print("intPos/toString", e.toString());
    boolean containsIndex = e.toString().indexOf("5") >= 0;
    print("intPos/toStringContainsIndex", containsIndex);
  }

  /**
   * int 构造：0 这种边界 index 也应被精确写入 message。
   */
  private static void testIntConstructorZero() {
    System.out.println("=== intCtorZero ===");

    StringIndexOutOfBoundsException e = new StringIndexOutOfBoundsException(0);
    print("intZero/message", e.getMessage());
    print("intZero/toString", e.toString());
    boolean containsIndex = e.toString().indexOf("0") >= 0;
    print("intZero/toStringContainsIndex", containsIndex);
  }

  /**
   * int 构造：负数 index 同样应被转为字符串写入 message。
   */
  private static void testIntConstructorNegative() {
    System.out.println("=== intCtorNegative ===");

    StringIndexOutOfBoundsException e = new StringIndexOutOfBoundsException(-3);
    print("intNeg/message", e.getMessage());
    print("intNeg/toString", e.toString());
    boolean containsIndex = e.toString().indexOf("-3") >= 0;
    print("intNeg/toStringContainsIndex", containsIndex);
  }

  /**
   * 通过 String.charAt(len 之外的正数 index) 触发异常，验证：
   * - 抛出的实际类型为 java.lang.StringIndexOutOfBoundsException
   * - message 能够通过 Throwable 机制正常获取。
   */
  private static void testThrownByCharAtPositiveIndex() {
    System.out.println("=== thrownByCharAtPositiveIndex ===");

    try {
      String s = "abc";
      char ch = s.charAt(5);
      System.out.println("charAt/value = " + ch);
    } catch (StringIndexOutOfBoundsException e) {
      print("charAtPos/className", e.getClass().getName());
      print("charAtPos/message", e.getMessage());
      print("charAtPos/toString", e.toString());
    }
  }

  /**
   * 通过 String.substring(负数起始下标) 触发异常，验证：
   * - 抛出的类型仍为 java.lang.StringIndexOutOfBoundsException
   * - message 正常可读，不出现实现细节泄露或崩溃。
   */
  private static void testThrownBySubstringNegativeIndex() {
    System.out.println("=== thrownBySubstringNegativeIndex ===");

    try {
      String s = "abc";
      String sub = s.substring(-1);
      System.out.println("substringNeg/value = " + sub);
    } catch (StringIndexOutOfBoundsException e) {
      print("substringNeg/className", e.getClass().getName());
      print("substringNeg/message", e.getMessage());
      print("substringNeg/toString", e.toString());
    }
  }
}

