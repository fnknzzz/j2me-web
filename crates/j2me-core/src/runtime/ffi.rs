#[no_mangle]
pub extern "C" fn jvm_create(debug: i32) -> *mut JvmInstance {
    Box::into_raw(Box::new(JvmInstance::new(None, debug != 0)))
}

#[no_mangle]
pub extern "C" fn jvm_set_display_size(ptr: *mut JvmInstance, width: i32, height: i32) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    instance.set_display_size(width, height);
}

#[no_mangle]
pub extern "C" fn jvm_create_named(
    name_ptr: *const u8,
    name_len: u32,
    debug: i32,
) -> *mut JvmInstance {
    let name = if name_ptr.is_null() || name_len == 0 {
        None
    } else {
        let slice = unsafe { std::slice::from_raw_parts(name_ptr, name_len as usize) };
        std::str::from_utf8(slice).ok()
    };
    Box::into_raw(Box::new(JvmInstance::new(name, debug != 0)))
}

#[no_mangle]
pub extern "C" fn jvm_destroy(ptr: *mut JvmInstance) {
    if !ptr.is_null() {
        unsafe { drop(Box::from_raw(ptr)) };
    }
}

#[no_mangle]
pub extern "C" fn jvm_is_debug(ptr: *const JvmInstance) -> i32 {
    if ptr.is_null() {
        return 0;
    }
    let instance = unsafe { &*ptr };
    if instance.is_debug() {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "C" fn jvm_set_gc_enabled(ptr: *mut JvmInstance, enabled: i32) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    instance.set_gc_enabled(enabled != 0);
}

#[no_mangle]
pub extern "C" fn jvm_force_gc(ptr: *mut JvmInstance) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    instance.force_gc();
}

