/*
 * OpenGL ES 1.x fixed-function WebGPU shim for the WASM build.
 * This translates the m3gcore GLES client-array path into WebGPU commands.
 */
#include <GLES/gl.h>
#include <EGL/egl.h>
#include <math.h>
#include <stddef.h>
#include <string.h>
#include "m3g_webgpu_wire.h"

#define M3G_CMD_CREATE_INDEX_BUFFER 150
#define M3G_CMD_BEGIN_WGPU_BATCH 164
#define M3G_CMD_END_WGPU_BATCH 165
#define M3G_CMD_UPDATE_TEXTURE 147
#define M3G_CMD_DELETE_RESOURCE 148
#define M3G_RESOURCE_VERTEX_ARRAY 1u
#define M3G_RESOURCE_GL_TEXTURE 2u
#define M3G_RESOURCE_INDEX_BUFFER 4u
#define WGPU_TOPOLOGY_TRIANGLE_LIST 0
#define WGPU_TOPOLOGY_TRIANGLE_STRIP 1
#define WGPU_TOPOLOGY_TRIANGLE_FAN 2
#define WGPU_DRAW_HAS_VERTEX_COLOR M3G_WGPU_PIPELINE_HAS_VERTEX_COLOR
#define WGPU_DRAW_HAS_TEXTURE M3G_WGPU_PIPELINE_HAS_TEXTURE
#define WGPU_DRAW_HAS_LIGHTING M3G_WGPU_PIPELINE_HAS_LIGHTING
#define WGPU_DRAW_HAS_FOG M3G_WGPU_PIPELINE_HAS_FOG
#define WGPU_DRAW_HAS_ALPHA_TEST M3G_WGPU_PIPELINE_HAS_ALPHA_TEST
#define WGPU_DRAW_FLAT_SHADING M3G_WGPU_PIPELINE_FLAT_SHADING
#define WGPU_DRAW_PERSPECTIVE_CORRECTION M3G_WGPU_PIPELINE_PERSPECTIVE_CORRECTION
#define WGPU_DRAW_BLEND_SHIFT M3G_WGPU_PIPELINE_BLEND_SHIFT
#define WGPU_DRAW_WRITE_MASK_SHIFT M3G_WGPU_PIPELINE_WRITE_MASK_SHIFT
#define WGPU_DRAW_COLOR_MASK_RED_OFF (1u << (WGPU_DRAW_WRITE_MASK_SHIFT + 0u))
#define WGPU_DRAW_COLOR_MASK_GREEN_OFF (1u << (WGPU_DRAW_WRITE_MASK_SHIFT + 1u))
#define WGPU_DRAW_COLOR_MASK_BLUE_OFF (1u << (WGPU_DRAW_WRITE_MASK_SHIFT + 2u))
#define WGPU_DRAW_COLOR_MASK_ALPHA_OFF (1u << (WGPU_DRAW_WRITE_MASK_SHIFT + 3u))
#define M3G_DRAW_PACKET_FRAME_INDEX 0x01u
#define M3G_DRAW_PACKET_FRAME_POSITION 0x02u
#define M3G_DRAW_PACKET_FRAME_COLOR 0x04u
#define M3G_DRAW_PACKET_FRAME_NORMAL 0x08u
#define M3G_DRAW_PACKET_FRAME_TEXCOORD0 0x10u
#define M3G_DRAW_PACKET_FRAME_TEXCOORD1 0x20u
/* Cull face state — encoded as three orthogonal bits so the TS renderer can
 * pick a cull mode ('back' or 'front') and a front-face winding ('ccw' or
 * 'cw'). When the enable bit is clear the pipeline uses cullMode='none'. */
#define WGPU_DRAW_CULL_FACE M3G_WGPU_PIPELINE_CULL_FACE
#define WGPU_DRAW_CULL_FRONT M3G_WGPU_PIPELINE_CULL_FRONT
#define WGPU_DRAW_FRONT_CW M3G_WGPU_PIPELINE_FRONT_CW
/* Depth pipeline state. `TEST_ALWAYS` corresponds to glDepthFunc(GL_ALWAYS)
 * (M3G CompositingMode.setDepthTestEnable(false)). `WRITE_OFF` corresponds
 * to glDepthMask(GL_FALSE) (setDepthWriteEnable(false)). */
#define WGPU_DRAW_DEPTH_TEST_ALWAYS M3G_WGPU_PIPELINE_DEPTH_TEST_ALWAYS
#define WGPU_DRAW_DEPTH_WRITE_OFF M3G_WGPU_PIPELINE_DEPTH_WRITE_OFF
#define COMMAND_SCRATCH_SIZE (16u * 1024u)
#define MATRIX_STACK_DEPTH 16
#define MAX_TEXTURE_UNITS 2
#define MAX_VERTEX_ARRAY_POINTERS 256
#define MAX_WGPU_INSTANCE_BUCKETS 32
#define MAX_WGPU_INSTANCES_PER_PACKET 256
#define MAX_GLES1_WEBGPU_CONTEXTS 8
#define MAX_GLES_RESOURCE_VERSIONS 2048
#define MAX_DERIVED_TRIANGLE_INDICES 128
#define DERIVED_TRIANGLE_FAN 1u
#define DERIVED_TRIANGLE_FAN_FLAT 2u
#define DERIVED_TRIANGLE_STRIP_FLAT 3u

typedef struct {
    GLint size;
    GLenum type;
    GLsizei stride;
    const GLvoid *pointer;
    GLboolean enabled;
} ArrayPointerState;

typedef struct {
    GLboolean enabled;
    GLfloat ambient[4];
    GLfloat diffuse[4];
    GLfloat specular[4];
    GLfloat position[4];
    GLfloat spot_direction[3];
    GLfloat spot_exponent;
    GLfloat spot_cutoff;
    GLfloat constant_attenuation;
    GLfloat linear_attenuation;
    GLfloat quadratic_attenuation;
} LightState;

typedef struct {
    GLuint name;
    unsigned int source_image_handle;
    GLsizei width;
    GLsizei height;
    GLenum format;
    GLenum min_filter;
    GLenum mag_filter;
    GLenum wrap_s;
    GLenum wrap_t;
    GLenum env_mode;
    GLfloat env_color[4];
    unsigned char *pixels;
    unsigned int pixels_size;
    unsigned int upload_version;
} TextureState;

typedef struct {
    const void *pointer;
    unsigned int handle;
} VertexArrayPointerMapEntry;

typedef struct {
    const void *pointer;
    unsigned int handle;
} IndexBufferPointerMapEntry;

typedef struct {
    unsigned int kind;
    unsigned int handle;
    unsigned int version;
} ResourceVersionEntry;

typedef struct {
    unsigned int source_kind;
    unsigned int source_handle;
    unsigned int source_version;
    unsigned int transform_kind;
    int first;
    unsigned int count;
    unsigned int handle;
    unsigned int version;
    unsigned int last_use;
} DerivedTriangleIndexEntry;

typedef struct {
    VertexArrayPointerMapEntry vertex_array_pointer_map[MAX_VERTEX_ARRAY_POINTERS];
    unsigned int vertex_array_pointer_count;
    unsigned int external_vertex_arrays[MAX_VERTEX_ARRAY_POINTERS];
    unsigned int external_vertex_array_count;
    IndexBufferPointerMapEntry index_buffer_pointer_map[MAX_VERTEX_ARRAY_POINTERS];
    unsigned int index_buffer_pointer_count;
    ResourceVersionEntry versions[MAX_GLES_RESOURCE_VERSIONS];
    unsigned int version_count;
    DerivedTriangleIndexEntry
        derived_triangle_indices[MAX_DERIVED_TRIANGLE_INDICES];
    unsigned int derived_triangle_index_count;
    unsigned int derived_triangle_next_handle;
    unsigned int derived_triangle_use_serial;
} GlesResourceRegistry;

typedef struct {
    unsigned int topology;
    unsigned int pipeline_key;
    unsigned int position_handle;
    unsigned int position_version;
    unsigned int color_handle;
    unsigned int color_version;
    unsigned int index_handle;
    unsigned int index_version;
    GLint viewport[4];
    GLint scissor[4];
    unsigned int index_count;
    unsigned int instance_count;
    GLfloat mvp[MAX_WGPU_INSTANCES_PER_PACKET][16];
} DrawPacketBucket;

typedef struct {
    void *owner;
    TextureState *texture_objects;
    unsigned int texture_object_count;
    unsigned int texture_object_capacity;
    GLenum gl_error;
    ArrayPointerState vertex_pointer;
    ArrayPointerState normal_pointer;
    ArrayPointerState color_pointer;
    ArrayPointerState texcoord_pointers[MAX_TEXTURE_UNITS];
    GLfixed current_color[4];
    GLboolean texture_2d_enabled[MAX_TEXTURE_UNITS];
    GLuint bound_texture_2d[MAX_TEXTURE_UNITS];
    int active_texture_unit;
    int client_active_texture_unit;
    GLboolean lighting_enabled;
    GLboolean normalize_enabled;
    GLboolean color_material_enabled;
    GLboolean two_sided_lighting;
    GLboolean blend_enabled;
    GLenum blend_src_factor;
    GLenum blend_dst_factor;
    GLboolean cull_face_enabled;
    GLenum cull_face_mode;
    GLenum front_face_mode;
    GLenum depth_func_mode;
    GLboolean depth_mask_enabled;
    GLclampf depth_near;
    GLclampf depth_far;
    GLboolean color_mask_red;
    GLboolean color_mask_green;
    GLboolean color_mask_blue;
    GLboolean color_mask_alpha;
    GLboolean polygon_offset_fill_enabled;
    GLfloat polygon_offset_factor;
    GLfloat polygon_offset_units;
    GLboolean alpha_test_enabled;
    GLenum alpha_test_func;
    GLclampf alpha_test_ref;
    GLenum shade_model_mode;
    GLenum perspective_correction_hint;
    GLboolean fog_enabled;
    GLenum fog_mode;
    GLfloat fog_density;
    GLfloat fog_start;
    GLfloat fog_end;
    GLfloat fog_color[4];
    GLenum texture_env_mode[MAX_TEXTURE_UNITS];
    GLfloat texture_env_color[MAX_TEXTURE_UNITS][4];
    GLfloat light_model_ambient[4];
    GLfloat material_ambient[4];
    GLfloat material_diffuse[4];
    GLfloat material_emission[4];
    GLfloat material_specular[4];
    GLfloat material_shininess;
    LightState lights[8];
    GLint viewport_x;
    GLint viewport_y;
    GLsizei viewport_width;
    GLsizei viewport_height;
    GLboolean scissor_enabled;
    GLint scissor_x;
    GLint scissor_y;
    GLsizei scissor_width;
    GLsizei scissor_height;
    GLfloat modelview_matrix[16];
    GLfloat projection_matrix[16];
    GLfloat texture_matrices[MAX_TEXTURE_UNITS][16];
    GLfloat modelview_stack[MATRIX_STACK_DEPTH][16];
    GLfloat projection_stack[MATRIX_STACK_DEPTH][16];
    GLfloat texture_stack[MATRIX_STACK_DEPTH][16];
    int modelview_stack_size;
    int projection_stack_size;
    int texture_stack_size;
    GLenum current_matrix_mode;
} Gles1WebGpuContext;

typedef struct {
    GLboolean capture_enabled;
    GLboolean batch_started;
    void *sink;
    unsigned char scratch[COMMAND_SCRATCH_SIZE];
    unsigned int size;
    GLboolean overflowed;
    DrawPacketBucket buckets[MAX_WGPU_INSTANCE_BUCKETS];
    unsigned int bucket_count;
    unsigned char packet[
        M3G_DRAW_PACKET_BYTES(0u, MAX_WGPU_INSTANCES_PER_PACKET)
    ];
} GlesDrawRecorder;

static GLuint gl_next_texture = 1;
static GlesDrawRecorder draw_recorder;
static GlesResourceRegistry resource_registry;
static Gles1WebGpuContext gles1_contexts[MAX_GLES1_WEBGPU_CONTEXTS];
static Gles1WebGpuContext *current_gles1_context = NULL;

#define vertex_array_pointer_map      (resource_registry.vertex_array_pointer_map)
#define vertex_array_pointer_count    (resource_registry.vertex_array_pointer_count)
#define external_vertex_arrays        (resource_registry.external_vertex_arrays)
#define external_vertex_array_count   (resource_registry.external_vertex_array_count)
#define index_buffer_pointer_map      (resource_registry.index_buffer_pointer_map)
#define index_buffer_pointer_count    (resource_registry.index_buffer_pointer_count)
#define command_capture_enabled       (draw_recorder.capture_enabled)
#define capture_draw_batch_started    (draw_recorder.batch_started)
#define command_sink                  (draw_recorder.sink)
#define command_scratch               (draw_recorder.scratch)
#define command_size                  (draw_recorder.size)
#define command_overflowed            (draw_recorder.overflowed)
#define draw_packet_buckets           (draw_recorder.buckets)
#define draw_packet_bucket_count      (draw_recorder.bucket_count)
#define draw_packet_bytes             (draw_recorder.packet)

#define gl_error                    (current_gles1_context->gl_error)
#define vertex_pointer              (current_gles1_context->vertex_pointer)
#define normal_pointer              (current_gles1_context->normal_pointer)
#define color_pointer               (current_gles1_context->color_pointer)
#define texcoord_pointers           (current_gles1_context->texcoord_pointers)
#define current_color               (current_gles1_context->current_color)
#define texture_2d_enabled          (current_gles1_context->texture_2d_enabled)
#define bound_texture_2d            (current_gles1_context->bound_texture_2d)
#define active_texture_unit         (current_gles1_context->active_texture_unit)
#define client_active_texture_unit  (current_gles1_context->client_active_texture_unit)
#define lighting_enabled            (current_gles1_context->lighting_enabled)
#define normalize_enabled           (current_gles1_context->normalize_enabled)
#define color_material_enabled      (current_gles1_context->color_material_enabled)
#define two_sided_lighting          (current_gles1_context->two_sided_lighting)
#define blend_enabled               (current_gles1_context->blend_enabled)
#define blend_src_factor            (current_gles1_context->blend_src_factor)
#define blend_dst_factor            (current_gles1_context->blend_dst_factor)
#define cull_face_enabled           (current_gles1_context->cull_face_enabled)
#define cull_face_mode              (current_gles1_context->cull_face_mode)
#define front_face_mode             (current_gles1_context->front_face_mode)
#define depth_func_mode             (current_gles1_context->depth_func_mode)
#define depth_mask_enabled          (current_gles1_context->depth_mask_enabled)
#define depth_near                  (current_gles1_context->depth_near)
#define depth_far                   (current_gles1_context->depth_far)
#define color_mask_red              (current_gles1_context->color_mask_red)
#define color_mask_green            (current_gles1_context->color_mask_green)
#define color_mask_blue             (current_gles1_context->color_mask_blue)
#define color_mask_alpha            (current_gles1_context->color_mask_alpha)
#define polygon_offset_fill_enabled (current_gles1_context->polygon_offset_fill_enabled)
#define polygon_offset_factor       (current_gles1_context->polygon_offset_factor)
#define polygon_offset_units        (current_gles1_context->polygon_offset_units)
#define alpha_test_enabled          (current_gles1_context->alpha_test_enabled)
#define alpha_test_func             (current_gles1_context->alpha_test_func)
#define alpha_test_ref              (current_gles1_context->alpha_test_ref)
#define shade_model_mode            (current_gles1_context->shade_model_mode)
#define perspective_correction_hint (current_gles1_context->perspective_correction_hint)
#define fog_enabled                 (current_gles1_context->fog_enabled)
#define fog_mode                    (current_gles1_context->fog_mode)
#define fog_density                 (current_gles1_context->fog_density)
#define fog_start                   (current_gles1_context->fog_start)
#define fog_end                     (current_gles1_context->fog_end)
#define fog_color                   (current_gles1_context->fog_color)
#define texture_env_mode            (current_gles1_context->texture_env_mode)
#define texture_env_color           (current_gles1_context->texture_env_color)
#define light_model_ambient         (current_gles1_context->light_model_ambient)
#define material_ambient            (current_gles1_context->material_ambient)
#define material_diffuse            (current_gles1_context->material_diffuse)
#define material_emission           (current_gles1_context->material_emission)
#define material_specular           (current_gles1_context->material_specular)
#define material_shininess          (current_gles1_context->material_shininess)
#define lights                      (current_gles1_context->lights)
#define viewport_x                  (current_gles1_context->viewport_x)
#define viewport_y                  (current_gles1_context->viewport_y)
#define viewport_width              (current_gles1_context->viewport_width)
#define viewport_height             (current_gles1_context->viewport_height)
#define scissor_enabled             (current_gles1_context->scissor_enabled)
#define scissor_x                   (current_gles1_context->scissor_x)
#define scissor_y                   (current_gles1_context->scissor_y)
#define scissor_width               (current_gles1_context->scissor_width)
#define scissor_height              (current_gles1_context->scissor_height)
#define modelview_matrix            (current_gles1_context->modelview_matrix)
#define projection_matrix           (current_gles1_context->projection_matrix)
#define texture_matrices            (current_gles1_context->texture_matrices)
#define modelview_stack             (current_gles1_context->modelview_stack)
#define projection_stack            (current_gles1_context->projection_stack)
#define texture_stack               (current_gles1_context->texture_stack)
#define modelview_stack_size        (current_gles1_context->modelview_stack_size)
#define projection_stack_size       (current_gles1_context->projection_stack_size)
#define texture_stack_size          (current_gles1_context->texture_stack_size)
#define current_matrix_mode         (current_gles1_context->current_matrix_mode)

