package com.sun.mmedia;

import javax.microedition.media.*;
import com.sun.midp.events.*;

/**
 * Listens for native MMAPI events (type 45) from the EventQueue
 * and delivers them to the corresponding PlayerImpl's listeners.
 */
public class MMEventListener implements EventListener {

    private static final int EVENT_MEDIA_END_OF_MEDIA       =  1;
    private static final int EVENT_MEDIA_DURATION_UPDATED   =  2;
    private static final int EVENT_MEDIA_RECORD_SIZE_LIMIT  =  3;
    private static final int EVENT_MEDIA_RECORD_ERROR       =  4;
    private static final int EVENT_MEDIA_DEVICE_AVAILABLE   =  5;
    private static final int EVENT_MEDIA_DEVICE_UNAVAILABLE =  6;
    private static final int EVENT_MEDIA_NEED_MORE_MEDIA_DATA = 7;
    private static final int EVENT_MEDIA_BUFFERING_STARTED  =  8;
    private static final int EVENT_MEDIA_BUFFERING_STOPPED  =  9;
    private static final int EVENT_MEDIA_VOLUME_CHANGED     = 10;
    private static final int EVENT_MEDIA_SNAPSHOT_FINISHED  = 11;
    private static final int EVENT_MEDIA_ERROR              = 12;

    public MMEventListener() {
        MMEventHandler.setListener(this);
    }

    public boolean preprocess(Event event, Event waitingEvent) {
        return true;
    }

    public void process(Event event) {
        NativeEvent nevt = (NativeEvent) event;
        if (EventTypes.MMAPI_EVENT != nevt.getType()) return;

        PlayerImpl p;

        switch (nevt.intParam4) {
        case EVENT_MEDIA_END_OF_MEDIA:
            p = PlayerImpl.get(nevt.intParam1);
            if (p != null) {
                p.notifyListeners(PlayerListener.END_OF_MEDIA, new Long(nevt.intParam2 * 1000));
            }
            break;

        case EVENT_MEDIA_DURATION_UPDATED:
            p = PlayerImpl.get(nevt.intParam1);
            if (p != null) {
                p.notifyListeners(PlayerListener.DURATION_UPDATED, new Long(nevt.intParam2 * 1000));
            }
            break;

        case EVENT_MEDIA_VOLUME_CHANGED:
            if (nevt.intParam2 < 0) nevt.intParam2 = 0;
            if (nevt.intParam2 > 100) nevt.intParam2 = 100;
            PlayerImpl.sendExternalVolumeChanged(PlayerListener.VOLUME_CHANGED, nevt.intParam2);
            break;

        case EVENT_MEDIA_RECORD_ERROR:
            p = PlayerImpl.get(nevt.intParam1);
            if (p != null) {
                p.notifyListeners(PlayerListener.RECORD_ERROR, new String("Unexpected Record Error"));
            }
            break;

        case EVENT_MEDIA_BUFFERING_STARTED:
            p = PlayerImpl.get(nevt.intParam1);
            if (p != null) {
                p.notifyListeners(PlayerListener.BUFFERING_STARTED, new Long(nevt.intParam2 * 1000));
            }
            break;

        case EVENT_MEDIA_BUFFERING_STOPPED:
            p = PlayerImpl.get(nevt.intParam1);
            if (p != null) {
                p.notifyListeners(PlayerListener.BUFFERING_STOPPED, new Long(nevt.intParam2 * 1000));
            }
            break;

        case EVENT_MEDIA_ERROR:
            p = PlayerImpl.get(nevt.intParam1);
            if (p != null) {
                p.notifyListeners(PlayerListener.ERROR, new String("Unexpected Media Error"));
            }
            break;
        }
    }
}