#[no_mangle]
pub extern "C" fn jvm_repaint(ptr: *mut JvmInstance) -> i32 {
    if ptr.is_null() {
        return -1;
    }
    let instance = unsafe { &mut *ptr };
    match instance.repaint() {
        Ok(_) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn jvm_load_class(ptr: *mut JvmInstance, data: *const u8, len: u32) -> i32 {
    if ptr.is_null() || data.is_null() {
        return -1;
    }
    let instance = unsafe { &mut *ptr };
    let slice = unsafe { std::slice::from_raw_parts(data, len as usize) };
    match instance.load_class(slice) {
        Ok(_) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn jvm_load_jar(ptr: *mut JvmInstance, data: *const u8, len: u32) -> i32 {
    if ptr.is_null() || data.is_null() {
        return -1;
    }
    let instance = unsafe { &mut *ptr };
    let slice = unsafe { std::slice::from_raw_parts(data, len as usize) };
    match instance.load_jar(slice) {
        Ok(classes) => classes.len() as i32,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn jvm_load_resource(
    ptr: *mut JvmInstance,
    name_ptr: *const u8,
    name_len: u32,
    data: *const u8,
    data_len: u32,
) {
    if ptr.is_null() || name_ptr.is_null() || data.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    let name_slice = unsafe { std::slice::from_raw_parts(name_ptr, name_len as usize) };
    let name = match std::str::from_utf8(name_slice) {
        Ok(s) => s,
        Err(_) => return,
    };
    let data_slice = unsafe { std::slice::from_raw_parts(data, data_len as usize) };
    instance.load_resource(name, data_slice);
}

#[no_mangle]
pub extern "C" fn jvm_start_midlet(ptr: *mut JvmInstance, launch_mode: u32) -> i32 {
    if ptr.is_null() {
        return -1;
    }
    let instance = unsafe { &mut *ptr };
    match instance.start_suite(launch_mode) {
        Ok(_) => 0,
        Err(msg) if msg == "ASYNC_PENDING" => 2, // async pending
        Err(e) => {
            instance.println_to_active(&format!("[ERROR] {}", e));
            -1
        }
    }
}

#[no_mangle]
pub extern "C" fn jvm_start_main(
    ptr: *mut JvmInstance,
    class_name_ptr: *const u8,
    class_name_len: u32,
    args_ptr: *const u8,
    args_len: u32,
) -> i32 {
    if ptr.is_null() || class_name_ptr.is_null() {
        return -1;
    }
    let instance = unsafe { &mut *ptr };
    let class_name_slice =
        unsafe { std::slice::from_raw_parts(class_name_ptr, class_name_len as usize) };
    let class_name = match std::str::from_utf8(class_name_slice) {
        Ok(s) => s,
        Err(_) => return -1,
    };
    let args = if args_len == 0 {
        Vec::new()
    } else if args_ptr.is_null() {
        return -1;
    } else {
        let args_slice = unsafe { std::slice::from_raw_parts(args_ptr, args_len as usize) };
        let args_text = match std::str::from_utf8(args_slice) {
            Ok(s) => s,
            Err(_) => return -1,
        };
        args_text
            .split('\0')
            .map(str::to_string)
            .collect::<Vec<_>>()
    };
    match instance.start_main_class(class_name, &args) {
        Ok(_) => 0,
        Err(msg) if msg == "ASYNC_PENDING" => 2,
        Err(e) => {
            instance.println_to_active(&format!("[ERROR] {}", e));
            -1
        }
    }
}

#[no_mangle]
pub extern "C" fn jvm_start_main_from_manifest(
    ptr: *mut JvmInstance,
    args_ptr: *const u8,
    args_len: u32,
) -> i32 {
    if ptr.is_null() {
        return -1;
    }
    let instance = unsafe { &mut *ptr };
    let args = if args_len == 0 {
        Vec::new()
    } else if args_ptr.is_null() {
        return -1;
    } else {
        let args_slice = unsafe { std::slice::from_raw_parts(args_ptr, args_len as usize) };
        let args_text = match std::str::from_utf8(args_slice) {
            Ok(s) => s,
            Err(_) => return -1,
        };
        args_text
            .split('\0')
            .map(str::to_string)
            .collect::<Vec<_>>()
    };
    match instance.start_main_from_manifest(&args) {
        Ok(_) => 0,
        Err(msg) if msg == "ASYNC_PENDING" => 2,
        Err(e) => {
            instance.println_to_active(&format!("[ERROR] {}", e));
            -1
        }
    }
}

#[no_mangle]
pub extern "C" fn jvm_pause_midlet(ptr: *mut JvmInstance) -> i32 {
    if ptr.is_null() {
        return -1;
    }
    let instance = unsafe { &mut *ptr };
    match instance.pause_suite() {
        Ok(_) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn jvm_destroy_midlet(ptr: *mut JvmInstance, unconditional: i32) -> i32 {
    if ptr.is_null() {
        return -1;
    }
    let instance = unsafe { &mut *ptr };
    match instance.destroy_suite(unconditional != 0) {
        Ok(_) => 0,
        Err(msg) if msg == "ASYNC_PENDING" => 2,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn jvm_step(ptr: *mut JvmInstance, time_ms: u32) -> i32 {
    if ptr.is_null() {
        return 0;
    }
    let instance = unsafe { &mut *ptr };
    instance.step(time_ms)
}

#[no_mangle]
pub extern "C" fn jvm_get_foreground_display_id(ptr: *mut JvmInstance) -> i32 {
    if ptr.is_null() {
        return 0;
    }
    let instance = unsafe { &*ptr };
    instance.foreground_display_id()
}

#[no_mangle]
pub extern "C" fn jvm_get_output(ptr: *mut JvmInstance, out_len: *mut u32) -> *mut u8 {
    if ptr.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr };
    let output = instance.get_output();
    if output.is_empty() {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    }
    let bytes = output.into_bytes();
    unsafe {
        *out_len = bytes.len() as u32;
    }
    let mut boxed = bytes.into_boxed_slice();
    let result = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    result
}

/// Returns a pointer to the command buffer and its length.
/// The caller owns the returned memory and must free it with `jvm_free`.
#[no_mangle]
pub extern "C" fn jvm_get_command_buffer(ptr: *mut JvmInstance, out_len: *mut u32) -> *mut u8 {
    if ptr.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr };
    let buf = instance.get_command_buffer();
    if buf.is_empty() {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    }
    unsafe {
        *out_len = buf.len() as u32;
    }
    let mut boxed = buf.into_boxed_slice();
    let result = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    result
}

#[no_mangle]
pub extern "C" fn jvm_idle_delay(ptr: *const JvmInstance) -> u32 {
    if ptr.is_null() {
        return 0;
    }
    let instance = unsafe { &*ptr };
    instance.idle_delay()
}

#[no_mangle]
pub extern "C" fn jvm_set_speed_multiplier(ptr: *mut JvmInstance, speed: f64) {
    if ptr.is_null() {
        return;
    }
    let jvm = unsafe { &mut *ptr };
    jvm.set_speed_multiplier(speed);
}

#[no_mangle]
pub extern "C" fn jvm_dump_threads(ptr: *mut JvmInstance, out_len: *mut u32) -> *mut u8 {
    if ptr.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &*ptr };
    let output = instance.dump_threads();
    if output.is_empty() {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    }
    let bytes = output.into_bytes();
    unsafe {
        *out_len = bytes.len() as u32;
    }
    let mut boxed = bytes.into_boxed_slice();
    let result = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    result
}

#[no_mangle]
pub extern "C" fn jvm_save_state(
    ptr: *mut JvmInstance,
    display_width: i32,
    display_height: i32,
    display_png_data: *const u8,
    display_png_data_len: i32,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr };
    let display_png_snapshot = if !display_png_data.is_null()
        && display_width > 0
        && display_height > 0
        && display_png_data_len > 0
    {
        let bytes =
            unsafe { std::slice::from_raw_parts(display_png_data, display_png_data_len as usize) }
                .to_vec();
        Some(vm::DisplaySurfacePngSnapshot {
            width: display_width,
            height: display_height,
            data: bytes,
        })
    } else {
        None
    };
    let bytes = instance.save_state(display_png_snapshot);
    unsafe {
        *out_len = bytes.len() as u32;
    }
    if bytes.is_empty() {
        return std::ptr::null_mut();
    }
    let mut boxed = bytes.into_boxed_slice();
    let result = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    result
}

#[no_mangle]
pub extern "C" fn jvm_save_state_fast(ptr: *mut JvmInstance, out_len: *mut u32) -> *mut u8 {
    if ptr.is_null() || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr };
    let bytes = instance.save_state_fast();
    unsafe {
        *out_len = bytes.len() as u32;
    }
    if bytes.is_empty() {
        return std::ptr::null_mut();
    }
    let mut boxed = bytes.into_boxed_slice();
    let result = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    result
}

#[no_mangle]
pub extern "C" fn jvm_collect_image_surface_snapshot_targets(
    ptr: *mut JvmInstance,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr };
    let targets = instance.prepare_image_surface_snapshot_targets();
    if targets.is_empty() {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    }

    let mut bytes = Vec::with_capacity(4 + targets.len() * 12);
    bytes.extend_from_slice(&(targets.len() as u32).to_le_bytes());
    for target in targets {
        bytes.extend_from_slice(&target.surface_id.to_le_bytes());
        bytes.extend_from_slice(&target.width.to_le_bytes());
        bytes.extend_from_slice(&target.height.to_le_bytes());
    }

    unsafe {
        *out_len = bytes.len() as u32;
    }
    let mut boxed = bytes.into_boxed_slice();
    let result = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    result
}

#[no_mangle]
pub extern "C" fn jvm_collect_rewind_image_surface_snapshot_targets(
    ptr: *const JvmInstance,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &*ptr };
    let targets = instance.image_surface_snapshot_targets_for_rewind();
    if targets.is_empty() {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    }

    let mut bytes = Vec::with_capacity(4 + targets.len() * 12);
    bytes.extend_from_slice(&(targets.len() as u32).to_le_bytes());
    for target in targets {
        bytes.extend_from_slice(&target.surface_id.to_le_bytes());
        bytes.extend_from_slice(&target.width.to_le_bytes());
        bytes.extend_from_slice(&target.height.to_le_bytes());
    }

    unsafe {
        *out_len = bytes.len() as u32;
    }
    let mut boxed = bytes.into_boxed_slice();
    let result = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    result
}

#[no_mangle]
pub extern "C" fn jvm_clear_image_surface_snapshots(ptr: *mut JvmInstance) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    instance.clear_pending_image_surface_snapshots();
}

#[no_mangle]
pub extern "C" fn jvm_add_image_surface_snapshot(
    ptr: *mut JvmInstance,
    surface_id: i32,
    width: i32,
    height: i32,
    data_ptr: *const i32,
    data_len: i32,
) {
    if ptr.is_null() || data_ptr.is_null() || data_len <= 0 {
        return;
    }
    let expected_len = (width.max(0) as usize).saturating_mul(height.max(0) as usize);
    if expected_len == 0 || data_len as usize != expected_len {
        return;
    }
    let instance = unsafe { &mut *ptr };
    let pixels = unsafe { std::slice::from_raw_parts(data_ptr, data_len as usize) }.to_vec();
    instance.add_pending_image_surface_snapshot(surface_id, width, height, pixels);
}

#[no_mangle]
pub extern "C" fn jvm_load_state(ptr: *mut JvmInstance, data: *const u8, len: u32) {
    if ptr.is_null() || data.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    let slice = unsafe { std::slice::from_raw_parts(data, len as usize) };
    instance.load_state(slice);
}

#[no_mangle]
pub extern "C" fn jvm_save_state_version() -> u32 {
    save_state_version()
}

#[no_mangle]
pub extern "C" fn jvm_save_meta_size() -> u32 {
    save_meta_size()
}

#[no_mangle]
pub extern "C" fn jvm_parse_save_meta(
    data: *const u8,
    len: u32,
    out_meta: *mut JvmSaveMeta,
) -> bool {
    if data.is_null() || out_meta.is_null() {
        return false;
    }
    let slice = unsafe { std::slice::from_raw_parts(data, len as usize) };
    match JvmCore::parse_save_meta(slice) {
        Some(meta) => {
            unsafe {
                *out_meta = meta.into();
            }
            true
        }
        None => false,
    }
}

// ============ Rewind (zero-serialization in-memory snapshots) ============

/// Capture VM snapshot for rewind (skips GC, serialization, compression).
/// Returns an opaque pointer to a heap-allocated InstanceSaveState.
/// Caller must eventually free via jvm_rewind_free or consume via jvm_rewind_restore.
#[no_mangle]
pub extern "C" fn jvm_rewind_capture(ptr: *mut JvmInstance) -> *mut std::ffi::c_void {
    if ptr.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr };
    let state = instance.capture_for_rewind();
    Box::into_raw(state) as *mut std::ffi::c_void
}

/// Restore VM state from a rewind snapshot (skips deserialization/decompression).
/// Consumes ownership of snapshot_ptr (no need to free afterwards).
#[no_mangle]
pub extern "C" fn jvm_rewind_restore(
    ptr: *mut JvmInstance,
    snapshot_ptr: *mut std::ffi::c_void,
) -> bool {
    if ptr.is_null() || snapshot_ptr.is_null() {
        return false;
    }
    let instance = unsafe { &mut *ptr };
    let state = unsafe { Box::from_raw(snapshot_ptr as *mut InstanceSaveState) };
    instance.restore_from_rewind(*state)
}

/// Free a rewind snapshot that is no longer needed.
#[no_mangle]
pub extern "C" fn jvm_rewind_free(snapshot_ptr: *mut std::ffi::c_void) {
    if snapshot_ptr.is_null() {
        return;
    }
    unsafe {
        drop(Box::from_raw(snapshot_ptr as *mut InstanceSaveState));
    }
}

#[no_mangle]
pub extern "C" fn jvm_take_pending_resource_image_restores(
    ptr: *mut JvmInstance,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr };
    let buf = instance.take_pending_resource_image_restores();
    if buf.is_empty() {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    }
    unsafe {
        *out_len = buf.len() as u32;
    }
    let mut boxed = buf.into_boxed_slice();
    let result = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    result
}

