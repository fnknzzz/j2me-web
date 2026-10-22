package com.com.example;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 针对 java.io.OutputStream 的系统类实现进行测试。
 *
 * 目标：
 * - 覆盖 decl 中声明的全部方法：
 *   <init>()、write(int)、write(byte[])、write(byte[], int, int)、flush()、close()。
 * - 尽量覆盖边界场景：null 数组、越界 off/len、len 为 0、多次调用 flush/close。
 * - 不在 Java 侧做断言，仅通过 System.out.println 打印快照，由上层测试比对。
 */
public class OutputStreamSuite {

  public static void main(String[] args) throws Exception {
    testConstructorAndBasicWriteInt();
    testWriteByteArrayAndNull();
    testWriteByteArrayOffsetLengthBounds();
    testFlushAndCloseAreNoop();
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
   * 构造函数与 write(int)：
   * - OutputStream 的 decl 中 <init>()V 与 write(int) 均为 native 且空实现。
   * - 本测试通过子类 TrackingOutputStream 观察 write(int) 的调用次数与值。
   */
  private static void testConstructorAndBasicWriteInt() throws Exception {
    System.out.println("=== ctorAndWriteInt ===");

    TrackingOutputStream out = new TrackingOutputStream();
    // 直接调用子类覆盖的 write(int)，不依赖系统类的空实现。
    out.write(65); // 'A'
    out.write(66); // 'B'
    out.write(255); // 边界值

    print("writeInt/count", out.getCount());
    print("writeInt/last", out.getLast());
  }

  /**
   * write(byte[])：
   * - decl 中 write(byte[]) 为 native 空实现。
   * - 子类未覆盖该方法时，调用 out.write(byte[]) 将触发系统类 native。
   * - 这里使用 try/catch 只观测是否抛出异常，记录为快照。
   */
  private static void testWriteByteArrayAndNull() throws Exception {
    System.out.println("=== writeByteArray ===");

    TrackingOutputStream out = new TrackingOutputStream();

    byte[] data = new byte[] { 1, 2, 3 };
    try {
      out.write(data);
      print("writeArray/normal", "OK");
    } catch (Exception e) {
      printException("writeArray/normal", e);
    }

    // null 数组：
    // - Java 规范通常要求抛 NullPointerException。
    // - 当前系统 decl 为空实现，本测试只记录实际行为。
    try {
      out.write((byte[]) null);
      print("writeArray/null", "OK");
    } catch (NullPointerException e) {
      printException("writeArray/null", e);
    } catch (Exception e) {
      printException("writeArray/nullOther", e);
    }
  }

  /**
   * write(byte[], off, len)：
   * - decl 中 write(byte[], int, int) 为 native 空实现。
   * - 标准行为应包含边界检查并抛出异常，这里只观测当前实现是否抛异常。
   */
  private static void testWriteByteArrayOffsetLengthBounds() throws Exception {
    System.out.println("=== writeByteArrayOffsetLength ===");

    TrackingOutputStream out = new TrackingOutputStream();
    byte[] data = new byte[] { 10, 20, 30 };

    // 正常 off/len
    try {
      out.write(data, 0, data.length);
      print("writeOffLen/normal", "OK");
    } catch (Exception e) {
      printException("writeOffLen/normal", e);
    }

    // len 为 0
    try {
      out.write(data, 1, 0);
      print("writeOffLen/len0", "OK");
    } catch (Exception e) {
      printException("writeOffLen/len0", e);
    }

    // off < 0
    try {
      out.write(data, -1, 1);
      print("writeOffLen/negOff", "OK");
    } catch (IndexOutOfBoundsException e) {
      printException("writeOffLen/negOff", e);
    } catch (Exception e) {
      printException("writeOffLen/negOffOther", e);
    }

    // len < 0
    try {
      out.write(data, 0, -1);
      print("writeOffLen/negLen", "OK");
    } catch (IndexOutOfBoundsException e) {
      printException("writeOffLen/negLen", e);
    } catch (Exception e) {
      printException("writeOffLen/negLenOther", e);
    }

    // off + len > data.length
    try {
      out.write(data, 2, 5);
      print("writeOffLen/overflow", "OK");
    } catch (IndexOutOfBoundsException e) {
      printException("writeOffLen/overflow", e);
    } catch (Exception e) {
      printException("writeOffLen/overflowOther", e);
    }
  }

  /**
   * flush() 与 close()：
   * - decl 中为 native 空实现。
   * - 通过子类覆盖 flush/close 记录调用次数，并调用 super 方法触发系统实现。
   */
  private static void testFlushAndCloseAreNoop() throws Exception {
    System.out.println("=== flushAndClose ===");

    TrackingOutputStream out = new TrackingOutputStream();

    print("flushClose/initialFlush", out.getFlushCount());
    print("flushClose/initialClosed", out.isClosed());

    out.flush();
    out.flush();
    print("flushClose/afterFlush", out.getFlushCount());

    out.close();
    print("flushClose/afterClose", out.isClosed());

    // 再次 close()，应当不抛异常
    try {
      out.close();
      print("flushClose/closeTwice", "OK");
    } catch (IOException e) {
      printException("flushClose/closeTwice", e);
    }
  }

  /**
   * 子类，用于观察 write(int)、flush()、close() 的调用。
   * 其中 write(int) 覆盖父类，实现为简单计数；
   * flush()/close() 通过 super 调用系统类实现，再记录状态。
   */
  public static class TrackingOutputStream extends OutputStream {
    private int count = 0;
    private int last = -1;
    private int flushCount = 0;
    private boolean closed = false;

    
    public void write(int b) throws IOException {
      count++;
      last = b & 0xFF;
    }

    
    public void flush() throws IOException {
      try {
        super.flush(); // 调用系统类 decl 中的 flush() native
      } finally {
        flushCount++;
      }
    }

    
    public void close() throws IOException {
      try {
        super.close(); // 调用系统类 decl 中的 close() native
      } finally {
        closed = true;
      }
    }

    public int getCount() {
      return count;
    }

    public int getLast() {
      return last;
    }

    public int getFlushCount() {
      return flushCount;
    }

    public boolean isClosed() {
      return closed;
    }
  }
}