static GLboolean flush_draw_packet_buckets(void);

static void initialize_gles1_webgpu_context(Gles1WebGpuContext *ctx)
{
    int i;
    int light_index;
    int component;
    Gles1WebGpuContext *saved_context = current_gles1_context;

    for (i = 0; i < (int)sizeof(Gles1WebGpuContext); i++) {
        ((unsigned char *)ctx)[i] = 0;
    }
    current_gles1_context = ctx;
    gl_error = GL_NO_ERROR;
    normal_pointer.size = 3;
    for (i = 0; i < 4; i++) {
        current_color[i] = 65536;
        light_model_ambient[i] = i == 3 ? 1.0f : 0.0f;
        material_ambient[i] = i == 3 ? 1.0f : 0.2f;
        material_diffuse[i] = i == 3 ? 1.0f : 0.8f;
        material_emission[i] = i == 3 ? 1.0f : 0.0f;
        material_specular[i] = i == 3 ? 1.0f : 0.0f;
        fog_color[i] = i == 3 ? 1.0f : 0.0f;
    }
    for (i = 0; i < MAX_TEXTURE_UNITS; i++) {
        texture_env_mode[i] = GL_MODULATE;
        texture_matrices[i][0] = 1.0f;
        texture_matrices[i][5] = 1.0f;
        texture_matrices[i][10] = 1.0f;
        texture_matrices[i][15] = 1.0f;
    }
    blend_src_factor = GL_ONE;
    blend_dst_factor = GL_ZERO;
    cull_face_mode = GL_BACK;
    front_face_mode = GL_CCW;
    depth_func_mode = GL_LESS;
    depth_mask_enabled = GL_TRUE;
    depth_near = 0.0f;
    depth_far = 1.0f;
    color_mask_red = GL_TRUE;
    color_mask_green = GL_TRUE;
    color_mask_blue = GL_TRUE;
    color_mask_alpha = GL_TRUE;
    alpha_test_func = GL_ALWAYS;
    shade_model_mode = GL_SMOOTH;
    perspective_correction_hint = GL_FASTEST;
    fog_mode = GL_EXP;
    fog_density = 1.0f;
    fog_end = 1.0f;
    modelview_matrix[0] = 1.0f;
    modelview_matrix[5] = 1.0f;
    modelview_matrix[10] = 1.0f;
    modelview_matrix[15] = 1.0f;
    projection_matrix[0] = 1.0f;
    projection_matrix[5] = 1.0f;
    projection_matrix[10] = 1.0f;
    projection_matrix[15] = 1.0f;
    current_matrix_mode = GL_MODELVIEW;
    for (light_index = 0; light_index < 8; light_index++) {
        LightState *light = &lights[light_index];
        light->position[2] = 1.0f;
        light->spot_direction[2] = -1.0f;
        light->spot_cutoff = 180.0f;
        light->constant_attenuation = 1.0f;
        light->ambient[3] = 1.0f;
        if (light_index == 0) {
            for (component = 0; component < 4; component++) {
                light->diffuse[component] = 1.0f;
                light->specular[component] = 1.0f;
            }
        }
    }
    current_gles1_context = saved_context;
}

static void release_gles1_texture_pixels(Gles1WebGpuContext *ctx)
{
    unsigned int texture_index;
    for (texture_index = 0; texture_index < ctx->texture_object_count; texture_index++) {
        TextureState *texture = &ctx->texture_objects[texture_index];
        if (texture->pixels) {
            m3gWasmFreeBytes(texture->pixels);
        }
        texture->pixels = NULL;
        texture->pixels_size = 0;
    }
    m3gWasmFreeBytes(ctx->texture_objects);
    ctx->texture_objects = NULL;
    ctx->texture_object_count = 0;
    ctx->texture_object_capacity = 0;
}

static int create_gles1_webgpu_context(void *owner)
{
    int i;
    if (!owner) {
        return 0;
    }
    for (i = 0; i < MAX_GLES1_WEBGPU_CONTEXTS; i++) {
        if (gles1_contexts[i].owner == owner) {
            return 1;
        }
    }
    for (i = 0; i < MAX_GLES1_WEBGPU_CONTEXTS; i++) {
        if (!gles1_contexts[i].owner) {
            initialize_gles1_webgpu_context(&gles1_contexts[i]);
            gles1_contexts[i].owner = owner;
            return 1;
        }
    }
    return 0;
}

int m3gWasmBindGlesContext(void *owner)
{
    int i;
    if (!owner) {
        current_gles1_context = NULL;
        return 1;
    }
    for (i = 0; i < MAX_GLES1_WEBGPU_CONTEXTS; i++) {
        if (gles1_contexts[i].owner == owner) {
            current_gles1_context = &gles1_contexts[i];
            return 1;
        }
    }
    if (create_gles1_webgpu_context(owner)) {
        return m3gWasmBindGlesContext(owner);
    }
    current_gles1_context = NULL;
    return 0;
}

void m3gWasmDestroyGlesContext(void *owner)
{
    int i;
    if (!owner) {
        return;
    }
    for (i = 0; i < MAX_GLES1_WEBGPU_CONTEXTS; i++) {
        if (gles1_contexts[i].owner == owner) {
            if (current_gles1_context == &gles1_contexts[i]) {
                current_gles1_context = NULL;
            }
            release_gles1_texture_pixels(&gles1_contexts[i]);
            initialize_gles1_webgpu_context(&gles1_contexts[i]);
            return;
        }
    }
}

static GLboolean flush_command_scratch(void)
{
    if (command_size == 0) {
        return GL_TRUE;
    }
    if (!command_sink || !m3gWasmAppendCommandBytes(command_sink, command_scratch, command_size)) {
        command_size = 0;
        command_overflowed = GL_TRUE;
        gl_error = GL_OUT_OF_MEMORY;
        return GL_FALSE;
    }
    command_size = 0;
    return GL_TRUE;
}

static GLboolean reserve_command_bytes(unsigned int size)
{
    if (command_overflowed) {
        return GL_FALSE;
    }
    if (size > COMMAND_SCRATCH_SIZE) {
        if (!flush_command_scratch()) {
            return GL_FALSE;
        }
        return GL_TRUE;
    }
    if (size > COMMAND_SCRATCH_SIZE - command_size) {
        return flush_command_scratch();
    }
    return GL_TRUE;
}

static GLboolean write_large_bytes(const unsigned char *src, unsigned int size)
{
    if (!flush_command_scratch()) {
        return GL_FALSE;
    }
    if (!command_sink || !m3gWasmAppendCommandBytes(command_sink, src, size)) {
        command_overflowed = GL_TRUE;
        gl_error = GL_OUT_OF_MEMORY;
        return GL_FALSE;
    }
    return GL_TRUE;
}

static void write_u8(unsigned char v)
{
    if (reserve_command_bytes(1u)) {
        command_scratch[command_size++] = v;
    }
}

static void write_u32(unsigned int v)
{
    write_u8((unsigned char)(v & 0xff));
    write_u8((unsigned char)((v >> 8) & 0xff));
    write_u8((unsigned char)((v >> 16) & 0xff));
    write_u8((unsigned char)((v >> 24) & 0xff));
}

static void write_i32(int v)
{
    write_u32((unsigned int)v);
}

static void write_f32(GLfloat v)
{
    union {
        GLfloat f;
        unsigned int u;
    } u;
    u.f = v;
    write_u32(u.u);
}

static void write_bytes(const unsigned char *src, unsigned int size)
{
    unsigned int i;
    if (size > COMMAND_SCRATCH_SIZE) {
        (void)write_large_bytes(src, size);
        return;
    }
    if (!reserve_command_bytes(size)) {
        return;
    }
    for (i = 0; i < size; i++) {
        command_scratch[command_size++] = src[i];
    }
}

static GLboolean emit_texture_update(TextureState *tex)
{
    unsigned int bytes;
    if (!command_capture_enabled || !tex || !tex->pixels || tex->width <= 0 || tex->height <= 0) {
        return GL_FALSE;
    }
    if (!flush_draw_packet_buckets()) {
        return GL_FALSE;
    }
    bytes = (unsigned int)(tex->width * tex->height * 4);
    if (!reserve_command_bytes(25u + bytes)) {
        gl_error = GL_OUT_OF_MEMORY;
        return GL_FALSE;
    }
    tex->upload_version++;
    if (tex->upload_version == 0) {
        tex->upload_version = 1;
    }
    write_u8(M3G_CMD_UPDATE_TEXTURE);
    write_u32(tex->name);
    write_u32(tex->source_image_handle);
    write_u32(tex->upload_version);
    write_u32((unsigned int)tex->width);
    write_u32((unsigned int)tex->height);
    write_u32(bytes);
    write_bytes(tex->pixels, bytes);
    return GL_TRUE;
}

static ResourceVersionEntry *resource_version_entry(
    unsigned int kind,
    unsigned int handle,
    GLboolean create)
{
    unsigned int index;
    if (!handle) {
        return NULL;
    }
    for (index = 0; index < resource_registry.version_count; index++) {
        ResourceVersionEntry *entry = &resource_registry.versions[index];
        if (entry->kind == kind && entry->handle == handle) {
            return entry;
        }
    }
    if (!create || resource_registry.version_count >= MAX_GLES_RESOURCE_VERSIONS) {
        return NULL;
    }
    {
        ResourceVersionEntry *entry =
            &resource_registry.versions[resource_registry.version_count++];
        entry->kind = kind;
        entry->handle = handle;
        entry->version = 0u;
        return entry;
    }
}

static unsigned int resource_version(unsigned int kind, unsigned int handle)
{
    ResourceVersionEntry *entry = resource_version_entry(kind, handle, GL_TRUE);
    return entry ? entry->version : 0u;
}

static void resource_version_remove(unsigned int kind, unsigned int handle);

static void resource_version_bump(unsigned int kind, unsigned int handle)
{
    ResourceVersionEntry *entry = resource_version_entry(kind, handle, GL_TRUE);
    unsigned int index;
    if (!entry) {
        return;
    }
    entry->version++;
    if (entry->version == 0u) {
        entry->version = 1u;
    }
    for (index = 0; index < resource_registry.derived_triangle_index_count;) {
        DerivedTriangleIndexEntry *derived =
            &resource_registry.derived_triangle_indices[index];
        if (derived->source_kind == kind && derived->source_handle == handle) {
            resource_version_remove(
                M3G_RESOURCE_INDEX_BUFFER,
                derived->handle);
            resource_registry.derived_triangle_indices[index] =
                resource_registry.derived_triangle_indices[
                    resource_registry.derived_triangle_index_count - 1u];
            resource_registry.derived_triangle_index_count--;
            continue;
        }
        index++;
    }
}

static void resource_version_remove(unsigned int kind, unsigned int handle)
{
    unsigned int index;
    for (index = 0; index < resource_registry.version_count; index++) {
        ResourceVersionEntry *entry = &resource_registry.versions[index];
        if (entry->kind == kind && entry->handle == handle) {
            resource_registry.versions[index] =
                resource_registry.versions[resource_registry.version_count - 1u];
            resource_registry.version_count--;
            return;
        }
    }
}

static void invalidate_derived_triangle_indices(
    unsigned int source_kind,
    unsigned int source_handle)
{
    unsigned int index;
    for (index = 0; index < resource_registry.derived_triangle_index_count;) {
        DerivedTriangleIndexEntry *derived =
            &resource_registry.derived_triangle_indices[index];
        if (derived->source_kind == source_kind
            && derived->source_handle == source_handle) {
            resource_registry.derived_triangle_indices[index] =
                resource_registry.derived_triangle_indices[
                    resource_registry.derived_triangle_index_count - 1u];
            resource_registry.derived_triangle_index_count--;
            continue;
        }
        index++;
    }
}

static GLboolean is_external_vertex_array(unsigned int handle)
{
    unsigned int i;
    for (i = 0; i < external_vertex_array_count; i++) {
        if (external_vertex_arrays[i] == handle) {
            return GL_TRUE;
        }
    }
    return GL_FALSE;
}

static void unregister_external_vertex_array(unsigned int handle)
{
    unsigned int index;
    for (index = 0; index < external_vertex_array_count; index++) {
        if (external_vertex_arrays[index] == handle) {
            external_vertex_arrays[index] =
                external_vertex_arrays[external_vertex_array_count - 1u];
            external_vertex_array_count--;
            break;
        }
    }
    for (index = 0; index < vertex_array_pointer_count;) {
        if (vertex_array_pointer_map[index].handle == handle) {
            vertex_array_pointer_map[index] =
                vertex_array_pointer_map[vertex_array_pointer_count - 1u];
            vertex_array_pointer_count--;
            continue;
        }
        index++;
    }
}

static unsigned int vertex_array_handle_for_pointer(const void *pointer)
{
    unsigned int i;
    if (!pointer) {
        return 0;
    }
    for (i = 0; i < vertex_array_pointer_count; i++) {
        if (vertex_array_pointer_map[i].pointer == pointer) {
            return vertex_array_pointer_map[i].handle;
        }
    }
    return 0;
}

static unsigned int index_buffer_handle_for_pointer(const void *pointer)
{
    unsigned int i;
    if (!pointer) {
        return 0;
    }
    for (i = 0; i < index_buffer_pointer_count; i++) {
        if (index_buffer_pointer_map[i].pointer == pointer) {
            return index_buffer_pointer_map[i].handle;
        }
    }
    return 0;
}

static GLboolean array_pointer_has_stable_source(const ArrayPointerState *state)
{
    unsigned int handle;
    return state->enabled
        && state->pointer
        && (handle = vertex_array_handle_for_pointer(state->pointer)) != 0
        && resource_version(M3G_RESOURCE_VERTEX_ARRAY, handle) != 0;
}