#[no_mangle]
pub extern "C" fn jvm_take_pending_derived_image_restores(
    ptr: *mut JvmInstance,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr };
    let buf = instance.take_pending_derived_image_restores();
    if buf.is_empty() {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    }
    unsafe {
        *out_len = buf.len() as u32;
    }
    let mut boxed = buf.into_boxed_slice();
    let result = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    result
}

#[no_mangle]
pub extern "C" fn jvm_free(buf: *mut u8, len: u32) {
    if buf.is_null() {
        return;
    }
    unsafe {
        let _ = Vec::from_raw_parts(buf, len as usize, len as usize);
    };
}

#[no_mangle]
pub extern "C" fn jvm_alloc(len: u32) -> *mut u8 {
    let mut buf = Vec::<u8>::with_capacity(len as usize);
    buf.resize(len as usize, 0);
    let mut boxed = buf.into_boxed_slice();
    let ptr = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    ptr
}

/// Claim up to `max_count` pending async requests and return them as binary.
#[no_mangle]
pub extern "C" fn jvm_claim_pending_async(
    ptr: *mut JvmInstance,
    max_count: u32,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr };
    let buf = instance.claim_pending_async(max_count as usize);
    if buf.is_empty() {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    }
    unsafe {
        *out_len = buf.len() as u32;
    }
    let mut boxed = buf.into_boxed_slice();
    let result = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    result
}

