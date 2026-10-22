package com.com.example;

import java.util.NoSuchElementException;

/**
 * 针对 jsjvm 中 java.util.NoSuchElementException 系统类实现的测试。
 *
 * 覆盖点：
 * - 无参构造 <init>() 的基本行为（类型、默认 message/toString）
 * - 带 String 参数构造 <init>(String) 对 ThrowablePD.detailMessage 的设置：
 *   * 传入非 null 字符串时，getMessage()/toString() 中应包含该 message
 *   * 传入 null 时，不应崩溃，message 行为与无参构造类似
 * - 直接 throw 新建的 NoSuchElementException 并捕获，验证类型与 message 传播
 */
public class NoSuchElementExceptionSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== noArgCtor ===");
    testNoArgConstructor();

    System.out.println("=== stringCtor_nonNull ===");
    testStringConstructorNonNull();

    System.out.println("=== stringCtor_null ===");
    testStringConstructorNull();

    System.out.println("=== thrown_exception ===");
    testThrownException();
  }

  private static void print(String label, Object value) {
    System.out.println(label + "=" + String.valueOf(value));
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
   * 无参构造：TS 实现仅返回 void，不设置 detailMessage。
   * 验证：
   * - 类型为 java.util.NoSuchElementException
   * - 默认 message（通常为 null）与 toString() 输出。
   */
  private static void testNoArgConstructor() {
    NoSuchElementException e = new NoSuchElementException();
    print("noArg_class", e.getClass().getName());
    print("noArg_message", e.getMessage());
    print("noArg_toString", e.toString());
  }

  /**
   * 带字符串参数构造：TS 实现会在 msg 为非 null 且 String 类型时，将其写入
   * ThrowablePD.detailMessage，对应 Java 层的 getMessage()/toString() 行为。
   */
  private static void testStringConstructorNonNull() {
    String msg = "no more elements";
    NoSuchElementException e = new NoSuchElementException(msg);
    print("str_nonNull_class", e.getClass().getName());
    print("str_nonNull_message", e.getMessage());
    print("str_nonNull_toString", e.toString());

    boolean contains = e.toString().indexOf("no more elements") >= 0;
    print("str_nonNull_toString_contains_msg", contains);
  }

  /**
   * 传入 null 字符串：按 TS 实现，isObjectValue(msg) 为 false，不会设置 detailMessage。
   * 期望行为：
   * - 不崩溃
   * - getMessage() 行为与无参构造类似（通常为 null）。
   */
  private static void testStringConstructorNull() {
    NoSuchElementException e = new NoSuchElementException((String) null);
    print("str_null_message", e.getMessage());
    print("str_null_toString", e.toString());
  }

  /**
   * 显式抛出并捕获 NoSuchElementException，验证：
   * - 抛出类型正确
   * - message 通过 Throwable 机制正常传播。
   */
  private static void testThrownException() {
    try {
      throw new NoSuchElementException("thrown from test");
    } catch (NoSuchElementException e) {
      print("thrown_class", e.getClass().getName());
      print("thrown_message", e.getMessage());
      print("thrown_toString", e.toString());
    }
  }
}

