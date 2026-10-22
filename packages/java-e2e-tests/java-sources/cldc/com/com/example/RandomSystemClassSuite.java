package com.com.example;

import java.util.Random;

/**
 * 针对 jsjvm 中 java.util.Random 系统类实现的边界测试 (CLDC 1.1 兼容版本)。
 *
 * 覆盖点（对应 CLDC 1.1 Random 中声明的方法）：
 * - 构造方法：Random() / Random(long seed)
 * - 实例方法：setSeed(long) / next(int bits) / nextInt() / nextInt(int bound)
 * - 实例方法：nextLong() / nextFloat() / nextDouble()
 *
 * 注意：nextBoolean() 不是 CLDC 1.1 标准方法，已移至 Java8 测试。
 *
 * 测试重点：
 * - 固定 seed 下，各种 nextXxx 序列在不同实例之间是否完全一致（确定性）；
 * - setSeed(long) 是否重置内部状态，使得后续序列与新建同 seed 实例一致；
 * - next(int bits) 的 bits 参数是否被 [1,32] 范围 clamp，以及输出位数范围；
 * - nextInt(int bound) 在合法边界上的取值范围，以及对 bound <= 0 抛出 IllegalArgumentException；
 * - nextFloat()/nextDouble() 的取值是否始终在 [0,1) 区间内。
 *
 * 所有断言通过打印布尔值或关系结果完成，由快照统一校验，不依赖具体随机数值。
 */
public class RandomSystemClassSuite {

  /**
   * 公开 next(int) 的测试子类，用于验证 bits clamp 与位数范围。
   */
  private static class MyRandom extends Random {
    MyRandom(long seed) {
      super(seed);
    }

    int callNext(int bits) {
      return super.next(bits);
    }
  }

