package com.com.example;

public class ConversionPromotionSuite {
  public static void main(String[] args) {
    int i = -3;
    byte b = (byte) i;
    short s = (short) i;
    char c = (char) i;
    System.out.println(b % 2);
    System.out.println(s % 2);
    System.out.println(c % 2);

    int i2 = 128;
    byte b2 = (byte) i2;
    System.out.println(b2);
    int i3 = 32768;
    short s3 = (short) i3;
    System.out.println(s3);
    int i4 = -1;
    char c4 = (char) i4;
    System.out.println(c4 % 2);
  }
}
