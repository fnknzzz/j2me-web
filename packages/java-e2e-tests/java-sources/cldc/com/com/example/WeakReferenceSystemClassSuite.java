package com.com.example;

import java.lang.ref.WeakReference;

public class WeakReferenceSystemClassSuite {

  public static void main(String[] args) {
    testNonNullReferent();
    testNullReferent();
  }

  private static void print(String label, boolean value) {
    System.out.println(label + " = " + value);
  }

  private static void testNonNullReferent() {
    System.out.println("=== nonNullReferent ===");

    Object target = new Object();
    WeakReference ref = new WeakReference(target);

    Object first = ref.get();
    Object second = ref.get();

    print("nonNull/firstNotNull", first != null);
    print("nonNull/secondEqualsFirst", first == second);
    print("nonNull/equalsTarget", first == target);
  }

  private static void testNullReferent() {
    System.out.println("=== nullReferent ===");

    WeakReference ref = new WeakReference(null);
    Object got = ref.get();
    print("null/gotNull", got == null);
  }
}