static GLboolean color_pointer_has_stable_source(void)
{
    if (!array_pointer_has_stable_source(&color_pointer)) {
        return GL_FALSE;
    }
    if (color_pointer.type == GL_UNSIGNED_BYTE || color_pointer.type == GL_BYTE) {
        return color_pointer.size == 3 || color_pointer.size == 4;
    }
    return color_pointer.size == 3 || color_pointer.size == 4;
}

static GLboolean can_use_stable_index_source(unsigned int flags, TextureState *active_texture1)
{
    if (!array_pointer_has_stable_source(&vertex_pointer)) {
        return GL_FALSE;
    }
    if ((flags & WGPU_DRAW_HAS_VERTEX_COLOR) != 0
        && color_pointer.enabled
        && color_pointer.pointer
        && !color_pointer_has_stable_source()) {
        return GL_FALSE;
    }
    if (lighting_enabled && !array_pointer_has_stable_source(&normal_pointer)) {
        return GL_FALSE;
    }
    if ((flags & WGPU_DRAW_HAS_TEXTURE) != 0
        && !array_pointer_has_stable_source(&texcoord_pointers[0])) {
        return GL_FALSE;
    }
    if (active_texture1 && !array_pointer_has_stable_source(&texcoord_pointers[1])) {
        return GL_FALSE;
    }
    return GL_TRUE;
}

static void matrix_identity(GLfloat *m)
{
    int i;
    for (i = 0; i < 16; i++) {
        m[i] = (i % 5) == 0 ? 1.0f : 0.0f;
    }
}

static GLfloat *current_matrix(void)
{
    switch (current_matrix_mode) {
    case GL_PROJECTION:
        return projection_matrix;
    case GL_TEXTURE:
        return texture_matrices[active_texture_unit];
    case GL_MODELVIEW:
    default:
        return modelview_matrix;
    }
}

static GLfloat (*current_matrix_stack(void))[16]
{
    switch (current_matrix_mode) {
    case GL_PROJECTION:
        return projection_stack;
    case GL_TEXTURE:
        return texture_stack;
    case GL_MODELVIEW:
    default:
        return modelview_stack;
    }
}

static int *current_matrix_stack_size(void)
{
    switch (current_matrix_mode) {
    case GL_PROJECTION:
        return &projection_stack_size;
    case GL_TEXTURE:
        return &texture_stack_size;
    case GL_MODELVIEW:
    default:
        return &modelview_stack_size;
    }
}

static void matrix_copy(GLfloat *dst, const GLfloat *src)
{
    int i;
    for (i = 0; i < 16; i++) {
        dst[i] = src[i];
    }
}

static void matrix_multiply(GLfloat *dst, const GLfloat *left, const GLfloat *right)
{
    GLfloat out[16];
    int col, row, k;
    for (col = 0; col < 4; col++) {
        for (row = 0; row < 4; row++) {
            GLfloat sum = 0.0f;
            for (k = 0; k < 4; k++) {
                sum += left[k * 4 + row] * right[col * 4 + k];
            }
            out[col * 4 + row] = sum;
        }
    }
    for (k = 0; k < 16; k++) {
        dst[k] = out[k];
    }
}

static void matrix_translate(GLfloat *m, GLfloat x, GLfloat y, GLfloat z)
{
    GLfloat t[16];
    matrix_identity(t);
    t[12] = x;
    t[13] = y;
    t[14] = z;
    matrix_multiply(m, m, t);
}

static void matrix_scale(GLfloat *m, GLfloat x, GLfloat y, GLfloat z)
{
    GLfloat s[16];
    matrix_identity(s);
    s[0] = x;
    s[5] = y;
    s[10] = z;
    matrix_multiply(m, m, s);
}

static void matrix_ortho(GLfloat *m, GLfloat left, GLfloat right, GLfloat bottom, GLfloat top, GLfloat near, GLfloat far)
{
    matrix_identity(m);
    m[0] = 2.0f / (right - left);
    m[5] = 2.0f / (top - bottom);
    m[10] = -2.0f / (far - near);
    m[12] = -(right + left) / (right - left);
    m[13] = -(top + bottom) / (top - bottom);
    m[14] = -(far + near) / (far - near);
}

static void matrix_gl_to_wgpu_clip(GLfloat *dst, const GLfloat *src)
{
    int col;
    for (col = 0; col < 4; col++) {
        dst[col * 4 + 0] = src[col * 4 + 0];
        dst[col * 4 + 1] = src[col * 4 + 1];
        dst[col * 4 + 2] = 0.5f * src[col * 4 + 2] + 0.5f * src[col * 4 + 3];
        dst[col * 4 + 3] = src[col * 4 + 3];
    }
}

static void matrix_apply_depth_range(GLfloat *matrix, GLclampf near, GLclampf far)
{
    int col;
    for (col = 0; col < 4; col++) {
        matrix[col * 4 + 2] =
            near * matrix[col * 4 + 3]
            + (far - near) * matrix[col * 4 + 2];
    }
}

static void clear_draw_packet_buckets(void)
{
    draw_packet_bucket_count = 0;
}

static void draw_packet_write_u32(unsigned int *offset, unsigned int value)
{
    draw_packet_bytes[*offset + 0] = (unsigned char)(value & 0xff);
    draw_packet_bytes[*offset + 1] = (unsigned char)((value >> 8) & 0xff);
    draw_packet_bytes[*offset + 2] = (unsigned char)((value >> 16) & 0xff);
    draw_packet_bytes[*offset + 3] = (unsigned char)((value >> 24) & 0xff);
    *offset += 4;
}

static void draw_packet_write_f32(unsigned int *offset, GLfloat value)
{
    union {
        GLfloat f;
        unsigned int u;
    } bits;
    bits.f = value;
    draw_packet_write_u32(offset, bits.u);
}

static void write_packet_u32_to(
    unsigned char *target,
    unsigned int *offset,
    unsigned int value)
{
    target[*offset + 0] = (unsigned char)(value & 0xff);
    target[*offset + 1] = (unsigned char)((value >> 8) & 0xff);
    target[*offset + 2] = (unsigned char)((value >> 16) & 0xff);
    target[*offset + 3] = (unsigned char)((value >> 24) & 0xff);
    *offset += 4;
}

static void write_packet_f32_to(
    unsigned char *target,
    unsigned int *offset,
    GLfloat value)
{
    union {
        GLfloat f;
        unsigned int u;
    } bits;
    bits.f = value;
    write_packet_u32_to(target, offset, bits.u);
}

static void read_vertex_position(int index, GLfloat *out);
static void read_vertex_normal(int index, GLfloat *out);
static void read_texcoord_for_unit(int unit, int index, GLfloat *out);

static void write_draw_packet_textured_uniform_to(
    unsigned char *target,
    unsigned int *offset,
    const GLfloat *mvp,
    TextureState *texture0,
    TextureState *texture1)
{
    unsigned int i;
    for (i = 0; i < 16; i++) write_packet_f32_to(target, offset, mvp[i]);
    for (i = 0; i < 16; i++) write_packet_f32_to(target, offset, modelview_matrix[i]);
    for (i = 0; i < 16; i++) write_packet_f32_to(target, offset, texture_matrices[0][i]);
    for (i = 0; i < 16; i++) write_packet_f32_to(target, offset, texture_matrices[1][i]);
    write_packet_f32_to(target, offset, (GLfloat)viewport_x);
    write_packet_f32_to(target, offset, (GLfloat)viewport_y);
    write_packet_f32_to(target, offset, (GLfloat)viewport_width);
    write_packet_f32_to(target, offset, (GLfloat)viewport_height);
    for (i = 0; i < 4; i++) {
        write_packet_f32_to(target, offset, texture_env_color[0][i]);
    }
    for (i = 0; i < 4; i++) {
        write_packet_f32_to(target, offset, texture_env_color[1][i]);
    }
    write_packet_u32_to(
        target,
        offset,
        texture0 ? (unsigned int)texture_env_mode[0] : GL_MODULATE);
    write_packet_u32_to(
        target,
        offset,
        texture1 ? (unsigned int)texture_env_mode[1] : 0u);
    write_packet_u32_to(target, offset, blend_enabled ? 1u : 0u);
    write_packet_u32_to(target, offset, texture1 ? 1u : 0u);
    write_packet_f32_to(
        target,
        offset,
        alpha_test_enabled ? alpha_test_ref : 0.0f);
    write_packet_u32_to(target, offset, (unsigned int)fog_mode);
    write_packet_u32_to(target, offset, fog_enabled ? 1u : 0u);
    write_packet_u32_to(target, offset, lighting_enabled ? 1u : 0u);
    write_packet_f32_to(target, offset, fog_density);
    write_packet_f32_to(target, offset, fog_start);
    write_packet_f32_to(target, offset, fog_end);
    write_packet_f32_to(target, offset, 0.0f);
    for (i = 0; i < 4; i++) {
        write_packet_f32_to(target, offset, fog_color[i]);
    }
    write_packet_u32_to(target, offset, lighting_enabled ? 1u : 0u);
    write_packet_u32_to(target, offset, normalize_enabled ? 1u : 0u);
    write_packet_u32_to(target, offset, color_material_enabled ? 1u : 0u);
    write_packet_u32_to(target, offset, two_sided_lighting ? 1u : 0u);
    for (i = 0; i < 4; i++) write_packet_f32_to(target, offset, light_model_ambient[i]);
    for (i = 0; i < 4; i++) write_packet_f32_to(target, offset, material_ambient[i]);
    for (i = 0; i < 4; i++) write_packet_f32_to(target, offset, material_diffuse[i]);
    for (i = 0; i < 4; i++) write_packet_f32_to(target, offset, material_emission[i]);
    for (i = 0; i < 4; i++) write_packet_f32_to(target, offset, material_specular[i]);
    write_packet_f32_to(target, offset, material_shininess);
    write_packet_f32_to(target, offset, 0.0f);
    write_packet_f32_to(target, offset, 0.0f);
    write_packet_f32_to(target, offset, 0.0f);
    for (i = 0; i < 8; i++) {
        const LightState *light = &lights[i];
        unsigned int component;
        write_packet_f32_to(target, offset, light->enabled ? 1.0f : 0.0f);
        write_packet_f32_to(target, offset, light->spot_exponent);
        write_packet_f32_to(target, offset, light->spot_cutoff);
        write_packet_f32_to(target, offset, light->constant_attenuation);
        for (component = 0; component < 4; component++) {
            write_packet_f32_to(target, offset, light->ambient[component]);
        }
        for (component = 0; component < 4; component++) {
            write_packet_f32_to(target, offset, light->diffuse[component]);
        }
        for (component = 0; component < 4; component++) {
            write_packet_f32_to(target, offset, light->specular[component]);
        }
        for (component = 0; component < 4; component++) {
            write_packet_f32_to(target, offset, light->position[component]);
        }
        write_packet_f32_to(target, offset, light->spot_direction[0]);
        write_packet_f32_to(target, offset, light->spot_direction[1]);
        write_packet_f32_to(target, offset, light->spot_direction[2]);
        write_packet_f32_to(target, offset, 0.0f);
        write_packet_f32_to(target, offset, light->linear_attenuation);
        write_packet_f32_to(target, offset, light->quadratic_attenuation);
        write_packet_f32_to(target, offset, 0.0f);
        write_packet_f32_to(target, offset, 0.0f);
    }
    write_packet_u32_to(
        target,
        offset,
        texture0 ? (unsigned int)texture0->wrap_s : GL_CLAMP_TO_EDGE);
    write_packet_u32_to(
        target,
        offset,
        texture0 ? (unsigned int)texture0->wrap_t : GL_CLAMP_TO_EDGE);
    write_packet_u32_to(
        target,
        offset,
        texture0 ? (unsigned int)texture0->min_filter : GL_NEAREST);
    write_packet_u32_to(
        target,
        offset,
        texture0 ? (unsigned int)texture0->mag_filter : GL_NEAREST);
    write_packet_u32_to(
        target,
        offset,
        texture1 ? (unsigned int)texture1->wrap_s : GL_CLAMP_TO_EDGE);
    write_packet_u32_to(
        target,
        offset,
        texture1 ? (unsigned int)texture1->wrap_t : GL_CLAMP_TO_EDGE);
    write_packet_u32_to(
        target,
        offset,
        texture1 ? (unsigned int)texture1->min_filter : GL_NEAREST);
    write_packet_u32_to(
        target,
        offset,
        texture1 ? (unsigned int)texture1->mag_filter : GL_NEAREST);
}

static GLboolean write_draw_packet_textured_uniform(
    unsigned char *target,
    unsigned int *offset,
    const GLfloat *mvp,
    TextureState *texture0,
    TextureState *texture1)
{
    unsigned int uniform_start = *offset;
    write_draw_packet_textured_uniform_to(
        target,
        offset,
        mvp,
        texture0,
        texture1);
    return *offset - uniform_start == M3G_DRAW_PACKET_UNIFORM_BYTES;
}

static GLboolean flush_draw_packet_buckets(void)
{
    unsigned int bucket_index;
    for (bucket_index = 0; bucket_index < draw_packet_bucket_count; bucket_index++) {
        const DrawPacketBucket *bucket = &draw_packet_buckets[bucket_index];
        unsigned int offset = 0;
        unsigned int matrix_index;
        unsigned int value_index;

        if (bucket->instance_count == 0) {
            continue;
        }
        draw_packet_bytes[offset++] = M3G_DRAW_PACKET_COMMAND_ID;
        draw_packet_write_u32(&offset, bucket->topology);
        draw_packet_write_u32(&offset, bucket->pipeline_key);
        draw_packet_write_u32(&offset, bucket->position_handle);
        draw_packet_write_u32(&offset, bucket->position_version);
        draw_packet_write_u32(&offset, bucket->color_handle);
        draw_packet_write_u32(&offset, bucket->color_version);
        /* normal */
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        /* texcoord0 */
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        /* index */
        draw_packet_write_u32(&offset, bucket->index_handle);
        draw_packet_write_u32(&offset, bucket->index_version);
        /* texture0 */
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        /* texcoord1 */
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        /* texture1 */
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        for (value_index = 0; value_index < 4; value_index++) {
            draw_packet_write_u32(&offset, (unsigned int)bucket->viewport[value_index]);
        }
        for (value_index = 0; value_index < 4; value_index++) {
            draw_packet_write_u32(&offset, (unsigned int)bucket->scissor[value_index]);
        }
        draw_packet_write_u32(&offset, bucket->index_count);
        draw_packet_write_u32(&offset, bucket->instance_count);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_u32(&offset, 0u);
        draw_packet_write_f32(&offset, 0.0f);
        draw_packet_write_f32(&offset, 0.0f);
        for (value_index = 0; value_index < 4; value_index++) {
            draw_packet_write_f32(&offset, value_index == 3 ? 1.0f : 0.0f);
        }
        if (offset != 1u + M3G_DRAW_PACKET_UNIFORM_OFFSET) {
            clear_draw_packet_buckets();
            gl_error = GL_OUT_OF_MEMORY;
            return GL_FALSE;
        }
        if (!write_draw_packet_textured_uniform(
                draw_packet_bytes,
                &offset,
                bucket->mvp[0],
                NULL,
                NULL)) {
            clear_draw_packet_buckets();
            gl_error = GL_OUT_OF_MEMORY;
            return GL_FALSE;
        }
        if (offset != 1u + M3G_DRAW_PACKET_HEADER_BYTES) {
            clear_draw_packet_buckets();
            gl_error = GL_OUT_OF_MEMORY;
            return GL_FALSE;
        }
        for (matrix_index = 0; matrix_index < bucket->instance_count; matrix_index++) {
            unsigned int component_index;
            for (component_index = 0; component_index < 16; component_index++) {
                draw_packet_write_f32(&offset, bucket->mvp[matrix_index][component_index]);
            }
        }
        if (!write_large_bytes(draw_packet_bytes, offset)) {
            clear_draw_packet_buckets();
            return GL_FALSE;
        }
    }
    clear_draw_packet_buckets();
    return GL_TRUE;
}

