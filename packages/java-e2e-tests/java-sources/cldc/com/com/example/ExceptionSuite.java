package com.com.example;

import java.lang.Exception;

/**
 * 针对 java.lang.Exception 系统类实现的行为与边界测试。
 *
 * 覆盖点：
 * - 构造方法：Exception() 与 Exception(String)；
 * - detailMessage 的设置与 null message 行为（通过 getMessage()/toString() 间接观测）；
 * - 抛出/捕获场景下 message 与 toString() 的表现。
 */
public class ExceptionSuite {

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
   * - 验证类名；
   * - 验证 getMessage()（通常为 null）；
   * - 验证 toString() 的基本形态。
   */
  private static void testDefaultConstructor() {
    System.out.println("=== defaultCtor ===");

    Exception e = new Exception();
    print("default/class", e.getClass().getName());
    print("default/message", e.getMessage());
    print("default/toString", e.toString());
  }

  /**
   * 带 String 参数构造：
   * - message 为普通字符串；
   * - 验证 getMessage() 与构造参数一致；
   * - 验证 toString() 是否包含类名和消息。
   */
  private static void testMessageConstructor() {
    System.out.println("=== messageCtor ===");

    Exception e = new Exception("exception-error");
    print("message/class", e.getClass().getName());
    print("message/message", e.getMessage());
    print("message/toString", e.toString());
  }

  /**
   * 带 null String 的构造：
   * - 标准 JDK 中 getMessage() 通常为 null；
   * - 这里不假设实现细节，仅打印实际行为。
   */
  private static void testNullMessageConstructor() {
    System.out.println("=== nullMessageCtor ===");

    Exception e = new Exception((String) null);
    print("nullMessage/class", e.getClass().getName());
    print("nullMessage/message", e.getMessage());
    print("nullMessage/toString", e.toString());
  }

  /**
   * 抛出并捕获无参 Exception：
   * - 验证异常传播后 message 与 toString() 的行为是否稳定。
   */
  private static void testThrowCatchDefault() {
    System.out.println("=== throwCatchDefault ===");

    try {
      throw new Exception();
    } catch (Exception e) {
      print("throwDefault/caughtClass", e.getClass().getName());
      print("throwDefault/message", e.getMessage());
      print("throwDefault/toString", e.toString());
    }
  }

  /**
   * 抛出并捕获带 message 的 Exception：
   * - 覆盖带消息构造 + 抛出 + 捕获的完整路径；
   * - 验证 detailMessage 在异常传播后是否保留。
   */
  private static void testThrowCatchWithMessage() {
    System.out.println("=== throwCatchMessage ===");

    try {
      throw new Exception("after-throw");
    } catch (Exception e) {
      print("throwMessage/caughtClass", e.getClass().getName());
      print("throwMessage/message", e.getMessage());
      print("throwMessage/toString", e.toString());
    }
  }
}

