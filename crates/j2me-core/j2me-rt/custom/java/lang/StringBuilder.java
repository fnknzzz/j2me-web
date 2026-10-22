package java.lang;

public final class StringBuilder {
    private StringBuffer buffer;

    public StringBuilder() {
        buffer = new StringBuffer();
    }

    public StringBuilder(int capacity) {
        buffer = new StringBuffer(capacity);
    }

    public StringBuilder(String str) {
        buffer = new StringBuffer(str);
    }

    public StringBuilder append(Object obj) {
        buffer.append(obj);
        return this;
    }

    public StringBuilder append(String str) {
        buffer.append(str);
        return this;
    }

    public StringBuilder append(StringBuffer sb) {
        buffer.append(sb);
        return this;
    }

    public StringBuilder append(char[] str) {
        buffer.append(str);
        return this;
    }

    public StringBuilder append(char[] str, int offset, int len) {
        buffer.append(str, offset, len);
        return this;
    }

    public StringBuilder append(boolean b) {
        buffer.append(b);
        return this;
    }

    public StringBuilder append(char c) {
        buffer.append(c);
        return this;
    }

    public StringBuilder append(int i) {
        buffer.append(i);
        return this;
    }

    public StringBuilder append(long lng) {
        buffer.append(lng);
        return this;
    }

    public StringBuilder append(float f) {
        buffer.append(f);
        return this;
    }

    public StringBuilder append(double d) {
        buffer.append(d);
        return this;
    }

    public StringBuilder delete(int start, int end) {
        buffer.delete(start, end);
        return this;
    }

    public StringBuilder deleteCharAt(int index) {
        buffer.deleteCharAt(index);
        return this;
    }

    public StringBuilder insert(int offset, Object obj) {
        buffer.insert(offset, obj);
        return this;
    }

    public StringBuilder insert(int offset, String str) {
        buffer.insert(offset, str);
        return this;
    }

    public StringBuilder insert(int offset, char[] str) {
        buffer.insert(offset, str);
        return this;
    }

    public StringBuilder insert(int offset, boolean b) {
        buffer.insert(offset, b);
        return this;
    }

    public StringBuilder insert(int offset, char c) {
        buffer.insert(offset, c);
        return this;
    }

    public StringBuilder insert(int offset, int i) {
        buffer.insert(offset, i);
        return this;
    }

    public StringBuilder insert(int offset, long l) {
        buffer.insert(offset, l);
        return this;
    }

    public StringBuilder insert(int offset, float f) {
        buffer.insert(offset, f);
        return this;
    }

    public StringBuilder insert(int offset, double d) {
        buffer.insert(offset, d);
        return this;
    }

    public int length() {
        return buffer.length();
    }

    public int capacity() {
        return buffer.capacity();
    }

    public void ensureCapacity(int minimumCapacity) {
        buffer.ensureCapacity(minimumCapacity);
    }

    public void setLength(int newLength) {
        buffer.setLength(newLength);
    }

    public char charAt(int index) {
        return buffer.charAt(index);
    }

    public void setCharAt(int index, char ch) {
        buffer.setCharAt(index, ch);
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        buffer.getChars(srcBegin, srcEnd, dst, dstBegin);
    }

    public StringBuilder reverse() {
        buffer.reverse();
        return this;
    }

    public String toString() {
        return buffer.toString();
    }

    public String substring(int start) {
        return buffer.toString().substring(start);
    }

    public String substring(int start, int end) {
        return buffer.toString().substring(start, end);
    }
}
