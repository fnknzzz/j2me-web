use crate::interpreter::native::registry::{NativeContext, NativeRegistry};
use crate::vm::cmd;

use super::{
    array_as_mut_ptr, array_as_ptr, ffi, read_i32_array, write_vertex_array_cmd,
    M3G_RESOURCE_INDEX_BUFFER,
};

fn vertex_array_ctor(
    ctx: &mut NativeContext<'_>,
    interface: i32,
    vertices: i32,
    components: i32,
    component_size: i32,
) -> i32 {
    if component_size != 1 && component_size != 2 {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return 0;
    }
    let component_type = if component_size == 1 { 1 } else { 3 };
    let handle = unsafe {
        ffi::m3gCreateVertexArray(interface as usize, vertices, components, component_type)
    };
    if handle != 0 {
        unsafe { ffi::m3gWasmRegisterExternalVertexArray(handle) };
        ctx.vm
            .m3g_resources
            .create_vertex_array(handle, vertices, components, component_size);
    }
    handle as i32
}

fn vertex_array_set(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    first: i32,
    count: i32,
    source: u32,
    component_size: i32,
) {
    if handle == 0 || source == 0 {
        return;
    }
    let length = ctx.vm.heap.array_length(source) as i32;
    let pointer = array_as_ptr(ctx.vm, source);
    let component_type = if component_size == 1 { 1 } else { 3 };
    if !ffi::is_shadow_handle(handle as usize) {
        unsafe {
            ffi::m3gSetVertexArrayElements(
                handle as usize,
                first,
                count,
                length,
                component_type,
                pointer,
            )
        };
    }
    let byte_length = (length * component_size) as usize;
    let data = unsafe { std::slice::from_raw_parts(pointer, byte_length) };
    if ctx
        .vm
        .m3g_resources
        .set_vertex_array_data(handle as u32, first, count, data, component_size)
    {
        write_vertex_array_cmd(ctx.vm, handle as u32);
    }
}

fn vertex_array_get(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    first: i32,
    count: i32,
    target: u32,
    component_size: i32,
) {
    if handle == 0 || target == 0 {
        return;
    }
    let length = ctx.vm.heap.array_length(target) as i32;
    let component_type = if component_size == 1 { 1 } else { 3 };
    unsafe {
        ffi::m3gGetVertexArrayElements(
            handle as usize,
            first,
            count,
            length,
            component_type,
            array_as_mut_ptr(ctx.vm, target),
        )
    };
}
fn vertex_array_set_byte(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    first: i32,
    count: i32,
    source: u32,
) {
    vertex_array_set(ctx, handle, first, count, source, 1);
}
fn vertex_array_set_short(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    first: i32,
    count: i32,
    source: u32,
) {
    vertex_array_set(ctx, handle, first, count, source, 2);
}
fn vertex_array_get_byte(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    first: i32,
    count: i32,
    target: u32,
) {
    vertex_array_get(ctx, handle, first, count, target, 1);
}
fn vertex_array_get_short(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    first: i32,
    count: i32,
    target: u32,
) {
    vertex_array_get(ctx, handle, first, count, target, 2);
}

fn vertex_array_params(handle: i32) -> (i32, i32, i32) {
    let mut count = 0;
    let mut components = 0;
    let mut component_type = 0;
    let mut stride = 0;
    if handle != 0 {
        unsafe {
            ffi::m3gGetVertexArrayParams(
                handle as usize,
                &mut count,
                &mut components,
                &mut component_type,
                &mut stride,
            )
        }
    }
    (count, components, component_type)
}
fn vertex_array_components(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    vertex_array_params(handle).1
}
fn vertex_array_component_type(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    vertex_array_params(handle).2
}
fn vertex_array_vertex_count(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    vertex_array_params(handle).0
}

