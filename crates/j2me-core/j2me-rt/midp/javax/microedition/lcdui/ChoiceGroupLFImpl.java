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

import com.sun.midp.configurator.Constants;
import javax.microedition.lcdui.ChoiceGroup.CGElement;

/**
 *  This is the look and feel implementation for ChoiceGroup.
 */
class ChoiceGroupLFImpl extends ItemLFImpl implements ChoiceGroupLF {
    private static final int JAVA_LABEL_CONTENT_GAP = 2;
    private static final int JAVA_ROW_GAP = 2;
    private static final int JAVA_ROW_HORIZONTAL_PAD = 4;
    private static final int JAVA_ROW_VERTICAL_PAD = 2;
    private static final int JAVA_INDICATOR_SIZE = 13;
    private static final int JAVA_RADIO_DOT_SIZE = 7;
    private static final int JAVA_CHECKBOX_RADIUS = 3;
    private static final int JAVA_INDICATOR_TEXT_GAP = 4;
    private static final int JAVA_IMAGE_TEXT_GAP = 3;
    private static final int JAVA_POPUP_HEIGHT_PAD = 4;
    private static final int JAVA_POPUP_RADIUS = 6;
    private static final int JAVA_POPUP_HORIZONTAL_INSET = 2;
    private static final int JAVA_POPUP_VERTICAL_PAD = 2;
    private static final int JAVA_POPUP_TEXT_LEFT_PAD = 5;
    private static final int JAVA_POPUP_ARROW_WIDTH = 7;

    /**
     * Creates ChoiceLF for the passed in ChoiceGroup.
     * @param choiceGroup - the ChoiceGroup object associated with this view
     */
    ChoiceGroupLFImpl(ChoiceGroup choiceGroup) {
        super(choiceGroup);

        cg = choiceGroup;

        if (cg.numOfEls > 0 && cg.choiceType != Choice.MULTIPLE) {
            selectedIndex = 0;
            cg.cgElements[selectedIndex].setSelected(true);
        }
    }

    // *******************************************************
    // ChoiceGroupLF implementation
    // ********************************************************

    /**
     * Notifies Look &amps; Feel that an element was inserted into the 
     * <code>ChoiceGroup</code> at the the elementNum specified.
     *
     * @param elementNum the index of the element where insertion occurred
     * @param stringPart the string part of the element to be inserted
     * @param imagePart the image part of the element to be inserted,
     * or <code>null</code> if there is no image part
     */
    public void lInsert(int elementNum, String stringPart, Image imagePart) {
        // make sure that there is a default selection
        if (cg.choiceType != Choice.MULTIPLE) {
            if (selectedIndex == -1) {
                selectedIndex = 0;
                cg.cgElements[selectedIndex].setSelected(true);
            } else if (elementNum < selectedIndex &&
                       nativeId == DisplayableLFImpl.INVALID_NATIVE_ID) {
                // an element was inserted before selectedIndex and
                // selectedIndex has to be updated
                selectedIndex++;
            }
        }

        // Only update native resource if it exists.
        if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
            ImageData imagePartData = null;

            if (imagePart != null) {
              imagePartData = imagePart.getImageData();
            }

            insert0(nativeId, elementNum, 
                    stringPart, imagePartData, 
                    cg.cgElements[elementNum].selected);
        }

