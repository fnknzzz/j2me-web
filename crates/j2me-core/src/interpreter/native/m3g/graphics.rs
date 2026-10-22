use crate::interpreter::native::registry::{NativeContext, NativeRegistry, NativeResult};
use crate::scheduler::virtual_thread::YieldReason;
use crate::vm::cmd;
use crate::vm::{AsyncRequestType, AsyncResult};

use super::{array_as_mut_ptr, array_as_ptr, ffi};

fn ctor(_: &mut NativeContext<'_>, interface: i32) -> i32 {
    (unsafe { ffi::m3gCreateContext(interface as usize) }) as i32
}
fn get_backend_capability(_: &mut NativeContext<'_>, capability: i32) -> i32 {
    match capability {
        1 | 2 | 3 | 6 => 0,
        4 | 5 => 1,
        7 => 8,
        8 | 9 | 10 => 1024,
        11 => 2,
        12 => 4,
        _ => 0,
    }
}
fn add_ref(_: &mut NativeContext<'_>, object: i32) {
    if object != 0 {
        unsafe { ffi::m3gAddRef(object as usize) }
    }
}
fn bind_graphics(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    surface: i32,
    clip_x: i32,
    clip_y: i32,
    clip_width: i32,
    clip_height: i32,
    depth: bool,
    hints: i32,
    _proper_renderer: bool,
) -> bool {
    let hints = hints as u32;
    if hints & !(2 | 4 | 8 | 16) != 0 {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return false;
    }
    if handle == 0 {
        return false;
    }
    let (width, height) = ctx
        .vm
        .surface_dims
        .get(&surface)
        .copied()
        .unwrap_or((clip_width.max(1), clip_height.max(1)));
    let bound = unsafe {
        ffi::m3gSetRenderBuffers(handle as usize, if depth { 0x03 } else { 0x02 });
        ffi::m3gSetRenderHints(handle as usize, hints);
        ffi::m3gBindWasmTarget(handle as usize, surface as u32, width.max(1), height.max(1)) != 0
    };
    if bound && clip_width > 0 && clip_height > 0 {
        unsafe { ffi::m3gSetClipRect(handle as usize, clip_x, clip_y, clip_width, clip_height) }
    }
    if bound {
        let buffer = ctx.vm.m3g_command_buffer_mut();
        buffer.push(cmd::M3G_BIND_TARGET);
        buffer.extend_from_slice(&(surface as u32).to_le_bytes());
    }
    bound
}
fn bind_image(ctx: &mut NativeContext<'_>, handle: i32, image: i32, depth: bool, hints: i32) {
    if (hints as u32) & !(2 | 4 | 8 | 16) != 0 {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return;
    }
    if handle == 0 {
        return;
    }
    let image_size = ctx
        .vm
        .m3g_resources
        .images
        .get(&(image as u32))
        .map(|image| (image.width, image.height));
    if let Some((width, height)) = image_size {
        // m3gcore stores Image targets in a M3Guint field. On native 64-bit
        // platforms that truncates the Image pointer, which then crashes in
        // m3gReleaseTarget. Metal owns the actual Image2D render target, so
        // bind an opaque wasm target of the same size for m3gcore's render
        // state/capture bookkeeping and write the finished pixels back through
        // the existing async Image2D readback boundary on release.
        unsafe {
            ffi::m3gSetRenderBuffers(handle as usize, if depth { 0x03 } else { 0x02 });
            ffi::m3gSetRenderHints(handle as usize, hints as u32);
            ffi::m3gBindWasmTarget(handle as usize, image as u32, width, height);
        }
        let buffer = ctx.vm.m3g_command_buffer_mut();
        buffer.push(cmd::M3G_BIND_IMAGE_TARGET);
        buffer.extend_from_slice(&(image as u32).to_le_bytes());
        buffer.extend_from_slice(&width.to_le_bytes());
        buffer.extend_from_slice(&height.to_le_bytes());
    }
    if image != 0 {
        ctx.vm
            .m3g_resources
            .render_target_images
            .insert(handle as u32, image as u32);
    }
}
fn release_image(ctx: &mut NativeContext<'_>, handle: i32) {
    let image = ctx
        .vm
        .m3g_resources
        .render_target_images
        .get(&(handle as u32))
        .copied();
    if handle != 0 {
        ctx.vm
            .m3g_command_buffer_mut()
            .push(cmd::M3G_RELEASE_TARGET);
        unsafe { ffi::m3gReleaseWasmTarget(handle as usize) };
    }
    if let Some(image) = image {
        ctx.vm.m3g_resources.pending_image_target_release = Some((handle as u32, image));
    }
}
fn release_graphics(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    _surface: i32,
    _image_target: bool,
    _proper_renderer: bool,
) {
    if handle != 0 {
        ctx.vm
            .m3g_command_buffer_mut()
            .push(cmd::M3G_RELEASE_TARGET);
        unsafe { ffi::m3gReleaseWasmTarget(handle as usize) };
    }
}
fn begin_target_batch(ctx: &mut NativeContext<'_>) {
    ctx.vm.begin_m3g_target_batch();
}
fn reset_lights(_: &mut NativeContext<'_>, handle: i32) {
    if handle != 0 {
        unsafe { ffi::m3gClearLights(handle as usize) }
    }
}
fn clear(ctx: &mut NativeContext<'_>, handle: i32, background: i32) {
    if handle == 0 {
        return;
    }
    let mut captured = Vec::new();
    if background != 0 {
        unsafe {
            let sink = &mut captured as *mut Vec<u8> as *mut std::ffi::c_void;
            if ffi::m3gWasmBeginCommandCapture(handle as usize, sink) != 0 {
                ffi::m3gClear(handle as usize, background as usize);
                ffi::m3gWasmEndCommandCapture();
            }
        }
    }
    let (mut sx, mut sy, mut sw, mut sh, mut vx, mut vy, mut vw, mut vh) = (0, 0, 0, 0, 0, 0, 0, 0);
    unsafe {
        ffi::m3gWasmGetScissor(&mut sx, &mut sy, &mut sw, &mut sh);
        ffi::m3gGetViewport(handle as usize, &mut vx, &mut vy, &mut vw, &mut vh);
    }
    let buffer = ctx.vm.m3g_command_buffer_mut();
    if background != 0 {
        write_background_state_cmds(buffer, background as u32);
    }
    buffer.push(cmd::M3G_CLEAR);
    buffer.extend_from_slice(&(handle as u32).to_le_bytes());
    buffer.extend_from_slice(&(background as u32).to_le_bytes());
    for value in [sx, sy, sw, sh, vx, vy, vw, vh] {
        buffer.extend_from_slice(&value.to_le_bytes());
    }
    buffer.extend_from_slice(&captured);
}
fn render(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    vertices: i32,
    indices: i32,
    appearance: i32,
    transform: u32,
    scope: i32,
) {
    if handle != 0 {
        unsafe {
            let pointer = array_as_ptr(ctx.vm, transform);
            let sink = ctx.vm.m3g_command_buffer_mut() as *mut Vec<u8> as *mut std::ffi::c_void;
            if ffi::m3gWasmBeginCommandCapture(handle as usize, sink) != 0 {
                ffi::m3gRender(
                    handle as usize,
                    vertices as usize,
                    indices as usize,
                    appearance as usize,
                    pointer,
                    1.0,
                    scope,
                );
                ffi::m3gWasmEndCommandCapture();
            }
        }
    }
}
fn render_node(ctx: &mut NativeContext<'_>, handle: i32, node: i32, transform: u32) {
    if handle != 0 {
        unsafe {
            let pointer = array_as_ptr(ctx.vm, transform);
            let sink = ctx.vm.m3g_command_buffer_mut() as *mut Vec<u8> as *mut std::ffi::c_void;
            if ffi::m3gWasmBeginCommandCapture(handle as usize, sink) != 0 {
                ffi::m3gRenderNode(handle as usize, node as usize, pointer);
                ffi::m3gWasmEndCommandCapture();
            }
        }
    }
}
fn render_world(ctx: &mut NativeContext<'_>, handle: i32, world: i32) {
    if handle == 0 {
        return;
    }
    unsafe {
        let mut captured = Vec::new();
        let sink = &mut captured as *mut Vec<u8> as *mut std::ffi::c_void;
        if ffi::m3gWasmBeginCommandCapture(handle as usize, sink) == 0 {
            return;
        }
        ffi::m3gRenderWorld(handle as usize, world as usize);
        ffi::m3gWasmEndCommandCapture();
        let background = ffi::m3gGetBackground(world as usize);
        if background != 0 {
            let (mut sx, mut sy, mut sw, mut sh, mut vx, mut vy, mut vw, mut vh) =
                (0, 0, 0, 0, 0, 0, 0, 0);
            ffi::m3gWasmGetScissor(&mut sx, &mut sy, &mut sw, &mut sh);
            ffi::m3gGetViewport(handle as usize, &mut vx, &mut vy, &mut vw, &mut vh);
            let buffer = ctx.vm.m3g_command_buffer_mut();
            write_background_state_cmds(buffer, background as u32);
            buffer.push(cmd::M3G_CLEAR);
            buffer.extend_from_slice(&(handle as u32).to_le_bytes());
            buffer.extend_from_slice(&(background as u32).to_le_bytes());
            for value in [sx, sy, sw, sh, vx, vy, vw, vh] {
                buffer.extend_from_slice(&value.to_le_bytes());
            }
        }
        ctx.vm.m3g_command_buffer_mut().extend_from_slice(&captured);
    }
}
fn set_camera(ctx: &mut NativeContext<'_>, handle: i32, camera: i32, transform: u32) {
    if handle != 0 {
        unsafe {
            ffi::m3gSetCamera(
                handle as usize,
                camera as usize,
                array_as_mut_ptr(ctx.vm, transform),
            )
        }
    }
}
fn set_viewport(_: &mut NativeContext<'_>, handle: i32, x: i32, y: i32, width: i32, height: i32) {
    if handle != 0 {
        unsafe { ffi::m3gSetViewport(handle as usize, x, y, width, height) }
    }
}
fn set_light(ctx: &mut NativeContext<'_>, handle: i32, index: i32, light: i32, transform: u32) {
    if handle != 0 {
        unsafe {
            ffi::m3gSetLight(
                handle as usize,
                index,
                light as usize,
                array_as_ptr(ctx.vm, transform),
            )
        }
    }
}
fn add_light(ctx: &mut NativeContext<'_>, handle: i32, light: i32, transform: u32) -> i32 {
    if handle != 0 {
        unsafe {
            ffi::m3gAddLight(
                handle as usize,
                light as usize,
                array_as_ptr(ctx.vm, transform),
            )
            .max(0)
        }
    } else {
        0
    }
}
fn set_depth_range(_: &mut NativeContext<'_>, handle: i32, near: f32, far: f32) {
    if handle != 0 {
        unsafe { ffi::m3gSetDepthRange(handle as usize, near, far) }
    }
}
fn get_view_transform(ctx: &mut NativeContext<'_>, handle: i32, transform: u32) {
    if handle != 0 && transform != 0 {
        unsafe {
            ffi::m3gGetViewTransform(
                handle as usize,
                array_as_mut_ptr(ctx.vm, transform) as *mut f32,
            )
        }
    }
}
fn get_camera(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        (unsafe { ffi::m3gGetCamera(handle as usize) }) as i32
    } else {
        0
    }
}
fn get_light_transform(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    index: i32,
    transform: u32,
) -> i32 {
    if handle != 0 {
        unsafe {
            ffi::m3gGetLightTransform(
                handle as usize,
                index,
                array_as_mut_ptr(ctx.vm, transform) as *mut f32,
            ) as i32
        }
    } else {
        0
    }
}
fn get_light_count(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetLightCount(handle as usize) }
    } else {
        0
    }
}
fn depth_range(handle: i32) -> (f32, f32) {
    let (mut near, mut far) = (0.0, 0.0);
    if handle != 0 {
        unsafe { ffi::m3gGetDepthRange(handle as usize, &mut near, &mut far) }
    }
    (near, far)
}
fn get_depth_range_near(_: &mut NativeContext<'_>, handle: i32) -> f32 {
    depth_range(handle).0
}
fn get_depth_range_far(_: &mut NativeContext<'_>, handle: i32) -> f32 {
    depth_range(handle).1
}
fn viewport(handle: i32) -> (i32, i32, i32, i32) {
    let (mut x, mut y, mut width, mut height) = (0, 0, 0, 0);
    if handle != 0 {
        unsafe { ffi::m3gGetViewport(handle as usize, &mut x, &mut y, &mut width, &mut height) }
    }
    (x, y, width, height)
}
fn get_viewport_x(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    viewport(handle).0
}
fn get_viewport_y(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    viewport(handle).1
}
fn get_viewport_width(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    viewport(handle).2
}
fn get_viewport_height(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    viewport(handle).3
}
fn is_aa_supported(_: &mut NativeContext<'_>, handle: i32) -> bool {
    handle != 0 && unsafe { ffi::m3gIsAntialiasingSupported(handle as usize) != 0 }
}
fn is_proper_renderer(_: &mut NativeContext<'_>) -> bool {
    true
}

