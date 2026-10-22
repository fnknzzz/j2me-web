package com.com.example;

import java.util.Enumeration;
import java.util.Vector;

/**
 * 针对 jsjvm 中 java.util.Vector 系统类实现的边界与行为测试。
 *
 * 覆盖范围（对应 java-util-vector.ts 中的声明）：
 * - 构造方法：<init>(), <init>(int), <init>(int,int)
 * - 容量与大小：capacity(), size(), ensureCapacity(int), trimToSize(), setSize(int)
 * - 元素访问：addElement, elementAt, firstElement, lastElement, isEmpty
 * - 查找：contains, indexOf(obj), indexOf(obj,from), lastIndexOf(obj), lastIndexOf(obj,from)
 * - 修改与删除：insertElementAt, setElementAt, removeElement, removeElementAt, removeAllElements, copyInto
 * - 迭代：elements() 返回的 Enumeration 顺序
 * - toString()：只验证能返回一个非空字符串，具体内容交由快照比对
 */
public class VectorSystemClassSuite {

  private static String simpleName(Class c) {
    String n = c.getName();
    int i = n.lastIndexOf('.');
    if (i >= 0) {
      return n.substring(i + 1);
    }
    return n;
  }

  public static void main(String[] args) {
    System.out.println("=== constructors_and_capacity ===");
    testConstructorsAndCapacity();

    System.out.println("=== add_and_basic_access ===");
    testAddAndBasicAccess();

    System.out.println("=== contains_index_remove_copy ===");
    testContainsIndexRemoveAndCopyInto();

    System.out.println("=== insert_set_setSize_capacity ===");
    testInsertSetSetSizeAndCapacity();

    System.out.println("=== elements_enumerator ===");
    testElementsEnumerator();
  }

  /**
   * 覆盖三个构造方法及 capacity()/size()/isEmpty()/ensureCapacity()/trimToSize() 的基础行为与边界值。
   */
  private static void testConstructorsAndCapacity() {
    Vector vDefault = new Vector();
    System.out.println("default_size=" + vDefault.size());
    System.out.println("default_capacity=" + vDefault.capacity());
    System.out.println("default_isEmpty=" + vDefault.isEmpty());

    Vector vCap5 = new Vector(5);
    System.out.println("ctor_5_size=" + vCap5.size());
    System.out.println("ctor_5_capacity=" + vCap5.capacity());

    // 负容量：jsjvm 实现会将容量裁剪到 >=0，标准 JDK 抛出 IllegalArgumentException
    try {
      Vector vCapNeg = new Vector(-1);
      System.out.println("ctor_neg_size=" + vCapNeg.size());
      System.out.println("ctor_neg_capacity=" + vCapNeg.capacity());
    } catch (IllegalArgumentException e) {
      System.out.println("ctor_neg_size=exception:" + simpleName(e.getClass()));
      System.out.println("ctor_neg_capacity=exception:" + simpleName(e.getClass()));
    }

    Vector vInc = new Vector(3, 2);
    System.out.println("ctor_3_2_size=" + vInc.size());
    System.out.println("ctor_3_2_capacity=" + vInc.capacity());

    // ensureCapacity 扩容与不收缩行为
    int beforeEnsure = vInc.capacity();
    vInc.ensureCapacity(beforeEnsure + 10);
    int afterEnsure = vInc.capacity();
    System.out.println("ensure_capacity_before=" + beforeEnsure);
    System.out.println("ensure_capacity_after=" + afterEnsure);

    vInc.ensureCapacity(1); // 小于当前容量，不应收缩
    System.out.println("ensure_capacity_after_small_request=" + vInc.capacity());

    // trimToSize 是 no-op，实现中不会真正收缩容量，这里记录实际行为。
    int beforeTrim = vInc.capacity();
    vInc.trimToSize();
    int afterTrim = vInc.capacity();
    System.out.println("trimToSize_before=" + beforeTrim);
    System.out.println("trimToSize_after=" + afterTrim);
  }

