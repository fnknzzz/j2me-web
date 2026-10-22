use crate::classfile::class_repo::ClassRepository;
use crate::heap::{Heap, NULL, OBJ_HDR_SIZE};
use crate::scheduler::thread_stack::FRAME_HEADER_SIZE;
use crate::scheduler::virtual_thread::VirtualThread;
use crate::vm::{AsyncRequestType, ImageSurfaceRestoreSource, SharedVm};

/// GC statistics returned after a collection cycle
pub struct GcStats {
    pub freed_bytes: u32,
    pub retained_count: u32,
    pub freed_count: u32,
}

/// Run a full mark-sweep GC cycle on the VM.
/// This is safe to call at any interpreter safe-point (all virtual threads paused).
pub fn collect(vm: &mut SharedVm, threads: &[VirtualThread]) -> GcStats {
    let mut roots = Vec::with_capacity(256);

    // 1. Collect roots
    collect_vm_roots(vm, &mut roots);
    collect_stack_roots(threads, &vm.heap, &mut roots);
    collect_static_roots(vm, &mut roots);

    // 2. Mark phase (pass weak_ref_objects so mark skips their referent fields)
    let weak_ref_objects: std::collections::HashSet<u32> =
        vm.reference_map.keys().copied().collect();
    mark(&mut vm.heap, &vm.class_repo, &roots, &weak_ref_objects);

    // 3. Process weak references (before clearing marks)
    process_weak_references(vm);

    // 4. Remove cleared M3G wrapper references from Interface.liveObjects.
    // This runs only after a GC has cleared weak references, never while M3G
    // objects are being constructed.
    prune_dead_m3g_live_objects(vm);

    // 5. Release native M3G peers for wrappers that became unreachable.
    finalize_unreachable_m3g_objects(vm);

    // 6. Sweep phase
    let stats = sweep(&mut vm.heap);

    // 7. Clean up VM-side maps that hold dead object keys
    cleanup_vm_maps(vm);

    stats
}

// ---- Roots enumeration ----

fn collect_vm_roots(vm: &SharedVm, roots: &mut Vec<u32>) {
    let push = |roots: &mut Vec<u32>, addr: u32| {
        if addr != NULL {
            roots.push(addr);
        }
    };

    push(roots, vm.midp_display_ref);
    push(roots, vm.main_thread_obj);
    push(roots, vm.preallocated_oom);

    for &addr in vm.class_objects.keys() {
        push(roots, addr);
    }
    for &addr in vm.class_objects_by_name.values() {
        push(roots, addr);
    }

    // reference_map: keys (WeakReference objects) should NOT be roots —
    // they should be reclaimable when no Java code references them.
    // Values (referents) are weak — handled in process_weak_references.

    for &addr in vm.rms_obj_to_name.keys() {
        push(roots, addr);
    }

    for (&k, listeners) in &vm.rms_listeners {
        push(roots, k);
        for &l in listeners {
            push(roots, l);
        }
    }

    for &addr in vm.thread_object_map.keys() {
        push(roots, addr);
    }
    for &addr in vm.thread_id_to_object.values() {
        push(roots, addr);
    }
    for &addr in &vm.alive_threads {
        push(roots, addr);
    }
    for &addr in &vm.interrupted_threads {
        push(roots, addr);
    }

    if let Some(addr) = vm.pending_exception {
        push(roots, addr);
    }
    for &addr in &vm.pending_thread_spawns {
        push(roots, addr);
    }
    for (&addr, monitor) in &vm.monitors {
        if monitor.is_active() {
            push(roots, addr);
        }
    }

    // Media player listeners (object refs)
    for player in vm.media_players.values() {
        for &l in &player.listeners {
            push(roots, l);
        }
    }

    // Image surface restore sources: keep source ImageData alive so derived
    // images can still reference their source tileset during snapshot save.
    for source in vm.image_surface_restore_sources.values() {
        match source {
            ImageSurfaceRestoreSource::ImmutableCopy {
                source_image_data_ref,
            } => {
                push(roots, *source_image_data_ref);
            }
            ImageSurfaceRestoreSource::ImmutableRegion {
                source_image_data_ref,
                ..
            } => {
                push(roots, *source_image_data_ref);
            }
            _ => {}
        }
    }

    // String pool
    for addr in vm.heap.string_pool_values() {
        push(roots, addr);
    }

    for entry in vm.async_requests.values() {
        match &entry.request.request_type {
            AsyncRequestType::TextInput { text_field_ref, .. } => {
                push(roots, *text_field_ref);
            }
            AsyncRequestType::DateInput { date_field_ref, .. } => {
                push(roots, *date_field_ref);
            }
            _ => {}
        }
    }
}

