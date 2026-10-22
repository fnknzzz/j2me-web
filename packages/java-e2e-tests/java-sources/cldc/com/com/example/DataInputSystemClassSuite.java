package com.com.example;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.UTFDataFormatException;

/**
 * 针对 jsjvm 中 java.io.DataInputStream 系统类的边界测试。
 *
 * 重点覆盖：
 * - 通过 DataInputStream 调用所有常见读方法
 * - EOFException / UTFDataFormatException 行为
 * - readFully / skipBytes 等方法
 */
public class DataInputSystemClassSuite {

  public static void main(String[] args) {
    try {
      System.out.println("=== basic_primitives ===");
      testBasicPrimitives();

      System.out.println("=== arrays_and_skipBytes ===");
      testArraysAndSkipBytes();

      System.out.println("=== eof_exceptions ===");
      testEOF();

      System.out.println("=== readUTF_variants ===");
      testReadUTF();
    } catch (Exception e) {
      System.out.println("UNEXPECTED_EXCEPTION: " + e.getClass().getName());
      e.printStackTrace();
    }
  }

  private static void testBasicPrimitives() throws Exception {
    byte[] data = new byte[]{
      0x01, // readBoolean -> true
      (byte) 0x80, // readByte -> -128
      (byte) 0xFF, // readUnsignedByte -> 255
      0x01, 0x02, // readShort
      0x00, 0x5A, // readUnsignedShort
      0x00, 'Z', // readChar -> 'Z'
      0x01, 0x02, 0x03, 0x04, // readInt
      0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, // readLong
      // float 2.5f
      0x40, 0x20, 0x00, 0x00,
      // double 3.25d
      0x40, 0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

    boolean z = in.readBoolean();
    System.out.println("readBoolean:" + z);

    byte b = in.readByte();
    System.out.println("readByte:" + b);

    int ub = in.readUnsignedByte();
    System.out.println("readUnsignedByte:" + ub);

    short s = in.readShort();
    System.out.println("readShort:" + s);

    int us = in.readUnsignedShort();
    System.out.println("readUnsignedShort:" + us);

    char c = in.readChar();
    System.out.println("readChar:" + (int) c);

    int i = in.readInt();
    System.out.println("readInt:" + i);

    long l = in.readLong();
    System.out.println("readLong:" + l);

    float f = in.readFloat();
    System.out.println("readFloat:" + f);

    double d = in.readDouble();
    System.out.println("readDouble:" + d);
  }

  private static void testArraysAndSkipBytes() throws Exception {
    byte[] src = new byte[]{10, 20, 30, 40, 50};
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(src));

    int s0 = in.skipBytes(0);
    System.out.println("skipBytes_zero:" + s0);

    int s1 = in.skipBytes(2);
    System.out.println("skipBytes_two:" + s1);

    int sBig = in.skipBytes(100);
    System.out.println("skipBytes_big:" + sBig);

    byte[] buf = new byte[3];
    DataInputStream in2 = new DataInputStream(new ByteArrayInputStream(new byte[]{1, 2, 3}));
    in2.readFully(buf);
    System.out.println("readFully_array:" + buf[0] + "," + buf[1] + "," + buf[2]);

    byte[] buf2 = new byte[5];
    DataInputStream in3 = new DataInputStream(new ByteArrayInputStream(new byte[]{4, 5, 6, 7}));
    in3.readFully(buf2, 1, 4);
    System.out.println("readFully_off_len:" + buf2[0] + "," + buf2[1] + "," + buf2[2] + "," + buf2[3] + "," + buf2[4]);

    try {
      DataInputStream inNull = new DataInputStream(new ByteArrayInputStream(new byte[]{1, 2}));
      inNull.readFully((byte[]) null);
      System.out.println("readFully_null_array_NO_EX");
    } catch (NullPointerException e) {
      System.out.println("readFully_null_array_NPE");
    }

    try {
      DataInputStream inNull2 = new DataInputStream(new ByteArrayInputStream(new byte[]{1, 2}));
      inNull2.readFully(null, 0, 1);
      System.out.println("readFully_null_off_len_NO_EX");
    } catch (EOFException e) {
      System.out.println("readFully_null_off_len_EOF");
    } catch (NullPointerException e) {
      System.out.println("readFully_null_off_len_NPE");
    }
  }

