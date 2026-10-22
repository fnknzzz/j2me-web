package com.sun.mmedia;

import javax.microedition.media.*;
import javax.microedition.media.control.*;
import javax.microedition.media.protocol.DataSource;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/**
 * PlayerImpl: thin shell delegating lifecycle to native (Rust) methods.
 * Implements VolumeControl and ToneControl so getControl() can return self.
 */
public class PlayerImpl implements Player, VolumeControl, ToneControl {

    private int hNative;
    private boolean isClosed = false;
    private String locator;
    private Vector listeners = new Vector(2);

    // handle → PlayerImpl instance, used for event delivery
    private static Hashtable playerRegistry = new Hashtable();

    public int getHandle() { return hNative; }
    public PlayerStateSubscriber state_subscriber;

    /**
     * Look up a PlayerImpl by its native handle.
     * Called by MMEventListener to deliver events.
     */
    public static PlayerImpl get(int handle) {
        return (PlayerImpl) playerRegistry.get(new Integer(handle));
    }

    public static void sendExternalVolumeChanged(String evtName, int volume) {}
    public BasicPlayer getPlayerInst() { return null; }

    // Native methods — implemented in Rust media.rs
    private native int  nInit(int appId, int pID, String URI);
    private native void nRealize(int handle);
    private native void nPrefetch(int handle);
    private native void nStart(int handle);
    private native void nStop(int handle);
    private native void nClose(int handle);
    private native void nDeallocate(int handle);
    private native int  nGetState(int handle);
    private native long nGetDuration(int handle);
    private native long nGetMediaTime(int handle);
    private native long nSetMediaTime(int handle, long now);
    private native String nGetContentType(int handle);
    private native void nSetLoopCount(int handle, int count);
    private native int  nGetVolume(int handle);
    private native int  nSetVolume(int handle, int level);
    private native boolean nIsMuted(int handle);
    private native void nSetMute(int handle, boolean mute);
    private native void nSetSequence(int handle, byte[] sequence);

    private PlayerImpl() {}

    public PlayerImpl(DataSource source) throws MediaException, IOException {
        this.locator = source.getLocator();
        hNative = nInit(0, 0, this.locator);
        if (hNative == 0 || hNative == -1) {
            throw new MediaException("Unable to create native player");
        }
        playerRegistry.put(new Integer(hNative), this);
    }

    // --- Player lifecycle ---

    public void realize() throws MediaException {
        chkClosed();
        nRealize(hNative);
    }

    public void prefetch() throws MediaException {
        chkClosed();
        nPrefetch(hNative);
    }

    public void start() throws MediaException {
        chkClosed();
        nStart(hNative);
    }

    public void stop() throws MediaException {
        chkClosed();
        nStop(hNative);
    }

    public void deallocate() {
        chkClosed();
        nDeallocate(hNative);
    }

    public void close() {
        if (!isClosed) {
            nClose(hNative);
            isClosed = true;
            playerRegistry.remove(new Integer(hNative));
        }
    }

    public int getState() {
        if (isClosed) return Player.CLOSED;
        return nGetState(hNative);
    }

    public long getDuration() {
        chkClosed();
        return nGetDuration(hNative);
    }

    public long getMediaTime() {
        chkClosed();
        return nGetMediaTime(hNative);
    }

    public long setMediaTime(long now) throws MediaException {
        chkClosed();
        return nSetMediaTime(hNative, now);
    }

    public String getContentType() {
        chkClosed();
        return nGetContentType(hNative);
    }

    public Control getControl(String type) {
        chkClosed();
        if (type != null) {
            String shortType = type;
            int idx = type.lastIndexOf('.');
            if (idx >= 0) shortType = type.substring(idx + 1);
            if (shortType.equals("VolumeControl") || shortType.equals("ToneControl")) {
                return (Control) this;
            }
        }
        return null;
    }

    public Control[] getControls() {
        chkClosed();
        return new Control[0];
    }

    public void setLoopCount(int count) {
        chkClosed();
        nSetLoopCount(hNative, count);
    }

    // --- PlayerListener support ---

    public void addPlayerListener(PlayerListener playerListener) {
        if (playerListener != null) {
            listeners.addElement(playerListener);
        }
    }

    public void removePlayerListener(PlayerListener playerListener) {
        listeners.removeElement(playerListener);
    }

    /**
     * Deliver an event to all registered PlayerListeners.
     * Called directly by MMEventListener when a native event arrives.
     */
    public void notifyListeners(String evt, Object evtData) {
        for (int i = 0; i < listeners.size(); i++) {
            PlayerListener pl = (PlayerListener) listeners.elementAt(i);
            try {
                pl.playerUpdate(this, evt, evtData);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }

    public void setTimeBase(TimeBase master) throws MediaException {}

    public TimeBase getTimeBase() {
        return Manager.getSystemTimeBase();
    }

    private void chkClosed() {
        if (isClosed) throw new IllegalStateException("Player is closed");
    }

    // --- VolumeControl ---

    public int setLevel(int level) {
        return nSetVolume(hNative, level);
    }

    public int getLevel() {
        return nGetVolume(hNative);
    }

    public boolean isMuted() {
        return nIsMuted(hNative);
    }

    public void setMute(boolean mute) {
        nSetMute(hNative, mute);
    }

    // --- ToneControl ---

    public void setSequence(byte[] sequence) {
        nSetSequence(hNative, sequence);
    }
}
