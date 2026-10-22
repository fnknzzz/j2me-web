package com.com.example;

public class StackSuite {
  public static void main(String[] args) {
    java.util.Stack s = new java.util.Stack();
    System.out.println("empty-" + s.empty());
    s.push("a");
    s.push("b");
    s.push("c");
    System.out.println("peek-" + s.peek());
    System.out.println("search-b-" + s.search("b"));
    System.out.println("search-x-" + s.search("x"));
    System.out.println("pop-" + s.pop());
    System.out.println("pop-" + s.pop());
    System.out.println("pop-" + s.pop());
    System.out.println("empty2-" + s.empty());
  }
}
