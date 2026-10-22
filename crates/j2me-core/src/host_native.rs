use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};
use std::time::{Instant, SystemTime, UNIX_EPOCH};

use crate::host_api::HostPlatform;

static START_INSTANT: OnceLock<Instant> = OnceLock::new();
static RMS_DIR: OnceLock<Mutex<PathBuf>> = OnceLock::new();

// Callback function pointers (set by iOS/host at init)
static CREATE_SURFACE_CB: OnceLock<unsafe extern "C" fn(i32, i32, i32)> = OnceLock::new();
static MEASURE_TEXT_CB: OnceLock<unsafe extern "C" fn(*const u8, i32, i32, i32, i32) -> i32> =
    OnceLock::new();
static FONT_METRICS_CB: OnceLock<unsafe extern "C" fn(i32, i32, i32) -> i32> = OnceLock::new();
static GET_MEDIA_TIME_CB: OnceLock<unsafe extern "C" fn(i32) -> i64> = OnceLock::new();
static GET_MEDIA_DURATION_CB: OnceLock<unsafe extern "C" fn(i32) -> i64> = OnceLock::new();
static SET_MEDIA_TIME_CB: OnceLock<unsafe extern "C" fn(i32, i64)> = OnceLock::new();
static ERROR_CB: OnceLock<unsafe extern "C" fn(*const u8, u32)> = OnceLock::new();

pub fn set_rms_dir(path: PathBuf) {
    let _ = RMS_DIR.set(Mutex::new(path));
}

pub fn set_create_surface_callback(cb: unsafe extern "C" fn(i32, i32, i32)) {
    let _ = CREATE_SURFACE_CB.set(cb);
}

pub fn set_measure_text_callback(cb: unsafe extern "C" fn(*const u8, i32, i32, i32, i32) -> i32) {
    let _ = MEASURE_TEXT_CB.set(cb);
}

pub fn set_font_metrics_callback(cb: unsafe extern "C" fn(i32, i32, i32) -> i32) {
    let _ = FONT_METRICS_CB.set(cb);
}

pub fn set_get_media_time_callback(cb: unsafe extern "C" fn(i32) -> i64) {
    let _ = GET_MEDIA_TIME_CB.set(cb);
}

pub fn set_get_media_duration_callback(cb: unsafe extern "C" fn(i32) -> i64) {
    let _ = GET_MEDIA_DURATION_CB.set(cb);
}

pub fn set_set_media_time_callback(cb: unsafe extern "C" fn(i32, i64)) {
    let _ = SET_MEDIA_TIME_CB.set(cb);
}

pub fn set_error_callback(cb: unsafe extern "C" fn(*const u8, u32)) {
    let _ = ERROR_CB.set(cb);
}

pub fn emit_error(message: &str) {
    if let Some(cb) = ERROR_CB.get() {
        unsafe {
            cb(message.as_ptr(), message.len() as u32);
        }
    }
}

fn rms_dir() -> PathBuf {
    RMS_DIR
        .get()
        .and_then(|m| m.lock().ok().map(|p| p.clone()))
        .unwrap_or_else(|| PathBuf::from("rms_data"))
}

fn rms_key_path(key: &str) -> PathBuf {
    let sanitized: String = key
        .chars()
        .map(|c| {
            if c.is_alphanumeric() || c == '_' || c == '-' || c == '.' {
                c
            } else {
                '_'
            }
        })
        .collect();
    rms_dir().join(sanitized)
}

pub struct NativeHost;

impl HostPlatform for NativeHost {
    unsafe fn create_surface(surface_id: i32, width: i32, height: i32) {
        if let Some(cb) = CREATE_SURFACE_CB.get() {
            cb(surface_id, width, height);
        }
    }

    unsafe fn rms_load(key_ptr: *const u8, key_len: i32, buf_ptr: *mut u8, buf_cap: i32) -> i32 {
        let key = std::str::from_utf8(std::slice::from_raw_parts(key_ptr, key_len as usize))
            .unwrap_or("");
        let path = rms_key_path(key);
        match std::fs::read(&path) {
            Ok(data) => {
                let copy_len = data.len().min(buf_cap as usize);
                std::ptr::copy_nonoverlapping(data.as_ptr(), buf_ptr, copy_len);
                copy_len as i32
            }
            Err(_) => -1,
        }
    }

    unsafe fn rms_save(key_ptr: *const u8, key_len: i32, data_ptr: *const u8, data_len: i32) {
        let key = std::str::from_utf8(std::slice::from_raw_parts(key_ptr, key_len as usize))
            .unwrap_or("");
        let data = std::slice::from_raw_parts(data_ptr, data_len as usize);
        let dir = rms_dir();
        let _ = std::fs::create_dir_all(&dir);
        let _ = std::fs::write(rms_key_path(key), data);
    }

    unsafe fn rms_remove(key_ptr: *const u8, key_len: i32) {
        let key = std::str::from_utf8(std::slice::from_raw_parts(key_ptr, key_len as usize))
            .unwrap_or("");
        let _ = std::fs::remove_file(rms_key_path(key));
    }

    unsafe fn rms_keys(buf_ptr: *mut u8, buf_cap: i32) -> i32 {
        let dir = rms_dir();
        let entries = match std::fs::read_dir(&dir) {
            Ok(e) => e,
            Err(_) => return 0,
        };
        let mut result = String::new();
        for entry in entries.flatten() {
            if let Some(name) = entry.file_name().to_str() {
                if !result.is_empty() {
                    result.push('\n');
                }
                result.push_str(name);
            }
        }
        let bytes = result.as_bytes();
        let copy_len = bytes.len().min(buf_cap as usize);
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), buf_ptr, copy_len);
        copy_len as i32
    }

    unsafe fn current_time_millis() -> f64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_millis() as f64)
            .unwrap_or(0.0)
    }

    unsafe fn measure_text(
        text_ptr: *const u8,
        text_len: i32,
        face: i32,
        style: i32,
        size: i32,
    ) -> i32 {
        let cb = MEASURE_TEXT_CB
            .get()
            .expect("measure_text callback not registered");
        cb(text_ptr, text_len, face, style, size)
    }

    unsafe fn font_metrics(face: i32, style: i32, size: i32) -> i32 {
        let cb = FONT_METRICS_CB
            .get()
            .expect("font_metrics callback not registered");
        cb(face, style, size)
    }

    unsafe fn mono_time_millis() -> f64 {
        let start = START_INSTANT.get_or_init(Instant::now);
        start.elapsed().as_millis() as f64
    }

    unsafe fn get_locale(buf_ptr: *mut u8, buf_cap: i32) -> i32 {
        let locale = b"zh-CN";
        let copy_len = locale.len().min(buf_cap as usize);
        std::ptr::copy_nonoverlapping(locale.as_ptr(), buf_ptr, copy_len);
        copy_len as i32
    }

    unsafe fn get_media_time(handle: i32) -> i64 {
        if let Some(cb) = GET_MEDIA_TIME_CB.get() {
            cb(handle)
        } else {
            0
        }
    }

    unsafe fn get_media_duration(handle: i32) -> i64 {
        if let Some(cb) = GET_MEDIA_DURATION_CB.get() {
            cb(handle)
        } else {
            0
        }
    }

    unsafe fn set_media_time(handle: i32, time_ms: i64) {
        if let Some(cb) = SET_MEDIA_TIME_CB.get() {
            cb(handle, time_ms);
        }
    }
}
