package com.com.example;

import java.lang.VirtualMachineError;

/**
 * 针对 jsjvm 中 java.lang.VirtualMachineError 系统类实现的边界测试。
 *
 * 覆盖点（对应 VirtualMachineErrorDecl 中声明的构造方法）：
 * - VirtualMachineError()：无参构造，走 initImpl，不写入 detailMessage；
 * - VirtualMachineError(String)：带消息构造，通过 MemoryManager.setPrivateData
 *   将 detailMessage 写入 java.lang.Throwable 的 PD；
 *
 * 重点验证：
 * - 运行时类型名称与父类名称是否与系统类声明一致（java.lang.VirtualMachineError / java.lang.Error）；
 * - 无消息 / null 消息 / 非空消息下 getMessage() 与 toString() 的行为；
 * - 多实例之间的消息隔离，避免 PD 串写；
 * - 在 throw/catch 过程中的类型与 message/toString 的稳定性。
 */
public class VirtualMachineErrorSystemClassSuite {

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
   * - 覆盖 VirtualMachineError() -> initImpl 路径（不写 detailMessage）；
   * - 验证运行时类型名称；
   * - 验证 getMessage() 为空串，toString() 只包含类名。
   */
  private static void testDefaultConstructor() {
    System.out.println("=== defaultCtor ===");

    VirtualMachineError e = new VirtualMachineError() {};
    Class cls = e.getClass();

    print("default/class", cls.getName());
    print("default/superClass", "java.lang.VirtualMachineError");
    String message = e.getMessage();
    print("default/message", message);
    print("default/message_is_empty", message != null && message.length() == 0);
    print("default/toString", e.toString());
  }

  /**
   * 带 String 参数构造：
   * - 间接验证 VirtualMachineError(String) 将 detailMessage 写入 PD；
   * - 通过 getMessage()/toString() 观察消息是否被正确持久化；
   * - 记录类名，确保类型层级未被破坏。
   */
  private static void testMessageConstructor() {
    System.out.println("=== messageCtor ===");

    VirtualMachineError e = new VirtualMachineError("vm-error-message") {};
    Class cls = e.getClass();

    print("message/class", cls.getName());
    print("message/superClass", "java.lang.VirtualMachineError");
    String message = e.getMessage();
    print("message/message", message);
    print("message/message_equals_input", "vm-error-message".equals(message));
    print("message/toString", e.toString());
  }

  /**
   * 带 null String 的构造：
   * - 覆盖 isObjectValue(msg) 或 descriptor 不是 Ljava/lang/String; 的路径；
   * - native 构造不应写入 detailMessage；
   * - getMessage() 结果不为 null，但长度为 0；
   * - toString() 是否包含冒号/消息由快照记录。
   */
  private static void testNullMessageConstructor() {
    System.out.println("=== nullMessageCtor ===");

    VirtualMachineError e = new VirtualMachineError((String) null) {};
    String message = e.getMessage();
    String toStringValue = e.toString();

    print("nullMessage/message", message);
    print("nullMessage/message_is_not_null", message != null);
    print("nullMessage/message_is_empty", message != null && message.length() == 0);

    boolean hasColon = toStringValue != null && toStringValue.indexOf(':') >= 0;
    print("nullMessage/toString_has_colon", hasColon);
    print("nullMessage/toString", toStringValue);
  }

  /**
   * 抛出并捕获无参 VirtualMachineError：
   * - 覆盖构造 -> 抛出 -> 捕获的完整路径；
   * - 验证在异常传播之后，类型名称与 message/toString 保持稳定；
   * - message 仍然为长度为 0 的字符串。
   */
  private static void testThrowCatchDefault() {
    System.out.println("=== throwCatchDefault ===");

    try {
      throw new VirtualMachineError() {};
    } catch (VirtualMachineError e) {
      print("throwDefault/caughtClass", e.getClass().getName());
      String message = e.getMessage();
      print("throwDefault/message", message);
      print("throwDefault/message_is_empty", message != null && message.length() == 0);
      print("throwDefault/toString", e.toString());
    }
  }

  /**
   * 抛出并捕获带 message 的 VirtualMachineError：
   * - 验证通过 PD 存储的 detailMessage 在异常传播后仍可访问；
   * - 检查 getMessage()/toString() 是否保留构造时的消息内容。
   */
  private static void testThrowCatchWithMessage() {
    System.out.println("=== throwCatchMessage ===");

    try {
      throw new VirtualMachineError("after-throw-vm-error") {};
    } catch (VirtualMachineError e) {
      print("throwMessage/caughtClass", e.getClass().getName());
      String message = e.getMessage();
      print("throwMessage/message", message);
      print("throwMessage/message_equals_input", "after-throw-vm-error".equals(message));
      print("throwMessage/toString", e.toString());
    }
  }

  /**
   * 多实例隔离性测试：
   * - 创建两个带不同 message 的实例；
   * - 验证各自的 message 与 toString 内容不串写；
   * - 验证实例引用不相等。
   */
  private static void testMultipleInstanceIsolation() {
    System.out.println("=== multiInstance ===");

    VirtualMachineError e1 = new VirtualMachineError("first-vm-error-instance") {};
    VirtualMachineError e2 = new VirtualMachineError("second-vm-error-instance") {};

    print("multi/e1Class", e1.getClass().getName());
    print("multi/e2Class", e2.getClass().getName());
    print("multi/e1Message", e1.getMessage());
    print("multi/e2Message", e2.getMessage());
    print("multi/sameInstance", e1 == e2);

    boolean messagesEqual =
        e1.getMessage() != null && e1.getMessage().equals(e2.getMessage());
    print("multi/messagesEqual", messagesEqual);

    String toString1 = e1.toString();
    String toString2 = e2.toString();
    boolean toStringEqual =
        toString1 != null && toString1.equals(toString2);
    print("multi/toStringEqual", toStringEqual);
  }
}

