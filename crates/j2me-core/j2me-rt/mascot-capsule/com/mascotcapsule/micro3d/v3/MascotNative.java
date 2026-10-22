/*
 * Copyright 2026 The jsjvm authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.mascotcapsule.micro3d.v3;

/**
 * Platform boundary for the imported Android implementation.
 *
 * The Micro3D model, animation, and command-list code remains Java. Calls
 * that previously crossed into Android or GLES cross this boundary instead;
 * Rust currently registers safe placeholder handlers until WebGPU support is
 * wired up.
 */
final class MascotNative {
    private MascotNative() {}

    static native byte[] loadResource(String name);
    static native void logError(String message);
    static native int graphicsWidth(javax.microedition.lcdui.Graphics graphics);
    static native int graphicsHeight(javax.microedition.lcdui.Graphics graphics);
    static native int[] graphicsClip(javax.microedition.lcdui.Graphics graphics);
    static native void uploadGraphicsTexture(javax.microedition.lcdui.Graphics graphics, int texture);
    static native void clearGraphics(javax.microedition.lcdui.Graphics graphics);
    static native void readGraphicsPixels(int x, int y, int width, int height, javax.microedition.lcdui.Graphics graphics);
    static native int decodeTexture(byte[] data);
    static native int textureWidth(int image);
    static native int textureHeight(int image);
    static native void uploadTexture(int image, int texture);
    static native void registerTexture(Texture texture, int image);
    static native void releaseTexture(Texture texture, int image);
    static native String loadAsset(String name);
    static native boolean getBooleanProperty(String name);
    static native void bindRenderer(Graphics3D renderer, javax.microedition.lcdui.Graphics graphics);
    static native void releaseRenderer(Graphics3D renderer);
    static native void flushRenderer(Graphics3D renderer);
    static native void drawCommandList(Graphics3D renderer, Texture[] textures,
                                       int x, int y, FigureLayout layout,
                                       Effect3D effect, int[] commandList);
    static native void renderPrimitives(Graphics3D renderer, Texture texture,
                                        int x, int y, FigureLayout layout,
                                        Effect3D effect, int command,
                                        int numPrimitives, int[] vertexCoords,
                                        int[] normals, int[] textureCoords,
                                        int[] colors);
    static native void renderFigure(Graphics3D renderer, Figure figure,
                                    int x, int y, FigureLayout layout,
                                    Effect3D effect);
    static native void drawFigure(Graphics3D renderer, Figure figure,
                                  int x, int y, FigureLayout layout,
                                  Effect3D effect);
}
