package com.com.example;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 针对 java.io.InputStreamReader 的系统类实现进行测试。
 *
 * 目标：
 * - 覆盖 decl 中声明的全部方法：
 *   <init>(InputStream)、<init>(InputStream, String)、read()、read(char[], int, int)、
 *   ready()、close()、markSupported()、mark(int)、reset()、skip(long)。
 * - 尽量覆盖边界场景：null 输入流、null/越界字符数组、len 为 0、EOF、负 skip 等。
 * - 不在 Java 侧做断言，仅通过 System.out.println 打印快照，由上层测试比对。
 */
public class InputStreamReaderSuite {

  private static String simpleName(Class c) {
    String n = c.getName();
    int i = n.lastIndexOf('.');
    if (i >= 0) {
      return n.substring(i + 1);
    }
    return n;
  }

  public static void main(String[] args) throws Exception {
    testConstructorsAndNullStream();
    testReadCharArrayAndBounds();
    testReadyAndClose();
    testMarkResetAndMarkSupported();
    testSkipVariants();
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
   * 构造函数行为：
   * - 基本 InputStream 构造
   * - 带编码参数的构造
   * - 输入流为 null 时的行为（标准 JDK 通常抛 NPE，本实现可能存储 null PD）。
   */
  private static void testConstructorsAndNullStream() throws Exception {
    System.out.println("=== constructors ===");

    // 基本构造：桥接到 ByteArrayInputStream.read()
    byte[] data = new byte[] { 65, 66, 67 }; // 'A', 'B', 'C'
    InputStreamReader r1 = new InputStreamReader(new ByteArrayInputStream(data));
    print("ctor/basic/read1", r1.read());
    print("ctor/basic/read2", r1.read());
    print("ctor/basic/read3", r1.read());
    print("ctor/basic/readEOF", r1.read());

    // 带编码参数的构造：当前系统实现忽略编码，但应能正常工作
    byte[] data2 = new byte[] { 70, 71 }; // 'F', 'G'
    InputStreamReader r2 = new InputStreamReader(new ByteArrayInputStream(data2), "UTF-8");
    print("ctor/encoding/read1", r2.read());
    print("ctor/encoding/read2", r2.read());
    print("ctor/encoding/readEOF", r2.read());

    // 输入流为 null：
    // - 标准 JDK 行为通常为抛出 NullPointerException
    // - 本系统类实现会在 PD 中保存 null，read()/ready()/skip() 走 "无 inStream" 分支。
    try {
      InputStreamReader rNull = new InputStreamReader((InputStream) null);
      print("ctor/null/read", rNull.read());
      print("ctor/null/ready", rNull.ready());
      print("ctor/null/skip", rNull.skip(5L));
    } catch (NullPointerException e) {
      printException("ctor/null", e);
    }

    try {
      InputStreamReader rNullEnc =
          new InputStreamReader((InputStream) null, "UTF-8");
      print("ctor/nullEnc/read", rNullEnc.read());
      print("ctor/nullEnc/ready", rNullEnc.ready());
      print("ctor/nullEnc/skip", rNullEnc.skip(3L));
    } catch (NullPointerException e) {
      printException("ctor/nullEnc", e);
    }
  }

  /**
   * read(char[], off, len)：
   * - 正常读取
   * - len 为 0
   * - off/len 越界
   * - cbuf 为 null
   */
  private static void testReadCharArrayAndBounds() throws Exception {
    System.out.println("=== readCharArray ===");

    // 正常读取 + EOF 分支
    byte[] bytes = new byte[] { 65, 66, 67, 68 }; // A, B, C, D
    InputStreamReader r = new InputStreamReader(new ByteArrayInputStream(bytes), "UTF-8");
    char[] buf = new char[6];

    int n1 = r.read(buf, 1, 3);
    print("read/normal/n1", n1);
    print("read/normal/buf1", (int) buf[1]);
    print("read/normal/buf2", (int) buf[2]);
    print("read/normal/buf3", (int) buf[3]);
    print("read/normal/buf0", (int) buf[0]);

    // 再次读取，触发 "部分读取后遇到 EOF" 分支
    int n2 = r.read(buf, 4, 2);
    print("read/normal/n2", n2);
    print("read/normal/buf4", (int) buf[4]);

    // len 为 0：当前实现 for 循环不会执行，totalRead 为 0，返回 -1
    InputStreamReader rLen0 =
        new InputStreamReader(new ByteArrayInputStream(new byte[] { 1, 2 }), "UTF-8");
    char[] bufLen0 = new char[2];
    int nLen0 = rLen0.read(bufLen0, 0, 0);
    print("read/len0", nLen0);

    // off/len 越界：jsjvm 返回 -1，标准 JDK 抛出 IndexOutOfBoundsException
    InputStreamReader rBounds =
        new InputStreamReader(new ByteArrayInputStream(new byte[] { 10, 20, 30 }), "UTF-8");
    char[] bufBounds = new char[3];

    try {
      int nNegOff = rBounds.read(bufBounds, -1, 1);
      print("read/bounds/negOff", nNegOff);
    } catch (IndexOutOfBoundsException e) {
      print("read/bounds/negOff", "exception:" + simpleName(e.getClass()));
    }

    try {
      int nNegLen = rBounds.read(bufBounds, 0, -1);
      print("read/bounds/negLen", nNegLen);
    } catch (IndexOutOfBoundsException e) {
      print("read/bounds/negLen", "exception:" + simpleName(e.getClass()));
    }

    try {
      int nOverflow = rBounds.read(bufBounds, 2, 2);
      print("read/bounds/overflow", nOverflow);
    } catch (IndexOutOfBoundsException e) {
      print("read/bounds/overflow", "exception:" + simpleName(e.getClass()));
    }

    // cbuf 为 null：
    // - 标准 Reader 实现通常抛 NullPointerException
    // - 当前系统实现对 null 直接返回 -1。
    InputStreamReader rNullBuf =
        new InputStreamReader(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), "UTF-8");
    try {
      int nNull = rNullBuf.read((char[]) null, 0, 1);
      print("read/nullBuf/ret", nNull);
    } catch (NullPointerException e) {
      printException("read/nullBuf", e);
    }
  }

