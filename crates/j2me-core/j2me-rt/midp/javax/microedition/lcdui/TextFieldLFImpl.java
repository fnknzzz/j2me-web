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
 * Implementation class for TextFieldLF
 */

import com.sun.midp.lcdui.DynamicCharacterArray;
import com.sun.midp.lcdui.TextPolicy;
import com.sun.midp.configurator.Constants;

/** 
 * Look and feel implementation of <code>TextField</code> based on 
 * platform widget. 
 */
class TextFieldLFImpl extends ItemLFImpl implements TextFieldLF {
    private static final int JAVA_HORIZONTAL_INSET = 4;
    private static final int JAVA_VERTICAL_INSET = 3;
    private static final int JAVA_FIELD_PADDING = 4;
    private static final int JAVA_FIELD_RADIUS = 6;
    private static final int JAVA_GAP = 2;
    private static final int JAVA_TEXTBOX_MIN_LINES = 5;

    /**
     * Creates <code>TextFieldLF</code> for the passed in 
     * <code>TextField</code>.
     *
     * @param tf The <code>TextField</code> associated with this 
     *           <code>TextFieldLF</code>
     */
    TextFieldLFImpl(TextField tf) {
        super(tf);

        this.tf  = tf;
    }

    // *****************************************************
    //  Public methods defined in interfaces
    // *****************************************************

    /**
     * Update the character buffer in <code>TextField</code> with latest 
     * user input.
     *
     * @return <code>true</code> if there is new user input updated in 
     *         the buffer
     */
    public boolean lUpdateContents() {
	// No pending user input when there is no native resource or not shown
	if (nativeId == DisplayableLFImpl.INVALID_NATIVE_ID) {
	    return false;
	}

	// Query native resource for pending user input
	return getString0(nativeId, tf.buffer);
    }

    /**
     * Override the preferred width of this <code>Item</code>.
     *
     * @param h tentative locked height.
     * 		Ignored here.
     *
     * @return the preferred width
     */
    public int lGetPreferredWidth(int h) {

	// note: h is not used

	// For TextBox return all width available for the item
	if (tf.owner instanceof TextBox) {
	    return ((DisplayableLFImpl)tf.owner.getLF()).width;
	}
        if (lUseJavaPaint()) {
            return lGetAvailableJavaPaintWidth();
        }
	return super.lGetPreferredWidth(h);
    }

    /**
     * Override the preferred height of this <code>Item</code>.
     *
     * @param w tentative locked width.
     * 		Ignored here and preferred width is used always.
     *
     * @return the preferred height
     */
    public int lGetPreferredHeight(int w) {

	// note: w is not used

        if (lUseJavaPaint()) {
            if (w == -1) {
                w = lGetPreferredWidth(-1);
            }
            return lGetJavaPaintHeight(w);
        }

	int h = super.lGetPreferredHeight(w);
	
	// For TextBox return all height available for the item
	if (tf.owner instanceof TextBox) {
	    if (((DisplayableLFImpl)tf.owner.getLF()).height > h) {
		h = ((DisplayableLFImpl)tf.owner.getLF()).height;
	    }
	}
	return h;
    }

    /**
     * Get current contents from native resource.
     *
     * @param nativeId native resource id of this <code>Item</code>
     * @param buffer the char array to be populated
     *
     * @return <code>true</code> if there is new input
     */
    private native boolean getString0(int nativeId,
				      DynamicCharacterArray buffer);

    /**
     * Update content and caret position of the TextField's native widget.
     *
     * @param nativeId native resource id of this <code>Item</code>
     * @param buffer char array of the new contents
     */
    private native void setString0(int nativeId, DynamicCharacterArray buffer);

    /**
     * Notifies L&amp;F of a content change in the corresponding 
     * <code>TextField</code>.
     * The parameters are not used. Instead, this function directly 
     * uses data from TextField.java.
     */
    public void lSetChars() {
	// Only update native resource if it exists.
	if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
	    setString0(nativeId, tf.buffer);
	}

