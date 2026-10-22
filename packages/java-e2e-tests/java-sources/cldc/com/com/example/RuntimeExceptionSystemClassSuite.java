package com.com.example;

import java.lang.RuntimeException;

/**
 * 针对 jsjvm 中 java.lang.RuntimeException 系统类实现的测试。
 *
 * 覆盖点（对应 RuntimeExceptionDecl 中声明的构造方法）：
 * - RuntimeException()：无参构造的基本行为（className、message、toString）；
 * - RuntimeException(String)：带消息构造对 detailMessage 的写入行为；
 * - RuntimeException((String) null)：null 消息的边界行为；
 * - 构造后的异常在 throw/catch 流程中的 message / toString 保持稳定；
 * - 多实例之间的 message 隔离，避免私有数据（PD）发生串写。
 */
public class RuntimeExceptionSystemClassSuite {

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
   * - 验证运行时类型名称；
   * - 直接打印 getMessage() 结果（通常为 null，不强行假设实现）；
   * - 打印 toString() 以观测与标准 JDK 行为是否一致。
   */
  private static void testDefaultConstructor() {
    System.out.println("=== defaultCtor ===");

    RuntimeException e = new RuntimeException();
    print("default/class", e.getClass().getName());
    print("default/message", e.getMessage());
    print("default/toString", e.toString());
  }

  /**
   * 带 String 参数构造：
   * - 通过 getMessage() 间接验证 RuntimeException(String) 中对 Throwable PD 的写入；
   * - 打印 toString()，观察是否包含类名及消息内容。
   */
  private static void testMessageConstructor() {
    System.out.println("=== messageCtor ===");

    RuntimeException e = new RuntimeException("runtime-error");
    print("message/class", e.getClass().getName());
    print("message/message", e.getMessage());
    print("message/toString", e.toString());
  }

  /**
   * 带 null String 的构造：
   * - 这是对 native 构造中 isObjectValue(msg) 分支的边界覆盖；
   * - 标准 JDK 中 getMessage() 通常为 null，这里同样只打印实际结果，避免依赖具体实现；
   * - 观察 toString() 在 null message 情况下的表现形式。
   */
  private static void testNullMessageConstructor() {
    System.out.println("=== nullMessageCtor ===");

    RuntimeException e = new RuntimeException((String) null);
    print("nullMessage/class", e.getClass().getName());
    print("nullMessage/message", e.getMessage());
    print("nullMessage/toString", e.toString());
  }

  /**
   * 抛出并捕获无参 RuntimeException：
   * - 覆盖 RuntimeException() 经过 VM 抛出/捕获链路后的稳定性；
   * - 验证 message 与 toString() 在传播中的保持情况。
   */
  private static void testThrowCatchDefault() {
    System.out.println("=== throwCatchDefault ===");

    try {
      throw new RuntimeException();
    } catch (RuntimeException e) {
      print("throwDefault/caughtClass", e.getClass().getName());
      print("throwDefault/message", e.getMessage());
      print("throwDefault/toString", e.toString());
    }
  }

  /**
   * 抛出并捕获带 message 的 RuntimeException：
   * - 覆盖 RuntimeException(String) 的完整生命周期：构造 -> 抛出 -> 捕获；
   * - 间接验证 PD 中 detailMessage 在异常传播之后仍然可通过 getMessage()/toString() 读取。
   */
  private static void testThrowCatchWithMessage() {
    System.out.println("=== throwCatchMessage ===");

    try {
      throw new RuntimeException("after-throw-runtime");
    } catch (RuntimeException e) {
      print("throwMessage/caughtClass", e.getClass().getName());
      print("throwMessage/message", e.getMessage());
      print("throwMessage/toString", e.toString());
    }
  }

  /**
   * 多实例隔离性测试：
   * - 创建两个带不同 message 的 RuntimeException 实例；
   * - 打印各自的 message 以及引用是否相同；
   * - 打印 message 是否相等，用于暴露 PD 写入是否发生交叉覆盖。
   */
  private static void testMultipleInstanceIsolation() {
    System.out.println("=== multiInstance ===");

    RuntimeException e1 = new RuntimeException("first-instance");
    RuntimeException e2 = new RuntimeException("second-instance");

    print("multi/e1Class", e1.getClass().getName());
    print("multi/e2Class", e2.getClass().getName());
    print("multi/e1Message", e1.getMessage());
    print("multi/e2Message", e2.getMessage());
    print("multi/sameInstance", e1 == e2);

    // 当 PD 写入逻辑正确且彼此独立时，两个实例的 message 不应相等。
    boolean messagesEqual =
      e1.getMessage() != null && e1.getMessage().equals(e2.getMessage());
    print("multi/messagesEqual", messagesEqual);
  }
}

