use crate::heap::ARRAY_DATA_OFFSET;
use crate::interpreter::native::registry::{NativeContext, NativeRegistry};

use super::ffi;

fn array_ptr(ctx: &NativeContext<'_>, array: u32) -> *const f32 {
    if array == 0 {
        return std::ptr::null();
    }
    let offset = (ARRAY_DATA_OFFSET + array) as usize;
    ctx.vm.heap.data().as_ptr().wrapping_add(offset) as *const f32
}

fn array_mut_ptr(ctx: &mut NativeContext<'_>, array: u32) -> *mut f32 {
    if array == 0 {
        return std::ptr::null_mut();
    }
    let offset = (ARRAY_DATA_OFFSET + array) as usize;
    ctx.vm.heap.data_mut().as_mut_ptr().wrapping_add(offset) as *mut f32
}

fn controller_ctor(_ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    (unsafe { ffi::m3gCreateAnimationController(interface as usize) }) as i32
}
fn controller_set_active_interval(_ctx: &mut NativeContext<'_>, handle: i32, start: i32, end: i32) {
    if handle != 0 {
        unsafe { ffi::m3gSetActiveInterval(handle as usize, start, end) }
    }
}
fn controller_get_active_interval_start(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetActiveIntervalStart(handle as usize) }
    } else {
        0
    }
}
fn controller_get_active_interval_end(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetActiveIntervalEnd(handle as usize) }
    } else {
        0
    }
}
fn controller_set_speed(_ctx: &mut NativeContext<'_>, handle: i32, factor: f32, world_time: i32) {
    if handle != 0 {
        unsafe { ffi::m3gSetSpeed(handle as usize, factor, world_time) }
    }
}
fn controller_get_speed(_ctx: &mut NativeContext<'_>, handle: i32) -> f32 {
    if handle != 0 {
        unsafe { ffi::m3gGetSpeed(handle as usize) }
    } else {
        0.0
    }
}
fn controller_set_position(
    _ctx: &mut NativeContext<'_>,
    handle: i32,
    sequence_time: f32,
    world_time: i32,
) {
    if handle != 0 {
        unsafe { ffi::m3gSetPosition(handle as usize, sequence_time, world_time) }
    }
}
fn controller_get_position(_ctx: &mut NativeContext<'_>, handle: i32, world_time: i32) -> f32 {
    if handle != 0 {
        unsafe { ffi::m3gGetPosition(handle as usize, world_time) }
    } else {
        0.0
    }
}
fn controller_set_weight(_ctx: &mut NativeContext<'_>, handle: i32, weight: f32) {
    if handle != 0 {
        unsafe { ffi::m3gSetWeight(handle as usize, weight) }
    }
}
fn controller_get_weight(_ctx: &mut NativeContext<'_>, handle: i32) -> f32 {
    if handle != 0 {
        unsafe { ffi::m3gGetWeight(handle as usize) }
    } else {
        0.0
    }
}
fn controller_get_ref_world_time(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetRefWorldTime(handle as usize) }
    } else {
        0
    }
}

fn track_ctor(_ctx: &mut NativeContext<'_>, interface: i32, sequence: i32, property: i32) -> i32 {
    (unsafe { ffi::m3gCreateAnimationTrack(interface as usize, sequence as usize, property) })
        as i32
}
fn track_get_controller(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        (unsafe { ffi::m3gGetController(handle as usize) }) as i32
    } else {
        0
    }
}
fn track_get_sequence(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        (unsafe { ffi::m3gGetSequence(handle as usize) }) as i32
    } else {
        0
    }
}
fn track_get_target_property(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetTargetProperty(handle as usize) }
    } else {
        0
    }
}
fn track_set_controller(_ctx: &mut NativeContext<'_>, handle: i32, controller: i32) {
    if handle != 0 {
        unsafe { ffi::m3gSetController(handle as usize, controller as usize) }
    }
}