/// Write async state counts into `out_counts` as `[pending, claimed, resolved]`.
#[no_mangle]
pub extern "C" fn jvm_get_async_state_counts(ptr: *mut JvmInstance, out_counts: *mut u32) {
    if ptr.is_null() || out_counts.is_null() {
        return;
    }
    let instance = unsafe { &*ptr };
    let (pending, claimed, resolved) = instance.get_async_state_counts();
    unsafe {
        *out_counts.add(0) = pending as u32;
        *out_counts.add(1) = claimed as u32;
        *out_counts.add(2) = resolved as u32;
    }
}

/// Get the image bytes for a pending async DecodeImage request.
/// Copies the bytes into WASM memory and returns a pointer.
#[no_mangle]
pub extern "C" fn jvm_get_async_image_bytes(
    ptr: *mut JvmInstance,
    request_id: u32,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &*ptr };
    if let Some(bytes) = instance.get_async_image_bytes(request_id) {
        let len = bytes.len();
        unsafe {
            *out_len = len as u32;
        }
        let mut buf = bytes.to_vec().into_boxed_slice();
        let result = buf.as_mut_ptr();
        std::mem::forget(buf);
        result
    } else {
        unsafe {
            *out_len = 0;
        }
        std::ptr::null_mut()
    }
}

/// Resolve a DecodeImage async request with dimensions.
#[no_mangle]
pub extern "C" fn jvm_resolve_async_image(
    ptr: *mut JvmInstance,
    request_id: u32,
    width: i32,
    height: i32,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    instance.resolve_async_image(request_id, width, height);
}

#[no_mangle]
pub extern "C" fn jvm_resolve_async_pixels(
    ptr: *mut JvmInstance,
    request_id: u32,
    data_ptr: *const i32,
    data_len: i32,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    let pixels = if data_ptr.is_null() || data_len <= 0 {
        Vec::new()
    } else {
        unsafe { std::slice::from_raw_parts(data_ptr, data_len as usize) }.to_vec()
    };
    instance.resolve_async_pixels(request_id, pixels);
}

