use crate::interpreter::native::registry::{NativeContext, NativeRegistry};

use super::{array_as_mut_ptr, ffi};

fn add_ref(_: &mut NativeContext<'_>, object: i32) {
    if object != 0 {
        unsafe { ffi::m3gAddRef(object as usize) }
    }
}
fn animate(_: &mut NativeContext<'_>, object: i32, time: i32) -> i32 {
    if object != 0 {
        unsafe { ffi::m3gAnimate(object as usize, time) }
    } else {
        0
    }
}
fn set_user_id(_: &mut NativeContext<'_>, object: i32, user_id: i32) {
    if object != 0 {
        unsafe { ffi::m3gSetUserID(object as usize, user_id) }
    }
}
fn get_user_id(_: &mut NativeContext<'_>, object: i32) -> i32 {
    if object != 0 {
        unsafe { ffi::m3gGetUserID(object as usize) }
    } else {
        0
    }
}
fn add_animation_track(_: &mut NativeContext<'_>, object: i32, track: i32) -> i32 {
    if object != 0 {
        unsafe { ffi::m3gAddAnimationTrack(object as usize, track as usize) }
    } else {
        -1
    }
}
fn remove_animation_track(_: &mut NativeContext<'_>, object: i32, track: i32) {
    if object != 0 {
        unsafe { ffi::m3gRemoveAnimationTrack(object as usize, track as usize) }
    }
}
fn get_animation_track_count(_: &mut NativeContext<'_>, object: i32) -> i32 {
    if object != 0 {
        unsafe { ffi::m3gGetAnimationTrackCount(object as usize) }
    } else {
        0
    }
}
fn get_animation_track(_: &mut NativeContext<'_>, object: i32, index: i32) -> i32 {
    if object != 0 {
        (unsafe { ffi::m3gGetAnimationTrack(object as usize, index) }) as i32
    } else {
        0
    }
}
fn duplicate(_: &mut NativeContext<'_>, object: i32, _references: u32) -> i32 {
    if object == 0 {
        return 0;
    }
    let class_id = unsafe { ffi::m3gGetClass(object as usize) };
    let pair_count = match class_id {
        5 | 8 | 12 | 15 | 16 | 21 | 22 | 28 => {
            unsafe { ffi::m3gGetSubtreeSize(object as usize) }.max(1) as usize
        }
        _ => 1,
    };
    (unsafe { ffi::m3gDuplicate(object as usize, pair_count) }) as i32
}
fn get_references(ctx: &mut NativeContext<'_>, object: i32, references: u32) -> i32 {
    if object == 0 {
        return 0;
    }
    if references != 0 {
        let len = ctx.vm.heap.array_length(references) as i32;
        let output = unsafe {
            std::slice::from_raw_parts_mut(
                array_as_mut_ptr(ctx.vm, references) as *mut u32,
                len as usize,
            )
        };
        unsafe { ffi::m3gGetReferences(object as usize, Some(output)) }
    } else {
        unsafe { ffi::m3gGetReferences(object as usize, None) }
    }
}
fn find(_: &mut NativeContext<'_>, object: i32, user_id: i32) -> i32 {
    if object != 0 {
        (unsafe { ffi::m3gFind(object as usize, user_id) }) as i32
    } else {
        0
    }
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.Object3D._addRef(I)V" => add_ref(object: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Object3D._animate(II)I" => animate(object: i32, time: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Object3D._setUserID(II)V" => set_user_id(object: i32, user_id: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Object3D._getUserID(I)I" => get_user_id(object: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Object3D._addAnimationTrack(II)I" => add_animation_track(object: i32, track: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Object3D._removeAnimationTrack(II)V" => remove_animation_track(object: i32, track: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Object3D._getAnimationTrackCount(I)I" => get_animation_track_count(object: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Object3D._getAnimationTrack(II)I" => get_animation_track(object: i32, index: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Object3D._duplicate(I[I)I" => duplicate(object: i32, references: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Object3D._getReferences(I[I)I" => get_references(object: i32, references: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Object3D._find(II)I" => find(object: i32, user_id: i32) -> i32),
    ];
    registry.register_bindings(&bindings);
}
