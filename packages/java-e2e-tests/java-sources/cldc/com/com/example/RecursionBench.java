package com.com.example;

public class RecursionBench {

  public static void main(String[] args) throws Exception {
    int depth = 1000;
    int result = recursiveSum(depth);
    System.out.println("recursiveSum(" + depth + ") = " + result);
  }

  private static int recursiveSum(int n) {
    if (n <= 0) {
      return 0;
    }
    return n + recursiveSum(n - 1);
  }
}
