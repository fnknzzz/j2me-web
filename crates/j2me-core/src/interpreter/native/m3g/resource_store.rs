use std::collections::HashMap;

#[derive(Debug, Clone)]
pub struct VertexArrayData {
    pub num_vertices: i32,
    pub num_components: i32,
    pub component_size: i32,
    pub data: Vec<u8>,
}

#[derive(Debug, Clone, Default)]
pub struct VertexBufferData {
    pub positions: Option<u32>,
    pub normals: Option<u32>,
    pub colors: Option<u32>,
    pub texcoords: [Option<u32>; 4],
    pub scale_bias: [f32; 6],
    pub texcoord_scale_bias: [[f32; 4]; 4],
}

#[derive(Debug, Clone)]
pub struct IndexBufferData {
    pub strip_lengths: Vec<i32>,
    pub indices: Option<Vec<u8>>,
    pub is_implicit: bool,
    pub first_index: i32,
}

#[derive(Debug, Clone, Default)]
pub struct AppearanceData {
    pub compositing_mode: u32,
    pub polygon_mode: u32,
    pub material: u32,
    pub textures: [u32; 4],
}

#[derive(Debug, Clone)]
pub struct MaterialData {
    pub ambient: i32,
    pub diffuse: i32,
    pub emissive: i32,
    pub specular: i32,
    pub shininess: f32,
    pub vertex_color_tracking: bool,
}

#[derive(Debug, Clone)]
pub struct Image2DData {
    pub format: i32,
    pub width: i32,
    pub height: i32,
}

#[derive(Debug, Clone)]
pub struct Texture2DData {
    pub image: u32,
    pub level_filter: i32,
    pub image_filter: i32,
    pub wrap_s: i32,
    pub wrap_t: i32,
    pub blending: i32,
    pub blend_color: i32,
}

