use crate::interpreter::native::registry::{NativeContext, NativeRegistry};

use super::ffi;

fn ctor(ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    let handle = unsafe { ffi::m3gCreateAppearance(interface as usize) };
    ctx.vm.m3g_resources.create_appearance(handle as u32);
    handle as i32
}
fn set_compositing_mode(_ctx: &mut NativeContext<'_>, appearance: i32, mode: i32) {
    if appearance != 0 {
        unsafe { ffi::m3gSetCompositingMode(appearance as usize, mode as usize) }
    }
}
fn set_fog(_ctx: &mut NativeContext<'_>, appearance: i32, fog: i32) {
    if appearance != 0 {
        unsafe { ffi::m3gSetFog(appearance as usize, fog as usize) }
    }
}
fn set_material(ctx: &mut NativeContext<'_>, appearance: i32, material: i32) {
    if appearance != 0 {
        unsafe { ffi::m3gSetMaterial(appearance as usize, material as usize) };
        if let Some(data) = ctx
            .vm
            .m3g_resources
            .appearances
            .get_mut(&(appearance as u32))
        {
            data.material = material as u32;
        }
    }
}
fn set_polygon_mode(_ctx: &mut NativeContext<'_>, appearance: i32, mode: i32) {
    if appearance != 0 {
        unsafe { ffi::m3gSetPolygonMode(appearance as usize, mode as usize) }
    }
}
fn set_texture(_ctx: &mut NativeContext<'_>, appearance: i32, unit: i32, texture: i32) {
    if appearance != 0 {
        unsafe { ffi::m3gSetTexture(appearance as usize, unit, texture as usize) }
    }
}
fn set_layer(_ctx: &mut NativeContext<'_>, appearance: i32, layer: i32) {
    if appearance != 0 {
        unsafe { ffi::m3gSetLayer(appearance as usize, layer) }
    }
}
fn get_layer(_ctx: &mut NativeContext<'_>, appearance: i32) -> i32 {
    if appearance != 0 {
        unsafe { ffi::m3gGetLayer(appearance as usize) }
    } else {
        0
    }
}
fn get_compositing_mode(_ctx: &mut NativeContext<'_>, appearance: i32) -> i32 {
    if appearance != 0 {
        (unsafe { ffi::m3gGetCompositingMode(appearance as usize) }) as i32
    } else {
        0
    }
}
fn get_fog(_ctx: &mut NativeContext<'_>, appearance: i32) -> i32 {
    if appearance != 0 {
        (unsafe { ffi::m3gGetFog(appearance as usize) }) as i32
    } else {
        0
    }
}
fn get_material(_ctx: &mut NativeContext<'_>, appearance: i32) -> i32 {
    if appearance != 0 {
        (unsafe { ffi::m3gGetMaterial(appearance as usize) }) as i32
    } else {
        0
    }
}
fn get_polygon_mode(_ctx: &mut NativeContext<'_>, appearance: i32) -> i32 {
    if appearance != 0 {
        (unsafe { ffi::m3gGetPolygonMode(appearance as usize) }) as i32
    } else {
        0
    }
}
fn get_texture(_ctx: &mut NativeContext<'_>, appearance: i32, unit: i32) -> i32 {
    if appearance != 0 {
        (unsafe { ffi::m3gGetTexture(appearance as usize, unit) }) as i32
    } else {
        0
    }
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.Appearance._ctor(I)I" => ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._setCompositingMode(II)V" => set_compositing_mode(appearance: i32, mode: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._setFog(II)V" => set_fog(appearance: i32, fog: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._setMaterial(II)V" => set_material(appearance: i32, material: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._setPolygonMode(II)V" => set_polygon_mode(appearance: i32, mode: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._setTexture(III)V" => set_texture(appearance: i32, unit: i32, texture: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._setLayer(II)V" => set_layer(appearance: i32, layer: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._getLayer(I)I" => get_layer(appearance: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._getCompositingMode(I)I" => get_compositing_mode(appearance: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._getFog(I)I" => get_fog(appearance: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._getMaterial(I)I" => get_material(appearance: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._getPolygonMode(I)I" => get_polygon_mode(appearance: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Appearance._getTexture(II)I" => get_texture(appearance: i32, unit: i32) -> i32),
    ];
    registry.register_bindings(&bindings);
}
