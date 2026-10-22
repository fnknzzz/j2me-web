package com.com.example;

public class ThreadMultiCreateSuite {
    public static void main(String[] args) {
        Thread t1 = new Thread();
        System.out.println("t1_name=" + t1.getName());
        
        Thread t2 = new Thread();
        System.out.println("t2_name=" + t2.getName());
        
        Thread t3 = new Thread("MyThread");
        System.out.println("t3_name=" + t3.getName());
    }
}
