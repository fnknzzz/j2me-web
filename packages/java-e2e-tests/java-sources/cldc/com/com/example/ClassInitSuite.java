package com.com.example;

class InitClass {
  static {
    System.out.println("CLINIT");
  }
  public static void foo() {
    System.out.println("FOO");
  }
}

public class ClassInitSuite {
  static class T1 extends Thread {
    public void run() {
      InitClass.foo();
    }
  }
  static class T2 extends Thread {
    public void run() {
      InitClass.foo();
    }
  }

  public static void main(String[] args) {
    T1 a = new T1();
    T2 b = new T2();
    a.start();
    b.start();
    try {
      a.join();
      b.join();
    } catch (InterruptedException e) {
      System.out.println("join-interrupted");
    }
  }
}

