package com.com.example;

public class ArrayBench {

  public static void main(String[] args) throws Exception {
    int size = 10000;
    int[] arr = new int[size];
    
    for (int i = 0; i < size; i++) {
      arr[i] = i;
    }
    
    long sum = 0;
    for (int i = 0; i < size; i++) {
      sum += arr[i];
    }
    
    System.out.println("array sum = " + sum);
  }
}
