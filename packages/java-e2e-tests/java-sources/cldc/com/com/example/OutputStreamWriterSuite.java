package com.com.example;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;

public class OutputStreamWriterSuite {
    public static void main(String[] args) {
        testConstructorWithOutputStream();
        testConstructorWithEncoding();
        testWriteSingleChar();
        testWriteCharArray();
        testWriteString();
        testFlush();
        testClose();
        testMultipleWrites();
    }

    private static void testConstructorWithOutputStream() {
        System.out.println("=== testConstructorWithOutputStream ===");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos);
            System.out.println("OutputStreamWriter created successfully");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void testConstructorWithEncoding() {
        System.out.println("=== testConstructorWithEncoding ===");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos, "UTF-8");
            System.out.println("OutputStreamWriter with encoding created successfully");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void testWriteSingleChar() {
        System.out.println("=== testWriteSingleChar ===");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos);
            writer.write(65);
            writer.flush();
            byte[] bytes = baos.toByteArray();
            System.out.println("Wrote char, byte value: " + (int)bytes[0]);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void testWriteCharArray() {
        System.out.println("=== testWriteCharArray ===");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos);
            char[] buffer = {'H', 'e', 'l', 'l', 'o'};
            writer.write(buffer, 0, 5);
            writer.flush();
            byte[] bytes = baos.toByteArray();
            System.out.println("Wrote char array, length: " + bytes.length);
            System.out.println("First byte: " + (int)bytes[0]);
            System.out.println("Last byte: " + (int)bytes[4]);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void testWriteString() {
        System.out.println("=== testWriteString ===");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos);
            String text = "World";
            writer.write(text, 0, 5);
            writer.flush();
            byte[] bytes = baos.toByteArray();
            System.out.println("Wrote string, length: " + bytes.length);
            System.out.println("First byte: " + (int)bytes[0]);
            System.out.println("Last byte: " + (int)bytes[4]);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void testFlush() {
        System.out.println("=== testFlush ===");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos);
            writer.write(66);
            writer.flush();
            System.out.println("Flush completed");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void testClose() {
        System.out.println("=== testClose ===");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos);
            writer.write(67);
            writer.close();
            System.out.println("Close completed");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void testMultipleWrites() {
        System.out.println("=== testMultipleWrites ===");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos);
            writer.write(65);
            writer.write(66);
            writer.write(67);
            writer.flush();
            byte[] bytes = baos.toByteArray();
            System.out.println("Multiple writes, length: " + bytes.length);
            System.out.println("Byte 0: " + (int)bytes[0]);
            System.out.println("Byte 1: " + (int)bytes[1]);
            System.out.println("Byte 2: " + (int)bytes[2]);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