fn flush_target_batch(ctx: &mut NativeContext<'_>) -> NativeResult<()> {
    if let Some((_request_id, request, result)) = ctx
        .vm
        .take_resolved_async_for_thread(ctx.thread_id, |result| {
            matches!(result, AsyncResult::M3gImageRead { .. })
        })
    {
        if let (
            AsyncRequestType::ReadM3gImage { image_handle, .. },
            AsyncResult::M3gImageRead { pixels, .. },
        ) = (request.request_type, result)
        {
            if let Some(image) = ctx.vm.m3g_resources.images.get(&image_handle) {
                if let Some(pixels) =
                    pixels_from_rgba8(image.format, image.width, image.height, &pixels)
                {
                    unsafe { ffi::m3gSetImage(image_handle as usize, pixels.as_ptr()) };
                }
            }
            if let Some((context_handle, pending_image)) =
                ctx.vm.m3g_resources.pending_image_target_release.take()
            {
                if pending_image == image_handle {
                    ctx.vm
                        .m3g_resources
                        .render_target_images
                        .remove(&context_handle);
                }
            }
        }
        return NativeResult::Ready(());
    }

    ctx.vm.flush_m3g_target_batch();
    let Some((context_handle, image_handle)) = ctx.vm.m3g_resources.pending_image_target_release
    else {
        return NativeResult::Ready(());
    };
    let Some(image) = ctx.vm.m3g_resources.images.get(&image_handle).cloned() else {
        ctx.vm
            .m3g_resources
            .render_target_images
            .remove(&context_handle);
        ctx.vm.m3g_resources.pending_image_target_release = None;
        return NativeResult::Ready(());
    };
    let request_id = ctx.vm.issue_async_request(
        ctx.thread_id,
        AsyncRequestType::ReadM3gImage {
            image_handle,
            width: image.width,
            height: image.height,
        },
    );
    NativeResult::Yield(YieldReason::AsyncWait { request_id })
}

