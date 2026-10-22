mod animation;
mod appearance;
mod effects;
#[allow(non_snake_case)]
pub mod ffi;
mod geometry;
mod graphics;
mod image;
mod interface;
mod loader;
mod mesh;
mod object;
mod render_state;
pub mod resource_store;
mod scene;
mod transform;

use crate::heap::ARRAY_DATA_OFFSET;
use crate::vm::cmd;
use crate::vm::SharedVm;

use super::registry::NativeRegistry;

pub(super) const M3G_RESOURCE_VERTEX_ARRAY: u32 = 1;
pub(super) const M3G_RESOURCE_IMAGE_TARGET: u32 = 3;
pub(super) const M3G_RESOURCE_INDEX_BUFFER: u32 = 4;
const M3G_MATRIX_BYTES: usize = 76;

const M3G_NO_ERROR: u32 = 0;
const M3G_OUT_OF_MEMORY: u32 = 1;
const M3G_INVALID_VALUE: u32 = 2;
const M3G_INVALID_ENUM: u32 = 3;
const M3G_INVALID_OPERATION: u32 = 4;
const M3G_INVALID_INDEX: u32 = 6;
const M3G_IO_ERROR: u32 = 7;
const M3G_NULL_POINTER: u32 = 8;
const M3G_ARITHMETIC_ERROR: u32 = 9;

pub(super) const M3G_IMAGE_FORMAT_RGBA: i32 = 100;

pub fn check_pending_error(vm: &mut SharedVm) {
    let interface = vm.m3g_interface_handle;
    if interface == 0 {
        return;
    }

    let error = unsafe { ffi::m3gGetError(interface) };
    if vm.pending_exception_str.is_some() {
        return;
    }

    if let Some(exception) = m3g_error_exception(error) {
        vm.pending_exception_str = Some(exception.to_string());
    }
}

fn m3g_error_exception(error: u32) -> Option<&'static str> {
    if error == M3G_NO_ERROR {
        return None;
    }

    Some(match error {
        M3G_OUT_OF_MEMORY => "java.lang.OutOfMemoryError",
        M3G_INVALID_VALUE | M3G_INVALID_ENUM => "java.lang.IllegalArgumentException",
        M3G_INVALID_OPERATION => "java.lang.IllegalStateException",
        M3G_INVALID_INDEX => "java.lang.IndexOutOfBoundsException",
        M3G_IO_ERROR => "java.io.IOException",
        M3G_NULL_POINTER => "java.lang.NullPointerException",
        M3G_ARITHMETIC_ERROR => "java.lang.ArithmeticException",
        _ => "java.lang.IllegalArgumentException",
    })
}

pub(super) fn set_m3g_error_exception(vm: &mut SharedVm, error: u32) {
    if let Some(exception) = m3g_error_exception(error) {
        vm.pending_exception_str = Some(exception.to_string());
    }
}

pub(super) fn read_i32_array(vm: &SharedVm, array_ref: u32) -> Vec<i32> {
    let len = vm.heap.array_length(array_ref);
    let mut values = Vec::with_capacity(len as usize);
    for i in 0..len {
        values.push(vm.heap.array_read_u32(array_ref, i) as i32);
    }
    values
}

pub(super) fn write_invalidate_image_textures_cmd(vm: &mut SharedVm, image_handle: u32) {
    let buf = vm.m3g_command_buffer_mut();
    buf.push(cmd::M3G_INVALIDATE_IMAGE_TEXTURES);
    buf.extend_from_slice(&image_handle.to_le_bytes());
}

pub(super) fn write_delete_resource_cmd(vm: &mut SharedVm, kind: u32, handle: u32) {
    unsafe { ffi::m3gWasmReleaseResourceVersion(kind, handle) };
    let buf = vm.m3g_command_buffer_mut();
    buf.push(cmd::M3G_DELETE_RESOURCE);
    buf.extend_from_slice(&kind.to_le_bytes());
    buf.extend_from_slice(&handle.to_le_bytes());
}

/// Get a raw pointer to the beginning of a Java byte[] array's data.
/// Returns null if array_ref is 0.
pub(super) fn array_as_ptr(vm: &SharedVm, array_ref: u32) -> *const u8 {
    if array_ref == 0 {
        return std::ptr::null();
    }
    let offset = (ARRAY_DATA_OFFSET + array_ref) as usize;
    vm.heap.data().as_ptr().wrapping_add(offset)
}