fn create_implicit(
    ctx: &mut NativeContext<'_>,
    interface: i32,
    first_index: i32,
    lengths_ref: u32,
) -> i32 {
    if interface == 0 || lengths_ref == 0 {
        return 0;
    }
    let lengths = read_i32_array(ctx.vm, lengths_ref);
    let strip_count = lengths.len() as i32;
    let handle = unsafe {
        ffi::m3gCreateImplicitStripBuffer(
            interface as usize,
            strip_count,
            lengths.as_ptr(),
            first_index,
        )
    };
    ctx.vm
        .m3g_resources
        .create_index_buffer_implicit(handle as u32, first_index, lengths.clone());
    let version =
        unsafe { ffi::m3gWasmBumpResourceVersion(M3G_RESOURCE_INDEX_BUFFER, handle as u32) };
    let indices = joined_strip_indices(&lengths, None, first_index);
    let buf = ctx.vm.m3g_command_buffer_mut();
    buf.push(cmd::M3G_CREATE_INDEX_BUFFER);
    buf.extend_from_slice(&(handle as u32).to_le_bytes());
    buf.extend_from_slice(&version.to_le_bytes());
    buf.extend_from_slice(&(indices.len() as u32).to_le_bytes());
    for index in indices {
        buf.extend_from_slice(&index.to_le_bytes());
    }
    handle as i32
}

fn create_explicit(
    ctx: &mut NativeContext<'_>,
    interface: i32,
    indices_ref: u32,
    lengths_ref: u32,
) -> i32 {
    if interface == 0 || indices_ref == 0 || lengths_ref == 0 {
        return 0;
    }
    let lengths = read_i32_array(ctx.vm, lengths_ref);
    let indices = read_i32_array(ctx.vm, indices_ref);
    let strip_count = lengths.len() as i32;
    let handle = unsafe {
        ffi::m3gCreateStripBuffer(
            interface as usize,
            1,
            strip_count,
            lengths.as_ptr(),
            5,
            indices.len() as i32,
            indices.as_ptr() as *const u8,
        )
    };
    let mut bytes = Vec::with_capacity(indices.len() * 4);
    for index in &indices {
        bytes.extend_from_slice(&index.to_le_bytes());
    }
    ctx.vm.m3g_resources.create_index_buffer_explicit(
        handle as u32,
        bytes.clone(),
        lengths.clone(),
    );
    let version =
        unsafe { ffi::m3gWasmBumpResourceVersion(M3G_RESOURCE_INDEX_BUFFER, handle as u32) };
    let source = indices
        .iter()
        .map(|&index| index as u32)
        .collect::<Vec<_>>();
    let joined = joined_strip_indices(&lengths, Some(&source), 0);
    let buf = ctx.vm.m3g_command_buffer_mut();
    buf.push(cmd::M3G_CREATE_INDEX_BUFFER);
    buf.extend_from_slice(&(handle as u32).to_le_bytes());
    buf.extend_from_slice(&version.to_le_bytes());
    buf.extend_from_slice(&(joined.len() as u32).to_le_bytes());
    for index in joined {
        buf.extend_from_slice(&index.to_le_bytes());
    }
    handle as i32
}

fn joined_strip_indices(lengths: &[i32], explicit: Option<&[u32]>, first: i32) -> Vec<u32> {
    let source_count = lengths
        .iter()
        .filter_map(|&value| usize::try_from(value).ok())
        .sum::<usize>();
    let source = explicit.map(Vec::from).unwrap_or_else(|| {
        (0..source_count)
            .map(|index| first.wrapping_add(index as i32) as u32)
            .collect()
    });
    let mut joined = Vec::with_capacity(source_count + lengths.len().saturating_sub(1) * 3);
    let mut offset = 0usize;
    for (strip, &length) in lengths.iter().enumerate() {
        let Ok(length) = usize::try_from(length) else {
            return Vec::new();
        };
        if offset + length > source.len() {
            return Vec::new();
        }
        if strip > 0 && offset > 0 && length > 0 {
            joined.push(source[offset - 1]);
            joined.push(source[offset]);
            if lengths[strip - 1] & 1 != 0 {
                joined.push(source[offset]);
            }
        }
        joined.extend_from_slice(&source[offset..offset + length]);
        offset += length;
    }
    joined
}

fn get_index_count(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetStripIndexCount(handle as usize) }
    } else {
        0
    }
}
fn get_indices(ctx: &mut NativeContext<'_>, handle: i32, indices: u32) {
    if handle == 0 || indices == 0 {
        return;
    }
    let count = unsafe { ffi::m3gGetStripIndexCount(handle as usize) };
    if count > 0 && ctx.vm.heap.array_length(indices) < count as u32 {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return;
    }
    let offset = (crate::heap::ARRAY_DATA_OFFSET + indices) as usize;
    let ptr = ctx.vm.heap.data_mut().as_mut_ptr().wrapping_add(offset) as *mut i32;
    unsafe { ffi::m3gGetStripIndices(handle as usize, ptr) };
}