        lRequestInvalidate(true, true);
    }

    /**
     * Notifies Look &amps; Feel that an element referenced by
     * <code>elementNum</code> was deleted in the corresponding
     * ChoiceGroup.
     *
     * @param elementNum the index of the deleted element
     */
    public void lDelete(int elementNum) {

        // adjust selected index
        if (cg.numOfEls == 0) {
            selectedIndex = -1;
        } else if (cg.choiceType != ChoiceGroup.MULTIPLE) {
            if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
                if (selectedIndex != -1 && selectedIndex < cg.numOfEls) {
                    cg.cgElements[selectedIndex].setSelected(false);
                }
                selectedIndex = getSelectedIndex0(nativeId);
            }

            if (elementNum < selectedIndex) {
                selectedIndex--;
            } else if (elementNum == selectedIndex &&
                       selectedIndex == cg.numOfEls) {
                // last element is selected and deleted - 
                // new last should be selected
                selectedIndex = cg.numOfEls - 1;
            }
            cg.cgElements[selectedIndex].setSelected(true);
        }

        if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
            delete0(nativeId, elementNum, selectedIndex);
        }

        lRequestInvalidate(true, true);
    }

    /**
     * Notifies Look &amps; Feel that all elements 
     * were deleted in the corresponding ChoiceGroup.
     */
    public void lDeleteAll() {
        // Only update native resource if it exists.
        if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
            deleteAll0(nativeId);
        }
        selectedIndex = -1;
        lRequestInvalidate(true, true);
    }

    /**
     * Notifies Look &amps; Fell that the <code>String</code> and 
     * <code>Image</code> parts of the
     * element referenced by <code>elementNum</code> were set in
     * the corresponding ChoiceGroup,
     * replacing the previous contents of the element.
     *
     * @param elementNum the index of the element set
     * @param stringPart the string part of the new element
     * @param imagePart the image part of the element, or <code>null</code>
     * if there is no image part
     */
    public void lSet(int elementNum, String stringPart, Image imagePart) {
        // Only update native resource if it exists.
        if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
            ImageData imagePartData = null;
            if (imagePart != null) {
              imagePartData = imagePart.getImageData();
            }

            // for selected value to be passed correctly to the 
            // newly created element we have to do the sync first
            // (alternatively we could rely on native to maintain
            // the selected state correctly)
            syncSelectedIndex();
            syncSelectedFlags();
            set0(nativeId, elementNum, 
                 stringPart, imagePartData, 
                 cg.cgElements[elementNum].selected);
        }
        lRequestInvalidate(true, true);
    }

    /**
     * Notifies Look &amps; Feel that an element was selected (or
     * deselected) in the corresponding ChoiceGroup.
     *
     * @param elementNum the number of the element. Indexing of the
     * elements is zero-based
     * @param selected the new state of the element <code>true=selected</code>,
     * <code>false=not</code> selected
     */
    public void lSetSelectedIndex(int elementNum, boolean selected) {
        if (cg.choiceType == Choice.MULTIPLE) {
            cg.cgElements[elementNum].setSelected(selected);
        } else {
            // selected item cannot be deselected in
            // EXCLUSIVE, IMPLICIT, POPUP ChoiceGroup
            if (!selected ||
                (/* choiceType != Choice.IMPLICIT && */
                 selectedIndex == elementNum)) {
                return;
            }

            if (selectedIndex != -1) {
                cg.cgElements[selectedIndex].setSelected(false);
            }
            selectedIndex = elementNum;
            cg.cgElements[selectedIndex].setSelected(true);
        }

        // Only update native resource if it exists.
        if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
            setSelectedIndex0(nativeId, elementNum, selected);
        }
    }

    /**
     * Notifies Look &amps; Feel that selected state was changed on
     * several elements in the corresponding MULTIPLE ChoiceGroup
     * (cannot be null).
     * @param selectedArray an array in which the method collect the
     * selection status
     */
    public void lSetSelectedFlags(boolean[] selectedArray) {
        for (int i = 0; i < cg.numOfEls; i++) {
            cg.cgElements[i].setSelected(selectedArray[i]);
        }

        // Only update native resource if it exists.
        if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
            setSelectedFlags0(nativeId, selectedArray, 
                              selectedArray.length);
        }
    }

    /**
     * Notifies Look &amps; Feel that a new text fit policy was set
     * in the corresponding ChoiceGroup.
     * @param fitPolicy preferred content fit policy for choice elements
     */
    public void lSetFitPolicy(int fitPolicy) {
        // Only update native resource if it exists.
        if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
            setFitPolicy0(nativeId, fitPolicy);
	    lRequestInvalidate(true, true);
        }
    }

    /**
     * Notifies Look &amps; Feel that a new font was set for an
     * element with the  specified elementNum in the 
     * corresponding ChoiceGroup.
     * @param elementNum the index of the element, starting from zero
     * @param font the preferred font to use to render the element
     */
    public void lSetFont(int elementNum, Font font) {
        // Only update native resource if it exists.
        if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
            setFont0(nativeId, elementNum, 
                     font.getFace(), font.getStyle(), font.getSize());
	    lRequestInvalidate(true, true);
        }
    }

    /**
     * Gets default font to render ChoiceGroup element if it was not
     * set by the application
     * @return - the font to render ChoiceGroup element if it was not 
     *           set by the app
     */
    public Font getDefaultFont() {
	return Theme.curContentFont;
    }

    public int lGetPreferredWidth(int h) {
        int width = lGetJavaPaintWidth();
        int available = lGetAvailableJavaPaintWidth();
        return width > available ? available : width;
    }

    public int lGetPreferredHeight(int w) {
        int width = w;
        if (width <= 0) {
            width = lGetPreferredWidth(-1);
        }
        return lGetJavaPaintHeight(width);
    }

    public int lGetMinimumWidth() {
        return lGetPreferredWidth(-1);
    }

    public int lGetMinimumHeight() {
        return lGetPreferredHeight(-1);
    }

    /**
     * Gets currently selected index 
     * @return currently selected index
     */
    public int lGetSelectedIndex() {
        return selectedIndex;
    }


    /**
     * Gets selected flags.(only elements corresponding to the 
     * elements are expected to be filled). ChoiceGroup sets the rest to
     * false
     * @param selectedArray_return to contain the results
     * @return the number of selected elements
     */
    public int lGetSelectedFlags(boolean[] selectedArray_return) {
        int countSelected = 0;
        for (int i = 0; i < cg.numOfEls; i++) {
            selectedArray_return[i] = cg.cgElements[i].selected;
            if (selectedArray_return[i]) {
                countSelected++;
            }
        }
        return countSelected;
    }


    /**
     * Determines if an element with a passed in index
     * is selected or not.
     * @param elementNum the index of an element in question
     * @return true if the element is selected, false - otherwise
     */
    public boolean lIsSelected(int elementNum) {
        return cg.cgElements[elementNum].selected;
    }

    // *****************************************************
    //  Package private methods
    // *****************************************************

    /**
     * Called by event delivery to notify an ItemLF in current FormLF
     * of a change in its peer state.
     *
     * @param hint index of the element whose selection status has changed
     * @return always true so ItemStateListener should be notified
     */
    boolean uCallPeerStateChanged(int hint) {
	// Any hint means selection has change
	// For types other than IMPLICIT List, notify itemStateListener.
	if (cg.choiceType != Choice.IMPLICIT) {
	    return true; // notify itemStateListener
	}

	// For IMPLICIT List, notify commandListener
	List list;
	CommandListener cl;
	Command cmd;

	synchronized (Display.LCDUILock) {
	    list = (List)cg.owner;

	    if (list.listener == null ||
		list.selectCommand == null ||
		cg.numOfEls == 0) {
		return false; // No itemStateListener to notify
	    }

	    cl = list.listener;
	    cmd = list.selectCommand;
	}
	
	try {
	    synchronized (Display.calloutLock) {
		cl.commandAction(cmd, list);
	    }
	} catch (Throwable thr) {
	    Display.handleThrowable(thr);
	}

	return false; // No itemStateListener to notify
    }

    /**
     * Determine if this Item should have a newline after it
     *
     * @return true if it should have a newline after
     */
    boolean equateNLA() {
        if (super.equateNLA()) {
            return true;
        }
        return ((cg.layout & Item.LAYOUT_2) != Item.LAYOUT_2);
    }


    /**
     * Determine if this Item should have a newline before it
     *
     * @return true if it should have a newline before
     */
    boolean equateNLB() {
        if (super.equateNLB()) {
            return true;
        }

        return ((cg.layout & Item.LAYOUT_2) != Item.LAYOUT_2);
    }

    /**
    /**
     * Override <code>ItemLFImpl</code> method to sync with native resource
     * before hiding the native resource. Selection of native resource will 
     * be preserved before the resource is hidden.
     */
    void lHideNativeResource() {
	// sync selected flags and selectedIndex
	// before any visible native resource is deleted.
	if (nativeId != DisplayableLFImpl.INVALID_NATIVE_ID) {
	    syncSelectedIndex();
	    syncSelectedFlags();
	}

	// Hide native resource
        super.lHideNativeResource();
    }

    /**
     * Creates and sets  native resource for current ChoiceGroup.
     * Override function in ItemLFImpl.
     * @param ownerId Owner screen's native resource id
     */
    void createNativeResource(int ownerId) {
        nativeId = createNativeResource0(ownerId, cg.label, 
					 (cg.owner instanceof List ? 
					  -1 : cg.layout),
                                         cg.choiceType, cg.fitPolicy, 
                                         cg.cgElements, cg.numOfEls, 
                                         selectedIndex);
    }

    void lCallPaint(Graphics g, int w, int h) {
        if (g == null) {
            return;
        }

        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        int y = 0;
        if (cg.label != null && cg.label.length() > 0) {
            g.setColor(0x606060);
            g.setFont(labelFont);
            g.drawString(cg.label, 0, y, Graphics.TOP | Graphics.LEFT);
            y += labelFont.getHeight() + JAVA_LABEL_CONTENT_GAP;
        }

        if (cg.numOfEls == 0) {
            return;
        }

        if (cg.choiceType == Choice.POPUP) {
            lPaintJavaPopup(g, w, y);
            return;
        }

        for (int i = 0; i < cg.numOfEls && y < h; i++) {
            int rowHeight = lGetJavaPaintRowHeight(i);
            if (lIsImplicitList()) {
                lPaintJavaImplicitListRowBackground(g, i, 0, y, w,
                                                    rowHeight);
            }
            lPaintJavaChoiceRow(g, i, 0, y, w, rowHeight);
            if (lIsImplicitList()) {
                lPaintJavaImplicitListRowSeparator(g, i, 0, y, w,
                                                   rowHeight);
            }
            y += rowHeight + JAVA_ROW_GAP;
        }
    }

    void uCallKeyPressed(int keyCode) {
        int dir = KeyConverter.getGameAction(keyCode);
        if (cg.numOfEls == 0) {
            return;
        }

        if (dir == Canvas.LEFT || dir == Canvas.RIGHT) {
            int next = selectedIndex;
            if (next < 0 || next >= cg.numOfEls) {
                next = 0;
            } else if (dir == Canvas.LEFT) {
                next--;
            } else {
                next++;
            }
            if (next < 0) {
                next = cg.numOfEls - 1;
            } else if (next >= cg.numOfEls) {
                next = 0;
            }
            lSetSelectedIndex(next, true);
        } else if (dir == Canvas.FIRE) {
            int index = selectedIndex;
            if (index < 0 || index >= cg.numOfEls) {
                index = 0;
            }
            if (cg.choiceType == Choice.MULTIPLE) {
                lSetSelectedIndex(index, !cg.cgElements[index].selected);
            } else {
                lSetSelectedIndex(index, true);
            }
        } else {
            return;
        }

        if (cg.owner instanceof Form) {
            ((Form)cg.owner).uCallItemStateChanged(cg);
        }
        lRequestInvalidate(false, false);
    }

    boolean lHandleJavaPaintKey(int dir) {
        if (cg.numOfEls == 0) {
            return false;
        }

        int oldFocus = lGetJavaPaintFocusedIndex();
        int nextFocus = oldFocus;
        if (cg.choiceType == Choice.POPUP && !javaPaintPopupOpen) {
            if (dir == Canvas.FIRE) {
                javaPaintPopupOpen = true;
                javaPaintFocusIndex = selectedIndex >= 0
                    && selectedIndex < cg.numOfEls ? selectedIndex : oldFocus;
                lRequestInvalidate(false, false);
                return true;
            }
            return false;
        }

        if (dir == Canvas.UP) {
            if (oldFocus <= 0) {
                return cg.choiceType == Choice.POPUP && javaPaintPopupOpen;
            }
            nextFocus = oldFocus - 1;
        } else if (dir == Canvas.DOWN) {
            if (oldFocus >= lGetJavaPaintLastFocusableIndex()) {
                return cg.choiceType == Choice.POPUP && javaPaintPopupOpen;
            }
            nextFocus = oldFocus + 1;
        } else if (dir == Canvas.LEFT) {
            nextFocus = oldFocus - 1;
            if (nextFocus < 0) {
                nextFocus = cg.numOfEls - 1;
            }
        } else if (dir == Canvas.RIGHT) {
            nextFocus = oldFocus + 1;
            if (nextFocus >= cg.numOfEls) {
                nextFocus = 0;
            }
        } else if (dir == Canvas.FIRE) {
            nextFocus = oldFocus;
        } else {
            return false;
        }

        if (dir != Canvas.FIRE) {
            javaPaintFocusIndex = nextFocus;
            if (lIsImplicitList()) {
                lSetSelectedIndex(javaPaintFocusIndex, true);
            }
        }

        if (dir == Canvas.FIRE) {
            if (cg.choiceType == Choice.MULTIPLE) {
                lSetSelectedIndex(javaPaintFocusIndex,
                                  !cg.cgElements[javaPaintFocusIndex].selected);
                lNotifyJavaPaintStateChanged();
            } else if (lIsImplicitList()) {
                lSetSelectedIndex(javaPaintFocusIndex, true);
                uCallPeerStateChanged(javaPaintFocusIndex);
            } else {
                lSetSelectedIndex(javaPaintFocusIndex, true);
                if (cg.choiceType == Choice.POPUP) {
                    javaPaintPopupOpen = false;
                }
                lNotifyJavaPaintStateChanged();
            }
        }

        lRequestInvalidate(false, false);
        return true;
    }

    int lGetJavaPaintFocusX() {
        if (lIsImplicitList()) {
            return 0;
        }
        return JAVA_ROW_HORIZONTAL_PAD;
    }

    int lGetJavaPaintFocusY() {
        int y = 0;
        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        if (cg.label != null && cg.label.length() > 0) {
            y += labelFont.getHeight() + JAVA_LABEL_CONTENT_GAP;
        }
        if (cg.choiceType == Choice.POPUP) {
            return y;
        }
        int focus = lGetJavaPaintFocusedIndex();
        for (int i = 0; i < focus; i++) {
            y += lGetJavaPaintRowHeight(i) + JAVA_ROW_GAP;
        }
        return y;
    }

    int lGetJavaPaintFocusWidth(int itemWidth) {
        if (lIsImplicitList()) {
            return itemWidth;
        }
        int width = itemWidth - (JAVA_ROW_HORIZONTAL_PAD * 2);
        return width > 0 ? width : itemWidth;
    }

    int lGetJavaPaintFocusHeight(int itemWidth) {
        if (cg.numOfEls == 0) {
            return 0;
        }
        int focus = lGetJavaPaintFocusedIndex();
        int height = lGetJavaPaintRowHeight(focus, itemWidth);
        if (cg.choiceType == Choice.POPUP) {
            height = lGetJavaPaintPopupCollapsedRowHeight(focus);
        }
        return height;
    }

    boolean lIsJavaPaintPopupOpen() {
        return cg.choiceType == Choice.POPUP && javaPaintPopupOpen;
    }

    boolean lIsJavaPaintFullWidthList() {
        return lIsImplicitList();
    }

    void lPaintJavaPopupOverlay(Graphics g, int itemX, int itemY,
                                int itemWidth, int contentY,
                                int screenHeight) {
        if (!lIsJavaPaintPopupOpen() || g == null || cg.numOfEls == 0) {
            return;
        }

        int overlayX = itemX + JAVA_ROW_HORIZONTAL_PAD;
        int overlayY = itemY + lGetJavaPaintPopupCollapsedHeight(itemWidth)
            + 1;
        int overlayWidth = itemWidth - (JAVA_ROW_HORIZONTAL_PAD * 2);
        int overlayHeight = lGetJavaPaintPopupOverlayHeight(itemWidth);
        if (overlayWidth <= 0 || overlayHeight <= 0) {
            return;
        }
        if (overlayY + overlayHeight > screenHeight - 1) {
            overlayY = screenHeight - overlayHeight - 1;
        }
        if (overlayY < contentY) {
            overlayY = contentY;
        }

        int oldClipX = g.getClipX();
        int oldClipY = g.getClipY();
        int oldClipW = g.getClipWidth();
        int oldClipH = g.getClipHeight();
        int tx = g.getTranslateX();
        int ty = g.getTranslateY();

        g.setClip(overlayX, overlayY, overlayWidth, overlayHeight);
        g.setColor(0xffffff);
        g.fillRoundRect(overlayX, overlayY, overlayWidth - 1,
                        overlayHeight - 1,
                        JAVA_POPUP_RADIUS, JAVA_POPUP_RADIUS);
        g.setColor(0x8c8c8c);
        g.drawRoundRect(overlayX, overlayY, overlayWidth - 1,
                        overlayHeight - 1,
                        JAVA_POPUP_RADIUS, JAVA_POPUP_RADIUS);

        g.translate(overlayX + 1, overlayY + 1);
        int rowY = 1;
        int rowWidth = overlayWidth - 2;
        int focus = lGetJavaPaintFocusedIndex();
        for (int i = 0; i < cg.numOfEls; i++) {
            int rowHeight = lGetJavaPaintRowHeight(i, rowWidth);
            if (i == focus) {
                g.setColor(0xd7ecff);
                g.fillRect(1, rowY, rowWidth - 2, rowHeight);
                g.setColor(0x6fa9df);
                g.drawRect(1, rowY, rowWidth - 3, rowHeight - 1);
            }
            lPaintJavaChoiceRow(g, i, 0, rowY, rowWidth, rowHeight);
            rowY += rowHeight + JAVA_ROW_GAP;
        }

        g.translate(tx - g.getTranslateX(), ty - g.getTranslateY());
        g.setClip(oldClipX, oldClipY, oldClipW, oldClipH);
    }

    // *****************************************************
    //  Private methods
    // *****************************************************

    private void lPaintJavaImplicitListRowBackground(Graphics g, int index,
                                                     int x, int y, int w,
                                                     int rowHeight) {
        if (index == lGetJavaPaintFocusedIndex()) {
            g.setColor(0xd7ecff);
            g.fillRect(x, y, w, rowHeight);
        }
    }

    private void lPaintJavaImplicitListRowSeparator(Graphics g, int index,
                                                    int x, int y, int w,
                                                    int rowHeight) {
        if (index >= cg.numOfEls - 1 || w <= 0) {
            return;
        }
        int lineY = y + rowHeight;
        g.setColor(0xd0d0d0);
        g.drawLine(x, lineY, x + w - 1, lineY);
    }

    private int lGetImplicitListIconColumnWidth() {
        if (!lIsImplicitList()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < cg.numOfEls; i++) {
            Image image = cg.cgElements[i].imageEl;
            if (image != null && image.getWidth() > width) {
                width = image.getWidth();
            }
        }
        return width;
    }

    private int lGetImplicitListIconColumnHeight() {
        if (!lIsImplicitList()) {
            return 0;
        }
        int height = 0;
        for (int i = 0; i < cg.numOfEls; i++) {
            Image image = cg.cgElements[i].imageEl;
            if (image != null && image.getHeight() > height) {
                height = image.getHeight();
            }
        }
        return height;
    }

    private void lPaintJavaImplicitListFallbackIcon(Graphics g, int x, int y,
                                                   int width, int height) {
        int size = width;
        if (height < size) {
            size = height;
        }
        if (size > 18) {
            size = 18;
        }
        if (size < 10) {
            size = 10;
        }
        int iconX = x + ((width - size) / 2);
        int iconY = y + ((height - size) / 2);
        g.setColor(0xe6e6e6);
        g.fillRect(iconX, iconY, size, size);
        g.setColor(0x9a9a9a);
        g.drawRect(iconX, iconY, size - 1, size - 1);
        g.setColor(0x6fa9df);
        g.fillRect(iconX + 3, iconY + 3, size - 6, size - 6);
    }

    /**
     * Read and save user selection from native resource.
     */
    private void syncSelectedIndex() {
        if (cg.choiceType != Choice.MULTIPLE) {
            int newSelectedIndex = getSelectedIndex0(nativeId);
            if (selectedIndex != newSelectedIndex) {
                if (selectedIndex != -1) {
                    cg.cgElements[selectedIndex].setSelected(false);
                }
                selectedIndex = newSelectedIndex;
                if (selectedIndex != -1) {
                    cg.cgElements[selectedIndex].setSelected(true);
                }
            }
        }
    }

    /**
     * Read and save user selection from native resource.
     */
    private void syncSelectedFlags() {
        if (cg.numOfEls > 0 && cg.choiceType == Choice.MULTIPLE) {
            boolean[] selectedArray_return = new boolean[cg.numOfEls];
            
            getSelectedFlags0(nativeId, selectedArray_return,
                              cg.numOfEls);
            
            for (int i = 0; i < cg.numOfEls; i++) {
                cg.cgElements[i].setSelected(selectedArray_return[i]);
            }
        }
    }

    private int lGetJavaPaintFocusedIndex() {
        if (cg.numOfEls == 0) {
            javaPaintFocusIndex = -1;
            return -1;
        }
        if (javaPaintFocusIndex < 0 || javaPaintFocusIndex >= cg.numOfEls) {
            if (cg.choiceType == Choice.MULTIPLE) {
                javaPaintFocusIndex = 0;
            } else if (selectedIndex >= 0 && selectedIndex < cg.numOfEls) {
                javaPaintFocusIndex = selectedIndex;
            } else {
                javaPaintFocusIndex = 0;
            }
        }
        return javaPaintFocusIndex;
    }

    private int lGetJavaPaintLastFocusableIndex() {
        if (cg.choiceType == Choice.POPUP && !javaPaintPopupOpen) {
            return 0;
        }
        return cg.numOfEls - 1;
    }

    private void lNotifyJavaPaintStateChanged() {
        if (cg.owner instanceof Form) {
            ((Form)cg.owner).uCallItemStateChanged(cg);
        }
    }

    private void lPaintJavaChoiceRow(Graphics g, int index, int x, int y,
                                     int w, int rowHeight) {
        ChoiceGroup.CGElement element = cg.cgElements[index];
        Font font = element.getFont();
        if (font == null) {
            font = getDefaultFont();
        }

        int contentX = x + JAVA_ROW_HORIZONTAL_PAD;
        int contentY = y + JAVA_ROW_VERTICAL_PAD;
        int contentHeight = rowHeight - (JAVA_ROW_VERTICAL_PAD * 2);
        if (contentHeight < JAVA_INDICATOR_SIZE) {
            contentHeight = JAVA_INDICATOR_SIZE;
        }

        int indicatorY = contentY
            + ((contentHeight - JAVA_INDICATOR_SIZE) / 2);
        if (indicatorY < y) {
            indicatorY = contentY;
        }
        if (!lIsImplicitList()) {
            lPaintJavaIndicator(g, contentX, indicatorY, element.selected);
            contentX += JAVA_INDICATOR_SIZE + JAVA_INDICATOR_TEXT_GAP;
        }
        Image image = element.imageEl;
        int implicitIconWidth = lGetImplicitListIconColumnWidth();
        if (lIsImplicitList() && implicitIconWidth > 0) {
            if (image != null) {
                int imageY = contentY
                    + ((contentHeight - image.getHeight()) / 2);
                if (imageY < contentY) {
                    imageY = contentY;
                }
                g.drawImage(image, contentX, imageY,
                            Graphics.TOP | Graphics.LEFT);
            } else {
                lPaintJavaImplicitListFallbackIcon(g, contentX, contentY,
                                                   implicitIconWidth,
                                                   contentHeight);
            }
            contentX += implicitIconWidth + JAVA_IMAGE_TEXT_GAP;
        } else if (image != null) {
            int imageY = contentY
                + ((contentHeight - image.getHeight()) / 2);
            if (imageY < contentY) {
                imageY = contentY;
            }
            g.drawImage(image, contentX, imageY, Graphics.TOP | Graphics.LEFT);
            contentX += image.getWidth() + JAVA_IMAGE_TEXT_GAP;
        }

        int textWidth = w - (contentX - x);
        if (textWidth < 0) {
            textWidth = 0;
        }
        g.setColor(Theme.getColor(Display.COLOR_FOREGROUND));
        g.setFont(font);
        if (cg.fitPolicy == Choice.TEXT_WRAP_ON
                && cg.choiceType != Choice.POPUP) {
            lDrawWrappedText(g, element.stringEl, font, contentX, contentY,
                             textWidth);
        } else {
            g.drawString(element.stringEl, contentX,
                         contentY
                         + ((contentHeight - font.getHeight()) / 2),
                         Graphics.TOP | Graphics.LEFT);
        }
    }

    private void lPaintJavaPopup(Graphics g, int w, int y) {
        int index = selectedIndex;
        if (index < 0 || index >= cg.numOfEls) {
            index = 0;
        }
        ChoiceGroup.CGElement element = cg.cgElements[index];
        Font font = element.getFont();
        if (font == null) {
            font = getDefaultFont();
        }
        int rowHeight = font.getHeight() + (JAVA_POPUP_VERTICAL_PAD * 2);
        int popupX = JAVA_POPUP_HORIZONTAL_INSET;
        int popupWidth = w - (JAVA_POPUP_HORIZONTAL_INSET * 2);
        if (popupWidth <= 0 || rowHeight <= 0) {
            return;
        }

        g.setColor(0xf2f2f2);
        g.fillRoundRect(popupX, y, popupWidth - 1, rowHeight - 1,
                        JAVA_POPUP_RADIUS, JAVA_POPUP_RADIUS);
        g.setColor(0x8c8c8c);
        g.drawRoundRect(popupX, y, popupWidth - 1, rowHeight - 1,
                        JAVA_POPUP_RADIUS, JAVA_POPUP_RADIUS);
        if (hasFocus && !javaPaintPopupOpen) {
            g.setColor(0x6fa9df);
            g.drawRoundRect(popupX + 1, y + 1, popupWidth - 3,
                            rowHeight - 3,
                            JAVA_POPUP_RADIUS, JAVA_POPUP_RADIUS);
        }

        g.setColor(Theme.getColor(Display.COLOR_FOREGROUND));
        g.setFont(font);
        g.drawString(element.stringEl,
                     popupX + JAVA_POPUP_TEXT_LEFT_PAD,
                     y + JAVA_POPUP_VERTICAL_PAD,
                     Graphics.TOP | Graphics.LEFT);

        int arrowX = popupX + popupWidth - JAVA_POPUP_TEXT_LEFT_PAD
            - JAVA_POPUP_ARROW_WIDTH;
        int arrowY = y + (rowHeight / 2) - 2;
        g.setColor(0x606060);
        g.drawLine(arrowX, arrowY, arrowX + JAVA_POPUP_ARROW_WIDTH, arrowY);
        g.drawLine(arrowX + 1, arrowY + 1,
                   arrowX + JAVA_POPUP_ARROW_WIDTH - 1, arrowY + 1);
        g.drawLine(arrowX + 2, arrowY + 2,
                   arrowX + JAVA_POPUP_ARROW_WIDTH - 2, arrowY + 2);
        g.drawLine(arrowX + 3, arrowY + 3,
                   arrowX + JAVA_POPUP_ARROW_WIDTH - 3, arrowY + 3);
    }

    private void lPaintJavaIndicator(Graphics g, int x, int y,
                                     boolean selected) {
        if (cg.choiceType == Choice.MULTIPLE) {
            if (selected) {
                g.setColor(0x2f80ed);
                g.fillRoundRect(x, y, JAVA_INDICATOR_SIZE,
                                JAVA_INDICATOR_SIZE,
                                JAVA_CHECKBOX_RADIUS,
                                JAVA_CHECKBOX_RADIUS);
                g.drawRoundRect(x, y, JAVA_INDICATOR_SIZE,
                                JAVA_INDICATOR_SIZE,
                                JAVA_CHECKBOX_RADIUS,
                                JAVA_CHECKBOX_RADIUS);
                g.setColor(0xffffff);
                g.drawLine(x + 3, y + 7, x + 5, y + 9);
                g.drawLine(x + 5, y + 9, x + 10, y + 4);
                g.drawLine(x + 3, y + 8, x + 5, y + 10);
                g.drawLine(x + 5, y + 10, x + 10, y + 5);
            } else {
                g.setColor(0xffffff);
                g.fillRoundRect(x, y, JAVA_INDICATOR_SIZE,
                                JAVA_INDICATOR_SIZE,
                                JAVA_CHECKBOX_RADIUS,
                                JAVA_CHECKBOX_RADIUS);
                g.setColor(0x707070);
                g.drawRoundRect(x, y, JAVA_INDICATOR_SIZE,
                                JAVA_INDICATOR_SIZE,
                                JAVA_CHECKBOX_RADIUS,
                                JAVA_CHECKBOX_RADIUS);
            }
        } else {
            g.setColor(0xffffff);
            g.fillArc(x, y, JAVA_INDICATOR_SIZE, JAVA_INDICATOR_SIZE,
                      0, 360);
            g.setColor(selected ? 0x2f80ed : 0x707070);
            g.drawArc(x, y, JAVA_INDICATOR_SIZE, JAVA_INDICATOR_SIZE,
                      0, 360);
            if (selected) {
                g.setColor(0x2f80ed);
                int dotOffset = (JAVA_INDICATOR_SIZE - JAVA_RADIO_DOT_SIZE)
                    / 2;
                g.fillArc(x + dotOffset, y + dotOffset,
                          JAVA_RADIO_DOT_SIZE, JAVA_RADIO_DOT_SIZE,
                          0, 360);
            }
        }
    }

    private int lGetJavaPaintWidth() {
        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        int width = 0;
        if (cg.label != null && cg.label.length() > 0) {
            width = labelFont.stringWidth(cg.label);
        }
        for (int i = 0; i < cg.numOfEls; i++) {
            ChoiceGroup.CGElement element = cg.cgElements[i];
            Font font = element.getFont();
            if (font == null) {
                font = getDefaultFont();
            }
            int rowWidth = JAVA_INDICATOR_SIZE + JAVA_INDICATOR_TEXT_GAP
                + font.stringWidth(element.stringEl);
            if (lIsImplicitList()) {
                rowWidth -= JAVA_INDICATOR_SIZE + JAVA_INDICATOR_TEXT_GAP;
            }
            int implicitIconWidth = lGetImplicitListIconColumnWidth();
            if (lIsImplicitList() && implicitIconWidth > 0) {
                rowWidth += implicitIconWidth + JAVA_IMAGE_TEXT_GAP;
            } else if (element.imageEl != null) {
                rowWidth += element.imageEl.getWidth() + JAVA_IMAGE_TEXT_GAP;
            }
            if (cg.choiceType == Choice.POPUP) {
                rowWidth += 12;
            }
            rowWidth += JAVA_ROW_HORIZONTAL_PAD * 2;
            if (rowWidth > width) {
                width = rowWidth;
            }
        }
        return width;
    }

    private int lGetJavaPaintHeight(int width) {
        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        int height = 0;
        if (cg.label != null && cg.label.length() > 0) {
            height += labelFont.getHeight() + JAVA_LABEL_CONTENT_GAP;
        }
        if (cg.numOfEls == 0) {
            return height;
        }
        if (cg.choiceType == Choice.POPUP) {
            int index = selectedIndex;
            if (index < 0 || index >= cg.numOfEls) {
                index = 0;
            }
            return height + lGetJavaPaintPopupCollapsedRowHeight(index);
        }
        for (int i = 0; i < cg.numOfEls; i++) {
            if (i > 0) {
                height += JAVA_ROW_GAP;
            }
            height += lGetJavaPaintRowHeight(i, width);
        }
        return height;
    }

    private int lGetJavaPaintRowHeight(int index) {
        return lGetJavaPaintRowHeight(index, lGetAvailableJavaPaintWidth());
    }

    private int lGetJavaPaintRowHeight(int index, int width) {
        ChoiceGroup.CGElement element = cg.cgElements[index];
        Font font = element.getFont();
        if (font == null) {
            font = getDefaultFont();
        }
        int textHeight = font.getHeight();
        if (cg.fitPolicy == Choice.TEXT_WRAP_ON
                && cg.choiceType != Choice.POPUP) {
            int textWidth = width;
            if (!lIsImplicitList()) {
                textWidth -= JAVA_INDICATOR_SIZE + JAVA_INDICATOR_TEXT_GAP;
            }
            int implicitIconWidth = lGetImplicitListIconColumnWidth();
            if (lIsImplicitList() && implicitIconWidth > 0) {
                textWidth -= implicitIconWidth + JAVA_IMAGE_TEXT_GAP;
            } else if (element.imageEl != null) {
                textWidth -= element.imageEl.getWidth()
                    + JAVA_IMAGE_TEXT_GAP;
            }
            textHeight = lGetWrappedLineCount(element.stringEl, font,
                                             textWidth) * font.getHeight();
        }
        int height = textHeight;
        int implicitIconHeight = lGetImplicitListIconColumnHeight();
        if (lIsImplicitList() && implicitIconHeight > height) {
            height = implicitIconHeight;
        } else if (element.imageEl != null
                && element.imageEl.getHeight() > height) {
            height = element.imageEl.getHeight();
        }
        if (JAVA_INDICATOR_SIZE > height) {
            height = JAVA_INDICATOR_SIZE;
        }
        return height + (JAVA_ROW_VERTICAL_PAD * 2);
    }

    private int lGetAvailableJavaPaintWidth() {
        if (cg.owner != null) {
            DisplayableLF ownerLF = cg.owner.getLF();
            if (ownerLF != null) {
                return ownerLF.lGetWidth();
            }
        }
        return Display.WIDTH;
    }

    private boolean lIsImplicitList() {
        return cg.choiceType == Choice.IMPLICIT && cg.owner instanceof List;
    }

    private int lGetJavaPaintPopupCollapsedHeight(int width) {
        Font labelFont = Font.getFont(Font.FACE_SYSTEM,
                                      Font.STYLE_PLAIN,
                                      Font.SIZE_SMALL);
        int height = 0;
        if (cg.label != null && cg.label.length() > 0) {
            height += labelFont.getHeight() + JAVA_LABEL_CONTENT_GAP;
        }
        int index = selectedIndex;
        if (index < 0 || index >= cg.numOfEls) {
            index = 0;
        }
        return height + lGetJavaPaintPopupCollapsedRowHeight(index);
    }

    private int lGetJavaPaintPopupCollapsedRowHeight(int index) {
        ChoiceGroup.CGElement element = cg.cgElements[index];
        Font font = element.getFont();
        if (font == null) {
            font = getDefaultFont();
        }
        return font.getHeight() + (JAVA_POPUP_VERTICAL_PAD * 2);
    }

    private int lGetJavaPaintPopupOverlayHeight(int width) {
        int height = 4;
        for (int i = 0; i < cg.numOfEls; i++) {
            if (i > 0) {
                height += JAVA_ROW_GAP;
            }
            height += lGetJavaPaintRowHeight(i, width);
        }
        return height;
    }

    private void lDrawWrappedText(Graphics g, String text, Font font, int x,
                                  int y, int width) {
        if (text == null || text.length() == 0) {
            return;
        }
        if (width <= 0) {
            g.drawString(text, x, y, Graphics.TOP | Graphics.LEFT);
            return;
        }

        int lineStart = 0;
        int lastBreak = -1;
        int lineWidth = 0;
        int lineY = y;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\n') {
                lastBreak = i;
            }
            if (ch == '\n') {
                g.drawString(text.substring(lineStart, i), x, lineY,
                             Graphics.TOP | Graphics.LEFT);
                lineY += font.getHeight();
                lineStart = i + 1;
                lastBreak = -1;
                lineWidth = 0;
                continue;
            }

            lineWidth += font.charWidth(ch);
            if (lineWidth > width && i > lineStart) {
                int breakAt = lastBreak >= lineStart ? lastBreak : i;
                g.drawString(text.substring(lineStart, breakAt), x, lineY,
                             Graphics.TOP | Graphics.LEFT);
                lineY += font.getHeight();
                lineStart = breakAt;
                while (lineStart < text.length()
                        && text.charAt(lineStart) == ' ') {
                    lineStart++;
                }
                i = lineStart - 1;
                lastBreak = -1;
                lineWidth = 0;
            }
        }
        if (lineStart < text.length()) {
            g.drawString(text.substring(lineStart), x, lineY,
                         Graphics.TOP | Graphics.LEFT);
        }
    }

    private int lGetWrappedLineCount(String text, Font font, int width) {
        if (text == null || text.length() == 0) {
            return 1;
        }
        if (width <= 0) {
            return 1;
        }

        int lines = 1;
        int lineStart = 0;
        int lastBreak = -1;
        int lineWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\n') {
                lastBreak = i;
            }
            if (ch == '\n') {
                lines++;
                lineStart = i + 1;
                lastBreak = -1;
                lineWidth = 0;
                continue;
            }
            lineWidth += font.charWidth(ch);
            if (lineWidth > width && i > lineStart) {
                int breakAt = lastBreak >= lineStart ? lastBreak : i;
                lines++;
                lineStart = breakAt;
                while (lineStart < text.length()
                        && text.charAt(lineStart) == ' ') {
                    lineStart++;
                }
                i = lineStart - 1;
                lastBreak = -1;
                lineWidth = 0;
            }
        }
        return lines;
    }

    /**
     * KNI function that creates native resource for current ChoiceGroup.
     * @param ownerId Owner screen's native resource id (MidpDisplayable *)
     * @param label string to be used as label for this ChoiceGroup
     * @param layout layout directive associated with this <code>Item</code>
     * @param choiceType should be EXCLUSIVE, MULTIPLE, IMPLICIT, POPUP
     * @param fitPolicy should be TEXT_WRAP_DEFAULT, TEXT_WRAP_ON, or
     *                    TEXT_WRAP_OFF
     * @param cgElements array of CGElement that stores such data as
     *                   image, text, font, selection state per element
     * @param numChoices number of valid elements in cgElements array
     * @param selectedIndex index of a currently selected element
     *                      (has no meaning for MULTIPLE ChoiceGroup)
     * @return native resource id (MidpItem *) of this ChoiceGroup
     */
    private native int createNativeResource0(int ownerId, String label, 
					     int layout,
                                             int choiceType, int fitPolicy,
                                             CGElement []cgElements,
                                             int numChoices,
                                             int selectedIndex);


    /**
     * KNI function that notifies native resource of a new element
     * inserted prior to the element specified
     * 
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     * @param elementNum the index of an element where insertion is to occur
     * @param stringPart the string part of the element to be inserted
     * @param imagePart the image part of the element to be inserted
     * @param selected the selected state of the element to be inserted
     */
    private native void insert0(int nativeId, int elementNum, 
                                String stringPart, ImageData imagePart,
                                boolean selected);

    /**
     * KNI function that notifies native resource of a specified element
     * being deleted in the corresponding ChoiceGroup.
     *
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     * @param elementNum the index of an element to be deleted
     * @param selectedIndex the index of an element to be selected after the
     *        deletion is done (has no meaning for MULTIPLE ChoiceGroup)
     */
    private native void delete0(int nativeId, int elementNum, 
                                int selectedIndex); 

    /**
     * KNI function that notifies native resource that all elements were
     * deleted in the corresponding ChoiceGroup.
     *
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     */
    private native void deleteAll0(int nativeId);

    /**
     * KNI function that notifies native resource of a specified element
     * being set in the corresponding ChoiceGroup.
     *
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     * @param elementNum the index of an element to be set
     * @param stringPart the string part of the element to be set
     * @param imagePart the image part of the element to be set
     * @param selected the selected state of the element to be set
     */
    private native void set0(int nativeId, int elementNum, 
                             String stringPart, ImageData imagePart, 
                             boolean selected);

    /**
     * KNI function that notifies native resource of an element's new selected
     * state in the corresponding ChoiceGroup.
     *
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     * @param elementNum the index of an element which selected state changed
     * @param selected the new selected state
     */
    private native void setSelectedIndex0(int nativeId, int elementNum,
                                          boolean selected);
    /**
     * KNI function that notifies native resource of new selected
     * states in the corresponding ChoiceGroup.
     *
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     * @param selectedArray array with new selected states
     * @param numSelectedArray number of elements in selectedArray to be used
     */
    private native void setSelectedFlags0(int nativeId, 
                                          boolean []selectedArray,
                                          int numSelectedArray);
    /**
     * KNI function that notifies native resource of fit policy change
     * in the corresponding ChoiceGroup.
     *
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     * @param fitPolicy new fit policy (can be TEXT_WRAP_OFF, TEXT_WRAP_ON,
     *                  or TEXT_WRAP_DEFAULT)
     */
    private native void setFitPolicy0(int nativeId, int fitPolicy);

    /**
     * KNI function that notifies native resource of an element's new font
     * setting in the corresponding ChoiceGroup.
     *
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     * @param elementNum the index of an element which font has changed
     * @param face of the newly set font
     * @param style of the newly set font 
     * @param size of newly set font
     */
    private native void setFont0(int nativeId, int elementNum,
                                 int face, int style, int size);

    /**
     * KNI function that gets index of a currently selected index from 
     * the ChoiceGroup's native resource.
     *
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     * @return index of the currently selected element
     */
    private native int getSelectedIndex0(int nativeId);

    /**
     * KNI function that queries the state of all elements in the native
     * resource and returns it in the passed in selectedArray array.
     *
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     * @param selectedArray to contain the results
     * @param numOfEls number of elements in selectedArray
     * @return the number of elements selected in the native resource
     */
    private native int getSelectedFlags0(int nativeId, 
                                         boolean[] selectedArray,
                                         int numOfEls);
    /**
     * KNI function that queries the state of an element in the native
     * resource
     *
     * @param nativeId native resource id (MidpItem *) of this ChoiceGroup
     * @param elementNum the index of an element which state is queried
     * @return the current state of an element in the nativer resource
     */
    private native boolean isSelected0(int nativeId, int elementNum);

    /** ChoiceGroup associated with this ChoiceGroupLF. */
    ChoiceGroup cg;

    /**
     * The currently selected index of this ChoiceGroup (-1 by default).
     */
    int selectedIndex = -1;

    private int javaPaintFocusIndex = -1;

    private boolean javaPaintPopupOpen;
}
