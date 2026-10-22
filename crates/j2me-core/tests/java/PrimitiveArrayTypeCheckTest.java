public class PrimitiveArrayTypeCheckTest {
    private static int failures = 0;

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("PASS " + name);
        } else {
            failures++;
            System.out.println("FAIL " + name);
        }
    }

    private static Object makeByteArrayAsObject() {
        return new byte[] { 1, 2, 3 };
    }

    public static void main(String[] args) {
        byte[] bytes = new byte[] { 1, 2, 3 };
        short[] shorts = new short[] { 4, 5 };
        Object boxedBytes = makeByteArrayAsObject();
        String[] strings = new String[] { "a", "b" };
        Object[] objects = new Object[] { "x", "y" };
        Object boxedStrings = strings;
        Object boxedObjects = objects;

        check("byte-array-is-byte-array", bytes instanceof byte[]);
        check("short-array-is-short-array", shorts instanceof short[]);
        check("boxed-byte-array-is-byte-array", boxedBytes instanceof byte[]);
        check("byte-array-is-object", bytes instanceof Object);
        check("boxed-byte-array-is-object", boxedBytes instanceof Object);
        check("byte-array-is-not-short-array", !(boxedBytes instanceof short[]));
        check("string-array-is-string-array", strings instanceof String[]);
        check("boxed-string-array-is-string-array", boxedStrings instanceof String[]);
        check("string-array-is-object-array", strings instanceof Object[]);
        check("boxed-string-array-is-object-array", boxedStrings instanceof Object[]);
        check("object-array-is-object-array", objects instanceof Object[]);
        check("object-array-is-not-string-array", !(boxedObjects instanceof String[]));

        byte[] casted = (byte[]) boxedBytes;
        check("checkcast-byte-array-preserves-length", casted.length == 3);
        check("checkcast-byte-array-preserves-data", casted[0] == 1 && casted[2] == 3);
        String[] castedStrings = (String[]) boxedStrings;
        Object[] castedObjects = (Object[]) boxedStrings;
        check("checkcast-string-array-preserves-length", castedStrings.length == 2);
        check("checkcast-string-array-preserves-data", "a".equals(castedStrings[0]) && "b".equals(castedStrings[1]));
        check("checkcast-string-array-to-object-array", castedObjects.length == 2);

        if (failures == 0) {
            System.out.println("ALL ARRAY TYPE CHECKS PASSED");
        } else {
            System.out.println("ARRAY TYPE CHECK FAILURES=" + failures);
        }
    }
}
