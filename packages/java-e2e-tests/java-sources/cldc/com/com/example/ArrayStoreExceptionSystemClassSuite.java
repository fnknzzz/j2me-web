package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.ArrayStoreException 系统类实现的边界测试。
 *
 * 覆盖点：
 * - 无参构造：不设置 detailMessage，继承层级正确（RuntimeException / Exception）
 * - (String) 构造：将字符串写入 Throwable 的私有数据 detailMessage
 * - (null) 构造：msg 为 null 时分支行为（当前系统实现只在非 null 且为 String 时写入 PD）
 */
public class ArrayStoreExceptionSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== default_ctor_and_hierarchy ===");
    testDefaultCtorAndHierarchy();

    System.out.println("=== message_ctor ===");
    testMessageCtor();

    System.out.println("=== null_message_ctor ===");
    testNullMessageCtor();
  }

  /**
   * 覆盖无参构造及继承关系：
   * - getClass().getName()
   * - instanceof java.lang.RuntimeException / java.lang.Exception
   * - getMessage() 默认应为 null
   */
  private static void testDefaultCtorAndHierarchy() {
    java.lang.ArrayStoreException e1 = new java.lang.ArrayStoreException();

    System.out.println("e1_class=" + e1.getClass().getName());
    System.out.println(
      "e1_instanceof_RuntimeException=" + (e1 instanceof java.lang.RuntimeException)
    );
    System.out.println(
      "e1_instanceof_Exception=" + (e1 instanceof java.lang.Exception)
    );

    // 对于无参构造，detailMessage 一般为 null
    System.out.println("e1_message=" + e1.getMessage());
    System.out.println("e1_toString=" + e1.toString());
  }

  /**
   * 覆盖带 String 参数的构造器：
   * - 校验 getMessage() 与传入字符串一致
   * - toString() 中应包含该消息（由 Throwable 实现决定，快照对比即可暴露差异）
   */
  private static void testMessageCtor() {
    String msg = "array-store-test-message";
    java.lang.ArrayStoreException e2 = new java.lang.ArrayStoreException(msg);

    System.out.println("e2_message=" + e2.getMessage());
    System.out.println("e2_toString=" + e2.toString());

    // 再创建一个不同消息，确保不会被缓存或复用错误
    String msg2 = "second-message-array-store-123";
    java.lang.ArrayStoreException e3 = new java.lang.ArrayStoreException(msg2);
    System.out.println("e3_message=" + e3.getMessage());
    System.out.println("e3_toString=" + e3.toString());
  }

  /**
   * 覆盖 msg 为 null 的情况：
   * - 按 Java 语义允许 new ArrayStoreException((String) null)
   * - 当前系统实现只有在 msg 为非 null 且 descriptor 为 Ljava/lang/String; 时才写入 PD
   *   因此这里可以用 getMessage()/toString() 的输出差异来暴露实现是否遵循该分支
   */
  private static void testNullMessageCtor() {
    java.lang.ArrayStoreException e = new java.lang.ArrayStoreException((String) null);

    System.out.println("null_msg_class=" + e.getClass().getName());
    System.out.println("null_msg_message=" + e.getMessage());
    System.out.println("null_msg_toString=" + e.toString());
  }
}
