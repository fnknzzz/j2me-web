package com.com.example;

public class FieldDefaultValueSuite {
    int intField;
    long longField;
    float floatField;
    double doubleField;
    boolean booleanField;
    byte byteField;
    short shortField;
    char charField;
    String stringField;
    Object objectField;

    public static void main(String[] args) {
        FieldDefaultValueSuite obj = new FieldDefaultValueSuite();

        System.out.println("=== int default ===");
        System.out.println(obj.intField);

        System.out.println("=== long default ===");
        System.out.println(obj.longField);

        System.out.println("=== float default ===");
        System.out.println(obj.floatField);

        System.out.println("=== double default ===");
        System.out.println(obj.doubleField);

        System.out.println("=== boolean default ===");
        System.out.println(obj.booleanField);

        System.out.println("=== byte default ===");
        System.out.println(obj.byteField);

        System.out.println("=== short default ===");
        System.out.println(obj.shortField);

        System.out.println("=== char default ===");
        System.out.println((int) obj.charField);

        System.out.println("=== String default ===");
        System.out.println(obj.stringField);

        System.out.println("=== Object default ===");
        System.out.println(obj.objectField);
    }
}
