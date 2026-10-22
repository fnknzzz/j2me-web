use crate::interpreter::native::registry::{NativeContext, NativeRegistry};
use crate::vm::cmd;

use super::{array_as_mut_ptr, ffi};

fn world_ctor(ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    let handle = unsafe { ffi::m3gCreateWorld(interface as usize) };
    ctx.vm.m3g_resources.create_world(handle);
    handle as i32
}
fn world_set_active_camera(ctx: &mut NativeContext<'_>, handle: i32, camera: i32) {
    unsafe { ffi::m3gSetActiveCamera(handle as usize, camera as usize) };
    if let Some(world) = ctx.vm.m3g_resources.nodes.get_mut(&(handle as u32)) {
        world.active_camera = camera as u32;
    }
}
fn world_get_active_camera(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    (unsafe { ffi::m3gGetActiveCamera(handle as usize) }) as i32
}
fn world_set_background(ctx: &mut NativeContext<'_>, handle: i32, background: i32) {
    unsafe { ffi::m3gSetBackground(handle as usize, background as usize) };
    if let Some(world) = ctx.vm.m3g_resources.nodes.get_mut(&(handle as u32)) {
        world.background = background as u32;
    }
}
fn world_get_background(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    (unsafe { ffi::m3gGetBackground(handle as usize) }) as i32
}

fn background_ctor(ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    let h = unsafe { ffi::m3gCreateBackground(interface as usize) };
    let b = ctx.vm.m3g_command_buffer_mut();
    b.push(cmd::M3G_CREATE_BACKGROUND);
    b.extend_from_slice(&(h as u32).to_le_bytes());
    h as i32
}
fn background_set_color(ctx: &mut NativeContext<'_>, h: i32, color: i32) {
    if h != 0 {
        unsafe { ffi::m3gSetBgColor(h as usize, color as u32) };
        let b = ctx.vm.m3g_command_buffer_mut();
        b.push(cmd::M3G_BG_SET_COLOR);
        b.extend_from_slice(&(h as u32).to_le_bytes());
        b.extend_from_slice(&(color as u32).to_le_bytes());
    }
}
fn background_get_color(_: &mut NativeContext<'_>, h: i32) -> i32 {
    if h != 0 {
        unsafe { ffi::m3gGetBgColor(h as usize) as i32 }
    } else {
        0
    }
}
fn background_set_image(_: &mut NativeContext<'_>, h: i32, image: i32) {
    if h != 0 {
        unsafe { ffi::m3gSetBgImage(h as usize, image as usize) }
    }
}
fn background_get_image(_: &mut NativeContext<'_>, h: i32) -> i32 {
    if h != 0 {
        (unsafe { ffi::m3gGetBgImage(h as usize) }) as i32
    } else {
        0
    }
}
fn background_set_image_mode(_: &mut NativeContext<'_>, h: i32, x: i32, y: i32) {
    if h != 0 {
        unsafe { ffi::m3gSetBgMode(h as usize, x, y) }
    }
}
fn background_get_image_mode(_: &mut NativeContext<'_>, h: i32, which: i32) -> i32 {
    if h != 0 {
        unsafe { ffi::m3gGetBgMode(h as usize, which) }
    } else {
        0
    }
}
fn background_enable(ctx: &mut NativeContext<'_>, h: i32, which: i32, enable: bool) {
    if h != 0 {
        unsafe { ffi::m3gSetBgEnable(h as usize, which, enable as i32) };
        let b = ctx.vm.m3g_command_buffer_mut();
        b.push(cmd::M3G_BG_SET_ENABLE);
        b.extend_from_slice(&(h as u32).to_le_bytes());
        b.extend_from_slice(&(which as u32).to_le_bytes());
        b.extend_from_slice(&(enable as u32).to_le_bytes());
    }
}
fn background_is_enabled(_: &mut NativeContext<'_>, h: i32, which: i32) -> bool {
    if h != 0 {
        unsafe { ffi::m3gIsBgEnabled(h as usize, which) != 0 }
    } else {
        true
    }
}
fn background_set_crop(_: &mut NativeContext<'_>, h: i32, x: i32, y: i32, w: i32, height: i32) {
    if h != 0 {
        unsafe { ffi::m3gSetBgCrop(h as usize, x, y, w, height) }
    }
}
fn background_get_crop(_: &mut NativeContext<'_>, h: i32, which: i32) -> i32 {
    if h != 0 {
        unsafe { ffi::m3gGetBgCrop(h as usize, which + 2) }
    } else {
        0
    }
}

