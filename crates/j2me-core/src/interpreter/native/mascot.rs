//! Native boundary for Mascot Capsule.
//!
//! The primitive command-list path is Rust-authoritative: Java forwards API
//! arguments, Rust parses command streams and projects vertices, and the host
//! only submits already-resolved draw packets.

use crate::heap::NULL;
use crate::interpreter::native::registry::{NativeContext, NativeRegistry};
use crate::vm::{cmd, MascotFigureDraw, MascotTextureImage, SharedVm};
use std::sync::atomic::{AtomicI32, Ordering};

static NEXT_MOCK_GL_ID: AtomicI32 = AtomicI32::new(1);

const CLASS_GRAPHICS_3D: &str = "com.mascotcapsule.micro3d.v3.Graphics3D";
const CLASS_FIGURE_LAYOUT: &str = "com.mascotcapsule.micro3d.v3.FigureLayout";
const CLASS_AFFINE_TRANS: &str = "com.mascotcapsule.micro3d.v3.AffineTrans";
const CLASS_FIGURE: &str = "com.mascotcapsule.micro3d.v3.Figure";
const FIELD_GRAPHICS_ACTIVE_TEXTURES: &str = "activeTextures";
const FIELD_FIGURE_CURRENT_PATTERN: &str = "currentPattern";
const CLASS_MODEL: &str = "com.mascotcapsule.micro3d.v3.Model";
const CLASS_MODEL_POLYGON: &str = "com.mascotcapsule.micro3d.v3.Model$Polygon";
const CLASS_FLOAT_BUFFER: &str = "java.nio.FloatBuffer";
const CLASS_BYTE_BUFFER: &str = "java.nio.ByteBuffer";
const CLASS_TEXTURE: &str = "com.mascotcapsule.micro3d.v3.Texture";
const CLASS_EFFECT_3D: &str = "com.mascotcapsule.micro3d.v3.Effect3D";
const CLASS_LIGHT: &str = "com.mascotcapsule.micro3d.v3.Light";
const CLASS_VECTOR_3D: &str = "com.mascotcapsule.micro3d.v3.Vector3D";

const COMMAND_LIST_VERSION_1_0: i32 = 0xfe00_0001_u32 as i32;
const COMMAND_END: u32 = 0x8000_0000;
const COMMAND_FLUSH: u32 = 0x8200_0000;
const COMMAND_ATTRIBUTE: u32 = 0x8300_0000;
const COMMAND_CLIP: u32 = 0x8400_0000;
const COMMAND_CENTER: u32 = 0x8500_0000;
const COMMAND_TEXTURE_INDEX: u32 = 0x8600_0000;
const COMMAND_AFFINE_INDEX: u32 = 0x8700_0000;
const COMMAND_PARALLEL_SCALE: u32 = 0x9000_0000;
const COMMAND_PARALLEL_SIZE: u32 = 0x9100_0000;
const COMMAND_PERSPECTIVE_FOV: u32 = 0x9200_0000;
const COMMAND_PERSPECTIVE_WH: u32 = 0x9300_0000;
const COMMAND_AMBIENT_LIGHT: u32 = 0xa000_0000;
const COMMAND_DIRECTION_LIGHT: u32 = 0xa100_0000;
const COMMAND_THRESHOLD: u32 = 0xaf00_0000;

const PRIMITIVE_POINTS: u32 = 0x0100_0000;
const PRIMITIVE_LINES: u32 = 0x0200_0000;
const PRIMITIVE_TRIANGLES: u32 = 0x0300_0000;
const PRIMITIVE_QUADS: u32 = 0x0400_0000;
const PRIMITIVE_POINT_SPRITES: u32 = 0x0500_0000;
const PDATA_NORMAL_PER_FACE: u32 = 0x0000_0200;
const PDATA_NORMAL_PER_VERTEX: u32 = 0x0000_0300;
const PDATA_COLOR_PER_COMMAND: u32 = 0x0000_0400;
const PDATA_COLOR_PER_FACE: u32 = 0x0000_0800;
const PDATA_POINT_SPRITE_PARAMS_PER_COMMAND: u32 = 0x0000_1000;
const PDATA_POINT_SPRITE_PARAMS_PER_FACE: u32 = 0x0000_2000;
const PDATA_POINT_SPRITE_PARAMS_PER_VERTEX: u32 = 0x0000_3000;
const PDATA_TEXTURE_COORD: u32 = 0x0000_3000;
const PATTR_LIGHTING: u32 = 0x0000_0001;
const PATTR_BLEND_MASK: u32 = 0x0000_00e0;
const PATTR_SPHERE_MAP: u32 = 0x0000_0002;
const PATTR_COLORKEY: u32 = 0x0000_0010;
const ENV_ATTR_LIGHTING: u32 = 0x01;
const ENV_ATTR_TOON_SHADING: u32 = 0x04;
const ENV_ATTR_SEMI_TRANSPARENT: u32 = 0x08;

#[derive(Clone, Copy)]
struct Affine {
    m00: i32,
    m01: i32,
    m02: i32,
    m03: i32,
    m10: i32,
    m11: i32,
    m12: i32,
    m13: i32,
    m20: i32,
    m21: i32,
    m22: i32,
    m23: i32,
}

#[derive(Clone, Copy)]
struct BoneMatrix {
    m00: f32,
    m01: f32,
    m02: f32,
    m03: f32,
    m10: f32,
    m11: f32,
    m12: f32,
    m13: f32,
    m20: f32,
    m21: f32,
    m22: f32,
    m23: f32,
}

#[derive(Clone, Copy)]
struct Lighting {
    direction: [f32; 3],
    ambient: f32,
    directional: f32,
    toon_threshold: f32,
    toon_high: f32,
    toon_low: f32,
}

#[derive(Clone, Copy, Default)]
struct TextureBinding {
    handle: i32,
    color_key: [f32; 3],
}

fn primitive_blend_mode(command: u32) -> u32 {
    (command & PATTR_BLEND_MASK) >> 5
}

fn blend_mode(enabled: bool, command: u32) -> u32 {
    if enabled {
        primitive_blend_mode(command)
    } else {
        0
    }
}

impl BoneMatrix {
    fn multiply(left: Self, right: Self) -> Self {
        Self {
            m00: left.m00 * right.m00 + left.m01 * right.m10 + left.m02 * right.m20,
            m01: left.m00 * right.m01 + left.m01 * right.m11 + left.m02 * right.m21,
            m02: left.m00 * right.m02 + left.m01 * right.m12 + left.m02 * right.m22,
            m03: left.m00 * right.m03 + left.m01 * right.m13 + left.m02 * right.m23 + left.m03,
            m10: left.m10 * right.m00 + left.m11 * right.m10 + left.m12 * right.m20,
            m11: left.m10 * right.m01 + left.m11 * right.m11 + left.m12 * right.m21,
            m12: left.m10 * right.m02 + left.m11 * right.m12 + left.m12 * right.m22,
            m13: left.m10 * right.m03 + left.m11 * right.m13 + left.m12 * right.m23 + left.m13,
            m20: left.m20 * right.m00 + left.m21 * right.m10 + left.m22 * right.m20,
            m21: left.m20 * right.m01 + left.m21 * right.m11 + left.m22 * right.m21,
            m22: left.m20 * right.m02 + left.m21 * right.m12 + left.m22 * right.m22,
            m23: left.m20 * right.m03 + left.m21 * right.m13 + left.m22 * right.m23 + left.m23,
        }
    }

    fn transform_point(self, x: f32, y: f32, z: f32) -> [f32; 3] {
        [
            x * self.m00 + y * self.m01 + z * self.m02 + self.m03,
            x * self.m10 + y * self.m11 + z * self.m12 + self.m13,
            x * self.m20 + y * self.m21 + z * self.m22 + self.m23,
        ]
    }
}

impl Default for Affine {
    fn default() -> Self {
        Self {
            m00: 4096,
            m01: 0,
            m02: 0,
            m03: 0,
            m10: 0,
            m11: 4096,
            m12: 0,
            m13: 0,
            m20: 0,
            m21: 0,
            m22: 4096,
            m23: 0,
        }
    }
}

#[derive(Clone, Copy)]
struct Layout {
    affine: Affine,
    scale_x: i32,
    scale_y: i32,
    center_x: i32,
    center_y: i32,
    parallel_width: i32,
    parallel_height: i32,
    near: i32,
    far: i32,
    angle: i32,
    perspective_width: i32,
    perspective_height: i32,
    setting: u32,
}

impl Default for Layout {
    fn default() -> Self {
        Self {
            affine: Affine::default(),
            scale_x: 512,
            scale_y: 512,
            center_x: 0,
            center_y: 0,
            parallel_width: 0,
            parallel_height: 0,
            near: 1,
            far: 32767,
            angle: 512,
            perspective_width: 0,
            perspective_height: 0,
            setting: COMMAND_PARALLEL_SCALE,
        }
    }
}

#[derive(Clone, Copy)]
struct MascotTarget {
    surface_id: i32,
    width: i32,
    height: i32,
    clip_x: i32,
    clip_y: i32,
    clip_width: i32,
    clip_height: i32,
}

type ClipVertex = [f32; 4];

fn field_i32(vm: &SharedVm, object: u32, class_name: &str, field_name: &str) -> i32 {
    if object == NULL {
        return 0;
    }
    vm.class_repo
        .find_field_offset(class_name, field_name)
        .map(|offset| vm.heap.read_u32(object + offset) as i32)
        .unwrap_or(0)
}

fn field_ref(vm: &SharedVm, object: u32, class_name: &str, field_name: &str) -> u32 {
    field_i32(vm, object, class_name, field_name) as u32
}

fn read_affine(vm: &SharedVm, affine_ref: u32) -> Affine {
    if affine_ref == NULL {
        return Affine::default();
    }
    Affine {
        m00: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m00"),
        m01: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m01"),
        m02: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m02"),
        m03: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m03"),
        m10: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m10"),
        m11: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m11"),
        m12: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m12"),
        m13: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m13"),
        m20: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m20"),
        m21: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m21"),
        m22: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m22"),
        m23: field_i32(vm, affine_ref, CLASS_AFFINE_TRANS, "m23"),
    }
}

fn read_layout(vm: &SharedVm, layout_ref: u32) -> Layout {
    let mut layout = Layout::default();
    if layout_ref == NULL {
        return layout;
    }
    layout.affine = read_affine(vm, field_ref(vm, layout_ref, CLASS_FIGURE_LAYOUT, "affine"));
    layout.scale_x = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "scaleX");
    layout.scale_y = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "scaleY");
    layout.center_x = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "centerX");
    layout.center_y = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "centerY");
    layout.parallel_width = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "parallelWidth");
    layout.parallel_height = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "parallelHeight");
    layout.near = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "near");
    layout.far = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "far");
    layout.angle = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "angle");
    layout.perspective_width = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "perspectiveWidth");
    layout.perspective_height = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "perspectiveHeight");
    layout.setting = field_i32(vm, layout_ref, CLASS_FIGURE_LAYOUT, "settingIndex") as u32;
    layout
}

fn read_lighting(vm: &SharedVm, effect: u32) -> Option<Lighting> {
    if effect == NULL || field_i32(vm, effect, CLASS_EFFECT_3D, "isLighting") == 0 {
        return None;
    }
    let light = field_ref(vm, effect, CLASS_EFFECT_3D, "light");
    let direction = field_ref(vm, light, CLASS_LIGHT, "mDirVector");
    if light == NULL || direction == NULL {
        return None;
    }
    let x = field_i32(vm, direction, CLASS_VECTOR_3D, "x") as f32;
    let y = field_i32(vm, direction, CLASS_VECTOR_3D, "y") as f32;
    let z = field_i32(vm, direction, CLASS_VECTOR_3D, "z") as f32;
    if x == 0.0 && y == 0.0 && z == 0.0 {
        return None;
    }
    Some(Lighting {
        direction: [x, y, z],
        ambient: field_i32(vm, light, CLASS_LIGHT, "mAmbIntensity").clamp(0, 4096) as f32,
        directional: field_i32(vm, light, CLASS_LIGHT, "mDirIntensity").clamp(0, 16384) as f32,
        toon_threshold: if field_i32(vm, effect, CLASS_EFFECT_3D, "mShading") == 1
            && field_i32(vm, effect, CLASS_EFFECT_3D, "isToonShading") != 0
        {
            field_i32(vm, effect, CLASS_EFFECT_3D, "mToonThreshold").clamp(0, 255) as f32
        } else {
            -1.0
        },
        toon_high: field_i32(vm, effect, CLASS_EFFECT_3D, "mToonHigh").clamp(0, 255) as f32,
        toon_low: field_i32(vm, effect, CLASS_EFFECT_3D, "mToonLow").clamp(0, 255) as f32,
    })
}

fn read_sphere_texture(vm: &SharedVm, effect: u32) -> i32 {
    if effect == NULL {
        return 0;
    }
    let texture = field_ref(vm, effect, CLASS_EFFECT_3D, "mTexture");
    let handle = field_i32(vm, texture, CLASS_TEXTURE, "image");
    (handle > 0 && vm.mascot_textures.contains_key(&handle))
        .then_some(handle)
        .unwrap_or(0)
}

fn renderer_target(vm: &SharedVm, renderer: u32) -> Option<MascotTarget> {
    let graphics = field_ref(vm, renderer, CLASS_GRAPHICS_3D, "graphics");
    let state = vm.graphics_states.get(&graphics).copied()?;
    let (width, height) = vm
        .surface_dims
        .get(&state.surface_id)
        .copied()
        .unwrap_or((vm.display_width, vm.display_height));
    Some(MascotTarget {
        surface_id: state.surface_id,
        width,
        height,
        clip_x: state.clip_x,
        clip_y: state.clip_y,
        clip_width: state.clip_w,
        clip_height: state.clip_h,
    })
}

