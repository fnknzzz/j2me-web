use crate::interpreter::native::registry::{NativeContext, NativeRegistry};

use super::ffi;

fn cull_to_c(value: i32) -> i32 {
    match value {
        0xA0 => 1,
        0xA1 => 2,
        0xA2 => 0,
        value => value,
    }
}
fn cull_to_java(value: i32) -> i32 {
    match value {
        1 => 0xA0,
        2 => 0xA1,
        0 => 0xA2,
        value => value,
    }
}
fn shade_to_c(value: i32) -> i32 {
    match value {
        0xA4 => 0,
        0xA5 => 1,
        value => value,
    }
}
fn shade_to_java(value: i32) -> i32 {
    match value {
        0 => 0xA4,
        1 => 0xA5,
        value => value,
    }
}
fn winding_to_c(value: i32) -> i32 {
    match value {
        0xA8 => 0,
        0xA9 => 1,
        value => value,
    }
}
fn winding_to_java(value: i32) -> i32 {
    match value {
        0 => 0xA8,
        1 => 0xA9,
        value => value,
    }
}
fn blending_to_c(value: i32) -> i32 {
    match value {
        64 => 1,
        65 => 5,
        66 => 6,
        67 => 7,
        68 => 0,
        value => value,
    }
}
fn blending_to_java(value: i32) -> i32 {
    match value {
        1 => 64,
        5 => 65,
        6 => 66,
        7 => 67,
        0 => 68,
        value => value,
    }
}

fn polygon_ctor(_ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    unsafe { ffi::m3gCreatePolygonMode(interface as usize) as i32 }
}
fn polygon_set_culling(_ctx: &mut NativeContext<'_>, handle: i32, value: i32) {
    unsafe { ffi::m3gSetCulling(handle as usize, cull_to_c(value)) }
}
fn polygon_get_culling(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    cull_to_java(unsafe { ffi::m3gGetCulling(handle as usize) })
}
fn polygon_set_winding(_ctx: &mut NativeContext<'_>, handle: i32, value: i32) {
    unsafe { ffi::m3gSetWinding(handle as usize, winding_to_c(value)) }
}
fn polygon_get_winding(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    winding_to_java(unsafe { ffi::m3gGetWinding(handle as usize) })
}
fn polygon_set_shading(_ctx: &mut NativeContext<'_>, handle: i32, value: i32) {
    unsafe { ffi::m3gSetShading(handle as usize, shade_to_c(value)) }
}
fn polygon_get_shading(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    shade_to_java(unsafe { ffi::m3gGetShading(handle as usize) })
}
fn polygon_set_two_sided(_ctx: &mut NativeContext<'_>, handle: i32, value: bool) {
    unsafe { ffi::m3gSetTwoSidedLightingEnable(handle as usize, value as i32) }
}
fn polygon_is_two_sided(_ctx: &mut NativeContext<'_>, handle: i32) -> bool {
    unsafe { ffi::m3gIsTwoSidedLightingEnabled(handle as usize) != 0 }
}
fn polygon_set_local_camera(_ctx: &mut NativeContext<'_>, handle: i32, value: bool) {
    unsafe { ffi::m3gSetLocalCameraLightingEnable(handle as usize, value as i32) }
}
fn polygon_is_local_camera(_ctx: &mut NativeContext<'_>, handle: i32) -> bool {
    unsafe { ffi::m3gIsLocalCameraLightingEnabled(handle as usize) != 0 }
}
fn polygon_set_perspective(_ctx: &mut NativeContext<'_>, handle: i32, value: bool) {
    unsafe { ffi::m3gSetPerspectiveCorrectionEnable(handle as usize, value as i32) }
}
fn polygon_is_perspective(_ctx: &mut NativeContext<'_>, handle: i32) -> bool {
    unsafe { ffi::m3gIsPerspectiveCorrectionEnabled(handle as usize) != 0 }
}

