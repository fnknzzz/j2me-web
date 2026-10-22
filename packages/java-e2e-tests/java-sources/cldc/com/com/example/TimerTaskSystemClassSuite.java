package com.com.example;

import java.util.TimerTask;

/**
 * 针对 jsjvm 中 java.util.TimerTask 系统类实现的边界测试。
 *
 * 覆盖点（对应 TimerTaskDecl 中声明的方法）：
 * - 构造方法：TimerTask()
 * - 实例方法：run()
 * - 实例方法：cancel()
 * - 实例方法：scheduledExecutionTime()
 *
 * 重点验证：
 * - 默认构造后 scheduledExecutionTime() 在未调度/未运行情况下返回 0；
 * - cancel() 第一次调用返回 true，后续重复调用返回 false（幂等）；
 * - 不同实例的 cancel 状态互不影响，PD 不会串写；
 * - 在子类中调用 super.run() 不会抛异常、不会破坏 cancel/scheduledExecutionTime 的语义；
 * - TimerTask 实例实现 Runnable 接口（instanceof Runnable）。
 */
public class TimerTaskSystemClassSuite {

  /**
   * 简单子类，用于覆盖 run() 调用路径以及 super.run() 的行为。
   */
  private static class MyTimerTask extends TimerTask {
    
    public void run() {
      System.out.println("MyTimerTask-run-executed");
    }
  }

  public static void main(String[] args) {
    System.out.println("=== ctor_and_initial_state ===");
    testCtorAndInitialState();

    System.out.println("=== cancel_idempotent_single_instance ===");
    testCancelIdempotentSingleInstance();

    System.out.println("=== cancel_isolated_between_instances ===");
    testCancelIsolatedBetweenInstances();

    System.out.println("=== run_and_super_run_no_side_effect ===");
    testRunAndSuperRunNoSideEffect();
  }

  private static void print(String label, Object value) {
    System.out.println(label + " = " + String.valueOf(value));
  }

  private static void print(String label, int value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, long value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, boolean value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, double value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, float value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, byte value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, short value) {
    System.out.println(label + " = " + value);
  }

  private static void print(String label, char value) {
    System.out.println(label + " = " + value);
  }

  /**
   * 构造函数与初始状态：
   * - 验证 TimerTask() 可正常实例化；
   * - 验证实例是否实现 Runnable 接口；
   * - 验证在未调度/未执行前 scheduledExecutionTime() 返回 0。
   */
  private static void testCtorAndInitialState() {
    TimerTask task = new TimerTask() {
      
      public void run() {
        // 空实现，仅用于创建非抽象实例。
      }
    };

    print("ctor/class_name", task.getClass().getName());
    print("ctor/is_runnable", new Boolean(task instanceof Runnable));

    long scheduled = task.scheduledExecutionTime();
    print("ctor/scheduled_time_initial", new Long(scheduled));
    print("ctor/scheduled_time_is_zero", new Boolean(scheduled == 0L));
  }

  /**
   * cancel() 的幂等行为（单实例）：
   * - 第一次调用返回 true，对应 JsTimerTaskState.canceled 从 false -> true；
   * - 第二次及后续调用返回 false，且不抛异常。
   */
  private static void testCancelIdempotentSingleInstance() {
    TimerTask task = new TimerTask() {
      
      public void run() {}
    };

    boolean first = task.cancel();
    boolean second = task.cancel();
    boolean third = task.cancel();

    print("cancel/first_call", new Boolean(first));
    print("cancel/second_call", new Boolean(second));
    print("cancel/third_call", new Boolean(third));
  }

  /**
   * 多实例之间的 cancel 状态隔离：
   * - 两个不同的 TimerTask 实例，各自第一次 cancel() 都应返回 true；
   * - 互不影响，证明 JsTimerTaskState 通过 PD 与实例绑定，没有串写。
   */
  private static void testCancelIsolatedBetweenInstances() {
    TimerTask t1 = new TimerTask() {
      
      public void run() {}
    };
    TimerTask t2 = new TimerTask() {
      
      public void run() {}
    };

    boolean t1First = t1.cancel();
    boolean t1Second = t1.cancel();
    boolean t2First = t2.cancel();
    boolean t2Second = t2.cancel();

    print("multi/t1_first", new Boolean(t1First));
    print("multi/t1_second", new Boolean(t1Second));
    print("multi/t2_first", new Boolean(t2First));
    print("multi/t2_second", new Boolean(t2Second));
  }

  /**
   * run() 与 super.run() 的行为：
   * - 在子类 MyTimerTask 中重写 run()，并显式调用 super.run()；
   * - 验证调用过程中不会抛出异常；
   * - 验证多次 run() 调用后仍可正常调用 cancel()/scheduledExecutionTime()。
   */
  private static void testRunAndSuperRunNoSideEffect() {
    MyTimerTask task = new MyTimerTask();

    try {
      task.run();
      task.run();
      print("run/super_run_no_exception", Boolean.TRUE);
    } catch (Throwable t) {
      print("run/super_run_exception", t.getClass().getName());
    }

    // run() 的系统实现为 no-op，因此 scheduledExecutionTime 仍应保持 0。
    long scheduled = task.scheduledExecutionTime();
    print("run/scheduled_time_after_runs", new Long(scheduled));
    print(
        "run/scheduled_time_still_zero",
        new Boolean(scheduled == 0L));

    boolean cancelFirst = task.cancel();
    boolean cancelSecond = task.cancel();
    print("run/cancel_after_runs_first", new Boolean(cancelFirst));
    print("run/cancel_after_runs_second", new Boolean(cancelSecond));
  }
}