fn write_renderer_graphics(vm: &mut SharedVm, renderer: u32, graphics: u32) {
    if let Some(offset) = vm
        .class_repo
        .find_field_offset(CLASS_GRAPHICS_3D, "graphics")
    {
        vm.heap.write_u32(renderer + offset, graphics);
    }
}

fn emit_bind(vm: &mut SharedVm, target: MascotTarget) {
    let buffer = vm.m3g_command_buffer_mut();
    buffer.push(cmd::MASCOT_BIND_TARGET);
    for value in [
        target.surface_id,
        target.width,
        target.height,
        target.clip_x,
        target.clip_y,
        target.clip_width,
        target.clip_height,
    ] {
        buffer.extend_from_slice(&value.to_le_bytes());
    }
}

fn transform_vertex(affine: Affine, x: i32, y: i32, z: i32) -> (f32, f32, f32) {
    let transformed_x = ((x as i64 * affine.m00 as i64
        + y as i64 * affine.m01 as i64
        + z as i64 * affine.m02 as i64
        + 2048)
        >> 12) as i32
        + affine.m03;
    let transformed_y = ((x as i64 * affine.m10 as i64
        + y as i64 * affine.m11 as i64
        + z as i64 * affine.m12 as i64
        + 2048)
        >> 12) as i32
        + affine.m13;
    let transformed_z = ((x as i64 * affine.m20 as i64
        + y as i64 * affine.m21 as i64
        + z as i64 * affine.m22 as i64
        + 2048)
        >> 12) as i32
        + affine.m23;
    (
        transformed_x as f32,
        transformed_y as f32,
        transformed_z as f32,
    )
}

fn project_vertex(
    layout: Layout,
    target: MascotTarget,
    x: i32,
    y: i32,
    z: i32,
) -> Option<ClipVertex> {
    let (x, y, z) = transform_vertex(layout.affine, x, y, z);
    let width = target.width.max(1) as f32;
    let height = target.height.max(1) as f32;
    let tx = 2.0 * layout.center_x as f32 / width - 1.0;
    let ty = 2.0 * layout.center_y as f32 / height - 1.0;
    match layout.setting {
        COMMAND_PARALLEL_SCALE if layout.scale_x != 0 && layout.scale_y != 0 => Some([
            x * 2.0 * layout.scale_x as f32 / (width * 4096.0) + tx,
            -(y * 2.0 * layout.scale_y as f32 / (height * 4096.0) + ty),
            (z / 65_536.0 + 1.0) * 0.5,
            1.0,
        ]),
        COMMAND_PARALLEL_SIZE => {
            let projection_width = if layout.parallel_width == 0 {
                1600.0
            } else {
                layout.parallel_width as f32
            };
            let projection_height = if layout.parallel_height == 0 {
                projection_width * height / width
            } else {
                layout.parallel_height as f32
            };
            Some([
                x * 2.0 / projection_width + tx,
                -(y * 2.0 / projection_height + ty),
                (z / 65_536.0 + 1.0) * 0.5,
                1.0,
            ])
        }
        COMMAND_PERSPECTIVE_FOV if z != 0.0 && layout.near > 0 && layout.far > layout.near => {
            let near = layout.near as f32;
            let far = layout.far as f32;
            let inverse_depth = 1.0 / (near - far);
            let sx = 1.0 / ((layout.angle as f32 / 4096.0) * std::f32::consts::PI).tan();
            let sy = sx * width / height;
            let sz = -(far + near) * inverse_depth;
            let tz = 2.0 * far * near * inverse_depth;
            let gl_clip_z = sz * z + tz;
            Some([
                sx * x + tx * z,
                -(sy * y + ty * z),
                (gl_clip_z + z) * 0.5,
                z,
            ])
        }
        COMMAND_PERSPECTIVE_WH if z != 0.0 && layout.near > 0 && layout.far > layout.near => {
            let near = layout.near as f32;
            let far = layout.far as f32;
            let projection_width = if layout.perspective_width == 0 {
                width
            } else {
                layout.perspective_width as f32 / 4096.0
            };
            let projection_height = if layout.perspective_height == 0 {
                height
            } else {
                layout.perspective_height as f32 / 4096.0
            };
            if projection_width == 0.0 || projection_height == 0.0 {
                return None;
            }
            let inverse_depth = 1.0 / (near - far);
            let sz = -(near + far) * inverse_depth;
            let tz = 2.0 * far * near * inverse_depth;
            let gl_clip_z = sz * z + tz;
            Some([
                2.0 * near * x / projection_width + tx * z,
                -(2.0 * near * y / projection_height + ty * z),
                (gl_clip_z + z) * 0.5,
                z,
            ])
        }
        _ => None,
    }
}

fn clip_vertex_to_ndc(vertex: ClipVertex) -> Option<[f32; 3]> {
    (vertex[3] != 0.0).then(|| {
        [
            vertex[0] / vertex[3],
            vertex[1] / vertex[3],
            vertex[2] / vertex[3],
        ]
    })
}

fn clip_distance(vertex: ClipVertex, plane: u8) -> f32 {
    match plane {
        0 => vertex[3] - 0.0001,
        1 => vertex[0] + vertex[3],
        2 => vertex[3] - vertex[0],
        3 => vertex[1] + vertex[3],
        4 => vertex[3] - vertex[1],
        5 => vertex[2],
        6 => vertex[3] - vertex[2],
        _ => 0.0,
    }
}

fn interpolate_vertex<const N: usize>(from: [f32; N], to: [f32; N], factor: f32) -> [f32; N] {
    let mut interpolated = from;
    for index in 0..N {
        interpolated[index] = from[index] + (to[index] - from[index]) * factor;
    }
    interpolated
}

fn append_clipped_triangle_to_webgpu<const N: usize>(
    triangle: &[[f32; N]],
    output: &mut Vec<[f32; N]>,
) {
    if triangle.len() != 3 {
        return;
    }
    if (0..=6).all(|plane| {
        triangle
            .iter()
            .all(|vertex| clip_distance([vertex[0], vertex[1], vertex[2], vertex[3]], plane) >= 0.0)
    }) {
        output.extend_from_slice(triangle);
        return;
    }
    let mut polygon = triangle.to_vec();
    for plane in 0..=6 {
        if polygon.is_empty() {
            return;
        }
        let mut clipped = Vec::with_capacity(polygon.len() + 1);
        let mut previous = *polygon.last().unwrap_or(&polygon[0]);
        let mut previous_distance =
            clip_distance([previous[0], previous[1], previous[2], previous[3]], plane);
        let mut previous_inside = previous_distance >= 0.0;
        for current in polygon.iter().copied() {
            let current_distance =
                clip_distance([current[0], current[1], current[2], current[3]], plane);
            let current_inside = current_distance >= 0.0;
            if previous_inside != current_inside {
                let denominator = previous_distance - current_distance;
                if denominator != 0.0 {
                    let factor = (previous_distance / denominator).clamp(0.0, 1.0);
                    clipped.push(interpolate_vertex(previous, current, factor));
                }
            }
            if current_inside {
                clipped.push(current);
            }
            previous = current;
            previous_distance = current_distance;
            previous_inside = current_inside;
        }
        polygon = clipped;
    }

    if polygon.len() < 3 {
        return;
    }
    for index in 1..polygon.len() - 1 {
        output.extend_from_slice(&[polygon[0], polygon[index], polygon[index + 1]]);
    }
}

fn vertex_count(primitive: u32, primitive_count: usize) -> Option<usize> {
    match primitive {
        PRIMITIVE_POINTS | PRIMITIVE_POINT_SPRITES => Some(primitive_count),
        PRIMITIVE_LINES => Some(primitive_count * 2),
        PRIMITIVE_TRIANGLES => Some(primitive_count * 3),
        PRIMITIVE_QUADS => Some(primitive_count * 4),
        _ => None,
    }
}

fn emit_primitive_packet(
    vm: &mut SharedVm,
    target: MascotTarget,
    primitive: u32,
    vertices: &[ClipVertex],
    color: i32,
    blend_mode: u32,
) {
    if vertices.is_empty() {
        return;
    }
    let topology = match primitive {
        PRIMITIVE_POINTS => 1_u32,
        PRIMITIVE_LINES => 3_u32,
        PRIMITIVE_TRIANGLES | PRIMITIVE_QUADS => 3_u32,
        _ => return,
    };
    let buffer = vm.m3g_command_buffer_mut();
    buffer.push(cmd::MASCOT_DRAW_PRIMITIVES);
    buffer.extend_from_slice(&(topology | (blend_mode << 8)).to_le_bytes());
    let expanded_lines = (primitive == PRIMITIVE_LINES)
        .then(|| expand_lines_to_triangles(vertices, target.width, target.height));
    let packet_vertices = expanded_lines.as_deref().unwrap_or(vertices);
    buffer.extend_from_slice(&(packet_vertices.len() as u32).to_le_bytes());
    for value in [
        target.clip_x,
        target.clip_y,
        target.clip_width,
        target.clip_height,
    ] {
        buffer.extend_from_slice(&value.to_le_bytes());
    }
    for shift in [16, 8, 0] {
        let component = ((color as u32 >> shift) & 0xff) as f32 / 255.0;
        buffer.extend_from_slice(&component.to_le_bytes());
    }
    buffer.extend_from_slice(&1.0_f32.to_le_bytes());
    for vertex in packet_vertices {
        for value in vertex {
            buffer.extend_from_slice(&value.to_le_bytes());
        }
    }
}

fn expand_lines_to_triangles(
    vertices: &[ClipVertex],
    target_width: i32,
    target_height: i32,
) -> Vec<ClipVertex> {
    let width = target_width.max(1) as f32;
    let height = target_height.max(1) as f32;
    let mut expanded = Vec::with_capacity(vertices.len() / 2 * 6);
    for line in vertices.chunks_exact(2) {
        let (p0, p1) = (line[0], line[1]);
        if p0[3] == 0.0 || p1[3] == 0.0 {
            continue;
        }
        let dx = (p1[0] / p1[3] - p0[0] / p0[3]) * width * 0.5;
        let dy = (p1[1] / p1[3] - p0[1] / p0[3]) * height * 0.5;
        let length = (dx * dx + dy * dy).sqrt();
        if length == 0.0 {
            continue;
        }
        let nx = -dy * 0.5 / length;
        let ny = dx * 0.5 / length;
        let offset0 = [nx * 2.0 / width * p0[3], ny * 2.0 / height * p0[3]];
        let offset1 = [nx * 2.0 / width * p1[3], ny * 2.0 / height * p1[3]];
        let a = [p0[0] + offset0[0], p0[1] + offset0[1], p0[2], p0[3]];
        let b = [p0[0] - offset0[0], p0[1] - offset0[1], p0[2], p0[3]];
        let c = [p1[0] + offset1[0], p1[1] + offset1[1], p1[2], p1[3]];
        let d = [p1[0] - offset1[0], p1[1] - offset1[1], p1[2], p1[3]];
        expanded.extend_from_slice(&[a, b, c, c, b, d]);
    }
    expanded
}

fn write_lighting_uniform(
    buffer: &mut Vec<u8>,
    affine: Affine,
    lighting: Option<Lighting>,
    color_key: [f32; 3],
    color_key_enabled: bool,
) {
    let lighting = lighting.unwrap_or(Lighting {
        direction: [0.0; 3],
        ambient: -1.0,
        directional: 0.0,
        toon_threshold: -1.0,
        toon_high: 0.0,
        toon_low: 0.0,
    });
    for row in [
        [affine.m00 as f32, affine.m01 as f32, affine.m02 as f32, 0.0],
        [affine.m10 as f32, affine.m11 as f32, affine.m12 as f32, 0.0],
        [affine.m20 as f32, affine.m21 as f32, affine.m22 as f32, 0.0],
        [
            lighting.direction[0],
            lighting.direction[1],
            lighting.direction[2],
            lighting.ambient,
        ],
        [
            lighting.directional,
            lighting.toon_threshold,
            lighting.toon_high,
            lighting.toon_low,
        ],
        [
            color_key[0],
            color_key[1],
            color_key[2],
            color_key_enabled as i32 as f32,
        ],
    ] {
        for value in row {
            buffer.extend_from_slice(&value.to_le_bytes());
        }
    }
}

fn emit_color_material_packet(
    vm: &mut SharedVm,
    target: MascotTarget,
    affine: Affine,
    lighting: Option<Lighting>,
    sphere_texture_handle: i32,
    blend_mode: u32,
    vertices: &[[f32; 12]],
) {
    if vertices.is_empty() {
        return;
    }
    let buffer = vm.m3g_command_buffer_mut();
    buffer.push(cmd::MASCOT_DRAW_COLOR_MATERIAL);
    buffer.extend_from_slice(&(vertices.len() as u32).to_le_bytes());
    buffer.extend_from_slice(&blend_mode.to_le_bytes());
    for value in [
        target.clip_x,
        target.clip_y,
        target.clip_width,
        target.clip_height,
    ] {
        buffer.extend_from_slice(&value.to_le_bytes());
    }
    write_lighting_uniform(buffer, affine, lighting, [0.0; 3], false);
    buffer.extend_from_slice(&sphere_texture_handle.to_le_bytes());
    for vertex in vertices {
        for value in vertex {
            buffer.extend_from_slice(&value.to_le_bytes());
        }
    }
}

fn texture_bindings(vm: &SharedVm, textures: u32) -> Vec<TextureBinding> {
    if textures == NULL {
        return Vec::new();
    }
    (0..vm.heap.array_length(textures))
        .map(|index| texture_binding(vm, vm.heap.array_read_u32(textures, index)))
        .collect()
}

fn selected_figure_texture_bindings(vm: &SharedVm, figure: u32) -> Vec<TextureBinding> {
    let textures = field_ref(vm, figure, CLASS_FIGURE, "textures");
    let selected_texture = field_i32(vm, figure, CLASS_FIGURE, "selectedTex");
    if textures == NULL
        || selected_texture < 0
        || selected_texture as u32 >= vm.heap.array_length(textures)
    {
        return vec![TextureBinding::default()];
    }
    vec![texture_binding(
        vm,
        vm.heap.array_read_u32(textures, selected_texture as u32),
    )]
}