	lRequestInvalidate(true, true);
    }

    /**
     * Notifies L&amp;F of a character insertion in the corresponding 
     * <code>TextField</code>.
     *
     * @param data the source of the character data. Not used.
     * @param offset the beginning of the region of characters copied. 
     *               Not used.
     * @param length the number of characters copied. Not used.
     * @param position the position at which insertion occurred
     */
    public void lInsert(char data[], int offset, int length, int position) {
	// Simplify porting layer by treating insert as setChars
	lSetChars();
    }

    /**
     * Notifies L&amp;F of character deletion in the corresponding 
     * <code>TextField</code>.
     *
     * @param offset the beginning of the deleted region
     * @param length the number of characters deleted
     */
    public void lDelete(int offset, int length) {
	// Simplify porting layer by treating delete as setChars
	lSetChars();
    }

    /**
     * Notifies L&amp;F of a maximum size change in the corresponding 
     * <code>TextField</code>.
     *
     * @param maxSize the new maximum size
     */
    public void lSetMaxSize(int maxSize) {
	// Only update native resource if it exists.
	if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
	    setMaxSize0(nativeId, maxSize);
	}

	lRequestInvalidate(true, true);
    }

    /**
     * Set new maximum size of the native widget.
     *
     * @param nativeId native resource id of this <code>Item</code>
     * @param maxSize new maximum size
     */
    private native void setMaxSize0(int nativeId, int maxSize);

    /**
     * Gets the current input position.
     *
     * @return the current caret position, <code>0</code> if at the beginning
     */
    public int lGetCaretPosition() {
	return (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID)
		? getCaretPosition0(nativeId)
		: tf.buffer.length();
    }

    /**
     * Gets the current input position from native widget.
     *
     * @param nativeId native resource id of this <code>Item</code>
     * @return current caret position
     */
    private native int getCaretPosition0(int nativeId);

    /**
     * Notifies L&amp;F that constraints have to be changed.
     */
    public void lSetConstraints() {

	// Only update native resource if it exists.
	if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
	    setConstraints0(nativeId, tf.constraints);
	}

	// note: int some implementation this method call may affect
	// the layout, and int this case such a call will be needed:
        // lRequestInvalidate(true, true);
    }

    /**
     * Set input constraints of the native widget.
     *
     * @param nativeId native resource id of this <code>Item</code>
     * @param constraints the new input constraints
     */
    private native void setConstraints0(int nativeId, int constraints);

    /**
     * Validate a given character array against a constraints.
     *
     * @param buffer a character array
     * @param constraints text input constraints
     *
     * @return <code>true</code> if constraints is met by the character array
     */
    public boolean lValidate(DynamicCharacterArray buffer, int constraints) {
	return TextPolicy.isValidString(buffer, constraints);
    }

    /**
     * Notifies L&amp;F that preferred initial input mode was changed.
     *
     * @param characterSubset a string naming a Unicode character subset,
     *                        or <code>null</code>
     */
    public void lSetInitialInputMode(String characterSubset) {
	// No visual impact
    }

     /**
      * Notifies item that it has been recently deleted
      * Traverse out the textFieldLF. 
     */
     public void itemDeleted() {
         if (lUseJavaPaint()) {
             NativeTextInput.hide();
         }
         uCallTraverseOut();
     }

    // *****************************************************
    //  Package private methods
    // *****************************************************
 
    /**
     * Called by event delivery to notify an <code>ItemLF</code> in current 
     * <code>FormLF</code> of a change in its peer state.
     *
     * @param hint any value means contents have changed in native
     *
     * @return always <code>true</code> to notify 
     *         <code>ItemStateListener</code>
     */
    boolean uCallPeerStateChanged(int hint) {
	return true;
    }

    /**
     * Paint-only TextField rendering used by the LCDUI migration demos.
     */
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

        if (tf.label != null && tf.label.length() > 0) {
            g.setColor(0x606060);
            g.setFont(labelFont);
            g.drawString(tf.label, contentX, y, Graphics.TOP | Graphics.LEFT);
            y += labelFont.getHeight() + JAVA_GAP;
        }

        int fieldHeight = lGetJavaPaintFieldHeight(inputFont);
        int fieldX = contentX;
        int fieldWidth = contentWidth;
        if (fieldWidth <= 0) {
            return;
        }

        boolean editable = (tf.constraints & TextField.UNEDITABLE) == 0;
        g.setColor(editable ? 0xffffff : 0xf1f1f1);
        g.fillRoundRect(fieldX, y, fieldWidth - 1, fieldHeight - 1,
                        JAVA_FIELD_RADIUS, JAVA_FIELD_RADIUS);
        g.setColor(hasFocus ? 0x4f8fd8 : 0x9a9a9a);
        g.drawRoundRect(fieldX, y, fieldWidth - 1, fieldHeight - 1,
                        JAVA_FIELD_RADIUS, JAVA_FIELD_RADIUS);
        if (hasFocus) {
            g.setColor(0xd7ecff);
            g.drawRoundRect(fieldX + 1, y + 1, fieldWidth - 3,
                            fieldHeight - 3, JAVA_FIELD_RADIUS,
                            JAVA_FIELD_RADIUS);
        }

        String text = lGetJavaPaintDisplayText();
        int textX = fieldX + JAVA_FIELD_PADDING;
        int textY = y + ((fieldHeight - inputFont.getHeight()) / 2);
        if (lIsJavaPaintPasswordMasked()) {
            textY += inputFont.getHeight() / 8;
        }
        int textWidth = fieldWidth - (JAVA_FIELD_PADDING * 2);
        if (textWidth < 0) {
            textWidth = 0;
        }
        g.setFont(inputFont);
        g.setColor(editable ? 0x202020 : 0x707070);
        if (lIsJavaPaintTextBox()) {
            lDrawClippedMultilineString(g, text, textX,
                                        y + JAVA_FIELD_PADDING,
                                        textWidth,
                                        fieldHeight - (JAVA_FIELD_PADDING * 2));
        } else {
            lDrawClippedString(g, text, textX, textY, textWidth);
        }

        y += fieldHeight + JAVA_GAP;
        g.setFont(hintFont);
        g.setColor(0x777777);
        lDrawClippedString(g, lGetJavaPaintConstraintText(), contentX, y,
                           contentWidth);
    }

    void uCallKeyPressed(int keyCode) {
        if (!lUseJavaPaint()) {
            return;
        }
        int dir = KeyConverter.getGameAction(keyCode);
        if (dir == Canvas.FIRE || keyCode == Constants.KEYCODE_SELECT) {
            lShowNativeTextInput();
        }
    }

    void lShowNativeTextInput() {
        if ((tf.constraints & TextField.UNEDITABLE) != 0) {
            return;
        }
        NativeTextInput.show(tf, tf.getString(), tf.constraints,
                             tf.buffer.capacity());
    }

    void uCallTraverseOut() {
        if (lUseJavaPaint()) {
            NativeTextInput.hide();
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
        if (tf.label != null && tf.label.length() > 0) {
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
        if (tf.label != null && tf.label.length() > 0) {
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
     * Determine if this <code>Item</code> should have a newline after it.
     *
     * @return <code>true</code> if it should have a newline after
     */
    boolean equateNLA() {
        if (super.equateNLA()) {
            return true;
        }

        return ((tf.layout & Item.LAYOUT_2) != Item.LAYOUT_2);
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

        return ((tf.layout & Item.LAYOUT_2) != Item.LAYOUT_2);
    }

    /**
     * Create native resource for current <code>TextField</code>.
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
					 tf.label,
					 (tf.owner instanceof TextBox ? 
					  -1 : tf.layout),
					 tf.buffer,
					 tf.constraints,
					 tf.initialInputMode);
    }

    /**
     * KNI function that create native resource for current 
     * <code>TextField</code>.
     *
     * @param ownerId Owner screen's native resource id 
     *                (<code>MidpDisplayable *</code>)
     * @param label label of the item
     * @param layout layout directive associated with this <code>Item</code>
     * @param buffer char array of the contents
     * @param constraints input constraints
     * @param initialInputMode suggested input mode on creation
     *
     * @return native resource id (<code>MidpItem *</code>) of this 
     *         <code>Item</code>
     */
    private native int createNativeResource0(int ownerId,
					     String label,
					     int layout,
					     DynamicCharacterArray buffer,
					     int constraints,
					     String initialInputMode);

    /**
     * Override <code>ItemLFImpl</code> method to sync with native resource
     * before hiding the native resource.
     */
    void lHideNativeResource() {
	lUpdateContents();
	super.lHideNativeResource();
    }

    /** <code>TextField</code> instance associated with this view. */
    TextField tf;

    private boolean lUseJavaPaint() {
        if (tf.owner instanceof TextBox) {
            return true;
        }
        return tf.owner instanceof Form;
    }

    private int lGetAvailableJavaPaintWidth() {
        if (tf.owner != null) {
            DisplayableLF ownerLF = tf.owner.getLF();
            if (ownerLF != null) {
                return ownerLF.lGetWidth();
            }
        }
        return Display.WIDTH;
    }

    private int lGetJavaPaintFieldHeight(Font font) {
        if (lIsJavaPaintTextBox()) {
            return (font.getHeight() * JAVA_TEXTBOX_MIN_LINES)
                + (JAVA_FIELD_PADDING * 2);
        }
        return font.getHeight() + (JAVA_FIELD_PADDING * 2);
    }

    private boolean lIsJavaPaintTextBox() {
        return tf.owner instanceof TextBox;
    }

    private String lGetJavaPaintDisplayText() {
        String value = tf.buffer == null ? "" : tf.buffer.toString();
        if (lIsJavaPaintPasswordMasked()) {
            StringBuffer masked = new StringBuffer();
            for (int i = 0; i < value.length(); i++) {
                masked.append('*');
            }
            return masked.toString();
        }
        return value;
    }

    private boolean lIsJavaPaintPasswordMasked() {
        return (tf.constraints & TextField.PASSWORD) != 0
            && tf.buffer != null
            && tf.buffer.length() > 0;
    }

    private String lGetJavaPaintConstraintText() {
        int base = tf.constraints & TextField.CONSTRAINT_MASK;
        String name;
        switch (base) {
        case TextField.EMAILADDR:
            name = "email";
            break;
        case TextField.NUMERIC:
            name = "numeric";
            break;
        case TextField.PHONENUMBER:
            name = "phone";
            break;
        case TextField.URL:
            name = "url";
            break;
        case TextField.DECIMAL:
            name = "decimal";
            break;
        case TextField.ANY:
        default:
            name = "any";
            break;
        }
        if ((tf.constraints & TextField.PASSWORD) != 0) {
            name += " password";
        }
        if ((tf.constraints & TextField.UNEDITABLE) != 0) {
            name += " locked";
        }
        return name + "  " + tf.buffer.length() + "/" + tf.buffer.capacity();
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

    private void lDrawClippedMultilineString(Graphics g, String text, int x,
                                             int y, int maxWidth,
                                             int maxHeight) {
        if (text == null || text.length() == 0
                || maxWidth <= 0 || maxHeight <= 0) {
            return;
        }

        Font font = g.getFont();
        int lineHeight = font.getHeight();
        int lineY = y;
        int start = 0;
        while (start <= text.length() && lineY + lineHeight <= y + maxHeight) {
            int nextBreak = text.indexOf('\n', start);
            int end = nextBreak == -1 ? text.length() : nextBreak;
            String line = text.substring(start, end);
            lDrawClippedString(g, line, x, lineY, maxWidth);
            lineY += lineHeight;
            if (nextBreak == -1) {
                break;
            }
            start = nextBreak + 1;
        }
    }

} // TextFieldLFImpl
