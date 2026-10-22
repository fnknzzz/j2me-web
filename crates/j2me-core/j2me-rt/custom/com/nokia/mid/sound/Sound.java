package com.nokia.mid.sound;

public class Sound {
    public static final int SOUND_PLAYING = 0;
    public static final int SOUND_STOPPED = 1;
    public static final int SOUND_UNINITIALIZED = 3;

    public static final int FORMAT_TONE = 5;
    public static final int FORMAT_WAV = 6;
    public static final int FORMAT_MIDI = 7;

    private int state;
    private int gain;
    private int type;
    private int freq;
    private long duration;
    private byte[] data;
    private SoundListener listener;

    public Sound(byte[] data, int type) {
        this.data = data;
        this.type = type;
        this.state = SOUND_STOPPED;
        this.gain = 100;
    }

    public Sound(int freq, long duration) {
        this.freq = freq;
        this.duration = duration;
        this.type = FORMAT_TONE;
        this.state = SOUND_STOPPED;
        this.gain = 100;
    }

    public void init(byte[] data, int type) {
        stop();
        this.data = data;
        this.type = type;
        this.freq = 0;
        this.duration = 0;
        this.state = SOUND_STOPPED;
    }

    public void init(int freq, long duration) {
        stop();
        this.freq = freq;
        this.duration = duration;
        this.data = null;
        this.type = FORMAT_TONE;
        this.state = SOUND_STOPPED;
    }

    public native void play(int loop);

    public void stop() {
        if (state == SOUND_PLAYING) {
            state = SOUND_STOPPED;
            notifyListener(SOUND_STOPPED);
        }
    }

    public void resume() {
        if (state == SOUND_STOPPED) {
            state = SOUND_PLAYING;
            notifyListener(SOUND_PLAYING);
        }
    }

    public void release() {
        stop();
        state = SOUND_UNINITIALIZED;
        data = null;
        freq = 0;
        duration = 0;
        notifyListener(SOUND_UNINITIALIZED);
    }

    public int getState() {
        return state;
    }

    public int getGain() {
        return gain;
    }

    public void setGain(int gain) {
        this.gain = Math.max(0, Math.min(255, gain));
    }

    public void setSoundListener(SoundListener listener) {
        this.listener = listener;
    }

    public static int getConcurrentSoundCount(int type) {
        return 32;
    }

    public static int[] getSupportedFormats() {
        return new int[] { FORMAT_TONE, FORMAT_WAV, FORMAT_MIDI };
    }

    private void notifyListener(int event) {
        if (listener != null) {
            try {
                listener.soundStateChanged(this, event);
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