  /**
   * 覆盖 addElement / elementAt / firstElement / lastElement / isEmpty / toString。
   * 对越界 elementAt 进行探测，观察 jsjvm 的实际返回值（通常期望为 null）。
   */
  private static void testAddAndBasicAccess() {
    Vector v = new Vector();
    System.out.println("add_basic_isEmpty_initial=" + v.isEmpty());
    System.out.println("add_basic_size_initial=" + v.size());

    v.addElement("A");
    v.addElement("B");
    v.addElement("C");

    System.out.println("add_basic_isEmpty_after=" + v.isEmpty());
    System.out.println("add_basic_size_after=" + v.size());
    System.out.println("add_basic_firstElement=" + v.firstElement());
    System.out.println("add_basic_lastElement=" + v.lastElement());
    System.out.println("add_basic_elementAt_0=" + v.elementAt(0));
    System.out.println("add_basic_elementAt_2=" + v.elementAt(2));

    // 越界访问：期待实现返回 null，而不是抛异常；如果有异常则记录其类型。
    try {
      Object out = v.elementAt(10);
      System.out.println("add_basic_elementAt_10=" + String.valueOf(out));
    } catch (Throwable t) {
      System.out.println("add_basic_elementAt_10_ex=" + t.getClass().getName());
    }

    String ts = v.toString();
    System.out.println("add_basic_toString_isNull=" + (ts == null));
    System.out.println("add_basic_toString_len=" + (ts == null ? -1 : ts.length()));
  }

