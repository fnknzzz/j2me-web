use crate::host_api::HostPlatform;

extern "C" {
    fn host_create_surface(surface_id: i32, width: i32, height: i32);
    fn host_rms_load(key_ptr: *const u8, key_len: i32, buf_ptr: *mut u8, buf_cap: i32) -> i32;
    fn host_rms_save(key_ptr: *const u8, key_len: i32, data_ptr: *const u8, data_len: i32);
    fn host_rms_remove(key_ptr: *const u8, key_len: i32);
    fn host_rms_keys(buf_ptr: *mut u8, buf_cap: i32) -> i32;
    fn host_current_time_millis() -> f64;
    fn host_measure_text(
        text_ptr: *const u8,
        text_len: i32,
        face: i32,
        style: i32,
        size: i32,
    ) -> i32;
    fn host_font_metrics(face: i32, style: i32, size: i32) -> i32;
    fn host_mono_time_millis() -> f64;
    fn host_get_locale(buf_ptr: *mut u8, buf_cap: i32) -> i32;
    fn host_get_media_time(handle: i32) -> i64;
    fn host_get_media_duration(handle: i32) -> i64;
    fn host_set_media_time(handle: i32, time_ms: i64);
}

pub struct WasmHost;

impl HostPlatform for WasmHost {
    unsafe fn create_surface(surface_id: i32, width: i32, height: i32) {
        host_create_surface(surface_id, width, height)
    }
    unsafe fn rms_load(key_ptr: *const u8, key_len: i32, buf_ptr: *mut u8, buf_cap: i32) -> i32 {
        host_rms_load(key_ptr, key_len, buf_ptr, buf_cap)
    }
    unsafe fn rms_save(key_ptr: *const u8, key_len: i32, data_ptr: *const u8, data_len: i32) {
        host_rms_save(key_ptr, key_len, data_ptr, data_len)
    }
    unsafe fn rms_remove(key_ptr: *const u8, key_len: i32) {
        host_rms_remove(key_ptr, key_len)
    }
    unsafe fn rms_keys(buf_ptr: *mut u8, buf_cap: i32) -> i32 {
        host_rms_keys(buf_ptr, buf_cap)
    }
    unsafe fn current_time_millis() -> f64 {
        host_current_time_millis()
    }
    unsafe fn measure_text(
        text_ptr: *const u8,
        text_len: i32,
        face: i32,
        style: i32,
        size: i32,
    ) -> i32 {
        host_measure_text(text_ptr, text_len, face, style, size)
    }
    unsafe fn font_metrics(face: i32, style: i32, size: i32) -> i32 {
        host_font_metrics(face, style, size)
    }
    unsafe fn mono_time_millis() -> f64 {
        host_mono_time_millis()
    }
    unsafe fn get_locale(buf_ptr: *mut u8, buf_cap: i32) -> i32 {
        host_get_locale(buf_ptr, buf_cap)
    }
    unsafe fn get_media_time(handle: i32) -> i64 {
        host_get_media_time(handle)
    }
    unsafe fn get_media_duration(handle: i32) -> i64 {
        host_get_media_duration(handle)
    }
    unsafe fn set_media_time(handle: i32, time_ms: i64) {
        host_set_media_time(handle, time_ms)
    }
}