static const unsigned char *array_element_ptr(const ArrayPointerState *state, int index)
{
    int component_size;
    int stride;
    if (!state->pointer) {
        return NULL;
    }
    switch (state->type) {
    case GL_BYTE:
    case GL_UNSIGNED_BYTE:
        component_size = 1;
        break;
    case GL_SHORT:
    case GL_UNSIGNED_SHORT:
        component_size = 2;
        break;
    default:
        component_size = 4;
        break;
    }
    stride = state->stride != 0 ? state->stride : state->size * component_size;
    return (const unsigned char *)state->pointer + index * stride;
}

static GLfloat read_component(const unsigned char *ptr, GLenum type, int component)
{
    switch (type) {
    case GL_BYTE:
        return (GLfloat)((const signed char *)ptr)[component];
    case GL_UNSIGNED_BYTE:
        return (GLfloat)((const unsigned char *)ptr)[component];
    case GL_SHORT:
        return (GLfloat)((const GLshort *)ptr)[component];
    case GL_UNSIGNED_SHORT:
        return (GLfloat)((const GLushort *)ptr)[component];
    case GL_FIXED:
        return ((const GLfixed *)ptr)[component] / 65536.0f;
    case GL_FLOAT:
    default:
        return ((const GLfloat *)ptr)[component];
    }
}

static GLfloat read_signed_component(const unsigned char *ptr, GLenum type, int component)
{
    switch (type) {
    case GL_UNSIGNED_BYTE:
    case GL_BYTE:
        return (GLfloat)((const signed char *)ptr)[component];
    case GL_UNSIGNED_SHORT:
    case GL_SHORT:
        return (GLfloat)((const GLshort *)ptr)[component];
    case GL_FIXED:
        return ((const GLfixed *)ptr)[component] / 65536.0f;
    case GL_FLOAT:
    default:
        return ((const GLfloat *)ptr)[component];
    }
}

static TextureState *texture_state(GLuint name, GLboolean create)
{
    unsigned int i;
    TextureState *empty = NULL;
    if (name == 0) return NULL;
    for (i = 0; i < current_gles1_context->texture_object_count; i++) {
        if (current_gles1_context->texture_objects[i].name == name) {
            return &current_gles1_context->texture_objects[i];
        }
        if (!empty && current_gles1_context->texture_objects[i].name == 0) {
            empty = &current_gles1_context->texture_objects[i];
        }
    }
    if (create && !empty) {
        unsigned int capacity = current_gles1_context->texture_object_capacity;
        if (current_gles1_context->texture_object_count >= capacity) {
            unsigned int new_capacity = capacity ? capacity * 2u : 16u;
            TextureState *objects;

            if (new_capacity < capacity
                    || new_capacity > ((unsigned int)-1) / (unsigned int)sizeof(TextureState)) {
                gl_error = GL_OUT_OF_MEMORY;
                return NULL;
            }
            objects = (TextureState *)m3gWasmAllocateBytes(
                new_capacity * (unsigned int)sizeof(TextureState));
            if (!objects) {
                gl_error = GL_OUT_OF_MEMORY;
                return NULL;
            }
            memset(objects, 0, new_capacity * sizeof(TextureState));
            if (capacity) {
                memcpy(objects,
                       current_gles1_context->texture_objects,
                       capacity * sizeof(TextureState));
            }
            m3gWasmFreeBytes(current_gles1_context->texture_objects);
            current_gles1_context->texture_objects = objects;
            current_gles1_context->texture_object_capacity = new_capacity;
        }
        empty = &current_gles1_context->texture_objects[
            current_gles1_context->texture_object_count++];
    }
    if (create && empty) {
        empty->name = name;
        empty->min_filter = GL_NEAREST;
        empty->mag_filter = GL_NEAREST;
        empty->wrap_s = GL_CLAMP_TO_EDGE;
        empty->wrap_t = GL_CLAMP_TO_EDGE;
        empty->env_mode = GL_REPLACE;
        empty->env_color[0] = empty->env_color[1] = empty->env_color[2] = 0.0f;
        empty->env_color[3] = 1.0f;
        return empty;
    }
    return NULL;
}

static int texture_unit_index(GLenum texture)
{
    if (texture >= GL_TEXTURE0 && texture < GL_TEXTURE0 + MAX_TEXTURE_UNITS) {
        return (int)(texture - GL_TEXTURE0);
    }
    return 0;
}

static int bytes_per_pixel(GLenum format)
{
    switch (format) {
    case GL_ALPHA:
    case GL_LUMINANCE:
        return 1;
    case GL_LUMINANCE_ALPHA:
        return 2;
    case GL_RGB:
        return 3;
    case GL_RGBA:
        return 4;
    default:
        return 0;
    }
}

static GLboolean ensure_texture_pixels(TextureState *tex, unsigned int size)
{
    unsigned char *pixels;
    if (!tex) {
        return GL_FALSE;
    }
    if (tex->pixels_size < size) {
        pixels = (unsigned char *)m3gWasmAllocateBytes(size);
        if (!pixels) {
            gl_error = GL_OUT_OF_MEMORY;
            return GL_FALSE;
        }
        if (tex->pixels) {
            m3gWasmFreeBytes(tex->pixels);
        }
        tex->pixels = pixels;
        tex->pixels_size = size;
    }
    return GL_TRUE;
}

static void copy_texture_rgba(TextureState *tex, GLenum format, GLsizei width, GLsizei height, const GLvoid *pixels)
{
    const unsigned char *src = (const unsigned char *)pixels;
    unsigned int count;
    unsigned int i;
    unsigned int size;
    int bpp = bytes_per_pixel(format);
    if (!tex || !src || width <= 0 || height <= 0 || bpp <= 0) return;
    count = (unsigned int)(width * height);
    size = count * 4u;
    if (!ensure_texture_pixels(tex, size)) {
        return;
    }
    tex->width = width;
    tex->height = height;
    tex->format = GL_RGBA;
    for (i = 0; i < count; i++) {
        unsigned char r = 255, g = 255, b = 255, a = 255;
        const unsigned char *p = src + i * bpp;
        switch (format) {
        case GL_ALPHA:
            a = p[0];
            break;
        case GL_LUMINANCE:
            r = g = b = p[0];
            break;
        case GL_LUMINANCE_ALPHA:
            r = g = b = p[0];
            a = p[1];
            break;
        case GL_RGB:
            r = p[0]; g = p[1]; b = p[2];
            break;
        case GL_RGBA:
            r = p[0]; g = p[1]; b = p[2]; a = p[3];
            break;
        default:
            break;
        }
        tex->pixels[i * 4u] = r;
        tex->pixels[i * 4u + 1u] = g;
        tex->pixels[i * 4u + 2u] = b;
        tex->pixels[i * 4u + 3u] = a;
    }
    (void)emit_texture_update(tex);
}

static void copy_texture_sub_image_rgba(
    TextureState *tex,
    GLint xoffset,
    GLint yoffset,
    GLenum format,
    GLsizei width,
    GLsizei height,
    const GLvoid *pixels)
{
    const unsigned char *src = (const unsigned char *) pixels;
    int bpp = bytes_per_pixel(format);
    GLsizei row;

    if (!tex || !tex->pixels || !src || width < 0 || height < 0 ||
        xoffset < 0 || yoffset < 0 || bpp <= 0 ||
        xoffset + width > tex->width || yoffset + height > tex->height) {
        gl_error = GL_INVALID_VALUE;
        return;
    }

    for (row = 0; row < height; ++row) {
        GLsizei column;
        for (column = 0; column < width; ++column) {
            const unsigned char *source = src + ((row * width + column) * bpp);
            unsigned char *destination =
                tex->pixels + (((yoffset + row) * tex->width + xoffset + column) * 4);
            unsigned char r = 255, g = 255, b = 255, a = 255;

            switch (format) {
            case GL_ALPHA:
                a = source[0];
                break;
            case GL_LUMINANCE:
                r = g = b = source[0];
                break;
            case GL_LUMINANCE_ALPHA:
                r = g = b = source[0];
                a = source[1];
                break;
            case GL_RGB:
                r = source[0]; g = source[1]; b = source[2];
                break;
            case GL_RGBA:
                r = source[0]; g = source[1]; b = source[2]; a = source[3];
                break;
            default:
                gl_error = GL_INVALID_ENUM;
                return;
            }

            destination[0] = r;
            destination[1] = g;
            destination[2] = b;
            destination[3] = a;
        }
    }
    (void) emit_texture_update(tex);
}

static void copy_paletted_texture_rgba(
    TextureState *tex,
    GLenum format,
    GLsizei width,
    GLsizei height,
    GLsizei image_size,
    const GLvoid *data)
{
    const unsigned char *src = (const unsigned char *)data;
    const unsigned char *palette;
    const unsigned char *indices;
    unsigned int count;
    unsigned int palette_bytes;
    unsigned int expected_size;
    unsigned int i;
    unsigned int size;
    int components;

    if (format == GL_PALETTE8_RGB8_OES) {
        components = 3;
    }
    else if (format == GL_PALETTE8_RGBA8_OES) {
        components = 4;
    }
    else {
        gl_error = GL_INVALID_ENUM;
        return;
    }
    if (!tex || !src || width <= 0 || height <= 0) {
        return;
    }
    count = (unsigned int)(width * height);
    palette_bytes = 256u * (unsigned int)components;
    expected_size = palette_bytes + count;
    if (image_size < 0 || (unsigned int)image_size < expected_size) {
        gl_error = GL_INVALID_VALUE;
        return;
    }
    size = count * 4u;
    if (!ensure_texture_pixels(tex, size)) {
        return;
    }
    palette = src;
    indices = src + palette_bytes;
    tex->width = width;
    tex->height = height;
    tex->format = GL_RGBA;
    for (i = 0; i < count; i++) {
        const unsigned char *entry = palette + (unsigned int)indices[i] * components;
        tex->pixels[i * 4u] = entry[0];
        tex->pixels[i * 4u + 1u] = entry[1];
        tex->pixels[i * 4u + 2u] = entry[2];
        tex->pixels[i * 4u + 3u] = components == 4 ? entry[3] : 255;
    }
    (void)emit_texture_update(tex);
}

