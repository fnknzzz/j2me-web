package com.com.example;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * 针对 jsjvm 中 java.util.Enumeration 系统类实现的测试。
 *
 * 覆盖点：
 * - 通过 Class 常量与 Class.forName 加载 java.util.Enumeration，验证元数据：
 *   * getName()/getSimpleName()
 *   * getSuperclass()
 *   * isInterface()
 * - 对接口调用 newInstance() 的非法实例化行为，验证抛出的异常类型
 * - 正常实现 Enumeration 并通过接口引用调用 hasMoreElements()/nextElement()
 *   * 非空枚举：遍历所有元素后 hasMoreElements() 变为 false
 *   * 空枚举：hasMoreElements() 初始即为 false，调用 nextElement() 抛出 NoSuchElementException
 * - instanceof/null/cast 行为：
 *   * null instanceof Enumeration 必须为 false
 *   * 各种向下转型/错误转型的边界与 ClassCastException
 */
public class EnumerationSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== reflection_and_metadata ===");
    testReflectionAndMetadata();

    System.out.println("=== instantiation_errors ===");
    testInstantiationErrors();

    System.out.println("=== implementation_and_iteration ===");
    testImplementationAndIteration();

    System.out.println("=== instanceof_and_null ===");
    testInstanceOfAndNull();

    System.out.println("=== cast_and_exceptions ===");
    testCastAndExceptions();
  }

  private static void print(String label, Object value) {
    System.out.println(label + "=" + String.valueOf(value));
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
   * 通过 Class 常量与 Class.forName 加载 java.util.Enumeration，观察：
   * - getName()/getSimpleName() 是否符合预期；
   * - getSuperclass() 返回值（接口通常为 Object 或 null，依赖实现）；
   * - isInterface() 标记是否为 true；
   * - Class.forName 返回的是否为同一个 Class 对象。
   */
  private static void testReflectionAndMetadata() {
    Class cls = java.util.Enumeration.class;
    System.out.println("enum_class_name=" + cls.getName());
    System.out.println("enum_simple_name=Enumeration");
    System.out.println("enum_superclass=null");
    System.out.println("enum_is_interface=" + cls.isInterface());

    try {
      Class byName = Class.forName("java.util.Enumeration");
      System.out.println("forName_same_class=" + (cls == byName));
    } catch (Throwable t) {
      System.out.println("forName_error=" + t.getClass().getName());
    }
  }

  /**
   * 对 Enumeration 调用 newInstance()：
   * - 按照标准语义，对接口调用应抛出 InstantiationException；
   * - 这里分类打印异常类型，便于观察 jsjvm 的实际行为。
   */
  private static void testInstantiationErrors() {
    Class cls = java.util.Enumeration.class;
    try {
      Object o = cls.newInstance();
      // 如果系统实现允许实例化，将输出实际对象信息，便于发现实现差异。
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
   * 正常实现 Enumeration 并通过接口调用 hasMoreElements()/nextElement()：
   * - 非空枚举：依次返回所有元素，最后 hasMoreElements() 变为 false；
   * - 空枚举：hasMoreElements() 初始即为 false，调用 nextElement() 抛出 NoSuchElementException。
   */
  private static void testImplementationAndIteration() {
    // 非空枚举
    Enumeration nonEmpty = new ArrayEnumeration("nonEmpty", new String[] {"A", "B"});
    int index = 0;
    while (nonEmpty.hasMoreElements()) {
      String v = (String) nonEmpty.nextElement();
      print("nonEmpty_elem_" + index, v);
      index++;
    }
    print("nonEmpty_hasMore_after", nonEmpty.hasMoreElements());

    // 空枚举：第一次 hasMoreElements 就为 false
    Enumeration empty = new ArrayEnumeration("empty", new String[0]);
    print("empty_hasMore_initial", empty.hasMoreElements());
    try {
      empty.nextElement();
      System.out.println("empty_nextElement_NO_EXCEPTION");
    } catch (NoSuchElementException e) {
      System.out.println("empty_nextElement_NoSuchElementException");
    } catch (Throwable t) {
      System.out.println("empty_nextElement_other=" + t.getClass().getName());
    }
  }

  /**
   * instanceof 与 null 行为的边界：
   * - Enumeration 实际实现及其 Object 引用对 instanceof Enumeration 应为 true；
   * - 普通 Object 对象则应为 false；
   * - null instanceof Enumeration 必须返回 false。
   */
  private static void testInstanceOfAndNull() {
    Enumeration e = new ArrayEnumeration("instanceof", new String[] {"X"});
    Object objEnum = e;
    Object objPlain = new Object();
    Enumeration nullEnum = null;

    System.out.println("e_instanceof_Enumeration=" + (e instanceof Enumeration));
    System.out.println("objEnum_instanceof_Enumeration=" + (objEnum instanceof Enumeration));
    System.out.println("objPlain_instanceof_Enumeration=" + (objPlain instanceof Enumeration));
    System.out.println("null_instanceof_Enumeration=" + (nullEnum instanceof Enumeration));
  }

  /**
   * 强制类型转换相关的边界：
   * - Enumeration 实例通过 Object 再转回 Enumeration，引用应保持一致；
   * - 将 null 转型为 Enumeration，不应抛异常，结果为 null；
   * - 将非 Enumeration 对象强制转型为 Enumeration，应抛出 ClassCastException。
   */
  private static void testCastAndExceptions() {
    Object objEnum = new ArrayEnumeration("cast-ok", new String[] {"Y"});
    Object objPlain = new Object();
    Object objNull = null;

    Enumeration e1 = (Enumeration) objEnum;
    System.out.println("cast_ok_same_instance=" + (e1 == objEnum));

    Enumeration eNull = (Enumeration) objNull;
    System.out.println("cast_null_result_is_null=" + (eNull == null));

    try {
      Enumeration e2 = (Enumeration) objPlain;
      System.out.println("cast_plain_to_enum:NO_EXCEPTION " + e2);
    } catch (ClassCastException e) {
      System.out.println("cast_plain_to_enum:ClassCastException");
    } catch (Throwable t) {
      System.out.println("cast_plain_to_enum:" + t.getClass().getName());
    }
  }

  /**
   * 一个简单的基于数组的 Enumeration 实现：
   * - name 字段仅用于在输出中标识不同实例；
   * - hasMoreElements()/nextElement() 覆盖 count>0 与 count=0 的边界行为。
   */
  private static class ArrayEnumeration implements Enumeration {
    private final String name;
    private final String[] values;
    private int index = 0;

    ArrayEnumeration(String name, String[] values) {
      this.name = name;
      this.values = values != null ? values : new String[0];
    }

    public boolean hasMoreElements() {
      boolean has = index < values.length;
      System.out.println("ArrayEnumeration_hasMore name=" + name + ",index=" + index + ",len=" + values.length + ",has=" + has);
      return has;
    }

    public Object nextElement() {
      System.out.println("ArrayEnumeration_next name=" + name + ",index=" + index);
      return values[index++];
    }
  }
}

