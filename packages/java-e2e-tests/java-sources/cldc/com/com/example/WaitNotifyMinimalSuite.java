package com.com.example;

public class WaitNotifyMinimalSuite {
  static final Object lock = new Object();
  static boolean notified = false;

  static class WaiterThread extends Thread {
    public void run() {
      synchronized (lock) {
        System.out.println("Waiter: acquired lock");
        try {
          System.out.println("Waiter: calling wait");
          lock.wait();
          System.out.println("Waiter: woke up from wait");
        } catch (InterruptedException e) {
          System.out.println("Waiter: interrupted");
        }
        System.out.println("Waiter: done");
      }
    }
  }

  static class NotifierThread extends Thread {
    public void run() {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
      }
      synchronized (lock) {
        System.out.println("Notifier: acquired lock");
        System.out.println("Notifier: calling notify");
        lock.notify();
        System.out.println("Notifier: called notify");
      }
      System.out.println("Notifier: released lock");
    }
  }

  public static void main(String[] args) {
    System.out.println("Main: starting threads");
    WaiterThread waiter = new WaiterThread();
    NotifierThread notifier = new NotifierThread();
    
    waiter.start();
    notifier.start();
    
    try {
      waiter.join();
      notifier.join();
    } catch (InterruptedException e) {
      System.out.println("Main: interrupted");
    }
    
    System.out.println("Main: all threads finished");
  }
}
