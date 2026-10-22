package com.com.example;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 针对 jsjvm 中 java.io.DataOutput 接口系统类的行为测试。
 *
 * 由于系统类本身不在 TS 侧实现任何方法（仅用于声明接口类型），
 * 本套测试主要从以下角度验证：
 * - DataOutput 是否被识别为接口类型
 * - 通过 DataOutput 接口引用调用所有常见写方法时的行为
 * - writeUTF 等接口方法的边界与异常路径
 */
public class DataOutputSystemClassSuite {

  public static void main(String[] args) {
    try {
      System.out.println("=== interface_metadata ===");
      testInterfaceMetadata();

      System.out.println("=== interface_basic_writes ===");
      testBasicWritesViaInterface();

      System.out.println("=== interface_writeUTF_variants ===");
      testWriteUTFViaInterface();

      System.out.println("=== interface_cast_and_assign ===");
      testCastAndAssign();
    } catch (Exception e) {
      System.out.println("UNEXPECTED_EXCEPTION: " + e.getClass().getName());
      e.printStackTrace();
    }
  }

  // =============== 接口元数据：是否为接口、名称等 ===============

  private static void testInterfaceMetadata() {
    Class cls = java.io.DataOutput.class;
    System.out.println("name:" + cls.getName());
    System.out.println("isInterface:" + cls.isInterface());
    System.out.println(
      "isAssignableFrom_DataOutputStream:"
        + cls.isAssignableFrom(java.io.DataOutputStream.class)
    );

    // 接口无法直接 newInstance，应抛 InstantiationException / IllegalAccessException
    try {
      Object o = cls.newInstance();
      System.out.println("newInstance_result:" + o);
      System.out.println("ERROR: DataOutput should not be instantiable");
    } catch (InstantiationException e) {
      System.out.println("newInstance_InstantiationException");
    } catch (IllegalAccessException e) {
      System.out.println("newInstance_IllegalAccessException");
    }
  }

  // =============== 通过 DataOutput 接口进行基础写入 ===============

  private static void testBasicWritesViaInterface() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutput out = new DataOutputStream(baos);

    // 基础 write / writeByte / writeShort / writeChar / writeInt / writeLong 等
    out.writeBoolean(true);
    out.writeByte(0x7F);
    out.writeByte(-1);
    out.writeShort(0x1234);
    out.writeChar('A');
    out.writeInt(0x01020304);
    out.writeLong(0x0102030405060708L);
    out.writeFloat(1.5f);
    out.writeDouble(3.25d);

    byte[] buf = new byte[]{10, 20, 30};
    out.write(buf);
    out.write(buf, 1, 2);

    System.out.println("basic_writes_size:" + baos.toByteArray().length);
  }

  // =============== writeUTF 的正常与边界情况 ===============

  private static void testWriteUTFViaInterface() throws IOException {
    // 正常 ASCII
    ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
    DataOutput out1 = new DataOutputStream(baos1);
    out1.writeUTF("ABC");
    byte[] data1 = baos1.toByteArray();
    System.out.println("writeUTF_ascii_len:" + data1.length);

    // 包含 2 字节 UTF-8 字符
    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
    DataOutput out2 = new DataOutputStream(baos2);
    out2.writeUTF("©"); // U+00A9
    byte[] data2 = baos2.toByteArray();
    System.out.println("writeUTF_2bytes_len:" + data2.length);

    // 包含 3 字节 UTF-8 字符
    ByteArrayOutputStream baos3 = new ByteArrayOutputStream();
    DataOutput out3 = new DataOutputStream(baos3);
    out3.writeUTF("€"); // U+20AC
    byte[] data3 = baos3.toByteArray();
    System.out.println("writeUTF_3bytes_len:" + data3.length);

    // 边界：空字符串
    ByteArrayOutputStream baos4 = new ByteArrayOutputStream();
    DataOutput out4 = new DataOutputStream(baos4);
    out4.writeUTF("");
    byte[] data4 = baos4.toByteArray();
    System.out.println("writeUTF_empty_len:" + data4.length);

    // 边界：超长字符串（尽量小心长度，避免 OOM，这里只验证不会抛出编码异常）
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < 300; i++) {
      sb.append('x');
    }
    ByteArrayOutputStream baos5 = new ByteArrayOutputStream();
    DataOutput out5 = new DataOutputStream(baos5);
    out5.writeUTF(sb.toString());
    byte[] data5 = baos5.toByteArray();
    System.out.println("writeUTF_long_len:" + data5.length);
  }

  // =============== 接口赋值与强制类型转换 ===============

  private static void testCastAndAssign() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    DataOutput out = dos; // 向上转型到接口
    System.out.println("instanceof_DataOutput:" + (out instanceof DataOutput));
    System.out.println("instanceof_DataOutputStream:" + (out instanceof DataOutputStream));

    // 再次向下转型验证兼容性
    if (out instanceof DataOutputStream) {
      DataOutputStream casted = (DataOutputStream) out;
      casted.writeInt(123);
    }

    System.out.println("cast_and_assign_size:" + baos.toByteArray().length);
  }
}