pub(super) fn array_as_mut_ptr(vm: &mut SharedVm, array_ref: u32) -> *mut u8 {
    if array_ref == 0 {
        return std::ptr::null_mut();
    }
    let offset = (ARRAY_DATA_OFFSET + array_ref) as usize;
    vm.heap.data_mut().as_mut_ptr().wrapping_add(offset)
}

pub(super) fn image_bytes_per_pixel(format: i32) -> Option<u32> {
    match format {
        96 | 97 => Some(1),
        98 => Some(2),
        99 => Some(3),
        100 => Some(4),
        _ => None,
    }
}

pub(super) fn image_data_is_too_short(
    vm: &SharedVm,
    image_ref: u32,
    width: i32,
    height: i32,
    bytes_per_pixel: u32,
) -> bool {
    if width <= 0 || height <= 0 {
        return false;
    }

    let required = (width as u64)
        .saturating_mul(height as u64)
        .saturating_mul(bytes_per_pixel as u64);
    (vm.heap.array_length(image_ref) as u64) < required
}

/// Write a M3G_CREATE_VERTEX_ARRAY command to the command buffer.
/// Format: [cmd:u8][handle:u32][version:u32][numVerts:u32][flags:u32]
///         [vectorBytes:u32][colorBytes:u32][float4 vectors...][optional normalized float4 colors...]
pub(super) fn write_vertex_array_cmd(vm: &mut SharedVm, handle: u32) {
    // Split-borrow: `va` reads `vm.m3g_resources` while `buf` writes a disjoint
    // field of `vm`, so no clone is needed.
    let Some(va) = vm.m3g_resources.vertex_arrays.get(&handle) else {
        return;
    };
    let version = unsafe { ffi::m3gWasmBumpResourceVersion(M3G_RESOURCE_VERTEX_ARRAY, handle) };
    let count = va.num_vertices.max(0) as usize;
    let components = va.num_components.max(0) as usize;
    let component_size = va.component_size.max(0) as usize;
    let required = count
        .saturating_mul(components)
        .saturating_mul(component_size);
    if components == 0
        || components > 4
        || !matches!(component_size, 1 | 2 | 4)
        || va.data.len() < required
    {
        return;
    }
    let mut vectors = Vec::with_capacity(count * 4);
    let mut normalized = (component_size == 1).then(|| Vec::with_capacity(count * 4));
    for vertex in 0..count {
        for component in 0..4 {
            let value = if component < components {
                let source = (vertex * components + component) * component_size;
                match component_size {
                    1 => i8::from_le_bytes([va.data[source]]) as f32,
                    2 => i16::from_le_bytes([va.data[source], va.data[source + 1]]) as f32,
                    _ => f32::from_le_bytes(va.data[source..source + 4].try_into().unwrap()),
                }
            } else if component == 3 {
                1.0
            } else {
                0.0
            };
            vectors.push(value);
            if let Some(colors) = &mut normalized {
                colors.push(if component < components {
                    va.data[vertex * components + component] as f32 / 255.0
                } else if component == 3 {
                    1.0
                } else {
                    0.0
                });
            }
        }
    }
    let vector_bytes = (vectors.len() * 4) as u32;
    let color_bytes = normalized
        .as_ref()
        .map_or(0, |values| (values.len() * 4) as u32);
    let buf = match &mut vm.m3g_pending_command_buffer {
        Some(b) => b,
        None => &mut vm.command_buffer,
    };
    buf.push(cmd::M3G_CREATE_VERTEX_ARRAY);
    buf.extend_from_slice(&handle.to_le_bytes());
    buf.extend_from_slice(&version.to_le_bytes());
    buf.extend_from_slice(&(count as u32).to_le_bytes());
    buf.extend_from_slice(&(normalized.is_some() as u32).to_le_bytes());
    buf.extend_from_slice(&vector_bytes.to_le_bytes());
    buf.extend_from_slice(&color_bytes.to_le_bytes());
    for value in vectors {
        buf.extend_from_slice(&value.to_le_bytes());
    }
    if let Some(colors) = normalized {
        for value in colors {
            buf.extend_from_slice(&value.to_le_bytes());
        }
    }
}

pub use interface::finalize_object;

pub fn register(registry: &mut NativeRegistry) {
    interface::register(registry);

    graphics::register(registry);

    object::register(registry);

    loader::register(registry);

    animation::register(registry);

    geometry::register(registry);

    appearance::register(registry);

    render_state::register(registry);
    transform::register(registry);

    image::register(registry);

    effects::register(registry);

    scene::register(registry);

    mesh::register(registry);
}