#[no_mangle]
pub extern "C" fn jvm_resolve_async_m3g_image(
    ptr: *mut JvmInstance,
    request_id: u32,
    width: i32,
    height: i32,
    data_ptr: *const u8,
    data_len: i32,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    let pixels = if data_ptr.is_null() || data_len <= 0 {
        Vec::new()
    } else {
        unsafe { std::slice::from_raw_parts(data_ptr, data_len as usize) }.to_vec()
    };
    instance.resolve_async_m3g_image(request_id, width, height, pixels);
}

/// Resolve an RmsLoad async request. data_ptr=null + data_len=0 means "not found".
#[no_mangle]
pub extern "C" fn jvm_resolve_async_rms_load(
    ptr: *mut JvmInstance,
    request_id: u32,
    data_ptr: *const u8,
    data_len: i32,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    let data = if data_ptr.is_null() || data_len < 0 {
        None
    } else {
        Some(unsafe { std::slice::from_raw_parts(data_ptr, data_len as usize) }.to_vec())
    };
    instance.resolve_async_rms_load(request_id, data);
}

/// Resolve an RmsKeys async request. keys_ptr/keys_len is a UTF-8 newline-separated string.
#[no_mangle]
pub extern "C" fn jvm_resolve_async_rms_keys(
    ptr: *mut JvmInstance,
    request_id: u32,
    keys_ptr: *const u8,
    keys_len: i32,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    let keys = if keys_ptr.is_null() || keys_len <= 0 {
        Vec::new()
    } else {
        let s = unsafe {
            std::str::from_utf8_unchecked(std::slice::from_raw_parts(keys_ptr, keys_len as usize))
        };
        s.split('\n')
            .filter(|k| !k.is_empty())
            .map(|k| k.to_string())
            .collect()
    };
    instance.resolve_async_rms_keys(request_id, keys);
}

#[no_mangle]
pub extern "C" fn jvm_resolve_async_text_input(
    ptr: *mut JvmInstance,
    request_id: u32,
    text_ptr: *const u8,
    text_len: i32,
) {
    if ptr.is_null() {
        return;
    }
    let text = if text_ptr.is_null() || text_len < 0 {
        None
    } else {
        let bytes = unsafe { std::slice::from_raw_parts(text_ptr, text_len as usize) };
        std::str::from_utf8(bytes)
            .ok()
            .map(|value| value.to_string())
    };
    let instance = unsafe { &mut *ptr };
    instance.resolve_async_text_input(request_id, text);
}

#[no_mangle]
pub extern "C" fn jvm_resolve_async_date_input(
    ptr: *mut JvmInstance,
    request_id: u32,
    committed: u32,
    initialized: u32,
    value_millis: i64,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    instance.resolve_async_date_input(request_id, committed != 0, initialized != 0, value_millis);
}

#[no_mangle]
pub extern "C" fn jvm_resolve_async_bluetooth(
    ptr: *mut JvmInstance,
    request_id: u32,
    response_ptr: *const u8,
    response_len: i32,
) {
    if ptr.is_null() {
        return;
    }
    let response = if response_ptr.is_null() || response_len <= 0 {
        String::new()
    } else {
        let bytes = unsafe { std::slice::from_raw_parts(response_ptr, response_len as usize) };
        String::from_utf8_lossy(bytes).into_owned()
    };
    unsafe { &mut *ptr }.resolve_async_bluetooth(request_id, response);
}

/// Resume the thread that was waiting for async completion.
/// Returns 0 on success, 1 if still async-pending, -1 on error.
#[no_mangle]
pub extern "C" fn jvm_resume_async(ptr: *mut JvmInstance) -> i32 {
    if ptr.is_null() {
        return -1;
    }
    let instance = unsafe { &mut *ptr };
    match instance.resume_async_thread() {
        RunResult::Done => 0,
        RunResult::AsyncPending => 1,
        RunResult::StepLimitReached => 0, // treat as done — JS run loop will continue
    }
}

#[no_mangle]
pub extern "C" fn jvm_push_native_event(
    ptr: *mut JvmInstance,
    event_type: i32,
    int_param1: i32,
    int_param2: i32,
    int_param3: i32,
    int_param4: i32,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    instance.push_native_event(vm::NativeEventData {
        event_type,
        int_param1,
        int_param2,
        int_param3,
        int_param4,
    });
}

// ============ Cheat Engine ABI ============

/// Helper: parse a CheatValue from a raw pointer + length sent via the C ABI.
fn cheat_value_from_ptr(
    vtype: cheat_engine::ValueType,
    value_ptr: *const u8,
    value_len: u32,
) -> Option<cheat_engine::CheatValue> {
    if value_ptr.is_null() || value_len == 0 {
        return None;
    }
    let bytes = unsafe { std::slice::from_raw_parts(value_ptr, value_len as usize) };
    cheat_engine::CheatValue::from_le_bytes(vtype, bytes)
}

/// Helper: allocate a Vec<u8> as a raw C buffer (caller must free via jvm_free).
fn buf_to_raw(buf: Vec<u8>, out_len: *mut u32) -> *mut u8 {
    if buf.is_empty() {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    }
    unsafe {
        *out_len = buf.len() as u32;
    }
    let mut boxed = buf.into_boxed_slice();
    let ptr = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    ptr
}