fn collect_stack_roots(threads: &[VirtualThread], heap: &Heap, roots: &mut Vec<u32>) {
    for thread in threads {
        let stack = &thread.stack;
        if stack.sp == 0 {
            continue;
        }

        let mut fp = stack.fp;
        let mut sp = stack.sp;

        loop {
            // Scan MONITOR_OBJ slot (fp + 3)
            if fp + 3 < stack.data.len() {
                let monitor_obj = stack.data[fp + 3];
                if monitor_obj != 0 && heap.is_valid_heap_ref(monitor_obj) {
                    roots.push(monitor_obj);
                }
            }

            // Determine locals region: [fp - max_locals, fp)
            // We don't have access to max_locals_table here, so we scan
            // from the previous frame's sp to current fp
            let locals_start = if fp > 0 {
                // The locals base is at some point before fp
                // For the bottom frame, locals start at 0
                // For other frames, locals start where the caller's sp was
                // We approximate: scan everything from the caller's operand stack top
                // down to this frame's fp
                let caller_fp = stack.data.get(fp + 1).copied().unwrap_or(0) as usize;
                if caller_fp == 0 && fp > 0 {
                    0
                } else {
                    caller_fp + FRAME_HEADER_SIZE
                }
            } else {
                0
            };

            // Scan locals region [locals_start..fp]
            let scan_start = locals_start.min(fp);
            for i in scan_start..fp {
                if i < stack.data.len() {
                    let val = stack.data[i];
                    if val != 0 && heap.is_valid_heap_ref(val) {
                        roots.push(val);
                    }
                }
            }

            // Scan operand stack [fp + FRAME_HEADER_SIZE .. sp]
            let ops_start = fp + FRAME_HEADER_SIZE;
            for i in ops_start..sp {
                if i < stack.data.len() {
                    let val = stack.data[i];
                    if val != 0 && heap.is_valid_heap_ref(val) {
                        roots.push(val);
                    }
                }
            }

            // Move to caller frame
            let caller_fp = stack.data.get(fp + 1).copied().unwrap_or(0) as usize;
            if caller_fp == fp || (caller_fp == 0 && fp == 0) {
                break;
            }
            sp = fp; // caller's operand stack ended at our locals base
            fp = caller_fp;
            if fp == 0 {
                // Scan the bottom frame too
                for i in 0..sp {
                    if i < stack.data.len() {
                        let val = stack.data[i];
                        if val != 0 && heap.is_valid_heap_ref(val) {
                            roots.push(val);
                        }
                    }
                }
                break;
            }
        }
    }
}

fn collect_static_roots(vm: &SharedVm, roots: &mut Vec<u32>) {
    for (class_id, addr) in vm.static_fields.iter_entries() {
        if addr == NULL {
            continue;
        }
        // The pseudo-object itself must be a root so GC never reclaims it
        roots.push(addr);
        // Static fields use static_ref_bitmap; get it via class name
        let class_name = vm.class_repo.get_class_name_by_id(class_id);
        let static_bitmap = class_name
            .and_then(|n| vm.class_repo.get_static_ref_bitmap(n))
            .unwrap_or(&[]);

        for (byte_idx, &byte) in static_bitmap.iter().enumerate() {
            if byte == 0 {
                continue;
            }
            for bit in 0..8u32 {
                if byte & (1 << bit) != 0 {
                    let slot_index = (byte_idx as u32) * 8 + bit;
                    let field_addr = addr + OBJ_HDR_SIZE + slot_index * 4;
                    let ref_val = vm.heap.read_u32(field_addr);
                    if ref_val != NULL {
                        roots.push(ref_val);
                    }
                }
            }
        }
    }
}

