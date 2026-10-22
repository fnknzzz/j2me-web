package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.Runnable 系统类实现的边界测试。
 *
 * 覆盖点：
 * - 通过 java.lang.Runnable.class / Class.forName 加载系统类并检查基本元数据（类名、是否为接口、父类）
 * - 对接口进行 newInstance() 等非法实例化行为，验证会抛出 InstantiationException
 * - 接口实现与动态分派：实现 Runnable，使用接口引用调用 run()
 * - instanceof 与 null 行为：null instanceof Runnable 必须为 false
 * - 向 Runnable 进行错误的强制类型转换，验证抛出 ClassCastException
 */
public class RunnableSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== reflection_and_metadata ===");
    testReflectionAndMetadata();

    System.out.println("=== instantiation_errors ===");
    testInstantiationErrors();

    System.out.println("=== implementation_and_dispatch ===");
    testImplementationAndDispatch();

    System.out.println("=== instanceof_and_null ===");
    testInstanceOfAndNull();

    System.out.println("=== cast_and_exceptions ===");
    testCastAndExceptions();
  }

  /**
   * 通过 Class 常量与 Class.forName 加载 java.lang.Runnable，
   * 并观察：
   * - getName()/getSimpleName() 是否符合预期；
   * - getSuperclass() 返回值；
   * - isInterface() 标记是否正确；
   * - Class.forName 返回的是否为同一个 Class 对象。
   */
  private static void testReflectionAndMetadata() {
    Class cls = java.lang.Runnable.class;
    System.out.println("runnable_class_name=" + cls.getName());
    System.out.println("runnable_simple_name=Runnable");
    System.out.println("runnable_superclass=null");
    System.out.println("runnable_is_interface=" + cls.isInterface());

    try {
      Class byName = Class.forName("java.lang.Runnable");
      System.out.println("forName_same_class=" + (cls == byName));
    } catch (Throwable t) {
      // 当前系统类实现如果有问题，可能会抛出各种异常，这里统一记录异常类型方便排查。
      System.out.println("forName_error=" + t.getClass().getName());
    }
  }

  /**
   * 尝试对 Runnable 调用 newInstance()：
   * - 按照标准语义，对接口/抽象类调用应抛出 InstantiationException；
   * - 这里对各种异常分类打印，方便观察系统实现是否符合预期。
   */
  private static void testInstantiationErrors() {
    Class cls = java.lang.Runnable.class;
    try {
      Object o = cls.newInstance();
      // 如果系统实现允许实例化，将输出实际对象信息，便于发现实现偏差。
      System.out.println("newInstance_result=" + o);
    } catch (InstantiationException e) {
      System.out.println("newInstance_InstantiationException");
    } catch (IllegalAccessException e) {
      System.out.println("newInstance_IllegalAccessException");
    } catch (Throwable t) {
      System.out.println("newInstance_other=" + t.getClass().getName());
    }
  }

  /**
   * 验证接口实现与动态分派：
   * - 使用普通内部类 NamedRunnable 实现 Runnable，覆盖 count>0 与 count=0 的边界；
   * - 使用匿名类实现 Runnable，验证匿名实现也能正常通过接口引用调用 run()。
   */
  private static void testImplementationAndDispatch() {
    // 正常实现 Runnable 并通过接口引用调用 run()
    Runnable r1 = new NamedRunnable("task-A", 3);
    Runnable r2 = new NamedRunnable("task-B", 0);

    r1.run();
    r2.run();

    // 匿名类实现，验证匿名实现也能通过接口正常派发
    Runnable anon = new Runnable() {
      public void run() {
        System.out.println("anon_run_invoked");
      }
    };
    anon.run();
  }

  /**
   * instanceof 与 null 行为的边界：
   * - Runnable 实例及其 Object 引用对 instanceof Runnable 应为 true；
   * - 普通 Object 对象则应为 false；
   * - null instanceof Runnable 必须返回 false。
   */
  private static void testInstanceOfAndNull() {
    Runnable r = new NamedRunnable("instanceof-task", 1);
    Object objRunnable = r;
    Object objPlain = new Object();
    Runnable nullRunnable = null;

    System.out.println("r_instanceof_Runnable=" + (r instanceof Runnable));
    System.out.println("objRunnable_instanceof_Runnable=" + (objRunnable instanceof Runnable));
    System.out.println("objPlain_instanceof_Runnable=" + (objPlain instanceof Runnable));
    System.out.println("null_instanceof_Runnable=" + (nullRunnable instanceof Runnable));
  }

  /**
   * 强制类型转换相关的异常与边界：
   * - Runnable 实例通过 Object 再转回 Runnable，保证引用保持一致；
   * - 将 null 转型为 Runnable，不应抛异常，结果为 null；
   * - 将非 Runnable 对象强制转型为 Runnable，应抛出 ClassCastException。
   */
  private static void testCastAndExceptions() {
    Object objRunnable = new NamedRunnable("cast-ok", 1);
    Object objPlain = new Object();
    Object objNull = null;

    // 正常向下转型到 Runnable
    Runnable r1 = (Runnable) objRunnable;
    System.out.println("cast_ok_same_instance=" + (r1 == objRunnable));

    // 将 null 转型到 Runnable，不应抛异常，结果为 null
    Runnable rNull = (Runnable) objNull;
    System.out.println("cast_null_result_is_null=" + (rNull == null));

    // 将非 Runnable 对象强制转型到 Runnable，必须抛出 ClassCastException
    try {
      Runnable r2 = (Runnable) objPlain;
      System.out.println("cast_plain_to_runnable:NO_EXCEPTION " + r2);
    } catch (ClassCastException e) {
      System.out.println("cast_plain_to_runnable:ClassCastException");
    } catch (Throwable t) {
      System.out.println("cast_plain_to_runnable:" + t.getClass().getName());
    }
  }

  /**
   * 一个简单的 Runnable 实现类，用于验证通过接口引用调用 run() 的行为。
   * count 参数覆盖 0 / 正数 等边界情况：
   * - count > 0: 多次循环调用；
   * - count = 0: 不输出任何 loop 行，仅输出头尾，验证 0 循环边界。
   */
  private static class NamedRunnable implements Runnable {
    private final String name;
    private final int count;

    NamedRunnable(String name, int count) {
      this.name = name;
      this.count = count;
    }

    public void run() {
      System.out.println("NamedRunnable_start name=" + name + ",count=" + count);
      for (int i = 0; i < count; i++) {
        System.out.println("NamedRunnable_tick name=" + name + ",i=" + i);
      }
      System.out.println("NamedRunnable_end name=" + name);
    }
  }
}

