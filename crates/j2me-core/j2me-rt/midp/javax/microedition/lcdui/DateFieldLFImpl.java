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

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.sun.midp.log.Logging;
import com.sun.midp.log.LogChannels;

/**
 * Look and feel implementation of <code>DateField</code> based on 
 * platform widget. 
 */
class DateFieldLFImpl extends ItemLFImpl implements DateFieldLF {
    private static final int JAVA_HORIZONTAL_INSET = 4;
    private static final int JAVA_VERTICAL_INSET = 3;
    private static final int JAVA_FIELD_PADDING = 4;
    private static final int JAVA_FIELD_RADIUS = 6;
    private static final int JAVA_GAP = 2;
    private static final int JAVA_ICON_SIZE = 12;
    private static final int JAVA_ICON_GAP = 5;

    /**
     * Creates <code>DateFieldLF</code> for the passed in 
     * <code>DateField</code> object.
     *
     * @param dateField the <code>DateField</code> object associated with 
                        this view
    */
    DateFieldLFImpl(DateField dateField) {
        super(dateField);

        df = dateField;
    }
        
    /**
     * Notifies L&amp;F of a date change in the corresponding 
     * <code>DateField</code>.
     *
     * @param date the new <code>Date</code> set in the 
     *             <code>DateField</code>
     */
    public void lSetDate(java.util.Date date) {
	if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
	    Logging.report(Logging.INFORMATION, LogChannels.LC_HIGHUI,
			   "(mdSetDate: id="+nativeId+")");
	}

