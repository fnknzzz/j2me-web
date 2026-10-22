package com.com.example;

import java.security.Permission;
import java.security.PermissionCollection;

/**
 * 针对 jsjvm 中 java.security.PermissionCollection 系统类实现的测试。
 *
 * 覆盖点：
 * - 构造方法：PermissionCollection()
 * - 实例方法：add(Permission)/implies(Permission)/elements()
 * - 实例方法：isReadOnly()/setReadOnly()/toString()
 */
public class PermissionCollectionSystemClassSuite {

  private static class TestPermissionCollection extends PermissionCollection {
    public void add(Permission permission) {
    }

    public boolean implies(Permission permission) {
      return false;
    }

    public java.util.Enumeration elements() {
      return null;
    }
  }

  private static class TestPermission extends Permission {
    public TestPermission(String name) {
      super(name);
    }

    public boolean implies(Permission permission) {
      return false;
    }

    public boolean equals(Object obj) {
      return this == obj;
    }

    public int hashCode() {
      return 0;
    }

    public String getActions() {
      return "";
    }
  }

  public static void main(String[] args) {
    System.out.println("=== constructor ===");
    testConstructor();

    System.out.println("=== readOnly ===");
    testReadOnly();

    System.out.println("=== add_implies_elements ===");
    testAddImpliesElements();

    System.out.println("=== toString ===");
    testToString();
  }

  private static void print(String label, Object value) {
    System.out.println(label + " = " + String.valueOf(value));
  }

  private static void print(String label, boolean value) {
    System.out.println(label + " = " + value);
  }

  private static void testConstructor() {
    TestPermissionCollection pc = new TestPermissionCollection();
    print("ctor/not_null", pc != null);
    print("ctor/class_name", pc.getClass().getName());
  }

  private static void testReadOnly() {
    TestPermissionCollection pc = new TestPermissionCollection();
    print("readOnly/initial", pc.isReadOnly());

    pc.setReadOnly();
    print("readOnly/after_setReadOnly", pc.isReadOnly());

    TestPermissionCollection pc2 = new TestPermissionCollection();
    print("readOnly/new_instance", pc2.isReadOnly());
  }

  private static void testAddImpliesElements() {
    TestPermissionCollection pc = new TestPermissionCollection();
    TestPermission perm = new TestPermission("test.perm");

    pc.add(perm);
    print("add/no_exception", true);

    print("implies/false", pc.implies(perm));
    print("implies/null", pc.implies(null));

    java.util.Enumeration e = pc.elements();
    print("elements/is_null", e == null);
  }

  private static void testToString() {
    TestPermissionCollectionWithToString pc = new TestPermissionCollectionWithToString();
    String s = pc.toString();
    print("toString/not_null", s != null);
    print("toString/not_empty", s.length() > 0);
  }

  private static class TestPermissionCollectionWithToString extends PermissionCollection {
    public void add(Permission permission) {
    }

    public boolean implies(Permission permission) {
      return false;
    }

    public java.util.Enumeration elements() {
      return null;
    }

    public String toString() {
      return "TestPermissionCollectionWithToString";
    }
  }
}