fn keyframe_ctor(
    _ctx: &mut NativeContext<'_>,
    interface: i32,
    keyframes: i32,
    components: i32,
    interpolation: i32,
) -> i32 {
    (unsafe {
        ffi::m3gCreateKeyframeSequence(interface as usize, keyframes, components, interpolation)
    }) as i32
}
fn keyframe_set_keyframe(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    index: i32,
    time: i32,
    values: u32,
) {
    if handle != 0 {
        let len = if values != 0 {
            ctx.vm.heap.array_length(values) as i32
        } else {
            0
        };
        unsafe { ffi::m3gSetKeyframe(handle as usize, index, time, len, array_ptr(ctx, values)) }
    }
}
fn keyframe_set_valid_range(_ctx: &mut NativeContext<'_>, handle: i32, first: i32, last: i32) {
    if handle != 0 {
        unsafe { ffi::m3gSetValidRange(handle as usize, first, last) }
    }
}
fn keyframe_set_duration(_ctx: &mut NativeContext<'_>, handle: i32, duration: i32) {
    if handle != 0 {
        unsafe { ffi::m3gSetDuration(handle as usize, duration) }
    }
}
fn keyframe_get_duration(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetDuration(handle as usize) }
    } else {
        0
    }
}
fn keyframe_set_repeat_mode(_ctx: &mut NativeContext<'_>, handle: i32, mode: i32) {
    if handle != 0 {
        unsafe { ffi::m3gSetRepeatMode(handle as usize, mode) }
    }
}
fn keyframe_get_repeat_mode(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetRepeatMode(handle as usize) }
    } else {
        0
    }
}
fn keyframe_get_component_count(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetComponentCount(handle as usize) }
    } else {
        0
    }
}
fn keyframe_get_interpolation_type(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetInterpolationType(handle as usize) }
    } else {
        0
    }
}
fn keyframe_get_keyframe(ctx: &mut NativeContext<'_>, handle: i32, index: i32, values: u32) -> i32 {
    if handle != 0
        && values != 0
        && ctx.vm.heap.array_length(values)
            < unsafe { ffi::m3gGetComponentCount(handle as usize) } as u32
    {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return 0;
    }
    if handle != 0 {
        unsafe { ffi::m3gGetKeyframe(handle as usize, index, array_mut_ptr(ctx, values)) }
    } else {
        0
    }
}
fn keyframe_get_keyframe_count(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        unsafe { ffi::m3gGetKeyframeCount(handle as usize) }
    } else {
        0
    }
}
fn keyframe_get_valid_range_first(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    let mut first = 0;
    let mut last = 0;
    if handle != 0 {
        unsafe { ffi::m3gGetValidRange(handle as usize, &mut first, &mut last) }
    };
    first
}
fn keyframe_get_valid_range_last(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    let mut first = 0;
    let mut last = 0;
    if handle != 0 {
        unsafe { ffi::m3gGetValidRange(handle as usize, &mut first, &mut last) }
    };
    last
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._ctor(I)I" => controller_ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._setActiveInterval(III)V" => controller_set_active_interval(handle: i32, start: i32, end: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._getActiveIntervalStart(I)I" => controller_get_active_interval_start(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._getActiveIntervalEnd(I)I" => controller_get_active_interval_end(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._setSpeed(IFI)V" => controller_set_speed(handle: i32, factor: f32, world_time: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._getSpeed(I)F" => controller_get_speed(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._setPosition(IFI)V" => controller_set_position(handle: i32, sequence_time: f32, world_time: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._getPosition(II)F" => controller_get_position(handle: i32, world_time: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._setWeight(IF)V" => controller_set_weight(handle: i32, weight: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._getWeight(I)F" => controller_get_weight(handle: i32) -> f32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationController._getRefWorldTime(I)I" => controller_get_ref_world_time(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationTrack._ctor(III)I" => track_ctor(interface: i32, sequence: i32, property: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationTrack._getController(I)I" => track_get_controller(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationTrack._getSequence(I)I" => track_get_sequence(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationTrack._getTargetProperty(I)I" => track_get_target_property(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.AnimationTrack._setController(II)V" => track_set_controller(handle: i32, controller: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._ctor(IIII)I" => keyframe_ctor(interface: i32, keyframes: i32, components: i32, interpolation: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._setKeyframe(III[F)V" => keyframe_set_keyframe(handle: i32, index: i32, time: i32, values: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._setValidRange(III)V" => keyframe_set_valid_range(handle: i32, first: i32, last: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._setDuration(II)V" => keyframe_set_duration(handle: i32, duration: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._getDuration(I)I" => keyframe_get_duration(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._setRepeatMode(II)V" => keyframe_set_repeat_mode(handle: i32, mode: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._getRepeatMode(I)I" => keyframe_get_repeat_mode(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._getComponentCount(I)I" => keyframe_get_component_count(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._getInterpolationType(I)I" => keyframe_get_interpolation_type(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._getKeyframe(II[F)I" => keyframe_get_keyframe(handle: i32, index: i32, values: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._getKeyframeCount(I)I" => keyframe_get_keyframe_count(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._getValidRangeFirst(I)I" => keyframe_get_valid_range_first(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.KeyframeSequence._getValidRangeLast(I)I" => keyframe_get_valid_range_last(handle: i32) -> i32),
    ];
    registry.register_bindings(&bindings);
}
