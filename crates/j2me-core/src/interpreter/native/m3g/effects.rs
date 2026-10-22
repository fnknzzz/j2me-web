use crate::interpreter::native::registry::{NativeContext, NativeRegistry};

use super::ffi;

fn light_ctor(ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    let handle = unsafe { ffi::m3gCreateLight(interface as usize) };
    ctx.vm.m3g_resources.create_light(handle);
    handle as i32
}
fn light_set_intensity(ctx: &mut NativeContext<'_>, handle: i32, intensity: f32) {
    unsafe { ffi::m3gSetIntensity(handle as usize, intensity) };
    if let Some(light) = ctx.vm.m3g_resources.nodes.get_mut(&(handle as u32)) {
        light.light_intensity = intensity;
    }
}
fn light_get_intensity(_ctx: &mut NativeContext<'_>, handle: i32) -> f32 {
    unsafe { ffi::m3gGetIntensity(handle as usize) }
}
fn light_set_color(ctx: &mut NativeContext<'_>, handle: i32, color: i32) {
    unsafe { ffi::m3gSetLightColor(handle as usize, color as u32) };
    if let Some(light) = ctx.vm.m3g_resources.nodes.get_mut(&(handle as u32)) {
        light.light_color = color;
    }
}
fn light_get_color(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    unsafe { ffi::m3gGetLightColor(handle as usize) as i32 }
}
fn light_set_mode(ctx: &mut NativeContext<'_>, handle: i32, mode: i32) {
    unsafe { ffi::m3gSetLightMode(handle as usize, mode) };
    if let Some(light) = ctx.vm.m3g_resources.nodes.get_mut(&(handle as u32)) {
        light.light_mode = mode;
    }
}
fn light_get_mode(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    unsafe { ffi::m3gGetLightMode(handle as usize) }
}
fn light_set_spot_angle(_ctx: &mut NativeContext<'_>, handle: i32, angle: f32) {
    unsafe { ffi::m3gSetSpotAngle(handle as usize, angle) }
}
fn light_get_spot_angle(_ctx: &mut NativeContext<'_>, handle: i32) -> f32 {
    unsafe { ffi::m3gGetSpotAngle(handle as usize) }
}
fn light_set_spot_exponent(_ctx: &mut NativeContext<'_>, handle: i32, exponent: f32) {
    unsafe { ffi::m3gSetSpotExponent(handle as usize, exponent) }
}
fn light_get_spot_exponent(_ctx: &mut NativeContext<'_>, handle: i32) -> f32 {
    unsafe { ffi::m3gGetSpotExponent(handle as usize) }
}
fn light_set_attenuation(
    _ctx: &mut NativeContext<'_>,
    handle: i32,
    constant: f32,
    linear: f32,
    quadratic: f32,
) {
    unsafe { ffi::m3gSetAttenuation(handle as usize, constant, linear, quadratic) }
}
fn light_get_attenuation(_ctx: &mut NativeContext<'_>, handle: i32, which: i32) -> f32 {
    unsafe { ffi::m3gGetAttenuation(handle as usize, which) }
}

fn material_ctor(ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    let handle = unsafe { ffi::m3gCreateMaterial(interface as usize) };
    ctx.vm.m3g_resources.create_material(handle);
    handle as i32
}

fn material_set_color(ctx: &mut NativeContext<'_>, handle: i32, target: i32, color: i32) {
    unsafe { ffi::m3gSetColor(handle as usize, target, color as u32) };
    if let Some(material) = ctx.vm.m3g_resources.materials.get_mut(&(handle as u32)) {
        if target & 1024 != 0 {
            material.ambient = color;
        }
        if target & 2048 != 0 {
            material.diffuse = color;
        }
        if target & 4096 != 0 {
            material.emissive = color;
        }
        if target & 8192 != 0 {
            material.specular = color;
        }
    }
}
fn material_get_color(_ctx: &mut NativeContext<'_>, handle: i32, target: i32) -> i32 {
    unsafe { ffi::m3gGetColor(handle as usize, target) as i32 }
}
fn material_set_shininess(ctx: &mut NativeContext<'_>, handle: i32, shininess: f32) {
    unsafe { ffi::m3gSetShininess(handle as usize, shininess) };
    if let Some(material) = ctx.vm.m3g_resources.materials.get_mut(&(handle as u32)) {
        material.shininess = shininess;
    }
}
fn material_get_shininess(_ctx: &mut NativeContext<'_>, handle: i32) -> f32 {
    unsafe { ffi::m3gGetShininess(handle as usize) }
}
fn material_set_tracking(ctx: &mut NativeContext<'_>, handle: i32, enabled: bool) {
    unsafe { ffi::m3gSetVertexColorTrackingEnable(handle as usize, enabled as i32) };
    if let Some(material) = ctx.vm.m3g_resources.materials.get_mut(&(handle as u32)) {
        material.vertex_color_tracking = enabled;
    }
}
fn material_is_tracking(_ctx: &mut NativeContext<'_>, handle: i32) -> bool {
    unsafe { ffi::m3gIsVertexColorTrackingEnabled(handle as usize) != 0 }
}

