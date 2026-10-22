package com.com.example;

public class ArrayNullPointerExceptionSuite {

  public static void main(String[] args) {
    testIaloadNull();
    testLaloadNull();
    testFaloadNull();
    testDaloadNull();
    testAaloadNull();
    testBaloadNull();
    testCaloadNull();
    testSaloadNull();
    testIastoreNull();
    testLastoreNull();
    testFastoreNull();
    testDastoreNull();
    testAastoreNull();
    testBastoreNull();
    testCastoreNull();
    testSastoreNull();
    testArraylengthNull();
  }

  private static void print(String label, String value) {
    System.out.println(label + " = " + value);
  }

  private static void testIaloadNull() {
    System.out.println("=== iaload null ===");
    int[] arr = null;
    try {
      int val = arr[0];
      print("iaload/unexpected", String.valueOf(val));
    } catch (NullPointerException e) {
      print("iaload/caught", e.getClass().getName());
    }
  }

  private static void testLaloadNull() {
    System.out.println("=== laload null ===");
    long[] arr = null;
    try {
      long val = arr[0];
      print("laload/unexpected", String.valueOf(val));
    } catch (NullPointerException e) {
      print("laload/caught", e.getClass().getName());
    }
  }

  private static void testFaloadNull() {
    System.out.println("=== faload null ===");
    float[] arr = null;
    try {
      float val = arr[0];
      print("faload/unexpected", String.valueOf(val));
    } catch (NullPointerException e) {
      print("faload/caught", e.getClass().getName());
    }
  }

  private static void testDaloadNull() {
    System.out.println("=== daload null ===");
    double[] arr = null;
    try {
      double val = arr[0];
      print("daload/unexpected", String.valueOf(val));
    } catch (NullPointerException e) {
      print("daload/caught", e.getClass().getName());
    }
  }

  private static void testAaloadNull() {
    System.out.println("=== aaload null ===");
    Object[] arr = null;
    try {
      Object val = arr[0];
      print("aaload/unexpected", String.valueOf(val));
    } catch (NullPointerException e) {
      print("aaload/caught", e.getClass().getName());
    }
  }

  private static void testBaloadNull() {
    System.out.println("=== baload null ===");
    byte[] arr = null;
    try {
      byte val = arr[0];
      print("baload/unexpected", String.valueOf(val));
    } catch (NullPointerException e) {
      print("baload/caught", e.getClass().getName());
    }
  }

  private static void testCaloadNull() {
    System.out.println("=== caload null ===");
    char[] arr = null;
    try {
      char val = arr[0];
      print("caload/unexpected", String.valueOf(val));
    } catch (NullPointerException e) {
      print("caload/caught", e.getClass().getName());
    }
  }

  private static void testSaloadNull() {
    System.out.println("=== saload null ===");
    short[] arr = null;
    try {
      short val = arr[0];
      print("saload/unexpected", String.valueOf(val));
    } catch (NullPointerException e) {
      print("saload/caught", e.getClass().getName());
    }
  }

  private static void testIastoreNull() {
    System.out.println("=== iastore null ===");
    int[] arr = null;
    try {
      arr[0] = 42;
      print("iastore/unexpected", "stored");
    } catch (NullPointerException e) {
      print("iastore/caught", e.getClass().getName());
    }
  }

  private static void testLastoreNull() {
    System.out.println("=== lastore null ===");
    long[] arr = null;
    try {
      arr[0] = 42L;
      print("lastore/unexpected", "stored");
    } catch (NullPointerException e) {
      print("lastore/caught", e.getClass().getName());
    }
  }

  private static void testFastoreNull() {
    System.out.println("=== fastore null ===");
    float[] arr = null;
    try {
      arr[0] = 42.0f;
      print("fastore/unexpected", "stored");
    } catch (NullPointerException e) {
      print("fastore/caught", e.getClass().getName());
    }
  }

  private static void testDastoreNull() {
    System.out.println("=== dastore null ===");
    double[] arr = null;
    try {
      arr[0] = 42.0;
      print("dastore/unexpected", "stored");
    } catch (NullPointerException e) {
      print("dastore/caught", e.getClass().getName());
    }
  }

  private static void testAastoreNull() {
    System.out.println("=== aastore null ===");
    Object[] arr = null;
    try {
      arr[0] = new Object();
      print("aastore/unexpected", "stored");
    } catch (NullPointerException e) {
      print("aastore/caught", e.getClass().getName());
    }
  }

  private static void testBastoreNull() {
    System.out.println("=== bastore null ===");
    byte[] arr = null;
    try {
      arr[0] = (byte) 42;
      print("bastore/unexpected", "stored");
    } catch (NullPointerException e) {
      print("bastore/caught", e.getClass().getName());
    }
  }

  private static void testCastoreNull() {
    System.out.println("=== castore null ===");
    char[] arr = null;
    try {
      arr[0] = 'A';
      print("castore/unexpected", "stored");
    } catch (NullPointerException e) {
      print("castore/caught", e.getClass().getName());
    }
  }

  private static void testSastoreNull() {
    System.out.println("=== sastore null ===");
    short[] arr = null;
    try {
      arr[0] = (short) 42;
      print("sastore/unexpected", "stored");
    } catch (NullPointerException e) {
      print("sastore/caught", e.getClass().getName());
    }
  }

  private static void testArraylengthNull() {
    System.out.println("=== arraylength null ===");
    int[] arr = null;
    try {
      int len = arr.length;
      print("arraylength/unexpected", String.valueOf(len));
    } catch (NullPointerException e) {
      print("arraylength/caught", e.getClass().getName());
    }
  }
}
