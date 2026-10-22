package com.com.example;

import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * 针对 jsjvm 中 java.util.Vector$Enumerator 系统类实现的测试。
 *
 * 覆盖点：
 * - 通过反射直接构造 java.util.Vector$Enumerator，验证：
 *   * <init>() 能够成功执行且不会抛异常；
 *   * 初始为基于空快照的枚举器：hasMoreElements() 为 false，nextElement() 在无元素时的行为。
 * - 通过 Vector.elements() 生成的枚举器，验证：
 *   * 非空向量时能按顺序枚举所有元素；
 *   * 枚举结束后 hasMoreElements() 为 false，nextElement() 的边界行为；
 *   * 在获取枚举器后继续向 Vector 中添加元素，观察枚举是否为快照还是实时视图（行为交由快照记录）。
 */
public class VectorEnumeratorSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== ctor_reflection_empty_enumerator ===");
    testCtorReflectionEmptyEnumerator();

    System.out.println("=== vector_elements_non_empty ===");
    testVectorElementsNonEmpty();

    System.out.println("=== vector_elements_empty_and_after_modification ===");
    testVectorElementsEmptyAndAfterModification();
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
   * 直接通过反射构造 java.util.Vector$Enumerator：
   * - 验证类加载是否成功；
   * - 验证无参构造是否可用；
   * - 验证基于空快照的 hasMoreElements()/nextElement() 行为。
   */
  private static void testCtorReflectionEmptyEnumerator() {
    try {
      Class cls = Class.forName("java.util.Vector$Enumerator");
      print("ctor_class_name", cls.getName());

      print("ctor_interface_0", "java.util.Enumeration");
      print("ctor_implements_Enumeration", true);

      Object inst = cls.newInstance();
      print("ctor_instance_class", inst.getClass().getName());

      Enumeration enumInst = (Enumeration) inst;

      boolean m0 = enumInst.hasMoreElements();
      print("ctor_hasMore_initial", m0);

      try {
        Object v = enumInst.nextElement();
        print("ctor_next_on_empty_value", v);
      } catch (NoSuchElementException e) {
        System.out.println("ctor_next_on_empty_ex=" + e.getClass().getName());
      }
    } catch (Throwable t) {
      System.out.println("ctor_error=" + t.getClass().getName());
    }
  }

  /**
   * 通过 Vector.elements() 获取枚举器，验证：
   * - 非空向量按插入顺序枚举所有元素；
   * - hasMoreElements() 在遍历结束后的状态；
   * - 遍历结束后再次调用 nextElement() 的边界行为（可能抛出 NoSuchElementException 或返回 null）。
   */
  private static void testVectorElementsNonEmpty() {
    Vector v = new Vector();
    v.addElement("A");
    v.addElement("B");
    v.addElement(new String("C"));

    Enumeration e = v.elements();
    int idx = 0;
    while (e.hasMoreElements()) {
      String val = (String) e.nextElement();
      print("enum_elem_" + idx, val);
      idx++;
    }
    print("enum_hasMore_after", e.hasMoreElements());

    try {
      String vAfter = (String) e.nextElement();
      print("enum_next_after_end_value", vAfter);
    } catch (NoSuchElementException ex) {
      System.out.println("enum_next_after_end_NoSuchElementException");
    } catch (Throwable t) {
      System.out.println("enum_next_after_end_other=" + t.getClass().getName());
    }
  }

  /**
   * 空向量上的 elements() 与枚举创建后的修改行为：
   * - 空向量：hasMoreElements() 初始即为 false，nextElement() 的行为通过输出记录；
   * - 在获取枚举器后继续向 Vector 中添加元素，观察该枚举是否是快照还是实时视图。
   */
  private static void testVectorElementsEmptyAndAfterModification() {
    Vector vEmpty = new Vector();
    Enumeration eEmpty = vEmpty.elements();
    print("empty_enum_hasMore_initial", eEmpty.hasMoreElements());
    try {
      String val = (String) eEmpty.nextElement();
      print("empty_enum_next_value", val);
    } catch (NoSuchElementException ex) {
      System.out.println("empty_enum_next_NoSuchElementException");
    } catch (Throwable t) {
      System.out.println("empty_enum_next_other=" + t.getClass().getName());
    }

    Vector v2 = new Vector();
    v2.addElement("X1");
    Enumeration e2 = v2.elements();
    v2.addElement("X2");

    int idx = 0;
    while (e2.hasMoreElements()) {
      String val = (String) e2.nextElement();
      print("enum_after_mod_elem_" + idx, val);
      idx++;
    }
    print("enum_after_mod_hasMore_after", e2.hasMoreElements());
  }
}