fn texture_binding(vm: &SharedVm, texture: u32) -> TextureBinding {
    if texture == NULL {
        return TextureBinding::default();
    }
    let handle = field_i32(vm, texture, CLASS_TEXTURE, "image");
    let color_key = field_ref(vm, texture, CLASS_TEXTURE, "colorKey");
    let color_key = if color_key == NULL || vm.heap.array_length(color_key) < 3 {
        [0.0; 3]
    } else {
        [
            vm.heap.array_read_f32(color_key, 0),
            vm.heap.array_read_f32(color_key, 1),
            vm.heap.array_read_f32(color_key, 2),
        ]
    };
    TextureBinding { handle, color_key }
}

fn emit_command_textured(
    vm: &mut SharedVm,
    target: MascotTarget,
    layout: Layout,
    texture: TextureBinding,
    lighting: Option<Lighting>,
    color_key_enabled: bool,
    blend_mode: u32,
    sphere_texture_handle: i32,
    vertices: &[[f32; 11]],
) {
    if texture.handle <= 0 || vertices.is_empty() {
        return;
    }
    ensure_mascot_texture_uploaded(vm, texture.handle);
    if sphere_texture_handle > 0 {
        ensure_mascot_texture_uploaded(vm, sphere_texture_handle);
    }
    emit_textured_packet(
        vm,
        target,
        texture.handle,
        false,
        layout.affine,
        lighting,
        texture.color_key,
        color_key_enabled,
        blend_mode,
        sphere_texture_handle,
        vertices,
    );
}

fn point_sprite_half_extents(
    layout: Layout,
    target: MascotTarget,
    z: f32,
    width: i32,
    height: i32,
    flags: i32,
) -> Option<(f32, f32)> {
    let pixel_size = flags & 0x01 != 0;
    let no_perspective = flags & 0x02 != 0;
    if flags & !0x03 != 0 {
        return None;
    }
    let width = width as f32;
    let height = height as f32;
    let target_width = target.width.max(1) as f32;
    let target_height = target.height.max(1) as f32;
    match layout.setting {
        COMMAND_PARALLEL_SCALE if layout.scale_x != 0 && layout.scale_y != 0 => {
            if pixel_size {
                Some((width / target_width, height / target_height))
            } else {
                Some((
                    width * layout.scale_x as f32 / (target_width * 4096.0),
                    height * layout.scale_y as f32 / (target_height * 4096.0),
                ))
            }
        }
        COMMAND_PARALLEL_SIZE => {
            if pixel_size {
                Some((width / target_width, height / target_height))
            } else {
                let projection_width = if layout.parallel_width == 0 {
                    1600.0
                } else {
                    layout.parallel_width as f32
                };
                let projection_height = if layout.parallel_height == 0 {
                    projection_width * target_height / target_width
                } else {
                    layout.parallel_height as f32
                };
                Some((width / projection_width, height / projection_height))
            }
        }
        COMMAND_PERSPECTIVE_FOV if z != 0.0 && layout.near > 0 && layout.far > layout.near => {
            let near = layout.near as f32;
            let sx = 1.0 / ((layout.angle as f32 / 4096.0) * std::f32::consts::PI).tan();
            let sy = sx * target_width / target_height;
            if pixel_size {
                let scale = if no_perspective { 1.0 } else { near / z };
                Some((width * scale / target_width, height * scale / target_height))
            } else {
                let scale = if no_perspective { near } else { z };
                Some((width * sx / (2.0 * scale), height * sy / (2.0 * scale)))
            }
        }
        COMMAND_PERSPECTIVE_WH if z != 0.0 && layout.near > 0 && layout.far > layout.near => {
            let near = layout.near as f32;
            let projection_width = if layout.perspective_width == 0 {
                target_width
            } else {
                layout.perspective_width as f32 / 4096.0
            };
            let projection_height = if layout.perspective_height == 0 {
                target_height
            } else {
                layout.perspective_height as f32 / 4096.0
            };
            if projection_width == 0.0 || projection_height == 0.0 {
                return None;
            }
            if pixel_size {
                let scale = if no_perspective { 1.0 } else { near / z };
                Some((width * scale / target_width, height * scale / target_height))
            } else {
                let scale = if no_perspective { near } else { z };
                Some((
                    width * near / (projection_width * scale),
                    height * near / (projection_height * scale),
                ))
            }
        }
        _ => None,
    }
}

fn emit_point_sprites(
    vm: &mut SharedVm,
    target: MascotTarget,
    layout: Layout,
    texture: TextureBinding,
    lighting: Option<Lighting>,
    sphere_texture_handle: i32,
    transparency_enabled: bool,
    command: u32,
    coordinates: &[i32],
    parameters: &[i32],
) {
    if texture.handle <= 0 || parameters.len() < 8 {
        return;
    }
    let mode = command & PDATA_POINT_SPRITE_PARAMS_PER_VERTEX;
    let per_sprite = mode != PDATA_POINT_SPRITE_PARAMS_PER_COMMAND;
    let count = coordinates.len() / 3;
    let mut vertices = Vec::with_capacity(count * 6);
    for index in 0..count {
        let parameter_offset = if per_sprite { index * 8 } else { 0 };
        let Some(params) = parameters.get(parameter_offset..parameter_offset + 8) else {
            return;
        };
        let [center_x, center_y, center_z] = [
            coordinates[index * 3],
            coordinates[index * 3 + 1],
            coordinates[index * 3 + 2],
        ];
        let Some(center) = project_vertex(layout, target, center_x, center_y, center_z) else {
            return;
        };
        let Some(center) = clip_vertex_to_ndc(center) else {
            continue;
        };
        let (_, _, z) = transform_vertex(layout.affine, center_x, center_y, center_z);
        let Some((half_width, half_height)) =
            point_sprite_half_extents(layout, target, z, params[0], params[1], params[7])
        else {
            return;
        };
        let radians = params[2] as f32 * std::f32::consts::PI / 2048.0;
        let sin = radians.sin();
        let cos = radians.cos();
        let u0 = params[3] as f32;
        let v0 = params[4] as f32;
        let u1 = params[5] as f32 - 1.0;
        let v1 = params[6] as f32 - 1.0;
        let corner = |x: f32, y: f32| {
            [
                center[0] + x * cos + y * sin,
                center[1] - x * sin + y * cos,
                center[2],
            ]
        };
        let top_left = corner(-half_width, half_height);
        let top_right = corner(half_width, half_height);
        let bottom_left = corner(-half_width, -half_height);
        let bottom_right = corner(half_width, -half_height);
        for (position, u, v) in [
            (top_left, u0, v1),
            (bottom_left, u0, v0),
            (top_right, u1, v1),
            (top_right, u1, v1),
            (bottom_left, u0, v0),
            (bottom_right, u1, v0),
        ] {
            vertices.push([
                position[0],
                position[1],
                position[2],
                1.0,
                u,
                v,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
            ]);
        }
    }
    emit_command_textured(
        vm,
        target,
        layout,
        texture,
        lighting,
        command & PATTR_COLORKEY != 0,
        blend_mode(transparency_enabled, command),
        sphere_texture_handle,
        &vertices,
    );
}

fn emit_primitives(
    vm: &mut SharedVm,
    target: MascotTarget,
    layout: Layout,
    command: u32,
    primitive_count: usize,
    coordinates: &[i32],
    colors: &[i32],
    texture: TextureBinding,
    texture_coords: &[i32],
    normals: &[i32],
    lighting: Option<Lighting>,
    sphere_texture_handle: i32,
    transparency_enabled: bool,
) {
    let lighting = (command & PATTR_LIGHTING != 0)
        .then_some(lighting)
        .flatten();
    let primitive = command & 0x0700_0000;
    let Some(count) = vertex_count(primitive, primitive_count) else {
        return;
    };
    if coordinates.len() < count * 3 {
        return;
    }
    if primitive == PRIMITIVE_POINT_SPRITES {
        emit_point_sprites(
            vm,
            target,
            layout,
            texture,
            lighting,
            sphere_texture_handle,
            transparency_enabled,
            command,
            coordinates,
            texture_coords,
        );
        return;
    }
    let mut projected = Vec::with_capacity(if primitive == PRIMITIVE_QUADS {
        primitive_count * 6
    } else {
        count
    });
    let mut source = Vec::with_capacity(count);
    for index in 0..count {
        let offset = index * 3;
        let Some(vertex) = project_vertex(
            layout,
            target,
            coordinates[offset],
            coordinates[offset + 1],
            coordinates[offset + 2],
        ) else {
            return;
        };
        source.push(vertex);
    }
    if primitive == PRIMITIVE_LINES {
        let blend_mode = blend_mode(transparency_enabled, command);
        let color_per_primitive = command & PDATA_COLOR_PER_COMMAND == 0;
        for (index, line) in source.chunks_exact(2).enumerate() {
            let color = if color_per_primitive {
                colors.get(index).copied()
            } else {
                colors.first().copied()
            }
            .unwrap_or(0xffff_ffff_u32 as i32);
            emit_primitive_packet(vm, target, PRIMITIVE_LINES, line, color, blend_mode);
        }
        return;
    }
    if texture.handle > 0
        && texture_coords.len() >= count * 2
        && (primitive == PRIMITIVE_TRIANGLES || primitive == PRIMITIVE_QUADS)
    {
        let source_indices: Vec<usize> = if primitive == PRIMITIVE_QUADS {
            (0..primitive_count)
                .flat_map(|index| {
                    let base = index * 4;
                    [base, base + 1, base + 2, base + 2, base + 1, base + 3]
                })
                .collect()
        } else {
            (0..count).collect()
        };
        let vertices = source_indices
            .iter()
            .enumerate()
            .filter_map(|(vertex_offset, &index)| {
                source.get(index).map(|position| {
                    let normal = match command & 0x0300 {
                        PDATA_NORMAL_PER_FACE if normals.len() >= 3 => {
                            let normal_offset = (vertex_offset / 3) * 3;
                            [
                                normals[normal_offset] as f32,
                                normals[normal_offset + 1] as f32,
                                normals[normal_offset + 2] as f32,
                            ]
                        }
                        PDATA_NORMAL_PER_VERTEX if normals.len() >= count * 3 => [
                            normals[index * 3] as f32,
                            normals[index * 3 + 1] as f32,
                            normals[index * 3 + 2] as f32,
                        ],
                        _ => [0.0; 3],
                    };
                    [
                        position[0],
                        position[1],
                        position[2],
                        position[3],
                        texture_coords[index * 2] as f32,
                        texture_coords[index * 2 + 1] as f32,
                        normal[0],
                        normal[1],
                        normal[2],
                        (lighting.is_some() && normals.len() >= 3) as i32 as f32,
                        (lighting.is_some()
                            && sphere_texture_handle > 0
                            && command & PATTR_SPHERE_MAP != 0) as i32
                            as f32,
                    ]
                })
            })
            .collect::<Vec<_>>();
        emit_command_textured(
            vm,
            target,
            layout,
            texture,
            lighting,
            command & PATTR_COLORKEY != 0,
            blend_mode(transparency_enabled, command),
            sphere_texture_handle,
            &vertices,
        );
        return;
    }
    let material_lighting = lighting.is_some() && normals.len() >= 3;
    let material_reflection =
        material_lighting && sphere_texture_handle > 0 && command & PATTR_SPHERE_MAP != 0;
    if (primitive == PRIMITIVE_TRIANGLES || primitive == PRIMITIVE_QUADS)
        && (material_lighting || material_reflection || command & PDATA_COLOR_PER_FACE != 0)
    {
        let source_indices: Vec<usize> = if primitive == PRIMITIVE_QUADS {
            (0..primitive_count)
                .flat_map(|index| {
                    let base = index * 4;
                    [base, base + 1, base + 2, base + 2, base + 1, base + 3]
                })
                .collect()
        } else {
            (0..count).collect()
        };
        let vertices = source_indices
            .iter()
            .enumerate()
            .filter_map(|(vertex_offset, &index)| {
                source.get(index).map(|position| {
                    let color_index = if command & PDATA_COLOR_PER_FACE != 0 {
                        if primitive == PRIMITIVE_QUADS {
                            vertex_offset / 6
                        } else {
                            vertex_offset / 3
                        }
                    } else {
                        0
                    };
                    let color = colors
                        .get(color_index)
                        .copied()
                        .unwrap_or(0xffff_ffff_u32 as i32);
                    let [red, green, blue] =
                        [16, 8, 0].map(|shift| ((color as u32 >> shift) & 0xff) as f32 / 255.0);
                    let normal = match command & 0x0300 {
                        PDATA_NORMAL_PER_FACE if normals.len() >= 3 => {
                            let normal_offset = (vertex_offset / 3) * 3;
                            [
                                normals[normal_offset] as f32,
                                normals[normal_offset + 1] as f32,
                                normals[normal_offset + 2] as f32,
                            ]
                        }
                        PDATA_NORMAL_PER_VERTEX if normals.len() >= count * 3 => [
                            normals[index * 3] as f32,
                            normals[index * 3 + 1] as f32,
                            normals[index * 3 + 2] as f32,
                        ],
                        _ => [0.0; 3],
                    };
                    [
                        position[0],
                        position[1],
                        position[2],
                        position[3],
                        normal[0],
                        normal[1],
                        normal[2],
                        red,
                        green,
                        blue,
                        material_lighting as i32 as f32,
                        material_reflection as i32 as f32,
                    ]
                })
            })
            .collect::<Vec<_>>();
        emit_color_material_packet(
            vm,
            target,
            layout.affine,
            lighting,
            sphere_texture_handle,
            blend_mode(transparency_enabled, command),
            &vertices,
        );
        return;
    }
    if primitive == PRIMITIVE_QUADS {
        for quad in source.chunks_exact(4) {
            projected.extend_from_slice(&[quad[0], quad[1], quad[2], quad[3], quad[0], quad[2]]);
        }
    } else if primitive == PRIMITIVE_POINTS {
        let half_width = 1.0 / target.width.max(1) as f32;
        let half_height = 1.0 / target.height.max(1) as f32;
        for point in source {
            let Some(point) = clip_vertex_to_ndc(point) else {
                continue;
            };
            let top_left = [point[0] - half_width, point[1] + half_height, point[2], 1.0];
            let top_right = [point[0] + half_width, point[1] + half_height, point[2], 1.0];
            let bottom_left = [point[0] - half_width, point[1] - half_height, point[2], 1.0];
            let bottom_right = [point[0] + half_width, point[1] - half_height, point[2], 1.0];
            projected.extend_from_slice(&[
                top_left,
                bottom_left,
                top_right,
                bottom_left,
                bottom_right,
                top_right,
            ]);
        }
    } else {
        projected = source;
    }
    emit_primitive_packet(
        vm,
        target,
        if primitive == PRIMITIVE_POINTS {
            PRIMITIVE_TRIANGLES
        } else {
            primitive
        },
        &projected,
        colors.first().copied().unwrap_or(0xffff_ffff_u32 as i32),
        blend_mode(transparency_enabled, command),
    );
}

