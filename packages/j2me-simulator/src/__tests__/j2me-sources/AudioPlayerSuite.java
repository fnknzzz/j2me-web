package com.com.example;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;

public class AudioPlayerSuite extends MIDlet implements PlayerListener {
    private Display display;
    private AudioCanvas canvas;
    private Player player;
    private VolumeControl volumeControl;
    
    protected void startApp() {
        display = Display.getDisplay(this);
        canvas = new AudioCanvas();
        display.setCurrent(canvas);
        
        try {
            loadAudio();
        } catch (Exception e) {
            canvas.updateStatus("Init failed: " + e.getMessage());
        }
    }
    
    private void loadAudio() {
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/audio.mid");
            if (is == null) {
                canvas.updateStatus("Resource not found");
                return;
            }
            
            player = Manager.createPlayer(is, "audio/midi");
            player.addPlayerListener(this);
            player.realize();
            
            volumeControl = (VolumeControl) player.getControl("VolumeControl");
            if (volumeControl != null) {
                volumeControl.setLevel(80);
            }
            
            canvas.updateStatus("Audio loaded");
        } catch (Exception e) {
            canvas.updateStatus("Load failed: " + e.getMessage());
        }
    }
    
    private void playAudio() {
        try {
            if (player != null) {
                if (volumeControl != null) {
                    volumeControl.setLevel(canvas.getVolume());
                }
                player.start();
                canvas.updateStatus("Playing...");
            }
        } catch (Exception e) {
            canvas.updateStatus("Play failed");
        }
    }
    
    private void stopAudio() {
        try {
            if (player != null) {
                player.stop();
                canvas.updateStatus("Stopped");
            }
        } catch (Exception e) {
            canvas.updateStatus("Stop failed");
        }
    }
    
    private void cleanup() {
        try {
            if (player != null) {
                player.stop();
                player.close();
                player = null;
            }
        } catch (Exception e) {
        }
    }
    
    public void playerUpdate(Player p, String event, Object eventData) {
        if (event.equals("started")) {
            canvas.updateStatus("Started");
        } else if (event.equals("stopped")) {
            canvas.updateStatus("Stopped");
        } else if (event.equals("endOfMedia")) {
            canvas.updateStatus("Finished");
        } else if (event.equals("error")) {
            canvas.updateStatus("Error");
        }
    }
    
    protected void pauseApp() {
        stopAudio();
    }
    
    protected void destroyApp(boolean unconditional) {
        cleanup();
    }
    
    class AudioCanvas extends Canvas {
        private String status = "Ready";
        private int volume = 80;
        
        protected void paint(Graphics g) {
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, getWidth(), getHeight());
            
            Font titleFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            
            int y = 5;
            
            g.setColor(0, 0, 0);
            g.setFont(titleFont);
            g.drawString("Audio Player", getWidth() / 2, y, Graphics.TOP | Graphics.HCENTER);
            y = y + titleFont.getHeight() + 5;
            
            g.setColor(200, 200, 200);
            g.drawLine(5, y, getWidth() - 5, y);
            y = y + 5;
            
            g.setColor(0, 0, 0);
            g.setFont(font);
            g.drawString("Status: " + status, 10, y, Graphics.TOP | Graphics.LEFT);
            y = y + font.getHeight() + 10;
            
            g.drawString("Volume: " + volume + "%", 10, y, Graphics.TOP | Graphics.LEFT);
            y = y + font.getHeight() + 5;
            
            int barX = 10;
            int barY = y;
            int barWidth = getWidth() - 20;
            int barHeight = 10;
            
            g.setColor(200, 200, 200);
            g.fillRect(barX, barY, barWidth, barHeight);
            
            g.setColor(0, 120, 215);
            int fillWidth = (barWidth * volume) / 100;
            g.fillRect(barX, barY, fillWidth, barHeight);
            
            g.setColor(100, 100, 100);
            g.drawRect(barX, barY, barWidth, barHeight);
            
            y = y + barHeight + 20;
            
            g.setColor(100, 100, 100);
            g.setFont(font);
            g.drawString("Controls:", 10, y, Graphics.TOP | Graphics.LEFT);
            y = y + font.getHeight() + 3;
            
            g.drawString("5 - Play", 15, y, Graphics.TOP | Graphics.LEFT);
            y = y + font.getHeight() + 2;
            g.drawString("0 - Stop", 15, y, Graphics.TOP | Graphics.LEFT);
            y = y + font.getHeight() + 2;
            g.drawString("2 - Volume Up", 15, y, Graphics.TOP | Graphics.LEFT);
            y = y + font.getHeight() + 2;
            g.drawString("8 - Volume Down", 15, y, Graphics.TOP | Graphics.LEFT);
        }
        
        protected void keyPressed(int keyCode) {
            if (keyCode == KEY_NUM5) {
                playAudio();
            } else if (keyCode == KEY_NUM0) {
                stopAudio();
            } else if (keyCode == KEY_NUM2) {
                if (volume < 100) {
                    volume = volume + 10;
                    if (volume > 100) volume = 100;
                    if (volumeControl != null) {
                        volumeControl.setLevel(volume);
                    }
                    repaint();
                }
            } else if (keyCode == KEY_NUM8) {
                if (volume > 0) {
                    volume = volume - 10;
                    if (volume < 0) volume = 0;
                    if (volumeControl != null) {
                        volumeControl.setLevel(volume);
                    }
                    repaint();
                }
            }
        }
        
        public void updateStatus(String newStatus) {
            status = newStatus;
            repaint();
        }
        
        public int getVolume() {
            return volume;
        }
    }
}