fn fog_ctor(_ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    (unsafe { ffi::m3gCreateFog(interface as usize) }) as i32
}
fn fog_set_mode(_ctx: &mut NativeContext<'_>, handle: i32, mode: i32) {
    if handle != 0 {
        unsafe { ffi::m3gSetFogMode(handle as usize, mode) }
    }
}
fn fog_get_mode(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetFogMode(handle as usize) }
    } else {
        0
    }
}
fn fog_set_linear(_ctx: &mut NativeContext<'_>, handle: i32, near: f32, far: f32) {
    if handle != 0 {
        unsafe { ffi::m3gSetFogLinear(handle as usize, near, far) }
    }
}
fn fog_get_distance(_ctx: &mut NativeContext<'_>, handle: i32, which: i32) -> f32 {
    if handle != 0 {
        unsafe { ffi::m3gGetFogDistance(handle as usize, which) }
    } else {
        0.0
    }
}
fn fog_set_density(_ctx: &mut NativeContext<'_>, handle: i32, density: f32) {
    if handle != 0 {
        unsafe { ffi::m3gSetFogDensity(handle as usize, density) }
    }
}
fn fog_get_density(_ctx: &mut NativeContext<'_>, handle: i32) -> f32 {
    if handle != 0 {
        unsafe { ffi::m3gGetFogDensity(handle as usize) }
    } else {
        0.0
    }
}
fn fog_set_color(_ctx: &mut NativeContext<'_>, handle: i32, color: i32) {
    if handle != 0 {
        unsafe { ffi::m3gSetFogColor(handle as usize, color as u32) }
    }
}
fn fog_get_color(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetFogColor(handle as usize) as i32 }
    } else {
        0
    }
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.Light._ctor(I)I" => light_ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Light._setIntensity(IF)V" => light_set_intensity(handle: i32, intensity: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Light._getIntensity(I)F" => light_get_intensity(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.Light._setColor(II)V" => light_set_color(handle: i32, color: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Light._getColor(I)I" => light_get_color(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Light._setMode(II)V" => light_set_mode(handle: i32, mode: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Light._getMode(I)I" => light_get_mode(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Light._setSpotAngle(IF)V" => light_set_spot_angle(handle: i32, angle: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Light._getSpotAngle(I)F" => light_get_spot_angle(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.Light._setSpotExponent(IF)V" => light_set_spot_exponent(handle: i32, exponent: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Light._getSpotExponent(I)F" => light_get_spot_exponent(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.Light._setAttenuation(IFFF)V" => light_set_attenuation(handle: i32, constant: f32, linear: f32, quadratic: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Light._getAttenuation(II)F" => light_get_attenuation(handle: i32, which: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.Material._ctor(I)I" => material_ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Material._setColor(III)V" => material_set_color(handle: i32, target: i32, color: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Material._getColor(II)I" => material_get_color(handle: i32, target: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Material._setShininess(IF)V" => material_set_shininess(handle: i32, shininess: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Material._getShininess(I)F" => material_get_shininess(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.Material._setVertexColorTrackingEnable(IZ)V" => material_set_tracking(handle: i32, enabled: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Material._isVertexColorTrackingEnabled(I)Z" => material_is_tracking(handle: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.Fog._ctor(I)I" => fog_ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Fog._setMode(II)V" => fog_set_mode(handle: i32, mode: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Fog._getMode(I)I" => fog_get_mode(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Fog._setLinear(IFF)V" => fog_set_linear(handle: i32, near: f32, far: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Fog._getDistance(II)F" => fog_get_distance(handle: i32, which: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.Fog._setDensity(IF)V" => fog_set_density(handle: i32, density: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Fog._getDensity(I)F" => fog_get_density(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.Fog._setColor(II)V" => fog_set_color(handle: i32, color: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Fog._getColor(I)I" => fog_get_color(handle: i32) -> i32),
    ];
    registry.register_bindings(&bindings);
}