fn command_payload_words(command: u32, primitive_count: usize) -> Option<usize> {
    let primitive = command & 0x0700_0000;
    let vertices = vertex_count(primitive, primitive_count)?;
    let mut words = vertices * 3;
    if primitive == PRIMITIVE_TRIANGLES || primitive == PRIMITIVE_QUADS {
        words += match command & 0x0300 {
            PDATA_NORMAL_PER_FACE => primitive_count * 3,
            PDATA_NORMAL_PER_VERTEX => vertices * 3,
            _ => 0,
        };
    }
    words += if command & PDATA_COLOR_PER_COMMAND != 0 {
        1
    } else if command & PDATA_COLOR_PER_FACE != 0 {
        primitive_count
    } else {
        0
    };
    if command & PDATA_TEXTURE_COORD != 0 {
        words += if primitive == PRIMITIVE_POINT_SPRITES {
            match command & PDATA_POINT_SPRITE_PARAMS_PER_VERTEX {
                PDATA_POINT_SPRITE_PARAMS_PER_COMMAND => 8,
                PDATA_POINT_SPRITE_PARAMS_PER_FACE | PDATA_POINT_SPRITE_PARAMS_PER_VERTEX => {
                    primitive_count * 8
                }
                _ => return None,
            }
        } else {
            vertices * 2
        };
    }
    Some(words)
}

fn draw_command_list(
    ctx: &mut NativeContext<'_>,
    renderer: u32,
    textures: u32,
    x: i32,
    y: i32,
    layout_ref: u32,
    effect: u32,
    command_list: u32,
) {
    let Some(mut target) = renderer_target(ctx.vm, renderer) else {
        return;
    };
    if command_list == NULL || ctx.vm.heap.array_length(command_list) == 0 {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return;
    }
    if ctx.vm.heap.array_read_i32(command_list, 0) != COMMAND_LIST_VERSION_1_0 {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return;
    }

    let mut layout = read_layout(ctx.vm, layout_ref);
    layout.center_x = layout.center_x.saturating_add(x);
    layout.center_y = layout.center_y.saturating_add(y);
    let texture_bindings = texture_bindings(ctx.vm, textures);
    let mut texture_index = 0_usize;
    let mut command_lighting = read_lighting(ctx.vm, effect);
    let mut command_sphere_map = false;
    let mut command_transparency_enabled =
        field_i32(ctx.vm, effect, CLASS_EFFECT_3D, "isTransparency") != 0;
    let total = ctx.vm.heap.array_length(command_list) as usize;
    let mut cursor = 1_usize;
    while cursor < total {
        let command = ctx.vm.heap.array_read_i32(command_list, cursor as u32) as u32;
        cursor += 1;
        match command & 0xff00_0000 {
            COMMAND_END => return,
            COMMAND_FLUSH => ctx.vm.m3g_command_buffer_mut().push(cmd::MASCOT_FLUSH),
            COMMAND_ATTRIBUTE => {
                command_transparency_enabled = command & ENV_ATTR_SEMI_TRANSPARENT != 0;
                if command & ENV_ATTR_LIGHTING == 0 {
                    command_lighting = None;
                } else if command_lighting.is_none() {
                    command_lighting = Some(Lighting {
                        direction: [0.0, 0.0, 4096.0],
                        ambient: 0.0,
                        directional: 4096.0,
                        toon_threshold: -1.0,
                        toon_high: 0.0,
                        toon_low: 0.0,
                    });
                }
                command_sphere_map = command & PATTR_SPHERE_MAP != 0;
                if command & ENV_ATTR_TOON_SHADING != 0 {
                    if let Some(lighting) = &mut command_lighting {
                        lighting.toon_threshold = 0.0;
                    }
                }
            }
            COMMAND_CENTER if cursor + 2 <= total => {
                layout.center_x =
                    x.saturating_add(ctx.vm.heap.array_read_i32(command_list, cursor as u32));
                layout.center_y =
                    y.saturating_add(ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 1));
                cursor += 2;
            }
            COMMAND_TEXTURE_INDEX => {
                let index = (command & 0x00ff_ffff) as usize;
                if index < texture_bindings.len() {
                    texture_index = index;
                }
            }
            COMMAND_CLIP if cursor + 4 <= total => {
                let clip_x = ctx.vm.heap.array_read_i32(command_list, cursor as u32);
                let clip_y = ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 1);
                let clip_right = ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 2);
                let clip_bottom = ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 3);
                let left = target.clip_x.max(clip_x);
                let top = target.clip_y.max(clip_y);
                let right = (target.clip_x + target.clip_width).min(clip_right);
                let bottom = (target.clip_y + target.clip_height).min(clip_bottom);
                target.clip_x = left;
                target.clip_y = top;
                target.clip_width = (right - left).max(0);
                target.clip_height = (bottom - top).max(0);
                cursor += 4;
            }
            COMMAND_AFFINE_INDEX => {
                let affine_array =
                    field_ref(ctx.vm, layout_ref, CLASS_FIGURE_LAYOUT, "affineArray");
                let index = command & 0x00ff_ffff;
                if affine_array != NULL && index < ctx.vm.heap.array_length(affine_array) {
                    layout.affine =
                        read_affine(ctx.vm, ctx.vm.heap.array_read_u32(affine_array, index));
                }
            }
            COMMAND_PARALLEL_SCALE if cursor + 2 <= total => {
                layout.scale_x = ctx.vm.heap.array_read_i32(command_list, cursor as u32);
                layout.scale_y = ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 1);
                layout.setting = COMMAND_PARALLEL_SCALE;
                cursor += 2;
            }
            COMMAND_PARALLEL_SIZE if cursor + 2 <= total => {
                layout.parallel_width = ctx.vm.heap.array_read_i32(command_list, cursor as u32);
                layout.parallel_height =
                    ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 1);
                layout.setting = COMMAND_PARALLEL_SIZE;
                cursor += 2;
            }
            COMMAND_PERSPECTIVE_FOV if cursor + 3 <= total => {
                layout.near = ctx.vm.heap.array_read_i32(command_list, cursor as u32);
                layout.far = ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 1);
                layout.angle = ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 2);
                layout.setting = COMMAND_PERSPECTIVE_FOV;
                cursor += 3;
            }
            COMMAND_PERSPECTIVE_WH if cursor + 4 <= total => {
                layout.near = ctx.vm.heap.array_read_i32(command_list, cursor as u32);
                layout.far = ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 1);
                layout.perspective_width =
                    ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 2);
                layout.perspective_height =
                    ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 3);
                layout.setting = COMMAND_PERSPECTIVE_WH;
                cursor += 4;
            }
            COMMAND_AMBIENT_LIGHT if cursor < total => {
                if let Some(lighting) = &mut command_lighting {
                    lighting.ambient = ctx
                        .vm
                        .heap
                        .array_read_i32(command_list, cursor as u32)
                        .clamp(0, 4096) as f32;
                }
                cursor += 1;
            }
            COMMAND_DIRECTION_LIGHT if cursor + 4 <= total => {
                if let Some(lighting) = &mut command_lighting {
                    lighting.direction = [
                        ctx.vm.heap.array_read_i32(command_list, cursor as u32) as f32,
                        ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 1) as f32,
                        ctx.vm.heap.array_read_i32(command_list, cursor as u32 + 2) as f32,
                    ];
                    lighting.directional = ctx
                        .vm
                        .heap
                        .array_read_i32(command_list, cursor as u32 + 3)
                        .clamp(0, 16384) as f32;
                }
                cursor += 4;
            }
            COMMAND_THRESHOLD if cursor + 3 <= total => {
                if let Some(lighting) = &mut command_lighting {
                    lighting.toon_threshold = ctx
                        .vm
                        .heap
                        .array_read_i32(command_list, cursor as u32)
                        .clamp(0, 255) as f32;
                    lighting.toon_high = ctx
                        .vm
                        .heap
                        .array_read_i32(command_list, cursor as u32 + 1)
                        .clamp(0, 255) as f32;
                    lighting.toon_low = ctx
                        .vm
                        .heap
                        .array_read_i32(command_list, cursor as u32 + 2)
                        .clamp(0, 255) as f32;
                }
                cursor += 3;
            }
            _ => {
                let primitive = command & 0x0700_0000;
                let primitive_count = ((command >> 16) & 0xff) as usize;
                let Some(source_vertex_count) = vertex_count(primitive, primitive_count) else {
                    continue;
                };
                let Some(payload_words) = command_payload_words(command, primitive_count) else {
                    continue;
                };
                if cursor + payload_words > total {
                    return;
                }
                let coordinate_words = source_vertex_count * 3;
                let coordinates = (0..coordinate_words)
                    .map(|index| {
                        ctx.vm
                            .heap
                            .array_read_i32(command_list, (cursor + index) as u32)
                    })
                    .collect::<Vec<_>>();
                let mut payload_cursor = cursor + coordinate_words;
                let normal_count =
                    if primitive == PRIMITIVE_TRIANGLES || primitive == PRIMITIVE_QUADS {
                        match command & 0x0300 {
                            PDATA_NORMAL_PER_FACE => primitive_count * 3,
                            PDATA_NORMAL_PER_VERTEX => source_vertex_count * 3,
                            _ => 0,
                        }
                    } else {
                        0
                    };
                let normals = (0..normal_count)
                    .map(|index| {
                        ctx.vm
                            .heap
                            .array_read_i32(command_list, (payload_cursor + index) as u32)
                    })
                    .collect::<Vec<_>>();
                payload_cursor += normal_count;
                let color_count = if command & PDATA_COLOR_PER_COMMAND != 0 {
                    1
                } else if command & PDATA_COLOR_PER_FACE != 0 {
                    primitive_count
                } else {
                    0
                };
                let colors = (0..color_count)
                    .map(|index| {
                        ctx.vm
                            .heap
                            .array_read_i32(command_list, (payload_cursor + index) as u32)
                    })
                    .collect::<Vec<_>>();
                payload_cursor += color_count;
                let texture_coord_count = payload_words - (payload_cursor - cursor);
                let texture_coords = (0..texture_coord_count)
                    .map(|index| {
                        ctx.vm
                            .heap
                            .array_read_i32(command_list, (payload_cursor + index) as u32)
                    })
                    .collect::<Vec<_>>();
                let command = command
                    | if command_sphere_map {
                        PATTR_SPHERE_MAP
                    } else {
                        0
                    };
                emit_primitives(
                    ctx.vm,
                    target,
                    layout,
                    command,
                    primitive_count,
                    &coordinates,
                    &colors,
                    texture_bindings
                        .get(texture_index)
                        .copied()
                        .unwrap_or_default(),
                    &texture_coords,
                    &normals,
                    command_lighting,
                    read_sphere_texture(ctx.vm, effect),
                    command_transparency_enabled,
                );
                cursor += payload_words;
            }
        }
    }
}

fn render_primitives(
    ctx: &mut NativeContext<'_>,
    renderer: u32,
    texture: u32,
    x: i32,
    y: i32,
    layout_ref: u32,
    effect: u32,
    command: i32,
    primitive_count: i32,
    vertex_coords: u32,
    normals: u32,
    texture_coords: u32,
    colors: u32,
) {
    let Some(target) = renderer_target(ctx.vm, renderer) else {
        return;
    };
    if primitive_count <= 0 || vertex_coords == NULL {
        return;
    }
    let mut layout = read_layout(ctx.vm, layout_ref);
    layout.center_x = layout.center_x.saturating_add(x);
    layout.center_y = layout.center_y.saturating_add(y);
    let coordinates = (0..ctx.vm.heap.array_length(vertex_coords))
        .map(|index| ctx.vm.heap.array_read_i32(vertex_coords, index))
        .collect::<Vec<_>>();
    let colors = (0..ctx.vm.heap.array_length(colors))
        .map(|index| ctx.vm.heap.array_read_i32(colors, index))
        .collect::<Vec<_>>();
    let texture_coords = (0..ctx.vm.heap.array_length(texture_coords))
        .map(|index| ctx.vm.heap.array_read_i32(texture_coords, index))
        .collect::<Vec<_>>();
    let normals = (0..ctx.vm.heap.array_length(normals))
        .map(|index| ctx.vm.heap.array_read_i32(normals, index))
        .collect::<Vec<_>>();
    let texture = texture_binding(ctx.vm, texture);
    let transparency_enabled = field_i32(ctx.vm, effect, CLASS_EFFECT_3D, "isTransparency") != 0
        || command as u32 & ENV_ATTR_SEMI_TRANSPARENT != 0;
    emit_primitives(
        ctx.vm,
        target,
        layout,
        command as u32,
        primitive_count as usize,
        &coordinates,
        &colors,
        texture,
        &texture_coords,
        &normals,
        read_lighting(ctx.vm, effect),
        read_sphere_texture(ctx.vm, effect),
        transparency_enabled,
    );
}

