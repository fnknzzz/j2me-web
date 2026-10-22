/// Host platform interface — both wasm and native must implement this trait.
/// Ensures compile-time signature consistency across platforms.
pub trait HostPlatform {
    unsafe fn create_surface(surface_id: i32, width: i32, height: i32);
    unsafe fn rms_load(key_ptr: *const u8, key_len: i32, buf_ptr: *mut u8, buf_cap: i32) -> i32;
    unsafe fn rms_save(key_ptr: *const u8, key_len: i32, data_ptr: *const u8, data_len: i32);
    unsafe fn rms_remove(key_ptr: *const u8, key_len: i32);
    unsafe fn rms_keys(buf_ptr: *mut u8, buf_cap: i32) -> i32;
    unsafe fn current_time_millis() -> f64;
    unsafe fn measure_text(
        text_ptr: *const u8,
        text_len: i32,
        face: i32,
        style: i32,
        size: i32,
    ) -> i32;
    unsafe fn font_metrics(face: i32, style: i32, size: i32) -> i32;
    unsafe fn mono_time_millis() -> f64;
    unsafe fn get_locale(buf_ptr: *mut u8, buf_cap: i32) -> i32;
    unsafe fn get_media_time(handle: i32) -> i64;
    unsafe fn get_media_duration(handle: i32) -> i64;
    unsafe fn set_media_time(handle: i32, time_ms: i64);
}
