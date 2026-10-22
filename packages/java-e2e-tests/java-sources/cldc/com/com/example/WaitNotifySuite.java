package com.com.example;

public class WaitNotifySuite {
  static final Object lock = new Object();

  static class T1 extends Thread {
    public void run() {
      synchronized (lock) {
        System.out.println("T1-start");
        try {
          lock.wait(5);
        } catch (InterruptedException e) {
          System.out.println("T1-interrupted");
        }
        System.out.println("T1-after-wait");
      }
    }
  }

  static class T2 extends Thread {
    public void run() {
      synchronized (lock) {
        System.out.println("T2-start");
        lock.notifyAll();
        System.out.println("T2-end");
      }
    }
  }

  public static void main(String[] args) {
    T1 t1 = new T1();
    T2 t2 = new T2();
    t1.start();
    t2.start();
    try {
      t1.join();
      t2.join();
    } catch (InterruptedException e) {
      System.out.println("join-interrupted");
    }
  }
}