fn vertex_buffer_ctor(ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    let handle = unsafe { ffi::m3gCreateVertexBuffer(interface as usize) };
    ctx.vm.m3g_resources.create_vertex_buffer(handle as u32);
    handle as i32
}

fn copy_scale_bias(ctx: &NativeContext<'_>, bias_ref: u32) -> [f32; 3] {
    let mut bias = [0.0; 3];
    if bias_ref != 0 {
        let pointer = array_as_ptr(ctx.vm, bias_ref) as *const f32;
        for (index, value) in bias
            .iter_mut()
            .enumerate()
            .take(ctx.vm.heap.array_length(bias_ref).min(3) as usize)
        {
            *value = unsafe { *pointer.add(index) };
        }
    }
    bias
}

fn vertex_buffer_set_vertices(
    ctx: &mut NativeContext<'_>,
    buffer: i32,
    array: i32,
    scale: f32,
    bias_ref: u32,
) {
    if buffer == 0 {
        return;
    }
    if !ffi::is_shadow_handle(array as usize) {
        let (bias_ptr, bias_len) = if bias_ref != 0 {
            (
                array_as_ptr(ctx.vm, bias_ref) as *const f32,
                ctx.vm.heap.array_length(bias_ref) as i32,
            )
        } else {
            (std::ptr::null(), 0)
        };
        unsafe {
            ffi::m3gSetVertexArray(buffer as usize, array as usize, scale, bias_ptr, bias_len)
        };
    }
    let bias = copy_scale_bias(ctx, bias_ref);
    if let Some(vertex_buffer) = ctx
        .vm
        .m3g_resources
        .vertex_buffers
        .get_mut(&(buffer as u32))
    {
        vertex_buffer.positions = Some(array as u32);
        vertex_buffer.scale_bias = [scale, bias[0], bias[1], bias[2], 0.0, 0.0];
    }
}

fn vertex_buffer_set_normals(ctx: &mut NativeContext<'_>, buffer: i32, array: i32) {
    if buffer == 0 {
        return;
    }
    if !ffi::is_shadow_handle(array as usize) {
        unsafe { ffi::m3gSetNormalArray(buffer as usize, array as usize) };
    }
    if let Some(vertex_buffer) = ctx
        .vm
        .m3g_resources
        .vertex_buffers
        .get_mut(&(buffer as u32))
    {
        vertex_buffer.normals = Some(array as u32);
    }
}

fn vertex_buffer_set_colors(ctx: &mut NativeContext<'_>, buffer: i32, array: i32) {
    if buffer == 0 {
        return;
    }
    if !ffi::is_shadow_handle(array as usize) {
        unsafe { ffi::m3gSetColorArray(buffer as usize, array as usize) };
    }
    if let Some(vertex_buffer) = ctx
        .vm
        .m3g_resources
        .vertex_buffers
        .get_mut(&(buffer as u32))
    {
        vertex_buffer.colors = Some(array as u32);
    }
}

fn vertex_buffer_set_tex_coords(
    ctx: &mut NativeContext<'_>,
    buffer: i32,
    unit: i32,
    array: i32,
    scale: f32,
    bias_ref: u32,
) {
    if buffer == 0 {
        return;
    }
    if !ffi::is_shadow_handle(array as usize) {
        let (bias_ptr, bias_len) = if bias_ref != 0 {
            (
                array_as_ptr(ctx.vm, bias_ref) as *const f32,
                ctx.vm.heap.array_length(bias_ref) as i32,
            )
        } else {
            (std::ptr::null(), 0)
        };
        unsafe {
            ffi::m3gSetTexCoordArray(
                buffer as usize,
                unit,
                array as usize,
                scale,
                bias_ptr,
                bias_len,
            )
        };
    }
    let bias = copy_scale_bias(ctx, bias_ref);
    if let Some(vertex_buffer) = ctx
        .vm
        .m3g_resources
        .vertex_buffers
        .get_mut(&(buffer as u32))
    {
        if let Some(texcoord) = vertex_buffer.texcoords.get_mut(unit as usize) {
            *texcoord = Some(array as u32);
            vertex_buffer.texcoord_scale_bias[unit as usize] = [scale, bias[0], bias[1], bias[2]];
        }
    }
}

fn vertex_buffer_set_default_color(_ctx: &mut NativeContext<'_>, buffer: i32, argb: i32) {
    if buffer != 0 {
        unsafe { ffi::m3gSetVertexDefaultColor(buffer as usize, argb as u32) };
    }
}

