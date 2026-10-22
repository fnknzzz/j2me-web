package com.com.example;

public class TimeSliceFairnessSuite {
    static int counter1 = 0;
    static int counter2 = 0;
    static int counter3 = 0;
    
    static class Worker extends Thread {
        int id;
        Worker(int id) { 
            this.id = id; 
        }
        
        public void run() {
            for (int i = 0; i < 100; i++) {
                if (id == 1) counter1++;
                else if (id == 2) counter2++;
                else counter3++;
            }
            System.out.println("Worker " + id + " done");
        }
    }
    
    public static void main(String[] args) throws Exception {
        Worker w1 = new Worker(1);
        Worker w2 = new Worker(2);
        Worker w3 = new Worker(3);
        
        w1.start();
        w2.start();
        w3.start();
        
        w1.join();
        w2.join();
        w3.join();
        
        System.out.println("Counter1: " + counter1);
        System.out.println("Counter2: " + counter2);
        System.out.println("Counter3: " + counter3);
    }
}
