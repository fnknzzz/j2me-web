package com.com.example;

import java.util.EmptyStackException;
import java.util.Stack;

/**
 * 针对 jsjvm 中 java.util.Stack 系统类实现的测试。
 *
 * 覆盖点：
 * - 构造方法：Stack() 初始化底层 JsStack，并可调用 empty()/push()/pop()/peek()/search()
 * - empty()：在空栈与非空栈上的返回值
 * - push()：返回被压入的对象，支持多次压栈
 * - peek()/pop()：
 *   * 非空栈：返回栈顶元素，并保持/修改栈内状态
 *   * 空栈：当前实现返回 null 或抛出 EmptyStackException，统一通过输出记录
 * - search(Object)：
 *   * 对 String 使用内容相等比较（equalsObject 的字符串分支）
 *   * 对自定义类型使用 equals(Object) 分支
 *   * 在空栈与不存在元素上的 -1 行为
 */
public class StackSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== constructors_and_empty_behavior ===");
    testConstructorsAndEmptyBehavior();

    System.out.println("=== push_peek_pop ===");
    testPushPeekPop();

    System.out.println("=== search_with_strings ===");
    testSearchWithStrings();

    System.out.println("=== search_with_custom_keys ===");
    testSearchWithCustomKeys();
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
   * 覆盖：
   * - new Stack() 后 empty() 的行为
   * - 在空栈上调用 peek()/pop() 的边界：记录是返回 null 还是抛 EmptyStackException
   * - 对空栈执行 search() 应返回 -1
   */
  private static void testConstructorsAndEmptyBehavior() {
    Stack s = new Stack();

    print("ctor_class", s.getClass().getName());
    print("empty_initial", s.empty());

    // 空栈 peek()
    try {
      Object v = s.peek();
      print("peek_empty_value", v);
    } catch (EmptyStackException e) {
      System.out.println("peek_empty_EmptyStackException");
    } catch (Throwable t) {
      System.out.println("peek_empty_other=" + t.getClass().getName());
    }

    // 空栈 pop()
    try {
      Object v = s.pop();
      print("pop_empty_value", v);
    } catch (EmptyStackException e) {
      System.out.println("pop_empty_EmptyStackException");
    } catch (Throwable t) {
      System.out.println("pop_empty_other=" + t.getClass().getName());
    }

    // 空栈 search()
    print("search_empty", s.search("X"));
  }

  /**
   * 覆盖 push/peek/pop 在非空栈上的行为：
   * - push 返回被压入的元素
   * - 多次 push 后 empty() 为 false
   * - peek 查看栈顶但不删除
   * - 多次 pop 依次弹出栈顶
   */
  private static void testPushPeekPop() {
    Stack s = new Stack();

    String r1 = (String) s.push("A");
    String r2 = (String) s.push("B");
    String r3 = (String) s.push(new String("C"));
    print("push_r1", r1);
    print("push_r2", r2);
    print("push_r3", r3);

    print("empty_after_push", s.empty());

    String top = (String) s.peek();
    print("peek_after_push", top);

    String p1 = (String) s.pop();
    String p2 = (String) s.pop();
    String p3 = (String) s.pop();
    print("pop_first", p1);
    print("pop_second", p2);
    print("pop_third", p3);

    print("empty_after_pops", s.empty());
  }

  /**
   * 覆盖 search(Object) 针对 String 的行为：
   * - 栈内容从底到顶依次为 A, B, new String("B"), C
   * - search(C) -> 1
   * - search("B") 命中顶部的 B（内容相等），位置为 2
   * - search("A") -> 4
   * - search(不存在) -> -1
   */
  private static void testSearchWithStrings() {
    Stack s = new Stack();
    s.push("A");
    s.push("B");
    s.push(new String("B"));
    s.push("C");

    print("search_C", s.search("C"));
    print("search_B", s.search("B"));
    print("search_A", s.search("A"));
    print("search_missing", s.search("X"));
  }

  /**
   * 覆盖 search(Object) 针对自定义类型的 equals 分支：
   * - 栈内容从底到顶为 Key("k1"), Key("k2")
   * - search(new Key("k1")) 应通过 equals 找到底部元素，返回 2
   * - search(new Key("k2")) 返回 1
   */
  private static void testSearchWithCustomKeys() {
    Stack s = new Stack();
    Key k1 = new Key("k1");
    Key k2 = new Key("k2");

    s.push(k1);
    s.push(k2);

    Key k1b = new Key("k1");
    print("search_k1b", s.search(k1b));
    print("search_k1_ref", s.search(k1));
    print("search_k2_new", s.search(new Key("k2")));
  }

  /** 自定义 Key 类型，基于 id 实现 equals/hashCode。 */
  private static class Key {
    private final String id;

    Key(String id) {
      this.id = id;
    }

    
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Key)) return false;
      Key other = (Key) obj;
      return this.id.equals(other.id);
    }

    
    public int hashCode() {
      return id.hashCode();
    }

    
    public String toString() {
      return "Key(" + id + ")";
    }
  }
}