// ---- Mark phase ----

fn mark(
    heap: &mut Heap,
    class_repo: &ClassRepository,
    roots: &[u32],
    weak_ref_objects: &std::collections::HashSet<u32>,
) {
    let mut worklist: Vec<u32> = Vec::with_capacity(roots.len());

    // Deduplicate and validate roots
    for &addr in roots {
        if addr != 0 && heap.is_valid_heap_ref(addr) && !heap.is_marked(addr) {
            heap.set_mark(addr);
            worklist.push(addr);
        }
    }

    while let Some(addr) = worklist.pop() {
        // Atomic objects (primitive arrays) have no references to scan
        if heap.is_atomic_obj(addr) {
            continue;
        }

        if heap.is_array_obj(addr) {
            // Reference array: scan each element
            let length = heap.array_length(addr);
            for i in 0..length {
                let elem = heap.array_read_u32(addr, i);
                if elem != 0 && heap.is_valid_heap_ref(elem) && !heap.is_marked(elem) {
                    heap.set_mark(elem);
                    worklist.push(elem);
                }
            }
        } else {
            // Object: use ref_bitmap for precise scanning
            // Skip field scanning for WeakReference objects — their only ref field
            // is `holder` which points to the referent. The referent must NOT be
            // marked through this path; it will be handled by process_weak_references.
            if weak_ref_objects.contains(&addr) {
                continue;
            }

            let class_id = heap.get_class_id(addr);
            let bitmap = class_repo.get_ref_bitmap_by_id(class_id);

            for (byte_idx, &byte) in bitmap.iter().enumerate() {
                if byte == 0 {
                    continue;
                }
                for bit in 0..8u32 {
                    if byte & (1 << bit) != 0 {
                        let slot_index = (byte_idx as u32) * 8 + bit;
                        let field_addr = addr + OBJ_HDR_SIZE + slot_index * 4;
                        let ref_val = heap.read_u32(field_addr);
                        if ref_val != 0
                            && heap.is_valid_heap_ref(ref_val)
                            && !heap.is_marked(ref_val)
                        {
                            heap.set_mark(ref_val);
                            worklist.push(ref_val);
                        }
                    }
                }
            }
        }
    }
}

// ---- Weak reference processing ----

fn process_weak_references(vm: &mut SharedVm) {
    let mut to_clear = Vec::new();
    for (&ref_obj, &referent) in &vm.reference_map {
        if referent == NULL {
            continue;
        }
        if !vm.heap.is_valid_heap_ref(referent) || !vm.heap.is_marked(referent) {
            to_clear.push(ref_obj);
        }
    }
    for ref_obj in to_clear {
        vm.reference_map.insert(ref_obj, NULL);
    }
}

