package com.jsjvm.midp;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;
import javax.microedition.midlet.MIDlet;

public final class MidletSuiteSelectorMIDlet extends MIDlet implements CommandListener {
    private final Command enterCommand = new Command("进入", Command.OK, 1);
    private final Command exitCommand = new Command("退出", Command.EXIT, 2);
    private List list;

    private static final int ICON_DISPLAY_SIZE = 32;

    protected void startApp() {
        int entryCount = MidletSuiteLauncher.entryCount();
        if (entryCount <= 0) {
            return;
        }
        String[] names = new String[entryCount];
        Image[] icons = new Image[entryCount];
        for (int i = 0; i < entryCount; i++) {
            String name = MidletSuiteLauncher.entryName(i + 1);
            if (name == null || name.length() == 0) {
                name = MidletSuiteLauncher.entryClassName(i + 1);
            }
            names[i] = name;

            String iconPath = MidletSuiteLauncher.entryIcon(i + 1);
            if (iconPath != null && iconPath.length() > 0) {
                try {
                    Image raw = Image.createImage(iconPath);
                    if (raw.getWidth() != ICON_DISPLAY_SIZE || raw.getHeight() != ICON_DISPLAY_SIZE) {
                        icons[i] = scaleImage(raw, ICON_DISPLAY_SIZE, ICON_DISPLAY_SIZE);
                    } else {
                        icons[i] = raw;
                    }
                } catch (Exception e) {
                }
            }
        }
        list = new List("选择应用", List.IMPLICIT, names, icons);
        list.setSelectCommand(enterCommand);
        list.addCommand(exitCommand);
        list.setCommandListener(this);
        Display.getDisplay(this).setCurrent(list);
    }

    private static Image scaleImage(Image src, int dstW, int dstH) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        int[] srcPixels = new int[srcW * srcH];
        src.getRGB(srcPixels, 0, srcW, 0, 0, srcW, srcH);
        int[] dstPixels = new int[dstW * dstH];
        for (int dy = 0; dy < dstH; dy++) {
            int sy = dy * srcH / dstH;
            for (int dx = 0; dx < dstW; dx++) {
                dstPixels[dy * dstW + dx] = srcPixels[sy * srcW + (dx * srcW / dstW)];
            }
        }
        return Image.createRGBImage(dstPixels, dstW, dstH, true);
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {}

    public void commandAction(Command command, Displayable displayable) {
        if (command == enterCommand) {
            MidletSuiteLauncher.launch(list.getSelectedIndex() + 1);
        } else if (command == exitCommand) {
            notifyDestroyed();
        }
    }
}