  public static void main(String[] args) {
    System.out.println("=== default_ctor_and_basic_ranges ===");
    testDefaultCtorAndBasicRanges();

    System.out.println("=== seeded_ctor_determinism ===");
    testSeededCtorDeterminism();

    System.out.println("=== setSeed_resets_sequence ===");
    testSetSeedResetsSequence();

    System.out.println("=== next_bits_clamp_and_ranges ===");
    testNextBitsClampAndRanges();

    System.out.println("=== nextInt_boundaries_and_exceptions ===");
    testNextIntBoundariesAndExceptions();
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
   * 默认构造函数与基础范围检查：
   * - 调用 nextInt()/nextLong()/nextFloat()/nextDouble();
   * - 只验证 float/double 在 [0,1) 区间内，不依赖具体数值；
   * - 确认这些方法可被正常调用且不会抛异常。
   */
  private static void testDefaultCtorAndBasicRanges() {
    Random r = new Random();

    // 调用但不打印具体值，以避免依赖默认 seed 的具体实现
    r.nextInt();
    r.nextLong();
    float f = r.nextFloat();
    double d = r.nextDouble();

    print("default/float_in_range", f >= 0.0f && f < 1.0f);
    print("default/double_in_range", d >= 0.0d && d < 1.0d);
    print("default/int_long_called", true);
  }

  /**
   * 带 seed 构造函数的确定性：
   * - 同一 seed 构造的两个 Random 实例，其 nextInt/nextLong/nextFloat/nextDouble
   *   序列应完全一致；
   * - 不依赖具体随机数值，只比较序列是否逐项相等。
   */
  private static void testSeededCtorDeterminism() {
    long seed = 123456789L;
    Random r1 = new Random(seed);
    Random r2 = new Random(seed);

    boolean intEqual = true;
    boolean longEqual = true;
    boolean floatEqual = true;
    boolean doubleEqual = true;

    for (int i = 0; i < 5; i++) {
      if (r1.nextInt() != r2.nextInt()) intEqual = false;
    }
    for (int i = 0; i < 3; i++) {
      if (r1.nextLong() != r2.nextLong()) longEqual = false;
    }
    for (int i = 0; i < 5; i++) {
      if (r1.nextFloat() != r2.nextFloat()) floatEqual = false;
    }
    for (int i = 0; i < 5; i++) {
      if (r1.nextDouble() != r2.nextDouble()) doubleEqual = false;
    }

    print("seeded/ints_equal", intEqual);
    print("seeded/longs_equal", longEqual);
    print("seeded/floats_equal", floatEqual);
    print("seeded/doubles_equal", doubleEqual);
  }

  /**
   * setSeed(long) 重置序列：
   * - 使用同一 seed 初始化 Random 并产生一段 nextInt 序列；
   * - 调用 setSeed(seed) 后再次产生同长度序列，应与初始序列完全一致；
   * - 与新建同 seed 实例的序列也应一致。
   */
  private static void testSetSeedResetsSequence() {
    long seed = 42L;
    Random r = new Random(seed);

    int[] s1 = new int[5];
    for (int i = 0; i < s1.length; i++) {
      s1[i] = r.nextInt();
    }

    r.setSeed(seed);
    int[] s2 = new int[5];
    for (int i = 0; i < s2.length; i++) {
      s2[i] = r.nextInt();
    }

    Random r2 = new Random(seed);
    int[] s3 = new int[5];
    for (int i = 0; i < s3.length; i++) {
      s3[i] = r2.nextInt();
    }

    boolean s1EqualsS2 = true;
    boolean s1EqualsS3 = true;
    for (int i = 0; i < s1.length; i++) {
      if (s1[i] != s2[i]) s1EqualsS2 = false;
      if (s1[i] != s3[i]) s1EqualsS3 = false;
    }

    print("setSeed/reset_equals_initial", s1EqualsS2);
    print("setSeed/reset_equals_new_instance", s1EqualsS3);
  }

  /**
   * next(int bits) 的 clamp 与位数范围：
   * - bits <= 0 应等价于 bits = 1；
   * - bits >= 32 应等价于 bits = 32；
   * - bits 为 1 时结果应为 {0,1}；bits 为 2 时结果应在 [0,4)；
   * - bits 为 31 时结果应为非负（最高位为 0）。
   */
  private static void testNextBitsClampAndRanges() {
    long seed = 999L;

    // clamp 行为：0 -> 1，负数 -> 1，>32 -> 32
    MyRandom ra0 = new MyRandom(seed);
    MyRandom rb1 = new MyRandom(seed);
    int v0 = ra0.callNext(0);
    int v1 = rb1.callNext(1);
    print("next/clamp_0_to_1", v0 == v1);

    MyRandom raNeg = new MyRandom(seed);
    MyRandom rb1b = new MyRandom(seed);
    int vNeg = raNeg.callNext(-5);
    int v1b = rb1b.callNext(1);
    print("next/clamp_negative_to_1", vNeg == v1b);

    MyRandom ra40 = new MyRandom(seed);
    MyRandom rb32 = new MyRandom(seed);
    int v40 = ra40.callNext(40);
    int v32 = rb32.callNext(32);
    print("next/clamp_over32_to_32", v40 == v32);

    // 位数范围检查
    MyRandom rr = new MyRandom(123L);
    int v1bit = rr.callNext(1);
    int v2bit = rr.callNext(2);
    int v31bit = rr.callNext(31);

    boolean range1 = (v1bit == 0 || v1bit == 1);
    boolean range2 = (v2bit >= 0 && v2bit < 4);
    boolean range31NonNegative = (v31bit >= 0);

    print("next/range_bits1", range1);
    print("next/range_bits2", range2);
    print("next/range_bits31_non_negative", range31NonNegative);
  }

  /**
   * nextInt(int bound) 的边界与异常：
   * - bound = 1 时结果必须恒为 0；
   * - bound > 1 时结果必须在 [0,bound)；
   * - bound <= 0 时应抛 IllegalArgumentException。
   */
  private static void testNextIntBoundariesAndExceptions() {
    Random r = new Random(1L);

    int v1 = r.nextInt(1);
    int v2 = r.nextInt(2);
    int v7 = r.nextInt(7);

    print("bound/1_is_zero", v1 == 0);
    print("bound/2_in_range", v2 >= 0 && v2 < 2);
    print("bound/7_in_range", v7 >= 0 && v7 < 7);

    boolean zeroIsIAE = false;
    boolean negativeIsIAE = false;
    String zeroExType = null;
    String negativeExType = null;

    try {
      r.nextInt(0);
    } catch (Throwable t) {
      zeroExType = t.getClass().getName();
      zeroIsIAE = (t instanceof IllegalArgumentException);
    }

    try {
      r.nextInt(-5);
    } catch (Throwable t) {
      negativeExType = t.getClass().getName();
      negativeIsIAE = (t instanceof IllegalArgumentException);
    }

    print("bound/0_is_IllegalArgumentException", zeroIsIAE);
    print("bound/0_exception_type", zeroExType);
    print(
        "bound/negative_is_IllegalArgumentException",
        negativeIsIAE);
    print("bound/negative_exception_type", negativeExType);
  }
}