/// Begin a new search session across all value types simultaneously.
/// search_mode: 0=exact 1=changed 2=unchanged 3=increased 4=decreased 5=increasedBy 6=decreasedBy 7=snapshot
/// value: f64 target for modes 0/5/6; ignored for all others.
#[no_mangle]
pub extern "C" fn jvm_cheat_search_begin(ptr: *mut JvmInstance, search_mode: i32, value: f64) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let mode = match cheat_engine::SearchMode::from_tag(search_mode, value) {
        Some(m) => m,
        None => return,
    };
    instance.cheat_engine.search_begin(&instance.vm.heap, mode);
}

/// Refine the current candidate list with a new filter pass.
/// search_mode and value follow the same convention as jvm_cheat_search_begin.
#[no_mangle]
pub extern "C" fn jvm_cheat_search_refine(ptr: *mut JvmInstance, search_mode: i32, value: f64) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let mode = match cheat_engine::SearchMode::from_tag(search_mode, value) {
        Some(m) => m,
        None => return,
    };
    instance
        .cheat_engine
        .search_refine(&instance.vm.heap, Some(&instance.vm.class_repo), mode);
}

/// Returns the current candidate list as a binary buffer.
/// Caller must free the returned buffer via jvm_free(ptr, len).
#[no_mangle]
pub extern "C" fn jvm_cheat_get_results(ptr: *mut JvmInstance, out_len: *mut u32) -> *mut u8 {
    if ptr.is_null() || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let buf = instance
        .cheat_engine
        .results_binary(&instance.vm.heap, Some(&instance.vm.class_repo));
    buf_to_raw(buf, out_len)
}

/// Search for a sequence of numeric values within a single object or primitive array.
/// References are deliberately not followed.
/// match_mode: 0=all values in distinct fields (unordered), 1=consecutive fields.
#[no_mangle]
pub extern "C" fn jvm_cheat_search_objects(
    ptr: *mut JvmInstance,
    values_ptr: *const u8,
    value_count: u32,
    match_mode: i32,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() || values_ptr.is_null() || value_count == 0 || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let Some(values_len) = (value_count as usize).checked_mul(8) else {
        return std::ptr::null_mut();
    };
    let value_bytes = unsafe { std::slice::from_raw_parts(values_ptr, values_len) };
    let values: Vec<f64> = value_bytes
        .chunks_exact(8)
        .map(|bytes| f64::from_le_bytes(bytes.try_into().expect("f64 chunk has eight bytes")))
        .collect();
    let instance = unsafe { &mut *ptr };
    let Some(core) = instance.current_core_mut() else {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    };
    let buf = core
        .cheat_engine
        .object_search_begin_binary(&core.vm, &values, match_mode);
    buf_to_raw(buf, out_len)
}

/// Refine only the candidates retained by jvm_cheat_search_objects, matching
/// each new value against the field slot selected for the same input index.
#[no_mangle]
pub extern "C" fn jvm_cheat_refine_objects(
    ptr: *mut JvmInstance,
    values_ptr: *const u8,
    value_count: u32,
    match_mode: i32,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() || values_ptr.is_null() || value_count == 0 || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let Some(values_len) = (value_count as usize).checked_mul(8) else {
        return std::ptr::null_mut();
    };
    let value_bytes = unsafe { std::slice::from_raw_parts(values_ptr, values_len) };
    let values: Vec<f64> = value_bytes
        .chunks_exact(8)
        .map(|bytes| f64::from_le_bytes(bytes.try_into().expect("f64 chunk has eight bytes")))
        .collect();
    let instance = unsafe { &mut *ptr };
    let Some(core) = instance.current_core_mut() else {
        unsafe {
            *out_len = 0;
        }
        return std::ptr::null_mut();
    };
    let buf = core
        .cheat_engine
        .object_search_refine_binary(&core.vm, &values, match_mode);
    buf_to_raw(buf, out_len)
}

#[no_mangle]
pub extern "C" fn jvm_cheat_object_search_clear(ptr: *mut JvmInstance) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr };
    if let Some(core) = instance.current_core_mut() {
        core.cheat_engine.object_search_clear();
    }
}

/// Clear all search state (candidates + prev_values).
#[no_mangle]
pub extern "C" fn jvm_cheat_search_clear(ptr: *mut JvmInstance) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    instance.cheat_engine.search_clear();
}

/// Read the value at addr and return as binary: [vtype: u8][value: 8 bytes LE]
/// Caller must free via jvm_free(ptr, len).
#[no_mangle]
pub extern "C" fn jvm_cheat_read(
    ptr: *mut JvmInstance,
    address: u32,
    value_type: i32,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let vtype = match cheat_engine::ValueType::from_tag(value_type) {
        Some(v) => v,
        None => return std::ptr::null_mut(),
    };
    let buf = instance
        .cheat_engine
        .read_binary(&instance.vm.heap, address, vtype);
    buf_to_raw(buf.to_vec(), out_len)
}

