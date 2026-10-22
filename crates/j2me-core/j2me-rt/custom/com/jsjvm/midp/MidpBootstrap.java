package com.jsjvm.midp;

import com.sun.midp.events.EventQueue;
import com.sun.midp.lcdui.DisplayContainer;
import com.sun.midp.lcdui.ForegroundEventListener;
import com.sun.midp.lcdui.LCDUIEnvironment;
import com.sun.midp.security.ImplicitlyTrustedClass;
import com.sun.midp.security.SecurityInitializer;
import com.sun.midp.security.SecurityToken;
import com.sun.mmedia.MMEventListener;

public final class MidpBootstrap {
    private static boolean inited = false;

    private static final class Trusted implements ImplicitlyTrustedClass {}

    public static synchronized void init() {
        if (inited) {
            return;
        }
        SecurityInitializer.initSystem();
        SecurityToken token = SecurityInitializer.requestToken(new Trusted());
        EventQueue queue = EventQueue.getEventQueue(token);
        NoopForegroundController controller = new NoopForegroundController();
        controller.setEventQueue(queue);
        LCDUIEnvironment env = new LCDUIEnvironment(token, queue, 0, controller);
        DisplayContainer displayContainer = env.getDisplayContainer();
        controller.setDisplayContainer(displayContainer);
        new ForegroundEventListener(queue, displayContainer);
        new MMEventListener();
        inited = true;
    }
}
