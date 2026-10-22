/*
 * M3G public API header (reconstructed for WASM build)
 * Original: Nokia Corporation, Eclipse Public License v1.0
 */

#ifndef __M3G_CORE_H__
#define __M3G_CORE_H__

#if defined(__cplusplus)
extern "C" {
#endif

/*----------------------------------------------------------------------
 * Basic types
 *--------------------------------------------------------------------*/

typedef signed char     M3Gbyte;
typedef unsigned char   M3Gubyte;
typedef signed short    M3Gshort;
typedef unsigned short  M3Gushort;
typedef int             M3Gint;
typedef unsigned int    M3Guint;
typedef float           M3Gfloat;
typedef int             M3Gbool;
typedef int             M3Gsizei;
typedef unsigned int    M3Genum;
typedef unsigned int    M3Gbitfield;
typedef unsigned int    M3Gbitmask;

#define M3G_TRUE        1
#define M3G_FALSE       0

/*----------------------------------------------------------------------
 * Opaque handle types
 *--------------------------------------------------------------------*/

#include <stdint.h>
typedef void*       M3GMemObject;
typedef uintptr_t   M3GNativeBitmap;
typedef uintptr_t   M3GNativeWindow;

/*----------------------------------------------------------------------
 * Object handle types (opaque pointers)
 *--------------------------------------------------------------------*/

typedef /*@abstract@*/ struct M3GInterfaceImpl       *M3GInterface;
typedef /*@abstract@*/ struct M3GObjectImpl          *M3GObject;
typedef /*@abstract@*/ struct M3GRenderContextImpl   *M3GRenderContext;
typedef /*@abstract@*/ struct M3GWorldImpl           *M3GWorld;
typedef /*@abstract@*/ struct M3GGroupImpl           *M3GGroup;
typedef /*@abstract@*/ struct M3GNodeImpl            *M3GNode;
typedef /*@abstract@*/ struct M3GCameraImpl          *M3GCamera;
typedef /*@abstract@*/ struct M3GLightImpl           *M3GLight;
typedef /*@abstract@*/ struct M3GMeshImpl            *M3GMesh;
typedef /*@abstract@*/ struct M3GMorphingMeshImpl    *M3GMorphingMesh;
typedef /*@abstract@*/ struct M3GSkinnedMeshImpl     *M3GSkinnedMesh;
typedef /*@abstract@*/ struct M3GSpriteImpl          *M3GSprite;
typedef /*@abstract@*/ struct M3GAppearanceImpl      *M3GAppearance;
typedef /*@abstract@*/ struct M3GTextureImpl         *M3GTexture;
typedef /*@abstract@*/ struct M3GImageImpl           *M3GImage;
typedef /*@abstract@*/ struct M3GVertexBufferImpl    *M3GVertexBuffer;
typedef /*@abstract@*/ struct M3GVertexArrayImpl     *M3GVertexArray;
typedef /*@abstract@*/ struct M3GIndexBufferImpl     *M3GIndexBuffer;
typedef /*@abstract@*/ struct M3GMaterialImpl        *M3GMaterial;
typedef /*@abstract@*/ struct M3GFogImpl             *M3GFog;
typedef /*@abstract@*/ struct M3GCompositingModeImpl *M3GCompositingMode;
typedef /*@abstract@*/ struct M3GPolygonModeImpl     *M3GPolygonMode;
typedef /*@abstract@*/ struct M3GBackgroundImpl      *M3GBackground;
typedef /*@abstract@*/ struct M3GAnimationTrackImpl  *M3GAnimationTrack;
typedef /*@abstract@*/ struct M3GAnimationControllerImpl *M3GAnimationController;
typedef /*@abstract@*/ struct M3GKeyframeSequenceImpl *M3GKeyframeSequence;
typedef /*@abstract@*/ struct M3GTransformableImpl   *M3GTransformable;
typedef /*@abstract@*/ struct M3GLoaderImpl          *M3GLoader;

/*----------------------------------------------------------------------
 * Pixel formats
 *--------------------------------------------------------------------*/

typedef enum {
    M3G_ALPHA       = 96,
    M3G_LUMINANCE   = 97,
    M3G_LUMINANCE_ALPHA = 98,
    M3G_RGB         = 99,
    M3G_RGBA        = 100
} M3GImageFormat;

typedef enum {
    M3G_A8          = 1,
    M3G_L8          = 2,
    M3G_LA8         = 3,
    M3G_RGB8        = 4,
    M3G_RGB8_32     = 5,
    M3G_RGBA8       = 6,
    M3G_BGRA8       = 7,
    M3G_ARGB8       = 8,
    M3G_BGR8_32     = 9,
    M3G_RGB4        = 10,
    M3G_RGB565      = 11,
    M3G_RGBA4       = 12,
    M3G_LA4         = 13,
    M3G_PALETTE8_RGB8   = 14,
    M3G_PALETTE8_RGBA8  = 15,
    M3G_NUM_PIXEL_FORMATS
} M3GPixelFormat;

/*----------------------------------------------------------------------
 * Math types
 *--------------------------------------------------------------------*/

typedef struct { M3Gfloat x, y, z; } M3GVec3;
typedef struct { M3Gfloat x, y, z, w; } M3GVec4;
typedef struct { M3Gfloat x, y, z, w; } M3GQuat;
typedef struct {
    M3Gfloat elem[16];
    M3Guint mask;
    M3Gbool complete;
    M3Gbool classified;
} M3GMatrix;
typedef struct { M3Gint x, y, width, height; } M3GRectangle;

/*----------------------------------------------------------------------
 * Object class enumeration
 *--------------------------------------------------------------------*/

typedef enum {
    M3G_CLASS_ANIMATION_CONTROLLER = 1,
    M3G_CLASS_ANIMATION_TRACK,
    M3G_CLASS_APPEARANCE,
    M3G_CLASS_BACKGROUND,
    M3G_CLASS_CAMERA,
    M3G_CLASS_COMPOSITING_MODE,
    M3G_CLASS_FOG,
    M3G_CLASS_GROUP,
    M3G_CLASS_IMAGE,
    M3G_CLASS_INDEX_BUFFER,
    M3G_CLASS_KEYFRAME_SEQUENCE,
    M3G_CLASS_LIGHT,
    M3G_CLASS_LOADER,
    M3G_CLASS_MATERIAL,
    M3G_CLASS_MESH,
    M3G_CLASS_MORPHING_MESH,
    M3G_CLASS_NODE,
    M3G_CLASS_OBJECT,
    M3G_CLASS_POLYGON_MODE,
    M3G_CLASS_RENDER_CONTEXT,
    M3G_CLASS_SKINNED_MESH,
    M3G_CLASS_SPRITE,
    M3G_CLASS_TEXTURE,
    M3G_CLASS_TRANSFORMABLE,
    M3G_CLASS_TRIANGLE_STRIP_ARRAY,
    M3G_CLASS_VERTEX_ARRAY,
    M3G_CLASS_VERTEX_BUFFER,
    M3G_CLASS_WORLD
} M3GClass;

/*----------------------------------------------------------------------
 * Error codes
 *--------------------------------------------------------------------*/

#define M3G_NO_ERROR            0
#define M3G_OUT_OF_MEMORY       1
#define M3G_INVALID_VALUE       2
#define M3G_INVALID_ENUM        3
#define M3G_INVALID_OPERATION   4
#define M3G_INVALID_OBJECT      5
#define M3G_INVALID_INDEX       6
#define M3G_IO_ERROR            7
#define M3G_NULL_POINTER        8
#define M3G_ARITHMETIC_ERROR    9

/* Blending mode constants (used in CompositingMode) */
#define M3G_REPLACE             0
#define M3G_ALPHA_BLEND         1
#define M3G_MODULATE_BLEND      2
#define M3G_MODULATE_X2_BLEND   3
#define M3G_ADD_BLEND           4

/*----------------------------------------------------------------------
 * Interface initialization parameters
 *--------------------------------------------------------------------*/

/*----------------------------------------------------------------------
 * Public API function declarations (subset used by JNI layer)
 *--------------------------------------------------------------------*/

/* Interface */
M3GInterface m3gCreateInterface(const M3Gparams *params);
void         m3gDeleteInterface(M3GInterface interface);
M3Genum      m3gGetError(M3GInterface interface);

/* RenderContext */
M3GRenderContext m3gCreateContext(M3GInterface interface);

/* Generic object */
void    m3gDeleteObject(M3GObject obj);
void    m3gAddRef(M3GObject obj);
void    m3gDeleteRef(M3GObject obj);
M3Gint  m3gGetRefCount(M3GObject obj);
M3GClass m3gGetClass(M3GObject obj);

#if defined(__cplusplus)
} /* extern "C" */
#endif

#endif /* __M3G_CORE_H__ */
