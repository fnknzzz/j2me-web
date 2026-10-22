package com.com.example;

public class TimerYieldSuite {
  static class SleepTask extends java.util.TimerTask {
    public void run() {
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        System.out.println("interrupted");
      }
      System.out.println("tick-after-sleep");
    }
  }

  public static void main(String[] args) throws Exception {
    java.util.Timer t = new java.util.Timer();
    t.schedule(new SleepTask(), 10);
    Thread.sleep(80);
    t.cancel();
    System.out.println("done");
  }
}

