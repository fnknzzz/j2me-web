package com.com.example;

public class ThreadInterruptedSuite {
  public static void main(String[] args) {
    Thread.currentThread().interrupt();
    System.out.println(Thread.interrupted());
    System.out.println(Thread.interrupted());
  }
}

