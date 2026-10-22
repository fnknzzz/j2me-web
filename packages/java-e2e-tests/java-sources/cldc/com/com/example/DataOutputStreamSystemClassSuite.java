package com.com.example;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;

public class DataOutputStreamSystemClassSuite {

  public static void main(String[] args) {
    try {
      System.out.println("=== ctor_and_null_stream ===");
      testConstructorAndNullStream();

      System.out.println("=== primitives_write ===");
      testPrimitivesWrite();

      System.out.println("=== byte_array_variants ===");
      testByteArrayVariants();

      System.out.println("=== string_and_utf ===");
      testStringAndUTF();

      System.out.println("=== flush_and_close ===");
      testFlushAndClose();
    } catch (Exception e) {
      System.out.println("UNEXPECTED_EXCEPTION: " + e.getClass().getName());
      e.printStackTrace();
    }
  }

  private static void testConstructorAndNullStream() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    System.out.println("ctor_normal_initial_len=" + baos.toByteArray().length);

    dos.write(0x7F);
    System.out.println("after_write_int_baos_len=" + baos.toByteArray().length);

    DataOutputStream dosNull = new DataOutputStream((OutputStream) null);

    try {
      dosNull.writeBoolean(true);
      dosNull.writeByte(1);
      dosNull.writeShort(2);
      dosNull.writeChar('A');
      dosNull.writeInt(0x12345678);
      dosNull.writeLong(123L);
      dosNull.writeFloat(1.0f);
      dosNull.writeDouble(2.0);
      dosNull.write(new byte[]{1, 2, 3});
      dosNull.write(new byte[]{1, 2, 3}, 0, 2);
      dosNull.writeChars("XY");
      dosNull.writeUTF("hello");
      dosNull.flush();
      dosNull.close();
      System.out.println("null_stream_ops_completed_without_throw");
    } catch (Exception e) {
      System.out.println("null_stream_ops_exception=" + e.getClass().getName());
    }
  }

  private static void testPrimitivesWrite() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    System.out.println("len_initial=" + baos.toByteArray().length);

    dos.writeBoolean(true);
    System.out.println("len_after_writeBoolean_true=" + baos.toByteArray().length);

    dos.writeBoolean(false);
    System.out.println("len_after_writeBoolean_false=" + baos.toByteArray().length);

    dos.writeByte(0xFF);
    System.out.println("len_after_writeByte=" + baos.toByteArray().length);

    dos.writeShort(0x1234);
    System.out.println("len_after_writeShort=" + baos.toByteArray().length);

    dos.writeChar('Z');
    System.out.println("len_after_writeChar=" + baos.toByteArray().length);

    dos.writeInt(0x01020304);
    System.out.println("len_after_writeInt=" + baos.toByteArray().length);

    dos.writeLong(0x0102030405060708L);
    System.out.println("len_after_writeLong=" + baos.toByteArray().length);

    dos.writeFloat(1.5f);
    System.out.println("len_after_writeFloat=" + baos.toByteArray().length);

    dos.writeDouble(1.5d);
    System.out.println("len_after_writeDouble=" + baos.toByteArray().length);

    byte[] bytes = baos.toByteArray();
    System.out.println("baos_len_after_primitives=" + bytes.length);

    System.out.print("first_32_bytes_unsigned=");
    for (int i = 0; i < bytes.length && i < 32; i++) {
      if (i > 0) System.out.print(",");
      System.out.print(bytes[i] & 0xFF);
    }
    System.out.println();
  }

  private static void testByteArrayVariants() throws Exception {
    byte[] data = new byte[]{10, 20, 30, 40, 50};

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.write(data);
    System.out.println("len_after_write_array=" + baos.toByteArray().length);
    System.out.print("bytes_after_write_array=");
    byte[] bytes1 = baos.toByteArray();
    for (int i = 0; i < bytes1.length; i++) {
      if (i > 0) System.out.print(",");
      System.out.print(bytes1[i] & 0xFF);
    }
    System.out.println();

    dos.write(data, 1, 3);
    System.out.println("len_after_write_array_off_len=" + baos.toByteArray().length);
    System.out.print("bytes_after_write_array_off_len=");
    byte[] bytes2 = baos.toByteArray();
    for (int i = 0; i < bytes2.length; i++) {
      if (i > 0) System.out.print(",");
      System.out.print(bytes2[i] & 0xFF);
    }
    System.out.println();

    int lenBeforeErrors = baos.toByteArray().length;

    try {
      dos.write(data, -1, 1);
      System.out.println("write_off_negative:NO_EXCEPTION");
    } catch (Exception e) {
      System.out.println("write_off_negative:" + e.getClass().getName());
    }
    System.out.println("len_after_write_off_negative=" + baos.toByteArray().length);

    try {
      dos.write(data, 0, -1);
      System.out.println("write_len_negative:NO_EXCEPTION");
    } catch (Exception e) {
      System.out.println("write_len_negative:" + e.getClass().getName());
    }
    System.out.println("len_after_write_len_negative=" + baos.toByteArray().length);

    try {
      dos.write(data, 3, 5);
      System.out.println("write_off_plus_len_too_big:NO_EXCEPTION");
    } catch (Exception e) {
      System.out.println("write_off_plus_len_too_big:" + e.getClass().getName());
    }
    System.out.println("len_after_write_off_plus_len_too_big=" + baos.toByteArray().length);

    byte[] nullArr = null;
    try {
      dos.write(nullArr);
      System.out.println("write_null_array:NO_EXCEPTION");
    } catch (Exception e) {
      System.out.println("write_null_array:" + e.getClass().getName());
    }
    System.out.println("len_after_write_null_array=" + baos.toByteArray().length);

    try {
      dos.write(nullArr, 0, 1);
      System.out.println("write_null_array_off_len:NO_EXCEPTION");
    } catch (Exception e) {
      System.out.println("write_null_array_off_len:" + e.getClass().getName());
    }
    System.out.println("len_after_write_null_array_off_len=" + baos.toByteArray().length);

    System.out.println("len_before_errors=" + lenBeforeErrors);
  }

  private static void testStringAndUTF() throws Exception {
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      String s = "A中";
      dos.writeChars(s);
      System.out.println("writeChars_len=" + baos.toByteArray().length);
      byte[] bytes = baos.toByteArray();
      System.out.print("writeChars_bytes_unsigned=");
      for (int i = 0; i < bytes.length; i++) {
        if (i > 0) System.out.print(",");
        System.out.print(bytes[i] & 0xFF);
      }
      System.out.println();
    }

    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      String s = "A" + '\u0000' + "中";
      s = "A" + '\u0000' + "中";
      dos.writeUTF(s);
      System.out.println("writeUTF_len=" + baos.toByteArray().length);
      byte[] bytes = baos.toByteArray();
      System.out.print("writeUTF_length_prefix_and_bytes=");
      for (int i = 0; i < bytes.length; i++) {
        if (i > 0) System.out.print(",");
        System.out.print(bytes[i] & 0xFF);
      }
      System.out.println();
    }
  }

  private static void testFlushAndClose() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeInt(0x01020304);
    System.out.println("len_before_flush_close=" + baos.toByteArray().length);

    dos.flush();
    System.out.println("len_after_flush=" + baos.toByteArray().length);

    dos.close();
    System.out.println("len_after_close=" + baos.toByteArray().length);

    try {
      dos.writeInt(0x05060708);
      System.out.println("write_after_close:NO_EXCEPTION len=" + baos.toByteArray().length);
    } catch (Exception e) {
      System.out.println("write_after_close:" + e.getClass().getName());
    }

    byte[] bytes = baos.toByteArray();
    System.out.print("bytes_after_close_unsigned=");
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) System.out.print(",");
      System.out.print(bytes[i] & 0xFF);
    }
    System.out.println();
  }
}
