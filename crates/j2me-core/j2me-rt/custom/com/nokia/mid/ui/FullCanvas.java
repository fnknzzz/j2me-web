package com.nokia.mid.ui;

import javax.microedition.lcdui.Canvas;

public abstract class FullCanvas extends Canvas {
    public static final int KEY_SOFTKEY1 = -6;
    public static final int KEY_SOFTKEY2 = -7;
    public static final int KEY_SOFTKEY3 = -5;
    public static final int KEY_SEND = -10;
    public static final int KEY_END = -11;
    public static final int KEY_SOFTKEY4 = -8;

    protected FullCanvas() {
        super();
        setFullScreenMode(true);
    }
}
