package com.com.example;

import java.security.AccessController;
import java.security.Permission;

/**
 * 针对 jsjvm 中 java.security.AccessController 系统类实现的边界测试。
 *
 * 覆盖点（对应 AccessControllerDecl 中声明的静态方法）：
 * - static void checkPermission(Permission)：
 *   - 正常 Permission 对象；
 *   - null 作为参数；
 *   - 多次重复调用，验证无隐藏状态或副作用；
 *
 * 由于当前系统实现为 no-op（直接返回 void），测试重点是：
 * - 任意 Permission（含自定义子类）不会触发异常；
 * - 传入 null 也不会触发 NPE 或 SecurityException；
 * - 多次调用后程序继续执行，控制流稳定。
 */
public class AccessControllerSystemClassSuite {

  /**
   * 简单的 Permission 子类，用于作为 checkPermission 的参数。
   *
   * 这里实现为最小语义：
   * - implies 一律返回 false；
   * - equals 只按引用比较；
   * - hashCode 固定常量；
   * - getActions 返回常量字符串，避免依赖底层实现细节。
   */
  private static final class SimplePermission extends Permission {

    SimplePermission(String name) {
      super(name);
    }

    
    public boolean implies(Permission p) {
      return false;
    }

    
    public boolean equals(Object obj) {
      return this == obj;
    }

    
    public int hashCode() {
      return 1234567;
    }

    
    public String getActions() {
      return "SIMPLE";
    }
  }

  public static void main(String[] args) {
    System.out.println("=== checkPermission_basic ===");
    testCheckPermissionBasic();

    System.out.println("=== checkPermission_null_and_multiple_calls ===");
    testCheckPermissionNullAndMultipleCalls();
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
   * 基本调用场景：
   * - 使用普通的自定义 Permission 实例调用 checkPermission；
   * - 验证调用过程中不会抛出任何异常；
   * - 多次调用不同实例，确保实现不依赖 Permission 的具体类型或内容。
   */
  private static void testCheckPermissionBasic() {
    Permission p1 = new SimplePermission("perm-basic-1");
    Permission p2 = new SimplePermission("perm-basic-2");

    try {
      AccessController.checkPermission(p1);
      print("basic/p1_no_exception", Boolean.TRUE);
    } catch (Throwable t) {
      print("basic/p1_exception", t.getClass().getName());
    }

    try {
      AccessController.checkPermission(p2);
      print("basic/p2_no_exception", Boolean.TRUE);
    } catch (Throwable t) {
      print("basic/p2_exception", t.getClass().getName());
    }
  }

  /**
   * 边界与重复调用场景：
   * - 传入 null：由于系统实现直接返回 void，应当不抛异常；
   * - 大量重复调用：验证无隐藏状态/资源泄漏迹象（由快照和后续输出辅助观察）。
   */
  private static void testCheckPermissionNullAndMultipleCalls() {
    // null 参数场景
    try {
      AccessController.checkPermission(null);
      print("nullArg/no_exception", Boolean.TRUE);
    } catch (Throwable t) {
      print("nullArg/exception", t.getClass().getName());
    }

    // 多次重复调用：这里选择有限次数，兼顾覆盖与执行时间
    Permission p = new SimplePermission("perm-loop");
    int successCount = 0;
    int failCount = 0;

    for (int i = 0; i < 20; i++) {
      try {
        AccessController.checkPermission(p);
        successCount++;
      } catch (Throwable t) {
        failCount++;
        // 将首次异常类型打印出来，便于定位实现问题
        if (failCount == 1) {
          print("loop/first_exception", t.getClass().getName());
        }
      }
    }

    print("loop/successCount", successCount);
    print("loop/failCount", failCount);
  }
}
