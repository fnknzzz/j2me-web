package com.com.example;

/**
 * 针对 jsjvm 中 java.lang.Character 系统类实现的边界测试。
 *
 * 重点覆盖：
 * - 实例方法：<init>(C)、charValue()、hashCode()、equals(Object)、toString()
 * - 静态方法：isDigit / isUpperCase / isLowerCase / toUpperCase / toLowerCase / digit
 * - 各种边界情况：补码截断、radix 越界、非数字字符、非 Character 对象的 equals 对比等。
 */
public class CharacterSystemClassSuite {

  public static void main(String[] args) {
    System.out.println("=== instance_methods ===");
    testInstanceMethods();

    System.out.println("=== static_isDigit_and_case ===");
    testStaticIsDigitAndCase();

    System.out.println("=== static_toUpperLower ===");
    testStaticToUpperLower();

    System.out.println("=== static_digit_radix ===");
    testStaticDigitRadix();
  }

  /**
   * 覆盖：<init>(C)、charValue()、hashCode()、equals(Object)、toString()。
   */
  private static void testInstanceMethods() {
    Character cA1 = new Character('A');
    Character cA2 = new Character('A');
    Character cB = new Character('B');

    System.out.println("cA1_class=" + cA1.getClass().getName());
    System.out.println("cA1_charValue=" + cA1.charValue());
    System.out.println("cA1_toString=" + cA1.toString());
    System.out.println("cA1_hashCode=" + cA1.hashCode());

    System.out.println("cA1_equals_cA2=" + cA1.equals(cA2));
    System.out.println("cA1_equals_cB=" + cA1.equals(cB));
    System.out.println("cA1_equals_null=" + cA1.equals(null));
    System.out.println("cA1_equals_nonCharacter=" + cA1.equals("A"));

    // 使用不同的 char 值，确保 PD 中存储的是 16 位 char 而不是其他类型
    Character cz = new Character('z');
    System.out.println("cz_charValue=" + cz.charValue());
    System.out.println("cz_toString=" + cz.toString());
  }

  /**
   * 覆盖静态方法 isDigit / isUpperCase / isLowerCase 的典型与边界值：
   * - isDigit: 十进制数字、非数字字母、全角数字等
   * - isUpperCase / isLowerCase: ASCII、大写/小写中文、非字母符号
   */
  private static void testStaticIsDigitAndCase() {
    System.out.println("isDigit_9=" + Character.isDigit('9'));
    System.out.println("isDigit_a=" + Character.isDigit('a'));

    // 使用 Unicode 数字字符（全角 １ U+FF11）验证 \p{Nd} 匹配
    System.out.println("isDigit_fullwidth_1=" + Character.isDigit('１'));

    System.out.println("isUpperCase_A=" + Character.isUpperCase('A'));
    System.out.println("isUpperCase_a=" + Character.isUpperCase('a'));
    System.out.println("isLowerCase_a=" + Character.isLowerCase('a'));
    System.out.println("isLowerCase_A=" + Character.isLowerCase('A'));

    // 非字母字符
    System.out.println("isUpperCase_exclam=" + Character.isUpperCase('!'));
    System.out.println("isLowerCase_exclam=" + Character.isLowerCase('!'));

    // 中文字符：用输出来锁定行为
    System.out.println("isUpperCase_zhong=" + Character.isUpperCase('中'));
    System.out.println("isLowerCase_zhong=" + Character.isLowerCase('中'));
  }

  /**
   * 覆盖静态 toUpperCase / toLowerCase：
   * - ASCII 大小写转换
   * - 已经是对应大小写的字符
   * - 非字母符号应保持不变
   * - 中文字符在 toUpperCase/toLowerCase 下的行为
   */
  private static void testStaticToUpperLower() {
    System.out.println("toUpperCase_a=" + Character.toUpperCase('a'));
    System.out.println("toUpperCase_A=" + Character.toUpperCase('A'));
    System.out.println("toLowerCase_A=" + Character.toLowerCase('A'));
    System.out.println("toLowerCase_a=" + Character.toLowerCase('a'));

    System.out.println("toUpperCase_exclam=" + Character.toUpperCase('!'));
    System.out.println("toLowerCase_exclam=" + Character.toLowerCase('!'));
  }

  /**
   * 覆盖静态 digit(char, radix)：
   * - 合法数字/字母（大小写）在不同 radix 下的取值
   * - radix 小于 2、大于 36 的裁剪行为
   * - 非数字字符返回 -1
   */
  private static void testStaticDigitRadix() {
    // 十进制数字
    System.out.println("digit_9_10=" + Character.digit('9', 10));
    System.out.println("digit_9_2=" + Character.digit('9', 2));

    // 16 进制：大小写字母
    System.out.println("digit_A_16=" + Character.digit('A', 16));
    System.out.println("digit_a_16=" + Character.digit('a', 16));
    System.out.println("digit_G_16=" + Character.digit('G', 16));

    // radix 越界：<2 时按 2 处理，>36 时按 36 处理
    System.out.println("digit_1_radix_1=" + Character.digit('1', 1));
    System.out.println("digit_z_radix_40=" + Character.digit('z', 40));

    // 非数字字符
    System.out.println("digit_quest_10=" + Character.digit('?', 10));
  }
}
