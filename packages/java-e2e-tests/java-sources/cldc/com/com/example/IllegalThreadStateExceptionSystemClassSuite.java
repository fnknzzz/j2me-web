package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.IllegalThreadStateException 系统类实现的测试。
 *
 * 覆盖点：
 * - 无参构造（initImpl）下的默认 message 行为
 * - 带 String 参数构造对 ThrowablePD.detailMessage 的设置
 * - 传入 null String 的边界情况
 * - 通过线程状态非法操作触发 IllegalThreadStateException 时的类型与 message 行为
 */
public class IllegalThreadStateExceptionSystemClassSuite {

  public static void main(String[] args) {
    testNoArgConstructor();
    testStringConstructor();
    testNullStringConstructor();
    testThrownByThreadState();
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
   * - 类型为 java.lang.IllegalThreadStateException
   * - 默认 message（通常为 null）
   */
  private static void testNoArgConstructor() {
    System.out.println("=== noArgCtor ===");

    IllegalThreadStateException e = new IllegalThreadStateException();
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

    String msg = "illegal thread state";
    IllegalThreadStateException e = new IllegalThreadStateException(msg);
    print("string/className", e.getClass().getName());
    print("string/message", e.getMessage());
    print("string/toString", e.toString());

    boolean contains = e.toString().indexOf("illegal thread state") >= 0;
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

    IllegalThreadStateException e = new IllegalThreadStateException((String) null);
    print("nullString/className", e.getClass().getName());
    print("nullString/message", e.getMessage());
    print("nullString/toString", e.toString());
  }

  /**
   * 通过线程状态非法操作触发 IllegalThreadStateException，验证：
   * - 实际抛出的类型为 java.lang.IllegalThreadStateException
   * - message 中包含可用信息（若有）。
   */
  private static void testThrownByThreadState() {
    System.out.println("=== thrownByThreadState ===");

    class SimpleThread extends Thread {
      
      public void run() {
        // 空实现即可
      }
    }

    SimpleThread t = new SimpleThread();
    t.start();
    try {
      // 对已经启动过的线程再次 start，根据 JDK 规范将抛出 IllegalThreadStateException
      t.start();
      System.out.println("thread/start twice succeeded unexpectedly");
    } catch (IllegalThreadStateException e) {
      print("thread/className", e.getClass().getName());
      print("thread/message", e.getMessage());
      print("thread/toString", e.toString());
    } catch (Exception e) {
      // 若特定实现下未抛 IllegalThreadStateException，也记录实际异常类型
      print("thread/otherException", e.getClass().getName());
    }
  }
}