fn vertex_buffer_get_default_color(_ctx: &mut NativeContext<'_>, buffer: i32) -> i32 {
    if buffer != 0 {
        unsafe { ffi::m3gGetVertexDefaultColor(buffer as usize) as i32 }
    } else {
        0
    }
}

fn vertex_buffer_get_array(
    ctx: &mut NativeContext<'_>,
    buffer: i32,
    which: i32,
    scale_bias_ref: u32,
) -> i32 {
    let (scale_bias_ptr, scale_bias_len) = if scale_bias_ref != 0 {
        (
            array_as_mut_ptr(ctx.vm, scale_bias_ref) as *mut f32,
            ctx.vm.heap.array_length(scale_bias_ref) as i32,
        )
    } else {
        (std::ptr::null_mut(), 0)
    };
    if buffer != 0 {
        unsafe {
            (ffi::m3gGetVertexArray(buffer as usize, which, scale_bias_ptr, scale_bias_len)) as i32
        }
    } else {
        0
    }
}

fn vertex_buffer_get_vertex_count(_ctx: &mut NativeContext<'_>, buffer: i32) -> i32 {
    if buffer != 0 {
        unsafe { ffi::m3gGetVertexCount(buffer as usize) }
    } else {
        0
    }
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.VertexArray._ctor(IIII)I" => vertex_array_ctor(interface: i32, vertices: i32, components: i32, component_size: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.VertexArray._setByte(III[B)V" => vertex_array_set_byte(handle: i32, first: i32, count: i32, source: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.VertexArray._setShort(III[S)V" => vertex_array_set_short(handle: i32, first: i32, count: i32, source: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.VertexArray._getByte(III[B)V" => vertex_array_get_byte(handle: i32, first: i32, count: i32, target: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.VertexArray._getShort(III[S)V" => vertex_array_get_short(handle: i32, first: i32, count: i32, target: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.VertexArray._getComponentCount(I)I" => vertex_array_components(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.VertexArray._getComponentType(I)I" => vertex_array_component_type(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.VertexArray._getVertexCount(I)I" => vertex_array_vertex_count(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.TriangleStripArray._createImplicit(II[I)I" => create_implicit(interface: i32, first_index: i32, lengths: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.TriangleStripArray._createExplicit(I[I[I)I" => create_explicit(interface: i32, indices: u32, lengths: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.TriangleStripArray._getIndexCount(I)I" => get_index_count(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.TriangleStripArray._getIndices(I[I)V" => get_indices(handle: i32, indices: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.VertexBuffer._ctor(I)I" => vertex_buffer_ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.VertexBuffer._setVertices(IIF[F)V" => vertex_buffer_set_vertices(buffer: i32, array: i32, scale: f32, bias: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.VertexBuffer._setNormals(II)V" => vertex_buffer_set_normals(buffer: i32, array: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.VertexBuffer._setColors(II)V" => vertex_buffer_set_colors(buffer: i32, array: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.VertexBuffer._setTexCoords(IIIF[F)V" => vertex_buffer_set_tex_coords(buffer: i32, unit: i32, array: i32, scale: f32, bias: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.VertexBuffer._setDefaultColor(II)V" => vertex_buffer_set_default_color(buffer: i32, argb: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.VertexBuffer._getDefaultColor(I)I" => vertex_buffer_get_default_color(buffer: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.VertexBuffer._getArray(II[F)I" => vertex_buffer_get_array(buffer: i32, which: i32, scale_bias: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.VertexBuffer._getVertexCount(I)I" => vertex_buffer_get_vertex_count(buffer: i32) -> i32),
    ];
    registry.register_bindings(&bindings);
}

#[cfg(test)]
mod tests {
    use super::joined_strip_indices;

    #[test]
    fn joins_even_strip_with_two_degenerate_indices() {
        assert_eq!(
            joined_strip_indices(&[4, 3], Some(&[0, 1, 2, 3, 4, 5, 6]), 0),
            vec![0, 1, 2, 3, 3, 4, 4, 5, 6]
        );
    }

    #[test]
    fn joins_odd_strip_with_extra_index_to_preserve_winding() {
        assert_eq!(
            joined_strip_indices(&[3, 3], None, 7),
            vec![7, 8, 9, 9, 10, 10, 10, 11, 12]
        );
    }
}