  private static void testEOF() {
    final byte[] empty = new byte[0];

    try { new DataInputStream(new ByteArrayInputStream(empty)).readBoolean(); System.out.println("eof_readBoolean:NO_EOF"); } catch (EOFException e) { System.out.println("eof_readBoolean:EOFException"); } catch (Exception e) { System.out.println("eof_readBoolean:" + e.getClass().getName()); }
    try { new DataInputStream(new ByteArrayInputStream(empty)).readByte(); System.out.println("eof_readByte:NO_EOF"); } catch (EOFException e) { System.out.println("eof_readByte:EOFException"); } catch (Exception e) { System.out.println("eof_readByte:" + e.getClass().getName()); }
    try { new DataInputStream(new ByteArrayInputStream(empty)).readUnsignedByte(); System.out.println("eof_readUnsignedByte:NO_EOF"); } catch (EOFException e) { System.out.println("eof_readUnsignedByte:EOFException"); } catch (Exception e) { System.out.println("eof_readUnsignedByte:" + e.getClass().getName()); }
    try { new DataInputStream(new ByteArrayInputStream(empty)).readShort(); System.out.println("eof_readShort:NO_EOF"); } catch (EOFException e) { System.out.println("eof_readShort:EOFException"); } catch (Exception e) { System.out.println("eof_readShort:" + e.getClass().getName()); }
    try { new DataInputStream(new ByteArrayInputStream(empty)).readUnsignedShort(); System.out.println("eof_readUnsignedShort:NO_EOF"); } catch (EOFException e) { System.out.println("eof_readUnsignedShort:EOFException"); } catch (Exception e) { System.out.println("eof_readUnsignedShort:" + e.getClass().getName()); }
    try { new DataInputStream(new ByteArrayInputStream(empty)).readChar(); System.out.println("eof_readChar:NO_EOF"); } catch (EOFException e) { System.out.println("eof_readChar:EOFException"); } catch (Exception e) { System.out.println("eof_readChar:" + e.getClass().getName()); }
    try { new DataInputStream(new ByteArrayInputStream(empty)).readInt(); System.out.println("eof_readInt:NO_EOF"); } catch (EOFException e) { System.out.println("eof_readInt:EOFException"); } catch (Exception e) { System.out.println("eof_readInt:" + e.getClass().getName()); }
    try { new DataInputStream(new ByteArrayInputStream(empty)).readLong(); System.out.println("eof_readLong:NO_EOF"); } catch (EOFException e) { System.out.println("eof_readLong:EOFException"); } catch (Exception e) { System.out.println("eof_readLong:" + e.getClass().getName()); }
    try { new DataInputStream(new ByteArrayInputStream(empty)).readFloat(); System.out.println("eof_readFloat:NO_EOF"); } catch (EOFException e) { System.out.println("eof_readFloat:EOFException"); } catch (Exception e) { System.out.println("eof_readFloat:" + e.getClass().getName()); }
    try { new DataInputStream(new ByteArrayInputStream(empty)).readDouble(); System.out.println("eof_readDouble:NO_EOF"); } catch (EOFException e) { System.out.println("eof_readDouble:EOFException"); } catch (Exception e) { System.out.println("eof_readDouble:" + e.getClass().getName()); }

    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[]{1}));
      byte[] buf = new byte[2];
      in.readFully(buf);
      System.out.println("eof_readFully_array:NO_EOF");
    } catch (EOFException e) {
      System.out.println("eof_readFully_array:EOFException");
    } catch (Exception e) {
      System.out.println("eof_readFully_array:" + e.getClass().getName());
    }

    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[]{1}));
      byte[] buf = new byte[3];
      in.readFully(buf, 1, 3);
      System.out.println("eof_readFully_off_len:NO_EOF");
    } catch (EOFException e) {
      System.out.println("eof_readFully_off_len:EOFException");
    } catch (Exception e) {
      System.out.println("eof_readFully_off_len:" + e.getClass().getName());
    }
  }

  private static void testReadUTF() throws Exception {
    byte[] ascii = new byte[]{0x00, 0x03, 'A', 'B', 'C'};
    DataInputStream inAscii = new DataInputStream(new ByteArrayInputStream(ascii));
    String s1 = inAscii.readUTF();
    System.out.println("readUTF_ascii:" + s1);

    byte[] twoBytes = new byte[]{0x00, 0x02, (byte) 0xC2, (byte) 0xA9};
    DataInputStream inTwo = new DataInputStream(new ByteArrayInputStream(twoBytes));
    String s2 = inTwo.readUTF();
    System.out.println("readUTF_2bytes_len:" + s2.length());

    byte[] threeBytes = new byte[]{0x00, 0x03, (byte) 0xE2, (byte) 0x82, (byte) 0xAC};
    DataInputStream inThree = new DataInputStream(new ByteArrayInputStream(threeBytes));
    String s3 = inThree.readUTF();
    System.out.println("readUTF_3bytes_len:" + s3.length());

    final byte[] bad2 = new byte[]{0x00, 0x01, (byte) 0xC2};
    try {
      DataInputStream inBad2 = new DataInputStream(new ByteArrayInputStream(bad2));
      inBad2.readUTF();
      System.out.println("utf_bad_2bytes_short:NO_UTF_ERROR");
    } catch (UTFDataFormatException e) {
      System.out.println("utf_bad_2bytes_short:UTFDataFormatException");
    } catch (EOFException e) {
      System.out.println("utf_bad_2bytes_short:EOFException");
    }

    final byte[] bad3 = new byte[]{0x00, 0x02, (byte) 0xE2, (byte) 0x82};
    try {
      DataInputStream inBad3 = new DataInputStream(new ByteArrayInputStream(bad3));
      inBad3.readUTF();
      System.out.println("utf_bad_3bytes_short:NO_UTF_ERROR");
    } catch (UTFDataFormatException e) {
      System.out.println("utf_bad_3bytes_short:UTFDataFormatException");
    } catch (EOFException e) {
      System.out.println("utf_bad_3bytes_short:EOFException");
    }

    final byte[] badCont = new byte[]{0x00, 0x02, (byte) 0xC2, (byte) 0x20};
    try {
      DataInputStream inBadCont = new DataInputStream(new ByteArrayInputStream(badCont));
      inBadCont.readUTF();
      System.out.println("utf_bad_continuation:NO_UTF_ERROR");
    } catch (UTFDataFormatException e) {
      System.out.println("utf_bad_continuation:UTFDataFormatException");
    } catch (EOFException e) {
      System.out.println("utf_bad_continuation:EOFException");
    }

    final byte[] badStart = new byte[]{0x00, 0x01, (byte) 0xF8};
    try {
      DataInputStream inBadStart = new DataInputStream(new ByteArrayInputStream(badStart));
      inBadStart.readUTF();
      System.out.println("utf_bad_start:NO_UTF_ERROR");
    } catch (UTFDataFormatException e) {
      System.out.println("utf_bad_start:UTFDataFormatException");
    } catch (EOFException e) {
      System.out.println("utf_bad_start:EOFException");
    }
  }
}
