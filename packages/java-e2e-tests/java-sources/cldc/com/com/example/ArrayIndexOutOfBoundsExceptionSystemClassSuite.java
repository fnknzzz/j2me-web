package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.ArrayIndexOutOfBoundsException 系统类实现的测试。
 *
 * 重点覆盖：
 * - 无参构造的默认行为（由 initImpl 提供）
 * - 仅带 int 参数构造时，是否将 index 转为字符串写入 detailMessage
 * - 带 String 参数构造时，是否将原始字符串写入 detailMessage
 * - 传入 null String 时的边界行为
 * - 通过数组访问越界抛出的异常类型与 message 行为
 */
public class ArrayIndexOutOfBoundsExceptionSystemClassSuite {

  public static void main(String[] args) {
    testNoArgConstructor();
    testIntConstructorPositive();
    testIntConstructorNegative();
    testStringConstructor();
    testNullStringConstructor();
    testThrownByArrayAccess();
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
   * 无参构造：只调用 initImpl -> voidValue。
   * 验证：
   * - 类型正确
   * - 默认 message 行为（通常为 null）
   */
  private static void testNoArgConstructor() {
    System.out.println("=== noArgCtor ===");

    ArrayIndexOutOfBoundsException e = new ArrayIndexOutOfBoundsException();
    print("noArg/className", e.getClass().getName());
    print("noArg/message", e.getMessage());
    print("noArg/toString", e.toString());
  }

  /**
   * int 构造：TS 实现中会将 idx 转为字符串并写入 detailMessage。
   * 这里验证：正数 index 在 message 中是否可见。
   */
  private static void testIntConstructorPositive() {
    System.out.println("=== intCtorPositive ===");

    ArrayIndexOutOfBoundsException e = new ArrayIndexOutOfBoundsException(5);
    print("intPos/message", e.getMessage());
    print("intPos/toString", e.toString());
    boolean containsIndex = e.toString().indexOf("5") >= 0;
    print("intPos/toStringContainsIndex", containsIndex);
  }

  /**
   * int 构造：负数 index 同样应被转为字符串写入。
   */
  private static void testIntConstructorNegative() {
    System.out.println("=== intCtorNegative ===");

    ArrayIndexOutOfBoundsException e = new ArrayIndexOutOfBoundsException(-3);
    print("intNeg/message", e.getMessage());
    print("intNeg/toString", e.toString());
    boolean containsIndex = e.toString().indexOf("-3") >= 0;
    print("intNeg/toStringContainsIndex", containsIndex);
  }

  /**
   * String 构造：TS 实现中在 msg 为非 null 且 String 类型时设置 detailMessage。
   * 这里验证：message 与 toString() 是否包含原始字符串内容。
   */
  private static void testStringConstructor() {
    System.out.println("=== stringCtor ===");

    String msg = "index out of range";
    ArrayIndexOutOfBoundsException e = new ArrayIndexOutOfBoundsException(msg);
    print("string/message", e.getMessage());
    print("string/toString", e.toString());
    boolean contains = e.toString().indexOf("index out of range") >= 0;
    print("string/toStringContainsMsg", contains);
  }

  /**
   * 传入 null String：按 TS 实现，isObjectValue(msg) 为 false，
   * 不会设置 detailMessage，应与无参构造类似。
   */
  private static void testNullStringConstructor() {
    System.out.println("=== nullStringCtor ===");

    ArrayIndexOutOfBoundsException e = new ArrayIndexOutOfBoundsException((String) null);
    print("nullString/message", e.getMessage());
    print("nullString/toString", e.toString());
  }

  /**
   * 通过数组越界访问触发 ArrayIndexOutOfBoundsException，验证：
   * - 抛出的实际类型为 java.lang.ArrayIndexOutOfBoundsException
   * - message 是否包含具体下标信息（这依赖 VM 对异常构造的调用路径）。
   */
  private static void testThrownByArrayAccess() {
    System.out.println("=== thrownByArrayAccess ===");

    try {
      int[] arr = new int[3];
      int v = arr[5];
      System.out.println("arrayAccess/value = " + v);
    } catch (ArrayIndexOutOfBoundsException e) {
      print("arrayAccess/className", e.getClass().getName());
      print("arrayAccess/message", e.getMessage());
      print("arrayAccess/toString", e.toString());
    }
  }
}

