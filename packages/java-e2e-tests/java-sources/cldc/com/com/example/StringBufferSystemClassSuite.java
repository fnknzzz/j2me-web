package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.StringBuffer 系统类实现的边界与行为测试。
 *
 * 覆盖范围（仅聚焦 StringBuffer 系统类本身）：
 * - 构造方法：<init>(), <init>(int), <init>(String)
 * - 内容与容量：toString(), length(), capacity()
 * - 追加 append(...) 的各种重载：String / Object / 基本类型 / char[] / char[] 带 offset/len
 * - 按索引访问与修改：charAt(int), setCharAt(int,char), setLength(int)
 * - 删除：deleteCharAt(int), delete(int,int)
 * - 插入 insert(...) 的各种重载
 * - 批量复制字符：getChars(int,int,char[],int)
 * - 反转与容量调整：reverse(), ensureCapacity(int)
 *
 * 所有断言通过 System.out.println 打印标签化结果，方便与快照对比，
 * 不依赖额外的断言框架，且不测试除 StringBuffer 之外的系统类逻辑。
 */
public class StringBufferSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== constructors_and_basic_properties ===");
    testConstructorsAndBasicProperties();

    System.out.println("=== append_overloads_and_nulls ===");
    testAppendOverloadsAndNulls();

    System.out.println("=== index_access_and_setters ===");
    testIndexAccessAndSetters();

    System.out.println("=== delete_variants ===");
    testDeleteVariants();

    System.out.println("=== insert_overloads ===");
    testInsertOverloads();

    System.out.println("=== getChars_and_exceptions ===");
    testGetCharsAndExceptions();

    System.out.println("=== reverse_and_ensureCapacity ===");
    testReverseAndEnsureCapacity();
  }

  /**
   * 覆盖所有构造方法以及 length()/capacity()/toString() 的基础行为与典型边界。
   */
  private static void testConstructorsAndBasicProperties() {
    // 默认构造：内容为空，长度为 0，容量至少为默认值（JDK 中为 16）。
    StringBuffer sbDefault = new StringBuffer();
    System.out.println("default_ctor_toString='" + sbDefault.toString() + "'");
    System.out.println("default_ctor_length=" + sbDefault.length());
    System.out.println("default_ctor_capacity=" + sbDefault.capacity());

    // 指定容量构造：JDK 语义为初始容量即传入值。
    // 这里记录不同容量下的 capacity 行为，方便对比 jsjvm 与标准 JDK。
    StringBuffer sbCap1 = new StringBuffer(1);
    StringBuffer sbCap32 = new StringBuffer(32);
    System.out.println("ctor_int_1_capacity=" + sbCap1.capacity());
    System.out.println("ctor_int_32_capacity=" + sbCap32.capacity());

    // String 构造：内容为给定字符串，容量一般为 length + 16。
    StringBuffer sbFromStrShort = new StringBuffer("abc");
    StringBuffer sbFromStrLong = new StringBuffer("0123456789ABCDEFGHIJ"); // 长度 >= 20
    System.out.println("ctor_str_abc_toString='" + sbFromStrShort.toString() + "'");
    System.out.println("ctor_str_abc_length=" + sbFromStrShort.length());
    System.out.println("ctor_str_abc_capacity=" + sbFromStrShort.capacity());
    System.out.println("ctor_str_long_length=" + sbFromStrLong.length());
    System.out.println("ctor_str_long_capacity=" + sbFromStrLong.capacity());

    // String 构造传入 null：JDK 期望抛出 NullPointerException。
    try {
      new StringBuffer((String) null);
      System.out.println("ctor_str_null:NO_EXCEPTION");
    } catch (NullPointerException e) {
      System.out.println("ctor_str_null:NullPointerException");
    } catch (Throwable t) {
      System.out.println("ctor_str_null:" + t.getClass().getName());
    }
  }

  /** 自定义对象，用于测试 append(Object) 与 insert(int,Object) 的 toString 调用路径。 */
  private static class CustomObject {
    private final String name;

    CustomObject(String name) {
      this.name = name;
    }

    
    public String toString() {
      return "CustomObject-" + name;
    }
  }

  /**
   * 覆盖所有 append(...) 重载，并重点验证 null、负数等边界输入。
   */
  private static void testAppendOverloadsAndNulls() {
    StringBuffer sb = new StringBuffer();

    // append(String) 与 append(String) 传入 null
    sb.append("hello");
    sb.append(",");
    sb.append("world");
    sb.append("!");
    sb.append((String) null); // JDK 语义为追加字符串 "null"
    System.out.println("append_String_result='" + sb.toString() + "'");

    // reset
    sb = new StringBuffer("base-");

    // append(Object) 正常对象与 null，验证是否经由 toString() 或显示为 "null"。
    sb.append(new CustomObject("A"));
    sb.append("|");
    sb.append((Object) null);
    System.out.println("append_Object_result='" + sb.toString() + "'");

    // append(int) / append(long) / append(boolean) / append(char) / append(double) / append(float)
    sb = new StringBuffer();
    sb.append(0);
    sb.append(-1);
    sb.append(1234567890);
    sb.append(1L);
    sb.append(Long.MIN_VALUE);
    sb.append(true);
    sb.append(false);
    sb.append('X');
    sb.append(1.5d);
    sb.append(-0.0d);
    sb.append(3.14f);
    sb.append(-2.5f);
    System.out.println("append_primitives_result='" + sb.toString() + "'");

    // append(char[]) 与 append(char[], offset, len) 的正常与边界。
    char[] chars = new char[] {'A', 'B', 'C', 'D', 'E'};
    sb = new StringBuffer("[");
    sb.append(chars);
    sb.append(']');
    System.out.println("append_charArray_full='" + sb.toString() + "'");

    sb = new StringBuffer("[");
    sb.append(chars, 1, 3); // 期望 "BCD"
    sb.append(']');
    System.out.println("append_charArray_slice_normal='" + sb.toString() + "'");

    // off < 0 / len < 0 / off+len 超出数组边界：JDK 会抛出 IndexOutOfBoundsException 或 StringIndexOutOfBoundsException。
    // 这里通过标签化结果记录 jsjvm 实际行为。
    sb = new StringBuffer("X");
    try {
      sb.append(chars, -1, 2);
      System.out.println("append_charArray_slice_off_neg:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("append_charArray_slice_off_neg:StringIndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      System.out.println("append_charArray_slice_off_neg:IndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("append_charArray_slice_off_neg:" + t.getClass().getName());
    }

    sb = new StringBuffer("Y");
    try {
      sb.append(chars, 2, -1);
      System.out.println("append_charArray_slice_len_neg:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("append_charArray_slice_len_neg:StringIndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      System.out.println("append_charArray_slice_len_neg:IndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("append_charArray_slice_len_neg:" + t.getClass().getName());
    }

    sb = new StringBuffer("Z");
    try {
      sb.append(chars, 3, 5); // off+len 超出
      System.out.println("append_charArray_slice_overflow:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("append_charArray_slice_overflow:StringIndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      System.out.println("append_charArray_slice_overflow:IndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("append_charArray_slice_overflow:" + t.getClass().getName());
    }

    // append((char[]) null)：JDK 语义为抛出 NullPointerException。
    sb = new StringBuffer("N");
    try {
      sb.append((char[]) null);
      System.out.println("append_charArray_null:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (NullPointerException e) {
      System.out.println("append_charArray_null:NullPointerException");
    } catch (Throwable t) {
      System.out.println("append_charArray_null:" + t.getClass().getName());
    }
  }

  /**
   * 覆盖 charAt / setCharAt / setLength 等索引相关方法的正常与异常路径。
   */
  private static void testIndexAccessAndSetters() {
    StringBuffer sb = new StringBuffer("abcdef");

    // charAt 的正常索引
    System.out.println("charAt_0='" + sb.charAt(0) + "'");
    System.out.println("charAt_5='" + sb.charAt(5) + "'");

    // charAt 的越界索引
    try {
      sb.charAt(-1);
      System.out.println("charAt_neg_index:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("charAt_neg_index:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("charAt_neg_index:" + t.getClass().getName());
    }

    try {
      sb.charAt(6);
      System.out.println("charAt_eq_length:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("charAt_eq_length:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("charAt_eq_length:" + t.getClass().getName());
    }

    // setCharAt 的正常与越界。
    sb = new StringBuffer("xyz");
    sb.setCharAt(0, 'A');
    sb.setCharAt(2, 'Z');
    System.out.println("setCharAt_normal_result='" + sb.toString() + "'");

    try {
      sb.setCharAt(-1, 'Q');
      System.out.println("setCharAt_neg_index:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("setCharAt_neg_index:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("setCharAt_neg_index:" + t.getClass().getName());
    }

    try {
      sb.setCharAt(3, 'Q');
      System.out.println("setCharAt_eq_length:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("setCharAt_eq_length:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("setCharAt_eq_length:" + t.getClass().getName());
    }

    // setLength 扩展与截断，以及负值的异常行为。
    sb = new StringBuffer("01");
    sb.setLength(5); // 扩展，JDK 中会用 '\u0000' 填充
    System.out.println("setLength_extend_length=" + sb.length());
    System.out.println("setLength_extend_chars='" + printableChars(sb) + "'");

    sb.setLength(1); // 截断
    System.out.println("setLength_truncate_length=" + sb.length());
    System.out.println("setLength_truncate_toString='" + sb.toString() + "'");

    try {
      sb.setLength(-1);
      System.out.println("setLength_negative:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("setLength_negative:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("setLength_negative:" + t.getClass().getName());
    }
  }

  /**
   * 打印 StringBuffer 中每个字符的整数编码，避免依赖其它系统类的复杂行为。
   */
  private static String printableChars(StringBuffer sb) {
    StringBuffer out = new StringBuffer();
    for (int i = 0; i < sb.length(); i++) {
      if (i > 0) {
        out.append(',');
      }
      out.append((int) sb.charAt(i));
    }
    return out.toString();
  }

  /**
   * 覆盖 deleteCharAt / delete 的正常与多种异常组合。
   */
  private static void testDeleteVariants() {
    StringBuffer sb = new StringBuffer("012345");

    // deleteCharAt 正常删除中间字符
    sb.deleteCharAt(2);
    System.out.println("deleteCharAt_middle='" + sb.toString() + "'");

    // deleteCharAt 边界与越界
    try {
      sb.deleteCharAt(-1);
      System.out.println("deleteCharAt_neg_index:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("deleteCharAt_neg_index:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("deleteCharAt_neg_index:" + t.getClass().getName());
    }

    try {
      sb.deleteCharAt(sb.length());
      System.out.println("deleteCharAt_eq_length:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("deleteCharAt_eq_length:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("deleteCharAt_eq_length:" + t.getClass().getName());
    }

    // delete(start,end) 的典型路径：
    sb = new StringBuffer("abcdefgh");
    sb.delete(0, 2); // 删除前两个字符
    System.out.println("delete_prefix='" + sb.toString() + "'");

    sb = new StringBuffer("abcdefgh");
    sb.delete(2, 2); // start == end，应为 no-op
    System.out.println("delete_emptyRange='" + sb.toString() + "'");

    sb = new StringBuffer("abcdefgh");
    sb.delete(3, 100); // end > length，JDK 中会截断到 length
    System.out.println("delete_to_end='" + sb.toString() + "'");

    // start < 0
    sb = new StringBuffer("abcd");
    try {
      sb.delete(-1, 2);
      System.out.println("delete_start_neg:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("delete_start_neg:StringIndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      System.out.println("delete_start_neg:IndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("delete_start_neg:" + t.getClass().getName());
    }

    // start > end
    sb = new StringBuffer("abcd");
    try {
      sb.delete(3, 1);
      System.out.println("delete_start_gt_end:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("delete_start_gt_end:StringIndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      System.out.println("delete_start_gt_end:IndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("delete_start_gt_end:" + t.getClass().getName());
    }
  }

  /**
   * 覆盖 insert 的所有重载形式，包括字符串、对象、各类基本类型以及 char[] 与切片。
   */
  private static void testInsertOverloads() {
    // insert(int,String)
    StringBuffer sb = new StringBuffer("012345");
    sb.insert(0, "A");
    sb.insert(3, "B");
    sb.insert(sb.length(), "C");
    System.out.println("insert_String_basic='" + sb.toString() + "'");

    // insert(int,String) 的越界 index
    sb = new StringBuffer("123");
    try {
      sb.insert(-1, "X");
      System.out.println("insert_String_index_neg:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("insert_String_index_neg:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("insert_String_index_neg:" + t.getClass().getName());
    }

    try {
      sb.insert(10, "X");
      System.out.println("insert_String_index_gt_len:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("insert_String_index_gt_len:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("insert_String_index_gt_len:" + t.getClass().getName());
    }

    // insert(int, 基本类型)
    sb = new StringBuffer("-");
    sb.insert(1, 123);
    sb.insert(2, 456L);
    sb.insert(0, true);
    sb.insert(0, false);
    sb.insert(2, 'Z');
    sb.insert(sb.length(), 1.25d);
    sb.insert(sb.length(), -3.5f);
    System.out.println("insert_primitives='" + sb.toString() + "'");

    // insert(int,char[]) 与 insert(int,char[],off,len)
    char[] chars = new char[] {'a', 'b', 'c', 'd', 'e'};
    sb = new StringBuffer("---");
    sb.insert(1, chars);
    System.out.println("insert_charArray_full='" + sb.toString() + "'");

    sb = new StringBuffer("---");
    sb.insert(2, chars, 1, 3);
    System.out.println("insert_charArray_slice_normal='" + sb.toString() + "'");

    // char[] 切片的边界与异常（off < 0 / len < 0 / off+len 超界）。
    sb = new StringBuffer("P");
    try {
      sb.insert(1, chars, -1, 2);
      System.out.println("insert_charArray_slice_off_neg:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("insert_charArray_slice_off_neg:StringIndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      System.out.println("insert_charArray_slice_off_neg:IndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("insert_charArray_slice_off_neg:" + t.getClass().getName());
    }

    sb = new StringBuffer("Q");
    try {
      sb.insert(1, chars, 2, -1);
      System.out.println("insert_charArray_slice_len_neg:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("insert_charArray_slice_len_neg:StringIndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      System.out.println("insert_charArray_slice_len_neg:IndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("insert_charArray_slice_len_neg:" + t.getClass().getName());
    }

    sb = new StringBuffer("R");
    try {
      sb.insert(1, chars, 4, 5);
      System.out.println("insert_charArray_slice_overflow:NO_EXCEPTION, result='" + sb.toString() + "'");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("insert_charArray_slice_overflow:StringIndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      System.out.println("insert_charArray_slice_overflow:IndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("insert_charArray_slice_overflow:" + t.getClass().getName());
    }

    // insert(int,Object) 覆盖 null 与自定义对象的 toString()。
    sb = new StringBuffer("OBJ");
    sb.insert(1, new CustomObject("X"));
    sb.insert(0, (Object) null);
    System.out.println("insert_Object_result='" + sb.toString() + "'");
  }

  /**
   * 覆盖 getChars 的正常复制、边界检查以及对 null 目标数组的行为。
   */
  private static void testGetCharsAndExceptions() {
    StringBuffer sb = new StringBuffer("ABCDEFG");
    char[] dst = new char[10];
    for (int i = 0; i < dst.length; i++) {
      dst[i] = '#';
    }

    // 正常路径：复制中间一段到目标数组中间位置。
    sb.getChars(1, 5, dst, 2); // 预期覆盖 "BCDE"
    System.out.println("getChars_normal_dst='" + new String(dst) + "'");

    // srcBegin < 0
    try {
      sb.getChars(-1, 3, dst, 0);
      System.out.println("getChars_srcBegin_neg:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("getChars_srcBegin_neg:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("getChars_srcBegin_neg:" + t.getClass().getName());
    }

    // srcEnd > length
    try {
      sb.getChars(0, 100, dst, 0);
      System.out.println("getChars_srcEnd_gt_len:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("getChars_srcEnd_gt_len:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("getChars_srcEnd_gt_len:" + t.getClass().getName());
    }

    // dstBegin < 0
    try {
      sb.getChars(0, 2, dst, -1);
      System.out.println("getChars_dstBegin_neg:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("getChars_dstBegin_neg:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("getChars_dstBegin_neg:" + t.getClass().getName());
    }

    // dstBegin + (srcEnd - srcBegin) > dst.length
    try {
      sb.getChars(0, 7, dst, 5);
      System.out.println("getChars_dst_overflow:NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("getChars_dst_overflow:StringIndexOutOfBoundsException");
    } catch (Throwable t) {
      System.out.println("getChars_dst_overflow:" + t.getClass().getName());
    }

    // dst 为 null：JDK 中应抛出 NullPointerException，这里记录 jsjvm 的实际行为。
    try {
      sb.getChars(0, 1, null, 0);
      System.out.println("getChars_dst_null:NO_EXCEPTION");
    } catch (NullPointerException e) {
      System.out.println("getChars_dst_null:NullPointerException");
    } catch (Throwable t) {
      System.out.println("getChars_dst_null:" + t.getClass().getName());
    }
  }

  /**
   * 覆盖 reverse() 与 ensureCapacity(int) 的行为，包括容量扩展请求后的 capacity 变化。
   */
  private static void testReverseAndEnsureCapacity() {
    StringBuffer sb = new StringBuffer("racecar");
    sb.reverse();
    System.out.println("reverse_palindrome='" + sb.toString() + "'");

    sb = new StringBuffer("0123456789");
    sb.reverse();
    System.out.println("reverse_normal='" + sb.toString() + "'");

    // ensureCapacity：JDK 语义为在当前容量基础上扩容到至少 requestedCapacity。
    StringBuffer cap = new StringBuffer();
    int before = cap.capacity();
    cap.ensureCapacity(before + 50);
    int after = cap.capacity();
    System.out.println("ensureCapacity_before=" + before);
    System.out.println("ensureCapacity_after=" + after);
    System.out.println("ensureCapacity_grew_or_equal=" + (after >= before + 50));
  }
}