  /**
   * ready() 与 close()：
   * - ready() 根据底层 InputStream.available() 判断是否有数据。
   * - close() 应调用底层 InputStream.close()。
   */
  private static void testReadyAndClose() throws Exception {
    System.out.println("=== readyAndClose ===");

    // 使用 ByteArrayInputStream：available() 从非 0 变为 0
    byte[] data = new byte[] { 10, 11 };
    InputStreamReader r = new InputStreamReader(new ByteArrayInputStream(data));
    print("ready/basic/start", r.ready());
    r.read();
    print("ready/basic/afterOne", r.ready());
    r.read();
    print("ready/basic/afterTwo", r.ready());

    // 底层为 null（如果构造成功）时，ready() 应返回 false
    try {
      InputStreamReader rNull = new InputStreamReader((InputStream) null);
      print("ready/nullStream", rNull.ready());
    } catch (NullPointerException e) {
      printException("ready/nullStream", e);
    }

    // 使用自定义 InputStream 来观察 close() 是否被调用
    CloseTrackingInputStream tracking =
        new CloseTrackingInputStream(new byte[] { 1, 2, 3 });
    InputStreamReader rClose = new InputStreamReader(tracking);
    print("close/tracking/before", tracking.isClosed());
    rClose.close();
    print("close/tracking/after", tracking.isClosed());
  }

  /**
   * markSupported()/mark()/reset()：
   * - 当前系统实现 markSupported() 恒为 false，mark/reset 为空操作。
   * - 通过读数据前后调用 mark/reset，观察读取顺序是否受到影响。
   */
  private static void testMarkResetAndMarkSupported() throws Exception {
    System.out.println("=== markReset ===");

    byte[] data = new byte[] { 65, 66, 67 }; // A, B, C
    InputStreamReader r = new InputStreamReader(new ByteArrayInputStream(data));

    print("markReset/markSupported", r.markSupported());

    int first = r.read();
    // mark/reset：jsjvm 可能支持，标准 JDK 的 InputStreamReader 不支持
    try {
      r.mark(100);
      int second = r.read();
      r.reset();
      int third = r.read();

      print("markReset/first", first);
      print("markReset/second", second);
      print("markReset/third", third);
    } catch (IOException e) {
      print("markReset/first", first);
      print("markReset/second", "exception:" + simpleName(e.getClass()));
      print("markReset/third", "exception:" + simpleName(e.getClass()));
    }
  }

  /**
   * skip(long)：
   * - 正常跳过若干字节
   * - 跳过超过剩余字节数
   * - 传入 0 或负值
   * - 底层 InputStream 为 null。
   */
  private static void testSkipVariants() throws Exception {
    System.out.println("=== skipVariants ===");

    byte[] data = new byte[] { 1, 2, 3, 4, 5 };
    InputStreamReader r = new InputStreamReader(new ByteArrayInputStream(data));

    long s1 = r.skip(2L);
    print("skip/basic/s1", s1);
    print("skip/basic/afterS1Read", r.read());

    long s2 = r.skip(10L);
    print("skip/basic/s2", s2);
    print("skip/basic/afterS2Read", r.read());

    long sZero = r.skip(0L);
    print("skip/basic/zero", sZero);
    // skip 负值：jsjvm 返回 0，标准 JDK 抛出 IllegalArgumentException
    try {
      long sNeg = r.skip(-5L);
      print("skip/basic/neg", sNeg);
    } catch (IllegalArgumentException e) {
      print("skip/basic/neg", "exception:" + simpleName(e.getClass()));
    }

    // 底层 InputStream 为 null：skip 应返回 0
    try {
      InputStreamReader rNull = new InputStreamReader((InputStream) null);
      print("skip/nullStream", rNull.skip(3L));
    } catch (NullPointerException e) {
      printException("skip/nullStream", e);
    }
  }

  /**
   * 自定义 InputStream，用于观察 close() 是否被 InputStreamReader 透传调用。
   */
  public static class CloseTrackingInputStream extends InputStream {
    private final ByteArrayInputStream delegate;
    private boolean closed;

    public CloseTrackingInputStream(byte[] data) {
      this.delegate = new ByteArrayInputStream(data);
      this.closed = false;
    }

    
    public int read() throws IOException {
      return delegate.read();
    }

    
    public int available() throws IOException {
      return delegate.available();
    }

    
    public long skip(long n) throws IOException {
      return delegate.skip(n);
    }

    
    public void close() throws IOException {
      this.closed = true;
      delegate.close();
    }

    public boolean isClosed() {
      return closed;
    }
  }
}
