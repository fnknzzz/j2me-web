package javax.microedition.lcdui;

final class NativeTextInput {
    private NativeTextInput() {}

    static void show(Object textField, String text, int constraints,
                     int maxSize) {
        String value = show0(textField, text, constraints, maxSize);
        if (value != null) {
            commit(textField, value);
        }
    }

    private static native String show0(Object textField, String text,
                                      int constraints, int maxSize);

    static native void hide();

    static void commit(Object item, String value) {
        if (item instanceof TextField) {
            TextField textField = (TextField)item;
            String oldValue = textField.getString();
            if (oldValue != null && oldValue.equals(value)) {
                return;
            }
            try {
                textField.setString(value);
            } catch (IllegalArgumentException e) {
                return;
            }
            textField.notifyStateChanged();
        }
    }
}
