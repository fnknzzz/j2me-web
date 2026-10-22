package com.com.example;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * 针对 jsjvm 中 java.io.OutputStreamWriter 系统类实现的边界测试。
 *
 * 重点覆盖：
 * - 两个构造函数：普通构造与带编码参数构造（编码参数在系统类中被忽略）
 * - write(int)、write(char[],off,len)、write(String,off,len) 的正常路径与边界 off/len 行为
 * - 底层 OutputStream 为 null 时，PD.out 为空分支（所有操作应被静默忽略）
 * - flush()/close() 在有/无底层流时的表现
 */
public class OutputStreamWriterSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== ctor_and_null_out ===");
    testConstructorAndNullOut();

    System.out.println("=== write_int_and_mask ===");
    testWriteIntAndMask();

    System.out.println("=== write_char_array_offsets ===");
    testWriteCharArrayOffsets();

    System.out.println("=== write_string_offsets ===");
    testWriteStringOffsets();

    System.out.println("=== flush_and_close_behaviors ===");
    testFlushAndCloseBehaviors();
  }

  /**
   * 覆盖：
   * - new OutputStreamWriter(OutputStream)
   * - new OutputStreamWriter(OutputStream,String)
   * - OutputStream 为 null 时，构造是否允许以及后续写入是否静默忽略。
   */
  private static void testConstructorAndNullOut() {
    try {
      ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
      OutputStreamWriter w1 = new OutputStreamWriter(baos1);
      w1.write('A');
      w1.flush();
      byte[] b1 = baos1.toByteArray();
      System.out.println("ctor_normal_bytes_len=" + b1.length);
      if (b1.length > 0) {
        System.out.println("ctor_normal_first_byte=" + (b1[0] & 0xFF));
      }
    } catch (Exception e) {
      System.out.println("ctor_normal_ex=" + e.getClass().getName());
    }

    try {
      ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
      OutputStreamWriter w2 = new OutputStreamWriter(baos2, "UTF-8");
      w2.write('B');
      w2.flush();
      byte[] b2 = baos2.toByteArray();
      System.out.println("ctor_encoding_bytes_len=" + b2.length);
      if (b2.length > 0) {
        System.out.println("ctor_encoding_first_byte=" + (b2[0] & 0xFF));
      }
    } catch (Exception e) {
      System.out.println("ctor_encoding_ex=" + e.getClass().getName());
    }

    // OutputStream 为 null，按照系统类实现 PD.out 将被设置为 null，后续 write/flush/close 应静默返回
    try {
      OutputStream nullOut = null;
      OutputStreamWriter wNull = new OutputStreamWriter(nullOut);
      wNull.write('C');
      wNull.flush();
      wNull.close();
      System.out.println("ctor_null_out_completed_without_ex");
    } catch (Exception e) {
      System.out.println("ctor_null_out_ex=" + e.getClass().getName());
    }
  }

  /**
   * 覆盖 write(int) 的行为：
   * - 仅保留低 8 位写入底层 OutputStream（& 0xFF）
   * - 多次写入的顺序与截断行为。
   */
  private static void testWriteIntAndMask() {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      OutputStreamWriter w = new OutputStreamWriter(baos);

      w.write(0x01FF); // 预期写入 0xFF
      w.write(-1);     // 预期写入 0xFF
      w.write(0x0123); // 预期写入 0x23
      w.flush();

      byte[] bytes = baos.toByteArray();
      System.out.println("write_int_mask_len=" + bytes.length);
      System.out.print("write_int_mask_unsigned=");
      for (int i = 0; i < bytes.length; i++) {
        if (i > 0) System.out.print(",");
        System.out.print(bytes[i] & 0xFF);
      }
      System.out.println();
    } catch (Exception e) {
      System.out.println("write_int_mask_ex=" + e.getClass().getName());
    }
  }

  /**
   * 覆盖 write(char[],off,len)：
   * - 正常范围写入，验证偏移与长度生效
   * - off/len 越界时，系统类实现选择直接返回，不写入任何内容（与 JDK 抛异常语义不同），用输出观察差异
   * - cbuf 为 null 时，系统类实现会提前返回，不写入内容。
   */
  private static void testWriteCharArrayOffsets() {
    char[] buf = new char[]{'A', 'B', 'C', 'D', 'E'};

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      OutputStreamWriter w = new OutputStreamWriter(baos);

      // 正常写入中间一段 "BCD"
      w.write(buf, 1, 3);
      w.flush();
      byte[] bytes1 = baos.toByteArray();
      System.out.println("write_char_valid_len=" + bytes1.length);
      System.out.print("write_char_valid_bytes=");
      for (int i = 0; i < bytes1.length; i++) {
        if (i > 0) System.out.print(",");
        System.out.print(bytes1[i] & 0xFF);
      }
      System.out.println();

      int sizeBeforeErrors = bytes1.length;

      // off < 0
      try {
        w.write(buf, -1, 2);
        w.flush();
        System.out.println("write_char_off_negative_no_ex");
      } catch (Exception e) {
        System.out.println("write_char_off_negative_ex=" + e.getClass().getName());
      }

      // len < 0
      try {
        w.write(buf, 0, -1);
        w.flush();
        System.out.println("write_char_len_negative_no_ex");
      } catch (Exception e) {
        System.out.println("write_char_len_negative_ex=" + e.getClass().getName());
      }

      // off + len > buf.length
      try {
        w.write(buf, 3, 5);
        w.flush();
        System.out.println("write_char_off_plus_len_too_big_no_ex");
      } catch (Exception e) {
        System.out.println("write_char_off_plus_len_too_big_ex=" + e.getClass().getName());
      }

      byte[] bytesAfterErrors = baos.toByteArray();
      System.out.println("write_char_after_errors_len=" + bytesAfterErrors.length);
      System.out.println("write_char_size_before_errors=" + sizeBeforeErrors);

      // cbuf 为 null
      char[] nullBuf = null;
      try {
        w.write(nullBuf, 0, 1);
        w.flush();
        System.out.println("write_char_null_buf_no_ex");
      } catch (Exception e) {
        System.out.println("write_char_null_buf_ex=" + e.getClass().getName());
      }
      byte[] bytesAfterNull = baos.toByteArray();
      System.out.println("write_char_after_null_len=" + bytesAfterNull.length);
    } catch (Exception e) {
      System.out.println("write_char_offsets_ex=" + e.getClass().getName());
    }
  }

  /**
   * 覆盖 write(String,off,len)：
   * - 正常范围写入
   * - off/len 越界时静默返回
   * - str 为 null 时静默返回。
   */
  private static void testWriteStringOffsets() {
    String s = "Hello"; // 长度 5

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      OutputStreamWriter w = new OutputStreamWriter(baos);

      // 正常写入 "ell"
      w.write(s, 1, 3);
      w.flush();
      byte[] bytes1 = baos.toByteArray();
      System.out.println("write_str_valid_len=" + bytes1.length);
      System.out.print("write_str_valid_bytes=");
      for (int i = 0; i < bytes1.length; i++) {
        if (i > 0) System.out.print(",");
        System.out.print(bytes1[i] & 0xFF);
      }
      System.out.println();

      int sizeBeforeErrors = bytes1.length;

      // off < 0
      try {
        w.write(s, -1, 2);
        w.flush();
        System.out.println("write_str_off_negative_no_ex");
      } catch (Exception e) {
        System.out.println("write_str_off_negative_ex=" + e.getClass().getName());
      }

      // len < 0
      try {
        w.write(s, 0, -1);
        w.flush();
        System.out.println("write_str_len_negative_no_ex");
      } catch (Exception e) {
        System.out.println("write_str_len_negative_ex=" + e.getClass().getName());
      }

      // off + len > s.length()
      try {
        w.write(s, 3, 5);
        w.flush();
        System.out.println("write_str_off_plus_len_too_big_no_ex");
      } catch (Exception e) {
        System.out.println("write_str_off_plus_len_too_big_ex=" + e.getClass().getName());
      }

      byte[] bytesAfterErrors = baos.toByteArray();
      System.out.println("write_str_after_errors_len=" + bytesAfterErrors.length);
      System.out.println("write_str_size_before_errors=" + sizeBeforeErrors);

      // str 为 null
      String nullStr = null;
      try {
        w.write(nullStr, 0, 1);
        w.flush();
        System.out.println("write_str_null_no_ex");
      } catch (Exception e) {
        System.out.println("write_str_null_ex=" + e.getClass().getName());
      }
      byte[] bytesAfterNull = baos.toByteArray();
      System.out.println("write_str_after_null_len=" + bytesAfterNull.length);
    } catch (Exception e) {
      System.out.println("write_str_offsets_ex=" + e.getClass().getName());
    }
  }

  /**
   * 覆盖 flush()/close()：
   * - 有底层流时 flush/close 不改变已写入的内容
   * - 调用 close() 后再次 write/flush 的行为
   * - 底层流为 null 时 flush/close 应静默返回。
   */
  private static void testFlushAndCloseBehaviors() {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      OutputStreamWriter w = new OutputStreamWriter(baos);

      w.write('X');
      w.flush();
      System.out.println("flush_close_initial_len=" + baos.toByteArray().length);

      w.close();
      System.out.println("flush_close_after_close_len=" + baos.toByteArray().length);

      try {
        w.write('Y');
        w.flush();
        System.out.println("flush_close_write_after_close_no_ex_len=" + baos.toByteArray().length);
      } catch (Exception e) {
        System.out.println("flush_close_write_after_close_ex=" + e.getClass().getName());
      }
    } catch (Exception e) {
      System.out.println("flush_close_normal_ex=" + e.getClass().getName());
    }

    // 底层流为 null 时 flush/close
    try {
      OutputStream nullOut = null;
      OutputStreamWriter wNull = new OutputStreamWriter(nullOut);
      wNull.flush();
      wNull.close();
      System.out.println("flush_close_null_out_completed_without_ex");
    } catch (Exception e) {
      System.out.println("flush_close_null_out_ex=" + e.getClass().getName());
    }
  }
}

