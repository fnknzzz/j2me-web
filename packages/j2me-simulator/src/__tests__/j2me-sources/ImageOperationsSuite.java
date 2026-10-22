package com.com.example;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class ImageOperationsSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;
    
    protected void startApp() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
        display.setCurrent(canvas);
    }
    
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    
    class TestCanvas extends Canvas {
        private int y = 5;
        private Graphics g;
        
        protected void paint(Graphics graphics) {
            g = graphics;
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, getWidth(), getHeight());
            
            y = 5;
            drawTitle("Image Operations Suite");
            
            testImageCreation();
            testImageDrawing();
            testRGBOperations();
            testComplexComposition();
        }
        
        private void drawTitle(String title) {
            g.setColor(0, 0, 0);
            Font boldFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            g.setFont(boldFont);
            g.drawString(title, getWidth() / 2, y, Graphics.TOP | Graphics.HCENTER);
            y = y + boldFont.getHeight() + 3;
        }
        
        private void drawSection(String section) {
            g.setColor(0, 0, 255);
            Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
            g.setFont(font);
            g.drawString(section, 2, y, Graphics.TOP | Graphics.LEFT);
            y = y + font.getHeight() + 2;
        }
        
        private void drawText(String text) {
            g.setColor(0, 0, 0);
            Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(font);
            g.drawString(text, 5, y, Graphics.TOP | Graphics.LEFT);
            y = y + font.getHeight() + 1;
        }
        
        private void testImageCreation() {
            drawSection("Image Creation");
            
            Image img1 = Image.createImage(40, 30);
            Graphics g1 = img1.getGraphics();
            g1.setColor(255, 200, 200);
            g1.fillRect(0, 0, 40, 30);
            g1.setColor(255, 0, 0);
            g1.drawRect(0, 0, 39, 29);
            
            g.drawImage(img1, 10, y, Graphics.TOP | Graphics.LEFT);
            drawText("40x30 mutable image");
            
            Image img2 = Image.createImage(30, 30);
            Graphics g2 = img2.getGraphics();
            g2.setColor(200, 255, 200);
            g2.fillRect(0, 0, 30, 30);
            g2.setColor(0, 255, 0);
            g2.fillArc(5, 5, 20, 20, 0, 360);
            
            g.drawImage(img2, 60, y, Graphics.TOP | Graphics.LEFT);
            drawText("30x30 with circle");
            
            y = y + 5;
        }
        
        private void testImageDrawing() {
            drawSection("Image Drawing Anchors");
            
            Image sourceImg = Image.createImage(20, 20);
            Graphics sourceG = sourceImg.getGraphics();
            sourceG.setColor(0, 150, 255);
            sourceG.fillRect(0, 0, 20, 20);
            sourceG.setColor(255, 255, 255);
            sourceG.drawLine(0, 0, 19, 19);
            sourceG.drawLine(0, 19, 19, 0);
            
            int boxY = y;
            int boxW = getWidth() - 10;
            int boxH = 50;
            
            g.setColor(245, 245, 245);
            g.fillRect(5, boxY, boxW, boxH);
            
            g.setColor(200, 200, 200);
            g.drawRect(5, boxY, boxW - 1, boxH - 1);
            g.drawLine(5 + boxW / 2, boxY, 5 + boxW / 2, boxY + boxH);
            g.drawLine(5, boxY + boxH / 2, 5 + boxW, boxY + boxH / 2);
            
            g.drawImage(sourceImg, 5, boxY, Graphics.TOP | Graphics.LEFT);
            g.drawImage(sourceImg, 5 + boxW, boxY, Graphics.TOP | Graphics.RIGHT);
            g.drawImage(sourceImg, 5 + boxW / 2, boxY + boxH / 2, Graphics.HCENTER | Graphics.VCENTER);
            g.drawImage(sourceImg, 5, boxY + boxH, Graphics.BOTTOM | Graphics.LEFT);
            g.drawImage(sourceImg, 5 + boxW, boxY + boxH, Graphics.BOTTOM | Graphics.RIGHT);
            
            y = boxY + boxH + 2;
            drawText("5 anchor positions");
            
            y = y + 3;
        }
        
        private void testRGBOperations() {
            drawSection("RGB Operations");
            
            int size = 16;
            int[] rgbData = new int[size * size];
            
            for (int i = 0; i < rgbData.length; i++) {
                int x = i % size;
                int y = i / size;
                if ((x + y) % 2 == 0) {
                    rgbData[i] = 0xFFFF0000;
                } else {
                    rgbData[i] = 0xFF0000FF;
                }
            }
            
            Image rgbImage = Image.createRGBImage(rgbData, size, size, true);
            g.drawImage(rgbImage, 10, y, Graphics.TOP | Graphics.LEFT);
            drawText("Checkerboard RGB");
            
            int[] gradientData = new int[size * size];
            for (int i = 0; i < gradientData.length; i++) {
                int x = i % size;
                int gray = (x * 255) / size;
                gradientData[i] = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
            }
            
            Image gradientImage = Image.createRGBImage(gradientData, size, size, true);
            g.drawImage(gradientImage, 40, y, Graphics.TOP | Graphics.LEFT);
            drawText("Gradient RGB");
            
            y = y + 5;
        }
        
        private void testComplexComposition() {
            drawSection("Complex Composition");
            
            Image redSquare = Image.createImage(15, 15);
            Graphics redG = redSquare.getGraphics();
            redG.setColor(255, 0, 0);
            redG.fillRect(0, 0, 15, 15);
            
            Image blueCircle = Image.createImage(15, 15);
            Graphics blueG = blueCircle.getGraphics();
            blueG.setColor(0, 0, 255);
            blueG.fillArc(0, 0, 15, 15, 0, 360);
            
            Image greenTriangle = Image.createImage(15, 15);
            Graphics greenG = greenTriangle.getGraphics();
            greenG.setColor(0, 200, 0);
            greenG.fillTriangle(7, 0, 0, 15, 15, 15);
            
            int startX = 10;
            int spacing = 20;
            
            g.drawImage(redSquare, startX, y, Graphics.TOP | Graphics.LEFT);
            g.drawImage(blueCircle, startX + spacing, y, Graphics.TOP | Graphics.LEFT);
            g.drawImage(greenTriangle, startX + spacing * 2, y, Graphics.TOP | Graphics.LEFT);
            g.drawImage(redSquare, startX + spacing * 3, y, Graphics.TOP | Graphics.LEFT);
            g.drawImage(blueCircle, startX + spacing * 4, y, Graphics.TOP | Graphics.LEFT);
            
            drawText("Shape pattern");
            
            Image canvas = Image.createImage(60, 40);
            Graphics canvasG = canvas.getGraphics();
            canvasG.setColor(240, 240, 240);
            canvasG.fillRect(0, 0, 60, 40);
            
            canvasG.drawImage(redSquare, 5, 5, Graphics.TOP | Graphics.LEFT);
            canvasG.drawImage(blueCircle, 25, 5, Graphics.TOP | Graphics.LEFT);
            canvasG.drawImage(greenTriangle, 45, 5, Graphics.TOP | Graphics.LEFT);
            canvasG.drawImage(blueCircle, 15, 20, Graphics.TOP | Graphics.LEFT);
            canvasG.drawImage(greenTriangle, 35, 20, Graphics.TOP | Graphics.LEFT);
            
            Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            canvasG.setFont(font);
            canvasG.setColor(0, 0, 0);
            canvasG.drawString("Layered", 2, 32, Graphics.TOP | Graphics.LEFT);
            
            g.drawImage(canvas, 10, y, Graphics.TOP | Graphics.LEFT);
            drawText("Multi-layer composition");
            
            y = y + 3;
            
            Image original = Image.createImage(25, 25);
            Graphics origG = original.getGraphics();
            origG.setColor(255, 200, 0);
            origG.fillRect(0, 0, 25, 25);
            origG.setColor(0, 0, 0);
            origG.drawLine(0, 0, 24, 24);
            origG.drawLine(0, 24, 24, 0);
            
            Image copy = Image.createImage(original);
            
            g.drawImage(original, 10, y, Graphics.TOP | Graphics.LEFT);
            g.drawImage(copy, 40, y, Graphics.TOP | Graphics.LEFT);
            drawText("Original and copy");
        }
    }
}
