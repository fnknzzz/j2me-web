package javax.microedition.lcdui;

import java.util.Date;

final class NativeDateInput {
    private NativeDateInput() {}

    static void show(Object dateField, int mode, boolean initialized,
                     long valueMillis) {
        long[] result = show0(dateField, mode, initialized, valueMillis);
        if (result != null && result.length >= 3 && result[0] != 0) {
            commit(dateField, result[1] != 0, result[2]);
        }
    }

    private static native long[] show0(Object dateField, int mode,
                                       boolean initialized, long valueMillis);

    static native void hide();

    static void commit(Object item, boolean initialized, long valueMillis) {
        if (item instanceof DateField) {
            DateField dateField = (DateField)item;
            Date oldValue = dateField.getDate();
            Date newValue = initialized ? new Date(valueMillis) : null;
            if (oldValue == null && newValue == null) {
                return;
            }
            if (oldValue != null && newValue != null
                    && oldValue.getTime() == newValue.getTime()) {
                return;
            }
            dateField.setDate(newValue);
            dateField.notifyStateChanged();
        }
    }
}
