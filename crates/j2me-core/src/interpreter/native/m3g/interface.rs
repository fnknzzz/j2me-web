use crate::interpreter::native::registry::{NativeContext, NativeRegistry};
use crate::vm::SharedVm;

use super::ffi;
use super::{
    write_delete_resource_cmd, M3G_RESOURCE_IMAGE_TARGET, M3G_RESOURCE_INDEX_BUFFER,
    M3G_RESOURCE_VERTEX_ARRAY,
};

fn interface_ctor(ctx: &mut NativeContext<'_>) -> i32 {
    let handle = ffi::create_interface() as usize;
    if handle == 0 {
        ctx.vm.pending_exception_str = Some("java.lang.OutOfMemoryError".to_string());
    } else {
        ctx.vm.m3g_interface_handle = handle;
    }
    handle as i32
}

pub(super) fn get_class_id(_ctx: &mut NativeContext<'_>, object: i32) -> i32 {
    unsafe { ffi::m3gGetClass(object as usize) as i32 }
}

fn finalize_interface(ctx: &mut NativeContext<'_>, handle: i32) {
    let handle = handle as usize;
    if handle == 0 {
        return;
    }

    let external_resources = std::mem::take(&mut ctx.vm.m3g_resources.external_resources);
    unsafe {
        for handles in external_resources.into_values() {
            for resource_handle in handles {
                ffi::m3gDeleteRef(resource_handle as usize);
            }
        }
        ffi::m3gDeleteInterface(handle);
    }
    if ctx.vm.m3g_interface_handle == handle {
        ctx.vm.m3g_interface_handle = 0;
    }
    ffi::release_java_handle(handle);
}

fn finalize_object_native(ctx: &mut NativeContext<'_>, handle: i32) {
    finalize_object(ctx.vm, handle as usize);
}

pub fn finalize_object(vm: &mut SharedVm, handle: usize) {
    if handle == 0 {
        return;
    }
    let h = handle as u32;
    if vm.m3g_resources.vertex_arrays.remove(&h).is_some() {
        write_delete_resource_cmd(vm, M3G_RESOURCE_VERTEX_ARRAY, h);
    }
    if vm.m3g_resources.images.remove(&h).is_some() {
        write_delete_resource_cmd(vm, M3G_RESOURCE_IMAGE_TARGET, h);
    }
    vm.m3g_resources
        .render_target_images
        .retain(|context_handle, image_handle| *context_handle != h && *image_handle != h);
    if vm.m3g_resources.pending_image_target_release == Some((h, h))
        || vm
            .m3g_resources
            .pending_image_target_release
            .is_some_and(|(context_handle, image_handle)| context_handle == h || image_handle == h)
    {
        vm.m3g_resources.pending_image_target_release = None;
    }
    if vm.m3g_resources.index_buffers.remove(&h).is_some() {
        write_delete_resource_cmd(vm, M3G_RESOURCE_INDEX_BUFFER, h);
    }
    vm.m3g_resources.vertex_buffers.remove(&h);
    vm.m3g_resources.textures.remove(&h);
    vm.m3g_resources.appearances.remove(&h);
    vm.m3g_resources.materials.remove(&h);
    vm.m3g_resources.nodes.remove(&h);
    unsafe {
        if !ffi::is_shadow_handle(handle) {
            ffi::m3gDeleteObject(handle);
        }
    };
    ffi::release_java_handle(handle);
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.Interface._ctor()I" => interface_ctor() -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Interface._getClassID(I)I" => get_class_id(object: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Platform.finalizeInterface(I)V" => finalize_interface(handle: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Platform._finalizeObject(I)V" => finalize_object_native(handle: i32) -> ()),
    ];
    registry.register_bindings(&bindings);
}
