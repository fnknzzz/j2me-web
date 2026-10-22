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
 * Look and feel implementation for <code>StringItem</code> using 
 * platform widget.
 */
class StringItemLFImpl extends ItemLFImpl implements StringItemLF {
    private static final int JAVA_BUTTON_HORIZONTAL_INSET = 1;
    private static final int JAVA_BUTTON_VERTICAL_PAD = 2;
    private static final int JAVA_BUTTON_RADIUS = 8;
        
    /**
     * Creates a look&amp;feel for a <code>StringItem</code>.
     *
     * @param strItem The <code>StringItem</code> associated with this 
     *                look&amp;feel
     */
    StringItemLFImpl(StringItem strItem) {
        
        super(strItem);
        
        this.strItem = strItem;

        // when no commands are added, the actual appearance
        // is PLAIN; the actual appearance will be the same
        // as appearance set in StringItem if a command is added
        // to this StringItem
        appearanceMode = Item.PLAIN;
    }


    // *****************************************************
    //  Public methods (StringItemLF interface impl)
    // *****************************************************

    /**
     * Notifies L&amp;F of a string change in the corresponding 
     * <code>StringItem</code>.
     *
     * @param str the new string set in the <code>StringItem</code>
     */
    public void lSetText(String str) {
        // Only update native resource if it exists.
        if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
            setContent0(nativeId, str, appearanceMode);
        }

