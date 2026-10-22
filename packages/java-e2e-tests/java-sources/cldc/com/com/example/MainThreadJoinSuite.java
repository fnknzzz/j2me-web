package com.com.example;

/**
 * 主线程 join 等待测试
 * 
 * 这个测试专门验证主线程的 join() 是否正确等待 Worker 线程完成
 * 
 * 旧方案问题（阶段2总结中提到的真实 bug）：
 * - 主线程直接 invokeStatic，不经过 enqueueRun
 * - 第一次 yield 后没有保存的 Generator
 * - 从调用栈重新创建 Generator，PC 位置不正确
 * - 导致跳过 join 的等待逻辑
 * - 结果：joined 在 worker-run 之前输出
 * 
 * 新方案：
 * - 主线程也通过 enqueueRun 启动
 * - Generator 实例被保存和恢复
 * - join() 正确等待
 * - 结果：worker-run 在 joined 之前输出
 */
public class MainThreadJoinSuite {
    
    static class SimpleWorker extends Thread {
        public void run() {
            // 做一些工作，确保会触发 yield
            int sum = 0;
            for (int i = 0; i < 50; i++) {
                sum = sum + i;
            }
            System.out.println("worker-run");
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("start");
        
        // 主线程创建并启动 Worker
        Thread t = new SimpleWorker();
        t.start();
        
        // 主线程等待 Worker 完成
        t.join();
        
        // 这行应该在 worker-run 之后输出
        System.out.println("joined");
        
        System.out.println("done");
    }
}