	if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
	    if (date != null) {	    
		setDate0(nativeId, date.getTime());
	    } else {
		setDate0(nativeId, 0l);
	    }
	}
        lRequestInvalidate(true, true);
    }

    /**
     * Setting date/time of this widget.
     *
     * @param nativeId native resource id of this <code>Item</code>
     * @param date in seconds
     */
    private native void setDate0(int nativeId, long date);

    /**
     * Notifies L&amp;F of a new input mode set in the corresponding 
     * <code>DateField</code>.
     *
     * @param mode the new input mode set in the <code>DateField</code>.
     */
    public void lSetInputMode(int mode) {

	if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
	    Logging.report(Logging.INFORMATION, LogChannels.LC_HIGHUI,
			   "(mdSetInputMode: id="+nativeId+")");
	}

	if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
	    setInputMode0(nativeId, mode);
	    lRequestInvalidate(true, true);
	}
        if (lUseJavaPaint()) {
            lRequestInvalidate(true, true);
        }
    }

    /**
     * Set mode - time and or date.
     *
     * @param nativeId native resource id of this <code>Item</code>
     * @param mode the new input mode set in the <code>DateField</code>.
     */
    private native void setInputMode0(int nativeId, int mode);

    /**
     * Overrides <code>ItemLFImpl</code> to sync with
     * native resource before hiding it
     * Called by the system to hide this <code>Item</code>'s
     * native resource
     */
    void lHideNativeResource() {
        if (lUseJavaPaint()) {
            NativeDateInput.hide();
            super.lHideNativeResource();
            return;
        }

	if (!dateSynced) {
	    // sync java peer with native data before hiding
	    df.setDateImpl(lGetDate());
	    dateSynced = true;
	}

	super.lHideNativeResource();
    }

    /**
     * Called by event delivery to notify an <code>ItemLF</code> in current 
     * <code>FormLF</code> of a change in its peer state.
     *
     * @param hint <code>1</code> if date, 
     *             <code>2</code> if hour, 
     *             <code>3</code> if minute changed
     *
     * @return always <code>true</code> to notify 
     *         <code>ItemStateListener</code>
     */
    boolean uCallPeerStateChanged(int hint) { 
	// Any hint means user has changed some aspect of the date in native
	// since native peer changed, java peer is no longer up to date.
	dateSynced = false;
	
	// if the datefield is not initialized yet, initialize now.
	if (!df.initialized) {
	    synchronized (Display.LCDUILock) {
		lGetDate();
	    }
	}	
	// Set flag so native resource will be queried for latest value
	// Tell Form that ItemStateListener should be notified
	return true;
    }

    /**
     * Gets the date currently set on the date field widget.
     * This method is called by <code>Date</code> only if <code>Date</code> 
     * is initialized.
     *
     * @return the date this widget is currently set to
     */
    public Date lGetDate() {

	if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
	    Logging.report(Logging.INFORMATION, LogChannels.LC_HIGHUI,
			   "(mdGetDate: id="+nativeId+")");
	}

	if (lUseJavaPaint()) {
            return df.initialized
                ? new Date(df.currentDate.getTime().getTime())
                : null;
        }

	if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {

	    long ld = getDate0(nativeId);
			
	    if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
		Logging.report(Logging.INFORMATION, LogChannels.LC_HIGHUI,
			       "(mdGetDate: returned long=="+ld+")");
	    }
	    if (ld > 0) {
		Date nd = new Date(ld);
		df.setDateImpl(nd);
		dateSynced = true;
		return nd;
	    }    
	} else if (dateSynced && df.currentDate != null) {
	    return df.currentDate.getTime();
	}

	return null;
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

        return ((df.layout & Item.LAYOUT_2) != Item.LAYOUT_2);
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

        return ((df.layout & Item.LAYOUT_2) != Item.LAYOUT_2);
    }

    public int lGetPreferredWidth(int h) {
        if (lUseJavaPaint()) {
            return lGetAvailableJavaPaintWidth();
        }
        return super.lGetPreferredWidth(h);
    }

    public int lGetPreferredHeight(int w) {
        if (lUseJavaPaint()) {
            if (w == -1) {
                w = lGetPreferredWidth(-1);
            }
            return lGetJavaPaintHeight(w);
        }
        return super.lGetPreferredHeight(w);
    }

    void lCallPaint(Graphics g, int w, int h) {
        if (g == null || !lUseJavaPaint()) {
            return;
        }

        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        Font inputFont = Font.getFont(Font.FONT_INPUT_TEXT);
        Font hintFont = Font.getFont(Font.FACE_SYSTEM,
                                     Font.STYLE_PLAIN,
                                     Font.SIZE_SMALL);
        int y = JAVA_VERTICAL_INSET;
        int contentX = JAVA_HORIZONTAL_INSET;
        int contentWidth = w - (JAVA_HORIZONTAL_INSET * 2);
        if (contentWidth < 0) {
            contentWidth = 0;
        }

        if (df.label != null && df.label.length() > 0) {
            g.setColor(0x606060);
            g.setFont(labelFont);
            g.drawString(df.label, contentX, y, Graphics.TOP | Graphics.LEFT);
            y += labelFont.getHeight() + JAVA_GAP;
        }

        int fieldHeight = lGetJavaPaintFieldHeight(inputFont);
        if (contentWidth <= 0) {
            return;
        }

        g.setColor(0xffffff);
        g.fillRoundRect(contentX, y, contentWidth - 1, fieldHeight - 1,
                        JAVA_FIELD_RADIUS, JAVA_FIELD_RADIUS);
        g.setColor(hasFocus ? 0x4f8fd8 : 0x9a9a9a);
        g.drawRoundRect(contentX, y, contentWidth - 1, fieldHeight - 1,
                        JAVA_FIELD_RADIUS, JAVA_FIELD_RADIUS);
        if (hasFocus) {
            g.setColor(0xd7ecff);
            g.drawRoundRect(contentX + 1, y + 1, contentWidth - 3,
                            fieldHeight - 3, JAVA_FIELD_RADIUS,
                            JAVA_FIELD_RADIUS);
        }

        int iconX = contentX + JAVA_FIELD_PADDING;
        int iconY = y + ((fieldHeight - JAVA_ICON_SIZE) / 2);
        lDrawDateIcon(g, iconX, iconY, JAVA_ICON_SIZE,
                      hasFocus ? 0x4f8fd8 : 0x606060);

        int textX = iconX + JAVA_ICON_SIZE + JAVA_ICON_GAP;
        int textWidth = contentX + contentWidth - JAVA_FIELD_PADDING - textX;
        int textY = y + ((fieldHeight - inputFont.getHeight()) / 2);
        g.setFont(inputFont);
        g.setColor(df.initialized ? 0x202020 : 0x707070);
        lDrawClippedString(g, lGetJavaPaintDisplayText(), textX, textY,
                           textWidth);

        y += fieldHeight + JAVA_GAP;
        g.setFont(hintFont);
        g.setColor(0x777777);
        lDrawClippedString(g, lGetJavaPaintModeText(), contentX, y,
                           contentWidth);
    }

    void uCallKeyPressed(int keyCode) {
        if (!lUseJavaPaint()) {
            return;
        }
        int dir = KeyConverter.getGameAction(keyCode);
        if (dir == Canvas.FIRE) {
            lShowNativeDateInput();
        }
    }

    void lShowNativeDateInput() {
        long value = 0l;
        if (df.initialized) {
            value = df.currentDate.getTime().getTime();
        }
        NativeDateInput.show(df, df.mode, df.initialized, value);
    }

    void uCallTraverseOut() {
        if (lUseJavaPaint()) {
            NativeDateInput.hide();
        }
        super.uCallTraverseOut();
    }

    int lGetJavaPaintHeight(int width) {
        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        Font inputFont = Font.getFont(Font.FONT_INPUT_TEXT);
        Font hintFont = Font.getFont(Font.FACE_SYSTEM,
                                     Font.STYLE_PLAIN,
                                     Font.SIZE_SMALL);
        int height = JAVA_VERTICAL_INSET * 2;
        if (df.label != null && df.label.length() > 0) {
            height += labelFont.getHeight() + JAVA_GAP;
        }
        height += lGetJavaPaintFieldHeight(inputFont);
        height += JAVA_GAP + hintFont.getHeight();
        return height;
    }

    int lGetJavaPaintFocusX() {
        return JAVA_HORIZONTAL_INSET;
    }

    int lGetJavaPaintFocusY() {
        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        int y = JAVA_VERTICAL_INSET;
        if (df.label != null && df.label.length() > 0) {
            y += labelFont.getHeight() + JAVA_GAP;
        }
        return y;
    }

    int lGetJavaPaintFocusWidth(int width) {
        int focusWidth = width - (JAVA_HORIZONTAL_INSET * 2);
        return focusWidth < 0 ? 0 : focusWidth;
    }

    int lGetJavaPaintFocusHeight() {
        return lGetJavaPaintFieldHeight(Font.getFont(Font.FONT_INPUT_TEXT));
    }

    /**
     * Gets the current value set on the native datefield.
     *
     * @param nativeId native resource id of this <code>Item</code>
     *
     * @return time in millis since 1970
     */
    private native long getDate0(int nativeId);

    /**
     * Create native resource for current <code>DateField</code>.
     * Override function in <code>ItemLFImpl</code>.
     *
     * @param ownerId Owner screen's native resource id
     */
    void createNativeResource(int ownerId) {
        if (lUseJavaPaint()) {
            nativeId = DisplayableLFImpl.INVALID_NATIVE_ID;
            return;
        }
	nativeId = createNativeResource0(ownerId,
					 df.label,
					 df.layout,
					 (df.getDate() != null ?
					  df.currentDate.getTime().getTime():
					  0),
					 df.mode,
					 df.currentDate.getTimeZone().getID());
	
	if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
	    Logging.report(Logging.INFORMATION, LogChannels.LC_HIGHUI,
			   "(createNativeResource: id="+nativeId+")");
	}
    }

    /**
     * KNI function that create native resource for current 
     * <code>DateField</code>.
     *
     * @param ownerId Owner screen's native resource id 
     *               (<code>MidpDisplayable *</code>)
     * @param label label sting of this <code>Item</code>
     * @param layout layout directive associated with 
     *               this <code>DateField</code>.
     * @param datetime date in millis since Jan 1'st 1970
     * @param displayMode date and or time mode
     * @param timeZone label of timezone
     *
     * @return native resource id (<code>MidpItem *</code>) of this 
     *         <code>Item</code>
     */
    private native int createNativeResource0(int ownerId,
					     String label,
					     int layout,
					     long datetime,
					     int displayMode,
					     String timeZone);

    /**
     * <code>DateField</code> associated with this view.
     */
    private DateField df;

    /**
     * Flag indicating if date value stored in java is up to date with native.
     * When visible, the date is stored in the native widget.
     * When hiding (and destroying) the native resource, we need to cache the 
     * value in the java peer for later use.
     * dateSynced flag is a dirty bit used to check if a sync is needed
     * when hiding.
     */
    private boolean dateSynced = true;

    private boolean lUseJavaPaint() {
        return df.owner instanceof Form;
    }

    private int lGetAvailableJavaPaintWidth() {
        if (df.owner != null) {
            DisplayableLF ownerLF = df.owner.getLF();
            if (ownerLF != null) {
                return ownerLF.lGetWidth();
            }
        }
        return Display.WIDTH;
    }

    private int lGetJavaPaintFieldHeight(Font font) {
        return font.getHeight() + (JAVA_FIELD_PADDING * 2);
    }

    private String lGetJavaPaintDisplayText() {
        if (!df.initialized) {
            return "";
        }

        Calendar calendar = Calendar.getInstance(
            df.currentDate.getTimeZone());
        calendar.setTime(df.currentDate.getTime());
        String date = lPad(calendar.get(Calendar.YEAR), 4) + "-"
            + lPad(calendar.get(Calendar.MONTH) + 1, 2) + "-"
            + lPad(calendar.get(Calendar.DATE), 2);
        String time = lPad(calendar.get(Calendar.HOUR_OF_DAY), 2) + ":"
            + lPad(calendar.get(Calendar.MINUTE), 2);

        if (df.mode == DateField.TIME) {
            return time;
        }
        if (df.mode == DateField.DATE_TIME) {
            return date + " " + time;
        }
        return date;
    }

    private String lGetJavaPaintModeText() {
        if (df.mode == DateField.TIME) {
            return "time";
        }
        if (df.mode == DateField.DATE_TIME) {
            return "date time";
        }
        return "date";
    }

    private String lPad(int value, int length) {
        String text = String.valueOf(value);
        while (text.length() < length) {
            text = "0" + text;
        }
        return text;
    }

    private void lDrawClippedString(Graphics g, String text, int x, int y,
                                    int maxWidth) {
        if (text == null || text.length() == 0 || maxWidth <= 0) {
            return;
        }

        Font font = g.getFont();
        int end = text.length();
        while (end > 0 && font.substringWidth(text, 0, end) > maxWidth) {
            end--;
        }
        if (end <= 0) {
            return;
        }
        g.drawString(text.substring(0, end), x, y,
                     Graphics.TOP | Graphics.LEFT);
    }

    private void lDrawDateIcon(Graphics g, int x, int y, int size,
                               int color) {
        if (size <= 0) {
            return;
        }
        g.setColor(color);
        g.drawRect(x, y + 1, size - 1, size - 2);
        g.drawLine(x, y + 4, x + size - 1, y + 4);
        g.fillRect(x + 2, y, 2, 3);
        g.fillRect(x + size - 4, y, 2, 3);
        g.fillRect(x + 3, y + 7, 2, 2);
        g.fillRect(x + 7, y + 7, 2, 2);
    }
}
