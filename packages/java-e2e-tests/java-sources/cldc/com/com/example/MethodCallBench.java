package com.com.example;

public class MethodCallBench {

  public static void main(String[] args) throws Exception {
    int iterations = 100000;
    int result = 0;
    for (int i = 0; i < iterations; i++) {
      result = add(result, 1);
    }
    System.out.println("method call result = " + result);
  }

  private static int add(int a, int b) {
    return a + b;
  }
}
