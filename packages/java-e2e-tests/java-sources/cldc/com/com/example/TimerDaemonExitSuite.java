package com.com.example;

/**
 * 测试场景：守护线程 Timer 在主线程结束后自动退出
 * 
 * 1. 创建守护线程 Timer（isDaemon=true）
 * 2. 调度一个 500ms 后执行的任务
 * 3. 主线程 sleep 50ms 后结束
 * 4. 验证 JVM 退出，Timer 任务不会执行（因为是守护线程）
 */
public class TimerDaemonExitSuite {
    public static void main(String[] args) throws Exception {
        java.util.Timer timer = new java.util.Timer(true);
        timer.schedule(new java.util.TimerTask() {
            public void run() {
                System.out.println("daemon-task-executed");
            }
        }, 500);
        
        Thread.sleep(50);
        System.out.println("main-end");
    }
}
