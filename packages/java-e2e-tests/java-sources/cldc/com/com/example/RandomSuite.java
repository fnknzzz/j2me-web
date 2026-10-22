package com.com.example;

public class RandomSuite {
  public static void main(String[] args) {
    java.util.Random r = new java.util.Random(12345L);
    System.out.println("i-" + r.nextInt());
    System.out.println("ib-" + r.nextInt(10));
    System.out.println("l-" + r.nextLong());
    System.out.println("f-" + r.nextFloat());
    System.out.println("d-" + r.nextDouble());
  }
}
