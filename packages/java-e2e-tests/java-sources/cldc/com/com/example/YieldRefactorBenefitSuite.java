package com.com.example;

/**
 * YIELD 机制重构效果验证用例
 * 
 * 这个用例展示了从异常控制流改为 Generator 的必要性和效果
 * 
 * 关键改进：
 * 1. Generator 天然保存状态（局部变量、PC 位置）
 * 2. 真正的暂停/恢复，零状态不一致
 * 3. Generator 实例复用，性能优秀
 * 4. 统一的 enqueueRun 架构（主线程和 Worker 线程）
 */
public class YieldRefactorBenefitSuite {
    
    static class SimpleWorker extends Thread {
        public void run() {
            System.out.println("worker-start");
            int counter = 0;
            for (int i = 0; i < 50; i++) {
                counter++;
            }
            System.out.print("worker-counter=");
            System.out.println(counter);
            System.out.println("worker-end");
        }
    }
    
    static class NestedWorker extends Thread {
        int depth1 = 0;
        int depth2 = 0;
        
        void level2() {
            for (int i = 0; i < 10; i++) {
                depth2++;
            }
        }
        
        void level1() {
            for (int i = 0; i < 10; i++) {
                depth1++;
            }
            level2();
        }
        
        public void run() {
            System.out.println("nested-start");
            level1();
            System.out.print("depth1=");
            System.out.println(depth1);
            System.out.print("depth2=");
            System.out.println(depth2);
            System.out.println("nested-end");
        }
    }
    
    static class YieldWorker extends Thread {
        int before = 0;
        int after = 0;
        
        public void run() {
            System.out.println("yield-start");
            
            for (int i = 0; i < 15; i++) {
                before++;
            }
            
            Thread.yield();
            
            for (int i = 0; i < 15; i++) {
                after++;
            }
            
            System.out.print("before=");
            System.out.println(before);
            System.out.print("after=");
            System.out.println(after);
            System.out.println("yield-end");
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== YIELD Refactor Benefits ===");
        System.out.println();
        
        // 测试 1：基本循环
        System.out.println("Test 1: Basic Loop");
        SimpleWorker w1 = new SimpleWorker();
        w1.start();
        w1.join();
        System.out.println();
        
        // 测试 2：嵌套调用
        System.out.println("Test 2: Nested Calls");
        NestedWorker w2 = new NestedWorker();
        w2.start();
        w2.join();
        System.out.println();
        
        // 测试 3：显式 yield
        System.out.println("Test 3: Explicit Yield");
        YieldWorker w3 = new YieldWorker();
        w3.start();
        w3.join();
        System.out.println();
        
        System.out.println("=== All Tests Passed ===");
    }
}
