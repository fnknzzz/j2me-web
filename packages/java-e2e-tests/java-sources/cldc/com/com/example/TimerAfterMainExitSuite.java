package com.com.example;

/**
 * 测试场景：主线程输出后，Timer 延迟输出
 * 
 * 1. 连续输出 3 行文本
 * 2. 通过 Timer 设置 200ms 后再输出
 * 3. 验证 JVM 能正确执行延迟的 Timer 任务
 */
public class TimerAfterMainExitSuite {
    static volatile boolean done = false;
    static java.util.Timer timer;

    static class DelayedTask extends java.util.TimerTask {
        public void run() {
            System.out.println("timer-delayed-output");
            done = true;
            timer.cancel();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("line-1");
        System.out.println("line-2");
        System.out.println("line-3");
        
        timer = new java.util.Timer();
        timer.schedule(new DelayedTask(), 200);
        
        System.out.println("main-end");
    }
}
