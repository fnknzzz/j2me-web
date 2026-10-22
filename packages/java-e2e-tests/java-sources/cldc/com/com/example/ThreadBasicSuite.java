package com.com.example;

public class ThreadBasicSuite {
    public static void main(String[] args) {
        Thread main = Thread.currentThread();
        System.out.println("current_thread_ok=" + (main != null));
        System.out.println("active_count=" + Thread.activeCount());
        
        Thread t = new Thread("TestThread");
        System.out.println("name=" + t.getName());
        System.out.println("priority=" + t.getPriority());
        System.out.println("is_alive=" + t.isAlive());
        
        t.setPriority(Thread.MAX_PRIORITY);
        System.out.println("priority_after_set=" + t.getPriority());
    }
}
