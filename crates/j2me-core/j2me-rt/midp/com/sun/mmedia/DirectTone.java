package com.sun.mmedia;

import javax.microedition.media.control.ToneControl;
import javax.microedition.media.Control;

/**
 * DirectTone extends DirectPlayer to add ToneControl support
 * for device://tone players.
 */
public class DirectTone extends DirectPlayer implements ToneControl {

    private byte[] sequence;

    protected Control doGetControl(String type) {
        String prefix = "javax.microedition.media.control.";
        String shortType = type;
        if (type.startsWith(prefix)) {
            shortType = type.substring(prefix.length());
        }
        if (shortType.equals("ToneControl")) {
            return this;
        }
        return super.doGetControl(type);
    }

    public void setSequence(byte[] sequence) {
        if (sequence == null) {
            throw new IllegalArgumentException("null sequence");
        }
        this.sequence = sequence;
        nSetSequence(hNative, sequence);
    }

    private static native void nSetSequence(int handle, byte[] sequence);
}
