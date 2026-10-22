package com.com.example;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.UTFDataFormatException;

/**
 * 针对 jsjvm 中 java.io.DataInputStream 系统类实现的边界测试。
 *
 * 重点覆盖：
 * - 构造函数在 null/正常 InputStream 下的行为
 * - 所有声明方法的正常分支和异常分支（EOF / NPE / UTFDataFormatException 等）
 * - 与底层 InputStream 的交互（available/skip/mark/reset/readFully 等）
 */
public class DataInputStreamSystemClassSuite {

  public static void main(String[] args) {
    try {
      System.out.println("=== basic_primitives ===");
      testBasicPrimitives();

      System.out.println("=== read_char_and_arrays ===");
      testReadCharAndArrays();

      System.out.println("=== skip_and_mark ===");
      testSkipAndMark();

      System.out.println("=== null_stream_defaults ===");
      testNullStreamDefaults();

      System.out.println("=== eof_exceptions ===");
      testEOFExceptions();

      System.out.println("=== read_fully_variants ===");
      testReadFullyVariants();

      System.out.println("=== read_utf_variants ===");
      testReadUTFVariants();

      System.out.println("=== static_read_utf ===");
      testStaticReadUTF();
    } catch (Exception e) {
      // 防止单个 case 异常影响整体输出
      System.out.println("UNEXPECTED_EXCEPTION: " + e.getClass().getName());
      e.printStackTrace();
    }
  }

  // =============== 基础读写，覆盖各种基本类型 ===============

  private static void testBasicPrimitives() throws Exception {
    // 手动构造用于 readBoolean/readByte/readUnsignedByte/... 的字节序列
    byte[] data = new byte[]{
      0x01, // readBoolean -> true
      (byte) 0x80, // readByte -> -128
      (byte) 0xFF, // readUnsignedByte -> 255
      0x01, 0x02, // readShort -> 258
      0x00, 0x5A, // readUnsignedShort -> 90
      0x00, 'A', // readChar -> 'A' (65)
      0x01, 0x02, 0x03, 0x04, // readInt
      0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, // readLong
      // float 2.5f 的 IEEE754 表示
      0x40, 0x20, 0x00, 0x00,
      // double 3.25d 的 IEEE754 表示
      0x40, 0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    ByteArrayInputStream bis = new ByteArrayInputStream(data);
    DataInputStream dis = new DataInputStream(bis);

    System.out.println("available_before:" + dis.available());

    boolean z = dis.readBoolean();
    System.out.println("readBoolean:" + z);

    byte b = dis.readByte();
    System.out.println("readByte:" + b);

    int ub = dis.readUnsignedByte();
    System.out.println("readUnsignedByte:" + ub);

    short s = dis.readShort();
    System.out.println("readShort:" + s);

    int us = dis.readUnsignedShort();
    System.out.println("readUnsignedShort:" + us);

    char c = dis.readChar();
    System.out.println("readChar:" + (int) c);

    int i = dis.readInt();
    System.out.println("readInt:" + i);

    long l = dis.readLong();
    System.out.println("readLong:" + l);

    float f = dis.readFloat();
    System.out.println("readFloat:" + f);

    double d = dis.readDouble();
    System.out.println("readDouble:" + d);

    System.out.println("available_after:" + dis.available());
    dis.close();
  }

  // =============== 数组读写 & read() 重载 ===============

  private static void testReadCharAndArrays() throws Exception {
    byte[] data = new byte[]{
      'A', 'B', 'C', 'D', 'E'
    };

    DataInputStream dis1 = new DataInputStream(new ByteArrayInputStream(data));
    // read() 单字节
    int r1 = dis1.read();
    System.out.println("read_single:" + r1);

    byte[] buf = new byte[3];
    int n1 = dis1.read(buf);
    System.out.println("read_array_len:" + n1 + " values:" + buf[0] + "," + buf[1] + "," + buf[2]);
    dis1.close();

    DataInputStream dis2 = new DataInputStream(new ByteArrayInputStream(data));
    byte[] buf2 = new byte[5];
    int n2 = dis2.read(buf2, 1, 3);
    System.out.println("read_array_off_len:" + n2 + " values:" + buf2[0] + "," + buf2[1] + "," + buf2[2] + "," + buf2[3] + "," + buf2[4]);
    dis2.close();
  }

  // =============== skip / skipBytes / mark / reset / markSupported ===============

  private static void testSkipAndMark() throws Exception {
    byte[] data = new byte[]{10, 20, 30, 40, 50};
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));

    System.out.println("markSupported:" + dis.markSupported());
    dis.mark(100);
    int first = dis.read();
    System.out.println("read_before_reset:" + first);
    dis.reset();
    int again = dis.read();
    System.out.println("read_after_reset:" + again);

    long skipped = dis.skip(2L);
    System.out.println("skip_long:" + skipped + " next:" + dis.read());

