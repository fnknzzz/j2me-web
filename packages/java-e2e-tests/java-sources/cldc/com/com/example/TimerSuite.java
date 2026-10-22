package com.com.example;

public class TimerSuite {
  static class MyTask extends java.util.TimerTask {
    int c = 0;
    public void run() { System.out.println("tick-" + (++c)); }
  }

  static class CancelTask extends java.util.TimerTask {
    public void run() { System.out.println("cancel-tick"); }
  }

  static class FixedRateTask extends java.util.TimerTask {
    public void run() { System.out.println("fixedrate-tick"); }
  }

  public static void main(String[] args) throws Exception {
    java.util.Timer t = new java.util.Timer();
    java.util.Timer t2 = new java.util.Timer(true);
    java.util.Timer t3 = new java.util.Timer("t3");
    java.util.Timer t4 = new java.util.Timer("t4", true);
    System.out.println("ctor-ok");

    CancelTask ct = new CancelTask();
    boolean canceled = ct.cancel();
    System.out.println("cancel-before-schedule-" + canceled);

    CancelTask ct2 = new CancelTask();
    t.schedule(ct2, 30);
    boolean canceled2 = ct2.cancel();
    System.out.println("cancel-after-schedule-" + canceled2);
    Thread.sleep(80);

    FixedRateTask fr = new FixedRateTask();
    t.scheduleAtFixedRate(fr, new java.util.Date(System.currentTimeMillis() + 1000), 50);
    boolean frCanceled = fr.cancel();
    System.out.println("fixedrate-cancel-" + frCanceled);
    Thread.sleep(120);

    MyTask task = new MyTask();
    t.schedule(task, 10); // 一次性任务
    Thread.sleep(50);
    t.cancel();
    t2.cancel();
    t3.cancel();
    t4.cancel();
    System.out.println("done");
  }
}