  /**
   * 覆盖 contains / indexOf / lastIndexOf / removeElement / removeElementAt / removeAllElements / copyInto。
   * 同时利用自定义 Key 类型验证 equals(Object) 的使用路径。
   */
  private static void testContainsIndexRemoveAndCopyInto() {
    Vector v = new Vector();
    v.addElement("X");
    v.addElement("Y");
    v.addElement("X");
    v.addElement("Z");

    System.out.println("contains_X=" + v.contains("X"));
    System.out.println("contains_W=" + v.contains("W"));

    System.out.println("indexOf_X=" + v.indexOf("X"));
    System.out.println("indexOf_X_from1=" + v.indexOf("X", 1));
    System.out.println("indexOf_W=" + v.indexOf("W"));

    System.out.println("lastIndexOf_X=" + v.lastIndexOf("X"));
    System.out.println("lastIndexOf_X_from2=" + v.lastIndexOf("X", 2));
    System.out.println("lastIndexOf_W=" + v.lastIndexOf("W"));

    // 自定义 Key，覆盖 equalsObject 中通过 equals 方法比较对象的分支。
    class Key {
      final int id;
      Key(int id) { this.id = id; }
      
      public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Key)) return false;
        return this.id == ((Key) o).id;
      }
      
      public String toString() {
        return "Key(" + id + ")";
      }
    }

    Vector keys = new Vector();
    keys.addElement(new Key(1));
    keys.addElement(new Key(2));
    System.out.println("contains_Key1_by_equal=" + keys.contains(new Key(1)));
    System.out.println("contains_Key3_by_equal=" + keys.contains(new Key(3)));

    // removeElement / removeElementAt / removeAllElements
    Vector vRem = new Vector();
    vRem.addElement("A");
    vRem.addElement("B");
    vRem.addElement("A");
    System.out.println("remove_vector_initial=" + vRem);
    System.out.println("removeElement_A_first=" + vRem.removeElement("A"));
    System.out.println("remove_vector_after_first=" + vRem);
    System.out.println("removeElement_W=" + vRem.removeElement("W"));

    try {
      vRem.removeElementAt(10); // 越界删除，观察是否抛异常或静默忽略
      System.out.println("removeElementAt_10_done_size=" + vRem.size());
    } catch (Throwable t) {
      System.out.println("removeElementAt_10_ex=" + t.getClass().getName());
    }

    vRem.removeAllElements();
    System.out.println("removeAllElements_size=" + vRem.size());
    System.out.println("removeAllElements_isEmpty=" + vRem.isEmpty());

    // copyInto 覆盖：向较大数组拷贝元素
    Vector vCopy = new Vector();
    vCopy.addElement("C1");
    vCopy.addElement("C2");
    Object[] dst = new Object[5];
    for (int i = 0; i < dst.length; i++) {
      dst[i] = "init";
    }
    System.out.println("copyInto_before=" + arrayToString(dst));
    vCopy.copyInto(dst);
    System.out.println("copyInto_after=" + arrayToString(dst));
  }

  private static String arrayToString(Object[] arr) {
    if (arr == null) return "null";
    StringBuffer sb = new StringBuffer();
    sb.append("[");
    for (int i = 0; i < arr.length; i++) {
      if (i > 0) sb.append(", ");
      sb.append(arr[i]);
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * 覆盖 insertElementAt / setElementAt / setSize / capacity 边界行为：
   * - 插入负索引、过大索引
   * - setElementAt 越界
   * - setSize 扩展与收缩、以及负 size
   */
  private static void testInsertSetSetSizeAndCapacity() {
    Vector v = new Vector();
    v.addElement("A");
    v.addElement("C");
    System.out.println("insert_initial=" + v + ",cap=" + v.capacity());

    // 正常插入到中间
    v.insertElementAt("B", 1);
    System.out.println("insert_middle=" + v + ",size=" + v.size());

    // 负索引：jsjvm 实现会 Math.max(0,index)，标准 JDK 抛出 ArrayIndexOutOfBoundsException
    try {
      v.insertElementAt("HEAD", -5);
      System.out.println("insert_neg_index=" + v);
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("insert_neg_index=exception:" + simpleName(e.getClass()));
    }

    // 过大索引：jsjvm 可能追加或补空位，标准 JDK 抛出 ArrayIndexOutOfBoundsException
    try {
      v.insertElementAt("TAIL", 100);
      System.out.println("insert_large_index=" + v + ",size=" + v.size());
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("insert_large_index=exception:" + simpleName(e.getClass()));
    }

    // setElementAt 正常索引
    v.setElementAt("X", 1);
    System.out.println("setElementAt_1=" + v);

    // setElementAt 越界
    try {
      v.setElementAt("Y", 100);
      System.out.println("setElementAt_100_done_size=" + v.size());
    } catch (Throwable t) {
      System.out.println("setElementAt_100_ex=" + t.getClass().getName());
    }

    // setSize 扩展与收缩
    Vector vs = new Vector();
    vs.addElement("1");
    vs.addElement("2");
    vs.addElement("3");
    System.out.println("setSize_initial=" + vs + ",size=" + vs.size());

    vs.setSize(5);
    System.out.println("setSize_5=" + vs + ",size=" + vs.size());

    vs.setSize(1);
    System.out.println("setSize_1=" + vs + ",size=" + vs.size());

    // 负 size：jsjvm 会被裁剪到 0，标准 JDK 抛出 ArrayIndexOutOfBoundsException
    try {
      vs.setSize(-1);
      System.out.println("setSize_neg=" + vs + ",size=" + vs.size());
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("setSize_neg=exception:" + simpleName(e.getClass()));
    }

    // 再次确认容量接口
    int capBefore = vs.capacity();
    vs.ensureCapacity(capBefore + 1);
    int capAfter = vs.capacity();
    System.out.println("capacity_before2=" + capBefore + ",after2=" + capAfter);
  }

  /**
   * 覆盖 elements() 返回的 Enumeration：
   * - 非空向量的正向遍历顺序
   * - 空向量的 hasMoreElements() 行为
   */
  private static void testElementsEnumerator() {
    Vector v = new Vector();
    v.addElement("e1");
    v.addElement("e2");
    v.addElement("e3");

    Enumeration en = v.elements();
    int idx = 0;
    while (en.hasMoreElements()) {
      String s = (String) en.nextElement();
      System.out.println("elements_nonempty_" + idx + "=" + s);
      idx++;
    }
    System.out.println("elements_nonempty_hasMore_after=" + en.hasMoreElements());

    Vector empty = new Vector();
    Enumeration enEmpty = empty.elements();
    System.out.println("elements_empty_hasMore=" + enEmpty.hasMoreElements());
    try {
      String v0 = (String) enEmpty.nextElement();
      System.out.println("elements_empty_next=" + v0);
    } catch (Throwable t) {
      System.out.println("elements_empty_next_ex=" + t.getClass().getName());
    }
  }
}