fn direct_float_range(vm: &SharedVm, buffer: u32) -> Option<(i32, usize)> {
    if buffer == NULL {
        return None;
    }
    let address = field_i32(vm, buffer, CLASS_FLOAT_BUFFER, "arrayOffset");
    let float_count =
        usize::try_from(field_i32(vm, buffer, CLASS_FLOAT_BUFFER, "capacity")).ok()?;
    let byte_count = float_count.checked_mul(std::mem::size_of::<f32>())?;
    vm.direct_buffer_arena.bytes(address, byte_count)?;
    Some((address, float_count))
}

fn direct_float_bytes<'a>(vm: &'a SharedVm, buffer: u32) -> Option<(i32, &'a [u8])> {
    let (address, float_count) = direct_float_range(vm, buffer)?;
    let byte_count = float_count.checked_mul(std::mem::size_of::<f32>())?;
    Some((address, vm.direct_buffer_arena.bytes(address, byte_count)?))
}

fn float_at(bytes: &[u8], index: usize) -> Option<f32> {
    let offset = index.checked_mul(std::mem::size_of::<f32>())?;
    let bytes = bytes.get(offset..offset.checked_add(std::mem::size_of::<f32>())?)?;
    Some(f32::from_bits(u32::from_le_bytes(bytes.try_into().ok()?)))
}

fn push_f32(bytes: &mut Vec<u8>, value: f32) {
    bytes.extend_from_slice(&value.to_bits().to_le_bytes());
}

fn read_float_buffer_vertices(vm: &SharedVm, buffer: u32) -> Vec<[f32; 3]> {
    let Some((_, bytes)) = direct_float_bytes(vm, buffer) else {
        return Vec::new();
    };
    let vertex_count = bytes.len() / (std::mem::size_of::<f32>() * 3);
    let mut vertices = Vec::with_capacity(vertex_count);
    for values in bytes.chunks_exact(std::mem::size_of::<f32>() * 3) {
        let Some(x) = float_at(values, 0) else {
            return Vec::new();
        };
        let Some(y) = float_at(values, 1) else {
            return Vec::new();
        };
        let Some(z) = float_at(values, 2) else {
            return Vec::new();
        };
        vertices.push([x, y, z]);
    }
    vertices
}

fn direct_byte_buffer_address(vm: &SharedVm, buffer: u32) -> Option<i32> {
    (buffer != NULL).then(|| field_i32(vm, buffer, CLASS_BYTE_BUFFER, "arrayOffset"))
}

fn action_matrix(vm: &SharedVm, action_matrices: u32, bone_index: usize) -> Option<BoneMatrix> {
    if action_matrices == NULL || bone_index >= vm.heap.array_length(action_matrices) as usize / 12
    {
        return None;
    }
    let offset = (bone_index * 12) as u32;
    Some(BoneMatrix {
        m00: vm.heap.array_read_f32(action_matrices, offset),
        m01: vm.heap.array_read_f32(action_matrices, offset + 1),
        m02: vm.heap.array_read_f32(action_matrices, offset + 2),
        m03: vm.heap.array_read_f32(action_matrices, offset + 3),
        m10: vm.heap.array_read_f32(action_matrices, offset + 4),
        m11: vm.heap.array_read_f32(action_matrices, offset + 5),
        m12: vm.heap.array_read_f32(action_matrices, offset + 6),
        m13: vm.heap.array_read_f32(action_matrices, offset + 7),
        m20: vm.heap.array_read_f32(action_matrices, offset + 8),
        m21: vm.heap.array_read_f32(action_matrices, offset + 9),
        m22: vm.heap.array_read_f32(action_matrices, offset + 10),
        m23: vm.heap.array_read_f32(action_matrices, offset + 11),
    })
}

fn transform_buffers(
    vm: &mut SharedVm,
    source_vertices: u32,
    destination_vertices: u32,
    source_normals: u32,
    destination_normals: u32,
    bones: u32,
    action_matrices: u32,
) -> bool {
    let Some((_, source_vertex_bytes)) = direct_float_bytes(vm, source_vertices) else {
        return false;
    };
    let vertex_count = source_vertex_bytes.len() / (std::mem::size_of::<f32>() * 3);
    if vertex_count == 0 {
        return false;
    }
    let Some((destination_vertex_address, destination_vertex_float_count)) =
        direct_float_range(vm, destination_vertices)
    else {
        return false;
    };
    if destination_vertex_float_count < vertex_count * 3 {
        return false;
    }
    let source_normal_bytes = if source_normals == NULL {
        None
    } else {
        let Some((_, bytes)) = direct_float_bytes(vm, source_normals) else {
            return false;
        };
        if bytes.len() / (std::mem::size_of::<f32>() * 3) != vertex_count {
            return false;
        }
        let Some((_, destination_normal_float_count)) = direct_float_range(vm, destination_normals)
        else {
            return false;
        };
        if destination_normal_float_count < vertex_count * 3 {
            return false;
        }
        Some(bytes)
    };
    if bones == NULL {
        return false;
    }
    let bone_capacity = field_i32(vm, bones, CLASS_BYTE_BUFFER, "capacity");
    let Some(bone_capacity) = usize::try_from(bone_capacity).ok() else {
        return false;
    };
    let Some(bone_address) = direct_byte_buffer_address(vm, bones) else {
        return false;
    };
    let Some(bone_bytes) = vm.direct_buffer_arena.bytes(bone_address, bone_capacity) else {
        return false;
    };
    const BONE_BYTES: usize = 56;
    let bone_count = bone_capacity / BONE_BYTES;
    let mut matrices = Vec::with_capacity(bone_count);
    let mut vertex_offset = 0usize;
    let mut transformed_vertices =
        Vec::with_capacity(vertex_count * 3 * std::mem::size_of::<f32>());
    let mut transformed_normals = source_normal_bytes
        .map(|_| Vec::with_capacity(vertex_count * 3 * std::mem::size_of::<f32>()));
    for bone_index in 0..bone_count {
        let byte_offset = bone_index * BONE_BYTES;
        let Some(length_bytes) = bone_bytes.get(byte_offset..byte_offset + 4) else {
            return false;
        };
        let length = i32::from_le_bytes(length_bytes.try_into().unwrap());
        let Some(parent_bytes) = bone_bytes.get(byte_offset + 4..byte_offset + 8) else {
            return false;
        };
        let parent = i32::from_le_bytes(parent_bytes.try_into().unwrap());
        let matrix_offset = byte_offset + 8;
        let Some(matrix_bytes) = bone_bytes.get(matrix_offset..matrix_offset + 48) else {
            return false;
        };
        let Some(matrix) = (|| {
            Some(BoneMatrix {
                m00: float_at(matrix_bytes, 0)?,
                m01: float_at(matrix_bytes, 1)?,
                m02: float_at(matrix_bytes, 2)?,
                m03: float_at(matrix_bytes, 3)?,
                m10: float_at(matrix_bytes, 4)?,
                m11: float_at(matrix_bytes, 5)?,
                m12: float_at(matrix_bytes, 6)?,
                m13: float_at(matrix_bytes, 7)?,
                m20: float_at(matrix_bytes, 8)?,
                m21: float_at(matrix_bytes, 9)?,
                m22: float_at(matrix_bytes, 10)?,
                m23: float_at(matrix_bytes, 11)?,
            })
        })() else {
            return false;
        };
        let mut matrix = if parent < 0 {
            matrix
        } else {
            let Some(parent_matrix) = matrices.get(parent as usize).copied() else {
                return false;
            };
            BoneMatrix::multiply(parent_matrix, matrix)
        };
        if let Some(action_matrix) = action_matrix(vm, action_matrices, bone_index) {
            matrix = BoneMatrix::multiply(matrix, action_matrix);
        }
        matrices.push(matrix);
        let Some(length) = usize::try_from(length).ok() else {
            return false;
        };
        for _ in 0..length {
            let Some(x) = float_at(source_vertex_bytes, vertex_offset * 3) else {
                return false;
            };
            let Some(y) = float_at(source_vertex_bytes, vertex_offset * 3 + 1) else {
                return false;
            };
            let Some(z) = float_at(source_vertex_bytes, vertex_offset * 3 + 2) else {
                return false;
            };
            let [x, y, z] = matrix.transform_point(x, y, z);
            push_f32(&mut transformed_vertices, x);
            push_f32(&mut transformed_vertices, y);
            push_f32(&mut transformed_vertices, z);
            if let Some(source_normal_bytes) = source_normal_bytes {
                let Some(x) = float_at(source_normal_bytes, vertex_offset * 3) else {
                    return false;
                };
                let Some(y) = float_at(source_normal_bytes, vertex_offset * 3 + 1) else {
                    return false;
                };
                let Some(z) = float_at(source_normal_bytes, vertex_offset * 3 + 2) else {
                    return false;
                };
                let normal = [
                    x * matrix.m00 + y * matrix.m01 + z * matrix.m02,
                    x * matrix.m10 + y * matrix.m11 + z * matrix.m12,
                    x * matrix.m20 + y * matrix.m21 + z * matrix.m22,
                ];
                let output = transformed_normals
                    .as_mut()
                    .expect("normal output is allocated");
                push_f32(output, normal[0]);
                push_f32(output, normal[1]);
                push_f32(output, normal[2]);
            }
            vertex_offset += 1;
        }
    }
    if vertex_offset != vertex_count
        || !vm
            .direct_buffer_arena
            .copy_from_slice(destination_vertex_address, &transformed_vertices)
    {
        return false;
    }
    if let Some(transformed_normals) = transformed_normals {
        let Some((destination_normal_address, _)) = direct_float_range(vm, destination_normals)
        else {
            return false;
        };
        if !vm
            .direct_buffer_arena
            .copy_from_slice(destination_normal_address, &transformed_normals)
        {
            return false;
        }
    }
    true
}

fn array_byte(vm: &SharedVm, array: u32, index: usize) -> Option<u8> {
    (index < vm.heap.array_length(array) as usize)
        .then(|| vm.heap.array_read_i8(array, index as u32) as u8)
}

fn array_u16_le(vm: &SharedVm, array: u32, offset: usize) -> Option<u16> {
    Some(u16::from_le_bytes([
        array_byte(vm, array, offset)?,
        array_byte(vm, array, offset.checked_add(1)?)?,
    ]))
}

fn array_u32_le(vm: &SharedVm, array: u32, offset: usize) -> Option<u32> {
    Some(u32::from_le_bytes([
        array_byte(vm, array, offset)?,
        array_byte(vm, array, offset.checked_add(1)?)?,
        array_byte(vm, array, offset.checked_add(2)?)?,
        array_byte(vm, array, offset.checked_add(3)?)?,
    ]))
}

fn average_colors(colors: &[i32]) -> i32 {
    let count = colors.len().max(1) as i32;
    let red = colors.iter().map(|color| (color >> 16) & 0xff).sum::<i32>() / count;
    let green = colors.iter().map(|color| (color >> 8) & 0xff).sum::<i32>() / count;
    let blue = colors.iter().map(|color| color & 0xff).sum::<i32>() / count;
    0xff00_0000_u32 as i32 | red << 16 | green << 8 | blue
}

fn decode_bmp_rgba(vm: &SharedVm, bytes: u32) -> Option<MascotTextureImage> {
    if array_byte(vm, bytes, 0)? != b'B' || array_byte(vm, bytes, 1)? != b'M' {
        return None;
    }
    let pixel_offset = array_u32_le(vm, bytes, 10)? as usize;
    let dib_size = array_u32_le(vm, bytes, 14)? as usize;
    let width = array_u32_le(vm, bytes, 18)? as usize;
    let height = array_u32_le(vm, bytes, 22)? as usize;
    if dib_size < 12 || width == 0 || height == 0 || array_u16_le(vm, bytes, 28)? != 8 {
        return None;
    }
    let palette_size = array_u32_le(vm, bytes, 46)
        .filter(|size| *size > 0)
        .unwrap_or(256) as usize;
    let palette_offset = 14usize.checked_add(dib_size)?;
    let row_stride = width.checked_add(3)? & !3;
    let mut rgba = Vec::with_capacity(width.checked_mul(height)?.checked_mul(4)?);
    for y in 0..height {
        let source_row = height.checked_sub(y.checked_add(1)?)?;
        let row_offset = pixel_offset.checked_add(source_row.checked_mul(row_stride)?)?;
        for x in 0..width {
            let palette_index = array_byte(vm, bytes, row_offset.checked_add(x)?)? as usize;
            if palette_index >= palette_size {
                return None;
            }
            let entry = palette_offset.checked_add(palette_index.checked_mul(4)?)?;
            rgba.push(array_byte(vm, bytes, entry.checked_add(2)?)?);
            rgba.push(array_byte(vm, bytes, entry.checked_add(1)?)?);
            rgba.push(array_byte(vm, bytes, entry)?);
            rgba.push(0xff);
        }
    }
    Some(MascotTextureImage {
        width: width as u32,
        height: height as u32,
        rgba,
    })
}

fn emit_texture_upload(vm: &mut SharedVm, texture_handle: i32, image: &MascotTextureImage) {
    let buffer = vm.m3g_command_buffer_mut();
    buffer.push(cmd::MASCOT_UPLOAD_TEXTURE);
    buffer.extend_from_slice(&texture_handle.to_le_bytes());
    buffer.extend_from_slice(&image.width.to_le_bytes());
    buffer.extend_from_slice(&image.height.to_le_bytes());
    buffer.extend_from_slice(&(image.rgba.len() as u32).to_le_bytes());
    buffer.extend_from_slice(&image.rgba);
}

