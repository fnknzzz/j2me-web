pub mod cheat_engine;
pub mod classfile;
pub mod heap;
pub mod host_api;
pub mod interpreter;
pub mod minijvm;
mod runtime;
pub mod scheduler;
pub mod vm;

pub use j2me_core_native_macros::native_bind;

#[cfg(not(target_arch = "wasm32"))]
pub mod host_native;
#[cfg(target_arch = "wasm32")]
mod host_wasm;

mod host;

pub use host::*;
pub use runtime::*;

#[no_mangle]
pub unsafe extern "C" fn m3gInflateBlock(
    src_len: i32,
    src_ptr: *const u8,
    dst_len: i32,
    dst_ptr: *mut u8,
) -> i32 {
    if src_len <= 0 || dst_len < 0 || src_ptr.is_null() || dst_ptr.is_null() {
        return 0;
    }

    let src = std::slice::from_raw_parts(src_ptr, src_len as usize);
    let mut decoder = flate2::read::ZlibDecoder::new(src);
    let mut decoded = Vec::new();
    if std::io::Read::read_to_end(&mut decoder, &mut decoded).is_err() {
        return 0;
    }
    if decoded.len() > dst_len as usize {
        return 0;
    }

    std::ptr::copy_nonoverlapping(decoded.as_ptr(), dst_ptr, decoded.len());
    decoded.len() as i32
}

#[cfg(test)]
mod m3gcore_ffi_test {
    use crate::interpreter::native::m3g::ffi;

    #[test]
    fn test_m3gcore_create_interface() {
        let handle = ffi::create_interface();
        assert_ne!(
            handle, 0,
            "m3gCreateInterface should return valid handle with malloc"
        );
        // Clean up
        unsafe { ffi::m3gDeleteInterface(handle as usize) };
    }

    #[test]
    fn test_m3gcore_create_context() {
        let iface = ffi::create_interface();
        assert_ne!(iface, 0);
        let ctx = unsafe { ffi::m3gCreateContext(iface as usize) };
        assert_ne!(
            ctx, 0,
            "m3gCreateContext should return valid render context"
        );
        // Clean up
        unsafe {
            ffi::m3gDeleteObject(ctx as usize);
            ffi::m3gDeleteInterface(iface as usize);
        }
    }

    #[test]
    fn test_m3gcore_camera_invalid_perspective_sets_error() {
        let iface = ffi::create_interface();
        assert_ne!(iface, 0);
        let camera = unsafe { ffi::m3gCreateCamera(iface as usize) };
        assert_ne!(camera, 0);

        unsafe {
            ffi::m3gSetPerspective(camera as usize, 0.0, 1.0, 1.0, 10.0);
            assert_eq!(ffi::m3gGetError(iface as usize), 2);
            ffi::m3gDeleteObject(camera as usize);
            ffi::m3gDeleteInterface(iface as usize);
        }
    }
}
