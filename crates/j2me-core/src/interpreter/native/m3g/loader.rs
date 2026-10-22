use crate::scheduler::virtual_thread::YieldReason;
use crate::vm::{AsyncRequestType, AsyncResult, SharedVm};

use crate::interpreter::native::registry::{NativeContext, NativeRegistry, NativeResult};

use super::resource_store::Image2DData;
use super::{array_as_ptr, ffi, set_m3g_error_exception, M3G_IMAGE_FORMAT_RGBA};

const M3G_FILE_IDENTIFIER: [u8; 12] = [
    0xAB, 0x4A, 0x53, 0x52, 0x31, 0x38, 0x34, 0xBB, 0x0D, 0x0A, 0x1A, 0x0A,
];
const M3G_SECTION_HEADER_BYTES: usize = 9;
const M3G_CHECKSUM_BYTES: usize = 4;
const M3G_OBJECT_HEADER_BYTES: usize = 5;
const M3G_OBJECT_EXTERNAL_REFERENCE: u8 = 255;
const PNG_FILE_IDENTIFIER: [u8; 8] = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];

enum ExternalImportResult {
    Complete,
    Pending {
        resource_name: String,
        image_bytes: Vec<u8>,
    },
    Failed,
}

enum DecodeM3gDataResult {
    Ready(u32),
    Pending {
        resource_name: String,
        image_bytes: Vec<u8>,
    },
}

enum DecodeM3gHandlesResult {
    Complete(Vec<u32>),
    Pending {
        resource_name: String,
        image_bytes: Vec<u8>,
    },
    Failed,
}

fn load(ctx: &mut NativeContext<'_>, interface: i32, data_ref: u32, offset: i32) -> u32 {
    let offset = offset as usize;
    if data_ref == 0 {
        return 0;
    }
    let len = ctx.vm.heap.array_length(data_ref) as usize;
    if offset > len {
        return 0;
    }
    let ptr = unsafe { array_as_ptr(ctx.vm, data_ref).add(offset) };
    match decode_m3g_data(ctx.vm, interface as usize, (len - offset) as i32, ptr, None) {
        DecodeM3gDataResult::Ready(arr) => arr,
        DecodeM3gDataResult::Pending { .. } => empty_int_array(ctx.vm),
    }
}

fn load_resource(ctx: &mut NativeContext<'_>, interface: i32, name_ref: u32) -> NativeResult<u32> {
    let interface = interface as usize;
    let mut name = ctx.vm.get_string_value(name_ref).unwrap_or_default();
    if let Some(stripped) = name.strip_prefix('/') {
        name = stripped.to_string();
    }

    if let Some((_rid, request, result)) = ctx
        .vm
        .take_resolved_async_for_thread(ctx.thread_id, |result| {
            matches!(result, AsyncResult::M3gImageRead { .. })
        })
    {
        if let (
            AsyncRequestType::DecodeM3gExternalImage { resource_name, .. },
            AsyncResult::M3gImageRead {
                width,
                height,
                pixels,
            },
        ) = (request.request_type, result)
        {
            if width > 0 && height > 0 && pixels.len() >= (width * height * 4).max(0) as usize {
                let handle = unsafe {
                    ffi::m3gCreateImage(interface, M3G_IMAGE_FORMAT_RGBA, width, height, 0)
                };
                if handle != 0 {
                    unsafe {
                        ffi::m3gSetImage(handle as usize, pixels.as_ptr());
                        ffi::m3gCommitImage(handle as usize);
                    }
                    ctx.vm.m3g_resources.images.insert(
                        handle as u32,
                        Image2DData {
                            format: M3G_IMAGE_FORMAT_RGBA,
                            width,
                            height,
                        },
                    );
                    unsafe {
                        ffi::m3gAddRef(handle as usize);
                    }
                    ctx.vm
                        .m3g_resources
                        .external_resources
                        .insert(resource_name, vec![handle]);
                }
            }
        }
    }

    let Some(data) = ctx.vm.get_resource(&name) else {
        return NativeResult::Ready(0);
    };
    match decode_m3g_data(
        ctx.vm,
        interface,
        data.len() as i32,
        data.as_ptr(),
        Some(&name),
    ) {
        DecodeM3gDataResult::Ready(arr) => NativeResult::Ready(arr),
        DecodeM3gDataResult::Pending {
            resource_name,
            image_bytes,
        } => {
            let request_id = ctx.vm.issue_async_request(
                ctx.thread_id,
                AsyncRequestType::DecodeM3gExternalImage {
                    resource_name,
                    image_bytes,
                },
            );
            NativeResult::Yield(YieldReason::AsyncWait { request_id })
        }
    }
}

