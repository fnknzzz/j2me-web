package com.jsjvm.midp;

import javax.microedition.lcdui.Displayable;

import com.sun.midp.events.EventQueue;
import com.sun.midp.events.EventTypes;
import com.sun.midp.events.NativeEvent;
import com.sun.midp.lcdui.DisplayContainer;
import com.sun.midp.lcdui.ForegroundController;
import com.sun.midp.lcdui.ForegroundEventConsumer;

public final class NoopForegroundController implements ForegroundController {
    private DisplayContainer displayContainer;
    private EventQueue eventQueue;

    public void setDisplayContainer(DisplayContainer container) {
        this.displayContainer = container;
    }

    public void setEventQueue(EventQueue queue) {
        this.eventQueue = queue;
    }

    public Displayable registerDisplay(int displayId, String ownerClassName) {
        return null;
    }

    public void requestForeground(int displayId, boolean isAlert) {
        if (eventQueue != null) {
            NativeEvent event = new NativeEvent(EventTypes.FOREGROUND_NOTIFY_EVENT);
            event.intParam4 = displayId;
            eventQueue.post(event);
        }
    }

    public void requestBackground(int displayId) {
        if (eventQueue != null) {
            NativeEvent event = new NativeEvent(EventTypes.BACKGROUND_NOTIFY_EVENT);
            event.intParam4 = displayId;
            eventQueue.post(event);
        }
    }

    public void startPreempting(int displayId) {}

    public void stopPreempting(int displayId) {}
}
