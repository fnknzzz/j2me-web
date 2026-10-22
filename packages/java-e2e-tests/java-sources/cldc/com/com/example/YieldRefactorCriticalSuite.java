package com.com.example;

/**
 * YIELD 机制重构关键测试用例
 * 
 * 这个用例专门测试旧方案（异常控制流）容易出错的场景：
 * 1. 主线程在 Worker 完成前就打印结果（主线程启动方式不一致）
 * 2. 多次 yield 后状态累积错误
 * 3. 深度嵌套 + 长循环组合
 */
public class YieldRefactorCriticalSuite {
    
    /**
     * 关键测试：主线程等待 Worker 完成
     * 
     * 旧方案问题：
     * - 主线程直接 invokeStatic，不经过 enqueueRun
     * - join() 可能不会正确等待
     * - 主线程会在 Worker 完成前就打印 "main-done"
     * 
     * 新方案：
     * - 主线程也通过 enqueueRun 启动
     * - join() 正确等待
     * - "main-done" 一定在 "worker-done" 之后
     */
    static class CriticalWorker extends Thread {
        public void run() {
            System.out.println("worker-start");
            
            // 长循环，确保会触发多次 yield
            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum = sum + i;
            }
            
            System.out.print("worker-sum=");
            System.out.println(sum);
            System.out.println("worker-done");
        }
    }
    
    /**
     * 关键测试：多次 yield 后的状态累积
     * 
     * 旧方案问题：
     * - 每次 yield 后重新创建 Generator
     * - PC 位置可能不准确
     * - 循环计数器可能重置或跳过
     * 
     * 新方案：
     * - Generator 实例保留
     * - PC 位置精确
     * - 计数器正确累积
     */
    static class AccumulationWorker extends Thread {
        public void run() {
            System.out.println("accumulation-start");
            
            int counter1 = 0;
            // 第一段循环
            for (int i = 0; i < 30; i++) {
                counter1++;
            }
            
            // 主动 yield，触发状态保存
            Thread.yield();
            
            int counter2 = 0;
            // 第二段循环
            for (int i = 0; i < 30; i++) {
                counter2++;
            }
            
            // 再次 yield
            Thread.yield();
            
            int counter3 = 0;
            // 第三段循环
            for (int i = 0; i < 30; i++) {
                counter3++;
            }
            
            System.out.print("counter1=");
            System.out.println(counter1);
            System.out.print("counter2=");
            System.out.println(counter2);
            System.out.print("counter3=");
            System.out.println(counter3);
            System.out.println("accumulation-done");
        }
    }
    
    /**
     * 关键测试：深度嵌套 + 长循环
     * 
     * 旧方案问题：
     * - 调用栈深度增加，状态保存复杂
     * - 长循环触发多次 yield
     * - 恢复时调用栈可能错误
     * 
     * 新方案：
     * - Generator 自动保存完整调用栈
     * - 恢复时状态完整
     */
    static class DeepNestedWorker extends Thread {
        int level1Counter = 0;
        int level2Counter = 0;
        int level3Counter = 0;
        
        void level3() {
            // 长循环，会触发 yield
            for (int i = 0; i < 40; i++) {
                level3Counter++;
            }
        }
        
        void level2() {
            for (int i = 0; i < 30; i++) {
                level2Counter++;
            }
            level3();
        }
        
        void level1() {
            for (int i = 0; i < 20; i++) {
                level1Counter++;
            }
            level2();
        }
        
        public void run() {
            System.out.println("deep-start");
            level1();
            System.out.print("level1=");
            System.out.println(level1Counter);
            System.out.print("level2=");
            System.out.println(level2Counter);
            System.out.print("level3=");
            System.out.println(level3Counter);
            System.out.println("deep-done");
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== YIELD Critical Tests ===");
        System.out.println();
        
        // 测试 1：主线程等待
        System.out.println("Test 1: Main Thread Wait");
        CriticalWorker w1 = new CriticalWorker();
        w1.start();
        w1.join();
        System.out.println("main-done");
        System.out.println();
        
        // 测试 2：状态累积
        System.out.println("Test 2: State Accumulation");
        AccumulationWorker w2 = new AccumulationWorker();
        w2.start();
        w2.join();
        System.out.println();
        
        // 测试 3：深度嵌套
        System.out.println("Test 3: Deep Nesting");
        DeepNestedWorker w3 = new DeepNestedWorker();
        w3.start();
        w3.join();
        System.out.println();
        
        System.out.println("=== All Critical Tests Passed ===");
    }
}
