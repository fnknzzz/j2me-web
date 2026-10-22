package com.com.example;

/**
 * 测试场景：Timer 默认创建非守护线程
 * 
 * 1. 创建 Timer（默认非守护线程）
 * 2. 调度一个 100ms 后执行的任务
 * 3. 主线程立即结束
 * 4. 验证 JVM 不会退出，Timer 任务会执行
 * 5. 任务执行后 cancel Timer，JVM 才退出
 */
public class TimerNonDaemonSuite {
    static java.util.Timer timer;

    static class MyTask extends java.util.TimerTask {
        public void run() {
            System.out.println("task-executed");
            timer.cancel();
        }
    }

    public static void main(String[] args) {
        timer = new java.util.Timer();
        timer.schedule(new MyTask(), 100);
        System.out.println("main-end");
    }
}
