/*
 * EGL 1.x stub header for WASM build
 */
#ifndef __egl_h_
#define __egl_h_

#include <GLES/gl.h>

typedef unsigned int    EGLBoolean;
typedef unsigned int    EGLenum;
typedef void*           EGLConfig;
typedef void*           EGLContext;
typedef void*           EGLDisplay;
typedef void*           EGLSurface;
typedef void*           EGLNativeDisplayType;
typedef void*           EGLNativeWindowType;
typedef void*           EGLNativePixmapType;
typedef int             EGLint;

#define EGL_FALSE                   0
#define EGL_TRUE                    1

#define EGL_DEFAULT_DISPLAY         ((EGLNativeDisplayType)0)
#define EGL_NO_CONTEXT              ((EGLContext)0)
#define EGL_NO_DISPLAY              ((EGLDisplay)0)
#define EGL_NO_SURFACE              ((EGLSurface)0)

#define EGL_SUCCESS                 0x3000
#define EGL_NOT_INITIALIZED         0x3001
#define EGL_BAD_ACCESS              0x3002
#define EGL_BAD_ALLOC               0x3003
#define EGL_BAD_ATTRIBUTE           0x3004
#define EGL_BAD_CONFIG              0x3005
#define EGL_BAD_CONTEXT             0x3006
#define EGL_BAD_CURRENT_SURFACE     0x3007
#define EGL_BAD_DISPLAY             0x3008
#define EGL_BAD_MATCH               0x3009
#define EGL_BAD_NATIVE_PIXMAP       0x300A
#define EGL_BAD_NATIVE_WINDOW       0x300B
#define EGL_BAD_PARAMETER           0x300C
#define EGL_BAD_SURFACE             0x300D

#define EGL_ALPHA_SIZE              0x3021
#define EGL_BLUE_SIZE               0x3022
#define EGL_GREEN_SIZE              0x3023
#define EGL_RED_SIZE                0x3024
#define EGL_DEPTH_SIZE              0x3025
#define EGL_STENCIL_SIZE            0x3026
#define EGL_CONFIG_ID               0x3028
#define EGL_NONE                    0x3038
#define EGL_SURFACE_TYPE            0x3033
#define EGL_PBUFFER_BIT             0x0001
#define EGL_WINDOW_BIT              0x0004
#define EGL_RENDERABLE_TYPE         0x3040
#define EGL_OPENGL_ES_BIT           0x0001

#define EGL_WIDTH                   0x3057
#define EGL_HEIGHT                  0x3058

#define EGL_MATCH_NATIVE_PIXMAP     0x3041
#define EGL_SAMPLE_BUFFERS          0x3032
#define EGL_SAMPLES                 0x3031
#define EGL_PIXMAP_BIT              0x0002
#define EGL_CONFIG_CAVEAT           0x3027
#define EGL_SLOW_CONFIG             0x3050
#define EGL_NON_CONFORMANT_CONFIG   0x3051

#define EGL_OPENGL_ES_API           0x30A0
#define EGL_VERSION                 0x3054
#define EGL_CORE_NATIVE_ENGINE      0x305B

typedef EGLNativeWindowType NativeWindowType;
typedef EGLNativePixmapType NativePixmapType;

EGLDisplay  eglGetDisplay(EGLNativeDisplayType display_id);
EGLBoolean  eglInitialize(EGLDisplay dpy, EGLint *major, EGLint *minor);
EGLBoolean  eglTerminate(EGLDisplay dpy);
EGLBoolean  eglChooseConfig(EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs, EGLint config_size, EGLint *num_config);
EGLBoolean  eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint *value);
EGLContext  eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint *attrib_list);
EGLBoolean  eglDestroyContext(EGLDisplay dpy, EGLContext ctx);
EGLSurface  eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint *attrib_list);
EGLSurface  eglCreatePixmapSurface(EGLDisplay dpy, EGLConfig config, EGLNativePixmapType pixmap, const EGLint *attrib_list);
EGLSurface  eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig config, const EGLint *attrib_list);
EGLBoolean  eglDestroySurface(EGLDisplay dpy, EGLSurface surface);
EGLBoolean  eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx);
EGLBoolean  eglSwapBuffers(EGLDisplay dpy, EGLSurface surface);
EGLBoolean  eglCopyBuffers(EGLDisplay dpy, EGLSurface surface, EGLNativePixmapType target);
EGLint      eglGetError(void);
EGLBoolean  eglBindAPI(EGLenum api);
EGLBoolean  eglWaitNative(EGLint engine);
EGLBoolean  eglQuerySurface(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint *value);
EGLBoolean  eglQueryContext(EGLDisplay dpy, EGLContext ctx, EGLint attribute, EGLint *value);
const char* eglQueryString(EGLDisplay dpy, EGLint name);
EGLDisplay  eglGetCurrentDisplay(void);

#endif /* __egl_h_ */