fn group_ctor(ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    let handle = unsafe { ffi::m3gCreateGroup(interface as usize) };
    ctx.vm.m3g_resources.create_group(handle);
    handle as i32
}
fn group_add_child(ctx: &mut NativeContext<'_>, handle: i32, child: i32) {
    unsafe { ffi::m3gAddChild(handle as usize, child as usize) };
    ctx.vm.m3g_resources.add_child(handle as u32, child as u32);
}
fn group_remove_child(ctx: &mut NativeContext<'_>, handle: i32, child: i32) {
    unsafe { ffi::m3gRemoveChild(handle as usize, child as usize) };
    ctx.vm
        .m3g_resources
        .remove_child(handle as u32, child as u32);
}
fn group_get_child_count(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    unsafe { ffi::m3gGetChildCount(handle as usize) }
}
fn group_get_child(ctx: &mut NativeContext<'_>, handle: i32, index: i32) -> i32 {
    let count = unsafe { ffi::m3gGetChildCount(handle as usize) };
    if index < 0 || index >= count {
        ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
        0
    } else {
        (unsafe { ffi::m3gGetChild(handle as usize, index) }) as i32
    }
}
fn group_pick_3d(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    mask: i32,
    ray: u32,
    result: u32,
) -> i32 {
    if handle != 0 && ray != 0 && result != 0 {
        unsafe {
            ffi::m3gPick3D(
                handle as usize,
                mask,
                array_as_mut_ptr(ctx.vm, ray) as *mut f32,
                array_as_mut_ptr(ctx.vm, result) as *mut f32,
            ) as i32
        }
    } else {
        0
    }
}
fn group_pick_2d(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    mask: i32,
    x: f32,
    y: f32,
    camera: i32,
    result: u32,
) -> i32 {
    if handle != 0 && result != 0 {
        unsafe {
            ffi::m3gPick2D(
                handle as usize,
                mask,
                x,
                y,
                camera as usize,
                array_as_mut_ptr(ctx.vm, result) as *mut f32,
            ) as i32
        }
    } else {
        0
    }
}

fn node_get_transform_to(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    target: i32,
    transform: u32,
) -> bool {
    unsafe {
        ffi::m3gGetTransformTo(
            handle as usize,
            target as usize,
            array_as_mut_ptr(ctx.vm, transform),
        ) != 0
    }
}
fn node_set_alignment(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    z_ref: i32,
    z_target: i32,
    y_ref: i32,
    y_target: i32,
) {
    unsafe {
        ffi::m3gSetAlignment(
            handle as usize,
            z_ref as usize,
            z_target,
            y_ref as usize,
            y_target,
        )
    };
    if let Some(node) = ctx.vm.m3g_resources.nodes.get_mut(&(handle as u32)) {
        node.z_ref = z_ref as u32;
        node.z_target = z_target;
        node.y_ref = y_ref as u32;
        node.y_target = y_target;
        node.alignment_explicit = true;
    }
}
fn node_align(_: &mut NativeContext<'_>, handle: i32, reference: i32) {
    unsafe { ffi::m3gAlignNode(handle as usize, reference as usize) }
}
fn node_set_alpha_factor(_: &mut NativeContext<'_>, handle: i32, alpha: f32) {
    if handle != 0 {
        unsafe { ffi::m3gSetAlphaFactor(handle as usize, alpha) }
    }
}
fn node_get_alpha_factor(_: &mut NativeContext<'_>, handle: i32) -> f32 {
    if handle != 0 {
        unsafe { ffi::m3gGetAlphaFactor(handle as usize) }
    } else {
        1.0
    }
}
fn node_enable(ctx: &mut NativeContext<'_>, handle: i32, which: i32, enable: bool) {
    unsafe { ffi::m3gEnable(handle as usize, java_setget_to_m3g(which), enable as i32) };
    if which == 0 {
        if let Some(node) = ctx.vm.m3g_resources.nodes.get_mut(&(handle as u32)) {
            node.rendering_enable = enable;
        }
    }
}
fn node_is_enabled(_: &mut NativeContext<'_>, handle: i32, which: i32) -> bool {
    unsafe { ffi::m3gIsEnabled(handle as usize, java_setget_to_m3g(which)) != 0 }
}