fn emit_textured_packet(
    vm: &mut SharedVm,
    target: MascotTarget,
    texture_handle: i32,
    cull_back_faces: bool,
    affine: Affine,
    lighting: Option<Lighting>,
    color_key: [f32; 3],
    color_key_enabled: bool,
    blend_mode: u32,
    sphere_texture_handle: i32,
    vertices: &[[f32; 11]],
) {
    if vertices.is_empty() {
        return;
    }
    let buffer = vm.m3g_command_buffer_mut();
    buffer.push(cmd::MASCOT_DRAW_TEXTURED);
    buffer.extend_from_slice(&(vertices.len() as u32).to_le_bytes());
    buffer.extend_from_slice(&texture_handle.to_le_bytes());
    buffer.extend_from_slice(&(cull_back_faces as u32 | (blend_mode << 8)).to_le_bytes());
    for value in [
        target.clip_x,
        target.clip_y,
        target.clip_width,
        target.clip_height,
    ] {
        buffer.extend_from_slice(&value.to_le_bytes());
    }
    write_lighting_uniform(buffer, affine, lighting, color_key, color_key_enabled);
    buffer.extend_from_slice(&sphere_texture_handle.to_le_bytes());
    for vertex in vertices {
        for value in vertex {
            buffer.extend_from_slice(&value.to_le_bytes());
        }
    }
}

fn ensure_mascot_texture_uploaded(vm: &mut SharedVm, texture_handle: i32) {
    if vm.mascot_texture_reset_pending {
        vm.m3g_command_buffer_mut().push(cmd::MASCOT_RESET_TEXTURES);
        vm.mascot_texture_reset_pending = false;
    }
    if !vm.mascot_uploaded_textures.insert(texture_handle) {
        return;
    }
    let Some(image) = vm.mascot_textures.get(&texture_handle).cloned() else {
        vm.mascot_uploaded_textures.remove(&texture_handle);
        return;
    };
    emit_texture_upload(vm, texture_handle, &image);
}

fn is_polygon_active(polygon_pattern: i32, current_pattern: i32) -> bool {
    polygon_pattern & current_pattern == polygon_pattern
}

fn render_model_polygons(
    vm: &mut SharedVm,
    target: MascotTarget,
    layout: Layout,
    polygons: u32,
    current_pattern: i32,
    source_vertices: &[[f32; 3]],
    source_normals: Option<&[[f32; 3]]>,
    lighting: Option<Lighting>,
    sphere_texture_handle: i32,
    textures: &[TextureBinding],
    textured: bool,
    blend_pass: Option<u32>,
    force_opaque: bool,
) {
    if polygons == NULL || source_vertices.is_empty() {
        return;
    }
    let mut textured_batches: Vec<(TextureBinding, bool, u32, bool, Vec<[f32; 11]>)> = Vec::new();
    for polygon_index in 0..vm.heap.array_length(polygons) {
        let polygon = vm.heap.array_read_u32(polygons, polygon_index);
        if polygon == NULL {
            continue;
        }
        let polygon_pattern = field_i32(vm, polygon, CLASS_MODEL_POLYGON, "pattern");
        if !is_polygon_active(polygon_pattern, current_pattern) {
            continue;
        }
        let source_blend_mode =
            (field_i32(vm, polygon, CLASS_MODEL_POLYGON, "blendMode") >> 1).max(0) as u32;
        if blend_pass.is_some_and(|pass| pass != source_blend_mode) {
            continue;
        }
        let blend_mode = if force_opaque { 0 } else { source_blend_mode };
        let indices = field_ref(vm, polygon, CLASS_MODEL_POLYGON, "indices");
        let color_data = field_ref(vm, polygon, CLASS_MODEL_POLYGON, "texCoords");
        if indices == NULL || color_data == NULL {
            continue;
        }
        let mut vertices = Vec::with_capacity(vm.heap.array_length(indices) as usize);
        let mut textured_vertices = Vec::with_capacity(vertices.capacity());
        let mut colors = (!textured).then(|| Vec::with_capacity(vertices.capacity()));
        let mut color_material_vertices =
            (!textured && lighting.is_some() && source_normals.is_some())
                .then(|| Vec::with_capacity(vertices.capacity()));
        for index_position in 0..vm.heap.array_length(indices) {
            let vertex_index = vm.heap.array_read_i32(indices, index_position);
            let Some(vertex_index) = usize::try_from(vertex_index).ok() else {
                vertices.clear();
                break;
            };
            let Some([x, y, z]) = source_vertices.get(vertex_index).copied() else {
                vertices.clear();
                break;
            };
            let Some(vertex) = project_vertex(
                layout,
                target,
                x.round() as i32,
                y.round() as i32,
                z.round() as i32,
            ) else {
                vertices.clear();
                break;
            };
            vertices.push(vertex);
            let data_offset = index_position as usize * 5;
            if textured {
                let (Some(u), Some(v)) = (
                    array_byte(vm, color_data, data_offset),
                    array_byte(vm, color_data, data_offset + 1),
                ) else {
                    vertices.clear();
                    textured_vertices.clear();
                    break;
                };
                let normal = source_normals
                    .and_then(|normals| normals.get(vertex_index).copied())
                    .unwrap_or([0.0; 3]);
                let material_lighting =
                    array_byte(vm, color_data, data_offset + 2).unwrap_or(0) as f32;
                let material_reflection =
                    array_byte(vm, color_data, data_offset + 3).unwrap_or(0) as f32;
                textured_vertices.push([
                    vertex[0],
                    vertex[1],
                    vertex[2],
                    vertex[3],
                    u as f32,
                    v as f32,
                    normal[0],
                    normal[1],
                    normal[2],
                    material_lighting,
                    (lighting.is_some()
                        && sphere_texture_handle > 0
                        && material_lighting > 0.0
                        && material_reflection > 0.0) as i32 as f32,
                ]);
            } else {
                let color = match (
                    array_byte(vm, color_data, data_offset),
                    array_byte(vm, color_data, data_offset + 1),
                    array_byte(vm, color_data, data_offset + 2),
                ) {
                    (Some(red), Some(green), Some(blue)) => Some(
                        0xff00_0000_u32 as i32
                            | (red as i32) << 16
                            | (green as i32) << 8
                            | blue as i32,
                    ),
                    _ => None,
                }
                .unwrap_or(0xffc8_d0d8_u32 as i32);
                if let Some(colors) = &mut colors {
                    colors.push(color);
                }
                if let Some(color_material_vertices) = &mut color_material_vertices {
                    let normal = source_normals
                        .and_then(|normals| normals.get(vertex_index).copied())
                        .unwrap_or([0.0; 3]);
                    let material_lighting = source_normals.is_some()
                        && array_byte(vm, color_data, data_offset + 3).unwrap_or(0) != 0;
                    let material_reflection = material_lighting
                        && sphere_texture_handle > 0
                        && array_byte(vm, color_data, data_offset + 4).unwrap_or(0) != 0;
                    color_material_vertices.push([
                        vertex[0],
                        vertex[1],
                        vertex[2],
                        vertex[3],
                        normal[0],
                        normal[1],
                        normal[2],
                        ((color as u32 >> 16) & 0xff) as f32 / 255.0,
                        ((color as u32 >> 8) & 0xff) as f32 / 255.0,
                        (color as u32 & 0xff) as f32 / 255.0,
                        material_lighting as i32 as f32,
                        material_reflection as i32 as f32,
                    ]);
                }
            }
        }
        if textured {
            let mut clipped_vertices = Vec::with_capacity(textured_vertices.len());
            for triangle in textured_vertices.chunks_exact(3) {
                append_clipped_triangle_to_webgpu(triangle, &mut clipped_vertices);
            }
            if clipped_vertices.is_empty() {
                continue;
            }
            let face = field_i32(vm, polygon, CLASS_MODEL_POLYGON, "face");
            let cull_back_faces = field_i32(vm, polygon, CLASS_MODEL_POLYGON, "doubleFace") == 0;
            let texture = textures
                .get(usize::try_from(face.max(0)).unwrap_or(0))
                .copied()
                .unwrap_or_default();
            if texture.handle <= 0 {
                continue;
            }
            let color_key_enabled = array_byte(vm, color_data, 4).unwrap_or(0) != 0;
            if let Some((_, _, _, _, batch)) = textured_batches.iter_mut().find(
                |(candidate_texture, candidate_cull, candidate_blend, candidate_color_key, _)| {
                    candidate_texture.handle == texture.handle
                        && *candidate_cull == cull_back_faces
                        && *candidate_blend == blend_mode
                        && *candidate_color_key == color_key_enabled
                },
            ) {
                batch.extend_from_slice(&clipped_vertices);
            } else {
                textured_batches.push((
                    texture,
                    cull_back_faces,
                    blend_mode,
                    color_key_enabled,
                    clipped_vertices,
                ));
            }
            continue;
        }
        let Some(colors) = colors else {
            continue;
        };
        if let Some(color_material_vertices) = color_material_vertices {
            let mut clipped_vertices = Vec::with_capacity(color_material_vertices.len());
            for triangle in color_material_vertices.chunks_exact(3) {
                append_clipped_triangle_to_webgpu(triangle, &mut clipped_vertices);
            }
            emit_color_material_packet(
                vm,
                target,
                layout.affine,
                lighting,
                sphere_texture_handle,
                blend_mode,
                &clipped_vertices,
            );
            continue;
        }
        for (triangle, triangle_colors) in vertices.chunks_exact(3).zip(colors.chunks_exact(3)) {
            let mut clipped_vertices = Vec::with_capacity(6);
            append_clipped_triangle_to_webgpu(triangle, &mut clipped_vertices);
            emit_primitive_packet(
                vm,
                target,
                PRIMITIVE_TRIANGLES,
                &clipped_vertices,
                average_colors(triangle_colors),
                blend_mode,
            );
        }
    }
    for (texture, cull_back_faces, blend_mode, color_key_enabled, vertices) in textured_batches {
        emit_textured_packet(
            vm,
            target,
            texture.handle,
            cull_back_faces,
            layout.affine,
            lighting,
            texture.color_key,
            color_key_enabled,
            blend_mode,
            sphere_texture_handle,
            &vertices,
        );
    }
}

fn append_figure_packets(
    vm: &mut SharedVm,
    target: MascotTarget,
    layout: Layout,
    model: u32,
    current_pattern: i32,
    source_vertices: &[[f32; 3]],
    normals: Option<&[[f32; 3]]>,
    lighting: Option<Lighting>,
    sphere_texture_handle: i32,
    textures: &[TextureBinding],
    blend_pass: Option<u32>,
    force_opaque: bool,
) {
    render_model_polygons(
        vm,
        target,
        layout,
        field_ref(vm, model, CLASS_MODEL, "polygonsT"),
        current_pattern,
        source_vertices,
        normals,
        lighting,
        sphere_texture_handle,
        textures,
        true,
        blend_pass,
        force_opaque,
    );
    render_model_polygons(
        vm,
        target,
        layout,
        field_ref(vm, model, CLASS_MODEL, "polygonsC"),
        current_pattern,
        source_vertices,
        normals,
        lighting,
        sphere_texture_handle,
        &[],
        false,
        blend_pass,
        force_opaque,
    );
}

fn capture_figure_draw(
    vm: &mut SharedVm,
    renderer: u32,
    figure: u32,
    x: i32,
    y: i32,
    layout_ref: u32,
    effect: u32,
    textures: Vec<TextureBinding>,
) -> Option<MascotFigureDraw> {
    let target = renderer_target(vm, renderer)?;
    let model = field_ref(vm, figure, CLASS_FIGURE, "data");
    if model == NULL {
        return None;
    }
    let original_vertices = field_ref(vm, model, CLASS_MODEL, "originalVertices");
    let vertices = field_ref(vm, model, CLASS_MODEL, "vertices");
    if original_vertices == NULL || vertices == NULL {
        return None;
    }
    let source_vertices = read_float_buffer_vertices(vm, vertices);
    if source_vertices.is_empty() {
        return None;
    }
    let lighting = read_lighting(vm, effect);
    let sphere_texture_handle = read_sphere_texture(vm, effect);
    let normals = if lighting.is_some() {
        let normals = field_ref(vm, model, CLASS_MODEL, "normals");
        let normals = read_float_buffer_vertices(vm, normals);
        (normals.len() == source_vertices.len()).then_some(normals)
    } else {
        None
    };

    let mut layout = read_layout(vm, layout_ref);
    layout.center_x = layout.center_x.saturating_add(x);
    layout.center_y = layout.center_y.saturating_add(y);
    let current_pattern = field_i32(vm, figure, CLASS_FIGURE, FIELD_FIGURE_CURRENT_PATTERN);
    let pending = vm.m3g_pending_command_buffer.take();
    let transparency_enabled = field_i32(vm, effect, CLASS_EFFECT_3D, "isTransparency") != 0;

    let mut opaque_packets = Vec::new();
    vm.m3g_pending_command_buffer = Some(Vec::new());
    append_figure_packets(
        vm,
        target,
        layout,
        model,
        current_pattern,
        &source_vertices,
        normals.as_deref(),
        lighting,
        sphere_texture_handle,
        &textures,
        Some(0),
        false,
    );
    if !transparency_enabled {
        for blend_mode in 1..=3 {
            append_figure_packets(
                vm,
                target,
                layout,
                model,
                current_pattern,
                &source_vertices,
                normals.as_deref(),
                lighting,
                sphere_texture_handle,
                &textures,
                Some(blend_mode),
                true,
            );
        }
    }
    if let Some(packets) = vm.m3g_pending_command_buffer.take() {
        opaque_packets = packets;
    }

    let mut transparent_packets = Vec::new();
    if transparency_enabled {
        vm.m3g_pending_command_buffer = Some(Vec::new());
        for blend_mode in 1..=3 {
            append_figure_packets(
                vm,
                target,
                layout,
                model,
                current_pattern,
                &source_vertices,
                normals.as_deref(),
                lighting,
                sphere_texture_handle,
                &textures,
                Some(blend_mode),
                false,
            );
        }
        if let Some(packets) = vm.m3g_pending_command_buffer.take() {
            transparent_packets = packets;
        }
    }

    vm.m3g_pending_command_buffer = pending;
    let mut texture_handles = textures
        .into_iter()
        .map(|texture| texture.handle)
        .filter(|handle| *handle > 0 && vm.mascot_textures.contains_key(handle))
        .collect::<Vec<_>>();
    if sphere_texture_handle > 0 && vm.mascot_textures.contains_key(&sphere_texture_handle) {
        texture_handles.push(sphere_texture_handle);
    }
    texture_handles.sort_unstable();
    texture_handles.dedup();
    Some(MascotFigureDraw {
        opaque_packets,
        transparent_packets,
        texture_handles,
    })
}

