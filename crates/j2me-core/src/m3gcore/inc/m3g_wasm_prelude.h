/*
 * Pre-include header for m3gcore WASM build.
 * Overrides problematic macros and adds missing definitions.
 */
#ifndef __M3G_WASM_PRELUDE_H__
#define __M3G_WASM_PRELUDE_H__

/* Disable compile-time asserts (struct sizes differ on 64-bit host) */
#define M3G_CT_ASSERT(a)
#define M3G_CT_ASSERT1(a)
#define M3G_CT_ASSERT2(a)

/* Missing type */
typedef unsigned int M3Gbitmask;

/* Missing error type */
typedef int M3GError;
typedef void (*m3gErrorHandler)(M3GError error, void *userData);

/* M3G_API export macro - not needed for static linking */
#define M3G_API

/* EGLSurface alias used by m3gcore */
typedef void* M3GEGLSurface;

/* Function pointer types used by Interface */
typedef void* (m3gMallocFunc)(unsigned int bytes);
typedef void  (m3gFreeFunc)(void *ptr);
typedef void* (m3gObjectAllocator)(unsigned int bytes);
typedef void  (m3gObjectDeallocator)(void *ptr);
typedef void* (m3gObjectResolver)(void *handle);
typedef void  (m3gBeginRenderFunc)(void *target);
typedef void  (m3gEndRenderFunc)(void *target);
typedef void  (m3gReleaseTargetFunc)(void *target);

void m3gWasmSetTextureImageHandle(unsigned int texture, const void *image);
unsigned int m3gWasmResolveStableHandle(const void *object);
void m3gWasmRegisterExternalVertexArray(unsigned int handle);
void m3gWasmRegisterVertexArrayPointer(unsigned int handle, const void *pointer);
void m3gWasmRegisterIndexBufferPointer(unsigned int handle, const void *pointer);
void m3gWasmEnsureIndexBufferEmitted(
    unsigned int handle,
    unsigned int glType,
    int indexCount,
    int stripCount,
    const unsigned short *lengths,
    const void *indices);
unsigned int m3gWasmBumpResourceVersion(unsigned int kind, unsigned int handle);
void m3gWasmReleaseResourceVersion(unsigned int kind, unsigned int handle);
int m3gWasmBindGlesContext(void *owner);
void m3gWasmDestroyGlesContext(void *owner);
int m3gWasmAppendCommandBytes(void *sink, const unsigned char *ptr, unsigned int len);
void *m3gWasmAllocateBytes(unsigned int bytes);
void m3gWasmFreeBytes(void *ptr);

/* M3Gparams - params struct for m3gCreateInterface (lowercase alias) */
typedef struct {
    m3gMallocFunc *mallocFunc;
    m3gFreeFunc *freeFunc;
    m3gObjectAllocator *objAllocFunc;
    m3gObjectDeallocator *objFreeFunc;
    m3gObjectResolver *objResolveFunc;
    m3gErrorHandler *errorFunc;
    m3gBeginRenderFunc *beginRenderFunc;
    m3gEndRenderFunc *endRenderFunc;
    m3gReleaseTargetFunc *releaseTargetFunc;
    void *userContext;
} M3Gparams;

/* Primitive types */
typedef int M3Gprimitive;
typedef int M3Gdatatype;

#define M3G_TRIANGLE_STRIPS     1
#define M3G_TRIANGLES           2

/* Data types */
#define M3G_BYTE                1
#define M3G_UBYTE               2
#define M3G_SHORT               3
#define M3G_USHORT              4
#define M3G_INT                 5
#define M3G_UINT                6
#define M3G_FLOAT               7

/* Image internal flags */
#define M3G_PALETTED            0x10
#define M3G_NO_FORMAT           0
#define M3G_PALETTE8_RGB8_32    17
#define M3G_ABSTRACT_CLASS      0xFF

/* GL OES palette extensions */
#define GL_PALETTE8_RGB8_OES    0x8B95
#define GL_PALETTE8_RGBA8_OES   0x8B96

/* Missing M3G buffer bit constants */
#define M3G_DEPTH_BUFFER_BIT        0x01
#define M3G_COLOR_BUFFER_BIT        0x02
#define M3G_MULTISAMPLE_BUFFER_BIT  0x04
#define M3G_STENCIL_BUFFER_BIT      0x08
#define M3G_OVERWRITE_BIT           0x10
#define M3G_ANTIALIAS_BIT           0x20
#define M3G_DITHER_BIT              0x40

/* Blending mode constants */
#define M3G_REPLACE             0
#define M3G_ALPHA_BLEND         1
#define M3G_MODULATE_BLEND      2
#define M3G_MODULATE_X2_BLEND   3
#define M3G_ADD_BLEND           4

