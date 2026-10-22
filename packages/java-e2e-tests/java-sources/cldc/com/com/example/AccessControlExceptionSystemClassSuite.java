package com.com.example;

import java.security.AccessControlException;
import java.security.Permission;

/**
 * 针对 jsjvm 中 java.security.AccessControlException 系统类实现的测试。
 *
 * 覆盖点：
 * - 构造方法：<init>(String) 与 <init>(String, Permission)
 * - getPermission() 在不同构造路径下的行为（null / 非 null / 传入 null Permission）
 * - 通过 ThrowablePD 存储的 detailMessage 对 getMessage()/toString() 的影响
 */
public class AccessControlExceptionSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== ctor_msg_only ===");
    testConstructorMsgOnly();

    System.out.println("=== ctor_msg_and_permission ===");
    testConstructorMsgAndPermission();

    System.out.println("=== ctor_msg_and_null_permission ===");
    testConstructorMsgAndNullPermission();
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
   * 覆盖 <init>(String)：
   * - message 为非 null 与 null 的情况
   * - getPermission() 应返回 null（因为 TS 实现未为 perm 设置 PD）
   */
  private static void testConstructorMsgOnly() {
    AccessControlException e1 = new AccessControlException("denied");
    print("msg_only_e1_class", e1.getClass().getName());
    print("msg_only_e1_message", e1.getMessage());
    print("msg_only_e1_toString", e1.toString());
    print("msg_only_e1_perm_null", e1.getPermission() == null);

    AccessControlException e2 = new AccessControlException((String) null);
    print("msg_only_e2_message", e2.getMessage());
    print("msg_only_e2_toString", e2.toString());
    print("msg_only_e2_perm_null", e2.getPermission() == null);
  }

  /**
   * 用于测试 <init>(String, Permission) 时，Permission 是否通过 PD 正确存储并由 getPermission() 返回。
   */
  private static void testConstructorMsgAndPermission() {
    MyPermission perm = new MyPermission("TEST_PERM");
    AccessControlException e = new AccessControlException("no access", perm);

    print("msg_perm_class", e.getClass().getName());
    print("msg_perm_message", e.getMessage());
    print("msg_perm_toString", e.toString());

    Permission p = e.getPermission();
    print("msg_perm_getPermission_not_null", p != null);
    if (p != null) {
      print("msg_perm_getPermission_class", p.getClass().getName());
      print("msg_perm_getPermission_name", p.getName());
      print("msg_perm_same_instance", p == perm);
    }
  }

  /**
   * 覆盖第二个构造参数为 null 的情况：
   * - TS 实现中只有在 perm 为对象值时才设置 AccessControlExceptionPD.perm
   * - 传入 null 时 getPermission() 应该返回 null
   */
  private static void testConstructorMsgAndNullPermission() {
    AccessControlException e = new AccessControlException("no access", (Permission) null);
    print("msg_nullPerm_message", e.getMessage());
    print("msg_nullPerm_perm_null", e.getPermission() == null);
  }

  /**
   * 简单的 Permission 子类，用于构造可观测的 Permission 实例。
   */
  private static class MyPermission extends Permission {

    public MyPermission(String name) {
      super(name);
    }

    
    public boolean implies(Permission permission) {
      return false;
    }

    
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof MyPermission)) return false;
      MyPermission other = (MyPermission) obj;
      return this.getName().equals(other.getName());
    }

    
    public int hashCode() {
      return getName().hashCode();
    }

    
    public String getActions() {
      return "";
    }
  }
}

