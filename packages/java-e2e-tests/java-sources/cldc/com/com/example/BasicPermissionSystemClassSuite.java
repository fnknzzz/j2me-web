package com.com.example;

import java.security.BasicPermission;
import java.security.Permission;
import java.security.PermissionCollection;

/**
 * 针对 jsjvm 中 java.security.BasicPermission 系统类实现的测试。
 *
 * 覆盖点：
 * - 构造方法：BasicPermission(String) 与 BasicPermission(String, String) 通过子类调用，
 *   验证其委托到 java.security.Permission.<init>(String) 的逻辑（name 正确写入 PD）。
 * - getActions()：BasicPermission 始终返回空字符串 ""。
 * - equals(Object)：同类同名相等，不同名/不同类/空引用均不相等。
 * - hashCode()：同类同名 hash 相同，名称不同 hash 不同（不要求具体数值，只要求稳定）。
 * - implies(Permission)：
 *   * 对 BasicPermission 子类实例，当前实现因 className != "java.security.BasicPermission" 而返回 false。
 *   * 对 null 和非 BasicPermission 类型，也应返回 false。
 * - newPermissionCollection()：返回非 null 的 PermissionCollection 实例，其类名为
 *   "java.security.PermissionCollection"（或该系统类的具体实现）。
 */
public class BasicPermissionSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== ctor_and_getActions ===");
    testConstructorsAndGetActions();

    System.out.println("=== equals_and_hashCode ===");
    testEqualsAndHashCode();

    System.out.println("=== implies_behavior ===");
    testImpliesBehavior();

    System.out.println("=== newPermissionCollection ===");
    testNewPermissionCollection();
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
   * 覆盖 BasicPermission 的两个构造方法以及 getActions()：
   * - name 通过 Permission.<init>(String) 正常存储，可由 getName() 读出。
   * - actions 参数在 BasicPermissionDecl 中被忽略，getActions() 始终返回空串。
   */
  private static void testConstructorsAndGetActions() {
    MyBasicPermission p1 = new MyBasicPermission("read");
    MyBasicPermission p2 = new MyBasicPermission("read", "read,write");
    MyBasicPermission p3 = new MyBasicPermission("write");

    print("ctor_p1_name", p1.getName());
    print("ctor_p2_name", p2.getName());
    print("ctor_p3_name", p3.getName());

    // TS 实现中 getActions() 始终返回空字符串
    print("ctor_p1_actions", p1.getActions());
    print("ctor_p2_actions", p2.getActions());
    print("ctor_p3_actions", p3.getActions());
  }

  /**
   * 覆盖 equals/hashCode：
   * - 同一子类、同名 -> equals 为 true，hashCode 相同。
   * - 同一子类、不同名 -> equals 为 false，hashCode 通常不同（但只要求行为稳定）。
   * - 不同子类、同名 -> equals 为 false（实现中先比较类名）。
   * - 与 null/非 Permission 类型比较 -> equals 为 false。
   */
  private static void testEqualsAndHashCode() {
    MyBasicPermission a1 = new MyBasicPermission("read");
    MyBasicPermission a2 = new MyBasicPermission("read");
    MyBasicPermission b = new MyBasicPermission("write");
    OtherBasicPermission otherSubclass = new OtherBasicPermission("read");

    print("eq_a1_a2", a1.equals(a2));
    print("eq_a1_b", a1.equals(b));
    print("eq_a1_otherSubclass", a1.equals(otherSubclass));
    print("eq_a1_null", a1.equals(null));
    print("eq_a1_string", a1.equals("read"));

    int a1Hash = a1.hashCode();
    int a2Hash = a2.hashCode();
    int bHash = b.hashCode();
    print("hash_a1", a1Hash);
    print("hash_a2", a2Hash);
    print("hash_b", bHash);
    print("hash_a1_eq_a2", a1Hash == a2Hash);
    print("hash_a1_eq_b", a1Hash == bHash);
  }

  /**
   * 覆盖 implies(Permission)：
   * - self/perm 均为 BasicPermission 子类时，由于实现检查 perm 的 className 必须为
   *   "java.security.BasicPermission"，因此这里应始终返回 false。
   * - perm 为 null 或其它非 BasicPermission 类型，也应返回 false。
   */
  private static void testImpliesBehavior() {
    MyBasicPermission r1 = new MyBasicPermission("read");
    MyBasicPermission r2 = new MyBasicPermission("read");
    MyBasicPermission w = new MyBasicPermission("write");
    DummyPermission dummy = new DummyPermission("read");

    print("implies_r1_r2", r1.implies(r2));
    print("implies_r1_w", r1.implies(w));
    print("implies_r1_null", r1.implies((Permission) null));
    print("implies_r1_dummy", r1.implies(dummy));
  }

  /**
   * 覆盖 newPermissionCollection()：应返回非 null 的 PermissionCollection 实例，
   * 其运行时类型名称应为 java.security.PermissionCollection（或该系统类的实现）。
   */
  private static void testNewPermissionCollection() {
    MyBasicPermission p = new MyBasicPermission("read");
    PermissionCollection c = p.newPermissionCollection();
    print("npc_not_null", c != null);
    if (c != null) {
      print("npc_class_name", c.getClass().getName());
    }
  }

  /**
   * 测试用 BasicPermission 子类，直接调用父类构造：
   * - super(name)
   * - super(name, actions)
   */
  private static class MyBasicPermission extends BasicPermission {
    public MyBasicPermission(String name) {
      super(name);
    }

    public MyBasicPermission(String name, String actions) {
      super(name, actions);
    }
  }

  /**
   * 另一个 BasicPermission 子类，用于验证 equals 先比较类名的行为。
   */
  private static class OtherBasicPermission extends BasicPermission {
    public OtherBasicPermission(String name) {
      super(name);
    }
  }

  /**
   * 非 BasicPermission 类型的 Permission 实现，用于 implies() 的负向测试。
   */
  private static class DummyPermission extends Permission {

    public DummyPermission(String name) {
      super(name);
    }

    
    public boolean implies(Permission permission) {
      return false;
    }

    
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof DummyPermission)) return false;
      DummyPermission other = (DummyPermission) obj;
      return getName().equals(other.getName());
    }

    
    public int hashCode() {
      return getName().hashCode();
    }

    
    public String getActions() {
      return "";
    }
  }
}