/* Culling constants */
#define M3G_CULL_NONE           0
#define M3G_CULL_BACK           1
#define M3G_CULL_FRONT          2

/* Pixel format constants missing from m3g_core.h */
#define M3G_RGB5A1              16
#define M3G_RGB565_PIXEL        17

/* Shading constants */
#define M3G_SHADE_FLAT          0
#define M3G_SHADE_SMOOTH        1

/* Winding constants */
#define M3G_WINDING_CCW         0
#define M3G_WINDING_CW          1

/* Rendering hint bits */
#define M3G_TRUECOLOR_BIT       0x80

/* Image flags */
#define M3G_DYNAMIC             0x01
#define M3G_STATIC              0x02
#define M3G_RENDERING_TARGET    0x04

/* Animation target property IDs (JSR-184 spec values) */
#define M3G_ANIM_ALPHA              256
#define M3G_ANIM_AMBIENT_COLOR      257
#define M3G_ANIM_COLOR              258
#define M3G_ANIM_CROP               259
#define M3G_ANIM_DENSITY            260
#define M3G_ANIM_DIFFUSE_COLOR      261
#define M3G_ANIM_EMISSIVE_COLOR     262
#define M3G_ANIM_FAR_DISTANCE       263
#define M3G_ANIM_FIELD_OF_VIEW      264
#define M3G_ANIM_INTENSITY          265
#define M3G_ANIM_MORPH_WEIGHTS      266
#define M3G_ANIM_NEAR_DISTANCE      267
#define M3G_ANIM_ORIENTATION        268
#define M3G_ANIM_PICKABILITY        269
#define M3G_ANIM_SCALE              270
#define M3G_ANIM_SHININESS          271
#define M3G_ANIM_SPECULAR_COLOR     272
#define M3G_ANIM_SPOT_ANGLE         273
#define M3G_ANIM_SPOT_EXPONENT      274
#define M3G_ANIM_TRANSLATION        275
#define M3G_ANIM_VISIBILITY         276

/* Texture wrap modes */
#define M3G_BORDER              32
#define M3G_REPEAT              33

/* Texture wrap modes (Texture2D) */
#define M3G_WRAP_CLAMP          240
#define M3G_WRAP_REPEAT         241

/* Texture filter modes */
#define M3G_FILTER_BASE_LEVEL   208
#define M3G_FILTER_LINEAR       209
#define M3G_FILTER_NEAREST      210

/* Camera projection types */
#define M3G_GENERIC             48
#define M3G_PARALLEL            49
#define M3G_PERSPECTIVE         50

/* Background/Sprite getter IDs */
#define M3G_GET_MODEX           0
#define M3G_GET_MODEY           1
#define M3G_GET_CROPX           2
#define M3G_GET_CROPY           3
#define M3G_GET_CROPWIDTH       4
#define M3G_GET_CROPHEIGHT      5

/* Background/Node setget IDs */
#define M3G_SETGET_COLORCLEAR   0
#define M3G_SETGET_DEPTHCLEAR   1
#define M3G_SETGET_RENDERING    2
#define M3G_SETGET_PICKING      3

/* Profiling statistics enum (M3Gstatistic) - values as indices into stat array */
typedef int M3Gstatistic;
#define M3G_STAT_MEMORY_ALLOCS          0
#define M3G_STAT_MEMORY_ALLOCATED       1
#define M3G_STAT_MEMORY_MALLOC_BYTES    2
#define M3G_STAT_MEMORY_OBJECT_BYTES    3
#define M3G_STAT_MEMORY_PEAK            4
#define M3G_STAT_MEMORY_MALLOC_PEAK     5
#define M3G_STAT_MEMORY_OBJECT_PEAK     6
#define M3G_STAT_MEMORY_LOCKS           7
#define M3G_STAT_RENDERABLES            8
#define M3G_STAT_OBJECTS                9
#define M3G_STAT_CUMULATIVE             10
#define M3G_PROFILE_NGL_DRAW            11
#define M3G_PROFILE_VALIDATE            12
#define M3G_PROFILE_SETUP               13
#define M3G_PROFILE_COMMIT              14
#define M3G_PROFILE_SETUP_TRANSFORMS    15
#define M3G_PROFILE_SETUP_SORT          16
#define M3G_STAT_BOUNDING_BOXES         17
#define M3G_STAT_RENDER_NODES           18
#define M3G_STAT_RENDER_NODES_CULLED    19
#define M3G_PROFILE_VFC_UPDATE          20
#define M3G_PROFILE_TRANSFORM_INVERT    21
#define M3G_PROFILE_MORPH               22
#define M3G_PROFILE_VFC_TEST            23
#define M3G_PROFILE_TRANSFORM_TO        24
#define M3G_STAT_CULLING_TESTS          25
#define M3G_PROFILE_ANIM                26
#define M3G_STAT_RENDERQUEUE_SIZE       27
#define M3G_STAT_RENDER_NODES_DRAWN     28
#define M3G_PROFILE_SKIN                29
#define M3G_STAT_TCACHE_COMPOSITE_COLLISIONS 30
#define M3G_STAT_TCACHE_COMPOSITE_LOAD  31
#define M3G_STAT_TCACHE_COMPOSITE_HITS  32
#define M3G_STAT_TCACHE_COMPOSITE_INSERTS 33
#define M3G_STAT_TCACHE_COMPOSITE_MISSES 34
#define M3G_STAT_TCACHE_PATH_COLLISIONS 35
#define M3G_STAT_TCACHE_PATH_FLUSHES    36
#define M3G_STAT_TCACHE_PATH_HITS       37
#define M3G_STAT_TCACHE_PATH_INSERTS    38
#define M3G_STAT_TCACHE_PATH_LOAD       39
#define M3G_STAT_TCACHE_PATH_MISSES     40
#define M3G_PROFILE_LOADER_DECODE       41
#define M3G_PROFILE_PICK                42
#define M3G_PROFILE_TCACHE              43
#define M3G_STAT_MAX                    48

