package com.com.example;

public class ThrowableSuite {
  public static void main(String[] args) {
    Throwable t0 = new Throwable();
    System.out.println("t0-msg-" + String.valueOf(t0.getMessage()));
    System.out.println("t0-str-" + t0.toString());

    Throwable t1 = new Throwable("hello");
    System.out.println("t1-msg-" + t1.getMessage());
    System.out.println("t1-str-" + t1.toString());
    t1.printStackTrace(System.out);

    Exception ex = new Exception("emsg");
    System.out.println("ex-str-" + ex.toString());

    java.lang.ClassNotFoundException cnf = new java.lang.ClassNotFoundException("x");
    System.out.println("cnf-str-" + cnf.toString());

    java.lang.ArithmeticException ae = new java.lang.ArithmeticException("y");
    System.out.println("ae-str-" + ae.toString());

    java.lang.StringIndexOutOfBoundsException sioob = new java.lang.StringIndexOutOfBoundsException(5);
    System.out.println("sioob-str-" + sioob.toString());
  }
}
