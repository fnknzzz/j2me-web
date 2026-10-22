package com.com.example;

public class BooleanSuite {
  public static void main(String[] args) {
    Boolean trueVal = new Boolean(true);
    Boolean falseVal = new Boolean(false);

    System.out.println(trueVal.booleanValue());
    System.out.println(falseVal.booleanValue());

    System.out.println(trueVal.toString());
    System.out.println(falseVal.toString());

    System.out.println(trueVal.hashCode());
    System.out.println(falseVal.hashCode());

    System.out.println(trueVal.equals(new Boolean(true)));
    System.out.println(trueVal.equals(new Boolean(false)));
    System.out.println(trueVal.equals(falseVal));

    System.out.println(Boolean.TRUE.booleanValue());
    System.out.println(Boolean.FALSE.booleanValue());

    System.out.println(Boolean.TRUE.toString());
    System.out.println(Boolean.FALSE.toString());
  }
}
