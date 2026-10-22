use crate::heap::NULL;
use crate::scheduler::virtual_thread::YieldReason;
use crate::vm::SharedVm;

use super::registry::{NativeContext, NativeRegistry, NativeResult};

fn arraycopy(vm: &mut SharedVm, src: u32, src_pos: u32, dest: u32, dest_pos: u32, length: u32) {
    // Zero-length copy: no-op regardless of other arguments
    if length == 0 {
        return;
    }
    // Null src or dest: silently do nothing (jsjvm convention)
    if src == NULL || dest == NULL {
        return;
    }
    let src_len = vm.heap.array_length(src);
    let dest_len = vm.heap.array_length(dest);
    let src_end = (src_pos as u64) + (length as u64);
    let dest_end = (dest_pos as u64) + (length as u64);
    if (src_pos as i32) < 0
        || (dest_pos as i32) < 0
        || (length as i32) < 0
        || src_end > src_len as u64
        || dest_end > dest_len as u64
    {
        vm.pending_exception_str = Some("java.lang.ArrayIndexOutOfBoundsException".to_string());
        return;
    }
    let class_id = vm.heap.get_class_id(src);
    let bpe = vm.class_repo.get_array_bpe(class_id);
    let bpe = if bpe == 0 { 4 } else { bpe as u32 };
    // Vec::copy_within has memmove semantics, including overlapping ranges.
    if !vm
        .heap
        .copy_array_elements(src, src_pos, dest, dest_pos, length, bpe)
    {
        vm.pending_exception_str = Some("java.lang.ArrayIndexOutOfBoundsException".to_string());
    }
}

// --- Individual native handler functions for ID-based dispatch ---

