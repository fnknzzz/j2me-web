package com.mascotcapsule.micro3d.v3;

/** CLDC replacement for the Android-only Rect used by the renderer. */
final class MascotRect {
	int left, top, right, bottom;
	void set(int left, int top, int right, int bottom) {
		this.left = left; this.top = top; this.right = right; this.bottom = bottom;
	}
	int width() { return right - left; }
	int height() { return bottom - top; }
	boolean intersect(int left, int top, int right, int bottom) {
		if (left > this.left) this.left = left;
		if (top > this.top) this.top = top;
		if (right < this.right) this.right = right;
		if (bottom < this.bottom) this.bottom = bottom;
		return this.left < this.right && this.top < this.bottom;
	}
}
