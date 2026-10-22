package com.com.example;

import java.io.IOException;
import java.io.InputStream;

/**
 * 针对 jsjvm 中 java.io.InputStream 系统类默认实现的边界测试。
 *
 * 说明：
 * - InputStreamDecl 为基类提供了一组“非常保守”的默认实现：
 *   read() 返回 -1、available() 返回 0、skip() 返回 0、mark* 为 no-op，
 *   read(byte[]) / read(byte[],off,len) 在未初始化 PD(buf/pos/count) 时统一返回 -1。
 * - 这里通过一个最小子类 PlainInputStream 来观察这些默认实现的实际行为，
 *   不测试任何子类（如 ByteArrayInputStream）的覆盖逻辑，避免越界到其它系统类。
 */
public class InputStreamSystemClassSuite {

  private static String simpleName(Class c) {
    String n = c.getName();
    int i = n.lastIndexOf('.');
    if (i >= 0) {
      return n.substring(i + 1);
    }
    return n;
  }

  /**
   * 最小实现：只实现抽象的 read()，其余方法全部继承自系统类实现。
   * 我们不会依赖 read() 本身的语义，只关心其他非抽象方法的行为。
   */
  private static class PlainInputStream extends InputStream {
    
    public int read() throws IOException {
      // 这里返回固定值，仅用于让类可实例化，测试不依赖该返回值。
      return -1;
    }
  }

  public static void main(String[] args) throws Exception {
    testTrivialDefaults();
    testReadArrayNullAndEmpty();
    testReadArrayWithoutPD();
    testReadArrayOffsetLenWithoutPD();
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
    System.out.println(label + " throws " + simpleName(e.getClass()));
  }

  /**
   * 构造函数与可用字节、skip、mark/reset/close 等简单方法的默认行为。
   */
  private static void testTrivialDefaults() throws Exception {
    System.out.println("=== trivialDefaults ===");

    InputStream in = new PlainInputStream();

    // available() 默认 0
    print("trivial/available", in.available());

    // markSupported() 默认 false（0）
    print("trivial/markSupported", in.markSupported());

    // mark / reset：jsjvm 为 no-op，标准 JDK 会抛出 IOException
    in.mark(100);
    try {
      in.reset();
      System.out.println("trivial/markReset_OK");
    } catch (IOException e) {
      System.out.println("trivial/markReset_exception=" + simpleName(e.getClass()));
    }

    // skip 对任意 long 参数都应返回 0
    print("trivial/skipZero", in.skip(0L));
    print("trivial/skipPositive", in.skip(10L));
    print("trivial/skipNegative", in.skip(-5L));

    // close() 为 no-op，关闭后仍可调用其他方法
    in.close();
    print("trivial/availableAfterClose", in.available());
  }

  /**
   * read(byte[]) 针对 null 与长度为 0 的目标数组的行为：
   * - 空数组应立即返回 0；
   * - null 数组在 InputStreamDecl 中会被视为 handle 不存在，返回 -1 而非 NPE。
   */
  private static void testReadArrayNullAndEmpty() throws Exception {
    System.out.println("=== readArrayNullAndEmpty ===");

    InputStream inNull = new PlainInputStream();
    try {
      int nNull = inNull.read((byte[]) null);
      print("readNull/nNull", nNull);
    } catch (Exception e) {
      // 若运行时选择抛出异常，也记录下来方便比对实现差异
      printException("readNull/exception", e);
    }

    InputStream inEmpty = new PlainInputStream();
    byte[] empty = new byte[0];
    int nEmpty = inEmpty.read(empty);
    print("readEmpty/nEmpty", nEmpty);
  }

  /**
   * read(byte[]) 在未初始化任何 PD(buf/pos/count) 的情况下，
   * 对非空目标数组的行为：预期返回 -1，表示无可读数据。
   */
  private static void testReadArrayWithoutPD() throws Exception {
    System.out.println("=== readArrayWithoutPD ===");

    InputStream in = new PlainInputStream();
    byte[] dst = new byte[4];
    int n = in.read(dst);
    print("noPD/readArray", n);
  }

  /**
   * read(byte[], off, len) 未初始化 PD 时的边界行为：
   * - 目标数组为 null：返回 -1 或抛异常（记录实际行为）；
   * - off/len 越界：按 InputStreamDecl 直接返回 -1，不抛 IndexOutOfBounds；
   * - off/len 合法但 PD 为空：返回 -1；
   */
  private static void testReadArrayOffsetLenWithoutPD() throws Exception {
    System.out.println("=== readArrayOffsetLenWithoutPD ===");

    byte[] dst = new byte[5];
    InputStream in = new PlainInputStream();

    // 目标数组为 null
    try {
      int nNull = in.read(null, 0, 1);
      print("offLen/nullDst", nNull);
    } catch (Exception e) {
      printException("offLen/nullDstEx", e);
    }

    // 合法下标但未初始化 PD：应返回 -1
    int nValid = in.read(dst, 1, 3);
    print("offLen/valid", nValid);

    // off < 0：jsjvm 返回 -1，标准 JDK 抛出 IndexOutOfBoundsException
    try {
      int nNegOff = in.read(dst, -1, 2);
      print("offLen/negOff", nNegOff);
    } catch (IndexOutOfBoundsException e) {
      print("offLen/negOff", "exception:" + simpleName(e.getClass()));
    }

    // len < 0：jsjvm 返回 -1，标准 JDK 抛出 IndexOutOfBoundsException
    try {
      int nNegLen = in.read(dst, 0, -1);
      print("offLen/negLen", nNegLen);
    } catch (IndexOutOfBoundsException e) {
      print("offLen/negLen", "exception:" + simpleName(e.getClass()));
    }

    // off + len > dst.length：jsjvm 返回 -1，标准 JDK 抛出 IndexOutOfBoundsException
    try {
      int nOverflow = in.read(dst, 4, 2);
      print("offLen/overflow", nOverflow);
    } catch (IndexOutOfBoundsException e) {
      print("offLen/overflow", "exception:" + simpleName(e.getClass()));
    }
  }
}
