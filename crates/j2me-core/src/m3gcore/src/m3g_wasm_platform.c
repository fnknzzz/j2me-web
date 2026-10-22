/*
 * M3G platform abstraction layer stub for WASM build.
 * Replaces m3g_symbian.cpp / m3g_symbian_gl.cpp
 */
#include "m3g_defs.h"
#include "m3g_gl.h"

typedef char M3GWasmMatrixSizeMustMatchJavaTransform[
    sizeof(M3GMatrix) == 76 ? 1 : -1
];

void *memcpy(void *dst, const void *src, size_t size)
{
    M3Gbyte *dstBytes = (M3Gbyte *) dst;
    const M3Gbyte *srcBytes = (const M3Gbyte *) src;
    while (size--) {
        *dstBytes++ = *srcBytes++;
    }
    return dst;
}

void *memmove(void *dst, const void *src, size_t size)
{
    M3Gbyte *dstBytes = (M3Gbyte *) dst;
    const M3Gbyte *srcBytes = (const M3Gbyte *) src;
    if (dstBytes <= srcBytes || (srcBytes + size) <= dstBytes) {
        while (size--) {
            *dstBytes++ = *srcBytes++;
        }
    } else {
        srcBytes += size - 1;
        dstBytes += size - 1;
        while (size--) {
            *dstBytes-- = *srcBytes--;
        }
    }
    return dst;
}

void *memset(void *ptr, int value, size_t size)
{
    M3Gbyte *bytes = (M3Gbyte *) ptr;
    while (size--) {
        *bytes++ = (M3Gbyte) value;
    }
    return ptr;
}

int memcmp(const void *left, const void *right, size_t size)
{
    const M3Gubyte *leftBytes = (const M3Gubyte *) left;
    const M3Gubyte *rightBytes = (const M3Gubyte *) right;
    while (size--) {
        if (*leftBytes != *rightBytes) {
            return (int) *leftBytes - (int) *rightBytes;
        }
        ++leftBytes;
        ++rightBytes;
    }
    return 0;
}

char *strstr(const char *haystack, const char *needle)
{
    const char *h;
    if (needle == NULL || needle[0] == '\0') {
        return (char *) haystack;
    }
    if (haystack == NULL) {
        return NULL;
    }
    for (h = haystack; *h != '\0'; ++h) {
        const char *a = h;
        const char *b = needle;
        while (*a != '\0' && *b != '\0' && *a == *b) {
            ++a;
            ++b;
        }
        if (*b == '\0') {
            return (char *) h;
        }
    }
    return NULL;
}

M3Gbool m3gglLockNativeBitmap(M3GNativeBitmap bitmap,
                              M3Gubyte **ptr,
                              M3Gsizei *stride)
{
    (void)bitmap; (void)ptr; (void)stride;
    return M3G_FALSE;
}

void m3gglReleaseNativeBitmap(M3GNativeBitmap bitmap)
{
    (void)bitmap;
}

M3Gbool m3gglGetNativeBitmapParams(M3GNativeBitmap bitmap,
                                   M3GPixelFormat *format,
                                   M3Gint *width, M3Gint *height, M3Gint *pixels)
{
    (void)bitmap; (void)format; (void)width; (void)height; (void)pixels;
    return M3G_FALSE;
}

M3Gbool m3gglGetNativeWindowParams(M3GNativeWindow wnd,
                                   M3GPixelFormat *format,
                                   M3Gint *width, M3Gint *height)
{
    (void)wnd; (void)format; (void)width; (void)height;
    return M3G_FALSE;
}
