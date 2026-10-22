package com.com.example;

public class LoopBench {

  public static void main(String[] args) throws Exception {
    int iterations = 100000;
    long sum = 0;
    for (int i = 0; i < iterations; i++) {
      sum += i;
    }
    System.out.println("loop sum = " + sum);
  }
}
