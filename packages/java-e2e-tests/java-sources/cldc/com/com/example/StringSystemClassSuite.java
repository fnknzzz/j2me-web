package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.String 系统类实现的边界与行为测试。
 *
 * 重点覆盖：
 * - 静态方法：valueOf 的所有重载（int/long/float/double/boolean/char/Object/char[]/char[]+offset+len）
 * - 构造方法：()、(String)、(byte[])、(byte[],int,int)、(byte[],String)、(byte[],int,int,String)、
 *   (char[])、(char[],int,int)、(StringBuffer)
 * - 实例方法：length/charAt/equals/equalsIgnoreCase/hashCode/toString
 * - 子串与查找：substring/indexOf/lastIndexOf/startsWith/endsWith/compareTo/regionMatches
 * - 大小写与空白：toLowerCase/toUpperCase/trim/concat
 * - 数组与字节：toCharArray/getBytes()/getBytes(String)/getChars
 * - 其他：intern
 *
 * 所有测试通过 System.out.println 打印，最终通过快照比对，
 * 不依赖具体实现细节，只验证 jsjvm 行为与这里预期一致。
 */
public class StringSystemClassSuite {

  public static void main(String[] args) throws Exception {
    System.out.println("=== static_valueOf_primitives_and_object ===");
    testStaticValueOfPrimitivesAndObject();

    System.out.println("=== constructors_and_basic_methods ===");
    testConstructorsAndBasicMethods();

    System.out.println("=== constructors_from_bytes_and_charset ===");
    testConstructorsFromBytesAndCharset();

    System.out.println("=== substring_and_exceptions ===");
    testSubstringAndExceptions();

    System.out.println("=== indexOf_lastIndexOf_variants ===");
    testIndexOfAndLastIndexOfVariants();

    System.out.println("=== starts_ends_compare_trim_case_concat ===");
    testStartsEndsCompareTrimCaseConcat();

    System.out.println("=== char_arrays_getBytes_getChars ===");
    testCharArraysGetBytesGetChars();

    System.out.println("=== regionMatches_and_intern ===");
    testRegionMatchesAndIntern();

    System.out.println("=== utf8_bom_handling ===");
    testUtf8BomHandling();
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
   * - String.valueOf(int/long/float/double/boolean/char)
   * - String.valueOf(Object)：null、String、自定义对象
   * - String.valueOf(char[]) / String.valueOf(char[], off, len) 的边界（off<0、len 超界）
   */
  private static void testStaticValueOfPrimitivesAndObject() {
    // 基本类型
    print("valueOf_int_42", String.valueOf(42));
    print("valueOf_int_0", String.valueOf(0));
    print("valueOf_int_negative", String.valueOf(-7));

    print("valueOf_long", String.valueOf(1234567890123L));
    print("valueOf_boolean_true", String.valueOf(true));
    print("valueOf_boolean_false", String.valueOf(false));

    // float 的实现使用 toPrecision(7) 再去尾 0，验证几种边界
    print("valueOf_float_1_5", String.valueOf(1.5f));
    print("valueOf_float_0", String.valueOf(0.0f));
    print("valueOf_float_small", String.valueOf(0.00000123f));

    print("valueOf_double_pi", String.valueOf(3.1415926));

    print("valueOf_char_A", String.valueOf('A'));

    // Object 重载：null -> "null"
    Object objNull = null;
    print("valueOf_object_null", String.valueOf(objNull));

    // Object 重载：String 直接返回原字符串
    String s = "hello";
    print("valueOf_object_string", String.valueOf((Object) s));

    // Object 重载：自定义 toString，验证通过 toString 路径
    Object custom = new Object() {
      public String toString() {
        return "CustomToString";
      }
    };
    print("valueOf_object_custom", String.valueOf(custom));

    // char[] 与 char[],off,len
    char[] chars = new char[] {'a', 'b', 'c', 'd', 'e'};
    print("valueOf_chars_full", String.valueOf(chars));
    print("valueOf_chars_mid", String.valueOf(chars, 1, 3)); // "bcd"

    // 边界：起始偏移为负数、长度超出数组范围
    // jsjvm 实现会做 clamp，标准 JDK 抛出 StringIndexOutOfBoundsException
    try {
      print("valueOf_chars_negative_off", String.valueOf(chars, -2, 3));
    } catch (StringIndexOutOfBoundsException e) {
      print("valueOf_chars_negative_off", "exception:" + e.getClass().getSimpleName());
    }
    try {
      print("valueOf_chars_large_len", String.valueOf(chars, 3, 10));
    } catch (StringIndexOutOfBoundsException e) {
      print("valueOf_chars_large_len", "exception:" + e.getClass().getSimpleName());
    }
  }

  /**
   * 覆盖：
   * - 无参构造 new String()，以及 (String) 构造
   * - length/charAt/equals/equalsIgnoreCase/hashCode/toString
   * - charAt 越界时 jsjvm 当前实现不会抛异常，而是返回 0 code point
   */
  private static void testConstructorsAndBasicMethods() {
    String empty1 = new String();
    String empty2 = new String("");
    String fromLiteral = new String("AbC");

    print("ctor_empty1_length", empty1.length());
    print("ctor_empty2_length", empty2.length());
    print("ctor_fromLiteral_length", fromLiteral.length());

    // charAt 正常范围
    print("charAt_0", (int) fromLiteral.charAt(0));
    print("charAt_1", (int) fromLiteral.charAt(1));

    // charAt 越界：标准 JDK 抛出 StringIndexOutOfBoundsException，jsjvm 返回 0
    try {
      print("charAt_negative", (int) fromLiteral.charAt(-1));
    } catch (StringIndexOutOfBoundsException e) {
      print("charAt_negative", "exception:" + e.getClass().getSimpleName());
    }
    try {
      print("charAt_too_large", (int) fromLiteral.charAt(10));
    } catch (StringIndexOutOfBoundsException e) {
      print("charAt_too_large", "exception:" + e.getClass().getSimpleName());
    }

    // equals / equalsIgnoreCase
    print("equals_same", fromLiteral.equals("AbC"));
    print("equals_diff", fromLiteral.equals("ABC"));
    print("equals_null", fromLiteral.equals(null));
    print("equals_other_type", fromLiteral.equals(new Object()));

    print("equalsIgnoreCase_same", fromLiteral.equalsIgnoreCase("abc"));
    print("equalsIgnoreCase_diff_len", fromLiteral.equalsIgnoreCase("ab"));

    // hashCode 与 toString，只需打印以覆盖实现
    print("hashCode_AbC", fromLiteral.hashCode());
    print("toString_AbC", fromLiteral.toString());
  }

  /**
   * 覆盖：
   * - new String(byte[]) 与 new String(byte[],int,int)
   * - new String(byte[], String charset) 与 new String(byte[],int,int,String)
   *   对 UTF-8 / ISO-8859-1(LATIN1) / US-ASCII(ASCII) / 其他字符集名 的行为
   */
  private static void testConstructorsFromBytesAndCharset() throws Exception {
    // 基础 ASCII + 高位字节，验证 &0xff 行为
    byte[] bytes = new byte[] {65, 66, -1}; // 'A','B',0xFF
    String s1 = new String(bytes);
    dumpCharArray("ctor_bytes_basic_char_codes", s1.toCharArray());

    String s2 = new String(bytes, 1, 2);
    dumpCharArray("ctor_bytes_offset_char_codes", s2.toCharArray());

    // 偏移/长度越界：jsjvm 使用 clamp，标准 JDK 抛出 StringIndexOutOfBoundsException
    try {
      String s3 = new String(bytes, -1, 5);
      print("ctor_bytes_offset_clamp", s3);
    } catch (StringIndexOutOfBoundsException e) {
      print("ctor_bytes_offset_clamp", "exception:" + e.getClass().getSimpleName());
    }

    // 按不同 charset 构造
    byte[] asciiBytes = new byte[] {65, 66, 67}; // "ABC"
    String iso = new String(asciiBytes, "ISO-8859-1");
    String latin1 = new String(asciiBytes, "latin1");
    String ascii = new String(asciiBytes, "US-ASCII");
    String ascii2 = new String(asciiBytes, "ASCII");

    print("ctor_bytes_iso_8859_1", iso);
    print("ctor_bytes_latin1", latin1);
    print("ctor_bytes_us_ascii", ascii);
    print("ctor_bytes_ascii", ascii2);

    // UTF-8 字节：字符 "中" 的 UTF-8 序列 E4 B8 AD
    byte[] utf8Zh = new byte[] {(byte) 0xE4, (byte) 0xB8, (byte) 0xAD};
    // charset 为 null：jsjvm 走默认 UTF-8，标准 JDK 抛出 NullPointerException
    try {
      String utf8Default = new String(utf8Zh, (String) null);
      print("ctor_bytes_utf8_default", utf8Default);
    } catch (NullPointerException e) {
      print("ctor_bytes_utf8_default", "exception:" + e.getClass().getSimpleName());
    }
    String utf8Explicit = new String(utf8Zh, "UTF-8");
    dumpCharArray("ctor_bytes_utf8_explicit_char_codes", utf8Explicit.toCharArray());

    // 构造中使用未知 charset 名，按实现会退化为默认 UTF-8 行为
    String utf8Unknown = new String(utf8Zh, "UTF-16");
    dumpCharArray("ctor_bytes_unknown_charset_char_codes", utf8Unknown.toCharArray());

    // 带 offset/len + charset 的构造
    byte[] bytesWithHead = new byte[] {0, 65, 66, 67, 0};
    String s4 = new String(bytesWithHead, 1, 3, "ISO-8859-1");
    print("ctor_bytes_offset_charset_iso", s4);

    // 负偏移：jsjvm 使用 clamp，标准 JDK 抛出 StringIndexOutOfBoundsException
    try {
      String s5 = new String(bytesWithHead, -2, 10, "ASCII");
      print("ctor_bytes_offset_charset_clamp", s5);
    } catch (StringIndexOutOfBoundsException e) {
      print("ctor_bytes_offset_charset_clamp", "exception:" + e.getClass().getSimpleName());
    }
  }

  /**
   * 覆盖 substring 的合法与非法边界，并验证抛出的异常类型为
   * java.lang.StringIndexOutOfBoundsException（由 StringIndexOutOfBoundsExceptionDecl 提供）。
   */
  private static void testSubstringAndExceptions() {
    String base = "abcdef";

    // 合法切片
    print("substring_from_0", base.substring(0));
    print("substring_from_len", base.substring(base.length()));
    print("substring_1_4", base.substring(1, 4));

    // 非法：begin < 0
    try {
      base.substring(-1);
      print("substring_begin_negative", "NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      print("substring_begin_negative", e.getClass().getName());
    } catch (Throwable t) {
      print("substring_begin_negative_other", t.getClass().getName());
    }

    // 非法：begin > length
    try {
      base.substring(10);
      print("substring_begin_gt_length", "NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      print("substring_begin_gt_length", e.getClass().getName());
    } catch (Throwable t) {
      print("substring_begin_gt_length_other", t.getClass().getName());
    }

    // 非法：b < 0 / e > length / b > e
    try {
      base.substring(-1, 2);
      print("substring_range_negative", "NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      print("substring_range_negative", e.getClass().getName());
    } catch (Throwable t) {
      print("substring_range_negative_other", t.getClass().getName());
    }

    try {
      base.substring(0, 10);
      print("substring_range_end_gt_length", "NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      print("substring_range_end_gt_length", e.getClass().getName());
    } catch (Throwable t) {
      print("substring_range_end_gt_length_other", t.getClass().getName());
    }

    try {
      base.substring(4, 2);
      print("substring_range_begin_gt_end", "NO_EXCEPTION");
    } catch (StringIndexOutOfBoundsException e) {
      print("substring_range_begin_gt_end", e.getClass().getName());
    } catch (Throwable t) {
      print("substring_range_begin_gt_end_other", t.getClass().getName());
    }
  }

  /**
   * 覆盖 indexOf/lastIndexOf 的所有重载：
   * - 按字符（含 BMP 内和非 BMP 码点）
   * - 按字符串 + 起始位置
   * - 起始位置负数/大于 length 的边界
   */
  private static void testIndexOfAndLastIndexOfVariants() {
    String base = "ab😀cd😀ab"; // 使用代理对表示 U+1F600

    // 按字符：普通 BMP 范围
    print("indexOf_char_a", base.indexOf('a'));
    print("indexOf_char_z", base.indexOf('z'));

    // 按字符：非 BMP 码点 0x1F600（实现会转换为代理对）
    int smile = 0x1F600;
    print("indexOf_codePoint_smile", base.indexOf(smile));
    print("indexOf_codePoint_smile_from_3", base.indexOf(smile, 3));

    print("lastIndexOf_codePoint_smile", base.lastIndexOf(smile));
    print("lastIndexOf_codePoint_smile_from_100", base.lastIndexOf(smile, 100));

    // indexOf(String)
    print("indexOf_str_ab", base.indexOf("ab"));
    print("indexOf_str_emoji", base.indexOf("😀"));
    print("indexOf_str_empty", base.indexOf(""));

    // indexOf(String, from)
    print("indexOf_str_ab_from_1", base.indexOf("ab", 1));
    print("indexOf_str_ab_from_5", base.indexOf("ab", 5));
    print("indexOf_str_ab_from_negative", base.indexOf("ab", -5));
    print("indexOf_str_ab_from_large", base.indexOf("ab", 100));

    // lastIndexOf(int,char)
    print("lastIndexOf_char_a", base.lastIndexOf('a'));
    print("lastIndexOf_char_a_from_3", base.lastIndexOf('a', 3));
    print("lastIndexOf_char_a_from_100", base.lastIndexOf('a', 100));
    print("lastIndexOf_char_a_from_negative", base.lastIndexOf('a', -1));
  }

  /**
   * 覆盖 startsWith/endsWith/compareTo/toLowerCase/toUpperCase/trim/concat
   * 的典型与边界行为。
   */
  private static void testStartsEndsCompareTrimCaseConcat() {
    String s = "  AbCd  ";

    // startsWith / endsWith
    print("startsWith_Ab", s.startsWith("  Ab"));
    print("startsWith_empty", s.startsWith(""));
    print("startsWith_long_prefix", s.startsWith("  AbCd   xxx"));

    print("endsWith_spaces", s.endsWith("  "));
    print("endsWith_empty", s.endsWith(""));

    // startsWith(String,int) 边界：负 offset、大于 length、刚好贴边
    print("startsWith_offset_ok", s.startsWith("Ab", 2));
    print("startsWith_offset_negative", s.startsWith("Ab", -1));
    print("startsWith_offset_too_large", s.startsWith("Ab", 100));
    print("startsWith_offset_empty_prefix", s.startsWith("", 3));

    // compareTo
    print("compareTo_equal", "abc".compareTo("abc"));
    print("compareTo_less", "abc".compareTo("abd"));
    print("compareTo_greater", "abd".compareTo("abc"));
    print("compareTo_prefix", "abc".compareTo("abcd"));

    // 大小写与 trim
    print("toLowerCase", s.toLowerCase());
    print("toUpperCase", s.toUpperCase());
    print("trim", s.trim());

    // concat
    String a = "hello";
    print("concat_normal", a.concat(" world"));
    print("concat_empty_right", a.concat(""));
    print("concat_empty_left", "".concat("xyz"));
  }

  /**
   * 覆盖：
   * - toCharArray
   * - getBytes() 默认 UTF-8
   * - getBytes(String) 对 UTF-8 / ISO-8859-1 / ASCII / 其他 的行为
   * - getChars 源/目标下标的 clamp 行为
   */
  private static void testCharArraysGetBytesGetChars() throws Exception {
    String s = "Ab中"; // 包含 ASCII 与非 ASCII

    // toCharArray
    char[] chars = s.toCharArray();
    System.out.println("toCharArray_length=" + chars.length);
    for (int i = 0; i < chars.length; i++) {
      System.out.println("toCharArray_" + i + "=" + (int) chars[i]);
    }

    // getBytes("UTF-8")
    byte[] utf8 = s.getBytes("UTF-8");
    System.out.print("getBytes_default_utf8=");
    for (int i = 0; i < utf8.length; i++) {
      if (i > 0) System.out.print(",");
      System.out.print(utf8[i] & 0xFF);
    }
    System.out.println();

    // getBytes(String) 各种 charset
    printBytes("getBytes_UTF8", s.getBytes("UTF-8"));
    printBytes("getBytes_ISO_8859_1", s.getBytes("ISO-8859-1"));
    printBytes("getBytes_LATIN1", s.getBytes("LATIN1"));
    printBytes("getBytes_ASCII", s.getBytes("ASCII"));
    printBytes("getBytes_US_ASCII", s.getBytes("US-ASCII"));
    printBytes("getBytes_unknown", s.getBytes("UTF-16"));

    // getChars：源区间越界时，jsjvm 实现会 clamp
    String src = "abcdef";
    char[] dst = new char[10];
    src.getChars(1, 4, dst, 2);
    dumpCharArray("getChars_normal", dst);

    // srcBegin < 0、srcEnd > length：jsjvm 会 clamp，标准 JDK 抛出 StringIndexOutOfBoundsException
    char[] dst2 = new char[10];
    try {
      src.getChars(-3, 100, dst2, 0);
      dumpCharArray("getChars_clamp", dst2);
    } catch (StringIndexOutOfBoundsException e) {
      System.out.println("getChars_clamp=exception:" + e.getClass().getSimpleName());
    }
  }

  private static void printBytes(String label, byte[] bytes) {
    System.out.print(label + "=");
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) System.out.print(",");
      System.out.print(bytes[i] & 0xFF);
    }
    System.out.println();
  }

  private static void dumpCharArray(String label, char[] arr) {
    System.out.print(label + "=");
    for (int i = 0; i < arr.length; i++) {
      if (i > 0) System.out.print(",");
      System.out.print((int) arr[i]);
    }
    System.out.println();
  }

  /**
   * 覆盖：
   * - regionMatches(ignoreCase, toffset, other, ooffset, len) 的大小写与越界行为
   * - char[] 构造：new String(char[]) 与 new String(char[],int,int)
   * - new String(StringBuffer) 与 intern()
   */
  private static void testRegionMatchesAndIntern() {
    String a = "AbCdEf";
    String b = "xxabCDefyy";

    // regionMatches ignoreCase=false
    print(
        "regionMatches_exact",
        a.regionMatches(false, 0, b, 2, 4)); // "AbCd" vs "abCD" -> false

    // regionMatches ignoreCase=true
    print(
        "regionMatches_ignore_case",
        a.regionMatches(true, 0, b, 2, 4));

    // 越界：toffset/offset+len 超出字符串范围，按实现返回 false
    print("regionMatches_out_of_range1", a.regionMatches(false, -1, b, 0, 2));
    print(
        "regionMatches_out_of_range2",
        a.regionMatches(false, 0, b, 100, 2));

    // char[] 构造
    char[] data = new char[] {'H', 'e', 'l', 'l', 'o'};
    String fromChars = new String(data);
    String fromCharsSub = new String(data, 1, 3);
    print("ctor_chars_all", fromChars);
    print("ctor_chars_sub", fromCharsSub);

    // StringBuffer 构造：依赖 StringBuffer 系统类在 PD 中存储 value
    StringBuffer sb = new StringBuffer();
    sb.append("buffer");
    String fromBuffer = new String(sb);
    print("ctor_string_buffer", fromBuffer);

    // intern：验证常量池字符串与运行时 intern 结果的引用关系
    String literal = "interned";
    String viaNew = new String("interned");
    String interned = viaNew.intern();
    print("intern_literal_eq_interned", literal == interned);
    print("intern_new_eq_interned", viaNew == interned);
  }

  /**
   * 覆盖 UTF-8 BOM 处理：
   * - BOM 应该被保留在字符串中（ignoreBOM: true）
   * - 游戏代码通常用 substring(1) 跳过 BOM
   * - 测试 new String(byte[], charset) 和 new String(byte[], off, len, charset)
   */
  private static void testUtf8BomHandling() throws Exception {
    // UTF-8 BOM (EF BB BF) + "ABC"
    byte[] bomAbc = new byte[] {
      (byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
      0x41, 0x42, 0x43
    };
    String s1 = new String(bomAbc, "UTF-8");
    print("bom_abc_length", s1.length());
    print("bom_abc_first_is_bom", s1.charAt(0) == '\uFEFF');
    print("bom_abc_after_substring", s1.substring(1));

    // UTF-8 BOM + 中文 "余额"
    byte[] bomChinese = new byte[] {
      (byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
      (byte) 0xE4, (byte) 0xBD, (byte) 0x99,
      (byte) 0xE9, (byte) 0xA2, (byte) 0x9D
    };
    String s2 = new String(bomChinese, "UTF-8");
    print("bom_chinese_length", s2.length());
    print("bom_chinese_first_is_bom", s2.charAt(0) == '\uFEFF');
    String afterBom = s2.substring(1);
    dumpCharArray("bom_chinese_after_substring_char_codes", afterBom.toCharArray());
    print("bom_chinese_indexOf_1", afterBom.indexOf('\u4F59'));
    print("bom_chinese_indexOf_2", afterBom.indexOf('\u989D'));

    // 带 offset/length 的构造函数：new String(byte[], off, len, charset)
    byte[] withPadding = new byte[] {
      0x00, 0x00,
      (byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
      (byte) 0xE4, (byte) 0xBD, (byte) 0x99,
      (byte) 0xE9, (byte) 0xA2, (byte) 0x9D,
      0x00, 0x00
    };
    String s3 = new String(withPadding, 2, 9, "UTF-8");
    print("bom_offset_length", s3.length());
    print("bom_offset_first_is_bom", s3.charAt(0) == '\uFEFF');
    String afterBom2 = s3.substring(1);
    dumpCharArray("bom_offset_after_substring_char_codes", afterBom2.toCharArray());
    print("bom_offset_indexOf_1", afterBom2.indexOf('\u4F59'));
  }
}
