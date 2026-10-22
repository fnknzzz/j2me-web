package com.com.example;

/**
 * 针对 java.io.ByteArrayOutputStream 系统类的边界测试。
 *
 * 目标：
 * - 覆盖所有声明的方法：构造函数、size、write(int)、write(byte[], int, int)、toByteArray、reset、close、toString。
 * - 覆盖异常路径：构造函数负容量、write(byte[], int, int) 的越界与 null 处理等。
 */
public class ByteArrayOutputStreamSystemSuite {
  public static void main(String[] args) throws Exception {
    testConstructorsAndSize();
    System.out.println();

    testWriteIntAndCapacityGrowth();
    System.out.println();

    testWriteByteArrayAndBounds();
    System.out.println();

    testToByteArrayCopySemantics();
    System.out.println();

    testResetAndCloseNoop();
    System.out.println();

    testToStringEncoding();
  }

  /**
   * 覆盖：无参构造、有参构造（含负数）、size()。
   */
  private static void testConstructorsAndSize() {
    System.out.println("Test 1: constructors and size");

    // 无参构造：默认大小应为 0
    java.io.ByteArrayOutputStream bos1 = new java.io.ByteArrayOutputStream();
    System.out.println("bos1-size-initial=" + bos1.size());

    // 正容量构造：size 仍为 0
    java.io.ByteArrayOutputStream bos2 = new java.io.ByteArrayOutputStream(4);
    System.out.println("bos2-size-initial=" + bos2.size());

    // 负容量构造：JDK 规范应抛 IllegalArgumentException
    try {
      java.io.ByteArrayOutputStream bosNeg = new java.io.ByteArrayOutputStream(-1);
      System.out.println("bosNeg-created=" + bosNeg);
      System.out.println("ERROR: expected IllegalArgumentException for negative size");
    } catch (IllegalArgumentException e) {
      System.out.println("IllegalArgumentException as expected for negative initial size");
    }
  }

  /**
   * 覆盖：write(int)、内部缓冲区扩容逻辑、write(int) 的 8bit mask 行为。
   */
  private static void testWriteIntAndCapacityGrowth() throws java.io.IOException {
    System.out.println("Test 2: write(int) and capacity growth");

    // 从极小容量开始反复写入，触发扩容
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(1);
    for (int i = 0; i < 10; i++) {
      bos.write(i + 1); // 1..10
    }
    byte[] out = bos.toByteArray();
    System.out.println("bos-size-after-write-int=" + bos.size());
    System.out.print("bos-bytes-after-write-int=");
    for (int i = 0; i < out.length; i++) {
      if (i > 0) System.out.print(",");
      System.out.print(out[i]);
    }
    System.out.println();

    // 验证 write(int) 只保留低 8bit
    java.io.ByteArrayOutputStream bosMask = new java.io.ByteArrayOutputStream();
    bosMask.write(0x01FF); // 期望存储为 0xFF -> Java byte -1
    bosMask.write(0x0123); // 期望存储为 0x23 -> Java byte 35
    byte[] maskOut = bosMask.toByteArray();
    System.out.println(
      "write-int-mask-values=" + maskOut[0] + "," + maskOut[1]
    );
    System.out.println(
      "write-int-mask-unsigned=" + (maskOut[0] & 0xFF) + "," + (maskOut[1] & 0xFF)
    );
  }

