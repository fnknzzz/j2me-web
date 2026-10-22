package com.com.example;

/**
 * 原子操作保护测试
 * 
 * 这个测试验证原子操作（如类初始化、构造函数）不会被 yield 中断
 * 
 * 旧方案问题：
 * - 没有原子操作保护机制
 * - 类初始化过程可能被 yield 中断
 * - 导致类初始化状态不一致
 * - 多线程访问未完全初始化的类
 * 
 * 新方案：
 * - enterAtomicOperation/exitAtomicOperation 保护
 * - 类初始化在原子操作中，不会被 yield 中断
 * - 确保类初始化的完整性和一致性
 */
public class AtomicOperationSuite {
    
    /**
     * 测试类：包含静态初始化块和静态字段
     * 
     * 如果类初始化被中断：
     * - counter1 可能已初始化，但 counter2 还未初始化
     * - 其他线程可能看到部分初始化的类
     * - 导致数据不一致
     */
    static class InitializationTest {
        // 静态字段初始化
        static int counter1 = computeValue1();
        static int counter2 = computeValue2();
        static int counter3 = computeValue3();
        
        // 静态初始化块
        static {
            System.out.println("clinit-start");
            
            // 长时间计算，可能触发 yield
            int sum = 0;
            for (int i = 0; i < 50; i++) {
                sum = sum + i;
            }
            counter1 = counter1 + sum;
            
            System.out.println("clinit-end");
        }
        
        static int computeValue1() {
            int result = 0;
            for (int i = 0; i < 20; i++) {
                result = result + i;
            }
            return result;
        }
        
        static int computeValue2() {
            int result = 0;
            for (int i = 0; i < 30; i++) {
                result = result + i;
            }
            return result;
        }
        
        static int computeValue3() {
            int result = 0;
            for (int i = 0; i < 40; i++) {
                result = result + i;
            }
            return result;
        }
        
        static int getSum() {
            return counter1 + counter2 + counter3;
        }
    }
    
    /**
     * Worker 线程：访问需要初始化的类
     */
    static class InitWorker extends Thread {
        public void run() {
            System.out.println("worker-start");
            
            // 访问 InitializationTest，触发类初始化
            int sum = InitializationTest.getSum();
            
            System.out.print("worker-sum=");
            System.out.println(sum);
            System.out.println("worker-end");
        }
    }
    
    /**
     * 测试构造函数的原子性
     */
    static class ConstructorTest {
        int field1;
        int field2;
        int field3;
        
        ConstructorTest() {
            System.out.println("constructor-start");
            
            // 长时间计算
            int sum1 = 0;
            for (int i = 0; i < 30; i++) {
                sum1 = sum1 + i;
            }
            this.field1 = sum1;
            
            int sum2 = 0;
            for (int i = 0; i < 30; i++) {
                sum2 = sum2 + i;
            }
            this.field2 = sum2;
            
            int sum3 = 0;
            for (int i = 0; i < 30; i++) {
                sum3 = sum3 + i;
            }
            this.field3 = sum3;
            
            System.out.println("constructor-end");
        }
        
        int getSum() {
            return field1 + field2 + field3;
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Atomic Operation Tests ===");
        System.out.println();
        
        // 测试 1：类初始化的原子性
        System.out.println("Test 1: Class Initialization");
        InitWorker w1 = new InitWorker();
        w1.start();
        w1.join();
        System.out.println();
        
        // 测试 2：构造函数的原子性
        System.out.println("Test 2: Constructor");
        ConstructorTest obj = new ConstructorTest();
        System.out.print("obj-sum=");
        System.out.println(obj.getSum());
        System.out.println();
        
        System.out.println("=== All Tests Passed ===");
    }
}
