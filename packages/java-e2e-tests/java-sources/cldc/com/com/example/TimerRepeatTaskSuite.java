package com.com.example;

/**
 * 测试场景：Timer 重复任务
 * 
 * 1. 调度一个每 30ms 执行一次的重复任务
 * 2. 任务执行 3 次后 cancel
 * 3. 验证任务确实执行了 3 次
 */
public class TimerRepeatTaskSuite {
    static java.util.Timer timer;
    static int count = 0;

    static class RepeatTask extends java.util.TimerTask {
        public void run() {
            count++;
            System.out.println("tick-" + count);
            if (count >= 3) {
                timer.cancel();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        timer = new java.util.Timer();
        timer.schedule(new RepeatTask(), 10, 30);
        Thread.sleep(200);
        System.out.println("done");
    }
}
