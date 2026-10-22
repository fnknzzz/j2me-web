package com.com.example;

public class ByteArrayStoreMinimalSuite {
  public static void main(String[] args) {
    System.out.println("=== byte_array_store_minimal ===");
    System.out.println("before_store");
    byte[] buf = new byte[32];
    buf[0] = 65;
    System.out.println("after_store=" + buf[0]);
  }
}
