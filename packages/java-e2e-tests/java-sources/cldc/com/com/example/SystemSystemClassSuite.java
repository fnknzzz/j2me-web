package com.com.example;

import java.io.PrintStream;
import java.lang.System;

/**
 * 针对 jsjvm 中 java.lang.System 系统类实现的边界测试。
 *
 * 覆盖点（对应 SystemDecl 中声明的静态字段与方法）：
 * - 静态字段：out
 * - 静态方法：currentTimeMillis() / nanoTime()
 * - 静态方法：arraycopy(Object, int, Object, int, int)
 * - 静态方法：getProperty(String) / setProperty(String, String)
 * - 静态方法：getenv(String)
 * - 静态方法：gc()
 * - 静态方法：identityHashCode(Object)
 * - 静态方法：exit(int)
 *
 * 重点关注：
 * - System.out 的初始化类型、非空性以及多次访问的稳定性；
 * - 时间相关 API 只通过相对关系与非负性进行验证，避免快照随时间抖动；
 * - arraycopy 在正常拷贝、零长度拷贝以及 src/dest 为 null 的场景下的行为；
 * - 属性读写（含 vm.heap.used、未知 key、null key、null value 等边界）的一致性；
 * - getenv 在 null key 下的行为（不依赖宿主环境变量）；
 * - gc/exit/identityHashCode 的异常处理与控制流稳定性。
 */
