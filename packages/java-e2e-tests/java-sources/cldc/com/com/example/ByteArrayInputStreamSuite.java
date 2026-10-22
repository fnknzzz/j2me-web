package com.com.example;

import java.io.ByteArrayInputStream;

/**
 * 针对 java.io.ByteArrayInputStream 的边界与异常行为测试。
 *
 * 目标：
 * - 覆盖所有构造方法与实例方法：
 *   <init>(byte[]), <init>(byte[], int, int), read()、read(byte[])、read(byte[], int, int)、
 *   available()、skip(long)、mark(int)、reset()、markSupported()、close()。
 * - 尽量覆盖 JDK 规范中的边界情况（负偏移、长度为 0、读到 EOF、负 skip 等）。
 * - 使用 System.out.println 打印可比对的文本快照，不在 Java 侧做断言。
 */
public class ByteArrayInputStreamSuite {

  public static void main(String[] args) throws Exception {
    testCtorBasic();
    testCtorOffsetLengthBounds();
    testSingleByteReadAndEOF();
    testReadIntoArray();
    testReadIntoArrayWithOffsetLength();
    testAvailableAndSkip();
    testMarkAndReset();
    testCloseDoesNotDisable();
    testNullBufferCtor();
    testReadNullArray();
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
    System.out.println(label + " throws " + e.getClass().getName());
  }

  /**
   * 基本构造与 available() 行为：空数组、普通数组。
   */
  private static void testCtorBasic() throws Exception {
    System.out.println("=== ctorBasic ===");

    byte[] empty = new byte[0];
    ByteArrayInputStream inEmpty = new ByteArrayInputStream(empty);
    print("ctorBasic/empty/available", inEmpty.available());
    print("ctorBasic/empty/read", inEmpty.read()); // 预期 -1

    byte[] data = new byte[] { 1, 2, 3 };
    ByteArrayInputStream in = new ByteArrayInputStream(data);
    print("ctorBasic/data/available", in.available());
    print("ctorBasic/data/read1", in.read());
    print("ctorBasic/data/read2", in.read());
    print("ctorBasic/data/read3", in.read());
    print("ctorBasic/data/readEOF", in.read());
  }

  /**
   * 带 offset/length 的构造函数：正常范围与越界场景。
   */
  private static void testCtorOffsetLengthBounds() throws Exception {
    System.out.println("=== ctorOffsetLength ===");

    byte[] data = new byte[] { 10, 20, 30, 40 };

    // 正常 offset/length
    ByteArrayInputStream in = new ByteArrayInputStream(data, 1, 2);
    print("ctorOffset/available", in.available());
    print("ctorOffset/read1", in.read());
    print("ctorOffset/read2", in.read());
    print("ctorOffset/readEOF", in.read());

    // 刚好覆盖到数组尾部
    ByteArrayInputStream inExact = new ByteArrayInputStream(data, 0, data.length);
    print("ctorOffsetExact/first", inExact.read());
    print("ctorOffsetExact/availableAfterFirst", inExact.available());

    // 下面三个场景在标准 JDK 中应抛出 IndexOutOfBoundsException
    try {
      new ByteArrayInputStream(data, -1, 2);
      System.out.println("ctorOffset/negOff OK");
    } catch (IndexOutOfBoundsException e) {
      printException("ctorOffset/negOff", e);
    }

    try {
      new ByteArrayInputStream(data, 1, -1);
      System.out.println("ctorOffset/negLen OK");
    } catch (IndexOutOfBoundsException e) {
      printException("ctorOffset/negLen", e);
    }

    try {
      new ByteArrayInputStream(data, 3, 5); // off + len 超过数组长度
      System.out.println("ctorOffset/overflow OK");
    } catch (IndexOutOfBoundsException e) {
      printException("ctorOffset/overflow", e);
    }
  }

  /**
   * read() 单字节读取：负字节、EOF 与 available() 配合。
   */
  private static void testSingleByteReadAndEOF() throws Exception {
    System.out.println("=== singleByteRead ===");

    byte[] data = new byte[] { 0, 1, -1, -128, 127 };
    ByteArrayInputStream in = new ByteArrayInputStream(data);

    print("single/availableStart", in.available());
    print("single/read0", in.read());
    print("single/read1", in.read());
    // 下面三个值应当按无符号 0-255 返回
    print("single/read2(-1)", in.read());
    print("single/read3(-128)", in.read());
    print("single/read4(127)", in.read());
    print("single/readEOF", in.read());
    print("single/availableAfterEOF", in.available());
  }

  /**
   * read(byte[])：
   * - 正常读取
   * - 空目标数组
   * - 读到 EOF 后再次 read()
   */
  private static void testReadIntoArray() throws Exception {
    System.out.println("=== readArray ===");

    byte[] src = new byte[] { 5, 6, 7, 8 };
    ByteArrayInputStream in = new ByteArrayInputStream(src);

    byte[] dst = new byte[4];
    int n1 = in.read(dst);
    print("readArray/n1", n1);
    print("readArray/dst[0]", dst[0]);
    print("readArray/dst[1]", dst[1]);
    print("readArray/dst[2]", dst[2]);
    print("readArray/dst[3]", dst[3]);

    // 读到 EOF 再读，应该返回 -1
    int n2 = in.read(dst);
    print("readArray/n2AfterEOF", n2);

    // 空目标数组，按 JDK 规范应立即返回 0
    ByteArrayInputStream in2 = new ByteArrayInputStream(src);
    byte[] emptyDst = new byte[0];
    int n3 = in2.read(emptyDst);
    print("readArray/emptyDst", n3);
  }

