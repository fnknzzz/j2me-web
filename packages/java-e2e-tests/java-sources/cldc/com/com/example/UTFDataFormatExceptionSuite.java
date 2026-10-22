package com.com.example;

import java.io.UTFDataFormatException;

/**
 * 针对 java.io.UTFDataFormatException 的系统类实现进行测试。
 *
 * 目标：
 * - 覆盖 decl 中声明的两个构造方法：UTFDataFormatException() 与 UTFDataFormatException(String)。
 * - 验证 detailMessage 的设置效果（通过 getMessage()/toString() 间接观测）。
 * - 覆盖 null message、带 message、无参构造、throw/catch 场景。
 */
public class UTFDataFormatExceptionSuite {

  public static void main(String[] args) throws Exception {
    testDefaultConstructor();
    testMessageConstructor();
    testNullMessageConstructor();
    testThrowCatchDefault();
    testThrowCatchWithMessage();
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

  private static void printException(String label, Exception e) {
    System.out.println(label + " throws " + e.getClass().getSimpleName());
  }

  /**
   * 无参构造：
   * - 验证类名
   * - 验证 getMessage() 为 null
   * - 验证 toString() 的形态（一般为 ClassName 或 ClassName: null）。
   */
  private static void testDefaultConstructor() {
    System.out.println("=== defaultCtor ===");

    UTFDataFormatException e = new UTFDataFormatException();
    print("default/class", e.getClass().getName());
    print("default/message", e.getMessage());
    print("default/toString", e.toString());
  }

  /**
   * 带 String 参数的构造：
   * - message 为普通字符串
   * - 验证 getMessage() 与构造参数一致
   * - 验证 toString() 是否包含该 message。
   */
  private static void testMessageConstructor() {
    System.out.println("=== messageCtor ===");

    UTFDataFormatException e = new UTFDataFormatException("utf-error");
    print("message/class", e.getClass().getName());
    print("message/message", e.getMessage());
    print("message/toString", e.toString());
  }

  /**
   * 带 null String 的构造：
   * - 标准 JDK 中 getMessage() 返回 null。
   * - 这里不假设实现细节，仅打印实际行为。
   */
  private static void testNullMessageConstructor() {
    System.out.println("=== nullMessageCtor ===");

    UTFDataFormatException e = new UTFDataFormatException((String) null);
    print("nullMessage/class", e.getClass().getName());
    print("nullMessage/message", e.getMessage());
    print("nullMessage/toString", e.toString());
  }

  /**
   * 抛出并捕获无参 UTFDataFormatException：
   * - 验证经过异常传播后 message 与 toString() 的行为保持一致。
   */
  private static void testThrowCatchDefault() {
    System.out.println("=== throwCatchDefault ===");

    try {
      throw new UTFDataFormatException();
    } catch (UTFDataFormatException e) {
      print("throwDefault/caughtClass", e.getClass().getName());
      print("throwDefault/message", e.getMessage());
      print("throwDefault/toString", e.toString());
    }
  }

  /**
   * 抛出并捕获带 message 的 UTFDataFormatException：
   * - 验证通过构造函数设置的 detailMessage 在异常传播后依然可见。
   */
  private static void testThrowCatchWithMessage() {
    System.out.println("=== throwCatchMessage ===");

    try {
      throw new UTFDataFormatException("after-throw");
    } catch (UTFDataFormatException e) {
      print("throwMessage/caughtClass", e.getClass().getName());
      print("throwMessage/message", e.getMessage());
      print("throwMessage/toString", e.toString());
    }
  }
}

