package com.com.example;

public class SynchronizedMethodMonitorSuite {
    private static int counter = 0;
    private static boolean threadAHoldsLock = false;
    private static boolean threadBShouldTry = false;

    public static void main(String[] args) {
        System.out.println("=== synchronized_method_monitor_contention ===");
        testSynchronizedMethodMonitorContention();
    }

    private static synchronized void syncMethod(String name) {
        System.out.println(name + "_entered");
        if (name.equals("A")) {
            threadAHoldsLock = true;
            while (!threadBShouldTry) {
                Thread.yield();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {}
        }
        counter++;
        System.out.println(name + "_exiting_counter=" + counter);
    }

    private static void testSynchronizedMethodMonitorContention() {
        Thread threadA = new Thread(new Runnable() {
            public void run() {
                syncMethod("A");
                System.out.println("A_done");
            }
        }, "Thread-A");

        Thread threadB = new Thread(new Runnable() {
            public void run() {
                while (!threadAHoldsLock) {
                    Thread.yield();
                }
                threadBShouldTry = true;
                syncMethod("B");
                System.out.println("B_done");
            }
        }, "Thread-B");

        threadA.start();
        threadB.start();

        try {
            threadA.join();
            threadB.join();
        } catch (InterruptedException e) {
            System.out.println("join_interrupted");
        }

        System.out.println("final_counter=" + counter);
        System.out.println("test_passed=" + (counter == 2));
    }
}