fn render_figure(
    ctx: &mut NativeContext<'_>,
    renderer: u32,
    figure: u32,
    x: i32,
    y: i32,
    layout_ref: u32,
    effect: u32,
) {
    let textures = texture_bindings(
        ctx.vm,
        field_ref(
            ctx.vm,
            renderer,
            CLASS_GRAPHICS_3D,
            FIELD_GRAPHICS_ACTIVE_TEXTURES,
        ),
    );
    if let Some(draw) =
        capture_figure_draw(ctx.vm, renderer, figure, x, y, layout_ref, effect, textures)
    {
        ctx.vm.mascot_pending_figures.push(draw);
    }
}

fn draw_figure(
    ctx: &mut NativeContext<'_>,
    renderer: u32,
    figure: u32,
    x: i32,
    y: i32,
    layout_ref: u32,
    effect: u32,
) {
    let mut draws = std::mem::take(&mut ctx.vm.mascot_pending_figures);
    let textures = selected_figure_texture_bindings(ctx.vm, figure);
    let Some(draw) =
        capture_figure_draw(ctx.vm, renderer, figure, x, y, layout_ref, effect, textures)
    else {
        replay_figure_draws(ctx.vm, &draws);
        ctx.vm
            .m3g_command_buffer_mut()
            .push(cmd::MASCOT_CLEAR_DEPTH);
        return;
    };
    draws.push(draw);
    replay_figure_draws(ctx.vm, &draws);
    ctx.vm
        .m3g_command_buffer_mut()
        .push(cmd::MASCOT_CLEAR_DEPTH);
}

fn replay_pending_figures(vm: &mut SharedVm) {
    let draws = std::mem::take(&mut vm.mascot_pending_figures);
    replay_figure_draws(vm, &draws);
}

fn replay_figure_draws(vm: &mut SharedVm, draws: &[MascotFigureDraw]) {
    if draws.is_empty() {
        return;
    }
    for texture_handle in collect_figure_texture_handles(draws) {
        ensure_mascot_texture_uploaded(vm, texture_handle);
    }
    append_figure_passes(vm.m3g_command_buffer_mut(), &draws);
}

fn collect_figure_texture_handles(draws: &[MascotFigureDraw]) -> Vec<i32> {
    let mut texture_handles = draws
        .iter()
        .flat_map(|draw| draw.texture_handles.iter().copied())
        .filter(|handle| *handle > 0)
        .collect::<Vec<_>>();
    texture_handles.sort_unstable();
    texture_handles.dedup();
    texture_handles
}

fn append_figure_passes(buffer: &mut Vec<u8>, draws: &[MascotFigureDraw]) {
    for draw in draws {
        buffer.extend_from_slice(&draw.opaque_packets);
    }
    for draw in draws {
        buffer.extend_from_slice(&draw.transparent_packets);
    }
}

fn bind_renderer(ctx: &mut NativeContext<'_>, renderer: u32, graphics: u32) {
    if renderer == NULL || graphics == NULL {
        return;
    }
    write_renderer_graphics(ctx.vm, renderer, graphics);
    if let Some(target) = renderer_target(ctx.vm, renderer) {
        ctx.vm.begin_m3g_target_batch();
        emit_bind(ctx.vm, target);
    }
}

fn release_renderer(ctx: &mut NativeContext<'_>, renderer: u32) {
    if renderer == NULL {
        return;
    }
    ctx.vm.mascot_pending_figures.clear();
    ctx.vm
        .m3g_command_buffer_mut()
        .push(cmd::MASCOT_RELEASE_TARGET);
    write_renderer_graphics(ctx.vm, renderer, NULL);
    ctx.vm.flush_m3g_target_batch();
}

fn flush_renderer(ctx: &mut NativeContext<'_>, _renderer: u32) {
    replay_pending_figures(ctx.vm);
    ctx.vm.m3g_command_buffer_mut().push(cmd::MASCOT_FLUSH);
}

fn load_resource(ctx: &mut NativeContext<'_>, name: u32) -> u32 {
    let name = ctx.vm.get_string_value(name).unwrap_or_default();
    let lookup = name.strip_prefix('/').unwrap_or(&name);
    let Some(data) = ctx.vm.get_resource(lookup) else {
        return NULL;
    };
    let class_id = ctx.vm.class_repo.get_class_id("[B").unwrap_or(0);
    let bytes = ctx.vm.new_array(class_id, data.len() as u32, 1);
    for (index, byte) in data.iter().enumerate() {
        ctx.vm.heap.array_write_i8(bytes, index as u32, *byte as i8);
    }
    bytes
}

