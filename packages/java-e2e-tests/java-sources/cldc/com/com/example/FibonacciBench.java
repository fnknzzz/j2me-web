package com.com.example;

public class FibonacciBench {

  public static void main(String[] args) throws Exception {
    int n = 15;
    int result = fibonacci(n);
    System.out.println("fibonacci(" + n + ") = " + result);
  }

  private static int fibonacci(int n) {
    if (n <= 1) {
      return n;
    }
    return fibonacci(n - 1) + fibonacci(n - 2);
  }
}
