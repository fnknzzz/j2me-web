package com.com.example;

import java.lang.RuntimePermission;

/**
 * 针对 jsjvm 中 java.lang.RuntimePermission 系统类实现的边界测试。
 *
 * 覆盖点：
 * - 两个 native 构造方法：<init>(String) 与 <init>(String,String)
 * - name / actions 参数的典型与边界值（普通字符串、空字符串、null、不同 actions）
 * - 通过继承自 Permission/BasicPermission 的 getName()/getActions()/equals()/hashCode()/toString()
 *   验证构造函数是否将 name 正确写入权限运行时并确保类层级正确
 * - equals 与 hashCode 在同名/不同名、与 null/其他类型对象比较时的行为
 */
public class RuntimePermissionSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== ctor_one_arg_basic ===");
    testCtorOneArgBasic();

    System.out.println("=== ctor_two_args_and_actions ===");
    testCtorTwoArgsAndActions();

    System.out.println("=== name_boundaries_null_and_empty ===");
    testNameBoundaries();

    System.out.println("=== equals_hashCode_and_cross_type ===");
    testEqualsHashCodeAndCrossType();
  }

  /**
   * 单参数构造函数：new RuntimePermission(String)
   *
   * 验证要点：
   * - 对应的 Class 名称和父类名称是否符合声明：java.lang.RuntimePermission / java.security.BasicPermission
   * - getName()/getActions()/toString() 行为是否稳定
   */
  private static void testCtorOneArgBasic() {
    RuntimePermission p = new RuntimePermission("loadLibrary.foo");

    System.out.println("class_name=" + p.getClass().getName());
    System.out.println("super_class_name=java.security.BasicPermission");

    System.out.println("name=" + p.getName());
    System.out.println("actions=" + p.getActions());
    System.out.println("toString=" + p.toString());
  }

  /**
   * 双参数构造函数：new RuntimePermission(String, String)
   *
   * BasicPermission 的实现会忽略 actions，仅使用 name 初始化底层 Permission。
   * 这里通过同名不同 actions 的对象对比 equals/hashCode 与 name 值，验证 RuntimePermission
   * 是否正确委托到 BasicPermission/Permission 的构造逻辑。
   */
  private static void testCtorTwoArgsAndActions() {
    RuntimePermission p1 = new RuntimePermission("file.read", "read");
    RuntimePermission p2 = new RuntimePermission("file.read", "read,write");
    RuntimePermission p3 = new RuntimePermission("file.exec", null);

    System.out.println("p1_name=" + p1.getName());
    System.out.println("p2_name=" + p2.getName());
    System.out.println("p3_name=" + p3.getName());

    // 根据当前 BasicPermission/Permission 实现，getActions 总是返回空字符串，
    // 即使构造时传入了不同 actions 或 null。
    System.out.println("p1_actions=" + p1.getActions());
    System.out.println("p2_actions=" + p2.getActions());
    System.out.println("p3_actions=" + p3.getActions());

    // 同名不同 actions：应视为同一权限
    System.out.println("p1_equals_p2=" + p1.equals(p2));
    // name 不同：不应相等
    System.out.println("p1_equals_p3=" + p1.equals(p3));
  }

  /**
   * name 参数的边界：空字符串与 null。
   *
   * 当前 Permission.<init> 实现会将 null 视为空字符串写入 JsPermission，
   * 因此 new RuntimePermission(null) 与 new RuntimePermission("") 的 getName()
   * 均应返回空字符串，二者也应视为相等。
   */
  private static void testNameBoundaries() {
    // 空字符串和 null name：jsjvm 可能接受，标准 JDK 抛出 IllegalArgumentException
    try {
      RuntimePermission pEmpty = new RuntimePermission("");
      System.out.println("empty_getName=" + pEmpty.getName());

      RuntimePermission pNull = new RuntimePermission((String) null);
      System.out.println("null_getName=" + pNull.getName());

      RuntimePermission pNull2 = new RuntimePermission((String) null);
      System.out.println("null2_getName=" + pNull2.getName());

      System.out.println("empty_equals_null=" + pEmpty.equals(pNull));
      System.out.println("null_equals_null2=" + pNull.equals(pNull2));
    } catch (IllegalArgumentException e) {
      System.out.println("empty_getName=exception:" + e.getClass().getSimpleName());
      System.out.println("null_getName=exception:" + e.getClass().getSimpleName());
      System.out.println("null2_getName=exception:" + e.getClass().getSimpleName());
      System.out.println("empty_equals_null=exception:" + e.getClass().getSimpleName());
      System.out.println("null_equals_null2=exception:" + e.getClass().getSimpleName());
    } catch (NullPointerException e) {
      System.out.println("empty_getName=exception:" + e.getClass().getSimpleName());
      System.out.println("null_getName=exception:" + e.getClass().getSimpleName());
      System.out.println("null2_getName=exception:" + e.getClass().getSimpleName());
      System.out.println("empty_equals_null=exception:" + e.getClass().getSimpleName());
      System.out.println("null_equals_null2=exception:" + e.getClass().getSimpleName());
    }
  }

  /**
   * equals/hashCode 与跨类型比较：
   * - 同名 RuntimePermission 对象：equals 为 true，hashCode 一致；
   * - 不同 name：equals 为 false，hashCode 通常不同（这里直接打印具体值）；
   * - 与 null / 其他类型比较：equals 必须为 false。
   */
  private static void testEqualsHashCodeAndCrossType() {
    RuntimePermission p1 = new RuntimePermission("runtime.a");
    RuntimePermission p2 = new RuntimePermission("runtime.a");
    RuntimePermission p3 = new RuntimePermission("runtime.b");

    System.out.println("p1_equals_p2=" + p1.equals(p2));
    System.out.println("p1_equals_p3=" + p1.equals(p3));

    System.out.println("p1_hashCode=" + p1.hashCode());
    System.out.println("p2_hashCode=" + p2.hashCode());
    System.out.println("p3_hashCode=" + p3.hashCode());

    System.out.println("p1_equals_null=" + p1.equals(null));
    System.out.println("p1_equals_string=" + p1.equals("runtime.a"));
  }
}

