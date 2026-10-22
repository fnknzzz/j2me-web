package com.com.example;

public class ByteArrayOutputStreamSuite {
  public static void main(String[] args) throws java.io.IOException {
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    bos.write(1);
    bos.write(2);
    bos.write(3);
    byte[] out = bos.toByteArray();
    System.out.println("bos-size-" + bos.size());
    System.out.println("bos-bytes-" + out[0] + "," + out[1] + "," + out[2]);
    bos.close();
    System.out.println("bos-close-ok");

    java.io.ByteArrayOutputStream bos2 = new java.io.ByteArrayOutputStream(1);
    byte[] src = new byte[]{65,66,67,68};
    bos2.write(src, 1, 2);
    System.out.println("bos2-size-" + bos2.size());
    System.out.println("bos2-str-" + bos2.toString());
    bos2.reset();
    System.out.println("bos2-size-after-reset-" + bos2.size());
    bos2.write(src, 0, src.length);
    System.out.println("bos2-str-all-" + bos2.toString());
  }
}