/// Remove entries whose WeakReference referent was cleared during this GC.
///
/// `Interface.liveObjects` is an implementation cache mapping M3G native
/// handles to Java wrappers. Keeping its entries weak lets wrappers become
/// unreachable, but the Hashtable itself retains the key and WeakReference
/// container. Pruning here keeps that cache bounded without adding work to
/// `Object3D` construction.
fn prune_dead_m3g_live_objects(vm: &mut SharedVm) {
    let Some(interface_instance) = static_field_ref(
        vm,
        "javax.microedition.m3g.Interface",
        "instance",
        "Ljavax/microedition/m3g/Interface;",
    ) else {
        return;
    };
    if interface_instance == NULL {
        return;
    }

    let Some(live_objects_offset) = vm
        .class_repo
        .find_field_offset("javax.microedition.m3g.Interface", "liveObjects")
    else {
        return;
    };
    let live_objects = vm.heap.read_u32(interface_instance + live_objects_offset);
    if live_objects == NULL {
        return;
    }

    let Some(table_offset) = vm
        .class_repo
        .find_field_offset("java.util.Hashtable", "table")
    else {
        return;
    };
    let Some(count_offset) = vm
        .class_repo
        .find_field_offset("java.util.Hashtable", "count")
    else {
        return;
    };
    let Some(value_offset) = vm
        .class_repo
        .find_field_offset("java.util.HashtableEntry", "value")
    else {
        return;
    };
    let Some(next_offset) = vm
        .class_repo
        .find_field_offset("java.util.HashtableEntry", "next")
    else {
        return;
    };

    let table = vm.heap.read_u32(live_objects + table_offset);
    if table == NULL || !vm.heap.is_valid_heap_ref(table) {
        return;
    }

    let mut removed = 0u32;
    let bucket_count = vm.heap.array_length(table);
    for bucket in 0..bucket_count {
        let mut previous = NULL;
        let mut entry = vm.heap.array_read_u32(table, bucket);
        while entry != NULL {
            let next = vm.heap.read_u32(entry + next_offset);
            let reference = vm.heap.read_u32(entry + value_offset);
            let is_cleared = reference == NULL
                || vm.reference_map.get(&reference).copied().unwrap_or(NULL) == NULL;
            if is_cleared {
                if previous == NULL {
                    vm.heap.array_write_u32(table, bucket, next);
                } else {
                    vm.heap.write_u32(previous + next_offset, next);
                }
                removed += 1;
            } else {
                previous = entry;
            }
            entry = next;
        }
    }

    if removed != 0 {
        let count = vm.heap.read_u32(live_objects + count_offset);
        vm.heap
            .write_u32(live_objects + count_offset, count.saturating_sub(removed));
    }
}

fn static_field_ref(
    vm: &SharedVm,
    class_name: &str,
    field_name: &str,
    descriptor: &str,
) -> Option<u32> {
    let field = vm.resolve_field(class_name, field_name, descriptor)?;
    if !field.is_static {
        return None;
    }
    let base = vm.static_fields.get_addr(&field.class_name);
    Some(vm.heap.read_u32(base + field.byte_offset))
}

fn finalize_unreachable_m3g_objects(vm: &mut SharedVm) {
    let Some(object3d_class_id) = vm
        .class_repo
        .get_class_id("javax.microedition.m3g.Object3D")
    else {
        return;
    };
    let Some(handle_offset) = vm
        .class_repo
        .find_field_offset("javax.microedition.m3g.Object3D", "handle")
    else {
        return;
    };

    let handles: Vec<usize> = vm
        .heap
        .all_objects()
        .iter()
        .copied()
        .filter(|&object_ref| {
            !vm.heap.is_marked(object_ref)
                && vm
                    .class_repo
                    .is_assignable_by_id(vm.heap.get_class_id(object_ref), object3d_class_id)
        })
        .map(|object_ref| vm.heap.read_u32(object_ref + handle_offset) as usize)
        .filter(|&handle| handle != 0)
        .collect();

    for handle in handles {
        crate::interpreter::native::m3g::finalize_object(vm, handle);
    }
}

// ---- Sweep phase ----

fn sweep(heap: &mut Heap) -> GcStats {
    let mut freed: u32 = 0;
    let mut freed_count: u32 = 0;

    // Take ownership of all_objects to avoid clone
    let objects = std::mem::take(heap.all_objects_mut());
    let mut retained = Vec::with_capacity(objects.len());

    for addr in objects {
        if heap.is_marked(addr) {
            heap.clear_mark(addr);
            retained.push(addr);
        } else {
            let total_size = heap.obj_total_size(addr);
            heap.gc_free_block(addr);
            freed += total_size;
            freed_count += 1;
        }
    }

    // Refresh page indexes after releasing dead slots.
    heap.rebuild_free_list_from_retained(&retained);

    let retained_count = retained.len() as u32;
    heap.gc_finish_sweep(retained, freed);

    GcStats {
        freed_bytes: freed,
        retained_count,
        freed_count,
    }
}

