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

import com.sun.midp.i18n.Resource;
import com.sun.midp.i18n.ResourceConstants;
import com.sun.midp.util.ResourceHandler;

import java.util.Timer;
import java.util.TimerTask;

import com.sun.midp.security.SecurityToken;
import com.sun.midp.security.SecurityInitializer;
import com.sun.midp.security.ImplicitlyTrustedClass;

/**
 * Look &amp; Feel implementation of <code>Alert</code> based on
 * platform widget.
 */
class AlertLFImpl extends DisplayableLFImpl implements AlertLF {

    /**
     * Creates an <code>AlertLF</code> for the passed in <code>Alert</code>
     * instance.
     * @param a The <code>Alert</code> associated with this look &amp; feel
     */
    AlertLFImpl(Alert a) {
        super(a);
        alert = a;
    }

    // ************************************************************
    //  public methods - AlertLF interface implementation
    // ************************************************************

    /**
     * Determines if <code>Alert</code> associated with this view is modal.
     *
     * @return true if this <code>AlertLF</code> should be displayed as modal
     */
    public boolean lIsModal() {
        if (alert.numCommands > 1) {
            return true;
        }

        if (isContentScroll < 0) {
            layout();
        }

        return (isContentScroll == 1);
    }

    /**
     * Gets default timeout for the <code>Alert</code> associated with
     * this view.
     *
     * @return the default timeout
     */
    public int lGetDefaultTimeout() {
        return DEFAULT_TIMEOUT;
    }

    /**
     * Return the command that should be mapped to
     * <code>Alert.DISMISS_COMMAND</code>.
     *
     * @return command that maps to <code>Alert.DISMISS_COMMAND</code>
     */
    public Command lGetDismissCommand() {
        return DISMISS_COMMAND;
    }

    /**
     * Notifies timeout change.
     * Changing timeout on an already visible <code>Alert</code> will
     * restart the timer, but has no effect on current layout.
     *
     * @param timeout the new timeout set in the corresponding
     *                <code>Alert</code>.
     */
    public void lSetTimeout(int timeout) {
        if (timerTask != null) {
            try {
                timerTask.cancel();
                if (timeout == Alert.FOREVER) {
                    timerTask = null;
                } else {
                    timerTask = new TimeoutTask();
                    timeoutTimer.schedule(timerTask, timeout);
                }
            } catch (Throwable t) { }
        }
    }

    /**
     * Notifies <code>Alert</code> type change.
     * Changing type on an already visible <code>Alert</code> will only
     * update the default icon. No sound will be played.
     *
     * @param type the new <code>AlertType</code> set in the
     *             corresponding <code>Alert</code>.
     */
    public void lSetType(AlertType type) {
        lRequestInvalidate();
    }

    /**
     * Notifies string change.
     *
     * @param oldString the old string set in the corresponding
     *                  <code>Alert</code>.
     * @param newString the new string set in the corresponding
     *                  <code>Alert</code>.
     */
    public void lSetString(String oldString, String newString) {
        lRequestInvalidate();
    }

    /**
     * Notifies image change.
     *
     * @param oldImg the old image set in the corresponding
     *               <code>Alert</code>.
     * @param newImg the new image set in the corresponding
     *               <code>Alert</code>.
     */
    public void lSetImage(Image oldImg, Image newImg) {
        lRequestInvalidate();
    }

    /**
     * Notifies indicator change.
     *
     * @param oldIndicator the old indicator set in the corresponding
     *                     <code>Alert</code>.
     * @param newIndicator the new indicator set in the corresponding
     *                     <code>Alert</code>.
     */
    public void lSetIndicator(Gauge oldIndicator, Gauge newIndicator) {
        lRequestInvalidate();
    }

