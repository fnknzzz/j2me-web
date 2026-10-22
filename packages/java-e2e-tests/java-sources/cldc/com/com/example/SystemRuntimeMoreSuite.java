package com.com.example;

public class SystemRuntimeMoreSuite {
  public static void main(String[] args) {
    System.out.println("before-exit");
    System.exit(0);
    System.out.println("exit-ok");
    java.lang.Runtime rt = java.lang.Runtime.getRuntime();
    rt.exit(0);
    System.out.println("rt-exit-ok");
  }
}
