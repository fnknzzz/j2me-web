package com.com.example;

public class MultiDimensionalArraySuite {
  public static void main(String[] args) {
    // Test 2D char array (the bug case)
    char[][] arr2d = new char[2][3];
    arr2d[0][0] = 'A';
    arr2d[0][1] = 'B';
    arr2d[1][2] = 'C';
    System.out.println(arr2d[0][0]);
    System.out.println(arr2d[0][1]);
    System.out.println(arr2d[1][2]);

    // Test 3D int array
    int[][][] arr3d = new int[2][3][4];
    arr3d[0][1][2] = 123;
    arr3d[1][2][3] = 456;
    System.out.println(arr3d[0][1][2]);
    System.out.println(arr3d[1][2][3]);

    // Test 2D String array
    String[][] strArr = new String[2][2];
    strArr[0][0] = "Hello";
    strArr[1][1] = "World";
    System.out.println(strArr[0][0]);
    System.out.println(strArr[1][1]);

    // Test 4D byte array (extreme case)
    byte[][][][] arr4d = new byte[2][2][2][2];
    arr4d[0][0][0][0] = 1;
    arr4d[1][1][1][1] = 9;
    System.out.println(arr4d[0][0][0][0]);
    System.out.println(arr4d[1][1][1][1]);

    // Test assignment to field (the original bug scenario)
    TestClass tc = new TestClass();
    tc.testField();
  }
}

class TestClass {
  private char[][] field;

  public void testField() {
    // This should create a 2D array, not 4D
    this.field = new char[2][3];
    this.field[0][0] = 'X';
    this.field[1][2] = 'Y';
    System.out.println(this.field[0][0]);
    System.out.println(this.field[1][2]);
  }
}