  /**
   * 覆盖：write(byte[], int, int) 的正常路径与越界、null 处理。
   */
  private static void testWriteByteArrayAndBounds() throws java.io.IOException {
    System.out.println("Test 3: write(byte[], off, len) and bounds");

    byte[] src = new byte[] { 10, 20, 30, 40, 50 };
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(1);

    // 正常写入中间一段
    bos.write(src, 1, 3); // 20,30,40
    byte[] out1 = bos.toByteArray();
    System.out.println("bos-size-after-partial-write=" + bos.size());
    System.out.print("bos-bytes-after-partial-write=");
    for (int i = 0; i < out1.length; i++) {
      if (i > 0) System.out.print(",");
      System.out.print(out1[i]);
    }
    System.out.println();

    // 继续写入整段，触发扩容与尾部追加
    bos.write(src, 0, src.length);
    byte[] out2 = bos.toByteArray();
    System.out.println("bos-size-after-append=" + bos.size());
    System.out.print("bos-bytes-after-append=");
    for (int i = 0; i < out2.length; i++) {
      if (i > 0) System.out.print(",");
      System.out.print(out2[i]);
    }
    System.out.println();

    // 异常路径：off < 0
    try {
      int before = bos.size();
      bos.write(src, -1, 1);
      System.out.println("ERROR: expected IndexOutOfBoundsException for off<0");
      System.out.println("size-after-off-negative=" + bos.size());
    } catch (IndexOutOfBoundsException e) {
      System.out.println("IndexOutOfBoundsException as expected for off<0");
      System.out.println("size-unchanged-after-off-negative=" + bos.size());
    }

    // 异常路径：len < 0
    try {
      bos.write(src, 0, -1);
      System.out.println("ERROR: expected IndexOutOfBoundsException for len<0");
      System.out.println("size-after-len-negative=" + bos.size());
    } catch (IndexOutOfBoundsException e) {
      System.out.println("IndexOutOfBoundsException as expected for len<0");
      System.out.println("size-unchanged-after-len-negative=" + bos.size());
    }

    // 异常路径：off + len > src.length
    try {
      bos.write(src, 3, 5);
      System.out.println("ERROR: expected IndexOutOfBoundsException for off+len>length");
      System.out.println("size-after-off-plus-len-too-big=" + bos.size());
    } catch (IndexOutOfBoundsException e) {
      System.out.println("IndexOutOfBoundsException as expected for off+len>length");
      System.out.println("size-unchanged-after-off-plus-len-too-big=" + bos.size());
    }

    // 异常路径：src 为 null，应抛出 NullPointerException
    try {
      bos.write((byte[]) null, 0, 1);
      System.out.println("ERROR: expected NullPointerException for null src");
    } catch (NullPointerException e) {
      System.out.println("NullPointerException as expected for null src");
    }
  }

  /**
   * 覆盖：toByteArray() 返回的是拷贝而不是内部缓冲区直接暴露。
   */
  private static void testToByteArrayCopySemantics() throws java.io.IOException {
    System.out.println("Test 4: toByteArray() copy semantics");

    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    bos.write('A');
    bos.write('B');

    byte[] out1 = bos.toByteArray();
    System.out.println("first-toByteArray=" + (char) out1[0] + (char) out1[1]);

    // 修改返回数组，不应影响后续的 toByteArray 结果
    out1[0] = 'Z';

    byte[] out2 = bos.toByteArray();
    System.out.println("second-toByteArray=" + (char) out2[0] + (char) out2[1]);
  }

  /**
   * 覆盖：reset() 将计数清零；close() 为 no-op，之后仍可写入。
   */
  private static void testResetAndCloseNoop() throws java.io.IOException {
    System.out.println("Test 5: reset() and close() behavior");

    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    bos.write(new byte[] { 1, 2, 3 });
    System.out.println("size-before-reset=" + bos.size());

    bos.reset();
    System.out.println("size-after-reset=" + bos.size());
    System.out.println("toByteArray-after-reset-length=" + bos.toByteArray().length);

    bos.write(10);
    bos.close(); // ByteArrayOutputStream.close() 按规范是 no-op
    bos.write(20);

    byte[] out = bos.toByteArray();
    System.out.println("size-after-close-and-write=" + bos.size());
    System.out.println("bytes-after-close-and-write=" + out[0] + "," + out[1]);
  }

  /**
   * 覆盖：toString() 使用 ISO-8859-1 风格按字节值构造字符串；空缓冲区 toString() 应返回空串。
   */
  private static void testToStringEncoding() throws java.io.IOException {
    System.out.println("Test 6: toString() encoding behavior");

    // 空缓冲区 toString()
    java.io.ByteArrayOutputStream empty = new java.io.ByteArrayOutputStream();
    String emptyStr = empty.toString();
    System.out.println("empty-toString-length=" + emptyStr.length());

    // 包含负字节值的缓冲区，验证 & 0xFF 的行为
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    byte[] bytes = new byte[] { (byte) 0xFF, (byte) 0x80, (byte) 'A' };
    bos.write(bytes, 0, bytes.length);
    String s = bos.toString();
    System.out.println("toString-length=" + s.length());
    System.out.println(
      "toString-char-codes="
        + (int) s.charAt(0)
        + ","
        + (int) s.charAt(1)
        + ","
        + (int) s.charAt(2)
    );
  }
}