/// Write value_bytes at addr with the given type.
#[no_mangle]
pub extern "C" fn jvm_cheat_write(
    ptr: *mut JvmInstance,
    address: u32,
    value_type: i32,
    value_ptr: *const u8,
    value_len: u32,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let vtype = match cheat_engine::ValueType::from_tag(value_type) {
        Some(v) => v,
        None => return,
    };
    if let Some(val) = cheat_value_from_ptr(vtype, value_ptr, value_len) {
        instance
            .cheat_engine
            .write_value(&mut instance.vm.heap, address, val);
    }
}

/// Add or replace a freeze entry. The value is frozen to value_bytes every step.
#[no_mangle]
pub extern "C" fn jvm_cheat_freeze_add(
    ptr: *mut JvmInstance,
    address: u32,
    value_type: i32,
    value_ptr: *const u8,
    value_len: u32,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let vtype = match cheat_engine::ValueType::from_tag(value_type) {
        Some(v) => v,
        None => return,
    };
    if let Some(val) = cheat_value_from_ptr(vtype, value_ptr, value_len) {
        instance.cheat_engine.freeze_add(address, vtype, val);
        instance
            .cheat_engine
            .write_value(&mut instance.vm.heap, address, val);
    }
}

/// Remove the freeze entry for (addr, value_type). Symmetric with freeze_add.
#[no_mangle]
pub extern "C" fn jvm_cheat_freeze_remove(ptr: *mut JvmInstance, address: u32, value_type: i32) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let vtype = match cheat_engine::ValueType::from_tag(value_type) {
        Some(v) => v,
        None => return,
    };
    instance.cheat_engine.freeze_remove(address, vtype);
}

/// Remove all freeze entries.
#[no_mangle]
pub extern "C" fn jvm_cheat_freeze_clear(ptr: *mut JvmInstance) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    instance.cheat_engine.freeze_clear();
}

/// Enable or disable a freeze entry for (addr, value_type). enabled=1/disabled=0.
#[no_mangle]
pub extern "C" fn jvm_cheat_freeze_set_enabled(
    ptr: *mut JvmInstance,
    address: u32,
    value_type: i32,
    enabled: i32,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let vtype = match cheat_engine::ValueType::from_tag(value_type) {
        Some(v) => v,
        None => return,
    };
    instance
        .cheat_engine
        .freeze_set_enabled(address, vtype, enabled != 0);
}

/// Returns the current freeze list as a binary buffer.
/// Caller must free via jvm_free(ptr, len).
#[no_mangle]
pub extern "C" fn jvm_cheat_freeze_list(ptr: *mut JvmInstance, out_len: *mut u32) -> *mut u8 {
    if ptr.is_null() || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let buf = instance.cheat_engine.freeze_list_binary();
    buf_to_raw(buf, out_len)
}

// ---- watch list ----

/// Add or update watch entries from a binary buffer (batch).
///
/// Buffer layout: [count: u32 LE] then per entry:
///   [addr: u32 LE][vtype: u8][label_len: u16 LE][label: label_len bytes UTF-8]
#[no_mangle]
pub extern "C" fn jvm_cheat_watch_add_batch(
    ptr: *mut JvmInstance,
    buf_ptr: *const u8,
    buf_len: u32,
) {
    if ptr.is_null() || buf_ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let buf = unsafe { std::slice::from_raw_parts(buf_ptr, buf_len as usize) };
    instance
        .cheat_engine
        .watch_add_batch(&instance.vm.heap, buf);
}

/// Remove the watch entry for (addr, value_type).
#[no_mangle]
pub extern "C" fn jvm_cheat_watch_remove(ptr: *mut JvmInstance, address: u32, value_type: i32) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let vtype = match cheat_engine::ValueType::from_tag(value_type) {
        Some(v) => v,
        None => return,
    };
    instance.cheat_engine.watch_remove(address, vtype);
}

/// Remove all watch entries.
#[no_mangle]
pub extern "C" fn jvm_cheat_watch_clear(ptr: *mut JvmInstance) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    instance.cheat_engine.watch_clear();
}

/// Returns the current watch list as a binary buffer.
///
/// Layout: [count: u32 LE] then per entry:
///   [addr: u32 LE][vtype: u8][flags: u8 (bit0=changed, bit1=stale)][value: 8 bytes LE][label_len: u16 LE][label: label_len bytes UTF-8]
/// Caller must free via jvm_free(ptr, len).
#[no_mangle]
pub extern "C" fn jvm_cheat_watch_list(ptr: *mut JvmInstance, out_len: *mut u32) -> *mut u8 {
    if ptr.is_null() || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let buf = instance.cheat_engine.watch_list_binary();
    let len = buf.len();
    let raw = buf.into_boxed_slice();
    unsafe {
        *out_len = len as u32;
    }
    Box::into_raw(raw) as *mut u8
}

