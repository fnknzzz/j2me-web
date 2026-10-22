package com.com.example;

public class RuntimeApiSuite {
  public static void main(String[] args) {
    java.lang.Runtime rt = java.lang.Runtime.getRuntime();
    
    // 测试 freeMemory 返回值合理性
    long free = rt.freeMemory();
    if (free > 0 && free < Long.MAX_VALUE) {
      System.out.println("free-reasonable-yes");
    } else {
      System.out.println("free-reasonable-no");
    }
    
    // 测试 totalMemory 返回值合理性
    long total = rt.totalMemory();
    if (total > 0 && total < Long.MAX_VALUE) {
      System.out.println("total-reasonable-yes");
    } else {
      System.out.println("total-reasonable-no");
    }
    
    // 测试 freeMemory <= totalMemory
    if (free <= total) {
      System.out.println("free-le-total-yes");
    } else {
      System.out.println("free-le-total-no");
    }
    
    // 测试 gc() 不抛异常
    rt.gc();
    System.out.println("gc-ok");
  }
}