/// Java uses 0=rendering and 1=picking, while the C core uses 2 and 3.
fn java_setget_to_m3g(which: i32) -> i32 {
    match which {
        0 => 2,
        1 => 3,
        other => other,
    }
}

fn node_set_scope(ctx: &mut NativeContext<'_>, handle: i32, scope: i32) {
    unsafe { ffi::m3gSetScope(handle as usize, scope) };
    if let Some(node) = ctx.vm.m3g_resources.nodes.get_mut(&(handle as u32)) {
        node.scope = scope;
    }
}
fn node_get_scope(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    unsafe { ffi::m3gGetScope(handle as usize) }
}
fn node_get_subtree_size(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetSubtreeSize(handle as usize) }
    } else {
        0
    }
}
fn node_get_parent(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        (unsafe { ffi::m3gGetParent(handle as usize) }) as i32
    } else {
        0
    }
}
fn node_get_z_ref(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        (unsafe { ffi::m3gGetZRef(handle as usize) }) as i32
    } else {
        0
    }
}
fn node_get_y_ref(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        (unsafe { ffi::m3gGetYRef(handle as usize) }) as i32
    } else {
        0
    }
}
fn node_get_alignment_target(_: &mut NativeContext<'_>, handle: i32, axis: i32) -> i32 {
    unsafe { ffi::m3gGetAlignmentTarget(handle as usize, axis) }
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.World._ctor(I)I" => world_ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.World._setActiveCamera(II)V" => world_set_active_camera(handle: i32, camera: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.World._getActiveCamera(I)I" => world_get_active_camera(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.World._setBackground(II)V" => world_set_background(handle: i32, background: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.World._getBackground(I)I" => world_get_background(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Background._ctor(I)I"=>background_ctor(interface:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Background._setColor(II)V"=>background_set_color(handle:i32,color:i32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Background._getColor(I)I"=>background_get_color(handle:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Background._setImage(II)V"=>background_set_image(handle:i32,image:i32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Background._getImage(I)I"=>background_get_image(handle:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Background._setImageMode(III)V"=>background_set_image_mode(handle:i32,x:i32,y:i32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Background._getImageMode(II)I"=>background_get_image_mode(handle:i32,which:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Background._enable(IIZ)V"=>background_enable(handle:i32,which:i32,enable:bool)->()),
        crate::native_bind!(static "javax.microedition.m3g.Background._isEnabled(II)Z"=>background_is_enabled(handle:i32,which:i32)->bool),
        crate::native_bind!(static "javax.microedition.m3g.Background._setCrop(IIIII)V"=>background_set_crop(handle:i32,x:i32,y:i32,width:i32,height:i32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Background._getCrop(II)I"=>background_get_crop(handle:i32,which:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Group._ctor(I)I" => group_ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Group._addChild(II)V" => group_add_child(handle: i32, child: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Group._removeChild(II)V" => group_remove_child(handle: i32, child: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Group._getChildCount(I)I" => group_get_child_count(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Group._getChild(II)I" => group_get_child(handle: i32, index: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Group._pick3D(II[F[F)I" => group_pick_3d(handle: i32, mask: i32, ray: u32, result: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Group._pick2D(IIFFI[F)I" => group_pick_2d(handle: i32, mask: i32, x: f32, y: f32, camera: i32, result: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Node._getTransformTo(II[B)Z" => node_get_transform_to(handle: i32, target: i32, transform: u32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.Node._setAlignment(IIIII)V" => node_set_alignment(handle: i32, z_ref: i32, z_target: i32, y_ref: i32, y_target: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Node._align(II)V" => node_align(handle: i32, reference: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Node._setAlphaFactor(IF)V" => node_set_alpha_factor(handle: i32, alpha: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Node._getAlphaFactor(I)F" => node_get_alpha_factor(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.Node._enable(IIZ)V" => node_enable(handle: i32, which: i32, enable: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Node._isEnabled(II)Z" => node_is_enabled(handle: i32, which: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.Node._setScope(II)V" => node_set_scope(handle: i32, scope: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Node._getScope(I)I" => node_get_scope(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Node._getSubtreeSize(I)I" => node_get_subtree_size(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Node._getParent(I)I" => node_get_parent(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Node._getZRef(I)I" => node_get_z_ref(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Node._getYRef(I)I" => node_get_y_ref(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Node._getAlignmentTarget(II)I" => node_get_alignment_target(handle: i32, axis: i32) -> i32),
    ];
    registry.register_bindings(&bindings);
}