    /**
     * Notify this <code>Alert</code> that it is being displayed.
     * Override the version in <code>DisplayableLFImpl</code>.
     */
    void lCallShow() {

        super.lCallShow();

        if (alert.type != null) {
            currentDisplay.playAlertSound(alert.type);
        }

        isContentScroll = 0;
        if (alert.indicator != null) {
            GaugeLFImpl gaugeLF = (GaugeLFImpl)alert.indicator.gaugeLF;
            gaugeLF.lSetSize(gaugeLF.lGetPreferredWidth(-1),
                             gaugeLF.lGetPreferredHeight(-1));
            gaugeLF.lCallShowNotify();
        }

        if (alert.time != Alert.FOREVER
            && alert.numCommands == 1
            && isContentScroll == 0) {

            if (timeoutTimer == null) {
                timeoutTimer = new Timer();
            }
            timerTask = new TimeoutTask();
            timeoutTimer.schedule(timerTask, alert.time);
        }
    }

    /**
     * Notify this <code>Alert</code> that it will no longer be displayed.
     * Override the version in <code>DisplayableLFImpl</code>.
     */
    void lCallHide() {

        // Stop the timer
        if (timerTask != null) {
            try {
                timerTask.cancel();
                timerTask = null;
            } catch (Throwable t) { }
        }

        if (alert.indicator != null) {
            GaugeLFImpl gaugeLF = (GaugeLFImpl)alert.indicator.gaugeLF;
            if (gaugeLF.visibleInViewport) {
                gaugeLF.lCallHideNotify();
            }
        }

        // Hide and delete alert dialog window including title and ticker
        super.lCallHide();
    }

    /**
     * Called by the event handler to perform a re-layout
     * on this <code>AlertLF</code>.
     */
    public void uCallInvalidate() {
        lRequestPaint();
    }

    /**
     * Notify return screen about screen size change
     */
    public void uCallSizeChanged(int w, int h) {
        super.uCallSizeChanged(w,h);
        Displayable returnScreen = alert.getReturnScreen();
        if (returnScreen != null) {
            (returnScreen.displayableLF).uCallSizeChanged(w,h);
        }
    }

    /**
     * Paint this Alert in Java instead of delegating to a native overlay.
     */
    public void uCallPaint(Graphics g, Object target) {
        super.uCallPaint(g, target);

        g.resetGC();
        g.setColor(0xffffff);
        g.fillRect(0, 0, width, height);

        int titleHeight = uPaintScreenChrome(g);
        int x = JAVA_ALERT_PADDING;
        int y = titleHeight + JAVA_ALERT_PADDING;
        int contentWidth = width - (JAVA_ALERT_PADDING * 2);

        Image image = alert.image;
        if (image == null && alert.type != null) {
            image = getAlertImage(alert.type);
        }
        boolean hasText = alert.text != null && alert.text.length() > 0;
        boolean hasContent = image != null || hasText;
        Font textFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN,
                                     Font.SIZE_SMALL);
        g.setFont(textFont);

        if (hasContent) {
            int textX = x + JAVA_ALERT_CARD_PADDING;
            int textWidth = contentWidth - (JAVA_ALERT_CARD_PADDING * 2);
            int rowBottom = y + JAVA_ALERT_CARD_PADDING;
            if (image != null) {
                textX += image.getWidth() + JAVA_ALERT_GAP;
                textWidth -= image.getWidth() + JAVA_ALERT_GAP;
                rowBottom += image.getHeight();
            }
            int cardHeight = lGetWrappedTextHeight(alert.text, textWidth,
                                                   textFont);
            if (cardHeight < rowBottom - y - JAVA_ALERT_CARD_PADDING) {
                cardHeight = rowBottom - y - JAVA_ALERT_CARD_PADDING;
            }
            cardHeight += JAVA_ALERT_CARD_PADDING * 2;

            g.setColor(lGetAlertBackgroundColor(alert.type));
            g.fillRoundRect(x, y, contentWidth, cardHeight,
                            JAVA_ALERT_CARD_ARC, JAVA_ALERT_CARD_ARC);
            g.setColor(lGetAlertBorderColor(alert.type));
            g.drawRoundRect(x, y, contentWidth - 1, cardHeight - 1,
                            JAVA_ALERT_CARD_ARC, JAVA_ALERT_CARD_ARC);

            int contentY = y + JAVA_ALERT_CARD_PADDING;
            if (image != null) {
                g.drawImage(image, x + JAVA_ALERT_CARD_PADDING, contentY,
                            Graphics.TOP | Graphics.LEFT);
                textX = x + JAVA_ALERT_CARD_PADDING + image.getWidth()
                    + JAVA_ALERT_GAP;
                textWidth = contentWidth - (JAVA_ALERT_CARD_PADDING * 2)
                    - image.getWidth() - JAVA_ALERT_GAP;
                rowBottom = contentY + image.getHeight();
            } else {
                textX = x + JAVA_ALERT_CARD_PADDING;
                textWidth = contentWidth - (JAVA_ALERT_CARD_PADDING * 2);
                rowBottom = contentY;
            }

            g.setColor(Theme.getColor(Display.COLOR_FOREGROUND));
            int textBottom = paintWrappedText(g, alert.text, textX, contentY,
                                              textWidth, textFont);
            y = textBottom > rowBottom ? textBottom : rowBottom;
        }

