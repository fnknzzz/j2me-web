package com.com.example;

import java.io.IOException;
import java.io.Writer;

/**
 * 针对 java.io.Writer 的系统类实现进行测试。
 *
 * 目标：
 * - 覆盖 decl 中声明的全部方法：
 *   <init>()、<init>(Object)、write(int)、write(char[])、write(char[], int, int)、
 *   write(String)、write(String, int, int)、flush()、close()。
 * - 尽量覆盖边界场景：null 字符数组、null 字符串、越界 off/len、len 为 0 等；
 * - 不在 Java 侧做断言，仅通过 System.out.println 打印快照，由上层测试比对。
 */
public class WriterSuite {

  public static void main(String[] args) throws Exception {
    testConstructors();
    testWriteIntAndCharArray();
    testWriteCharArrayOffsetLength();
    testWriteStringVariants();
    testFlushAndClose();
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
   * 构造函数：
   * - 通过子类 TrackingWriter 间接调用 Writer 的两个构造方法。
   */
  private static void testConstructors() throws Exception {
    System.out.println("=== constructors ===");

    TrackingWriter w1 = new TrackingWriter();
    print("ctor/default/notNull", w1 != null);

    Object lock = new Object();
    TrackingWriter w2 = new TrackingWriter(lock);
    print("ctor/lock/notNull", w2 != null);
  }

  /**
   * write(int) 与 write(char[])：
   * - 观察通过基类方法写入后，子类实现的 write(char[], int, int) 是否收到调用；
   * - 当前系统 decl 中这些方法为 native 空实现，真实行为以快照为准。
   */
  private static void testWriteIntAndCharArray() throws Exception {
    System.out.println("=== writeIntAndCharArray ===");

    TrackingWriter w = new TrackingWriter();

    // 单个字符写入
    w.write(65); // 'A'
    w.write(66); // 'B'

    // 整个字符数组写入
    char[] data = new char[] { 'X', 'Y', 'Z' };
    w.write(data);

    print("writeIntChar/buffer", w.getBuffer());
    print("writeIntChar/length", w.getBuffer().length());
  }

  /**
   * write(char[], int, int)：
   * - 直接调用子类实现，覆盖抽象方法签名；
   * - 对越界参数的行为由子类负责，这里只在正常与 len=0 场景下观察结果。
   */
  private static void testWriteCharArrayOffsetLength() throws Exception {
    System.out.println("=== writeCharArrayOffsetLength ===");

    TrackingWriter w = new TrackingWriter();
    char[] data = new char[] { 'a', 'b', 'c', 'd' };

    // 正常范围
    w.write(data, 1, 2);
    print("writeCII/normal/buffer", w.getBuffer());

    // len = 0，不应再追加内容
    int beforeLen = w.getBuffer().length();
    w.write(data, 2, 0);
    int afterLen = w.getBuffer().length();
    print("writeCII/len0/before", beforeLen);
    print("writeCII/len0/after", afterLen);
  }

  /**
   * write(String) 与 write(String, int, int)：
   * - 覆盖正常子串写入场景；
   * - 覆盖边界参数（负 offset/len、越界、空串、null）。
   */
  private static void testWriteStringVariants() throws Exception {
    System.out.println("=== writeStringVariants ===");

    TrackingWriter w = new TrackingWriter();

    // 正常写入
    w.write("ABC");
    w.write("0123456789", 2, 4); // 期望写入 "2345"
    print("writeString/normal/buffer", w.getBuffer());

    // 空字符串 + len=0
    TrackingWriter wEmpty = new TrackingWriter();
    wEmpty.write("", 0, 0);
    print("writeString/empty/buffer", wEmpty.getBuffer());

    // 边界：负 offset
    try {
      wEmpty.write("XYZ", -1, 1);
      print("writeString/negOff", "OK");
    } catch (IndexOutOfBoundsException e) {
      printException("writeString/negOff", e);
    } catch (Exception e) {
      printException("writeString/negOffOther", e);
    }

    // 边界：负 len
    try {
      wEmpty.write("XYZ", 0, -1);
      print("writeString/negLen", "OK");
    } catch (IndexOutOfBoundsException e) {
      printException("writeString/negLen", e);
    } catch (Exception e) {
      printException("writeString/negLenOther", e);
    }

    // 边界：off + len 超过字符串长度
    try {
      wEmpty.write("XYZ", 2, 5);
      print("writeString/overflow", "OK");
    } catch (IndexOutOfBoundsException e) {
      printException("writeString/overflow", e);
    } catch (Exception e) {
      printException("writeString/overflowOther", e);
    }

    // null 字符串
    try {
      wEmpty.write((String) null);
      print("writeString/null", "OK");
    } catch (NullPointerException e) {
      printException("writeString/null", e);
    } catch (Exception e) {
      printException("writeString/nullOther", e);
    }
  }

  /**
   * flush() 与 close()：
   * - Writer 的 flush()/close() 在 JDK 中是抽象方法，由子类实现；
   * - 这里通过 TrackingWriter 记录调用次数和关闭状态。
   */
  private static void testFlushAndClose() throws Exception {
    System.out.println("=== flushAndClose ===");

    TrackingWriter w = new TrackingWriter();

    print("flushClose/initialFlush", w.getFlushCount());
    print("flushClose/initialClosed", w.isClosed());

    w.flush();
    w.flush();
    print("flushClose/afterFlush", w.getFlushCount());

    w.close();
    print("flushClose/afterClose", w.isClosed());

    // 再次 close()，观察是否抛异常
    try {
      w.close();
      print("flushClose/closeTwice", "OK");
    } catch (IOException e) {
      printException("flushClose/closeTwice", e);
    }
  }

  /**
   * 子类，用于观察 write/flush/close 的调用情况。
   *
   * 注意：
   * - 这里只实现抽象方法，不依赖系统 decl 中的具体逻辑；
   * - 用于间接测试 Writer 上层 API（如 write(String, int, int)）在 jsjvm 上的行为。
   */
  public static class TrackingWriter extends Writer {
    private final StringBuffer buffer = new StringBuffer();
    private int flushCount = 0;
    private boolean closed = false;

    public TrackingWriter() {
      super();
    }

    public TrackingWriter(Object lock) {
      super(lock);
    }

    
    public void write(char[] cbuf, int off, int len) throws IOException {
      if (cbuf == null) {
        throw new NullPointerException("cbuf");
      }
      if (off < 0 || len < 0 || off + len > cbuf.length) {
        throw new IndexOutOfBoundsException();
      }
      buffer.append(cbuf, off, len);
    }

    
    public void flush() throws IOException {
      flushCount++;
    }

    
    public void close() throws IOException {
      closed = true;
    }

    public String getBuffer() {
      return buffer.toString();
    }

    public int getFlushCount() {
      return flushCount;
    }

    public boolean isClosed() {
      return closed;
    }
  }
}