static void vec3_normalize(GLfloat *v)
{
    GLfloat len = sqrtf(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    if (len > 0.000001f) {
        v[0] /= len;
        v[1] /= len;
        v[2] /= len;
    }
}

static void transform_point(const GLfloat *m, const GLfloat *in, GLfloat *out)
{
    out[0] = m[0] * in[0] + m[4] * in[1] + m[8] * in[2] + m[12];
    out[1] = m[1] * in[0] + m[5] * in[1] + m[9] * in[2] + m[13];
    out[2] = m[2] * in[0] + m[6] * in[1] + m[10] * in[2] + m[14];
}

static void transform_direction(const GLfloat *m, const GLfloat *in, GLfloat *out)
{
    out[0] = m[0] * in[0] + m[4] * in[1] + m[8] * in[2];
    out[1] = m[1] * in[0] + m[5] * in[1] + m[9] * in[2];
    out[2] = m[2] * in[0] + m[6] * in[1] + m[10] * in[2];
}

static int light_index(GLenum light)
{
    if (light >= GL_LIGHT0 && light <= GL_LIGHT7) {
        return (int)(light - GL_LIGHT0);
    }
    return -1;
}

static void read_vertex_position(int index, GLfloat *out)
{
    const unsigned char *vp = array_element_ptr(&vertex_pointer, index);
    out[0] = out[1] = out[2] = 0.0f;
    if (vp) {
        out[0] = read_signed_component(vp, vertex_pointer.type, 0);
        if (vertex_pointer.size > 1) out[1] = read_signed_component(vp, vertex_pointer.type, 1);
        if (vertex_pointer.size > 2) out[2] = read_signed_component(vp, vertex_pointer.type, 2);
    }
}

static void read_vertex_normal(int index, GLfloat *out)
{
    const unsigned char *np = array_element_ptr(&normal_pointer, index);
    out[0] = 0.0f;
    out[1] = 0.0f;
    out[2] = 1.0f;
    if (np) {
        out[0] = read_signed_component(np, normal_pointer.type, 0);
        if (normal_pointer.size > 1) out[1] = read_signed_component(np, normal_pointer.type, 1);
        if (normal_pointer.size > 2) out[2] = read_signed_component(np, normal_pointer.type, 2);
        if (normal_pointer.type == GL_BYTE) {
            out[0] /= 127.0f;
            out[1] /= 127.0f;
            out[2] /= 127.0f;
        }
        else if (normal_pointer.type == GL_SHORT) {
            out[0] /= 32767.0f;
            out[1] /= 32767.0f;
            out[2] /= 32767.0f;
        }
    }
    vec3_normalize(out);
}

static void read_texcoord_for_unit(int unit, int index, GLfloat *out)
{
    ArrayPointerState *texcoord_pointer = &texcoord_pointers[unit];
    const unsigned char *tp = array_element_ptr(texcoord_pointer, index);
    out[0] = 0.0f;
    out[1] = 0.0f;
    out[2] = 0.0f;
    out[3] = 1.0f;
    if (tp) {
        out[0] = read_signed_component(tp, texcoord_pointer->type, 0);
        if (texcoord_pointer->size > 1) out[1] = read_signed_component(tp, texcoord_pointer->type, 1);
        if (texcoord_pointer->size > 2) out[2] = read_signed_component(tp, texcoord_pointer->type, 2);
        if (texcoord_pointer->size > 3) out[3] = read_signed_component(tp, texcoord_pointer->type, 3);
    }
}

static GLushort read_index(const GLvoid *indices, GLenum type, int index)
{
    switch (type) {
    case GL_UNSIGNED_BYTE:
        return ((const GLubyte *)indices)[index];
    case GL_UNSIGNED_SHORT:
        return ((const GLushort *)indices)[index];
    default:
        return (GLushort)((const GLint *)indices)[index];
    }
}

static unsigned char wgpu_topology(GLenum mode)
{
    switch (mode) {
    case GL_TRIANGLE_STRIP:
        return WGPU_TOPOLOGY_TRIANGLE_STRIP;
    case GL_TRIANGLE_FAN:
        return WGPU_TOPOLOGY_TRIANGLE_FAN;
    case GL_TRIANGLES:
    default:
        return WGPU_TOPOLOGY_TRIANGLE_LIST;
    }
}

static GLboolean can_batch_draw_packet(
    GLenum mode,
    unsigned int flags,
    TextureState *active_texture,
    TextureState *active_texture1,
    unsigned int stable_index_handle)
{
    if (!stable_index_handle || !array_pointer_has_stable_source(&vertex_pointer)) {
        return GL_FALSE;
    }
    if ((flags & WGPU_DRAW_HAS_VERTEX_COLOR) == 0 || !color_pointer_has_stable_source()) {
        return GL_FALSE;
    }
    if (active_texture || active_texture1 || (flags & WGPU_DRAW_HAS_TEXTURE) != 0) {
        return GL_FALSE;
    }
    if (lighting_enabled || fog_enabled || alpha_test_enabled || polygon_offset_fill_enabled) {
        return GL_FALSE;
    }
    if (blend_enabled
        || (depth_func_mode != GL_LESS && depth_func_mode != GL_LEQUAL)
        || depth_mask_enabled == GL_FALSE
        || !color_mask_red
        || !color_mask_green
        || !color_mask_blue
        || !color_mask_alpha) {
        return GL_FALSE;
    }
    return mode == GL_TRIANGLES || mode == GL_TRIANGLE_STRIP;
}

static GLboolean can_record_draw_packet(
    GLenum mode,
    unsigned int flags,
    TextureState *active_texture,
    TextureState *active_texture1)
{
    if (!vertex_pointer.pointer) {
        return GL_FALSE;
    }
    if ((flags & WGPU_DRAW_HAS_VERTEX_COLOR) != 0
        && (!color_pointer.enabled || !color_pointer.pointer)) {
        return GL_FALSE;
    }
    if ((flags & WGPU_DRAW_HAS_TEXTURE) != 0
        && (!active_texture
            || active_texture->upload_version == 0
            || !texcoord_pointers[0].enabled
            || !texcoord_pointers[0].pointer)) {
        return GL_FALSE;
    }
    if (lighting_enabled
        && (!normal_pointer.enabled
            || normal_pointer.size != 3
            || !normal_pointer.pointer)) {
        return GL_FALSE;
    }
    if (active_texture1
        && (!(flags & WGPU_DRAW_HAS_TEXTURE)
            || active_texture1->upload_version == 0
            || !texcoord_pointers[1].enabled
            || !texcoord_pointers[1].pointer)) {
        return GL_FALSE;
    }
    if (blend_enabled
        && !((blend_src_factor == GL_SRC_ALPHA
              && blend_dst_factor == GL_ONE_MINUS_SRC_ALPHA)
             || (blend_src_factor == GL_SRC_ALPHA
                 && blend_dst_factor == GL_ONE)
             || (blend_src_factor == GL_ZERO
                 && blend_dst_factor == GL_SRC_COLOR)
             || (blend_src_factor == GL_DST_COLOR
                 && blend_dst_factor == GL_SRC_COLOR)
             || (blend_src_factor == GL_ZERO
                 && blend_dst_factor == GL_ONE_MINUS_SRC_COLOR)
             || (blend_src_factor == GL_ONE
                 && blend_dst_factor == GL_ONE))) {
        return GL_FALSE;
    }
    if (depth_func_mode != GL_LESS
        && depth_func_mode != GL_LEQUAL
        && depth_func_mode != GL_ALWAYS) {
        return GL_FALSE;
    }
    return mode == GL_TRIANGLES
        || mode == GL_TRIANGLE_STRIP
        || mode == GL_TRIANGLE_FAN;
}

static unsigned int draw_packet_frame_bytes(
    unsigned int frame_flags,
    unsigned int index_count,
    unsigned int vertex_count)
{
    unsigned int bytes = 0u;
    if (frame_flags & M3G_DRAW_PACKET_FRAME_INDEX) {
        bytes += index_count * 4u;
    }
    if (frame_flags & M3G_DRAW_PACKET_FRAME_POSITION) {
        bytes += vertex_count * 16u;
    }
    if (frame_flags & M3G_DRAW_PACKET_FRAME_COLOR) {
        bytes += vertex_count * 16u;
    }
    if (frame_flags & M3G_DRAW_PACKET_FRAME_NORMAL) {
        bytes += vertex_count * 16u;
    }
    if (frame_flags & M3G_DRAW_PACKET_FRAME_TEXCOORD0) {
        bytes += vertex_count * 16u;
    }
    if (frame_flags & M3G_DRAW_PACKET_FRAME_TEXCOORD1) {
        bytes += vertex_count * 16u;
    }
    return bytes;
}

static void write_color_for_vertex(int index);
static void write_frame_normal_for_vertex(int index);
static void write_frame_position_for_vertex(int index);
static void write_frame_texcoord_for_vertex(int unit, int index);
static void write_normalized_triangle_indices(
    int first,
    unsigned int source_count,
    GLenum index_type,
    const GLvoid *indices,
    GLboolean indexed,
    unsigned int transform_kind,
    GLboolean local_indices);

static void write_draw_packet_frame_blob(
    unsigned int frame_flags,
    int vertex_start,
    unsigned int vertex_count,
    unsigned int source_index_count,
    GLenum index_type,
    const GLvoid *indices,
    GLboolean indexed,
    unsigned int transform_kind)
{
    if (frame_flags & M3G_DRAW_PACKET_FRAME_INDEX) {
        write_normalized_triangle_indices(
            vertex_start,
            source_index_count,
            index_type,
            indices,
            indexed,
            transform_kind,
            GL_TRUE);
    }
    {
        unsigned int index;
    if (frame_flags & M3G_DRAW_PACKET_FRAME_POSITION) {
        for (index = 0; index < vertex_count; index++) {
            write_frame_position_for_vertex(vertex_start + (int)index);
        }
    }
    if (frame_flags & M3G_DRAW_PACKET_FRAME_COLOR) {
        for (index = 0; index < vertex_count; index++) {
            write_color_for_vertex(vertex_start + (int)index);
        }
    }
    if (frame_flags & M3G_DRAW_PACKET_FRAME_NORMAL) {
        for (index = 0; index < vertex_count; index++) {
            write_frame_normal_for_vertex(vertex_start + (int)index);
        }
    }
    if (frame_flags & M3G_DRAW_PACKET_FRAME_TEXCOORD0) {
        for (index = 0; index < vertex_count; index++) {
            write_frame_texcoord_for_vertex(0, vertex_start + (int)index);
        }
    }
    if (frame_flags & M3G_DRAW_PACKET_FRAME_TEXCOORD1) {
        for (index = 0; index < vertex_count; index++) {
            write_frame_texcoord_for_vertex(1, vertex_start + (int)index);
        }
    }
    }
}

static unsigned int normalized_triangle_index_count(
    unsigned int transform_kind,
    unsigned int source_count)
{
    if (transform_kind == 0u || source_count < 3u) {
        return 0u;
    }
    return (source_count - 2u) * 3u;
}

static int draw_source_index(
    int first,
    GLenum index_type,
    const GLvoid *indices,
    GLboolean indexed,
    unsigned int index)
{
    return indexed
        ? read_index(indices, index_type, (int)index)
        : first + (int)index;
}

static void write_normalized_triangle_indices(
    int first,
    unsigned int source_count,
    GLenum index_type,
    const GLvoid *indices,
    GLboolean indexed,
    unsigned int transform_kind,
    GLboolean local_indices)
{
    unsigned int index;
    if (transform_kind == 0u) {
        for (index = 0; index < source_count; index++) {
            int value = draw_source_index(
                first, index_type, indices, indexed, index);
            write_u32((unsigned int)(value - (local_indices ? first : 0)));
        }
        return;
    }
    if (transform_kind == DERIVED_TRIANGLE_FAN
        || transform_kind == DERIVED_TRIANGLE_FAN_FLAT) {
        int fan_center = draw_source_index(
            first, index_type, indices, indexed, 0u);
        for (index = 1; index + 1 < source_count; index++) {
            int second = draw_source_index(
                first, index_type, indices, indexed, index);
            int third = draw_source_index(
                first, index_type, indices, indexed, index + 1u);
            if (transform_kind == DERIVED_TRIANGLE_FAN_FLAT) {
                write_u32((unsigned int)(third - (local_indices ? first : 0)));
                write_u32((unsigned int)(fan_center - (local_indices ? first : 0)));
                write_u32((unsigned int)(second - (local_indices ? first : 0)));
            }
            else {
                write_u32((unsigned int)(fan_center - (local_indices ? first : 0)));
                write_u32((unsigned int)(second - (local_indices ? first : 0)));
                write_u32((unsigned int)(third - (local_indices ? first : 0)));
            }
        }
        return;
    }
    if (transform_kind == DERIVED_TRIANGLE_STRIP_FLAT) {
        for (index = 0; index + 2 < source_count; index++) {
            int first_value = draw_source_index(
                first, index_type, indices, indexed, index);
            int second_value = draw_source_index(
                first, index_type, indices, indexed, index + 1u);
            int third_value = draw_source_index(
                first, index_type, indices, indexed, index + 2u);
            write_u32((unsigned int)(third_value - (local_indices ? first : 0)));
            if ((index & 1u) == 0u) {
                write_u32((unsigned int)(first_value - (local_indices ? first : 0)));
                write_u32((unsigned int)(second_value - (local_indices ? first : 0)));
            }
            else {
                write_u32((unsigned int)(second_value - (local_indices ? first : 0)));
                write_u32((unsigned int)(first_value - (local_indices ? first : 0)));
            }
        }
    }
}

static void release_derived_triangle_index(
    DerivedTriangleIndexEntry *entry)
{
    if (!entry || !entry->handle) {
        return;
    }
    write_u8(M3G_CMD_DELETE_RESOURCE);
    write_u32(M3G_RESOURCE_INDEX_BUFFER);
    write_u32(entry->handle);
    resource_version_remove(M3G_RESOURCE_INDEX_BUFFER, entry->handle);
    entry->handle = 0u;
}

static unsigned int get_or_create_derived_triangle_index(
    unsigned int source_kind,
    unsigned int source_handle,
    unsigned int source_version,
    int first,
    unsigned int source_count,
    GLenum index_type,
    const GLvoid *indices,
    GLboolean indexed,
    unsigned int transform_kind)
{
    unsigned int index;
    unsigned int derived_index_count =
        normalized_triangle_index_count(transform_kind, source_count);
    unsigned int command_start;
    DerivedTriangleIndexEntry *entry = NULL;

    if (!source_handle || !source_version || derived_index_count == 0u) {
        return 0u;
    }
    for (index = 0; index < resource_registry.derived_triangle_index_count; index++) {
        DerivedTriangleIndexEntry *candidate =
            &resource_registry.derived_triangle_indices[index];
        if (candidate->source_kind == source_kind
            && candidate->source_handle == source_handle
            && candidate->source_version == source_version
            && candidate->transform_kind == transform_kind
            && candidate->first == first
            && candidate->count == source_count) {
            candidate->last_use = ++resource_registry.derived_triangle_use_serial;
            return candidate->handle;
        }
    }
    if (!flush_draw_packet_buckets()) {
        return 0u;
    }
    if (resource_registry.derived_triangle_index_count
        < MAX_DERIVED_TRIANGLE_INDICES) {
        entry = &resource_registry.derived_triangle_indices[
            resource_registry.derived_triangle_index_count++];
    }
    else {
        unsigned int oldest_index = 0u;
        for (index = 1; index < MAX_DERIVED_TRIANGLE_INDICES; index++) {
            if (resource_registry.derived_triangle_indices[index].last_use
                < resource_registry.derived_triangle_indices[oldest_index].last_use) {
                oldest_index = index;
            }
        }
        entry = &resource_registry.derived_triangle_indices[oldest_index];
        release_derived_triangle_index(entry);
    }
    if (resource_registry.derived_triangle_next_handle < 0xf0000000u) {
        resource_registry.derived_triangle_next_handle = 0xf0000000u;
    }
    if (resource_registry.derived_triangle_next_handle == 0xffffffffu) {
        return 0u;
    }
    entry->handle = ++resource_registry.derived_triangle_next_handle;
    resource_version_bump(M3G_RESOURCE_INDEX_BUFFER, entry->handle);
    entry->source_kind = source_kind;
    entry->source_handle = source_handle;
    entry->source_version = source_version;
    entry->transform_kind = transform_kind;
    entry->first = first;
    entry->count = source_count;
    entry->version = resource_version(M3G_RESOURCE_INDEX_BUFFER, entry->handle);
    entry->last_use = ++resource_registry.derived_triangle_use_serial;

    if (!reserve_command_bytes(
            M3G_DERIVED_INDEX_BUFFER_BYTES(derived_index_count))) {
        return 0u;
    }
    command_start = command_size;
    write_u8(M3G_DERIVED_INDEX_BUFFER_COMMAND_ID);
    write_u32(entry->handle);
    write_u32(entry->version);
    write_u32(source_kind);
    write_u32(source_handle);
    write_u32(source_version);
    write_u32(derived_index_count);
    if (command_size
        != command_start + 1u + M3G_DERIVED_INDEX_BUFFER_HEADER_BYTES) {
        command_overflowed = GL_TRUE;
        gl_error = GL_OUT_OF_MEMORY;
        return 0u;
    }
    write_normalized_triangle_indices(
        first,
        source_count,
        index_type,
        indices,
        indexed,
        transform_kind,
        GL_FALSE);
    return entry->handle;
}

static GLboolean emit_draw_packet(
    GLenum mode,
    unsigned int flags,
    unsigned int position_handle,
    unsigned int color_handle,
    unsigned int normal_handle,
    unsigned int texcoord0_handle,
    unsigned int texcoord1_handle,
    unsigned int index_handle,
    unsigned int index_count,
    TextureState *texture0,
    TextureState *texture1,
    const GLfloat *mvp,
    int frame_vertex_start,
    unsigned int frame_vertex_count,
    unsigned int frame_source_flags,
    GLenum frame_index_type,
    const GLvoid *frame_indices,
    GLboolean frame_indexed,
    unsigned int frame_source_index_count,
    unsigned int frame_transform_kind)
{
    unsigned int component;
    unsigned int command_start;
    unsigned int position_version =
        resource_version(M3G_RESOURCE_VERTEX_ARRAY, position_handle);
    unsigned int color_version = color_handle
        ? resource_version(M3G_RESOURCE_VERTEX_ARRAY, color_handle)
        : 0u;
    unsigned int normal_version = normal_handle
        ? resource_version(M3G_RESOURCE_VERTEX_ARRAY, normal_handle)
        : 0u;
    unsigned int texcoord0_version = texcoord0_handle
        ? resource_version(M3G_RESOURCE_VERTEX_ARRAY, texcoord0_handle)
        : 0u;
    unsigned int texcoord1_version = texcoord1_handle
        ? resource_version(M3G_RESOURCE_VERTEX_ARRAY, texcoord1_handle)
        : 0u;
    unsigned int index_version =
        resource_version(M3G_RESOURCE_INDEX_BUFFER, index_handle);
    unsigned int pipeline_key = (flags
        & (WGPU_DRAW_HAS_VERTEX_COLOR
           | WGPU_DRAW_CULL_FACE
           | WGPU_DRAW_CULL_FRONT
           | WGPU_DRAW_FRONT_CW
           | WGPU_DRAW_DEPTH_TEST_ALWAYS
           | WGPU_DRAW_DEPTH_WRITE_OFF
           | WGPU_DRAW_HAS_TEXTURE))
        | (lighting_enabled ? WGPU_DRAW_HAS_LIGHTING : 0u);
    unsigned int blend_mode = 0u;
    unsigned int frame_bytes = draw_packet_frame_bytes(
        frame_source_flags,
        index_count,
        frame_vertex_count);

    if (blend_enabled) {
        if (blend_src_factor == GL_SRC_ALPHA
            && blend_dst_factor == GL_ONE_MINUS_SRC_ALPHA) {
            blend_mode = 1u;
        } else if (blend_src_factor == GL_SRC_ALPHA
                   && blend_dst_factor == GL_ONE) {
            blend_mode = 2u;
        } else if (blend_src_factor == GL_ZERO
                   && blend_dst_factor == GL_SRC_COLOR) {
            blend_mode = 3u;
        } else if (blend_src_factor == GL_DST_COLOR
                   && blend_dst_factor == GL_SRC_COLOR) {
            blend_mode = 4u;
        } else if (blend_src_factor == GL_ZERO
                   && blend_dst_factor == GL_ONE_MINUS_SRC_COLOR) {
            blend_mode = 5u;
        } else if (blend_src_factor == GL_ONE
                   && blend_dst_factor == GL_ONE) {
            blend_mode = 6u;
        } else {
            return GL_FALSE;
        }
    }
    if (fog_enabled) {
        pipeline_key |= WGPU_DRAW_HAS_FOG;
    }
    if (alpha_test_enabled && alpha_test_ref > 0.0f) {
        pipeline_key |= WGPU_DRAW_HAS_ALPHA_TEST;
    }
    if (shade_model_mode == GL_FLAT) {
        pipeline_key |= WGPU_DRAW_FLAT_SHADING;
    }
    if (perspective_correction_hint == GL_NICEST) {
        pipeline_key |= WGPU_DRAW_PERSPECTIVE_CORRECTION;
    }
    if (!color_mask_red) pipeline_key |= WGPU_DRAW_COLOR_MASK_RED_OFF;
    if (!color_mask_green) pipeline_key |= WGPU_DRAW_COLOR_MASK_GREEN_OFF;
    if (!color_mask_blue) pipeline_key |= WGPU_DRAW_COLOR_MASK_BLUE_OFF;
    if (!color_mask_alpha) pipeline_key |= WGPU_DRAW_COLOR_MASK_ALPHA_OFF;
    pipeline_key |= blend_mode << WGPU_DRAW_BLEND_SHIFT;

    if ((!position_handle && !(frame_source_flags & M3G_DRAW_PACKET_FRAME_POSITION))
        || (!index_handle && !(frame_source_flags & M3G_DRAW_PACKET_FRAME_INDEX))
        || (color_handle && !color_version)
        || (lighting_enabled && (!normal_handle || !normal_version)
            && !(frame_source_flags & M3G_DRAW_PACKET_FRAME_NORMAL))
        || (texcoord0_handle && !texcoord0_version)
        || (texcoord1_handle && !texcoord1_version)
        || ((flags & WGPU_DRAW_HAS_TEXTURE) != 0
            && (!texture0 || texture0->upload_version == 0))
        || (texture1 && texture1->upload_version == 0)) {
        return GL_FALSE;
    }
    if (!reserve_command_bytes(M3G_DRAW_PACKET_BYTES(frame_bytes, 1u))) {
        return GL_FALSE;
    }
    command_start = command_size;
    write_u8(M3G_DRAW_PACKET_COMMAND_ID);
    write_u32((unsigned int)wgpu_topology(mode));
    write_u32(pipeline_key);
    write_u32(position_handle);
    write_u32(position_version);
    write_u32(color_handle);
    write_u32(color_version);
    write_u32(normal_handle);
    write_u32(normal_version);
    write_u32(texcoord0_handle);
    write_u32(texcoord0_version);
    write_u32(index_handle);
    write_u32(index_version);
    write_u32(texture0 ? texture0->name : 0u);
    write_u32(texture0 ? texture0->upload_version : 0u);
    write_u32(texture0 ? texture0->source_image_handle : 0u);
    write_u32(texcoord1_handle);
    write_u32(texcoord1_version);
    write_u32(texture1 ? texture1->name : 0u);
    write_u32(texture1 ? texture1->upload_version : 0u);
    write_u32(texture1 ? texture1->source_image_handle : 0u);
    write_i32(viewport_x);
    write_i32(viewport_y);
    write_i32(viewport_width);
    write_i32(viewport_height);
    write_i32(scissor_x);
    write_i32(scissor_y);
    write_i32(scissor_width);
    write_i32(scissor_height);
    write_u32(index_count);
    write_u32(1u);
    write_u32((unsigned int)frame_vertex_start);
    write_u32(frame_vertex_count);
    write_u32(frame_source_flags);
    write_u32(frame_bytes);
    write_u32(texture0 ? (unsigned int)texture0->wrap_s : 0u);
    write_u32(texture0 ? (unsigned int)texture0->wrap_t : 0u);
    write_u32(texture0 ? (unsigned int)texture0->min_filter : 0u);
    write_u32(texture0 ? (unsigned int)texture0->mag_filter : 0u);
    write_u32(texture1 ? (unsigned int)texture1->wrap_s : 0u);
    write_u32(texture1 ? (unsigned int)texture1->wrap_t : 0u);
    write_u32(texture1 ? (unsigned int)texture1->min_filter : 0u);
    write_u32(texture1 ? (unsigned int)texture1->mag_filter : 0u);
    write_f32(polygon_offset_fill_enabled ? polygon_offset_factor : 0.0f);
    write_f32(polygon_offset_fill_enabled ? polygon_offset_units : 0.0f);
    for (component = 0; component < 4; component++) {
        write_f32(current_color[component] / 65536.0f);
    }
    if (command_size
        != command_start + 1u + M3G_DRAW_PACKET_UNIFORM_OFFSET) {
        command_size = command_start;
        return GL_FALSE;
    }
    if (!write_draw_packet_textured_uniform(
            command_scratch,
            &command_size,
            mvp,
            texture0,
            texture1)) {
        command_size = command_start;
        return GL_FALSE;
    }
    if (frame_source_flags != 0u) {
        write_draw_packet_frame_blob(
            frame_source_flags,
            frame_vertex_start,
            frame_vertex_count,
            frame_source_index_count,
            frame_index_type,
            frame_indices,
            frame_indexed,
            frame_transform_kind);
    }
    for (component = 0; component < 16; component++) {
        write_f32(mvp[component]);
    }
    if (command_overflowed) {
        command_size = command_start;
        return GL_FALSE;
    }
    return GL_TRUE;
}

static GLboolean append_draw_packet_instance(
    GLenum mode,
    unsigned int flags,
    unsigned int position_handle,
    unsigned int color_handle,
    unsigned int index_handle,
    unsigned int index_count,
    const GLfloat *mvp)
{
    unsigned int bucket_index;
    unsigned int topology = (unsigned int)wgpu_topology(mode);
    unsigned int pipeline_key = flags
        & (WGPU_DRAW_HAS_VERTEX_COLOR
           | WGPU_DRAW_CULL_FACE
           | WGPU_DRAW_CULL_FRONT
           | WGPU_DRAW_FRONT_CW
           | WGPU_DRAW_DEPTH_TEST_ALWAYS
           | WGPU_DRAW_DEPTH_WRITE_OFF);
    if (shade_model_mode == GL_FLAT) {
        pipeline_key |= WGPU_DRAW_FLAT_SHADING;
    }
    if (perspective_correction_hint == GL_NICEST) {
        pipeline_key |= WGPU_DRAW_PERSPECTIVE_CORRECTION;
    }
    if (!color_mask_red) pipeline_key |= WGPU_DRAW_COLOR_MASK_RED_OFF;
    if (!color_mask_green) pipeline_key |= WGPU_DRAW_COLOR_MASK_GREEN_OFF;
    if (!color_mask_blue) pipeline_key |= WGPU_DRAW_COLOR_MASK_BLUE_OFF;
    if (!color_mask_alpha) pipeline_key |= WGPU_DRAW_COLOR_MASK_ALPHA_OFF;
    unsigned int position_version =
        resource_version(M3G_RESOURCE_VERTEX_ARRAY, position_handle);
    unsigned int color_version =
        resource_version(M3G_RESOURCE_VERTEX_ARRAY, color_handle);
    unsigned int index_version =
        resource_version(M3G_RESOURCE_INDEX_BUFFER, index_handle);
    DrawPacketBucket *bucket = NULL;
    if (!position_version || !color_version || !index_version) {
        return GL_FALSE;
    }
    for (bucket_index = 0; bucket_index < draw_packet_bucket_count; bucket_index++) {
        DrawPacketBucket *candidate = &draw_packet_buckets[bucket_index];
        if (candidate->topology == topology
            && candidate->pipeline_key == pipeline_key
            && candidate->position_handle == position_handle
            && candidate->position_version == position_version
            && candidate->color_handle == color_handle
            && candidate->color_version == color_version
            && candidate->index_handle == index_handle
            && candidate->index_version == index_version
            && candidate->index_count == index_count
            && candidate->viewport[0] == viewport_x
            && candidate->viewport[1] == viewport_y
            && candidate->viewport[2] == viewport_width
            && candidate->viewport[3] == viewport_height
            && candidate->scissor[0] == scissor_x
            && candidate->scissor[1] == scissor_y
            && candidate->scissor[2] == scissor_width
            && candidate->scissor[3] == scissor_height) {
            bucket = candidate;
            break;
        }
    }
    if (!bucket) {
        if (draw_packet_bucket_count == MAX_WGPU_INSTANCE_BUCKETS) {
            if (!flush_draw_packet_buckets()) {
                return GL_FALSE;
            }
        }
        bucket = &draw_packet_buckets[draw_packet_bucket_count++];
        bucket->topology = topology;
        bucket->pipeline_key = pipeline_key;
        bucket->position_handle = position_handle;
        bucket->position_version = position_version;
        bucket->color_handle = color_handle;
        bucket->color_version = color_version;
        bucket->index_handle = index_handle;
        bucket->index_version = index_version;
        bucket->index_count = index_count;
        bucket->viewport[0] = viewport_x;
        bucket->viewport[1] = viewport_y;
        bucket->viewport[2] = viewport_width;
        bucket->viewport[3] = viewport_height;
        bucket->scissor[0] = scissor_x;
        bucket->scissor[1] = scissor_y;
        bucket->scissor[2] = scissor_width;
        bucket->scissor[3] = scissor_height;
        bucket->instance_count = 0;
    }
    if (bucket->instance_count == MAX_WGPU_INSTANCES_PER_PACKET) {
        if (!flush_draw_packet_buckets()) {
            return GL_FALSE;
        }
        return append_draw_packet_instance(
            mode,
            flags,
            position_handle,
            color_handle,
            index_handle,
            index_count,
            mvp);
    }
    matrix_copy(bucket->mvp[bucket->instance_count], mvp);
    bucket->instance_count++;
    return GL_TRUE;
}

static void write_color_for_vertex(int index)
{
    if (color_pointer.enabled && color_pointer.pointer) {
        const unsigned char *cp = array_element_ptr(&color_pointer, index);
        GLfloat r = read_component(cp, color_pointer.type, 0);
        GLfloat g = color_pointer.size > 1 ? read_component(cp, color_pointer.type, 1) : r;
        GLfloat b = color_pointer.size > 2 ? read_component(cp, color_pointer.type, 2) : r;
        GLfloat a = color_pointer.size > 3 ? read_component(cp, color_pointer.type, 3) : 255.0f;
        if (color_pointer.type == GL_UNSIGNED_BYTE || color_pointer.type == GL_BYTE) {
            r /= 255.0f;
            g /= 255.0f;
            b /= 255.0f;
            a /= 255.0f;
        }
        write_f32(r);
        write_f32(g);
        write_f32(b);
        write_f32(a);
    }
    else {
        write_f32(current_color[0] / 65536.0f);
        write_f32(current_color[1] / 65536.0f);
        write_f32(current_color[2] / 65536.0f);
        write_f32(current_color[3] / 65536.0f);
    }
}

static void write_frame_position_for_vertex(int index)
{
    GLfloat position[3];
    read_vertex_position(index, position);
    write_f32(position[0]);
    write_f32(position[1]);
    write_f32(position[2]);
    write_f32(1.0f);
}

static void write_frame_normal_for_vertex(int index)
{
    GLfloat normal[3];
    read_vertex_normal(index, normal);
    write_f32(normal[0]);
    write_f32(normal[1]);
    write_f32(normal[2]);
    write_f32(0.0f);
}

static void write_frame_texcoord_for_vertex(int unit, int index)
{
    GLfloat texcoord[4];
    read_texcoord_for_unit(unit, index, texcoord);
    write_f32(texcoord[0]);
    write_f32(texcoord[1]);
    write_f32(texcoord[2]);
    write_f32(texcoord[3]);
}

static int triangle_count(GLenum mode, GLsizei count)
{
    if (count < 3) {
        return 0;
    }
    switch (mode) {
    case GL_TRIANGLES:
        return count / 3;
    case GL_TRIANGLE_STRIP:
    case GL_TRIANGLE_FAN:
        return count - 2;
    default:
        return 0;
    }
}

static void emit_draw(GLenum mode, GLint first, GLsizei count, GLenum index_type, const GLvoid *indices, GLboolean indexed)
{
    int tris;
    int idx;
    int vertex_start = first;
    int unique_count = count;
    unsigned int packet_index_count = (unsigned int)count;
    unsigned int flags;
    GLfloat mvp_gl[16];
    GLfloat mvp_wgpu[16];
    TextureState *active_texture = NULL;
    TextureState *active_texture1 = NULL;
    unsigned int stable_index_handle = 0;
    unsigned int frame_source_flags = 0u;
    unsigned int position_handle;
    unsigned int color_handle;
    unsigned int normal_handle;
    unsigned int texcoord0_handle;
    unsigned int texcoord1_handle;
    GLboolean use_stable_index_source = GL_FALSE;
    GLboolean triangle_fan = mode == GL_TRIANGLE_FAN;
    GLboolean flat_triangle_strip =
        mode == GL_TRIANGLE_STRIP && shade_model_mode == GL_FLAT;
    unsigned int index_transform_kind = 0u;
    GLenum packet_mode = mode;
    if (!command_capture_enabled || !vertex_pointer.enabled) {
        return;
    }
    tris = triangle_count(mode, count);
    if (tris <= 0) {
        return;
    }
    if (triangle_fan) {
        index_transform_kind = shade_model_mode == GL_FLAT
            ? DERIVED_TRIANGLE_FAN_FLAT
            : DERIVED_TRIANGLE_FAN;
        packet_mode = GL_TRIANGLES;
        packet_index_count = normalized_triangle_index_count(
            index_transform_kind,
            (unsigned int)count);
    }
    else if (flat_triangle_strip) {
        index_transform_kind = DERIVED_TRIANGLE_STRIP_FLAT;
        packet_mode = GL_TRIANGLES;
        packet_index_count = normalized_triangle_index_count(
            index_transform_kind,
            (unsigned int)count);
    }

    if (indexed) {
        int min_index = 0x7fffffff;
        int max_index = 0;
        for (idx = 0; idx < count; idx++) {
            int value = read_index(indices, index_type, idx);
            if (value < min_index) min_index = value;
            if (value > max_index) max_index = value;
        }
        vertex_start = min_index;
        unique_count = max_index - min_index + 1;
    }
    flags = (color_pointer.enabled && color_pointer.pointer)
        ? WGPU_DRAW_HAS_VERTEX_COLOR
        : 0u;
    if (texture_2d_enabled[0] && texcoord_pointers[0].enabled && texcoord_pointers[0].pointer) {
        active_texture = texture_state(bound_texture_2d[0], GL_FALSE);
        if (active_texture && active_texture->pixels && active_texture->width > 0 && active_texture->height > 0) {
            flags |= WGPU_DRAW_HAS_TEXTURE;
        }
    }
    if (texture_2d_enabled[1] && texcoord_pointers[1].enabled && texcoord_pointers[1].pointer) {
        active_texture1 = texture_state(bound_texture_2d[1], GL_FALSE);
        if (!(active_texture1 && active_texture1->pixels && active_texture1->width > 0 && active_texture1->height > 0)) {
            active_texture1 = NULL;
        }
    }
    if (cull_face_enabled) {
        flags |= WGPU_DRAW_CULL_FACE;
        if (cull_face_mode == GL_FRONT) {
            flags |= WGPU_DRAW_CULL_FRONT;
        }
    }
    if (front_face_mode == GL_CW) {
        flags |= WGPU_DRAW_FRONT_CW;
    }
    if (depth_func_mode == GL_ALWAYS) {
        flags |= WGPU_DRAW_DEPTH_TEST_ALWAYS;
    }
    if (depth_mask_enabled == GL_FALSE) {
        flags |= WGPU_DRAW_DEPTH_WRITE_OFF;
    }
    if (indexed) {
        stable_index_handle = index_buffer_handle_for_pointer(indices);
        use_stable_index_source = stable_index_handle != 0
            && can_use_stable_index_source(flags, active_texture1);
    }
    position_handle = array_pointer_has_stable_source(&vertex_pointer)
        ? vertex_array_handle_for_pointer(vertex_pointer.pointer)
        : 0u;
    color_handle = color_pointer_has_stable_source()
        ? vertex_array_handle_for_pointer(color_pointer.pointer)
        : 0u;
    normal_handle = lighting_enabled && array_pointer_has_stable_source(&normal_pointer)
        ? vertex_array_handle_for_pointer(normal_pointer.pointer)
        : 0u;
    texcoord0_handle = (flags & WGPU_DRAW_HAS_TEXTURE) != 0
        && array_pointer_has_stable_source(&texcoord_pointers[0])
        ? vertex_array_handle_for_pointer(texcoord_pointers[0].pointer)
        : 0u;
    texcoord1_handle = active_texture1
        && array_pointer_has_stable_source(&texcoord_pointers[1])
        ? vertex_array_handle_for_pointer(texcoord_pointers[1].pointer)
        : 0u;
    if (index_transform_kind != 0u
        && can_use_stable_index_source(flags, active_texture1)) {
        unsigned int source_kind = indexed
            ? M3G_RESOURCE_INDEX_BUFFER
            : M3G_RESOURCE_VERTEX_ARRAY;
        unsigned int source_handle = indexed
            ? stable_index_handle
            : position_handle;
        unsigned int source_version = resource_version(source_kind, source_handle);
        unsigned int derived_handle = get_or_create_derived_triangle_index(
            source_kind,
            source_handle,
            source_version,
            first,
            (unsigned int)count,
            index_type,
            indices,
            indexed,
            index_transform_kind);
        if (derived_handle != 0u) {
            stable_index_handle = derived_handle;
            use_stable_index_source = GL_TRUE;
        }
        else {
            stable_index_handle = 0u;
            use_stable_index_source = GL_FALSE;
        }
    }
    if (!position_handle) {
        frame_source_flags |= M3G_DRAW_PACKET_FRAME_POSITION;
    }
    if (!use_stable_index_source) {
        frame_source_flags |= M3G_DRAW_PACKET_FRAME_INDEX;
    }
    if ((flags & WGPU_DRAW_HAS_VERTEX_COLOR) != 0 && !color_handle) {
        frame_source_flags |= M3G_DRAW_PACKET_FRAME_COLOR;
    }
    if (lighting_enabled && !normal_handle) {
        frame_source_flags |= M3G_DRAW_PACKET_FRAME_NORMAL;
    }
    if ((flags & WGPU_DRAW_HAS_TEXTURE) != 0 && !texcoord0_handle) {
        frame_source_flags |= M3G_DRAW_PACKET_FRAME_TEXCOORD0;
    }
    if (active_texture1 && !texcoord1_handle) {
        frame_source_flags |= M3G_DRAW_PACKET_FRAME_TEXCOORD1;
    }
    if (frame_source_flags != 0u) {
        frame_source_flags = M3G_DRAW_PACKET_FRAME_INDEX
            | M3G_DRAW_PACKET_FRAME_POSITION;
        if ((flags & WGPU_DRAW_HAS_VERTEX_COLOR) != 0) {
            frame_source_flags |= M3G_DRAW_PACKET_FRAME_COLOR;
        }
        if (lighting_enabled) {
            frame_source_flags |= M3G_DRAW_PACKET_FRAME_NORMAL;
        }
        if ((flags & WGPU_DRAW_HAS_TEXTURE) != 0) {
            frame_source_flags |= M3G_DRAW_PACKET_FRAME_TEXCOORD0;
        }
        if (active_texture1) {
            frame_source_flags |= M3G_DRAW_PACKET_FRAME_TEXCOORD1;
        }
    }
    matrix_multiply(mvp_gl, projection_matrix, modelview_matrix);
    matrix_gl_to_wgpu_clip(mvp_wgpu, mvp_gl);
    matrix_apply_depth_range(mvp_wgpu, depth_near, depth_far);

    if (can_batch_draw_packet(
            packet_mode,
            flags,
            active_texture,
            active_texture1,
            stable_index_handle)) {
        if (!capture_draw_batch_started) {
            write_u8(M3G_CMD_BEGIN_WGPU_BATCH);
            capture_draw_batch_started = GL_TRUE;
        }
        if (!append_draw_packet_instance(
                packet_mode,
                flags,
                position_handle,
                color_handle,
                stable_index_handle,
                packet_index_count,
                mvp_wgpu)) {
            gl_error = GL_OUT_OF_MEMORY;
        }
        return;
    }

    if (!flush_draw_packet_buckets()) {
        gl_error = GL_OUT_OF_MEMORY;
        return;
    }
    if (can_record_draw_packet(
            packet_mode,
            flags,
            active_texture,
            active_texture1)) {
        if (!capture_draw_batch_started) {
            write_u8(M3G_CMD_BEGIN_WGPU_BATCH);
            capture_draw_batch_started = GL_TRUE;
        }
        if (!emit_draw_packet(
                packet_mode,
                flags,
                position_handle,
                color_handle,
                normal_handle,
                texcoord0_handle,
                texcoord1_handle,
                stable_index_handle,
                packet_index_count,
                active_texture,
                active_texture1,
                mvp_wgpu,
                vertex_start,
                (unsigned int)unique_count,
                frame_source_flags,
                index_type,
                indices,
                indexed,
                (unsigned int)count,
                index_transform_kind)) {
            gl_error = GL_OUT_OF_MEMORY;
        }
        return;
    }
    /*
     * M3G only uses the Packet-supported triangle and fixed-function state
     * combinations above. Do not encode a second variable-length draw format
     * when a packet cannot be formed: surface the unsupported state instead.
     */
    gl_error = GL_INVALID_OPERATION;
}

int m3gWasmBeginCommandCapture(void *owner, void *sink)
{
    if (!m3gWasmBindGlesContext(owner)) {
        return 0;
    }
    command_capture_enabled = GL_TRUE;
    command_sink = sink;
    command_size = 0;
    command_overflowed = GL_FALSE;
    capture_draw_batch_started = GL_FALSE;
    clear_draw_packet_buckets();
    return 1;
}

void m3gWasmGetScissor(int *x, int *y, int *width, int *height)
{
    if (x) *x = scissor_x;
    if (y) *y = scissor_y;
    if (width) *width = scissor_width;
    if (height) *height = scissor_height;
}

void m3gWasmSetTextureImageHandle(unsigned int texture, const void *image)
{
    if (!current_gles1_context) {
        return;
    }
    TextureState *tex = texture_state(texture, GL_TRUE);
    if (tex) {
        tex->source_image_handle = m3gWasmResolveStableHandle(image);
    }
}

void m3gWasmRegisterExternalVertexArray(unsigned int handle)
{
    unsigned int i;
    if (!handle) {
        return;
    }
    for (i = 0; i < external_vertex_array_count; i++) {
        if (external_vertex_arrays[i] == handle) {
            return;
        }
    }
    if (external_vertex_array_count < MAX_VERTEX_ARRAY_POINTERS) {
        external_vertex_arrays[external_vertex_array_count++] = handle;
    }
}

void m3gWasmRegisterVertexArrayPointer(unsigned int handle, const void *pointer)
{
    unsigned int i;
    if (!handle || !pointer || !is_external_vertex_array(handle)) {
        return;
    }
    for (i = 0; i < vertex_array_pointer_count; i++) {
        if (vertex_array_pointer_map[i].pointer == pointer) {
            vertex_array_pointer_map[i].handle = handle;
            return;
        }
    }
    if (vertex_array_pointer_count < MAX_VERTEX_ARRAY_POINTERS) {
        vertex_array_pointer_map[vertex_array_pointer_count].pointer = pointer;
        vertex_array_pointer_map[vertex_array_pointer_count].handle = handle;
        vertex_array_pointer_count++;
    }
}

void m3gWasmRegisterIndexBufferPointer(unsigned int handle, const void *pointer)
{
    unsigned int i;
    if (!handle || !pointer) {
        return;
    }
    for (i = 0; i < index_buffer_pointer_count; i++) {
        if (index_buffer_pointer_map[i].pointer == pointer) {
            index_buffer_pointer_map[i].handle = handle;
            return;
        }
    }
    if (index_buffer_pointer_count < MAX_VERTEX_ARRAY_POINTERS) {
        index_buffer_pointer_map[index_buffer_pointer_count].pointer = pointer;
        index_buffer_pointer_map[index_buffer_pointer_count].handle = handle;
        index_buffer_pointer_count++;
    }
}

void m3gWasmEnsureIndexBufferEmitted(
    unsigned int handle,
    unsigned int glType,
    int indexCount,
    int stripCount,
    const unsigned short *lengths,
    const void *indices)
{
    unsigned int version;
    int i;

    (void)stripCount;
    (void)lengths;

    if (!command_capture_enabled) {
        return;
    }
    version = resource_version(M3G_RESOURCE_INDEX_BUFFER, handle);
    if (version != 0u) {
        return;
    }
    resource_version_bump(M3G_RESOURCE_INDEX_BUFFER, handle);
    version = resource_version(M3G_RESOURCE_INDEX_BUFFER, handle);

    (void)flush_draw_packet_buckets();

    write_u8(M3G_CMD_CREATE_INDEX_BUFFER);
    write_u32(handle);
    write_u32(version);
    write_u32((unsigned int)indexCount);
    for (i = 0; i < indexCount; i++) {
        unsigned int value = glType == 0x1401
            ? ((const unsigned char *)indices)[i]
            : ((const unsigned short *)indices)[i];
        write_u32(value);
    }
}

unsigned int m3gWasmBumpResourceVersion(unsigned int kind, unsigned int handle)
{
    resource_version_bump(kind, handle);
    return resource_version(kind, handle);
}

void m3gWasmReleaseResourceVersion(unsigned int kind, unsigned int handle)
{
    invalidate_derived_triangle_indices(kind, handle);
    resource_version_remove(kind, handle);
    if (kind == M3G_RESOURCE_VERTEX_ARRAY) {
        unregister_external_vertex_array(handle);
    }
}

void m3gWasmEndCommandCapture(void)
{
    (void)flush_draw_packet_buckets();
    if (capture_draw_batch_started) {
        write_u8(M3G_CMD_END_WGPU_BATCH);
    }
    (void)flush_command_scratch();
    command_capture_enabled = GL_FALSE;
    command_sink = NULL;
    capture_draw_batch_started = GL_FALSE;
}

unsigned int m3gWasmCommandCaptureSize(void)
{
    return 0;
}

unsigned int m3gWasmDrainCommandCapture(unsigned char *dst, unsigned int capacity)
{
    (void)dst;
    (void)capacity;
    return 0;
}

void glActiveTexture(GLenum texture)
{
    active_texture_unit = texture_unit_index(texture);
}
void glAlphaFunc(GLenum func, GLclampf ref)
{
    alpha_test_func = func;
    alpha_test_ref = ref;
}
void glBindTexture(GLenum target, GLuint texture)
{
    if (target == GL_TEXTURE_2D) {
        bound_texture_2d[active_texture_unit] = texture;
        (void)texture_state(texture, GL_TRUE);
    }
}
void glBlendFunc(GLenum sfactor, GLenum dfactor)
{
    blend_src_factor = sfactor;
    blend_dst_factor = dfactor;
}
void glClear(GLbitfield mask) { (void)mask; }
void glClearColorx(GLfixed red, GLfixed green, GLfixed blue, GLfixed alpha) { (void)red; (void)green; (void)blue; (void)alpha; }
void glClearDepthx(GLfixed depth) { (void)depth; }
void glClientActiveTexture(GLenum texture)
{
    client_active_texture_unit = texture_unit_index(texture);
}
void glColorMask(GLboolean red, GLboolean green, GLboolean blue, GLboolean alpha)
{
    if ((red != color_mask_red
            || green != color_mask_green
            || blue != color_mask_blue
            || alpha != color_mask_alpha)
        && !flush_draw_packet_buckets()) {
        gl_error = GL_OUT_OF_MEMORY;
        return;
    }
    color_mask_red = red;
    color_mask_green = green;
    color_mask_blue = blue;
    color_mask_alpha = alpha;
}
void glColor4x(GLfixed red, GLfixed green, GLfixed blue, GLfixed alpha)
{
    current_color[0] = red;
    current_color[1] = green;
    current_color[2] = blue;
    current_color[3] = alpha;
}
void glColorPointer(GLint size, GLenum type, GLsizei stride, const GLvoid *pointer)
{
    color_pointer.size = size;
    color_pointer.type = type;
    color_pointer.stride = stride;
    color_pointer.pointer = pointer;
}
void glCullFace(GLenum mode) { cull_face_mode = mode; }
void glDeleteTextures(GLsizei n, const GLuint *textures)
{
    GLsizei i;
    if (!textures) {
        return;
    }
    for (i = 0; i < n; i++) {
        TextureState *tex = texture_state(textures[i], GL_FALSE);
        if (tex) {
            if (tex->pixels) {
                m3gWasmFreeBytes(tex->pixels);
            }
            tex->name = 0;
            tex->pixels = NULL;
            tex->pixels_size = 0;
            tex->source_image_handle = 0;
            tex->upload_version = 0;
        }
        if (command_capture_enabled) {
            write_u8(M3G_CMD_DELETE_RESOURCE);
            write_u32(M3G_RESOURCE_GL_TEXTURE);
            write_u32((unsigned int)textures[i]);
        }
    }
}
void glDepthFunc(GLenum func) { depth_func_mode = func; }
void glDepthMask(GLboolean flag) { depth_mask_enabled = flag; }
void glDepthRangef(GLclampf zNear, GLclampf zFar)
{
    depth_near = zNear;
    depth_far = zFar;
}
void glDisable(GLenum cap)
{
    int idx = light_index(cap);
    if (idx >= 0) {
        lights[idx].enabled = GL_FALSE;
        return;
    }
    switch (cap) {
    case GL_LIGHTING:
        lighting_enabled = GL_FALSE;
        break;
    case GL_TEXTURE_2D:
        texture_2d_enabled[active_texture_unit] = GL_FALSE;
        break;
    case GL_NORMALIZE:
        normalize_enabled = GL_FALSE;
        break;
    case GL_COLOR_MATERIAL:
        color_material_enabled = GL_FALSE;
        break;
    case GL_BLEND:
        blend_enabled = GL_FALSE;
        break;
    case GL_SCISSOR_TEST:
        scissor_enabled = GL_FALSE;
        break;
    case GL_CULL_FACE:
        cull_face_enabled = GL_FALSE;
        break;
    case GL_ALPHA_TEST:
        alpha_test_enabled = GL_FALSE;
        break;
    case GL_FOG:
        fog_enabled = GL_FALSE;
        break;
    case GL_POLYGON_OFFSET_FILL:
        polygon_offset_fill_enabled = GL_FALSE;
        break;
    default:
        break;
    }
}
void glDisableClientState(GLenum array)
{
    if (array == GL_VERTEX_ARRAY) vertex_pointer.enabled = GL_FALSE;
    if (array == GL_NORMAL_ARRAY) normal_pointer.enabled = GL_FALSE;
    if (array == GL_COLOR_ARRAY) color_pointer.enabled = GL_FALSE;
    if (array == GL_TEXTURE_COORD_ARRAY) texcoord_pointers[client_active_texture_unit].enabled = GL_FALSE;
}
void glDrawArrays(GLenum mode, GLint first, GLsizei count)
{
    emit_draw(mode, first, count, GL_UNSIGNED_SHORT, NULL, GL_FALSE);
}
void glDrawElements(GLenum mode, GLsizei count, GLenum type, const GLvoid *indices)
{
    emit_draw(mode, 0, count, type, indices, GL_TRUE);
}
void glEnable(GLenum cap)
{
    int idx = light_index(cap);
    if (idx >= 0) {
        lights[idx].enabled = GL_TRUE;
        return;
    }
    switch (cap) {
    case GL_LIGHTING:
        lighting_enabled = GL_TRUE;
        break;
    case GL_TEXTURE_2D:
        texture_2d_enabled[active_texture_unit] = GL_TRUE;
        break;
    case GL_NORMALIZE:
        normalize_enabled = GL_TRUE;
        break;
    case GL_COLOR_MATERIAL:
        color_material_enabled = GL_TRUE;
        break;
    case GL_BLEND:
        blend_enabled = GL_TRUE;
        break;
    case GL_SCISSOR_TEST:
        scissor_enabled = GL_TRUE;
        break;
    case GL_CULL_FACE:
        cull_face_enabled = GL_TRUE;
        break;
    case GL_ALPHA_TEST:
        alpha_test_enabled = GL_TRUE;
        break;
    case GL_FOG:
        fog_enabled = GL_TRUE;
        break;
    case GL_POLYGON_OFFSET_FILL:
        polygon_offset_fill_enabled = GL_TRUE;
        break;
    default:
        break;
    }
}
void glEnableClientState(GLenum array)
{
    if (array == GL_VERTEX_ARRAY) vertex_pointer.enabled = GL_TRUE;
    if (array == GL_NORMAL_ARRAY) normal_pointer.enabled = GL_TRUE;
    if (array == GL_COLOR_ARRAY) color_pointer.enabled = GL_TRUE;
    if (array == GL_TEXTURE_COORD_ARRAY) texcoord_pointers[client_active_texture_unit].enabled = GL_TRUE;
}
void glFinish(void) {}
void glFogf(GLenum pname, GLfloat param)
{
    switch (pname) {
    case GL_FOG_MODE:
        fog_mode = (GLenum)param;
        break;
    case GL_FOG_DENSITY:
        fog_density = param;
        break;
    case GL_FOG_START:
        fog_start = param;
        break;
    case GL_FOG_END:
        fog_end = param;
        break;
    default:
        break;
    }
}
void glFogxv(GLenum pname, const GLfixed *params)
{
    int i;
    if (pname == GL_FOG_COLOR && params) {
        for (i = 0; i < 4; i++) {
            fog_color[i] = params[i] / 65536.0f;
        }
    }
}
void glFrontFace(GLenum mode) { front_face_mode = mode; }
void glGenTextures(GLsizei n, GLuint *textures) { for (GLsizei i = 0; i < n; i++) textures[i] = gl_next_texture++; }
GLenum glGetError(void) { GLenum e = gl_error; gl_error = GL_NO_ERROR; return e; }
void glGetIntegerv(GLenum pname, GLint *params)
{
    if (!params) return;
    switch (pname) {
    case GL_MAX_TEXTURE_SIZE:
        params[0] = 4096;
        break;
    case GL_MAX_VIEWPORT_DIMS:
        params[0] = 4096;
        params[1] = 4096;
        break;
    default:
        params[0] = 0;
        gl_error = GL_INVALID_ENUM;
        break;
    }
}
const GLubyte* glGetString(GLenum name) { (void)name; return (const GLubyte*)"M3G WASM Stub"; }
void glHint(GLenum target, GLenum mode)
{
    if (target == GL_PERSPECTIVE_CORRECTION_HINT
        && (mode == GL_FASTEST || mode == GL_NICEST)) {
        perspective_correction_hint = mode;
    }
}
void glLightModelf(GLenum pname, GLfloat param)
{
    if (pname == GL_LIGHT_MODEL_TWO_SIDE) {
        two_sided_lighting = param != 0.0f ? GL_TRUE : GL_FALSE;
        return;
    }
    gl_error = GL_INVALID_ENUM;
}
void glLightModelfv(GLenum pname, const GLfloat *params)
{
    int i;
    if (pname == GL_LIGHT_MODEL_AMBIENT && params) {
        for (i = 0; i < 4; i++) light_model_ambient[i] = params[i];
    }
}
void glLightf(GLenum light, GLenum pname, GLfloat param)
{
    int idx = light_index(light);
    if (idx < 0) return;
    switch (pname) {
    case GL_SPOT_EXPONENT:
        lights[idx].spot_exponent = param;
        break;
    case GL_SPOT_CUTOFF:
        lights[idx].spot_cutoff = param;
        break;
    case GL_CONSTANT_ATTENUATION:
        lights[idx].constant_attenuation = param;
        break;
    case GL_LINEAR_ATTENUATION:
        lights[idx].linear_attenuation = param;
        break;
    case GL_QUADRATIC_ATTENUATION:
        lights[idx].quadratic_attenuation = param;
        break;
    default:
        break;
    }
}
void glLightfv(GLenum light, GLenum pname, const GLfloat *params)
{
    int idx = light_index(light);
    int i;
    if (idx < 0 || !params) return;
    switch (pname) {
    case GL_AMBIENT:
        for (i = 0; i < 4; i++) lights[idx].ambient[i] = params[i];
        break;
    case GL_DIFFUSE:
        for (i = 0; i < 4; i++) lights[idx].diffuse[i] = params[i];
        break;
    case GL_SPECULAR:
        for (i = 0; i < 4; i++) lights[idx].specular[i] = params[i];
        break;
    case GL_POSITION:
        if (params[3] == 0.0f) {
            transform_direction(modelview_matrix, params, lights[idx].position);
            lights[idx].position[3] = 0.0f;
            vec3_normalize(lights[idx].position);
        }
        else {
            transform_point(modelview_matrix, params, lights[idx].position);
            lights[idx].position[3] = 1.0f;
        }
        break;
    case GL_SPOT_DIRECTION:
        transform_direction(modelview_matrix, params, lights[idx].spot_direction);
        vec3_normalize(lights[idx].spot_direction);
        break;
    default:
        break;
    }
}
void glLoadIdentity(void) { matrix_identity(current_matrix()); }
void glLoadMatrixf(const GLfloat *m)
{
    matrix_copy(current_matrix(), m);
}
void glMaterialf(GLenum face, GLenum pname, GLfloat param)
{
    (void)face;
    if (pname == GL_SHININESS) {
        material_shininess = param;
    }
}
void glMaterialfv(GLenum face, GLenum pname, const GLfloat *params)
{
    GLfloat *dst = NULL;
    int i;
    (void)face;
    if (!params) return;
    switch (pname) {
    case GL_AMBIENT:
        dst = material_ambient;
        break;
    case GL_DIFFUSE:
        dst = material_diffuse;
        break;
    case GL_EMISSION:
        dst = material_emission;
        break;
    case GL_SPECULAR:
        dst = material_specular;
        break;
    default:
        break;
    }
    if (dst) {
        for (i = 0; i < 4; i++) dst[i] = params[i];
    }
}
void glMatrixMode(GLenum mode)
{
    if (mode == GL_MODELVIEW || mode == GL_PROJECTION || mode == GL_TEXTURE) {
        current_matrix_mode = mode;
    }
    else {
        gl_error = GL_INVALID_ENUM;
    }
}
void glMultMatrixf(const GLfloat *m) { matrix_multiply(current_matrix(), current_matrix(), m); }
void glNormalPointer(GLenum type, GLsizei stride, const GLvoid *pointer)
{
    normal_pointer.size = 3;
    normal_pointer.type = type;
    normal_pointer.stride = stride;
    normal_pointer.pointer = pointer;
}
void glOrthox(GLfixed left, GLfixed right, GLfixed bottom, GLfixed top, GLfixed zNear, GLfixed zFar)
{
    GLfloat ortho[16];
    matrix_ortho(ortho, left / 65536.0f, right / 65536.0f, bottom / 65536.0f, top / 65536.0f, zNear / 65536.0f, zFar / 65536.0f);
    matrix_multiply(current_matrix(), current_matrix(), ortho);
}
void glPixelStorei(GLenum pname, GLint param) { (void)pname; (void)param; }
void glPolygonOffset(GLfloat factor, GLfloat units)
{
    polygon_offset_factor = factor;
    polygon_offset_units = units;
}
void glPopMatrix(void)
{
    int *size = current_matrix_stack_size();
    if (*size <= 0) {
        gl_error = GL_STACK_UNDERFLOW;
        return;
    }
    (*size)--;
    matrix_copy(current_matrix(), current_matrix_stack()[*size]);
}
void glPushMatrix(void)
{
    int *size = current_matrix_stack_size();
    if (*size >= MATRIX_STACK_DEPTH) {
        gl_error = GL_STACK_OVERFLOW;
        return;
    }
    matrix_copy(current_matrix_stack()[*size], current_matrix());
    (*size)++;
}
void glReadPixels(GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, GLvoid *pixels) { (void)x; (void)y; (void)width; (void)height; (void)format; (void)type; (void)pixels; }
void glScalef(GLfloat x, GLfloat y, GLfloat z) { matrix_scale(current_matrix(), x, y, z); }
void glScissor(GLint x, GLint y, GLsizei width, GLsizei height)
{
    scissor_x = x;
    scissor_y = y;
    scissor_width = width;
    scissor_height = height;
}
void glShadeModel(GLenum mode) { shade_model_mode = mode; }
void glTexCoordPointer(GLint size, GLenum type, GLsizei stride, const GLvoid *pointer)
{
    ArrayPointerState *texcoord_pointer = &texcoord_pointers[client_active_texture_unit];
    texcoord_pointer->size = size;
    texcoord_pointer->type = type;
    texcoord_pointer->stride = stride;
    texcoord_pointer->pointer = pointer;
}
void glTexEnvfv(GLenum target, GLenum pname, const GLfloat *params)
{
    int i;
    if (target != GL_TEXTURE_ENV || !params) return;
    if (pname == GL_TEXTURE_ENV_COLOR) {
        for (i = 0; i < 4; i++) texture_env_color[active_texture_unit][i] = params[i];
    }
}
void glTexEnvx(GLenum target, GLenum pname, GLfixed param)
{
    if (target != GL_TEXTURE_ENV) return;
    if (pname == GL_TEXTURE_ENV_MODE) {
        texture_env_mode[active_texture_unit] = (GLenum)param;
    }
}
void glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const GLvoid *pixels)
{
    TextureState *tex;
    (void)internalformat;
    (void)border;
    if (target != GL_TEXTURE_2D || level != 0 || type != GL_UNSIGNED_BYTE) return;
    tex = texture_state(bound_texture_2d[active_texture_unit], GL_TRUE);
    copy_texture_rgba(tex, format, width, height, pixels);
}
void glTexParameteri(GLenum target, GLenum pname, GLint param)
{
    TextureState *tex = texture_state(bound_texture_2d[active_texture_unit], GL_TRUE);
    if (target != GL_TEXTURE_2D || !tex) return;
    switch (pname) {
    case GL_TEXTURE_MIN_FILTER:
        tex->min_filter = (GLenum)param;
        break;
    case GL_TEXTURE_MAG_FILTER:
        tex->mag_filter = (GLenum)param;
        break;
    case GL_TEXTURE_WRAP_S:
        tex->wrap_s = (GLenum)param;
        break;
    case GL_TEXTURE_WRAP_T:
        tex->wrap_t = (GLenum)param;
        break;
    default:
        break;
    }
}
void glTexParameterx(GLenum target, GLenum pname, GLfixed param)
{
    glTexParameteri(target, pname, (GLint)param);
}
void glTexSubImage2D(
    GLenum target,
    GLint level,
    GLint xoffset,
    GLint yoffset,
    GLsizei width,
    GLsizei height,
    GLenum format,
    GLenum type,
    const GLvoid *pixels)
{
    TextureState *tex;
    if (target != GL_TEXTURE_2D || level != 0 || type != GL_UNSIGNED_BYTE) {
        gl_error = GL_INVALID_ENUM;
        return;
    }
    tex = texture_state(bound_texture_2d[active_texture_unit], GL_FALSE);
    copy_texture_sub_image_rgba(tex, xoffset, yoffset, format, width, height, pixels);
}
void glTranslatef(GLfloat x, GLfloat y, GLfloat z) { matrix_translate(current_matrix(), x, y, z); }
void glVertexPointer(GLint size, GLenum type, GLsizei stride, const GLvoid *pointer)
{
    vertex_pointer.size = size;
    vertex_pointer.type = type;
    vertex_pointer.stride = stride;
    vertex_pointer.pointer = pointer;
}
void glViewport(GLint x, GLint y, GLsizei width, GLsizei height)
{
    viewport_x = x;
    viewport_y = y;
    viewport_width = width;
    viewport_height = height;
}
void glCompressedTexImage2D(
    GLenum target,
    GLint level,
    GLenum internalformat,
    GLsizei width,
    GLsizei height,
    GLint border,
    GLsizei imageSize,
    const GLvoid *data)
{
    TextureState *tex;
    (void)border;
    if (target != GL_TEXTURE_2D || level != 0) {
        return;
    }
    tex = texture_state(bound_texture_2d[active_texture_unit], GL_TRUE);
    copy_paletted_texture_rgba(
        tex, internalformat, width, height, imageSize, data);
}
/* EGL stubs */
static EGLint egl_error = EGL_SUCCESS;

