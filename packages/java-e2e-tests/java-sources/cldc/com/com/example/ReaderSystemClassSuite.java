package com.com.example;

import java.io.IOException;
import java.io.Reader;

/**
 * 针对 jsjvm 中 java.io.Reader 系统类默认实现的边界测试。
 *
 * 说明：
 * - ReaderDecl 为 char 流抽象基类提供了一套“非常保守”的默认实现：
 *   read() / read(char[]) / read(char[],off,len) 默认返回 -1；
 *   skip(long) 默认返回 0；ready()/markSupported() 默认 false；
 *   mark/reset/close 为 no-op。
 * - 这里通过一个最小子类 PlainReader 来覆盖所有这些默认行为与边界情况，
 *   不触碰任何其他具体子类（如 InputStreamReader）。
 */
public class ReaderSystemClassSuite {

  private static String simpleName(Class c) {
    String n = c.getName();
    int i = n.lastIndexOf('.');
    if (i >= 0) {
      return n.substring(i + 1);
    }
    return n;
  }

  /**
   * 最小实现：只实现抽象方法 read(char[], int, int)，
   * 其它非抽象方法全部继承系统类实现。
   *
   * 注意：这里 read(...) 仅用于让类可实例化，本测试不会依赖其返回值语义。
   */
  private static class PlainReader extends Reader {
    
    public int read(char[] cbuf, int off, int len) throws IOException {
      // 为了避免影响系统类默认实现的语义，这里始终返回 -1。
      return -1;
    }

    
    public void close() throws IOException {
      // 交由系统类默认实现即可；这里不增加额外行为。
    }
  }

  public static void main(String[] args) throws Exception {
    testConstructorsAndReadyMarkFlags();
    testReadNoArgAndArrayDefaults();
    testReadArrayNullAndEmpty();
    testReadArrayOffsetLenDefaults();
    testSkipAndClose();
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
   * 构造方法与 ready()/markSupported() 的默认标志行为。
   */
  private static void testConstructorsAndReadyMarkFlags() throws Exception {
    System.out.println("=== ctorAndFlags ===");

    // 使用无参构造：对应 TS 中 <init>()V
    Reader r1 = new PlainReader();
    print("ctor1/ready", r1.ready());
    print("ctor1/markSupported", r1.markSupported());

    // 使用带 lock 对象的构造：对应 TS 中 <init>(Ljava/lang/Object;)V
    Object lock = new Object();
    Reader r2 = new Reader(lock) {
      
      public int read(char[] cbuf, int off, int len) throws IOException {
        return -1;
      }
      
      public void close() throws IOException {}
    };
    print("ctor2/ready", r2.ready());
    print("ctor2/markSupported", r2.markSupported());

    // mark / reset：jsjvm 为 no-op，标准 JDK 可能抛出 IOException
    try {
      r1.mark(1024);
      r1.reset();
      System.out.println("ctor/markReset_OK");
    } catch (IOException e) {
      System.out.println("ctor/markReset_exception=" + simpleName(e.getClass()));
    }
  }

  /**
   * read() / read(char[]) / read(char[],off,len) 在默认实现下的返回值：
   * - 均应返回 -1，表示无可读字符。
   */
  private static void testReadNoArgAndArrayDefaults() throws Exception {
    System.out.println("=== readDefaults ===");

    Reader r = new PlainReader();

    int r0 = r.read();
    print("readDefaults/readNoArg", r0);

    char[] buf = new char[4];
    int rArr = r.read(buf);
    print("readDefaults/readArray", rArr);

    int rOffLen = r.read(buf, 1, 2);
    print("readDefaults/readArrayOffLen", rOffLen);
  }

  /**
   * read(char[]) 针对 null 与长度为 0 的目标数组的行为：
   * - 空数组：默认实现直接返回 -1；
   * - null：可能抛出 NullPointerException 或其他异常，这里记录实际行为。
   */
  private static void testReadArrayNullAndEmpty() throws Exception {
    System.out.println("=== readArrayNullAndEmpty ===");

    Reader rNull = new PlainReader();
    try {
      int nNull = rNull.read((char[]) null);
      print("readNull/nNull", nNull);
    } catch (Exception e) {
      printException("readNull/exception", e);
    }

    Reader rEmpty = new PlainReader();
    char[] empty = new char[0];
    int nEmpty = rEmpty.read(empty);
    print("readEmpty/nEmpty", nEmpty);
  }

  /**
   * read(char[], off, len) 的默认行为：
   * - 未覆写时，Reader 的实现会做参数检查并调用 read()；
   * - 结合系统类中 read(char[],off,len) 直接返回 -1 的实现，
   *   这里主要检查参数非法与正常参数下的返回值。
   */
  private static void testReadArrayOffsetLenDefaults() throws Exception {
    System.out.println("=== readArrayOffsetLenDefaults ===");

    Reader r = new PlainReader();
    char[] buf = new char[5];

    // 合法 off/len，预期 -1
    int nValid = r.read(buf, 1, 3);
    print("offLen/valid", nValid);

    // off < 0
    try {
      int nNegOff = r.read(buf, -1, 2);
      print("offLen/negOff", nNegOff);
    } catch (Exception e) {
      printException("offLen/negOffEx", e);
    }

    // len < 0
    try {
      int nNegLen = r.read(buf, 0, -1);
      print("offLen/negLen", nNegLen);
    } catch (Exception e) {
      printException("offLen/negLenEx", e);
    }

    // off + len > buf.length
    try {
      int nOverflow = r.read(buf, 4, 2);
      print("offLen/overflow", nOverflow);
    } catch (Exception e) {
      printException("offLen/overflowEx", e);
    }
  }

  /**
   * skip(long) 与 close() 的默认行为：
   * - skip 对任何参数都应返回 0；
   * - close 为 no-op，关闭后仍可调用其他方法。
   */
  private static void testSkipAndClose() throws Exception {
    System.out.println("=== skipAndClose ===");

    Reader r = new PlainReader();

    print("skip/zero", r.skip(0L));
    print("skip/positive", r.skip(10L));
    // skip 负值：jsjvm 返回 0，标准 JDK 抛出 IllegalArgumentException
    try {
      print("skip/negative", r.skip(-5L));
    } catch (IllegalArgumentException e) {
      print("skip/negative", "exception:" + simpleName(e.getClass()));
    }

    r.close();

    // close 后再次调用 ready()/read()，应仍然可用（与 InputStream 行为一致）。
    print("afterClose/ready", r.ready());
    int rAfterClose = r.read();
    print("afterClose/read", rAfterClose);
  }
}