fn decode_m3g_data(
    vm: &mut SharedVm,
    interface: usize,
    bytes: i32,
    ptr: *const u8,
    base: Option<&str>,
) -> DecodeM3gDataResult {
    let handles = match decode_m3g_handles(vm, interface, bytes, ptr, base, &mut Vec::new()) {
        DecodeM3gHandlesResult::Complete(handles) => handles,
        DecodeM3gHandlesResult::Pending {
            resource_name,
            image_bytes,
        } => {
            return DecodeM3gDataResult::Pending {
                resource_name,
                image_bytes,
            }
        }
        DecodeM3gHandlesResult::Failed => return DecodeM3gDataResult::Ready(empty_int_array(vm)),
    };
    let class = vm.class_repo.get_or_register_array_class("[I");
    let array = vm.new_array(class, handles.len() as u32, 4);
    for (index, handle) in handles.into_iter().enumerate() {
        vm.heap.array_write_i32(array, index as u32, handle as i32);
    }
    DecodeM3gDataResult::Ready(array)
}

fn empty_int_array(vm: &mut SharedVm) -> u32 {
    let class = vm.class_repo.get_or_register_array_class("[I");
    vm.new_array(class, 0, 4)
}

fn decode_m3g_handles(
    vm: &mut SharedVm,
    interface: usize,
    bytes: i32,
    ptr: *const u8,
    base: Option<&str>,
    resolving: &mut Vec<String>,
) -> DecodeM3gHandlesResult {
    let loader = unsafe { ffi::m3gCreateLoader(interface) };
    if loader == 0 {
        vm.pending_exception_str = Some("java.lang.OutOfMemoryError".to_string());
        return DecodeM3gHandlesResult::Failed;
    }
    let data = if bytes > 0 && !ptr.is_null() {
        unsafe { std::slice::from_raw_parts(ptr, bytes as usize) }
    } else {
        &[]
    };
    match import_external_references(vm, interface, loader as usize, data, base, resolving) {
        ExternalImportResult::Complete => {}
        ExternalImportResult::Pending {
            resource_name,
            image_bytes,
        } => {
            unsafe {
                ffi::m3gDeleteObject(loader as usize);
            }
            return DecodeM3gHandlesResult::Pending {
                resource_name,
                image_bytes,
            };
        }
        ExternalImportResult::Failed => {
            unsafe {
                ffi::m3gDeleteObject(loader as usize);
            }
            return DecodeM3gHandlesResult::Failed;
        }
    }
    let mut consumed = 0;
    let mut requested = 8;
    while consumed < bytes {
        let chunk = requested.max(1).min(bytes - consumed);
        requested =
            unsafe { ffi::m3gDecodeData(loader as usize, chunk, ptr.add(consumed as usize)) };
        consumed += chunk;
        let error = unsafe { ffi::m3gGetError(interface) };
        if error != 0 {
            unsafe {
                ffi::m3gDeleteObject(loader as usize);
            }
            set_m3g_error_exception(vm, error);
            return DecodeM3gHandlesResult::Failed;
        }
        if requested <= 0 {
            break;
        }
    }
    let handles = unsafe { ffi::m3gGetLoadedObjects(loader as usize) };
    for &handle in &handles {
        if handle != 0 {
            unsafe {
                ffi::m3gAddRef(handle as usize);
            }
        }
    }
    unsafe {
        ffi::m3gDeleteObject(loader as usize);
    }
    DecodeM3gHandlesResult::Complete(handles)
}