EGLDisplay eglGetDisplay(EGLNativeDisplayType display_id) { (void)display_id; return (EGLDisplay)1; }
EGLBoolean eglInitialize(EGLDisplay dpy, EGLint *major, EGLint *minor) { (void)dpy; if (major) *major = 1; if (minor) *minor = 0; return EGL_TRUE; }
EGLBoolean eglTerminate(EGLDisplay dpy) { (void)dpy; return EGL_TRUE; }
EGLBoolean eglChooseConfig(EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs, EGLint config_size, EGLint *num_config) { (void)dpy; (void)attrib_list; if (configs && config_size > 0) configs[0] = (EGLConfig)1; if (num_config) *num_config = 1; return EGL_TRUE; }
EGLBoolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint *value) { (void)dpy; (void)config; (void)attribute; if (value) *value = 0; return EGL_TRUE; }
EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint *attrib_list) { (void)dpy; (void)config; (void)share_context; (void)attrib_list; return (EGLContext)1; }
EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx) { (void)dpy; (void)ctx; return EGL_TRUE; }
EGLSurface eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint *attrib_list) { (void)dpy; (void)config; (void)win; (void)attrib_list; return (EGLSurface)1; }
EGLSurface eglCreatePixmapSurface(EGLDisplay dpy, EGLConfig config, EGLNativePixmapType pixmap, const EGLint *attrib_list) { (void)dpy; (void)config; (void)pixmap; (void)attrib_list; return (EGLSurface)1; }
EGLSurface eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig config, const EGLint *attrib_list) { (void)dpy; (void)config; (void)attrib_list; return (EGLSurface)1; }
EGLBoolean eglDestroySurface(EGLDisplay dpy, EGLSurface surface) { (void)dpy; (void)surface; return EGL_TRUE; }
EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) { (void)dpy; (void)draw; (void)read; (void)ctx; return EGL_TRUE; }
EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) { (void)dpy; (void)surface; return EGL_TRUE; }
EGLBoolean eglCopyBuffers(EGLDisplay dpy, EGLSurface surface, EGLNativePixmapType target) { (void)dpy; (void)surface; (void)target; return EGL_TRUE; }
EGLint eglGetError(void) { EGLint e = egl_error; egl_error = EGL_SUCCESS; return e; }
EGLBoolean eglBindAPI(EGLenum api) { (void)api; return EGL_TRUE; }
EGLBoolean eglWaitNative(EGLint engine) { (void)engine; return EGL_TRUE; }
EGLBoolean eglQuerySurface(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint *value) { (void)dpy; (void)surface; (void)attribute; if (value) *value = 256; return EGL_TRUE; }
EGLBoolean eglQueryContext(EGLDisplay dpy, EGLContext ctx, EGLint attribute, EGLint *value) { (void)dpy; (void)ctx; (void)attribute; if (value) *value = 0; return EGL_TRUE; }
const char* eglQueryString(EGLDisplay dpy, EGLint name) { (void)dpy; (void)name; return "1.0 WASM Stub"; }
EGLDisplay eglGetCurrentDisplay(void) { return (EGLDisplay)1; }
