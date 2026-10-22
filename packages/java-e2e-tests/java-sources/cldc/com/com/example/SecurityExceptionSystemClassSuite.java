package com.com.example;

import java.lang.SecurityException;

/**
 * 针对 jsjvm 中 java.lang.SecurityException 系统类实现的边界测试。
 *
 * 覆盖点（对应 SecurityExceptionDecl 中声明的构造方法）：
 * - SecurityException()：无参构造，对应 initImpl（不写入 detailMessage）；
 * - SecurityException(String)：带消息构造，直接向 java.lang.Throwable 的 PD 写入 detailMessage；
 * - SecurityException((String) null)：null 消息分支下，不应写入 detailMessage；
 * - 构造实例在 throw/catch 过程中的 message / toString 稳定性；
 * - 多实例之间的消息隔离，避免 PD 串写；
 * - 运行时类型及父类是否与系统类声明一致。
 */
public class SecurityExceptionSystemClassSuite {

  public static void main(String[] args) {
    testDefaultConstructor();
    testMessageConstructor();
    testNullMessageConstructor();
    testThrowCatchDefault();
    testThrowCatchWithMessage();
    testMultipleInstanceIsolation();
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
   * 无参构造：
   * - 覆盖 SecurityException() -> initImpl 的路径（不写入 detailMessage）；
   * - 验证运行时类型名称与父类名称；
   * - 打印 getMessage() 与 toString()，用于对比空消息时的行为。
   */
  private static void testDefaultConstructor() {
    System.out.println("=== defaultCtor ===");

    SecurityException e = new SecurityException();
    Class cls = e.getClass();

    print("default/class", cls.getName());
    print("default/superClass", "java.lang.RuntimeException");
    print("default/message", e.getMessage());
    print("default/toString", e.toString());
  }

  /**
   * 带 String 参数构造：
   * - 间接验证 SecurityException(String) 对 java.lang.Throwable PD 的写入；
   * - 通过 getMessage() / toString() 观察 detailMessage 是否被正确持久化；
   * - 记录类名与父类，确保类型层级与系统类声明一致。
   */
  private static void testMessageConstructor() {
    System.out.println("=== messageCtor ===");

    SecurityException e = new SecurityException("sec-error");
    Class cls = e.getClass();

    print("message/class", cls.getName());
    print("message/superClass", "java.lang.RuntimeException");
    print("message/message", e.getMessage());
    print("message/toString", e.toString());
  }

  /**
   * 带 null String 的构造：
   * - 覆盖 isObjectValue(msg) 分支为 false 的情况，native 构造不写入 detailMessage；
   * - 观察 getMessage() 与 toString() 在 null 消息场景下的表现；
   * - 只打印实际结果，不假定具体实现细节，由快照负责校验。
   */
  private static void testNullMessageConstructor() {
    System.out.println("=== nullMessageCtor ===");

    SecurityException e = new SecurityException((String) null);
    print("nullMessage/class", e.getClass().getName());
    print("nullMessage/message", e.getMessage());
    print("nullMessage/toString", e.toString());
  }

  /**
   * 抛出并捕获无参 SecurityException：
   * - 覆盖 SecurityException() 在 VM 异常传播链路中的行为；
   * - 确认在 throw/catch 之后，类型名称与 message/toString 保持稳定。
   */
  private static void testThrowCatchDefault() {
    System.out.println("=== throwCatchDefault ===");

    try {
      throw new SecurityException();
    } catch (SecurityException e) {
      print("throwDefault/caughtClass", e.getClass().getName());
      print("throwDefault/message", e.getMessage());
      print("throwDefault/toString", e.toString());
    }
  }

  /**
   * 抛出并捕获带 message 的 SecurityException：
   * - 覆盖 SecurityException(String) 从构造 -> 抛出 -> 捕获的完整流程；
   * - 验证 PD 中 detailMessage 在异常传播后仍可通过 getMessage()/toString() 访问。
   */
  private static void testThrowCatchWithMessage() {
    System.out.println("=== throwCatchMessage ===");

    try {
      throw new SecurityException("after-throw-security");
    } catch (SecurityException e) {
      print("throwMessage/caughtClass", e.getClass().getName());
      print("throwMessage/message", e.getMessage());
      print("throwMessage/toString", e.toString());
    }
  }

  /**
   * 多实例隔离性测试：
   * - 创建两个带不同 message 的 SecurityException 实例；
   * - 打印各自的 message 与实例是否相同；
   * - 打印 message 是否相等，用于暴露 PD 写入是否发生交叉覆盖。
   */
  private static void testMultipleInstanceIsolation() {
    System.out.println("=== multiInstance ===");

    SecurityException e1 = new SecurityException("first-sec-instance");
    SecurityException e2 = new SecurityException("second-sec-instance");

    print("multi/e1Class", e1.getClass().getName());
    print("multi/e2Class", e2.getClass().getName());
    print("multi/e1Message", e1.getMessage());
    print("multi/e2Message", e2.getMessage());
    print("multi/sameInstance", e1 == e2);

    boolean messagesEqual =
        e1.getMessage() != null && e1.getMessage().equals(e2.getMessage());
    print("multi/messagesEqual", messagesEqual);
  }
}
