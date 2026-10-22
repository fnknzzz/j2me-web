package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import java.util.Vector;

public class LayerManager {

    private Vector layers = new Vector();
    private int viewX;
    private int viewY;
    private int viewWidth;
    private int viewHeight;

    public LayerManager() {
    }

    public void append(Layer l) {
        if (l == null) {
            throw new NullPointerException();
        }
        // remove if already present
        layers.removeElement(l);
        layers.addElement(l);
    }

    public void insert(Layer l, int index) {
        if (l == null) {
            throw new NullPointerException();
        }
        // remove if already present
        layers.removeElement(l);
        if (index < 0 || index > layers.size()) {
            throw new IndexOutOfBoundsException();
        }
        layers.insertElementAt(l, index);
    }

    public void remove(Layer l) {
        if (l == null) {
            throw new NullPointerException();
        }
        layers.removeElement(l);
    }

    public Layer getLayerAt(int index) {
        if (index < 0 || index >= layers.size()) {
            throw new IndexOutOfBoundsException();
        }
        return (Layer) layers.elementAt(index);
    }

    public int getSize() {
        return layers.size();
    }

    public void setViewWindow(int x, int y, int width, int height) {
        this.viewX = x;
        this.viewY = y;
        this.viewWidth = width;
        this.viewHeight = height;
    }

    public void paint(Graphics g, int x, int y) {
        // paint layers from back to front (last appended = back, index 0 = front)
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer l = (Layer) layers.elementAt(i);
            if (l.isVisible()) {
                l.paint(g);
            }
        }
    }
}
