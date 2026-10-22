package com.com.example;

public class PrintStreamSuite {
  public static void main(String[] args) {
    System.out.write(65);
    System.out.write(new byte[]{66,67,68}, 1, 2);
    System.out.println();
    System.out.println("ok");
    System.out.flush();
    System.out.close();
    System.out.println(System.out.checkError());
  }
}