/// Write a value directly to any address.
/// value_type and value_bytes follow the same convention as jvm_cheat_write.
#[no_mangle]
pub extern "C" fn jvm_cheat_watch_write(
    ptr: *mut JvmInstance,
    address: u32,
    value_type: i32,
    value_ptr: *const u8,
    value_len: u32,
) {
    if ptr.is_null() {
        return;
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let vtype = match cheat_engine::ValueType::from_tag(value_type) {
        Some(v) => v,
        None => return,
    };
    if let Some(val) = cheat_value_from_ptr(vtype, value_ptr, value_len) {
        instance
            .cheat_engine
            .watch_write(&mut instance.vm.heap, address, val);
    }
}

// ---- stable cheat code (方案 B) ----

/// Inspect a heap address found by method A and return a stable code descriptor
/// as a binary buffer if the address belongs to a static field.
/// Caller must free via jvm_free(ptr, len).
#[no_mangle]
pub extern "C" fn jvm_cheat_code_from_candidate(
    ptr: *mut JvmInstance,
    address: u32,
    value_type: i32,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();
    let vtype = match cheat_engine::ValueType::from_tag(value_type) {
        Some(v) => v,
        None => {
            let err = b"invalid value_type";
            let mut buf = Vec::with_capacity(3 + err.len());
            buf.push(0u8); // status = error
            buf.extend_from_slice(&(err.len() as u16).to_le_bytes());
            buf.extend_from_slice(err);
            return buf_to_raw(buf, out_len);
        }
    };
    let buf = cheat_engine::code_from_candidate_binary(
        &instance.vm.heap,
        address,
        vtype,
        &instance.vm.class_repo,
        &instance.vm,
    );
    buf_to_raw(buf, out_len)
}

/// Parse a binary cheat code buffer, resolve each entry to a live heap address,
/// and optionally apply the action (write/freeze).
/// Binary input/output format — see cheat_engine::parse_cheat_code_binary docs.
/// Caller must free via jvm_free(ptr, len).
#[no_mangle]
pub extern "C" fn jvm_cheat_code_resolve(
    ptr: *mut JvmInstance,
    code_ptr: *const u8,
    code_len: u32,
    out_len: *mut u32,
) -> *mut u8 {
    if ptr.is_null() || out_len.is_null() {
        return std::ptr::null_mut();
    }
    let instance = unsafe { &mut *ptr }.current_core_mut_unchecked();

    let input_buf = unsafe { std::slice::from_raw_parts(code_ptr, code_len as usize) };

    let entries = match cheat_engine::parse_cheat_code_binary(input_buf) {
        Ok(e) => e,
        Err(msg) => {
            // Return a single error entry
            let msg_bytes = msg.as_bytes();
            let mut buf = Vec::with_capacity(4 + 3 + 2 + msg_bytes.len());
            buf.extend_from_slice(&1u32.to_le_bytes()); // count = 1
            buf.push(0); // status = error
            buf.extend_from_slice(&0u16.to_le_bytes()); // id_len = 0
            buf.extend_from_slice(&(msg_bytes.len() as u16).to_le_bytes());
            buf.extend_from_slice(msg_bytes);
            return buf_to_raw(buf, out_len);
        }
    };

    // Build binary output: [count: u32] per entry: [status: u8][id_len: u16][id]...
    let mut buf = Vec::with_capacity(4 + entries.len() * 16);
    buf.extend_from_slice(&(entries.len() as u32).to_le_bytes());

    for entry in &entries {
        let res = cheat_engine::resolve_code_entry(entry, &instance.vm);
        let id_bytes = res.id.as_bytes();

        if res.ok {
            // Apply action.
            match entry.action {
                cheat_engine::CheatCodeAction::Write => {
                    if let Some(val) = entry.value {
                        val.write_to(&mut instance.vm.heap, res.addr);
                    }
                }
                cheat_engine::CheatCodeAction::Freeze => {
                    if let Some(val) = entry.value {
                        instance
                            .cheat_engine
                            .freeze_add(res.addr, entry.value_type, val);
                        instance
                            .cheat_engine
                            .write_value(&mut instance.vm.heap, res.addr, val);
                    }
                }
                cheat_engine::CheatCodeAction::None => {}
            }
            buf.push(1); // status = ok
            buf.extend_from_slice(&(id_bytes.len() as u16).to_le_bytes());
            buf.extend_from_slice(id_bytes);
            buf.extend_from_slice(&res.addr.to_le_bytes());
            buf.push(res.value_type.tag());
        } else {
            buf.push(0); // status = error
            buf.extend_from_slice(&(id_bytes.len() as u16).to_le_bytes());
            buf.extend_from_slice(id_bytes);
            let err_bytes = res.error.as_bytes();
            buf.extend_from_slice(&(err_bytes.len() as u16).to_le_bytes());
            buf.extend_from_slice(err_bytes);
        }
    }
    buf_to_raw(buf, out_len)
}
