use crate::host_api::HostPlatform;

#[cfg(target_arch = "wasm32")]
type Host = crate::host_wasm::WasmHost;
#[cfg(not(target_arch = "wasm32"))]
type Host = crate::host_native::NativeHost;

pub fn call_host_create_surface(surface_id: i32, width: i32, height: i32) {
    unsafe { Host::create_surface(surface_id, width, height) }
}

pub fn call_host_rms_load(key: &str, buf: &mut [u8]) -> i32 {
    unsafe {
        Host::rms_load(
            key.as_ptr(),
            key.len() as i32,
            buf.as_mut_ptr(),
            buf.len() as i32,
        )
    }
}
pub fn call_host_rms_save(key: &str, data: &[u8]) {
    unsafe {
        Host::rms_save(
            key.as_ptr(),
            key.len() as i32,
            data.as_ptr(),
            data.len() as i32,
        )
    }
}
pub fn call_host_rms_remove(key: &str) {
    unsafe { Host::rms_remove(key.as_ptr(), key.len() as i32) }
}
pub fn call_host_rms_keys() -> Vec<String> {
    let mut buf = vec![0u8; 4096];
    let len = unsafe { Host::rms_keys(buf.as_mut_ptr(), buf.len() as i32) } as usize;
    if len == 0 {
        return Vec::new();
    }
    let s = std::str::from_utf8(&buf[..len]).unwrap_or("");
    s.split('\n')
        .filter(|k| !k.is_empty())
        .map(|k| k.to_string())
        .collect()
}
pub fn call_host_current_time_millis() -> i64 {
    unsafe { Host::current_time_millis() as i64 }
}
pub fn call_host_measure_text(text: &str, face: i32, style: i32, size: i32) -> i32 {
    unsafe { Host::measure_text(text.as_ptr(), text.len() as i32, face, style, size) }
}
pub fn call_host_font_metrics(face: i32, style: i32, size: i32) -> (i32, i32) {
    let packed = unsafe { Host::font_metrics(face, style, size) };
    let height = packed >> 16;
    let baseline = packed & 0xFFFF;
    (height, baseline)
}

pub fn call_host_mono_time_millis() -> u64 {
    unsafe { Host::mono_time_millis() as u64 }
}

pub fn call_host_get_media_time(handle: i32) -> i64 {
    unsafe { Host::get_media_time(handle) }
}

pub fn call_host_get_media_duration(handle: i32) -> i64 {
    unsafe { Host::get_media_duration(handle) }
}

pub fn call_host_set_media_time(handle: i32, time_ms: i64) {
    unsafe { Host::set_media_time(handle, time_ms) }
}

pub fn call_host_get_locale() -> String {
    let mut buf = vec![0u8; 32];
    let len = unsafe { Host::get_locale(buf.as_mut_ptr(), buf.len() as i32) };
    if len <= 0 {
        return "zh-CN".to_string();
    }

    let len = len as usize;
    if len > buf.len() {
        buf.resize(len, 0);
        let len2 = unsafe { Host::get_locale(buf.as_mut_ptr(), buf.len() as i32) };
        if len2 <= 0 {
            return "zh-CN".to_string();
        }
    }

    match std::str::from_utf8(&buf[..len.min(buf.len())]) {
        Ok(locale) => {
            let locale = locale.trim();
            if locale.is_empty() {
                "zh-CN".to_string()
            } else {
                locale.to_string()
            }
        }
        Err(_) => "zh-CN".to_string(),
    }
}