        lRequestInvalidate(true, true);
    }


    /**
     * Notifies L&amp;F of a font change in the corresponding 
     * <code>StringItem</code>.
     *
     * @param font the new font set in the <code>StringItem</code>
     */
    public void lSetFont(Font font) {
        // Only update native resource if it exists.
        if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
            setFont0(nativeId, 
                     font.getFace(), font.getStyle(), font.getSize());
        }

        lRequestInvalidate(true, true);
    }

    /**
     * Gets default font to render text in StringItem if it was not
     * set by the application.
     * @return - the font to render text if it was not set by the app
     */
    public Font getDefaultFont() {
        return Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    /**
     * Return Java paint preferred width without querying the native peer.
     */
    public int lGetPreferredWidth(int h) {
        int width = lGetNaturalJavaPaintWidth();
        int available = lGetAvailableJavaPaintWidth();
        if (width > available) {
            width = available;
        }
        return width;
    }

    /**
     * Return Java paint preferred height for the width chosen by Form layout.
     */
    public int lGetPreferredHeight(int w) {
        if (w == -1) {
            w = lGetPreferredWidth(-1);
        }
        return lGetJavaPaintHeight(w);
    }

    /**
     * Match phoneME's StringItem minimum-width behavior: two W characters.
     */
    public int lGetMinimumWidth() {
        int minWidth = getJavaPaintFont().charWidth('W') * 2;
        int available = lGetAvailableJavaPaintWidth();
        return minWidth > available ? available : minWidth;
    }

    public int lGetMinimumHeight() {
        return getJavaPaintFont().getHeight();
    }

    /**
     * Notifies L&amp;F of a command addition in the corresponding 
     * <code>StringItem</code>.
     *
     * @param cmd the newly added command
     * @param i the index of the added command in the <code>StringItem</code>'s
     *          commands[] array
     */
    public void lAddCommand(Command cmd, int i) {
        super.lAddCommand(cmd, i);

        if ((strItem.numCommands >= 1) && (appearanceMode == Item.PLAIN)) {
            appearanceMode = strItem.appearanceMode == Item.BUTTON ?
                             Item.BUTTON : Item.HYPERLINK;
            if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
                setContent0(nativeId, strItem.str, appearanceMode);
            }
            lRequestInvalidate(true, true);
        }
    }

    /**
     * Notifies L&amp;F of a command removal in the corresponding 
     * <code>StringItem</code>.
     *
     * @param cmd the newly removed command
     * @param i the index of the removed command in the 
     *          <code>StringItem</code>'s commands[] array
     */
    public void lRemoveCommand(Command cmd, int i) {
        super.lRemoveCommand(cmd, i);

        // restore the value of the original appearanceMode
        if (strItem.numCommands < 1) {
            appearanceMode = Item.PLAIN;
            if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
                setContent0(nativeId, strItem.str, appearanceMode);
            }
            lRequestInvalidate(true, true);
        }
    }

    // *****************************************************
    //  Package private methods
    // *****************************************************

    /**
     * Determine if this <code>Item</code> should have a newline before it.
     *
     * @return <code>true</code> if it should have a newline before
     */
    boolean equateNLB() {
        String label = strItem.label;
        String str   = strItem.str;

        // If label starts with a \n, put this StringItem on a newline
        if (label != null && label.length() > 0) {
            if (label.charAt(0) == '\n') {
                return true;
            }
        } else if (str != null && str.length() > 0) {
            // If there is no label and our content starts with a \n,
            // this StringItem starts on a newline
            if (str.charAt(0) == '\n') {
                return true;
            }
        } else {
            // empty StringItem
            return false;
        }
        
        if ((strItem.layout & Item.LAYOUT_2) == Item.LAYOUT_2) {
            return ((strItem.layout & Item.LAYOUT_NEWLINE_BEFORE)
                    == Item.LAYOUT_NEWLINE_BEFORE);
        }
        
        // LAYOUT_2 was not set, hence we need to provide backward 
        // compatibility with MIDP1.0 where any StringItem with a 
        // non-null label would go on a new line.
        return label != null && label.length() > 0;
    }
    
    /**
     * Determine if this <code>Item</code> should have a newline after it.
     *
     * @return <code>true</code> if it should have a newline after
     */
    boolean equateNLA() {
        
        String label = item.label;
        String str = strItem.str;

        // If content ends with a \n, there is a newline after 
        // this StringItem
        if (str != null && str.length() > 0) {
            if (str.charAt(str.length() - 1) == '\n') {
                return true;
            }
        } else if (label != null && label.length() > 0) {
            // If there is no content and our label ends with a \n, 
            // there is a newline after this StringItem
            if (label.charAt(label.length() - 1) == '\n') {
                return true;
            }
        } else {
            // empty StringItem
            return false;
        }
        
        if ((strItem.layout & Item.LAYOUT_2) == Item.LAYOUT_2) {
            return ((item.layout & Item.LAYOUT_NEWLINE_AFTER)
                    == Item.LAYOUT_NEWLINE_AFTER);
        }
        return false;
    }

    /**
     * Called by event delivery to notify an <code>ItemLF</code> in current 
     * <code>FormLF</code> of a change in its peer state.
     * Handle special gesture of default command.
     *
     * @param hint <code>-1</code> signals that user performed the 
     *             special gesture of default command
     *
     * @return always <code>false</code> so <code>ItemStateListener</code> 
     *         will not be notified
     */
    boolean uCallPeerStateChanged(int hint) { 
        // activate default command if hint is -1
        if (hint == -1) {

            Command defaultCommand;
            ItemCommandListener commandListener;

            synchronized (Display.LCDUILock) {

                defaultCommand  = strItem.defaultCommand;
                commandListener = strItem.commandListener;
            }

            if (defaultCommand != null && commandListener != null) {

                // Protect from any unexpected application exceptions
                try {
                    synchronized (Display.calloutLock) {
                        commandListener.commandAction(defaultCommand, strItem);
                    }
                } catch (Throwable thr) {
                    Display.handleThrowable(thr);
                }
            }
        }

        // Indicate to Form to not notify ItemStateListener
        return false;
    }

    /**
     * Paint this StringItem through Java Graphics. Native peer state is still
     * kept for layout and input, but text rendering can flow through the shared
     * command buffer.
     */
    void lCallPaint(Graphics g, int w, int h) {
        if (g == null) {
            return;
        }

        Font font = getJavaPaintFont();
        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        int inset = lGetJavaPaintInset();
        int contentX = inset;
        int contentWidth = w - (inset * 2);
        if (contentWidth < 0) {
            contentWidth = 0;
        }
        int y = inset;
        boolean commandAppearance = appearanceMode == Item.BUTTON
            || appearanceMode == Item.HYPERLINK;

        if (commandAppearance
                && strItem.label != null && strItem.label.length() > 0) {
            g.setColor(0x606060);
            g.setFont(labelFont);
            g.drawString(strItem.label, 0, 0, Graphics.TOP | Graphics.LEFT);
            y += labelFont.getHeight();
        }

        if (appearanceMode == Item.BUTTON) {
            int buttonHeight = lGetJavaPaintButtonHeight(font);
            if (buttonHeight <= 0) {
                return;
            }
            int buttonX = JAVA_BUTTON_HORIZONTAL_INSET;
            int buttonWidth = w - (JAVA_BUTTON_HORIZONTAL_INSET * 2);
            if (buttonWidth <= 0) {
                return;
            }
            g.setColor(0xe8e8e8);
            g.fillRoundRect(buttonX, y, buttonWidth - 1, buttonHeight - 1,
                            JAVA_BUTTON_RADIUS, JAVA_BUTTON_RADIUS);
            g.setColor(0x8c8c8c);
            g.drawRoundRect(buttonX, y, buttonWidth - 1, buttonHeight - 1,
                            JAVA_BUTTON_RADIUS, JAVA_BUTTON_RADIUS);
        }

        int textX = contentX;

        if (!commandAppearance
                && strItem.label != null && strItem.label.length() > 0) {
            g.setColor(0x606060);
            g.setFont(labelFont);
            g.drawString(strItem.label, textX, y, Graphics.TOP | Graphics.LEFT);
            y += labelFont.getHeight();
        }

        if (strItem.str != null && strItem.str.length() > 0 && y < h) {
            if (appearanceMode == Item.BUTTON) {
                int buttonHeight = lGetJavaPaintButtonHeight(font);
                int textY = y + ((buttonHeight - font.getHeight()) / 2);
                if (textY < y) {
                    textY = y;
                }
                g.setColor(Theme.getColor(Display.COLOR_FOREGROUND));
                g.setFont(font);
                g.drawString(strItem.str,
                             JAVA_BUTTON_HORIZONTAL_INSET
                             + ((w - (JAVA_BUTTON_HORIZONTAL_INSET * 2)) / 2),
                             textY,
                             Graphics.TOP | Graphics.HCENTER);
            } else if (appearanceMode == Item.HYPERLINK) {
                g.setColor(0x0645ad);
                g.setFont(font);
                lDrawWrappedText(g, strItem.str, font, textX, y,
                                 Graphics.TOP | Graphics.LEFT,
                                 contentWidth, true);
            } else {
                g.setColor(Theme.getColor(Display.COLOR_FOREGROUND));
                g.setFont(font);
                lDrawWrappedText(g, strItem.str, font, textX, y,
                                 Graphics.TOP | Graphics.LEFT,
                                 contentWidth, false);
            }
        }
    }

    int lGetJavaPaintHeight(int width) {
        Font font = getJavaPaintFont();
        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        int inset = lGetJavaPaintInset();
        int lines = 0;
        int height = inset * 2;
        boolean hasLabel = strItem.label != null && strItem.label.length() > 0;
        boolean hasText = strItem.str != null && strItem.str.length() > 0;
        boolean commandAppearance = appearanceMode == Item.BUTTON
            || appearanceMode == Item.HYPERLINK;
        if (appearanceMode == Item.BUTTON) {
            if (hasLabel) {
                lines++;
                height += labelFont.getHeight();
            }
            if (hasText) {
                lines++;
                height += lGetJavaPaintButtonHeight(font);
            }
        } else {
            if (hasLabel) {
                lines++;
                height += labelFont.getHeight();
            }
            if (hasText) {
                lines++;
                int contentWidth = width - (inset * 2);
                height += lGetWrappedLineCount(strItem.str, font, contentWidth)
                    * font.getHeight();
            }
        }
        if (appearanceMode == Item.HYPERLINK && hasText) {
            height += inset * 2;
        }
        if (lines == 0) {
            height += font.getHeight();
        }
        return height;
    }

    int lGetJavaPaintHeight() {
        int lockedWidth = strItem.lockedWidth > 0 ? strItem.lockedWidth : -1;
        return lGetJavaPaintHeight(lockedWidth);
    }

    private Font getJavaPaintFont() {
        if (strItem.font != null) {
            return strItem.font;
        }
        return getDefaultFont();
    }

    private int lGetJavaPaintInset() {
        return appearanceMode == Item.BUTTON ? 3 : 0;
    }

    private int lGetJavaPaintButtonHeight(Font font) {
        return font.getHeight() + (JAVA_BUTTON_VERTICAL_PAD * 2);
    }

    private void lDrawWrappedText(Graphics g, String text, Font font,
                                  int x, int y, int anchor, int maxWidth,
                                  boolean underline) {
        lDrawWrappedText(g, text, font, x, y, anchor, maxWidth, maxWidth, 0,
                         underline, font.getHeight());
    }

    private void lDrawWrappedText(Graphics g, String text, Font font,
                                  int x, int y, int anchor, int maxWidth,
                                  int firstLineWidth, int firstLineXOffset,
                                  boolean underline, int lineHeight) {
        int start = 0;
        boolean firstLine = true;
        while (start < text.length()) {
            int lineWidth = firstLine ? firstLineWidth : maxWidth;
            int lineXOffset = firstLine ? firstLineXOffset : 0;
            if (firstLine && lineWidth <= 0 && lineXOffset > 0) {
                y += lineHeight;
                firstLine = false;
                continue;
            }
            int next = lNextWrappedLineEnd(text, start, font, lineWidth);
            String line = text.substring(start, next);
            if (line.length() > 0) {
                g.drawString(line, x + lineXOffset, y, anchor);
                if (underline) {
                    int textWidth = font.stringWidth(line);
                    int lineX = x + lineXOffset;
                    int lineY = y + lineHeight - 2;
                    g.drawLine(lineX, lineY, lineX + textWidth, lineY);
                }
            }
            y += lineHeight;
            start = next;
            firstLine = false;
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
        }
    }

    private int lGetWrappedLineCount(String text, Font font, int maxWidth) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        int lines = 0;
        int start = 0;
        while (start < text.length()) {
            int next = lNextWrappedLineEnd(text, start, font, maxWidth);
            lines++;
            start = next;
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
        }
        return lines;
    }

    private int lGetWrappedLineCount(String text, Font font, int firstLineWidth,
                                     int maxWidth) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        int lines = 0;
        int start = 0;
        boolean firstLine = true;
        while (start < text.length()) {
            int width = firstLine ? firstLineWidth : maxWidth;
            if (firstLine && width <= 0) {
                lines++;
                firstLine = false;
                continue;
            }
            int next = lNextWrappedLineEnd(text, start, font, width);
            lines++;
            start = next;
            firstLine = false;
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
        }
        return lines;
    }

    private int lNextWrappedLineEnd(String text, int start, Font font,
                                    int maxWidth) {
        if (maxWidth <= 0) {
            return text.length();
        }

        int best = -1;
        int lastSpace = -1;
        for (int i = start + 1; i <= text.length(); i++) {
            char ch = text.charAt(i - 1);
            if (ch == ' ') {
                lastSpace = i - 1;
            }

            int width = font.substringWidth(text, start, i - start);
            if (width <= maxWidth) {
                best = i;
                continue;
            }

            if (lastSpace > start) {
                return lastSpace;
            }
            if (best > start) {
                return best;
            }
            return i;
        }
        return text.length();
    }

    private int lGetNaturalJavaPaintWidth() {
        Font font = getJavaPaintFont();
        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        int inset = lGetJavaPaintInset();
        int labelWidth = 0;
        int textWidth = 0;
        if (strItem.label != null && strItem.label.length() > 0) {
            labelWidth = labelFont.stringWidth(strItem.label);
        }
        if (strItem.str != null && strItem.str.length() > 0) {
            textWidth = font.stringWidth(strItem.str);
        }
        int width;
        if (labelWidth > 0 && textWidth > 0 && appearanceMode == Item.PLAIN) {
            width = labelWidth + font.charWidth(' ') + textWidth;
        } else {
            width = labelWidth > textWidth ? labelWidth : textWidth;
        }
        return width + (inset * 2);
    }

    private int lGetAvailableJavaPaintWidth() {
        if (strItem.owner != null) {
            DisplayableLF ownerLF = strItem.owner.getLF();
            if (ownerLF != null) {
                return ownerLF.lGetWidth();
            }
        }
        return Display.WIDTH;
    }
    
    /**
     * Create native resource for current <code>StringItem</code>.
     * Override function in <code>ItemLFImpl</code>.
     *
     * @param ownerId Owner screen's native resource id
     */
    void createNativeResource(int ownerId) {
        Font font = strItem.font == null ? getDefaultFont() : strItem.font;
        nativeId = createNativeResource0(ownerId,
                                         strItem.label, strItem.layout,
                                         strItem.str, 
                                         appearanceMode,
                                         font.getFace(), font.getStyle(),
                                         font.getSize());
    }

    /**
     * KNI function that create native resource for current 
     * <code>StringItem</code>.
     *
     * @param ownerId Owner screen's native resource id 
     *                (<code>MidpDisplayable *</code>)
     * @param label label to be used for this <code>Item</code>
     * @param layout layout directive associated with this <code>Item</code>
     * @param text text to be used for this <code>StringItem</code>
     * @param appearanceMode should be <code>PLAIN</code>, 
     *                       <code>HYPERLINK</code> or <code>BUTTON</code>
     * @param face face of the font used for rendering <code>StringItem</code>
     *             content
     * @param style style of the font used for rendering <code>StringItem</code>
     *              content
     * @param size size of the font used for rendering <code>StringItem</code>
     *             content
     * 
     * @return native resource id (<code>MidpItem *</code>) of this 
     *         <code>StringItem</code>
     */
    private native int createNativeResource0(int ownerId,
                                             String label, 
                                             int layout,
                                             String text,
                                             int appearanceMode, 
                                             int face,
                                             int style,
                                             int size);


    /**
     * KNI function that sets text on the native resource corresponding
     * to the current <code>StringItem</code>.
     *
     * @param nativeId native resource id for this item
     * @param text new text set on the current <code>StringItem</code>
     * @param appearanceMode the appearance mode of the text passed in
     */
    private native void setContent0(int nativeId, String text, 
                                    int appearanceMode);
    

    /**
     * KNI function that sets font on the native resource corresponding
     * to the current <code>StringItem</code>.
     *
     * @param nativeId native resource id for this item
     * @param face face of the new font
     * @param style style of the new font
     * @param size size of the new font
     */
    private native void setFont0(int nativeId, int face, int style, int size);


    /** 
     * The <code>StringItem</code> associated with this view.
     */
    private StringItem strItem;

    /**
     * The appearance mode.
     * The actual appearance of a <code>StringItem</code> could be different to
     * the one set in <code>StringItem</code>. A <code>StringItem</code> 
     * created with <code>PLAIN</code> appearance will look like a 
     * <code>HYPERLINK</code> if commands were added. 
     */
    private int appearanceMode;

}
