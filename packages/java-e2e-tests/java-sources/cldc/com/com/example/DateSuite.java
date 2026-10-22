package com.com.example;

public class DateSuite {
  public static void main(String[] args) throws Exception {
    java.util.Date d1 = new java.util.Date(1234567890000L);
    System.out.println("t1=" + d1.getTime());
    System.out.println("s1=" + d1.toString());
    java.util.Date d2 = new java.util.Date(1234567890000L);
    System.out.println("eq=" + String.valueOf(d1.equals(d2)));
    System.out.println("hc=" + d1.hashCode());
    d2.setTime(1234567890001L);
    System.out.println("eq2=" + String.valueOf(d1.equals(d2)));
    System.out.println("t2=" + d2.getTime());
  }
}