fn compositing_ctor(_ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    unsafe { ffi::m3gCreateCompositingMode(interface as usize) as i32 }
}
fn compositing_set_blending(_ctx: &mut NativeContext<'_>, handle: i32, value: i32) {
    unsafe { ffi::m3gSetBlending(handle as usize, blending_to_c(value)) }
}
fn compositing_get_blending(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    blending_to_java(unsafe { ffi::m3gGetBlending(handle as usize) })
}
fn compositing_set_alpha_threshold(_ctx: &mut NativeContext<'_>, handle: i32, value: f32) {
    unsafe { ffi::m3gSetAlphaThreshold(handle as usize, value) }
}
fn compositing_get_alpha_threshold(_ctx: &mut NativeContext<'_>, handle: i32) -> f32 {
    unsafe { ffi::m3gGetAlphaThreshold(handle as usize) }
}
fn compositing_set_alpha_write(_ctx: &mut NativeContext<'_>, handle: i32, value: bool) {
    unsafe { ffi::m3gSetAlphaWriteEnable(handle as usize, value as i32) }
}
fn compositing_is_alpha_write(_ctx: &mut NativeContext<'_>, handle: i32) -> bool {
    unsafe { ffi::m3gIsAlphaWriteEnabled(handle as usize) != 0 }
}
fn compositing_set_depth_test(_ctx: &mut NativeContext<'_>, handle: i32, value: bool) {
    unsafe { ffi::m3gEnableDepthTest(handle as usize, value as i32) }
}
fn compositing_is_depth_test(_ctx: &mut NativeContext<'_>, handle: i32) -> bool {
    unsafe { ffi::m3gIsDepthTestEnabled(handle as usize) != 0 }
}
fn compositing_set_depth_write(_ctx: &mut NativeContext<'_>, handle: i32, value: bool) {
    unsafe { ffi::m3gEnableDepthWrite(handle as usize, value as i32) }
}
fn compositing_is_depth_write(_ctx: &mut NativeContext<'_>, handle: i32) -> bool {
    unsafe { ffi::m3gIsDepthWriteEnabled(handle as usize) != 0 }
}
fn compositing_set_color_write(_ctx: &mut NativeContext<'_>, handle: i32, value: bool) {
    unsafe { ffi::m3gEnableColorWrite(handle as usize, value as i32) }
}
fn compositing_is_color_write(_ctx: &mut NativeContext<'_>, handle: i32) -> bool {
    unsafe { ffi::m3gIsColorWriteEnabled(handle as usize) != 0 }
}
fn compositing_set_depth_offset(
    _ctx: &mut NativeContext<'_>,
    handle: i32,
    factor: f32,
    units: f32,
) {
    unsafe { ffi::m3gSetDepthOffset(handle as usize, factor, units) }
}
fn compositing_get_depth_offset_factor(_ctx: &mut NativeContext<'_>, handle: i32) -> f32 {
    unsafe { ffi::m3gGetDepthOffsetFactor(handle as usize) }
}
fn compositing_get_depth_offset_units(_ctx: &mut NativeContext<'_>, handle: i32) -> f32 {
    unsafe { ffi::m3gGetDepthOffsetUnits(handle as usize) }
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._ctor(I)I" => polygon_ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._setCulling(II)V" => polygon_set_culling(handle: i32, value: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._getCulling(I)I" => polygon_get_culling(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._setWinding(II)V" => polygon_set_winding(handle: i32, value: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._getWinding(I)I" => polygon_get_winding(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._setShading(II)V" => polygon_set_shading(handle: i32, value: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._getShading(I)I" => polygon_get_shading(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._setTwoSidedLightingEnable(IZ)V" => polygon_set_two_sided(handle: i32, value: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._isTwoSidedLightingEnabled(I)Z" => polygon_is_two_sided(handle: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._setLocalCameraLightingEnable(IZ)V" => polygon_set_local_camera(handle: i32, value: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._isLocalCameraLightingEnabled(I)Z" => polygon_is_local_camera(handle: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._setPerspectiveCorrectionEnable(IZ)V" => polygon_set_perspective(handle: i32, value: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.PolygonMode._isPerspectiveCorrectionEnabled(I)Z" => polygon_is_perspective(handle: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._ctor(I)I" => compositing_ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._setBlending(II)V" => compositing_set_blending(handle: i32, value: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._getBlending(I)I" => compositing_get_blending(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._setAlphaThreshold(IF)V" => compositing_set_alpha_threshold(handle: i32, value: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._getAlphaThreshold(I)F" => compositing_get_alpha_threshold(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._setAlphaWriteEnable(IZ)V" => compositing_set_alpha_write(handle: i32, value: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._isAlphaWriteEnabled(I)Z" => compositing_is_alpha_write(handle: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._enableDepthTest(IZ)V" => compositing_set_depth_test(handle: i32, value: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._isDepthTestEnabled(I)Z" => compositing_is_depth_test(handle: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._enableDepthWrite(IZ)V" => compositing_set_depth_write(handle: i32, value: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._isDepthWriteEnabled(I)Z" => compositing_is_depth_write(handle: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._enableColorWrite(IZ)V" => compositing_set_color_write(handle: i32, value: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._isColorWriteEnabled(I)Z" => compositing_is_color_write(handle: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._setDepthOffset(IFF)V" => compositing_set_depth_offset(handle: i32, factor: f32, units: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._getDepthOffsetFactor(I)F" => compositing_get_depth_offset_factor(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.CompositingMode._getDepthOffsetUnits(I)F" => compositing_get_depth_offset_units(handle: i32) -> f32),
    ];
    registry.register_bindings(&bindings);
}
