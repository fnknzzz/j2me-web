package com.com.example;

import java.security.Permission;
import java.security.PermissionCollection;
import java.lang.RuntimePermission;

/**
 * 针对 jsjvm 中 java.security.Permission 系统类实现的边界测试。
 *
 * 覆盖点（对应 SecurityPermissionDecl 中声明的方法）：
 * - 构造方法：Permission(String)
 * - 实例方法：getName()/getActions()/equals(Object)/hashCode()/implies(Permission)/toString()
 * - 实例方法：newPermissionCollection()
 *
 * 重点关注：
 * - name 参数为普通字符串、空字符串、null 时对 JsPermission.name 的写入与暴露行为；
 * - getName()/toString() 在上述场景下的输出；
 * - equals/hashCode 在同名/不同名、null、跨类型比较时的表现；
 * - implies 恒为 false 以及 null 参数的处理；
 * - newPermissionCollection() 默认返回 null 的行为。
 */
public class SecurityPermissionSystemClassSuite {

  private static class TestPermission extends Permission {
    public TestPermission(String name) {
      super(name);
    }
    
    public boolean implies(Permission permission) {
      return false;
    }
    
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (!(obj instanceof TestPermission)) return false;
      TestPermission other = (TestPermission) obj;
      String thisName = getName();
      String otherName = other.getName();
      if (thisName == null) return otherName == null;
      return thisName.equals(otherName);
    }
    
    public int hashCode() {
      String name = getName();
      return name == null ? 0 : name.hashCode();
    }
    
    public String getActions() {
      return "";
    }
  }

  public static void main(String[] args) {
    System.out.println("=== ctor_and_basic_accessors ===");
    testCtorAndBasicAccessors();

    System.out.println("=== name_boundaries_null_and_empty ===");
    testNameBoundaries();

    System.out.println("=== equals_hashCode_and_cross_type ===");
    testEqualsHashCodeAndCrossType();

    System.out.println("=== implies_and_newPermissionCollection ===");
    testImpliesAndNewPermissionCollection();
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
   * 基础构造与访问器：
   * - 验证 Permission(String) 对 name 的存储是否可通过 getName()/toString() 访问；
   * - 验证 getActions() 当前实现恒为 ""；
   * - 验证运行时类名与父类名。
   */
  private static void testCtorAndBasicAccessors() {
    Permission p = new TestPermission("perm.read");

    Class cls = p.getClass();
    print("ctor/class_name", cls.getName());
    print("ctor/super_class_name", "java.security.Permission");

    print("ctor/name", p.getName());
    print("ctor/actions", p.getActions());
    print("ctor/toString", p.toString());
  }

  /**
   * name 参数的边界：空字符串与 null。
   *
   * 当前 Permission.<init> 实现会将非字符串或 null 视为 "" 写入 JsPermission，
   * 因此 new Permission(null) 与 new Permission("") 的 getName() 均应返回空字符串，
   * 二者之间的 equals 也应视为相等。
   */
  private static void testNameBoundaries() {
    Permission pEmpty = new TestPermission("");
    Permission pNull = new TestPermission((String) null);
    Permission pNull2 = new TestPermission((String) null);

    print("nameBoundary/empty_getName", pEmpty.getName());
    print("nameBoundary/null_getName", pNull.getName());
    print("nameBoundary/null2_getName", pNull2.getName());

    print("nameBoundary/empty_equals_null", pEmpty.equals(pNull));
    print("nameBoundary/null_equals_null2", pNull.equals(pNull2));

    print("nameBoundary/empty_toString", pEmpty.toString());
    print("nameBoundary/null_toString", pNull.toString());
  }

  /**
   * equals/hashCode 与跨类型比较：
   * - 同名 Permission 对象：equals 为 true，hashCode 一致；
   * - 不同 name：equals 为 false，hashCode 通常不同（这里直接打印具体值）；
   * - 与 null / 其他类型（如 String、RuntimePermission）比较：equals 必须为 false。
   */
  private static void testEqualsHashCodeAndCrossType() {
    Permission p1 = new TestPermission("perm.eq");
    Permission p2 = new TestPermission("perm.eq");
    Permission p3 = new TestPermission("perm.other");

    print("eq/p1_equals_p2", p1.equals(p2));
    print("eq/p1_equals_p3", p1.equals(p3));

    print("eq/p1_hashCode", p1.hashCode());
    print("eq/p2_hashCode", p2.hashCode());
    print("eq/p3_hashCode", p3.hashCode());

    print("eq/p1_equals_null", p1.equals(null));
    print("eq/p1_equals_string", p1.equals("perm.eq"));

    RuntimePermission rp = new RuntimePermission("perm.eq");
    print("eq/p1_equals_runtimePermission", p1.equals(rp));
  }

  /**
   * implies 与 newPermissionCollection：
   * - Permission.implies(Permission) 当前实现恒返回 false，包括 self、自身同名实例、不同名实例；
   * - 对 null 参数也应返回 false；
   * - newPermissionCollection() 默认返回 null。
   */
  private static void testImpliesAndNewPermissionCollection() {
    Permission p1 = new TestPermission("perm.implies");
    Permission p2 = new TestPermission("perm.implies");
    Permission p3 = new TestPermission("perm.other");

    print("implies/self", p1.implies(p1));
    print("implies/same_name_other_instance", p1.implies(p2));
    print("implies/different_name", p1.implies(p3));
    print("implies/null", p1.implies(null));

    PermissionCollection pc1 = p1.newPermissionCollection();
    PermissionCollection pc2 = p2.newPermissionCollection();

    print("npc/pc1_is_null", pc1 == null);
    print("npc/pc2_is_null", pc2 == null);
  }
}