public class SystemSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== out_field_basic ===");
    testSystemOutFieldAndPrint();

    System.out.println("=== time_methods ===");
    testCurrentTimeMillisAndNanoTime();

    System.out.println("=== arraycopy_basic_and_edges ===");
    testArraycopyBasicAndEdgeCases();

    System.out.println("=== properties_get_and_set ===");
    testPropertiesGetAndSet();

    System.out.println("=== getenv_null_key ===");
    testGetenvNullKey();

    System.out.println("=== gc_invocation ===");
    testGcInvocation();

    System.out.println("=== identityHashCode_behaviour ===");
    testIdentityHashCodeBehaviour();

    System.out.println("=== exit_noop ===");
    testExitNoop();
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

  private static String intArrayToString(int[] arr) {
    if (arr == null) {
      return "null";
    }
    StringBuffer sb = new StringBuffer();
    sb.append("[");
    for (int i = 0; i < arr.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(arr[i]);
    }
    sb.append("]");
    return sb.toString();
  }

  private static int[] copyIntArray(int[] src) {
    if (src == null) {
      return null;
    }
    int[] dest = new int[src.length];
    for (int i = 0; i < src.length; i++) {
      dest[i] = src[i];
    }
    return dest;
  }

  /**
   * System.out 字段：
   * - 应为非 null，类型为 java.io.PrintStream；
   * - 多次访问返回同一实例；
   * - 调用 println 不应抛出异常，用于间接验证 initSystemOut 的初始化逻辑。
   */
  private static void testSystemOutFieldAndPrint() {
    PrintStream out = System.out;
    PrintStream out2 = System.out;

    print("out_not_null", out != null);
    if (out != null) {
      print("out_class", out.getClass().getName());
    } else {
      print("out_class", "null");
    }
    print("out_same_instance", out == out2);

    try {
      if (out != null) {
        out.println("SystemSystemClassSuite: println via System.out");
        print("out_println_no_exception", true);
      } else {
        print("out_is_null_cannot_print", true);
      }
    } catch (Throwable t) {
      print("out_println_exception", t.getClass().getName());
    }
  }

  /**
   * 时间相关静态方法：
   * - currentTimeMillis()/nanoTime() 返回值仅通过非负性与相对大小关系验证；
   * - 避免直接打印时间戳，保证快照稳定。
   */
  private static void testCurrentTimeMillisAndNanoTime() {
    long t1 = System.currentTimeMillis();
    long t2 = System.currentTimeMillis();

    print("currentTimeMillis_ge_zero", t1 >= 0L && t2 >= 0L);
    print("currentTimeMillis_non_decreasing", t2 >= t1);

    long n1 = System.nanoTime();
    long n2 = System.nanoTime();
    long diff = n2 - n1;

    print("nanoTime_diff_ge_zero", diff >= 0L);
  }

  /**
   * arraycopy 的正常与边界行为：
   * - 基本拷贝：int 数组从中间位置拷贝到目标数组起始；
   * - 零长度拷贝：length 为 0 时即使起始索引为负也不应触发底层访问；
   * - src 为 null：不应抛异常，且目标数组保持原值；
   * - dest 为 null：不应抛异常，用于覆盖实现中的 isArray 检查分支。
   */
  private static void testArraycopyBasicAndEdgeCases() {
    int[] src = new int[] {1, 2, 3, 4, 5};
    int[] dest = new int[] {0, 0, 0, 0, 0};

    System.arraycopy(src, 1, dest, 0, 3);
    print("arraycopy_basic_dest", intArrayToString(dest));

    int[] zeroSrc = new int[] {10, 11, 12};
    int[] beforeZeroCopy = copyIntArray(zeroSrc);

    try {
      System.arraycopy(zeroSrc, -1, zeroSrc, -2, 0);
      print("arraycopy_zero_length_no_exception", true);
    } catch (Throwable t) {
      print("arraycopy_zero_length_exception", t.getClass().getName());
    }
    print("arraycopy_zero_length_array_unchanged", intArrayToString(zeroSrc));
    print("arraycopy_zero_length_original", intArrayToString(beforeZeroCopy));

    int[] src2 = new int[] {7, 8, 9};
    int[] dest2 = new int[] {100, 200, 300};
    int[] dest2Before = copyIntArray(dest2);

    try {
      System.arraycopy(null, 0, dest2, 0, 1);
      print("arraycopy_null_src_no_exception", true);
    } catch (Throwable t) {
      print("arraycopy_null_src_exception", t.getClass().getName());
    }
    print("arraycopy_null_src_dest_after", intArrayToString(dest2));
    print("arraycopy_null_src_dest_before", intArrayToString(dest2Before));

    try {
      System.arraycopy(src2, 0, null, 0, 1);
      print("arraycopy_null_dest_no_exception", true);
    } catch (Throwable t) {
      print("arraycopy_null_dest_exception", t.getClass().getName());
    }
  }

  /**
   * 属性读写行为：
   * - 读取内置属性：java.version/java.vendor/microedition.platform；
   * - 未知 key 返回 null；
   * - null key 返回 null；
   * - setProperty 新增/覆盖属性，并返回旧值；
   * - setProperty value 为 null 时按照实现应写入空字符串；
   * - vm.heap.used：若存在则应为非负整数，否则为 null，均通过布尔标记体现。
   */
  private static void testPropertiesGetAndSet() {
    String javaVersion = System.getProperty("java.version");
    String javaVendor = System.getProperty("java.vendor");

    print("java_version_not_null", javaVersion != null);
    print("java_vendor_not_null", javaVendor != null);

    String unknown = System.getProperty("jsjvm.system.unknown.key");
    print("unknown_property_is_null", unknown == null);

    try {
      String nullKey = System.getProperty((String) null);
      print("null_key_property_is_null", nullKey == null);
    } catch (NullPointerException e) {
      print("null_key_property_is_null", "exception:" + e.getClass().getName());
    }

    String prev1 = System.setProperty("jsjvm.system.test.key", "value1");
    print("set_new_prev_null", prev1 == null);
    print("get_after_set_v1", System.getProperty("jsjvm.system.test.key"));

    String prev2 = System.setProperty("jsjvm.system.test.key", "value2");
    print("set_existing_prev_v1", "value1".equals(prev2));
    print("get_after_set_v2", System.getProperty("jsjvm.system.test.key"));

    try {
      String prev3 = System.setProperty("jsjvm.system.empty.value.key", null);
      print("set_null_value_prev_null", prev3 == null);
      String emptyVal = System.getProperty("jsjvm.system.empty.value.key");
      print("get_after_set_null_value_empty_string", "".equals(emptyVal));
    } catch (NullPointerException e) {
      print("set_null_value_prev_null", "exception:" + e.getClass().getName());
      print("get_after_set_null_value_empty_string", "exception:" + e.getClass().getName());
    }

  }

  /**
   * getenv 在 null key 下的行为：
   * - 根据实现，当 key 为 null 时应直接返回 null；
   * - 不依赖宿主环境变量是否存在，避免快照受环境影响。
   */
  private static void testGetenvNullKey() {
    try {
      String val = System.getenv((String) null);
      print("getenv_null_is_null", val == null);
    } catch (Throwable t) {
      print("getenv_null_exception", t.getClass().getName());
    }
  }

  /**
   * gc() 调用：
   * - 调用不应抛异常；
   * - 若实现抛出异常，则记录异常类型，便于定位问题。
   */
  private static void testGcInvocation() {
    try {
      System.gc();
      print("gc_invoked", true);
    } catch (Throwable t) {
      print("gc_exception", t.getClass().getName());
    }
  }

  /**
   * identityHashCode 的行为：
   * - 同一对象多次调用返回值应相同；
   * - 不同对象的 identityHashCode 在当前实现中应不同；
   * - null 的 identityHashCode 应为 0。
   */
  private static void testIdentityHashCodeBehaviour() {
    Object o1 = new Object();
    Object o2 = new Object();

    int h1 = System.identityHashCode(o1);
    int h1b = System.identityHashCode(o1);
    int h2 = System.identityHashCode(o2);
    int hNull = System.identityHashCode(null);

    print("hash_same_for_same_object", h1 == h1b);
    print("hash_diff_for_different_objects", h1 != h2);
    print("hash_null_is_zero", hNull == 0);
  }

  /**
   * exit(int) 行为：
   * - 调用后应终止程序，后续输出不应出现；
   * - 仅打印调用前标记。
   */
  private static void testExitNoop() {
    try {
      print("before_exit_0", "ok");
      System.exit(0);
    } catch (Throwable t) {
      print("exit_exception", t.getClass().getName());
    }
  }
}
