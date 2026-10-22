package com.com.example;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

/**
 * 针对 jsjvm 中 java.lang.ref.Reference 系统类实现的测试。
 *
 * 覆盖点（对应 ReferenceDecl 中的 clear()/get() 实现）：
 * - PD 存在且保存有效句柄时，get() 能返回原始对象引用；
 * - 调用 clear() 后，PD 中的 refHandle 被设置为无效值，get() 返回 null；
 * - referent 为 null 时，PD 中应记录「空引用」，get() 返回 null；
 * - 未设置 PD 的 Reference 子类上调用 clear()/get() 不应抛异常，并且 get() 返回 null。
 */
public class ReferenceSystemClassSuite {

  public static void main(String[] args) {
    testGetWithValidHandle();
    testGetWithNullReferent();
    testClearWithPdPresent();
    testClearAndGetWithMissingPd();
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
   * 辅助类：尽量模拟 jsjvm 中 getPD(frame) 返回 null / undefined 的情况，
   * 即从未通过系统类构造函数写入 PD。
   *
   * 这里继承自 WeakReference，构造函数中仅调用 super(null)，不触及任何队列语义。
   */
  private static final class DummyReference extends WeakReference {

    DummyReference() {
      super(null);
    }

    
    public Object get() {
      return super.get();
    }

    
    public void clear() {
      super.clear();
    }

    
    public boolean enqueue() {
      return false;
    }

    
    public boolean isEnqueued() {
      return false;
    }
  }

  /**
   * PD 中保存有效句柄：new WeakReference(obj) 通过 WeakReferenceDecl 写入 PD，
   * ReferenceDecl.get 中应走「h >= 0」分支，并返回对应对象。
   */
  private static void testGetWithValidHandle() {
    System.out.println("=== getWithValidHandle ===");

    Object target = new Object();
    WeakReference ref = new WeakReference(target);

    Object first = ref.get();
    Object second = ref.get();

    print("firstNotNull", first != null);
    print("secondSameAsFirst", first == second);
    print("sameAsTarget", first == target);
  }

  /**
   * referent 为 null：WeakReferenceDecl 会将 PD.refHandle 设为 -1，
   * ReferenceDecl.get 中应走「h < 0」分支，返回 null 而非抛异常。
   */
  private static void testGetWithNullReferent() {
    System.out.println("=== getWithNullReferent ===");

    WeakReference ref = new WeakReference(null);
    Object got = ref.get();

    print("getReturnsNull", got == null);
  }

  /**
   * PD 存在且 refHandle 指向有效对象时调用 clear()：
   * - clear() 应通过 getPD(frame) 取到 PD 并将 refHandle 置为 -1；
   * - 随后 get() 应返回 null，且不影响原始 target 对象本身的可用性。
   */
  private static void testClearWithPdPresent() {
    System.out.println("=== clearWithPdPresent ===");

    Object target = new Object();
    WeakReference ref = new WeakReference(target);

    Object before = ref.get();
    print("beforeNotNull", before != null);
    print("beforeSameAsTarget", before == target);

    // 触发 ReferenceDecl.clear：若 PD 存在，则将 refHandle 改为 -1。
    ref.clear();

    Object after = ref.get();
    print("afterIsNull", after == null);
    // 多次 clear() 也应保持幂等，不抛异常。
    ref.clear();
    print("afterSecondClearStillNull", ref.get() == null);
  }

  /**
   * 未为该实例写入 PD（getPD(frame) 返回 null / undefined）：
   * - clear() 中应直接返回，不对 PD 做任何操作，也不抛异常；
   * - get() 应走「pd 为空 → h 默认为 -1」分支，直接返回 null。
   */
  private static void testClearAndGetWithMissingPd() {
    System.out.println("=== clearAndGetWithMissingPd ===");

    DummyReference ref = new DummyReference();

    // 在从未写入 PD 的对象上直接调用 get()。
    print("beforeClearGetNull", ref.get() == null);

    // clear() 不应因为 PD 缺失而抛出异常。
    ref.clear();

    // 再次 get() 仍应返回 null。
    print("afterClearGetNull", ref.get() == null);
  }
}