impl Default for MaterialData {
    fn default() -> Self {
        Self {
            ambient: 0x00333333,
            diffuse: 0x00cccccc,
            emissive: 0,
            specular: 0,
            shininess: 0.0,
            vertex_color_tracking: false,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NodeKind {
    Group,
    Mesh,
    World,
    Camera,
    Light,
}

#[derive(Debug, Clone)]
pub struct TransformableData {
    pub translation: [f32; 3],
    pub scale: [f32; 3],
    pub orientation: [f32; 4], // angle, ax, ay, az
    pub transform: [f32; 16],  // additional free-form transform (column-major)
    pub has_transform: bool,
}

impl Default for TransformableData {
    fn default() -> Self {
        Self {
            translation: [0.0; 3],
            scale: [1.0, 1.0, 1.0],
            orientation: [0.0, 0.0, 0.0, 1.0], // 0 degrees around Z
            transform: identity_matrix(),
            has_transform: false,
        }
    }
}

#[derive(Debug, Clone)]
pub struct NodeData {
    pub kind: NodeKind,
    pub transformable: TransformableData,
    pub parent: Option<u32>,
    pub children: Vec<u32>,
    // Mesh-specific
    pub vertex_buffer: u32,
    pub index_buffers: Vec<u32>,
    pub appearances: Vec<u32>,
    // Alignment
    pub z_ref: u32,
    pub z_target: i32,
    pub y_ref: u32,
    pub y_target: i32,
    pub alignment_explicit: bool,
    pub rendering_enable: bool,
    pub scope: i32,
    pub active_camera: u32,
    pub background: u32,
    pub light_mode: i32,
    pub light_color: i32,
    pub light_intensity: f32,
}

impl NodeData {
    fn new(kind: NodeKind) -> Self {
        Self {
            kind,
            transformable: TransformableData::default(),
            parent: None,
            children: Vec::new(),
            vertex_buffer: 0,
            index_buffers: Vec::new(),
            appearances: Vec::new(),
            z_ref: 0,
            z_target: 144, // NONE
            y_ref: 0,
            y_target: 144,
            alignment_explicit: false,
            rendering_enable: true,
            scope: -1,
            active_camera: 0,
            background: 0,
            light_mode: 129, // DIRECTIONAL default
            light_color: 0x00ffffff,
            light_intensity: 1.0,
        }
    }

    pub fn new_group() -> Self {
        Self::new(NodeKind::Group)
    }

    pub fn new_world() -> Self {
        Self::new(NodeKind::World)
    }

    pub fn new_camera() -> Self {
        Self::new(NodeKind::Camera)
    }

    pub fn new_light() -> Self {
        Self::new(NodeKind::Light)
    }

    pub fn new_mesh(vb: u32, ibs: Vec<u32>, apps: Vec<u32>) -> Self {
        let mut node = Self::new(NodeKind::Mesh);
        node.vertex_buffer = vb;
        node.index_buffers = ibs;
        node.appearances = apps;
        node
    }
}

#[derive(Debug, Default)]
pub struct M3gResourceStore {
    pub vertex_arrays: HashMap<u32, VertexArrayData>,
    pub vertex_buffers: HashMap<u32, VertexBufferData>,
    pub index_buffers: HashMap<u32, IndexBufferData>,
    pub appearances: HashMap<u32, AppearanceData>,
    pub materials: HashMap<u32, MaterialData>,
    pub images: HashMap<u32, Image2DData>,
    pub render_target_images: HashMap<u32, u32>,
    pub pending_image_target_release: Option<(u32, u32)>,
    /// Stable M3G object handles retained for Loader external references.
    /// Native pointers remain private to the FFI boundary.
    pub external_resources: HashMap<String, Vec<u32>>,
    pub textures: HashMap<u32, Texture2DData>,
    pub nodes: HashMap<u32, NodeData>,
    pub pending_external_image: Option<PendingExternalImage>,
}

#[derive(Debug, Clone)]
pub struct PendingExternalImage {
    pub root_resource: String,
    pub external_resource: String,
}

impl M3gResourceStore {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn create_vertex_array(
        &mut self,
        handle: u32,
        num_vertices: i32,
        num_components: i32,
        component_size: i32,
    ) {
        let data_len = num_vertices
            .checked_mul(num_components)
            .and_then(|value| value.checked_mul(component_size))
            .and_then(|value| usize::try_from(value).ok())
            .unwrap_or(0);
        self.vertex_arrays.insert(
            handle,
            VertexArrayData {
                num_vertices,
                num_components,
                component_size,
                data: vec![0; data_len],
            },
        );
    }

    pub fn set_vertex_array_data(
        &mut self,
        handle: u32,
        first: i32,
        count: i32,
        data: &[u8],
        component_size: i32,
    ) -> bool {
        let Some(va) = self.vertex_arrays.get_mut(&handle) else {
            return false;
        };
        if first < 0
            || count < 0
            || va.num_vertices < 1
            || va.num_components < 1
            || component_size != va.component_size
            || first > va.num_vertices
            || count > va.num_vertices - first
        {
            return false;
        }

        let Some(byte_stride) = va.num_components.checked_mul(component_size) else {
            return false;
        };
        let Some(start) = first
            .checked_mul(byte_stride)
            .and_then(|value| usize::try_from(value).ok())
        else {
            return false;
        };
        let Some(len) = count
            .checked_mul(byte_stride)
            .and_then(|value| usize::try_from(value).ok())
        else {
            return false;
        };
        let Some(end) = start.checked_add(len) else {
            return false;
        };
        if data.len() < len {
            return false;
        }

        let Some(full_len) = va
            .num_vertices
            .checked_mul(byte_stride)
            .and_then(|value| usize::try_from(value).ok())
        else {
            return false;
        };
        if va.data.len() != full_len {
            va.data.resize(full_len, 0);
        }
        va.data[start..end].copy_from_slice(&data[..len]);
        true
    }

    pub fn create_vertex_buffer(&mut self, handle: u32) {
        self.vertex_buffers
            .insert(handle, VertexBufferData::default());
    }

    pub fn create_index_buffer_implicit(
        &mut self,
        handle: u32,
        first: i32,
        strip_lengths: Vec<i32>,
    ) {
        self.index_buffers.insert(
            handle,
            IndexBufferData {
                strip_lengths,
                indices: None,
                is_implicit: true,
                first_index: first,
            },
        );
    }

    pub fn create_index_buffer_explicit(
        &mut self,
        handle: u32,
        indices: Vec<u8>,
        strip_lengths: Vec<i32>,
    ) {
        self.index_buffers.insert(
            handle,
            IndexBufferData {
                strip_lengths,
                indices: Some(indices),
                is_implicit: false,
                first_index: 0,
            },
        );
    }

    pub fn create_appearance(&mut self, handle: u32) {
        self.appearances.insert(handle, AppearanceData::default());
    }

    pub fn create_material(&mut self, handle: u32) {
        self.materials.insert(handle, MaterialData::default());
    }

    pub fn create_group(&mut self, handle: u32) {
        self.nodes.insert(handle, NodeData::new_group());
    }

    pub fn create_mesh(&mut self, handle: u32, vb: u32, ibs: Vec<u32>, apps: Vec<u32>) {
        self.nodes.insert(handle, NodeData::new_mesh(vb, ibs, apps));
    }

    pub fn create_world(&mut self, handle: u32) {
        self.nodes.insert(handle, NodeData::new_world());
    }

    pub fn create_camera_node(&mut self, handle: u32) {
        self.nodes.insert(handle, NodeData::new_camera());
    }

    pub fn create_light(&mut self, handle: u32) {
        self.nodes.insert(handle, NodeData::new_light());
    }

    pub fn add_child(&mut self, parent_handle: u32, child_handle: u32) {
        if let Some(parent) = self.nodes.get_mut(&parent_handle) {
            if !parent.children.contains(&child_handle) {
                parent.children.push(child_handle);
            }
        }
        if let Some(child) = self.nodes.get_mut(&child_handle) {
            child.parent = Some(parent_handle);
        }
    }

    pub fn remove_child(&mut self, parent_handle: u32, child_handle: u32) {
        if let Some(parent) = self.nodes.get_mut(&parent_handle) {
            parent.children.retain(|&c| c != child_handle);
        }
        if let Some(child) = self.nodes.get_mut(&child_handle) {
            if child.parent == Some(parent_handle) {
                child.parent = None;
            }
        }
    }

    /// Compute the composite local transform for a node: T * R * S * M
    pub fn get_composite_transform(&self, handle: u32) -> [f32; 16] {
        let node = match self.nodes.get(&handle) {
            Some(n) => n,
            None => return identity_matrix(),
        };
        let t = &node.transformable;

        // Start with translation
        let mut result = identity_matrix();
        result[12] = t.translation[0];
        result[13] = t.translation[1];
        result[14] = t.translation[2];

        // Multiply by rotation (angle-axis -> matrix)
        if t.orientation[0] != 0.0 {
            let rot = angle_axis_to_matrix(
                t.orientation[0],
                t.orientation[1],
                t.orientation[2],
                t.orientation[3],
            );
            result = mat4_multiply(&result, &rot);
        }

        // Multiply by scale
        let mut scale_mat = identity_matrix();
        scale_mat[0] = t.scale[0];
        scale_mat[5] = t.scale[1];
        scale_mat[10] = t.scale[2];
        result = mat4_multiply(&result, &scale_mat);

        // Multiply by free-form transform if set
        if t.has_transform {
            result = mat4_multiply(&result, &t.transform);
        }

        result
    }

    /// Get world transform for a node (product of all ancestor composites)
    pub fn get_world_transform(&self, handle: u32) -> [f32; 16] {
        let mut path = Vec::new();
        let mut current = handle;
        loop {
            path.push(current);
            match self.nodes.get(&current).and_then(|n| n.parent) {
                Some(p) => current = p,
                None => break,
            }
        }
        path.reverse();
        let mut result = identity_matrix();
        for &h in &path {
            let local = self.get_composite_transform(h);
            result = mat4_multiply(&result, &local);
        }
        result
    }

    /// Compute transform from `source` to `target` node
    pub fn get_transform_to(&self, source: u32, target: u32) -> Option<[f32; 16]> {
        if source == target {
            return Some(identity_matrix());
        }
        let source_world = self.get_world_transform(source);
        let target_world = self.get_world_transform(target);
        let inv_target = mat4_invert(&target_world)?;
        Some(mat4_multiply(&inv_target, &source_world))
    }
}

// --- Matrix utilities ---

pub fn identity_matrix() -> [f32; 16] {
    [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

pub fn mat4_multiply(a: &[f32; 16], b: &[f32; 16]) -> [f32; 16] {
    let mut out = [0.0f32; 16];
    for col in 0..4 {
        for row in 0..4 {
            let mut sum = 0.0;
            for k in 0..4 {
                sum += a[k * 4 + row] * b[col * 4 + k];
            }
            out[col * 4 + row] = sum;
        }
    }
    out
}

pub fn mat4_invert(m: &[f32; 16]) -> Option<[f32; 16]> {
    let a00 = m[0];
    let a01 = m[1];
    let a02 = m[2];
    let a03 = m[3];
    let a10 = m[4];
    let a11 = m[5];
    let a12 = m[6];
    let a13 = m[7];
    let a20 = m[8];
    let a21 = m[9];
    let a22 = m[10];
    let a23 = m[11];
    let a30 = m[12];
    let a31 = m[13];
    let a32 = m[14];
    let a33 = m[15];

    let b00 = a00 * a11 - a01 * a10;
    let b01 = a00 * a12 - a02 * a10;
    let b02 = a00 * a13 - a03 * a10;
    let b03 = a01 * a12 - a02 * a11;
    let b04 = a01 * a13 - a03 * a11;
    let b05 = a02 * a13 - a03 * a12;
    let b06 = a20 * a31 - a21 * a30;
    let b07 = a20 * a32 - a22 * a30;
    let b08 = a20 * a33 - a23 * a30;
    let b09 = a21 * a32 - a22 * a31;
    let b10 = a21 * a33 - a23 * a31;
    let b11 = a22 * a33 - a23 * a32;

    let det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
    if det.abs() < 1e-10 {
        return None;
    }
    let inv_det = 1.0 / det;
    Some([
        (a11 * b11 - a12 * b10 + a13 * b09) * inv_det,
        (a02 * b10 - a01 * b11 - a03 * b09) * inv_det,
        (a31 * b05 - a32 * b04 + a33 * b03) * inv_det,
        (a22 * b04 - a21 * b05 - a23 * b03) * inv_det,
        (a12 * b08 - a10 * b11 - a13 * b07) * inv_det,
        (a00 * b11 - a02 * b08 + a03 * b07) * inv_det,
        (a32 * b02 - a30 * b05 - a33 * b01) * inv_det,
        (a20 * b05 - a22 * b02 + a23 * b01) * inv_det,
        (a10 * b10 - a11 * b08 + a13 * b06) * inv_det,
        (a01 * b08 - a00 * b10 - a03 * b06) * inv_det,
        (a30 * b04 - a31 * b02 + a33 * b00) * inv_det,
        (a21 * b02 - a20 * b04 - a23 * b00) * inv_det,
        (a11 * b07 - a10 * b09 - a12 * b06) * inv_det,
        (a00 * b09 - a01 * b07 + a02 * b06) * inv_det,
        (a31 * b01 - a30 * b03 - a32 * b00) * inv_det,
        (a20 * b03 - a21 * b01 + a22 * b00) * inv_det,
    ])
}

pub fn angle_axis_to_matrix(angle_deg: f32, ax: f32, ay: f32, az: f32) -> [f32; 16] {
    let angle = angle_deg * std::f32::consts::PI / 180.0;
    let len = (ax * ax + ay * ay + az * az).sqrt();
    if len < 1e-10 {
        return identity_matrix();
    }
    let (x, y, z) = (ax / len, ay / len, az / len);
    let c = angle.cos();
    let s = angle.sin();
    let t = 1.0 - c;

    [
        t * x * x + c,
        t * x * y + s * z,
        t * x * z - s * y,
        0.0,
        t * x * y - s * z,
        t * y * y + c,
        t * y * z + s * x,
        0.0,
        t * x * z + s * y,
        t * y * z - s * x,
        t * z * z + c,
        0.0,
        0.0,
        0.0,
        0.0,
        1.0,
    ]
}