// ---- VM map cleanup ----

fn cleanup_vm_maps(vm: &mut SharedVm) {
    // Remove entries whose keys are dead object addresses
    // After sweep, dead objects are no longer in all_objects and their memory is recycled.
    // We use is_valid_heap_ref + check that GC header still has reasonable size.
    // Marks have already been cleared, so use the allocation bitmap as the
    // authoritative liveness check for object-keyed side tables.
    let is_live = |addr: &u32| -> bool { vm.heap.is_valid_heap_ref(*addr) };

    vm.throwable_backtraces.retain(|k, _| is_live(k));
    vm.graphics_states.retain(|k, _| is_live(k));
    vm.monitors
        .retain(|k, monitor| is_live(k) && monitor.is_active());
    vm.reference_map.retain(|k, _| is_live(k));

    // These maps have object refs as keys that should remain if the object is a root
    // (already marked as live through root collection). Only clean up truly dead ones.
    // Collect dead surface IDs before removing them from the map
    let dead_surface_ids: Vec<i32> = vm
        .image_surface_map
        .iter()
        .filter(|(k, _)| !is_live(k))
        .map(|(_, &v)| v)
        .collect();
    vm.image_surface_map.retain(|k, _| is_live(k));
    // Emit DESTROY_SURFACE commands so the host can free the native resources
    for surface_id in dead_surface_ids {
        if surface_id > 0 {
            // surface 0 is the display surface, never destroy it
            vm.command_buffer.push(crate::vm::cmd::DESTROY_SURFACE);
            vm.command_buffer
                .extend_from_slice(&surface_id.to_le_bytes());
        }
    }
    vm.image_surface_restore_sources.retain(|k, _| is_live(k));
    let dead_player_ids: Vec<i32> = vm
        .player_obj_to_id
        .iter()
        .filter(|(k, _)| !is_live(k))
        .map(|(_, &v)| v)
        .collect();
    vm.player_obj_to_id.retain(|k, _| is_live(k));
    for id in dead_player_ids {
        vm.media_players.remove(&id);
    }
    let dead_nokia_player_ids: Vec<i32> = vm
        .nokia_sound_handles
        .iter()
        .filter(|(k, _)| !is_live(k))
        .map(|(_, &v)| v)
        .collect();
    vm.nokia_sound_handles.retain(|k, _| is_live(k));
    for id in dead_nokia_player_ids {
        vm.media_players.remove(&id);
    }
    vm.rms_obj_to_name.retain(|k, _| is_live(k));
    vm.rms_listeners.retain(|k, _| is_live(k));

    // Clean up orphaned resource decoders whose owning ResourceInputStream objects are dead
    if !vm.resource_decoders.is_empty() {
        if let Some(ris_class_id) = vm
            .class_repo
            .get_class_id("com.sun.cldc.io.ResourceInputStream")
        {
            let fd_offset = vm
                .class_repo
                .find_field_offset("com.sun.cldc.io.ResourceInputStream", "fileDecoder");
            let sd_offset = vm
                .class_repo
                .find_field_offset("com.sun.cldc.io.ResourceInputStream", "savedDecoder");
            let mut referenced_ids: std::collections::HashSet<u32> =
                std::collections::HashSet::new();
            for &obj in vm.heap.all_objects() {
                if vm.heap.get_class_id(obj) == ris_class_id {
                    if let Some(off) = fd_offset {
                        let id = vm.heap.read_u32(obj + off);
                        if id != 0 {
                            referenced_ids.insert(id);
                        }
                    }
                    if let Some(off) = sd_offset {
                        let id = vm.heap.read_u32(obj + off);
                        if id != 0 {
                            referenced_ids.insert(id);
                        }
                    }
                }
            }
            vm.resource_decoders
                .retain(|id, _| referenced_ids.contains(id));
        }
    }
}