        if (alert.indicator != null) {
            y += JAVA_ALERT_GAP;
            GaugeLFImpl gaugeLF = (GaugeLFImpl)alert.indicator.gaugeLF;
            gaugeLF.uCallPaint(g, contentWidth, gaugeLF.lGetPreferredHeight(-1));
        }
    }

    // *****************************************************
    //  Package private methods
    // *****************************************************

    /**
     * Called upon content change to schedule a request for relayout and
     * repaint.
     */
    void lRequestInvalidate() {
        super.lRequestInvalidate();
        isContentScroll = -1; // Unknown scrolling state
    }

    // *****************************************************
    //  Private methods
    // *****************************************************

    /**
     * Layout the content of this <code>Alert</code>.
     * Query native resource for two informations:
     * - Whether the content needs scrolling, in 'isContentScroll'
     * - Location of the gauge indicator
     *
     * SYNC NOTE: Caller of this function should hold LCDUILock around
     *            this call.
     */
    private void layout() {

        boolean wasNoNative = (nativeId == INVALID_NATIVE_ID);

        // If no native resource yet, create it temporarily
        if (wasNoNative) {
            createNativeResource();
        }

        Image img = alert.image;

        // If no image is specified, default icon for that type should be used
        if (img == null && alert.type != null) {
            img = getAlertImage(alert.type);
        }

        // Bounds array of gauge
        // The reason gauge bounds is passed back from native is to be
        // consistent with Form's Java layout code.
        int[] gaugeBounds;
        GaugeLFImpl gaugeLF;

        if (alert.indicator == null) {
            gaugeLF = null;
            gaugeBounds = null;
        } else {
            // We temporarily use bounds array in gauge
            // The real values will be set later by setSize() and setLocation()
            gaugeLF = (GaugeLFImpl)alert.indicator.gaugeLF;
            gaugeBounds = new int[4];

            // Pass gauge's preferred size to native layout code
            gaugeBounds[WIDTH]  = gaugeLF.lGetPreferredWidth(-1);
            gaugeBounds[HEIGHT] = gaugeLF.lGetPreferredHeight(-1);
        }

        ImageData imageData = null;

        if (img != null) {
            imageData = img.getImageData();
        }

        // Set content to native dialog and get layout information back
        if (setNativeContents0(nativeId, imageData,
                               gaugeBounds, alert.text)) {
            isContentScroll = 1; // scrolling needed
        } else {
            isContentScroll = 0; // no scrolling
        }

        // Set gauge location and size based on return from native layout code
        if (gaugeBounds != null) {
            gaugeLF.lSetSize(gaugeBounds[WIDTH], gaugeBounds[HEIGHT]);
            gaugeLF.lSetLocation(gaugeBounds[X], gaugeBounds[Y]);
        }

        // Native resource should only be kept alive if it's visible
        // Free temporarily created native resource here
        if (wasNoNative) {
            deleteNativeResource();
        }
    }

    /**
     * Show or update contents on a visible <code>Alert</code>.
     *
     * SYNC NOTE: Caller must hold LCDUILock around this call.
     */
    private void showContents() {

        // Make sure gauge has native resource ready
        GaugeLFImpl gaugeLF = (alert.indicator == null)
                                        ? null
                                        : (GaugeLFImpl)alert.indicator.gaugeLF;

        if (gaugeLF != null && gaugeLF.nativeId == INVALID_NATIVE_ID) {
            gaugeLF.createNativeResource(nativeId);
        }

        // Re-populate the alert with updated contents
        layout();

        // Make sure gauge is shown
        if (gaugeLF != null) {
            gaugeLF.lShowNativeResource();

            // SYNC NOTE: Since Gauge show and showNotify does not involve
            // application code, we can call it while holding LCDUILock
            gaugeLF.lCallShowNotify();

            // IMPLEMENTATION NOTE: when gauge is present in the Alert
            // its visibleInViewport will always be set to true.
            // If dynamic update of gauge's visibleInViewport flag is
            // required in AlertLFImpl
            // uViewportChanged() can be moved up from FormLFImpl to
            // DisplayableLFImpl
        }
    }

    /**
     * Create native resource for this <code>Alert</code>.
     * <code>Gauge</code> resource will not be created.
     */
    void createNativeResource() {

        nativeId = createNativeResource0(alert.title,
                        alert.ticker == null ? null : alert.ticker.getString(),
                        alert.type == null ? 0 : alert.type.getType());
    }

    /**
     * Create native dialog with image and text widget for this
     * <code>Alert</code>.
     *
     * @param title the title being passed to native
     * @param tickerText text to be displayed on the <code>Ticker</code>
     * @param type the type of <code>Alert</code>
     * @return native resource id
     */
    private native int createNativeResource0(String title,
                                             String tickerText,
                                             int type);

    /**
     * (Re)Show native dialog with image and text widget for this
     * <code>Alert<code>.
     *
     * @param nativeId native resource id
     */
    private native void showNativeResource0(int nativeId);

    /**
     * Set content to native dialog.
     *
     * @param nativeId IN this alert's resource id (MidpDisplayable *)
     * @param imgId IN icon image native id. 0 if no image.
     * @param indicatorBounds a 4 integer array for indicator gauge
     *                        [0] : OUT x coordinate in alert dialog
     *                        [1] : OUT y coordinate in alert dialog
     *                        [2] : IN/OUT width of the gauge, in pixels
     *                        [3] : IN/OUT height of the gauge, in pixels
     *                        null if no indicator gauge present.
     * @param text IN alert text string
     * @return <code>true</code> if content requires scrolling
     */
    private native boolean setNativeContents0(int nativeId,
                                              ImageData imgId,
                                              int[] indicatorBounds,
                                              String text);

    /**
     * Get the corresponding image for a given alert type.
     *
     * @param alertType type defined in <code>AlertType</code>
     * @return image object to be displayed. Null if type is invalid.
     */
    private Image getAlertImage(AlertType alertType) {
        if (alertType != null) {
            if (alertType.equals(AlertType.INFO)) {
                if (ALERT_INFO == null) {
                    ALERT_INFO = createAlertIcon("i", 0x2f80ed, false);
                }
                return ALERT_INFO;
            } else if (alertType.equals(AlertType.WARNING)) {
                if (ALERT_WARN == null) {
                    ALERT_WARN = createAlertIcon("!", 0xf2c94c, true);
                }
                return ALERT_WARN;
            } else if (alertType.equals(AlertType.ERROR)) {
                if (ALERT_ERR == null) {
                    ALERT_ERR = createAlertIcon("!", 0xeb5757, false);
                }
                return ALERT_ERR;
            } else if (alertType.equals(AlertType.ALARM)) {
                if (ALERT_ALRM == null) {
                    ALERT_ALRM = createAlertIcon("!", 0xf2994a, false);
                }
                return ALERT_ALRM;
            } else if (alertType.equals(AlertType.CONFIRMATION)) {
                if (ALERT_CFM == null) {
                    ALERT_CFM = createAlertIcon("?", 0x27ae60, false);
                }
                return ALERT_CFM;
            }
        }

        return null;
    }

    private int lGetAlertBackgroundColor(AlertType alertType) {
        if (alertType != null) {
            if (alertType.equals(AlertType.INFO)) {
                return 0xe7f1ff;
            } else if (alertType.equals(AlertType.WARNING)) {
                return 0xfff1cc;
            } else if (alertType.equals(AlertType.ERROR)) {
                return 0xffe2e2;
            } else if (alertType.equals(AlertType.ALARM)) {
                return 0xffead6;
            } else if (alertType.equals(AlertType.CONFIRMATION)) {
                return 0xe2f5e9;
            }
        }
        return 0xf4f4f4;
    }

    private int lGetAlertBorderColor(AlertType alertType) {
        if (alertType != null) {
            if (alertType.equals(AlertType.INFO)) {
                return 0xb5d2f5;
            } else if (alertType.equals(AlertType.WARNING)) {
                return 0xe5c46b;
            } else if (alertType.equals(AlertType.ERROR)) {
                return 0xe0a4a4;
            } else if (alertType.equals(AlertType.ALARM)) {
                return 0xe5b07a;
            } else if (alertType.equals(AlertType.CONFIRMATION)) {
                return 0xadd6bc;
            }
        }
        return 0xd0d0d0;
    }

    /**
     * Create a small Java-painted fallback icon. Avoids depending on
     * romized image resources while Alert is rendered by Java.
     */
    private Image createAlertIcon(String glyph, int color, boolean darkText) {
        Image image = Image.createImage(20, 20);
        Graphics g = image.getGraphics();
        g.setColor(0xffffff);
        g.fillRect(0, 0, 20, 20);
        g.setColor(color);
        g.fillRoundRect(1, 1, 18, 18, 6, 6);
        g.setColor(0x666666);
        g.drawRoundRect(1, 1, 18, 18, 6, 6);

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,
                                 Font.SIZE_SMALL);
        g.setFont(font);
        if (darkText) {
            g.setColor(0x4a3a00);
        } else {
            g.setColor(0xffffff);
        }
        g.drawString(glyph, 10, (20 - font.getHeight()) / 2,
                     Graphics.TOP | Graphics.HCENTER);
        return image;
    }

    private int paintWrappedText(Graphics g, String text, int x, int y,
                                 int maxWidth, Font font) {
        if (text == null || text.length() == 0) {
            return y;
        }

        int start = 0;
        while (start < text.length()) {
            int newline = text.indexOf('\n', start);
            int end = newline < 0 ? text.length() : newline;
            y = paintWrappedLine(g, text, start, end, x, y, maxWidth, font);
            start = end + 1;
            if (newline < 0) {
                break;
            }
        }
        return y;
    }

    private int lGetWrappedTextHeight(String text, int maxWidth, Font font) {
        if (text == null || text.length() == 0) {
            return 0;
        }

        int height = 0;
        int start = 0;
        while (start < text.length()) {
            int newline = text.indexOf('\n', start);
            int end = newline < 0 ? text.length() : newline;
            height += lGetWrappedLineHeight(text, start, end, maxWidth, font);
            start = end + 1;
            if (newline < 0) {
                break;
            }
        }
        return height;
    }

    private int lGetWrappedLineHeight(String text, int start, int end,
                                      int maxWidth, Font font) {
        if (start == end) {
            return font.getHeight() + 2;
        }

        int height = 0;
        int lineStart = start;
        while (lineStart < end) {
            int lineEnd = lineStart;
            int lastSpace = -1;
            while (lineEnd < end) {
                char ch = text.charAt(lineEnd);
                if (ch == ' ') {
                    lastSpace = lineEnd;
                }
                int next = lineEnd + 1;
                if (font.substringWidth(text, lineStart, next - lineStart)
                        > maxWidth) {
                    if (lastSpace >= lineStart) {
                        lineEnd = lastSpace;
                    }
                    break;
                }
                lineEnd = next;
            }

            if (lineEnd <= lineStart) {
                lineEnd = lineStart + 1;
            }
            height += font.getHeight() + 2;
            lineStart = lineEnd;
            while (lineStart < end && text.charAt(lineStart) == ' ') {
                lineStart++;
            }
        }
        return height;
    }

    private int paintWrappedLine(Graphics g, String text, int start, int end,
                                 int x, int y, int maxWidth, Font font) {
        int lineStart = start;
        while (lineStart < end) {
            int lineEnd = lineStart;
            int lastSpace = -1;
            while (lineEnd < end) {
                char ch = text.charAt(lineEnd);
                if (ch == ' ') {
                    lastSpace = lineEnd;
                }
                int next = lineEnd + 1;
                if (font.substringWidth(text, lineStart, next - lineStart)
                        > maxWidth) {
                    if (lastSpace >= lineStart) {
                        lineEnd = lastSpace;
                    }
                    break;
                }
                lineEnd = next;
            }

            if (lineEnd <= lineStart) {
                lineEnd = lineStart + 1;
            }
            g.drawSubstring(text, lineStart, lineEnd - lineStart, x, y,
                            Graphics.TOP | Graphics.LEFT);
            y += font.getHeight() + 2;
            lineStart = lineEnd;
            while (lineStart < end && text.charAt(lineStart) == ' ') {
                lineStart++;
            }
        }
        if (start == end) {
            y += font.getHeight() + 2;
        }
        return y;
    }

    // *****************************************************
    //  Private members
    // *****************************************************

    /**
     * Inner class to request security token from SecurityInitializer.
     * SecurityInitializer should be able to check this inner class name.
     */
    static private class SecurityTrusted
        implements ImplicitlyTrustedClass {};

    /** Security token to allow access to implementation APIs */
    private static SecurityToken classSecurityToken =
        SecurityInitializer.requestToken(new SecurityTrusted());

    /**
     * Internal command used to visually represent
     * <code>Alert.DISMISS_COMMAND</code>.
     */
    private static final Command DISMISS_COMMAND =
        new Command(Resource.getString(ResourceConstants.DONE),
                    Command.CANCEL, 0);

    /**
     * The default timeout of all alerts.
     */
    private static final int DEFAULT_TIMEOUT = 2000;

    private static final int JAVA_ALERT_PADDING = 8;
    private static final int JAVA_ALERT_GAP = 8;
    private static final int JAVA_ALERT_CARD_PADDING = 6;
    private static final int JAVA_ALERT_CARD_ARC = 8;

    /**
     * A <code>Timer</code> which serves all <code>Alert</code> objects
     * to schedule their timeout tasks.
     */
    private static Timer timeoutTimer;

    /**
     * <code>Alert</code> associated with this view.
     */
    private Alert alert;

    /**
     * A <code>TimerTask</code> which will be set to expire this
     * <code>Alert</code> after its timeout period has elapsed.
     */
    private TimerTask timerTask;

    /**
     * A flag that indicates whether the content of the alert
     * needs scrolling.
     * Valid values are: -1: unknown, 0: no scrolling, 1: scrolling needed.
     */
    private int isContentScroll = -1; // Default is unknown


    /**
     * An image to be drawn in <code>Alert</code> when it was
     * created with AlertType ALARM.
     */
    private static Image ALERT_ALRM; // = null

    /**
     * An image to be drawn in <code>Alert</code> when it was
     * created with AlertType CONFIRMATION..
     */
    private static Image ALERT_CFM; // = null

    /**
     * An image to be drawn in <code>Alert</code> when it was
     * created with AlertType ERROR.
     */
    private static Image ALERT_ERR; // = null

    /**
     * An image to be drawn in <code>Alert</code> when it was
     * created with AlertType INFO.
     */
    private static Image ALERT_INFO; // = null

    /**
     * An image to be drawn in <code>Alert</code> when it was
     * created with AlertType WARNING.
     */
    private static Image ALERT_WARN; // = null

    // *****************************************************
    //  Inner Class for timed dismiss
    // *****************************************************

    /**
     * A <code>TimerTask</code> subclass which will notify the
     * <code>Display</code> to make the 'returnScreen' of this
     * <code>Alert</code> the new current screen.
     */
    private class TimeoutTask extends TimerTask {

        /**
         * Create a new timeout task.
         * This package protected constructor is just to enable creation
         * of new TimerTask instance.
         */
        TimeoutTask() { }

        /**
         * Simply set the <code>Display</code>'s current screen to be this
         * <code>Alert</code>'s return screen.
         */
        public void run() {
            alert.uNotifyTimeout();
        }
    } // TimeoutTask
}