    // skipBytes 边界：0、超过剩余长度
    int zero = dis.skipBytes(0);
    System.out.println("skipBytes_zero:" + zero);

    int over = dis.skipBytes(100);
    System.out.println("skipBytes_over:" + over);

    dis.close();
  }

  // =============== null InputStream 时的默认行为 ===============

  private static void testNullStreamDefaults() {
    // 构造函数允许 null，系统类会在 PD 中记录为 inStream == null
    DataInputStream dis = new DataInputStream((InputStream) null);

    // available/skip/mark/close 等应走到 "无效 inStream" 分支，返回默认值或直接返回
    try {
      System.out.println("null_available:" + dis.available());
    } catch (Exception e) {
      System.out.println("null_available_ex:" + e.getClass().getName());
    }

    long skipped;
    try {
      skipped = dis.skip(5L);
      System.out.println("null_skip_long:" + skipped);
    } catch (Exception e) {
      System.out.println("null_skip_long_ex:" + e.getClass().getName());
    }

    int skipBytes;
    try {
      skipBytes = dis.skipBytes(5);
      System.out.println("null_skipBytes:" + skipBytes);
    } catch (Exception e) {
      System.out.println("null_skipBytes_ex:" + e.getClass().getName());
    }

    try {
      dis.mark(10);
      dis.reset();
      dis.close();
      System.out.println("null_mark_reset_close:OK");
    } catch (Exception e) {
      System.out.println("null_mark_reset_close_ex:" + e.getClass().getName());
    }

    // readBoolean 在无效 inStream 时应返回 false，而不是抛异常
    try {
      boolean z = dis.readBoolean();
      System.out.println("null_readBoolean:" + z);
    } catch (Exception e) {
      System.out.println("null_readBoolean_ex:" + e.getClass().getName());
    }

    // read() 在无效 inStream 时应抛 NullPointerException
    try {
      dis.read();
      System.out.println("null_read_no_ex");
    } catch (Exception e) {
      System.out.println("null_read_ex:" + e.getClass().getName());
    }
  }

  // =============== EOF 分支：确保在数据不足时抛 EOFException ===============

  private interface IOOp {
    void run() throws Exception;
  }

  private static void expectEOF(String label, IOOp op) {
    try {
      op.run();
      System.out.println(label + ":NO_EOF");
    } catch (EOFException e) {
      System.out.println(label + ":EOFException");
    } catch (Exception e) {
      System.out.println(label + ":" + e.getClass().getName());
    }
  }

  private static void testEOFExceptions() {
    final byte[] empty = new byte[0];

    expectEOF("eof_readBoolean", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(empty)).readBoolean(); } });
    expectEOF("eof_readByte", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(empty)).readByte(); } });
    expectEOF("eof_readUnsignedByte", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(empty)).readUnsignedByte(); } });
    expectEOF("eof_readShort", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(empty)).readShort(); } });
    expectEOF("eof_readUnsignedShort", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(empty)).readUnsignedShort(); } });
    expectEOF("eof_readChar", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(empty)).readChar(); } });
    expectEOF("eof_readInt", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(empty)).readInt(); } });
    expectEOF("eof_readLong", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(empty)).readLong(); } });
    expectEOF("eof_readFloat", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(empty)).readFloat(); } });
    expectEOF("eof_readDouble", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(empty)).readDouble(); } });

    final byte[] buf = new byte[4];
    expectEOF("eof_readFully_array", new IOOp() { public void run() throws Exception {
      DataInputStream dis = new DataInputStream(new ByteArrayInputStream(new byte[]{1, 2}));
      dis.readFully(buf);
    } });

    final byte[] buf2 = new byte[4];
    expectEOF("eof_readFully_off_len", new IOOp() { public void run() throws Exception {
      DataInputStream dis = new DataInputStream(new ByteArrayInputStream(new byte[]{1, 2}));
      dis.readFully(buf2, 1, 3);
    } });

    final byte[] utfLenOnly = new byte[]{0x00, 0x01};
    expectEOF("eof_readUTF_len_only", new IOOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(utfLenOnly)).readUTF(); } });
  }

  // =============== readFully 变体：正常/异常路径 ===============

  private static void testReadFullyVariants() {
    // 正常场景：一次读取完整数组
    byte[] src = new byte[]{10, 20, 30, 40};
    DataInputStream dis1 = new DataInputStream(new ByteArrayInputStream(src));
    byte[] buf1 = new byte[4];
    try {
      dis1.readFully(buf1);
      System.out.println("readFully_ok:" + buf1[0] + "," + buf1[1] + "," + buf1[2] + "," + buf1[3]);
    } catch (Exception e) {
      System.out.println("readFully_ok_ex:" + e.getClass().getName());
    }

    // 正常场景：带 offset/length
    DataInputStream dis2 = new DataInputStream(new ByteArrayInputStream(src));
    byte[] buf2 = new byte[6];
    try {
      dis2.readFully(buf2, 1, 4);
      System.out.println("readFully_off_len_ok:" + buf2[0] + "," + buf2[1] + "," + buf2[2] + "," + buf2[3] + "," + buf2[4] + "," + buf2[5]);
    } catch (Exception e) {
      System.out.println("readFully_off_len_ok_ex:" + e.getClass().getName());
    }

    // 异常场景：readFully(byte[]) 传入 null，应抛 NullPointerException
    try {
      DataInputStream disNullBuf = new DataInputStream(new ByteArrayInputStream(src));
      disNullBuf.readFully((byte[]) null);
      System.out.println("readFully_null_buf_NO_EX");
    } catch (NullPointerException e) {
      System.out.println("readFully_null_buf_NPE");
    } catch (Exception e) {
      System.out.println("readFully_null_buf_ex:" + e.getClass().getName());
    }

    // 异常场景：readFully(byte[],off,len) 传入 null，根据系统类实现应抛 EOFException
    try {
      DataInputStream disNullBuf2 = new DataInputStream(new ByteArrayInputStream(src));
      disNullBuf2.readFully(null, 0, 1);
      System.out.println("readFully_null_off_len_NO_EX");
    } catch (EOFException e) {
      System.out.println("readFully_null_off_len_EOF");
    } catch (Exception e) {
      System.out.println("readFully_null_off_len_ex:" + e.getClass().getName());
    }
  }

  // =============== readUTF 的正常路径和 UTFDataFormatException 边界 ===============

  private interface UTFOp {
    void run() throws Exception;
  }

  private static void expectUTFDataFormat(String label, UTFOp op) {
    try {
      op.run();
      System.out.println(label + ":NO_UTF_ERROR");
    } catch (UTFDataFormatException e) {
      System.out.println(label + ":UTFDataFormatException");
    } catch (EOFException e) {
      System.out.println(label + ":EOFException");
    } catch (Exception e) {
      System.out.println(label + ":" + e.getClass().getName());
    }
  }

  private static void testReadUTFVariants() throws Exception {
    byte[] ascii = new byte[]{0x00, 0x03, 'A', 'B', 'C'};
    DataInputStream disAscii = new DataInputStream(new ByteArrayInputStream(ascii));
    String s1 = disAscii.readUTF();
    System.out.println("readUTF_ascii:" + s1);

    byte[] twoBytes = new byte[]{0x00, 0x02, (byte) 0xC2, (byte) 0xA9};
    DataInputStream disTwo = new DataInputStream(new ByteArrayInputStream(twoBytes));
    String s2 = disTwo.readUTF();
    System.out.println("readUTF_2bytes_len:" + s2.length());

    byte[] threeBytes = new byte[]{0x00, 0x03, (byte) 0xE2, (byte) 0x82, (byte) 0xAC};
    DataInputStream disThree = new DataInputStream(new ByteArrayInputStream(threeBytes));
    String s3 = disThree.readUTF();
    System.out.println("readUTF_3bytes_len:" + s3.length());

    final byte[] bad2 = new byte[]{0x00, 0x01, (byte) 0xC2};
    expectUTFDataFormat("utf_bad_2bytes_short", new UTFOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(bad2)).readUTF(); } });

    final byte[] bad3 = new byte[]{0x00, 0x02, (byte) 0xE2, (byte) 0x82};
    expectUTFDataFormat("utf_bad_3bytes_short", new UTFOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(bad3)).readUTF(); } });

    final byte[] badCont = new byte[]{0x00, 0x02, (byte) 0xC2, (byte) 0x20};
    expectUTFDataFormat("utf_bad_continuation", new UTFOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(badCont)).readUTF(); } });

    final byte[] badStart = new byte[]{0x00, 0x01, (byte) 0xF8};
    expectUTFDataFormat("utf_bad_start", new UTFOp() { public void run() throws Exception { new DataInputStream(new ByteArrayInputStream(badStart)).readUTF(); } });
  }

  // =============== 静态 readUTF(DataInput) 行为 ===============

  private static void testStaticReadUTF() throws Exception {
    // 正常路径：传入 DataInputStream 实例，应委托到实例方法
    byte[] ascii = new byte[]{0x00, 0x03, 'X', 'Y', 'Z'};
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(ascii));
    String s = DataInputStream.readUTF((DataInput) dis);
    System.out.println("static_readUTF_ok:" + s);

    // 异常路径：传入 null，根据系统类实现应抛 EOFException
    try {
      DataInputStream.readUTF((DataInput) null);
      System.out.println("static_readUTF_null_NO_EX");
    } catch (EOFException e) {
      System.out.println("static_readUTF_null_EOF");
    } catch (Exception e) {
      System.out.println("static_readUTF_null_ex:" + e.getClass().getName());
    }
  }
}

