package com.com.example;

public class ExceptionHandlerCleanupSuite {
    public static void main(String[] args) {
        System.out.println("=== Exception Handler Cleanup Test ===");
        
        testConstructorException();
        testMethodException();
        testNestedTryCatch();
    }
    
    private static void testConstructorException() {
        System.out.println("Test 1: Constructor with try-catch");
        try {
            new TestClass();
            System.out.println("Test 1: PASS");
        } catch (Exception e) {
            System.out.println("Test 1: FAIL - Unexpected exception in outer catch");
        }
    }
    
    private static void testMethodException() {
        System.out.println("Test 2: Method with try-catch");
        try {
            methodWithTryCatch();
            System.out.println("Test 2: PASS");
        } catch (Exception e) {
            System.out.println("Test 2: FAIL - Unexpected exception in outer catch");
        }
    }
    
    private static void testNestedTryCatch() {
        System.out.println("Test 3: Nested try-catch");
        try {
            try {
                throwException();
            } catch (RuntimeException e) {
                System.out.println("Test 3: Inner catch executed");
            }
            System.out.println("Test 3: PASS");
        } catch (Exception e) {
            System.out.println("Test 3: FAIL - Unexpected exception in outer catch");
        }
    }
    
    private static void methodWithTryCatch() {
        try {
            throwException();
        } catch (RuntimeException e) {
            System.out.println("Method catch executed");
        }
    }
    
    private static void throwException() {
        throw new RuntimeException("Test exception");
    }
    
    static class TestClass {
        public TestClass() {
            try {
                throw new RuntimeException("Constructor exception");
            } catch (RuntimeException e) {
                System.out.println("Constructor catch executed");
            }
        }
    }
}