/* Fog types */
#define M3G_EXPONENTIAL_FOG     80
#define M3G_LINEAR_FOG          81

/* Light types (short form used internally) */
#define M3G_AMBIENT_LIGHT       128
#define M3G_DIRECTIONAL_LIGHT   129
#define M3G_OMNI_LIGHT          130
#define M3G_SPOT_LIGHT          131
#define M3G_AMBIENT             128
#define M3G_DIRECTIONAL         129
#define M3G_OMNI                130
#define M3G_SPOT                131

/* CompositingMode blending (internal names) */
#define M3G_ALPHA_ADD           5
#define M3G_MODULATE            6
#define M3G_MODULATE_X2         7

/* CompositingMode depth test functions */
#define M3G_ALWAYS              0
#define M3G_NEVER               1
#define M3G_EQUAL               2
#define M3G_NOT_EQUAL           3
#define M3G_LESS                4
#define M3G_LESS_OR_EQUAL       5
#define M3G_GREATER             6
#define M3G_GREATER_OR_EQUAL    7

/* Texture combine modes */
#define M3G_TEX_FUNC_ADD        0
#define M3G_TEX_FUNC_BLEND      1
#define M3G_TEX_FUNC_DECAL      2
#define M3G_TEX_FUNC_MODULATE   3
#define M3G_TEX_FUNC_REPLACE    4
#define M3G_FUNC_ADD            0
#define M3G_FUNC_BLEND          1
#define M3G_FUNC_DECAL          2
#define M3G_FUNC_MODULATE       3
#define M3G_FUNC_REPLACE        4

/* Texture combine source */
#define M3G_TEX_SRC_RGB         0
#define M3G_TEX_SRC_ALPHA       1

/* Keyframe interpolation types */
#define M3G_LINEAR              176
#define M3G_SLERP               177
#define M3G_SPLINE              178
#define M3G_SQUAD               179
#define M3G_STEP                180

/* Keyframe repeat mode */
#define M3G_CONSTANT            192
#define M3G_LOOP                193

/* Keyframe sequence open/closed */
#define M3G_OPEN                0
#define M3G_CLOSED              1

/* Node alpha factor inherit */
#define M3G_NONE                144
#define M3G_ORIGIN              145
#define M3G_X_AXIS              146
#define M3G_Y_AXIS              147
#define M3G_Z_AXIS              148

/* Fog getter IDs */
#define M3G_GET_NEAR            0
#define M3G_GET_FAR             1

/* Light attenuation getter IDs */
#define M3G_GET_CONSTANT        0
#define M3G_GET_LINEAR          1
#define M3G_GET_QUADRATIC       2

/* VertexBuffer getter IDs */
#define M3G_GET_POSITIONS       0
#define M3G_GET_NORMALS         1
#define M3G_GET_COLORS          2
#define M3G_GET_TEXCOORDS0      3

/* Material color target bits */
#define M3G_AMBIENT_BIT         0x0400
#define M3G_DIFFUSE_BIT         0x0800
#define M3G_EMISSIVE_BIT        0x1000
#define M3G_SPECULAR_BIT        0x2000

/* Short-name aliases for matrix operations used throughout m3gcore.
 * The actual implementations are m3gPost* variants in m3g_math.c. */
#define m3gMulMatrix        m3gPostMultiplyMatrix
#define m3gRightMulMatrix   m3gPostMultiplyMatrix
#define m3gScaleMatrix      m3gPostScaleMatrix
#define m3gTranslateMatrix  m3gPostTranslateMatrix
#define m3gRotateMatrixQuat m3gPostRotateMatrixQuat

#endif /* __M3G_WASM_PRELUDE_H__ */