fn import_external_references(
    vm: &mut SharedVm,
    interface: usize,
    loader: usize,
    data: &[u8],
    base: Option<&str>,
    resolving: &mut Vec<String>,
) -> ExternalImportResult {
    for uri in scan_external_references(data) {
        let name = resolve_external_resource_name(base, &uri);
        if resolving.iter().any(|entry| entry == &name) {
            vm.pending_exception_str = Some("java.io.IOException".to_string());
            return ExternalImportResult::Failed;
        }
        if let Some(imported) = vm
            .m3g_resources
            .external_resources
            .get(&name)
            .filter(|handles| !handles.is_empty())
        {
            unsafe { ffi::m3gImportObjects(loader, imported) }
            continue;
        }
        let Some(data) = vm.get_resource(&name) else {
            vm.pending_exception_str = Some("java.io.IOException".to_string());
            return ExternalImportResult::Failed;
        };
        if is_png_data(&data) {
            return ExternalImportResult::Pending {
                resource_name: name,
                image_bytes: data.as_ref().clone(),
            };
        }
        resolving.push(name.clone());
        let handles = match decode_m3g_handles(
            vm,
            interface,
            data.len() as i32,
            data.as_ptr(),
            Some(&name),
            resolving,
        ) {
            DecodeM3gHandlesResult::Complete(handles) => handles,
            DecodeM3gHandlesResult::Pending {
                resource_name,
                image_bytes,
            } => {
                resolving.pop();
                return ExternalImportResult::Pending {
                    resource_name,
                    image_bytes,
                };
            }
            DecodeM3gHandlesResult::Failed => {
                resolving.pop();
                return ExternalImportResult::Failed;
            }
        };
        resolving.pop();
        if !handles.is_empty() {
            unsafe { ffi::m3gImportObjects(loader, &handles) }
        }
    }
    ExternalImportResult::Complete
}

fn is_png_data(data: &[u8]) -> bool {
    data.starts_with(&PNG_FILE_IDENTIFIER)
}

fn scan_external_references(data: &[u8]) -> Vec<String> {
    if !data.starts_with(&M3G_FILE_IDENTIFIER) {
        return Vec::new();
    }
    let mut refs = Vec::new();
    let mut pos = M3G_FILE_IDENTIFIER.len();
    while pos + M3G_SECTION_HEADER_BYTES <= data.len() {
        let compressed = data[pos];
        let Some(section_len) = read_u32(data, pos + 1).map(|value| value as usize) else {
            break;
        };
        if section_len < M3G_SECTION_HEADER_BYTES + M3G_CHECKSUM_BYTES
            || pos + section_len > data.len()
        {
            break;
        }
        if compressed == 0 {
            let mut object = pos + M3G_SECTION_HEADER_BYTES;
            let end = pos + section_len - M3G_CHECKSUM_BYTES;
            while object + M3G_OBJECT_HEADER_BYTES <= end {
                let object_type = data[object];
                let Some(length) = read_u32(data, object + 1).map(|value| value as usize) else {
                    break;
                };
                let start = object + M3G_OBJECT_HEADER_BYTES;
                let end_object = start.saturating_add(length);
                if end_object > end {
                    break;
                }
                if object_type == M3G_OBJECT_EXTERNAL_REFERENCE {
                    if let Some(nul) = data[start..end_object].iter().position(|byte| *byte == 0) {
                        if let Ok(uri) = std::str::from_utf8(&data[start..start + nul]) {
                            if !uri.is_empty() {
                                refs.push(uri.to_string());
                            }
                        }
                    }
                }
                object = end_object;
            }
        }
        pos += section_len;
    }
    refs
}

fn read_u32(data: &[u8], offset: usize) -> Option<u32> {
    Some(u32::from_le_bytes(
        data.get(offset..offset + 4)?.try_into().ok()?,
    ))
}

fn resolve_external_resource_name(base: Option<&str>, uri: &str) -> String {
    let uri = uri.strip_prefix('/').unwrap_or(uri);
    if uri.contains("://") || uri.starts_with('/') || uri.contains('/') {
        return uri.to_string();
    }
    if let Some(base) = base
        .and_then(|name| name.rsplit_once('/').map(|(dir, _)| dir))
        .filter(|dir| !dir.is_empty())
    {
        return format!("{base}/{uri}");
    }
    uri.to_string()
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.Loader._load(I[BI)[I" => load(interface: i32, data: u32, offset: i32) -> u32),
        crate::native_bind!(async static "javax.microedition.m3g.Loader._loadResource(ILjava/lang/String;)[I" => load_resource(interface: i32, name: u32) -> u32),
    ];
    registry.register_bindings(&bindings);
}
