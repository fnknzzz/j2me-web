package com.com.example;

public class WaitNotifyOneSuite {
  static final Object lock = new Object();

  static class W extends Thread {
    private final String name;
    W(String n) { this.name = n; }
    public void run() {
      synchronized (lock) {
        System.out.println(name + "-start");
        try {
          lock.wait(5);
        } catch (InterruptedException e) {
          System.out.println(name + "-interrupted");
        }
        System.out.println(name + "-after-wait");
      }
    }
  }

  static class N extends Thread {
    public void run() {
      synchronized (lock) {
        System.out.println("N-start");
        lock.notify();
        System.out.println("N-end");
      }
    }
  }

  public static void main(String[] args) {
    W t1 = new W("T1");
    W t2 = new W("T2");
    N n = new N();
    t1.start();
    t2.start();
    n.start();
    try {
      t1.join();
      t2.join();
      n.join();
    } catch (InterruptedException e) {
      System.out.println("join-interrupted");
    }
  }
}

