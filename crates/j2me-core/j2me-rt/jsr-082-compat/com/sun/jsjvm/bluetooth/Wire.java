package com.sun.jsjvm.bluetooth;

public final class Wire {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private Wire() {}

    public static String escape(String value) {
        if (value == null) return "";
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' || c == '\t' || c == '\n' || c == '\r' || c == ';' || c == '=') {
                out.append('%');
                out.append(HEX[(c >> 4) & 15]);
                out.append(HEX[c & 15]);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String unescape(String value) {
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.append((char)((hi << 4) | lo));
                    i += 2;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    public static String[] split(String value, char delimiter) {
        java.util.Vector parts = new java.util.Vector();
        int start = 0;
        for (int i = 0; i <= value.length(); i++) {
            if (i == value.length() || value.charAt(i) == delimiter) {
                parts.addElement(value.substring(start, i));
                start = i + 1;
            }
        }
        String[] result = new String[parts.size()];
        parts.copyInto(result);
        return result;
    }

    public static String bytesToHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 255;
            out[i * 2] = HEX[b >> 4];
            out[i * 2 + 1] = HEX[b & 15];
        }
        return new String(out);
    }

    public static byte[] hexToBytes(String value) {
        if ((value.length() & 1) != 0) throw new IllegalArgumentException();
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(value.charAt(i * 2), 16);
            int lo = Character.digit(value.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException();
            out[i] = (byte)((hi << 4) | lo);
        }
        return out;
    }
}
