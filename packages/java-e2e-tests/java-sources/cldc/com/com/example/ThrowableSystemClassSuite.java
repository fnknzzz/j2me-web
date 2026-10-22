package com.com.example;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * 针对 jsjvm 中 java.lang.Throwable 系统类实现的边界测试。
 *
 * 覆盖点（对应 ThrowableDecl 中声明的构造方法与实例方法）：
 * - 构造方法：Throwable() / Throwable(String)
 * - 实例方法：getMessage() / toString()
 * - 实例方法：printStackTrace() / printStackTrace(PrintStream)
 *
 * 重点关注：
 * - detailMessage 的写入与缺省行为：
 *   - 无参构造与 (String) null 构造均不写入 detailMessage；
 *   - (String) 非空构造通过 PD 写入 detailMessage；
 * - getMessage() 在多种场景下都不返回 null，而是空字符串或传入消息；
 * - toString() 使用运行时类名 + 可选消息，且子类应打印子类名；
 * - printStackTrace() 使用 System.out 的 PrintStream 打印 toString()；
 * - printStackTrace(PrintStream) 对外部 PrintStream 的使用，以及 null PrintStream 的异常行为。
 */
public class ThrowableSystemClassSuite {

  /**
   * 自定义 Throwable 子类，用于验证 toString() 中使用的是运行时类名。
   */
  private static class MyThrowable extends Throwable {
    MyThrowable() {
      super();
    }

    MyThrowable(String message) {
      super(message);
    }
  }

  public static void main(String[] args) {
    System.out.println("=== default_and_string_ctors ===");
    testConstructorsAndBasicMessage();

    System.out.println("=== null_message_ctor ===");
    testNullMessageConstructor();

    System.out.println("=== subclass_toString_and_message ===");
    testSubclassToStringAndMessage();

    System.out.println("=== printStackTrace_default_out ===");
    testPrintStackTraceDefaultOut();

    System.out.println("=== printStackTrace_custom_printstream ===");
    testPrintStackTraceCustomPrintStream();

    System.out.println("=== printStackTrace_null_printstream ===");
    testPrintStackTraceNullPrintStream();
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
   * 覆盖无参构造与带 String 消息构造：
   * - 无参构造：detailMessage 未写入，getMessage() 应返回长度为 0 的字符串；
   * - 带消息构造：detailMessage 通过 PD 写入，getMessage() 返回同一消息；
   * - toString()：
   *   - 无参构造只包含类名；
   *   - 带消息构造包含 "类名: 消息"。
   */
  private static void testConstructorsAndBasicMessage() {
    Throwable tDefault = new Throwable();
    String msgDefault = tDefault.getMessage();
    String toStringDefault = tDefault.toString();

    print("default_message_is_not_null", new Boolean(msgDefault != null));
    print(
        "default_message_is_empty_string",
        new Boolean(msgDefault != null && msgDefault.length() == 0));
    print("default_toString", toStringDefault);

    Throwable tMsg = new Throwable("base-message");
    String msg = tMsg.getMessage();
    String toStringMsg = tMsg.toString();

    print("string_message_equals_input", new Boolean("base-message".equals(msg)));
    print("string_message_is_not_empty", new Boolean(msg != null && msg.length() > 0));
    print("string_toString", toStringMsg);
  }

  /**
   * 覆盖 (String) null 构造：
   * - native 构造不写入 detailMessage；
   * - getMessage() 仍然返回非 null 的字符串，但长度为 0；
   * - toString() 不应包含冒号分隔的消息部分。
   */
  private static void testNullMessageConstructor() {
    Throwable tNull = new Throwable((String) null);
    String msgNull = tNull.getMessage();
    String toStringNull = tNull.toString();

    print("null_message_is_not_null", new Boolean(msgNull != null));
    print(
        "null_message_is_empty_string",
        new Boolean(msgNull != null && msgNull.length() == 0));

    boolean hasColon = toStringNull != null && toStringNull.indexOf(':') >= 0;
    print("null_toString_has_colon", new Boolean(hasColon));
    print("null_toString_value", toStringNull);
  }

  /**
   * 子类 toString()/getMessage 行为：
   * - MyThrowable(String) 通过 super(message) 调用 Throwable(String) 构造；
   * - getMessage() 应返回传入的消息；
   * - toString() 中类名应为子类全限定名，而非 java.lang.Throwable。
   */
  private static void testSubclassToStringAndMessage() {
    MyThrowable mt = new MyThrowable("subclass-message");

    String msg = mt.getMessage();
    String toStringValue = mt.toString();
    String className = mt.getClass().getName();

    print("subclass_class_name", className);
    print(
        "subclass_message_equals_input",
        new Boolean("subclass-message".equals(msg)));
    boolean toStringContainsClass =
        toStringValue != null && toStringValue.indexOf(className) >= 0;
    boolean toStringContainsMsg =
        toStringValue != null && toStringValue.indexOf("subclass-message") >= 0;
    print("subclass_toString_contains_class", new Boolean(toStringContainsClass));
    print("subclass_toString_contains_message", new Boolean(toStringContainsMsg));
    print("subclass_toString_value", toStringValue);
  }

  /**
   * 无参 printStackTrace()：
   * - 内部会读取 System.out 静态字段，调用 println(toString())；
   * - 这里通过前后标记行验证调用未抛异常，并让快照捕获中间输出内容。
   */
  private static void testPrintStackTraceDefaultOut() {
    Throwable t = new Throwable("print-default-out-message");

    System.out.println("before_printStackTrace_default");
    try {
      t.printStackTrace();
      System.out.println("after_printStackTrace_default_no_exception");
    } catch (Throwable e) {
      System.out.println(
          "after_printStackTrace_default_exception=" + e.getClass().getName());
    }
  }

  /**
   * 带 PrintStream 的 printStackTrace(PrintStream)：
   * - 使用 ByteArrayOutputStream + PrintStream 捕获输出；
   * - 验证输出中包含运行时类名与消息字符串；
   * - 同时输出长度信息方便观察实现变化。
   */
  private static void testPrintStackTraceCustomPrintStream() {
    Throwable t = new Throwable("print-custom-ps-message");

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);

    try {
      t.printStackTrace(ps);
      ps.flush();
      String content = baos.toString();

      boolean containsClass =
          content != null && content.indexOf("java.lang.Throwable") >= 0;
      boolean containsMsg =
          content != null && content.indexOf("print-custom-ps-message") >= 0;

      print("custom_ps_content_length", content.length());
      print("custom_ps_contains_class", new Boolean(containsClass));
      print("custom_ps_contains_message", new Boolean(containsMsg));
    } catch (Throwable e) {
      System.out.println(
          "printStackTrace_custom_ps_exception=" + e.getClass().getName());
    }
  }

  /**
   * printStackTrace(null) 的边界行为：
   * - 当前实现未对 null PrintStream 做显式保护，可能导致 NPE 或 VM 层异常；
   * - 这里专门调用并捕获异常类型，由快照记录实际行为。
   */
  private static void testPrintStackTraceNullPrintStream() {
    Throwable t = new Throwable("print-null-ps-message");

    try {
      t.printStackTrace((PrintStream) null);
      System.out.println("printStackTrace_null_ps_no_exception");
    } catch (Throwable e) {
      System.out.println(
          "printStackTrace_null_ps_exception=" + e.getClass().getName());
    }
  }
}