fn pixels_from_rgba8(format: i32, width: i32, height: i32, rgba8_pixels: &[u8]) -> Option<Vec<u8>> {
    let pixel_count = (width as usize).checked_mul(height as usize)?;
    if rgba8_pixels.len() != pixel_count.checked_mul(4)? {
        return None;
    }
    match format {
        99 => Some(
            rgba8_pixels
                .chunks_exact(4)
                .flat_map(|pixel| pixel[..3].iter().copied())
                .collect(),
        ),
        100 => Some(rgba8_pixels.to_vec()),
        _ => None,
    }
}

fn write_background_state_cmds(buffer: &mut Vec<u8>, handle: u32) {
    let color = unsafe { ffi::m3gGetBgColor(handle as usize) };
    let color_clear = unsafe { ffi::m3gIsBgEnabled(handle as usize, 0) as u32 };
    let depth_clear = unsafe { ffi::m3gIsBgEnabled(handle as usize, 1) as u32 };
    buffer.push(cmd::M3G_CREATE_BACKGROUND);
    buffer.extend_from_slice(&handle.to_le_bytes());
    buffer.push(cmd::M3G_BG_SET_COLOR);
    buffer.extend_from_slice(&handle.to_le_bytes());
    buffer.extend_from_slice(&color.to_le_bytes());
    for (which, enable) in [(0u32, color_clear), (1, depth_clear)] {
        buffer.push(cmd::M3G_BG_SET_ENABLE);
        buffer.extend_from_slice(&handle.to_le_bytes());
        buffer.extend_from_slice(&which.to_le_bytes());
        buffer.extend_from_slice(&enable.to_le_bytes());
    }
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._ctor(I)I" => ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getBackendCapability(I)I" => get_backend_capability(capability: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._addRef(I)V" => add_ref(object: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._bindGraphics(IIIIIIZIZ)Z" => bind_graphics(handle: i32, surface: i32, clip_x: i32, clip_y: i32, clip_width: i32, clip_height: i32, depth: bool, hints: i32, proper_renderer: bool) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._bindImage(IIZI)V" => bind_image(handle: i32, image: i32, depth: bool, hints: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._releaseImage(I)V" => release_image(handle: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._releaseGraphics(IIZZ)V" => release_graphics(handle: i32, surface: i32, image_target: bool, proper_renderer: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._beginTargetBatch()V" => begin_target_batch() -> ()),
        crate::native_bind!(async static "javax.microedition.m3g.Graphics3D._flushTargetBatch()V" => flush_target_batch() -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._resetLights(I)V" => reset_lights(handle: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._clear(II)V" => clear(handle: i32, background: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._render(IIII[BI)V" => render(handle: i32, vertices: i32, indices: i32, appearance: i32, transform: u32, scope: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._renderNode(II[B)V" => render_node(handle: i32, node: i32, transform: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._renderWorld(II)V" => render_world(handle: i32, world: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._setCamera(II[B)V" => set_camera(handle: i32, camera: i32, transform: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._setViewport(IIIII)V" => set_viewport(handle: i32, x: i32, y: i32, width: i32, height: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._setLight(III[B)V" => set_light(handle: i32, index: i32, light: i32, transform: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._addLight(II[B)I" => add_light(handle: i32, light: i32, transform: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._setDepthRange(IFF)V" => set_depth_range(handle: i32, near: f32, far: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getViewTransform(I[B)V" => get_view_transform(handle: i32, transform: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getCamera(I)I" => get_camera(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getLightTransform(II[B)I" => get_light_transform(handle: i32, index: i32, transform: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getLightCount(I)I" => get_light_count(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getDepthRangeNear(I)F" => get_depth_range_near(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getDepthRangeFar(I)F" => get_depth_range_far(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getViewportX(I)I" => get_viewport_x(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getViewportY(I)I" => get_viewport_y(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getViewportWidth(I)I" => get_viewport_width(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._getViewportHeight(I)I" => get_viewport_height(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._isAASupported(I)Z" => is_aa_supported(handle: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.Graphics3D._isProperRenderer()Z" => is_proper_renderer() -> bool),
    ];
    registry.register_bindings(&bindings);
}

#[cfg(test)]
mod tests {
    use super::pixels_from_rgba8;

    #[test]
    fn image_target_readback_converts_rgba8_to_rgb() {
        assert_eq!(
            pixels_from_rgba8(99, 2, 1, &[0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88]),
            Some(vec![0x11, 0x22, 0x33, 0x55, 0x66, 0x77])
        );
    }

    #[test]
    fn image_target_readback_keeps_rgba8_for_rgba() {
        assert_eq!(
            pixels_from_rgba8(100, 1, 1, &[0x11, 0x22, 0x33, 0x44]),
            Some(vec![0x11, 0x22, 0x33, 0x44])
        );
    }

    #[test]
    fn image_target_readback_rejects_invalid_source_length_or_format() {
        assert_eq!(pixels_from_rgba8(99, 1, 1, &[0; 3]), None);
        assert_eq!(pixels_from_rgba8(96, 1, 1, &[0; 4]), None);
    }
}
