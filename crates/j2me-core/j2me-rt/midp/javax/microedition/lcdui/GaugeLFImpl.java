/*
 *   
 *
 * Copyright  1990-2009 Sun Microsystems, Inc. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License version
 * 2 only, as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included at /legal/license.txt).
 * 
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 * 
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, CA 95054 or visit www.sun.com if you need additional
 * information or have any questions.
 */

package javax.microedition.lcdui;

/**
 * Look &amp; feel implementation for <code>Gauge</code> using platform widget.
 */
class GaugeLFImpl extends ItemLFImpl implements GaugeLF {
    private static final int JAVA_BAR_WIDTH = 120;
    private static final int JAVA_BAR_HEIGHT = 10;
    private static final int JAVA_LABEL_CONTENT_GAP = 2;
    private static final int JAVA_VALUE_GAP = 3;

    /**
     * Creates <code>GaugeLF</code> for the passed in <code>Gauge</code>.
     *
     * @param gauge the <code>Gauge</code> object associated with this 
     * look&amp;feel.
     */
    GaugeLFImpl(Gauge gauge) {
        
        super(gauge);

        this.gauge = gauge;
    }


    // *****************************************************
    //  Public methods
    // *****************************************************

    /**
     * Notifies L&amp;F of a value change in the corresponding 
     * <code>Gauge</code>.
     *
     * @param oldValue the old value set in the <code>Gauge</code>
     * @param newValue the new value set in the <code>Gauge</code>
     */
    public void lSetValue(int oldValue, int newValue) {
	// little optimization to avoid calling into native
	// in cases where unnecessary
	if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
	    // Only update native resource if it exists.
	    setValue0(nativeId, newValue, gauge.maxValue);
	    
	    lRequestInvalidate(true, true);
	}
    }

    /**
     * Notifies L&amp;F of a maximum value change in the corresponding 
     * <code>Gauge</code>.
     *
     * @param oldMaxValue the old maximum value set in the <code>Gauge</code>
     * @param newMaxValue the new maximum value set in the <code>Gauge</code>
     */
    public void lSetMaxValue(int oldMaxValue, int newMaxValue) {
	if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
	    // Only update native resource if it exists.
	    setValue0(nativeId, gauge.value, newMaxValue);
	    lRequestInvalidate(true, true);
	}
    }

    /**
     * Gets the current value.
     *
     * @return the current value
     */
    public int lGetValue() {
	return gauge.value;
    }

    public int lGetPreferredWidth(int h) {
        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN,
                                 Font.SIZE_SMALL);
        int width = JAVA_BAR_WIDTH;
        if (gauge.label != null && gauge.label.length() > 0) {
            int labelWidth = font.stringWidth(gauge.label);
            if (labelWidth > width) {
                width = labelWidth;
            }
        }
        String valueText = lGetJavaPaintValueText();
        int valueWidth = font.stringWidth(valueText);
        if (valueWidth > width) {
            width = valueWidth;
        }
        int available = lGetAvailableJavaPaintWidth();
        return width > available ? available : width;
    }

    public int lGetPreferredHeight(int w) {
        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN,
                                 Font.SIZE_SMALL);
        int height = 0;
        if (gauge.label != null && gauge.label.length() > 0) {
            height += font.getHeight() + JAVA_LABEL_CONTENT_GAP;
        }
        height += JAVA_BAR_HEIGHT + JAVA_VALUE_GAP + font.getHeight();
        return height;
    }

    public int lGetMinimumWidth() {
        return lGetPreferredWidth(-1);
    }

    public int lGetMinimumHeight() {
        return lGetPreferredHeight(-1);
    }

    /**
     * Notification of a change in its peer state.
     * Update Java peer with user input.
     *  
     * @param hint the new value of an interactive gauge
     *
     * @return always <code>true</code> so to notify 
     *         <code>ItemStateListener</code>
     */
    boolean uCallPeerStateChanged(int hint) {
	synchronized (Display.LCDUILock) {
	    // Update Java peer with the new value
	    gauge.value = hint;
	}
	// Indicate to Form to notify ItemStateListener
	return true;
    }

    void uCallKeyPressed(int keyCode) {
        int dir = KeyConverter.getGameAction(keyCode);
        if (!gauge.interactive || gauge.maxValue <= 0) {
            return;
        }
        if (dir == Canvas.LEFT) {
            gauge.setValue(gauge.value - 1);
        } else if (dir == Canvas.RIGHT) {
            gauge.setValue(gauge.value + 1);
        }
    }

    // *****************************************************
    //  Package private methods
    // *****************************************************

    /**
     * Determine if this <code>Item</code> should have a newline after it.
     *
     * @return <code>true</code> if it should have a newline after
     */
    boolean equateNLA() {
        if (super.equateNLA()) {
            return true;
        }

        return ((gauge.layout & Item.LAYOUT_2) != Item.LAYOUT_2);
    }
               

    /**
     * Determine if this <code>Item</code> should have a newline before it.
     *
     * @return <code>true</code> if it should have a newline before
     */
    boolean equateNLB() {
        if (super.equateNLB()) {
            return true;
        }

        return ((gauge.layout & Item.LAYOUT_2) != Item.LAYOUT_2);
    }

    /**
     * Create native resource for current <code>Gauge</code>.
     * Override function in <code>ItemLFImpl</code>.
     *
     * @param ownerId Owner screen's native resource id
     */
    void createNativeResource(int ownerId) {
	nativeId = createNativeResource0(ownerId,
					 gauge.label, gauge.layout,
					 gauge.interactive, 
					 gauge.maxValue, gauge.value);
    }

    void lCallPaint(Graphics g, int w, int h) {
        if (g == null) {
            return;
        }

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN,
                                 Font.SIZE_SMALL);
        int y = 0;
        if (gauge.label != null && gauge.label.length() > 0) {
            g.setColor(0x606060);
            g.setFont(font);
            g.drawString(gauge.label, 0, y, Graphics.TOP | Graphics.LEFT);
            y += font.getHeight() + JAVA_LABEL_CONTENT_GAP;
        }

        int barWidth = w;
        if (barWidth > JAVA_BAR_WIDTH) {
            barWidth = JAVA_BAR_WIDTH;
        }
        if (barWidth < 24) {
            barWidth = 24;
        }
        int barX = 0;
        int layout = getLayout() & FormLFImpl.LAYOUT_HMASK;
        if (layout == Item.LAYOUT_CENTER) {
            barX = (w - barWidth) / 2;
        } else if (layout == Item.LAYOUT_RIGHT) {
            barX = w - barWidth;
        }
        if (barX < 0) {
            barX = 0;
        }

        g.setColor(0xe8e8e8);
        g.fillRect(barX, y, barWidth, JAVA_BAR_HEIGHT);
        g.setColor(0x8c8c8c);
        g.drawRect(barX, y, barWidth - 1, JAVA_BAR_HEIGHT - 1);

        int fillWidth = lGetJavaPaintFillWidth(barWidth);
        if (fillWidth > 0) {
            g.setColor(gauge.interactive ? 0x2f80ed : 0x27ae60);
            g.fillRect(barX + 1, y + 1, fillWidth, JAVA_BAR_HEIGHT - 2);
        }

        y += JAVA_BAR_HEIGHT + JAVA_VALUE_GAP;
        g.setColor(Theme.getColor(Display.COLOR_FOREGROUND));
        g.setFont(font);
        g.drawString(lGetJavaPaintValueText(), barX, y,
                     Graphics.TOP | Graphics.LEFT);
    }

    /**
     * KNI function that creates native resource for current 
     * <code>Gauge</code>.
     *
     * @param ownerId Owner screen's native resource id 
     *                (<code>MidpDisplayable *</code>)
     * @param label label string of this <code>Item</code>
     * @param layout layout directive associated with this <code>Item</code> 
     * @param interactive indicates whether gauge is interactive or not
     * @param maxValue the maximum value for this gauge
     * @param initialValue the current value of the gauge
     *
     * @return native resource id (<code>MidpItem *</code>) of this 
     *         <code>Gauge</code>
     */
    private native int createNativeResource0(int ownerId,
					     String label, 
					     int layout,
					     boolean interactive,
					     int maxValue,
					     int initialValue);
    
    /**
     * KNI function that sets the current and maximum values on the native 
     * resource corresponding to the current <code>Gauge</code>.
     *
     * @param nativeId native resource id for this <code>Item</code>
     * @param newValue new value of the current <code>Gauge</code>
     * @param newMaxValue new maximum value of the current <code>Gauge</code>
     */
    private native void setValue0(int nativeId, 
				  int newValue,
				  int newMaxValue);

    private int lGetJavaPaintFillWidth(int barWidth) {
        int innerWidth = barWidth - 2;
        if (innerWidth <= 0) {
            return 0;
        }
        if (gauge.maxValue == Gauge.INDEFINITE) {
            if (gauge.value == Gauge.CONTINUOUS_RUNNING
                    || gauge.value == Gauge.INCREMENTAL_UPDATING) {
                return innerWidth / 2;
            }
            return 0;
        }
        if (gauge.maxValue <= 0) {
            return 0;
        }
        int fill = (innerWidth * gauge.value) / gauge.maxValue;
        if (fill < 0) {
            fill = 0;
        }
        if (fill > innerWidth) {
            fill = innerWidth;
        }
        return fill;
    }

    private String lGetJavaPaintValueText() {
        if (gauge.maxValue == Gauge.INDEFINITE) {
            switch (gauge.value) {
            case Gauge.CONTINUOUS_IDLE:
                return "idle";
            case Gauge.INCREMENTAL_IDLE:
                return "incremental idle";
            case Gauge.CONTINUOUS_RUNNING:
                return "running";
            case Gauge.INCREMENTAL_UPDATING:
                return "updating";
            default:
                return "indefinite";
            }
        }
        return gauge.value + "/" + gauge.maxValue;
    }

    private int lGetAvailableJavaPaintWidth() {
        if (gauge.owner != null) {
            DisplayableLF ownerLF = gauge.owner.getLF();
            if (ownerLF != null) {
                return ownerLF.lGetWidth();
            }
        }
        return Display.WIDTH;
    }
    
    /** 
     * <code>Gauge</code> instance associated with this view.
     */
    private Gauge gauge;     
    
} // GaugeView
