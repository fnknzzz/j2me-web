package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.ClassNotFoundException 系统类实现的测试。
 *
 * 覆盖点：
 * - 无参构造（使用 initImpl）下的类型与默认 message 行为
 * - 带 String 参数构造对 detailMessage 的设置
 * - 传入 null String 的边界行为
 * - 通过 Class.forName 触发 ClassNotFoundException 时的类型与 message 行为
 */
public class ClassNotFoundExceptionSystemClassSuite {

  public static void main(String[] args) {
    testNoArgConstructor();
    testStringConstructor();
    testNullStringConstructor();
    testThrownByClassForName();
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
   * - 类型为 java.lang.ClassNotFoundException
   * - 默认 message（通常为 null）
   */
  private static void testNoArgConstructor() {
    System.out.println("=== noArgCtor ===");

    ClassNotFoundException e = new ClassNotFoundException();
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

    String msg = "target class not found";
    ClassNotFoundException e = new ClassNotFoundException(msg);
    print("string/className", e.getClass().getName());
    print("string/message", e.getMessage());
    print("string/toString", e.toString());

    boolean contains = e.toString().indexOf("target class not found") >= 0;
    print("string/toStringContainsMsg", contains);
  }

  /**
   * 传入 null 字符串：TS 侧实现只有在 msg 为非 null 且 String 类型时才设置 detailMessage。
   * 验证：
   * - 传入 null 不应导致崩溃
   * - getMessage() 行为与无参构造一致（通常为 null）
   */
  private static void testNullStringConstructor() {
    System.out.println("=== nullStringCtor ===");

    ClassNotFoundException e = new ClassNotFoundException((String) null);
    print("nullString/className", e.getClass().getName());
    print("nullString/message", e.getMessage());
    print("nullString/toString", e.toString());
  }

  /**
   * 通过 Class.forName 触发 ClassNotFoundException，验证：
   * - 实际抛出的类型为 java.lang.ClassNotFoundException
   * - message 中包含类名或其他可用信息。
   */
  private static void testThrownByClassForName() {
    System.out.println("=== thrownByClassForName ===");

    try {
      Class.forName("com.com.example.NonExistingClass_123456");
      System.out.println("Class.forName succeeded unexpectedly");
    } catch (ClassNotFoundException e) {
      print("forName/className", e.getClass().getName());
      print("forName/message", e.getMessage());
      print("forName/toString", e.toString());
    }
  }
}