fn object_hash_code(ctx: &mut NativeContext<'_>, this: u32) -> i32 {
    ctx.vm.heap.get_hash_code(this) as i32
}
fn object_get_class(ctx: &mut NativeContext<'_>, this_ref: u32) -> u32 {
    let class_name = ctx
        .vm
        .get_class_name_of_object(this_ref)
        .unwrap_or("java.lang.Object")
        .to_string();
    if let Some(&cached) = ctx.vm.class_objects_by_name.get(&class_name) {
        cached
    } else {
        let class_obj = ctx.vm.new_object("java.lang.Class");
        ctx.vm.class_objects.insert(class_obj, class_name.clone());
        ctx.vm.class_objects_by_name.insert(class_name, class_obj);
        class_obj
    }
}
fn object_notify(ctx: &mut NativeContext<'_>, this: u32) {
    let monitor = ctx.vm.monitors.entry(this).or_default();
    let woke = monitor.notify();
    if let Some((tid, saved_recursion)) = woke {
        ctx.vm
            .pending_notify_wakeups
            .push((this, tid, saved_recursion));
    }
}
fn object_notify_all(ctx: &mut NativeContext<'_>, this: u32) {
    let monitor = ctx.vm.monitors.entry(this).or_default();
    let wakeups = monitor.notify_all();
    for (tid, saved_recursion) in wakeups {
        ctx.vm
            .pending_notify_wakeups
            .push((this, tid, saved_recursion));
    }
}
fn system_arraycopy(
    ctx: &mut NativeContext<'_>,
    src: u32,
    src_pos: i32,
    dest: u32,
    dest_pos: i32,
    length: i32,
) {
    arraycopy(
        ctx.vm,
        src,
        src_pos as u32,
        dest,
        dest_pos as u32,
        length as u32,
    );
}
fn system_get_property(ctx: &mut NativeContext<'_>, key: u32) -> u32 {
    let key = ctx.vm.get_string_value(key).unwrap_or_default();
    ctx.vm
        .system_properties
        .get(&key)
        .cloned()
        .map_or(NULL, |value| ctx.vm.create_string(&value))
}
fn system_current_time_millis(_ctx: &mut NativeContext<'_>) -> i64 {
    crate::call_host_current_time_millis()
}
fn system_identity_hash_code(ctx: &mut NativeContext<'_>, obj: u32) -> i32 {
    if obj == NULL {
        0
    } else {
        ctx.vm.heap.get_hash_code(obj) as i32
    }
}
fn runtime_exit(_ctx: &mut NativeContext<'_>, _this: u32, _code: i32) -> NativeResult<()> {
    NativeResult::Yield(YieldReason::Exit)
}
fn reference_get(ctx: &mut NativeContext<'_>, this: u32) -> u32 {
    ctx.vm.reference_map.get(&this).copied().unwrap_or(NULL)
}
fn reference_clear(ctx: &mut NativeContext<'_>, this: u32) {
    ctx.vm.reference_map.insert(this, NULL);
}
fn weak_reference_initialize(ctx: &mut NativeContext<'_>, this: u32, referent: u32) {
    ctx.vm.reference_map.insert(this, referent);
}
fn weak_reference_finalize(_ctx: &mut NativeContext<'_>, _this: u32) {}
fn string_intern(ctx: &mut NativeContext<'_>, this: u32) -> u32 {
    let value = ctx.vm.get_string_value(this).unwrap_or_default();
    if let Some(existing) = ctx.vm.heap.get_interned_string_dynamic(&value) {
        existing
    } else {
        ctx.vm.heap.intern_string_dynamic(&value, this);
        this
    }
}
fn throwable_fill_stack_trace(ctx: &mut NativeContext<'_>, this: u32) {
    let mut frames: Vec<(String, String)> = Vec::new();
    let mut fp = ctx.stack.fp;
    loop {
        let caller_fp = ctx.stack.data[fp + 1] as usize;
        if caller_fp == fp || caller_fp == 0 {
            break;
        }
        fp = caller_fp;
        let method_id = ctx.stack.data[fp + 2] & 0x0FFF_FFFF;
        if let Some(m) = ctx.vm.class_repo.get_method(method_id) {
            if ctx.vm.class_repo.get_string(m.name_id) != "<init>"
                && ctx.vm.class_repo.get_string(m.name_id) != "<clinit>"
                && ctx.vm.class_repo.get_string(m.name_id) != "fillInStackTrace"
            {
                frames.push((
                    ctx.vm
                        .class_repo
                        .get_class_name_by_id(m.class_id)
                        .unwrap_or("")
                        .to_string(),
                    ctx.vm.class_repo.get_string(m.name_id).to_string(),
                ));
            }
        }
    }
    frames.reverse();
    ctx.vm.throwable_backtraces.insert(this, frames);
}
fn runtime_free_memory(ctx: &mut NativeContext<'_>, _this: u32) -> i64 {
    ctx.vm.free_memory() as i64
}
fn runtime_total_memory(ctx: &mut NativeContext<'_>, _this: u32) -> i64 {
    ctx.vm.total_memory() as i64
}
fn runtime_gc(ctx: &mut NativeContext<'_>, _this: u32) {
    ctx.vm.force_gc = true;
}
fn thread_current(ctx: &mut NativeContext<'_>) -> u32 {
    if let Some(&obj) = ctx.vm.thread_id_to_object.get(&ctx.thread_id) {
        obj
    } else {
        if ctx.vm.main_thread_obj == NULL {
            ctx.vm.main_thread_obj = ctx.vm.new_object("java.lang.Thread");
            if let Some(off) = ctx
                .vm
                .class_repo
                .find_field_offset("java.lang.Thread", "priority")
            {
                ctx.vm.heap.write_u32(ctx.vm.main_thread_obj + off, 5);
            }
        }
        ctx.vm.main_thread_obj
    }
}
fn thread_active_count(_ctx: &mut NativeContext<'_>) -> i32 {
    1
}
fn thread_yield(_ctx: &mut NativeContext<'_>) -> NativeResult<()> {
    NativeResult::Yield(YieldReason::TimeSlice)
}
fn thread_set_priority(_ctx: &mut NativeContext<'_>, _this: u32, _old: i32, _new: i32) {}
fn thread_start(ctx: &mut NativeContext<'_>, this: u32) {
    if ctx.vm.alive_threads.contains(&this) {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalThreadStateException".to_string());
    } else {
        ctx.vm.alive_threads.insert(this);
        ctx.vm.pending_thread_spawns.push(this);
    }
}
fn thread_is_alive(ctx: &mut NativeContext<'_>, this: u32) -> bool {
    ctx.vm.alive_threads.contains(&this)
}
fn thread_interrupt(ctx: &mut NativeContext<'_>, this: u32) {
    ctx.vm.interrupted_threads.insert(this);
}
fn thread_interrupted(ctx: &mut NativeContext<'_>) -> bool {
    let current = ctx
        .vm
        .thread_id_to_object
        .get(&ctx.thread_id)
        .copied()
        .unwrap_or(ctx.vm.main_thread_obj);
    ctx.vm.interrupted_threads.remove(&current)
}
fn object_wait(ctx: &mut NativeContext<'_>, this: u32, timeout_ms: i64) -> NativeResult<()> {
    let monitor = ctx.vm.monitors.entry(this).or_default();
    let (saved_recursion, consumed_pending) = monitor.wait(ctx.thread_id);
    if consumed_pending {
        return NativeResult::Yield(YieldReason::Wait {
            obj_id: this,
            timeout: Some(0),
            saved_recursion,
        });
    }
    let deadline = if timeout_ms > 0 {
        let now = ctx.vm.effective_now();
        let speed = ctx.vm.speed_multiplier;
        let adjusted = (timeout_ms as f64 / speed).max(1.0) as u64;
        Some(now + adjusted)
    } else {
        None
    };
    NativeResult::Yield(YieldReason::Wait {
        obj_id: this,
        timeout: deadline,
        saved_recursion,
    })
}
fn thread_sleep(ctx: &mut NativeContext<'_>, millis: i64) -> NativeResult<()> {
    if millis > 0 {
        let now = ctx.vm.effective_now();
        let speed = ctx.vm.speed_multiplier;
        let adjusted = (millis as f64 / speed).max(1.0) as u64;
        let deadline = now + adjusted;
        return NativeResult::Yield(YieldReason::Sleep {
            deadline_ms: deadline,
        });
    }
    NativeResult::Ready(())
}
fn throwable_obtain_back_trace(ctx: &mut NativeContext<'_>, this: u32) -> u32 {
    if let Some(frames) = ctx.vm.throwable_backtraces.get(&this).cloned() {
        if frames.is_empty() {
            return NULL;
        } else {
            let len = frames.len() as u32;
            let obj_arr_class = ctx
                .vm
                .class_repo
                .get_class_id("[Ljava.lang.Object;")
                .unwrap_or_else(|| {
                    ctx.vm
                        .class_repo
                        .register_array_class("[Ljava.lang.Object;", 4)
                });
            let int_arr_class = ctx
                .vm
                .class_repo
                .get_class_id("[I")
                .unwrap_or_else(|| ctx.vm.class_repo.register_array_class("[I", 4));
            let class_names_arr = ctx.vm.heap.alloc_array_typed(obj_arr_class, len, 4, false);
            let method_names_arr = ctx.vm.heap.alloc_array_typed(obj_arr_class, len, 4, false);
            let offsets_arr = ctx.vm.heap.alloc_array_typed(int_arr_class, len, 4, true);
            for (i, (cn, mn)) in frames.iter().enumerate() {
                let cn_str = ctx.vm.create_string(cn);
                ctx.vm
                    .heap
                    .array_write_u32(class_names_arr, i as u32, cn_str);
                let mn_str = ctx.vm.create_string(mn);
                ctx.vm
                    .heap
                    .array_write_u32(method_names_arr, i as u32, mn_str);
                ctx.vm.heap.array_write_u32(offsets_arr, i as u32, 0);
            }
            let outer = ctx.vm.heap.alloc_array_typed(obj_arr_class, 3, 4, false);
            ctx.vm.heap.array_write_u32(outer, 0, class_names_arr);
            ctx.vm.heap.array_write_u32(outer, 1, method_names_arr);
            ctx.vm.heap.array_write_u32(outer, 2, offsets_arr);
            outer
        }
    } else {
        NULL
    }
}
fn system_set_property(ctx: &mut NativeContext<'_>, key: u32, value: u32) -> u32 {
    let key = ctx.vm.get_string_value(key).unwrap_or_default();
    let value = ctx.vm.get_string_value(value).unwrap_or_default();
    ctx.vm
        .system_properties
        .insert(key, value)
        .map_or(NULL, |old| ctx.vm.create_string(&old))
}
fn class_for_name(ctx: &mut NativeContext<'_>, name_ref: u32) -> u32 {
    let class_name = ctx
        .vm
        .get_string_value(name_ref)
        .unwrap_or_default()
        .replace('/', ".");
    let class_id = ctx.vm.class_repo.get_class_id(&class_name).unwrap_or(0);
    if class_id != 0 {
        if let Some(&cached) = ctx.vm.class_objects_by_name.get(&class_name) {
            cached
        } else {
            let class_obj = ctx.vm.new_object("java.lang.Class");
            ctx.vm.class_objects.insert(class_obj, class_name.clone());
            ctx.vm.class_objects_by_name.insert(class_name, class_obj);
            class_obj
        }
    } else {
        ctx.vm.pending_exception_str =
            Some(format!("java.lang.ClassNotFoundException ({})", class_name));
        NULL
    }
}
fn class_get_name(ctx: &mut NativeContext<'_>, this: u32) -> u32 {
    ctx.vm
        .create_string(&ctx.vm.class_objects.get(&this).cloned().unwrap_or_default())
}
fn class_new_instance(ctx: &mut NativeContext<'_>, this: u32) -> u32 {
    if let Some(class_name) = ctx.vm.class_objects.get(&this).cloned() {
        let is_interface_or_abstract = ctx
            .vm
            .class_repo
            .get_class(&class_name)
            .map_or(false, |c| c.is_interface() || c.is_abstract());
        if is_interface_or_abstract {
            ctx.vm.pending_exception_str = Some("java.lang.InstantiationException".to_string());
            NULL
        } else {
            ctx.vm.new_object(&class_name)
        }
    } else {
        NULL
    }
}
fn class_is_interface(ctx: &mut NativeContext<'_>, this: u32) -> bool {
    ctx.vm
        .class_objects
        .get(&this)
        .and_then(|name| ctx.vm.class_repo.get_class(name))
        .map_or(false, |c| c.is_interface())
}
fn class_is_assignable_from(ctx: &mut NativeContext<'_>, this: u32, class: u32) -> bool {
    match (
        ctx.vm.class_objects.get(&this),
        ctx.vm.class_objects.get(&class),
    ) {
        (Some(this_name), Some(cls_name)) => ctx.vm.class_repo.is_assignable(cls_name, this_name),
        _ => false,
    }
}
fn class_get_primitive_class(ctx: &mut NativeContext<'_>, name: u32) -> u32 {
    let prim_name = ctx.vm.get_string_value(name).unwrap_or_default();
    if let Some(&cached) = ctx.vm.class_objects_by_name.get(&prim_name) {
        cached
    } else {
        let class_obj = ctx.vm.new_object("java.lang.Class");
        ctx.vm.class_objects.insert(class_obj, prim_name.clone());
        ctx.vm.class_objects_by_name.insert(prim_name, class_obj);
        class_obj
    }
}

pub fn register(registry: &mut NativeRegistry) {
    registry.register_bindings(&[
        crate::native_bind!(instance "java.lang.Object.hashCode()I" => object_hash_code(this: u32) -> i32),
        crate::native_bind!(instance "java.lang.Object.getClass()Ljava/lang/Class;" => object_get_class(this: u32) -> u32),
        crate::native_bind!(instance "java.lang.Object.notify()V" => object_notify(this: u32) -> ()),
        crate::native_bind!(instance "java.lang.Object.notifyAll()V" => object_notify_all(this: u32) -> ()),
        crate::native_bind!(yielding instance "java.lang.Object.wait(J)V" => object_wait(this: u32, timeout_ms: i64) -> ()),
        crate::native_bind!(static "java.lang.System.arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V" => system_arraycopy(src: u32, src_pos: i32, dest: u32, dest_pos: i32, length: i32) -> ()),
        crate::native_bind!(static "java.lang.System.getProperty0(Ljava/lang/String;)Ljava/lang/String;" => system_get_property(key: u32) -> u32),
        crate::native_bind!(static "java.lang.System.setProperty0(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;" => system_set_property(key: u32, value: u32) -> u32),
        crate::native_bind!(static "java.lang.System.currentTimeMillis()J" => system_current_time_millis() -> i64),
        crate::native_bind!(static "java.lang.System.identityHashCode(Ljava/lang/Object;)I" => system_identity_hash_code(object: u32) -> i32),
        crate::native_bind!(yielding instance "java.lang.Runtime.exitInternal(I)V" => runtime_exit(this: u32, code: i32) -> ()),
        crate::native_bind!(instance "java.lang.ref.WeakReference.get()Ljava/lang/Object;" => reference_get(this: u32) -> u32),
        crate::native_bind!(instance "java.lang.ref.WeakReference.clear()V" => reference_clear(this: u32) -> ()),
        crate::native_bind!(instance "java.lang.ref.WeakReference.initializeWeakReference(Ljava/lang/Object;)V" => weak_reference_initialize(this: u32, referent: u32) -> ()),
        crate::native_bind!(instance "java.lang.ref.WeakReference.finalize()V" => weak_reference_finalize(this: u32) -> ()),
        crate::native_bind!(instance "java.lang.String.intern()Ljava/lang/String;" => string_intern(this: u32) -> u32),
        crate::native_bind!(instance "java.lang.Throwable.fillInStackTrace()V" => throwable_fill_stack_trace(this: u32) -> ()),
        crate::native_bind!(instance "java.lang.Throwable.obtainBackTrace()Ljava/lang/Object;" => throwable_obtain_back_trace(this: u32) -> u32),
        crate::native_bind!(instance "java.lang.Runtime.freeMemory()J" => runtime_free_memory(this: u32) -> i64),
        crate::native_bind!(instance "java.lang.Runtime.totalMemory()J" => runtime_total_memory(this: u32) -> i64),
        crate::native_bind!(instance "java.lang.Runtime.gc()V" => runtime_gc(this: u32) -> ()),
    ]);
    registry.register_bindings(&[
        crate::native_bind!(static "java.lang.Thread.currentThread()Ljava/lang/Thread;" => thread_current() -> u32),
        crate::native_bind!(static "java.lang.Thread.activeCount()I" => thread_active_count() -> i32),
        crate::native_bind!(yielding static "java.lang.Thread.yield()V" => thread_yield() -> ()),
        crate::native_bind!(yielding static "java.lang.Thread.sleep(J)V" => thread_sleep(millis: i64) -> ()),
        crate::native_bind!(instance "java.lang.Thread.setPriority0(II)V" => thread_set_priority(this: u32, old: i32, new: i32) -> ()),
        crate::native_bind!(instance "java.lang.Thread.start0()V" => thread_start(this: u32) -> ()),
        crate::native_bind!(instance "java.lang.Thread.isAlive()Z" => thread_is_alive(this: u32) -> bool),
        crate::native_bind!(instance "java.lang.Thread.interrupt0()V" => thread_interrupt(this: u32) -> ()),
        crate::native_bind!(static "java.lang.Thread.interrupted()Z" => thread_interrupted() -> bool),
        crate::native_bind!(static "java.lang.Class.forName(Ljava/lang/String;)Ljava/lang/Class;" => class_for_name(name: u32) -> u32),
        crate::native_bind!(instance "java.lang.Class.getName()Ljava/lang/String;" => class_get_name(this: u32) -> u32),
        crate::native_bind!(instance "java.lang.Class.newInstance()Ljava/lang/Object;" => class_new_instance(this: u32) -> u32),
        crate::native_bind!(instance "java.lang.Class.isInterface()Z" => class_is_interface(this: u32) -> bool),
        crate::native_bind!(instance "java.lang.Class.isAssignableFrom(Ljava/lang/Class;)Z" => class_is_assignable_from(this: u32, class: u32) -> bool),
        crate::native_bind!(static "java.lang.Class.getPrimitiveClass(Ljava/lang/String;)Ljava/lang/Class;" => class_get_primitive_class(name: u32) -> u32),
    ]);
}