fn log_error(_ctx: &mut NativeContext<'_>, _message: u32) {}

fn graphics_width(ctx: &mut NativeContext<'_>, _graphics: u32) -> i32 {
    ctx.vm.display_width
}
fn graphics_height(ctx: &mut NativeContext<'_>, _graphics: u32) -> i32 {
    ctx.vm.display_height
}
fn graphics_clip(ctx: &mut NativeContext<'_>, _graphics: u32) -> u32 {
    let class_id = ctx.vm.class_repo.get_class_id("[I").unwrap_or(0);
    ctx.vm.heap.alloc_array_typed(class_id, 4, 4, true)
}
fn upload_graphics_texture(_ctx: &mut NativeContext<'_>, _graphics: u32, _texture: i32) {}
fn clear_graphics(_ctx: &mut NativeContext<'_>, _graphics: u32) {}
fn read_graphics_pixels(
    _ctx: &mut NativeContext<'_>,
    _x: i32,
    _y: i32,
    _width: i32,
    _height: i32,
    _graphics: u32,
) {
}
fn decode_texture(ctx: &mut NativeContext<'_>, data: u32) -> i32 {
    let Some(image) = decode_bmp_rgba(ctx.vm, data) else {
        return 0;
    };
    let handle = ctx.vm.next_mascot_texture_handle;
    let Some(next_handle) = handle.checked_add(1) else {
        ctx.vm.pending_exception_str = Some("java.lang.OutOfMemoryError".to_string());
        return 0;
    };
    ctx.vm.next_mascot_texture_handle = next_handle;
    ctx.vm.mascot_textures.insert(handle, image);
    handle
}
fn texture_width(ctx: &mut NativeContext<'_>, image: i32) -> i32 {
    ctx.vm
        .mascot_textures
        .get(&image)
        .map(|texture| texture.width as i32)
        .unwrap_or(0)
}
fn texture_height(ctx: &mut NativeContext<'_>, image: i32) -> i32 {
    ctx.vm
        .mascot_textures
        .get(&image)
        .map(|texture| texture.height as i32)
        .unwrap_or(0)
}
fn upload_texture(_ctx: &mut NativeContext<'_>, _image: i32, _texture: i32) {}
fn register_texture(ctx: &mut NativeContext<'_>, texture: u32, image: i32) {
    if texture != NULL && image > 0 && ctx.vm.mascot_textures.contains_key(&image) {
        ctx.vm.mascot_texture_owners.insert(texture, image);
    }
}
fn release_texture(ctx: &mut NativeContext<'_>, texture: u32, image: i32) {
    if texture != NULL {
        ctx.vm.mascot_texture_owners.remove(&texture);
    }
    if image <= 0
        || ctx
            .vm
            .mascot_texture_owners
            .values()
            .any(|&handle| handle == image)
    {
        return;
    }
    ctx.vm.mascot_textures.remove(&image);
    ctx.vm.mascot_uploaded_textures.remove(&image);
    ctx.vm
        .m3g_command_buffer_mut()
        .push(cmd::MASCOT_DELETE_TEXTURE);
    ctx.vm
        .m3g_command_buffer_mut()
        .extend_from_slice(&image.to_le_bytes());
}
fn load_asset(ctx: &mut NativeContext<'_>, _name: u32) -> u32 {
    ctx.vm.create_string("")
}
fn get_boolean_property(_ctx: &mut NativeContext<'_>, _name: u32) -> bool {
    false
}
fn fill_buffer(ctx: &mut NativeContext<'_>, buffer: u32, vertices: u32, indices: u32) {
    if buffer == NULL || vertices == NULL || indices == NULL {
        ctx.vm.pending_exception_str = Some("java.lang.NullPointerException".to_string());
        return;
    }
    let index_count = ctx.vm.heap.array_length(indices) as usize;
    let Some((source_address, source_float_count)) = direct_float_range(ctx.vm, vertices) else {
        ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
        return;
    };
    let Some((destination_address, destination_float_count)) = direct_float_range(ctx.vm, buffer)
    else {
        ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
        return;
    };
    if destination_float_count < index_count * 3 {
        ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
        return;
    }
    let source_byte_count = source_float_count * std::mem::size_of::<f32>();
    let Some(source_bytes) = ctx
        .vm
        .direct_buffer_arena
        .bytes(source_address, source_byte_count)
    else {
        ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
        return;
    };
    let mut output = Vec::with_capacity(index_count * 3 * std::mem::size_of::<f32>());
    for output_index in 0..index_count {
        let vertex_index = ctx.vm.heap.array_read_i32(indices, output_index as u32);
        let Some(vertex_index) = usize::try_from(vertex_index).ok() else {
            ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
            return;
        };
        let Some(x) = float_at(source_bytes, vertex_index * 3) else {
            ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
            return;
        };
        let Some(y) = float_at(source_bytes, vertex_index * 3 + 1) else {
            ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
            return;
        };
        let Some(z) = float_at(source_bytes, vertex_index * 3 + 2) else {
            ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
            return;
        };
        push_f32(&mut output, x);
        push_f32(&mut output, y);
        push_f32(&mut output, z);
    }
    if !ctx
        .vm
        .direct_buffer_arena
        .copy_from_slice(destination_address, &output)
    {
        ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
    }
}
fn gl_read_pixels(
    _ctx: &mut NativeContext<'_>,
    _x: i32,
    _y: i32,
    _width: i32,
    _height: i32,
    _graphics: u32,
) {
}
fn transform(
    ctx: &mut NativeContext<'_>,
    source_vertices: u32,
    destination_vertices: u32,
    source_normals: u32,
    destination_normals: u32,
    bones: u32,
    action_matrices: u32,
) {
    if !transform_buffers(
        ctx.vm,
        source_vertices,
        destination_vertices,
        source_normals,
        destination_normals,
        bones,
        action_matrices,
    ) {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
    }
}

// GLES/WebGPU placeholders.  Every Java declaration is intentionally
// registered even though the first integration only needs no-op behaviour.
fn gl_void0(_ctx: &mut NativeContext<'_>) {}
fn gl_i0(_ctx: &mut NativeContext<'_>) -> i32 {
    // Used by glGetError and glCreateProgram.  Returning GL_NO_ERROR keeps
    // the Java command-list path usable before a real renderer is attached.
    0
}
fn gl_i1(_ctx: &mut NativeContext<'_>, _a: i32) -> i32 {
    NEXT_MOCK_GL_ID.fetch_add(1, Ordering::Relaxed)
}
fn gl_bool1(_ctx: &mut NativeContext<'_>, _a: i32) -> bool {
    _a > 0
}
fn gl_bool_void(_ctx: &mut NativeContext<'_>, _a: bool) {}
fn gl_void1(_ctx: &mut NativeContext<'_>, _a: i32) {}
fn gl_void2(_ctx: &mut NativeContext<'_>, _a: i32, _b: i32) {}
fn gl_void3(_ctx: &mut NativeContext<'_>, _a: i32, _b: i32, _c: i32) {}
fn gl_void4(_ctx: &mut NativeContext<'_>, _a: i32, _b: i32, _c: i32, _d: i32) {}
fn gl_f1(_ctx: &mut NativeContext<'_>, _a: i32, _b: f32) {}
fn gl_f2(_ctx: &mut NativeContext<'_>, _a: i32, _b: f32, _c: f32) {}
fn gl_f3(_ctx: &mut NativeContext<'_>, _a: i32, _b: f32, _c: f32, _d: f32) {}
fn gl_f4(_ctx: &mut NativeContext<'_>, _a: f32, _b: f32, _c: f32, _d: f32) {}
fn gl_string(_ctx: &mut NativeContext<'_>, _a: i32) -> u32 {
    NULL
}
fn gl_i_string(_ctx: &mut NativeContext<'_>, _a: i32, _b: u32) -> i32 {
    0
}
fn gl_shader_source(_ctx: &mut NativeContext<'_>, _shader: i32, _source: u32) {}
fn gl_array_i(ctx: &mut NativeContext<'_>, count: i32, values: u32, offset: i32) {
    if values == NULL {
        return;
    }
    for index in 0..count.max(0) {
        ctx.vm.heap.array_write_i32(
            values,
            (offset + index) as u32,
            NEXT_MOCK_GL_ID.fetch_add(1, Ordering::Relaxed),
        );
    }
}
fn gl_i_i_array_i(_ctx: &mut NativeContext<'_>, _a: i32, _b: i32, _c: u32, _d: i32) {}
fn gl_i_i_bool_i_i_obj(
    _ctx: &mut NativeContext<'_>,
    _a: i32,
    _b: i32,
    _c: i32,
    _d: bool,
    _e: i32,
    _f: u32,
) {
}
fn gl_i_i_bool_i_i(
    _ctx: &mut NativeContext<'_>,
    _a: i32,
    _b: i32,
    _c: i32,
    _d: bool,
    _e: i32,
    _f: i32,
) {
}
fn gl_i_i_obj_i(_ctx: &mut NativeContext<'_>, _a: i32, _b: i32, _c: u32, _d: i32) {}
fn gl_i_i_f_array_i(_ctx: &mut NativeContext<'_>, _a: i32, _b: i32, _c: bool, _d: u32, _e: i32) {}

pub fn register(registry: &mut NativeRegistry) {
    registry.register_bindings(&[
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.loadResource(Ljava/lang/String;)[B" => load_resource(name: u32) -> u32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.logError(Ljava/lang/String;)V" => log_error(message: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.graphicsWidth(Ljavax/microedition/lcdui/Graphics;)I" => graphics_width(graphics: u32) -> i32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.graphicsHeight(Ljavax/microedition/lcdui/Graphics;)I" => graphics_height(graphics: u32) -> i32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.graphicsClip(Ljavax/microedition/lcdui/Graphics;)[I" => graphics_clip(graphics: u32) -> u32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.uploadGraphicsTexture(Ljavax/microedition/lcdui/Graphics;I)V" => upload_graphics_texture(graphics: u32, texture: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.clearGraphics(Ljavax/microedition/lcdui/Graphics;)V" => clear_graphics(graphics: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.readGraphicsPixels(IIIILjavax/microedition/lcdui/Graphics;)V" => read_graphics_pixels(x: i32, y: i32, width: i32, height: i32, graphics: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.decodeTexture([B)I" => decode_texture(data: u32) -> i32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.textureWidth(I)I" => texture_width(image: i32) -> i32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.textureHeight(I)I" => texture_height(image: i32) -> i32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.uploadTexture(II)V" => upload_texture(image: i32, texture: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.registerTexture(Lcom/mascotcapsule/micro3d/v3/Texture;I)V" => register_texture(texture: u32, image: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.releaseTexture(Lcom/mascotcapsule/micro3d/v3/Texture;I)V" => release_texture(texture: u32, image: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.loadAsset(Ljava/lang/String;)Ljava/lang/String;" => load_asset(name: u32) -> u32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.getBooleanProperty(Ljava/lang/String;)Z" => get_boolean_property(name: u32) -> bool),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.bindRenderer(Lcom/mascotcapsule/micro3d/v3/Graphics3D;Ljavax/microedition/lcdui/Graphics;)V" => bind_renderer(renderer: u32, graphics: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.releaseRenderer(Lcom/mascotcapsule/micro3d/v3/Graphics3D;)V" => release_renderer(renderer: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.flushRenderer(Lcom/mascotcapsule/micro3d/v3/Graphics3D;)V" => flush_renderer(renderer: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.drawCommandList(Lcom/mascotcapsule/micro3d/v3/Graphics3D;[Lcom/mascotcapsule/micro3d/v3/Texture;IILcom/mascotcapsule/micro3d/v3/FigureLayout;Lcom/mascotcapsule/micro3d/v3/Effect3D;[I)V" => draw_command_list(renderer: u32, textures: u32, x: i32, y: i32, layout: u32, effect: u32, command_list: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.renderPrimitives(Lcom/mascotcapsule/micro3d/v3/Graphics3D;Lcom/mascotcapsule/micro3d/v3/Texture;IILcom/mascotcapsule/micro3d/v3/FigureLayout;Lcom/mascotcapsule/micro3d/v3/Effect3D;II[I[I[I[I)V" => render_primitives(renderer: u32, texture: u32, x: i32, y: i32, layout: u32, effect: u32, command: i32, primitive_count: i32, vertex_coords: u32, normals: u32, texture_coords: u32, colors: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.renderFigure(Lcom/mascotcapsule/micro3d/v3/Graphics3D;Lcom/mascotcapsule/micro3d/v3/Figure;IILcom/mascotcapsule/micro3d/v3/FigureLayout;Lcom/mascotcapsule/micro3d/v3/Effect3D;)V" => render_figure(renderer: u32, figure: u32, x: i32, y: i32, layout: u32, effect: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotNative.drawFigure(Lcom/mascotcapsule/micro3d/v3/Graphics3D;Lcom/mascotcapsule/micro3d/v3/Figure;IILcom/mascotcapsule/micro3d/v3/FigureLayout;Lcom/mascotcapsule/micro3d/v3/Effect3D;)V" => draw_figure(renderer: u32, figure: u32, x: i32, y: i32, layout: u32, effect: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.Utils.fillBuffer(Ljava/nio/FloatBuffer;Ljava/nio/FloatBuffer;[I)V" => fill_buffer(buffer: u32, vertices: u32, indices: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.Utils.glReadPixels(IIIILjavax/microedition/lcdui/Graphics;)V" => gl_read_pixels(x: i32, y: i32, width: i32, height: i32, graphics: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.Utils.transform(Ljava/nio/FloatBuffer;Ljava/nio/FloatBuffer;Ljava/nio/FloatBuffer;Ljava/nio/FloatBuffer;Ljava/nio/ByteBuffer;[F)V" => transform(src: u32, dst: u32, src_normals: u32, dst_normals: u32, bones: u32, matrices: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glGetError()I" => gl_i0() -> i32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glIsTexture(I)Z" => gl_bool1(texture: i32) -> bool),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glGenTextures(I[II)V" => gl_array_i(n: i32, values: u32, offset: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glDeleteTextures(I[II)V" => gl_array_i(n: i32, values: u32, offset: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glActiveTexture(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glBindTexture(II)V" => gl_void2(a: i32, b: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glTexParameteri(III)V" => gl_void3(a: i32, b: i32, c: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glViewport(IIII)V" => gl_void4(a: i32, b: i32, c: i32, d: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glClearColor(FFFF)V" => gl_f4(a: f32, b: f32, c: f32, d: f32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glClear(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glEnable(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glDisable(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glScissor(IIII)V" => gl_void4(a: i32, b: i32, c: i32, d: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glDepthMask(Z)V" => gl_bool_void(value: bool) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glDepthFunc(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glBlendColor(FFFF)V" => gl_f4(a: f32, b: f32, c: f32, d: f32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glBlendEquation(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glBlendFunc(II)V" => gl_void2(a: i32, b: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glBlendFuncSeparate(IIII)V" => gl_void4(a: i32, b: i32, c: i32, d: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glFlush()V" => gl_void0() -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glGenBuffers(I[II)V" => gl_array_i(n: i32, values: u32, offset: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glDeleteBuffers(I[II)V" => gl_array_i(n: i32, values: u32, offset: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glBindBuffer(II)V" => gl_void2(a: i32, b: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glBufferData(IILjava/lang/Object;I)V" => gl_i_i_obj_i(a: i32, b: i32, c: u32, d: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glVertexAttribPointer(IIIZILjava/lang/Object;)V" => gl_i_i_bool_i_i_obj(a: i32, b: i32, c: i32, d: bool, e: i32, f: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glVertexAttribPointer(IIIZII)V" => gl_i_i_bool_i_i(a: i32, b: i32, c: i32, d: bool, e: i32, f: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glEnableVertexAttribArray(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glDisableVertexAttribArray(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glDrawArrays(III)V" => gl_void3(a: i32, b: i32, c: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glCreateShader(I)I" => gl_i1(value: i32) -> i32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glShaderSource(ILjava/lang/String;)V" => gl_shader_source(shader: i32, source: u32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glCompileShader(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glGetShaderiv(II[II)V" => gl_i_i_array_i(a: i32, b: i32, c: u32, d: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glGetShaderInfoLog(I)Ljava/lang/String;" => gl_string(value: i32) -> u32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glCreateProgram()I" => gl_i0() -> i32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glAttachShader(II)V" => gl_void2(a: i32, b: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glLinkProgram(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glGetProgramiv(II[II)V" => gl_i_i_array_i(a: i32, b: i32, c: u32, d: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glGetProgramInfoLog(I)Ljava/lang/String;" => gl_string(value: i32) -> u32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glGetUniformLocation(ILjava/lang/String;)I" => gl_i_string(a: i32, b: u32) -> i32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glGetAttribLocation(ILjava/lang/String;)I" => gl_i_string(a: i32, b: u32) -> i32),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glUseProgram(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glDetachShader(II)V" => gl_void2(a: i32, b: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glDeleteShader(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glDeleteProgram(I)V" => gl_void1(value: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glReleaseShaderCompiler()V" => gl_void0() -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glUniform1i(II)V" => gl_void2(a: i32, b: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glUniform1f(IF)V" => gl_f1(a: i32, b: f32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glUniform2f(IFF)V" => gl_f2(a: i32, b: f32, c: f32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glUniform3f(IFFF)V" => gl_f3(a: i32, b: f32, c: f32, d: f32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glUniform3fv(II[FI)V" => gl_i_i_array_i(a: i32, b: i32, c: u32, d: i32) -> ()),
        crate::native_bind!(static "com.mascotcapsule.micro3d.v3.MascotGl.glUniformMatrix4fv(IIZ[FI)V" => gl_i_i_f_array_i(a: i32, b: i32, c: bool, d: u32, e: i32) -> ()),
    ]);
}

#[cfg(test)]
mod tests {
    use super::{
        append_figure_passes, collect_figure_texture_handles, expand_lines_to_triangles,
        project_vertex, Affine, Layout, MascotTarget, COMMAND_PERSPECTIVE_FOV,
    };
    use crate::vm::MascotFigureDraw;

    fn target() -> MascotTarget {
        MascotTarget {
            surface_id: 0,
            width: 240,
            height: 320,
            clip_x: 0,
            clip_y: 0,
            clip_width: 240,
            clip_height: 320,
        }
    }

    #[test]
    fn perspective_projection_preserves_clip_coordinates_for_gpu_clipping() {
        let layout = Layout {
            affine: Affine::default(),
            near: 100,
            far: 1_000,
            angle: 512,
            setting: COMMAND_PERSPECTIVE_FOV,
            ..Layout::default()
        };

        let before_near = project_vertex(layout, target(), 0, 0, 50).unwrap();
        let near = project_vertex(layout, target(), 0, 0, 100).unwrap();
        let far = project_vertex(layout, target(), 0, 0, 1_000).unwrap();
        let after_far = project_vertex(layout, target(), 0, 0, 1_200).unwrap();

        assert!(before_near[2] < 0.0);
        assert!(near[2].abs() < 1e-5);
        assert!((far[2] - far[3]).abs() < 1e-5);
        assert!(after_far[2] > after_far[3]);
    }

    #[test]
    fn triangle_crossing_camera_plane_is_clipped_before_packet_emission() {
        let triangle = [
            [-0.5, -0.5, 0.5, 1.0, 0.0],
            [0.5, -0.5, 0.5, 1.0, 1.0],
            [0.0, 0.5, -0.5, -1.0, 0.5],
        ];

        let mut clipped = Vec::new();
        super::append_clipped_triangle_to_webgpu(&triangle, &mut clipped);

        assert!(!clipped.is_empty());
        assert_eq!(clipped.len() % 3, 0);
        for vertex in clipped {
            let position = [vertex[0], vertex[1], vertex[2], vertex[3]];
            assert!(position[3] >= 0.00009);
            assert!(position[0].abs() <= position[3] + 0.00001);
            assert!(position[1].abs() <= position[3] + 0.00001);
            assert!(position[2] >= -0.00001);
            assert!(position[2] <= position[3] + 0.00001);
        }
    }

    #[test]
    fn triangle_behind_camera_is_not_emitted() {
        let triangle = [
            [-0.5, -0.5, -0.5, -1.0],
            [0.5, -0.5, -0.5, -1.0],
            [0.0, 0.5, -0.5, -1.0],
        ];

        let mut clipped = Vec::new();
        super::append_clipped_triangle_to_webgpu(&triangle, &mut clipped);
        assert!(clipped.is_empty());
    }

    #[test]
    fn figure_flush_replays_all_opaque_before_any_transparent_packets() {
        let draws = [
            MascotFigureDraw {
                opaque_packets: vec![1],
                transparent_packets: vec![2],
                texture_handles: vec![4, 8],
            },
            MascotFigureDraw {
                opaque_packets: vec![3],
                transparent_packets: vec![4],
                texture_handles: vec![4, 12],
            },
        ];
        let mut packets = Vec::new();

        append_figure_passes(&mut packets, &draws);

        assert_eq!(packets, vec![1, 3, 2, 4]);
        assert_eq!(collect_figure_texture_handles(&draws), vec![4, 8, 12]);
    }

    #[test]
    fn figure_dynamic_pattern_only_emits_enabled_polygons() {
        assert!(super::is_polygon_active(0, 0));
        assert!(super::is_polygon_active(0b0101, 0b1101));
        assert!(!super::is_polygon_active(0b0101, 0b1001));
    }

    #[test]
    fn line_list_expands_to_one_pixel_triangle_quad() {
        let line = [[-0.5, 0.0, 0.25, 1.0], [0.5, 0.0, 0.75, 1.0]];
        let triangles = expand_lines_to_triangles(&line, 100, 50);

        assert_eq!(triangles.len(), 6);
        assert_eq!(triangles[0], [-0.5, 0.02, 0.25, 1.0]);
        assert_eq!(triangles[1], [-0.5, -0.02, 0.25, 1.0]);
        assert_eq!(triangles[2], [0.5, 0.02, 0.75, 1.0]);
        assert_eq!(triangles[3], triangles[2]);
        assert_eq!(triangles[4], triangles[1]);
        assert_eq!(triangles[5], [0.5, -0.02, 0.75, 1.0]);
    }

    #[test]
    fn degenerate_lines_are_not_emitted() {
        let points = [[0.0, 0.0, 0.0, 1.0], [0.0, 0.0, 1.0, 1.0]];
        assert!(expand_lines_to_triangles(&points, 240, 320).is_empty());
    }
}