  /**
   * read(byte[], off, len)：
   * - 正常读取
   * - len 为 0
   * - off/len 越界时抛出 IndexOutOfBoundsException。
   */
  private static void testReadIntoArrayWithOffsetLength() throws Exception {
    System.out.println("=== readArrayOffsetLength ===");

    byte[] src = new byte[] { 10, 20, 30, 40 };
    ByteArrayInputStream in = new ByteArrayInputStream(src);
    byte[] dst = new byte[6];

    int n1 = in.read(dst, 1, 3);
    print("readOffset/n1", n1);
    print("readOffset/dst[0]", dst[0]);
    print("readOffset/dst[1]", dst[1]);
    print("readOffset/dst[2]", dst[2]);
    print("readOffset/dst[3]", dst[3]);
    print("readOffset/dst[4]", dst[4]);

    // len 为 0，按规范应立即返回 0，不读取任何字节
    int n2 = in.read(dst, 2, 0);
    print("readOffset/lenZero", n2);

    // off < 0
    try {
      in.read(dst, -1, 1);
      System.out.println("readOffset/negOff OK");
    } catch (IndexOutOfBoundsException e) {
      printException("readOffset/negOff", e);
    }

    // len < 0
    try {
      in.read(dst, 0, -1);
      System.out.println("readOffset/negLen OK");
    } catch (IndexOutOfBoundsException e) {
      printException("readOffset/negLen", e);
    }

    // off + len > dst.length
    try {
      in.read(dst, dst.length - 1, 2);
      System.out.println("readOffset/overflow OK");
    } catch (IndexOutOfBoundsException e) {
      printException("readOffset/overflow", e);
    }
  }

  /**
   * available() 与 skip(long)：
   * - 正常 skip
   * - skip 超过剩余长度
   * - skip 负数
   */
  private static void testAvailableAndSkip() throws Exception {
    System.out.println("=== availableSkip ===");

    byte[] src = new byte[] { 1, 2, 3, 4, 5 };
    ByteArrayInputStream in = new ByteArrayInputStream(src);

    print("skip/availableStart", in.available());

    long s1 = in.skip(2);
    print("skip/s1", s1);
    print("skip/availableAfterS1", in.available());
    print("skip/readAfterS1", in.read());

    long s2 = in.skip(10); // 超过剩余，可用字节应全部被跳过
    print("skip/s2", s2);
    print("skip/availableAfterS2", in.available());
    print("skip/readAfterS2", in.read());

    ByteArrayInputStream in2 = new ByteArrayInputStream(src);
    long sNeg = in2.skip(-3);
    print("skip/neg", sNeg);
    print("skip/negAvailable", in2.available());
  }

  /**
   * mark(int)、reset() 与 markSupported() 的行为：
   * - 支持 mark
   * - 多次 mark/reset
   * - 未显式 mark 时 reset 到起始位置。
   */
  private static void testMarkAndReset() throws Exception {
    System.out.println("=== markReset ===");

    byte[] src = new byte[] { 10, 20, 30, 40 };
    ByteArrayInputStream in = new ByteArrayInputStream(src);

    print("markReset/markSupported", in.markSupported());

    print("markReset/read0", in.read()); // 10
    in.mark(1000);
    print("markReset/read1", in.read()); // 20
    print("markReset/read2", in.read()); // 30
    in.reset();
    print("markReset/afterReset", in.read()); // 应回到 20
    print("markReset/afterResetNext", in.read()); // 30

    // 未显式 mark 的 reset 行为：默认为 0 位置
    ByteArrayInputStream in2 = new ByteArrayInputStream(src);
    in2.read(); // 读一个字节后直接 reset
    in2.reset();
    print("markReset/noMarkAfterReset", in2.read());
  }

  /**
   * close() 对 ByteArrayInputStream 按规范应当是无副作用的：
   * - close() 后仍可 read() / available()。
   */
  private static void testCloseDoesNotDisable() throws Exception {
    System.out.println("=== closeNoEffect ===");

    byte[] src = new byte[] { 1, 2, 3 };
    ByteArrayInputStream in = new ByteArrayInputStream(src);

    in.close();
    print("close/readAfterClose1", in.read());
    print("close/readAfterClose2", in.read());
    print("close/availableAfterReads", in.available());
  }

  /**
   * 构造函数传入 null 缓冲区：
   * - 在标准 JDK 中应抛出 NullPointerException。
   * - 这里不依赖具体实现，仅捕获并打印实际行为。
   */
  private static void testNullBufferCtor() throws Exception {
    System.out.println("=== ctorNullBuffer ===");

    try {
      ByteArrayInputStream in = new ByteArrayInputStream((byte[]) null);
      // 若未抛出异常，尝试一次 read() 观察行为
      print("ctorNullBuffer/read", in.read());
    } catch (NullPointerException e) {
      printException("ctorNullBuffer/null", e);
    }
  }

  /**
   * read(byte[]) / read(byte[], off, len) 传入 null：
   * - 在标准 InputStream 中应抛出 NullPointerException。
   */
  private static void testReadNullArray() throws Exception {
    System.out.println("=== readNullArray ===");

    byte[] src = new byte[] { 42, 43 };
    ByteArrayInputStream in = new ByteArrayInputStream(src);

    try {
      in.read((byte[]) null);
      System.out.println("readNull/array OK");
    } catch (NullPointerException e) {
      printException("readNull/array", e);
    }

    ByteArrayInputStream in2 = new ByteArrayInputStream(src);
    try {
      in2.read(null, 0, 1);
      System.out.println("readNull/arrayOffset OK");
    } catch (NullPointerException e) {
      printException("readNull/arrayOffset", e);
    }
  }
}
