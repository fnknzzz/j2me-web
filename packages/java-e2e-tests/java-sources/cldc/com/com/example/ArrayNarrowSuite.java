package com.com.example;

public class ArrayNarrowSuite {
  public static void main(String[] args) {
    byte[] ba = new byte[3];
    ba[0] = (byte) -3;
    ba[1] = (byte) 128;
    ba[2] = (byte) -128;
    System.out.println(ba[0] % 2);
    System.out.println(ba[1]);
    System.out.println(ba[2]);

    short[] sa = new short[3];
    sa[0] = (short) -3;
    sa[1] = (short) 32768;
    sa[2] = (short) -32768;
    System.out.println(sa[0] % 2);
    System.out.println(sa[1]);
    System.out.println(sa[2]);

    char[] ca = new char[3];
    ca[0] = (char) 3;
    ca[1] = (char) -1;
    ca[2] = (char) 65535;
    System.out.println(ca[0] % 2);
    System.out.println(ca[1] % 2);
    System.out.println(ca[2] % 2);
  }
}
