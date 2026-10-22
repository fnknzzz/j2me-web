use crate::scheduler::virtual_thread::YieldReason;
use crate::vm::{AsyncRequestType, AsyncResult, GraphicsState};

use crate::interpreter::native::registry::{NativeContext, NativeRegistry, NativeResult};

// --- Individual native handler functions for ID-based dispatch ---

fn image_decode(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    image_data: u32,
    bytes: u32,
    offset: i32,
    length: i32,
) -> NativeResult<()> {
    if let Some((_rid, request, result)) =
        ctx.vm.take_resolved_async_for_thread(ctx.thread_id, |r| {
            matches!(r, AsyncResult::ImageDecoded { .. })
        })
    {
        let original_sid = match &request.request_type {
            AsyncRequestType::DecodeImage { surface_id, .. } => Some(*surface_id),
            _ => None,
        };

        if let AsyncResult::ImageDecoded { width, height } = result {
            let sid = original_sid.unwrap_or_else(|| ctx.vm.alloc_surface_id());

            if let Some(w_off) = ctx
                .vm
                .class_repo
                .find_field_offset("javax.microedition.lcdui.ImageData", "width")
            {
                ctx.vm.heap.write_u32(image_data + w_off, width as u32);
            }
            if let Some(h_off) = ctx
                .vm
                .class_repo
                .find_field_offset("javax.microedition.lcdui.ImageData", "height")
            {
                ctx.vm.heap.write_u32(image_data + h_off, height as u32);
            }

            if width > 0 && height > 0 {
                ctx.vm.image_surface_map.insert(image_data, sid);
                ctx.vm.register_surface(sid, width, height);
            } else {
                ctx.vm.clear_image_surface_restore_source(image_data);
                ctx.vm.pending_exception_str =
                    Some("java.lang.IllegalArgumentException".to_string());
            }
        }
        NativeResult::Ready(())
    } else {
        let mut buf = Vec::new();
        if bytes != 0 && length > 0 {
            buf = vec![0u8; length as usize];
            for i in 0..length {
                buf[i as usize] = ctx.vm.heap.array_read_i8(bytes, (offset + i) as u32) as u8;
            }
        }
        ctx.vm
            .try_mark_image_surface_as_jar_resource(image_data, &buf);

        let sid = ctx.vm.alloc_surface_id();
        let request_id = ctx.vm.issue_async_request(
            ctx.thread_id,
            AsyncRequestType::DecodeImage {
                surface_id: sid,
                image_bytes: buf,
            },
        );

        NativeResult::Yield(YieldReason::AsyncWait { request_id })
    }
}

fn image_init_surface(ctx: &mut NativeContext<'_>, this: u32, width: i32, height: i32) {
    ctx.vm.clear_image_surface_restore_source(this);
    let sid = ctx.vm.alloc_surface_id();
    ctx.vm.image_surface_map.insert(this, sid);
    ctx.vm.register_surface(sid, width, height);
}

fn image_get_rgb(
    ctx: &mut NativeContext<'_>,
    this: u32,
    rgb_data: u32,
    offset: i32,
    scanlength: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
) -> NativeResult<()> {
    if let Some((_rid, _request, result)) =
        ctx.vm.take_resolved_async_for_thread(ctx.thread_id, |r| {
            matches!(r, AsyncResult::PixelsRead { .. })
        })
    {
        if rgb_data != 0 {
            if let AsyncResult::PixelsRead { pixels } = result {
                for row in 0..height {
                    for col in 0..width {
                        let src_idx = (row * width + col) as usize;
                        let dst_idx = (offset + row * scanlength + col) as u32;
                        let argb = pixels.get(src_idx).copied().unwrap_or(0);
                        ctx.vm.heap.array_write_i32(rgb_data, dst_idx, argb);
                    }
                }
            }
        }
        return NativeResult::Ready(());
    }

    let surface_id = ctx.vm.image_surface_map.get(&this).copied().unwrap_or(0);

    if rgb_data != 0 && surface_id > 0 && width > 0 && height > 0 {
        let request_id = ctx.vm.issue_async_request(
            ctx.thread_id,
            AsyncRequestType::GetPixels {
                surface_id,
                x,
                y,
                width,
                height,
            },
        );
        return NativeResult::Yield(YieldReason::AsyncWait { request_id });
    }
    NativeResult::Ready(())
}

fn image_write_pixels(
    ctx: &mut NativeContext<'_>,
    this: u32,
    rgb_data: u32,
    offset: i32,
    scanlength: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    process_alpha: bool,
) {
    ctx.vm.clear_image_surface_restore_source(this);

    let surface_id = ctx.vm.image_surface_map.get(&this).copied().unwrap_or(0);

    if surface_id > 0 && rgb_data != 0 && width > 0 && height > 0 {
        let state = GraphicsState {
            surface_id,
            clip_x: x,
            clip_y: y,
            clip_w: width,
            clip_h: height,
            ..Default::default()
        };

        let pixel_count = (width * height) as usize;
        let mut pixels = Vec::with_capacity(pixel_count);
        for i in 0..pixel_count {
            let src_idx = (offset + (i as i32 / width) * scanlength + (i as i32 % width)) as u32;
            pixels.push(ctx.vm.heap.array_read_i32(rgb_data, src_idx));
        }
        ctx.vm
            .append_draw_rgba(state, x, y, width, height, process_alpha, &pixels);
    }
}

fn mark_image_copy(ctx: &mut NativeContext<'_>, dest: u32, source: u32) {
    ctx.vm.mark_image_surface_as_copy(dest, source);
}

fn mark_image_region(
    ctx: &mut NativeContext<'_>,
    dest: u32,
    source: u32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    transform: i32,
) {
    ctx.vm
        .mark_image_surface_as_region(dest, source, x, y, width, height, transform);
}

fn suite_image_cache_load(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _image_data: u32,
    _id: i32,
    _filename: u32,
) -> bool {
    false
}
fn suite_id_to_string(ctx: &mut NativeContext<'_>, _suite_id: i32) -> u32 {
    ctx.vm.create_string("1")
}
fn midlet_suite_storage_id(_ctx: &mut NativeContext<'_>, _suite_id: i32) -> i32 {
    0
}
fn lock_midlet_suite(_ctx: &mut NativeContext<'_>, _id: i32, _locked: bool) {}
fn unlock_midlet_suite(_ctx: &mut NativeContext<'_>, _id: i32) {}
fn install_info_load(_ctx: &mut NativeContext<'_>, _this: u32) {}

fn suite_properties_load(ctx: &mut NativeContext<'_>, _this: u32) -> u32 {
    let manifest_data = ctx.vm.get_resource("META-INF/MANIFEST.MF");
    if let Some(data) = manifest_data {
        let text = String::from_utf8_lossy(&data);
        let mut pairs: Vec<(String, String)> = Vec::new();
        let mut current_key = String::new();
        let mut current_val = String::new();
        for line in text.lines() {
            if line.starts_with(' ') || line.starts_with('\t') {
                current_val.push_str(line[1..].trim_end());
            } else if let Some(colon_pos) = line.find(':') {
                if !current_key.is_empty() {
                    pairs.push((current_key.clone(), current_val.clone()));
                }
                current_key = line[..colon_pos].trim().to_string();
                current_val = line[colon_pos + 1..].trim().to_string();
            }
        }
        if !current_key.is_empty() {
            pairs.push((current_key, current_val));
        }
        let string_class = ctx.vm.class_repo.get_class_id("[L").unwrap_or(0);
        let arr_len = (pairs.len() * 2) as u32;
        let arr = ctx.vm.new_array(string_class, arr_len, 4);
        for (i, (k, v)) in pairs.iter().enumerate() {
            let k_ref = ctx.vm.create_string(k);
            let v_ref = ctx.vm.create_string(v);
            ctx.vm.heap.array_write_u32(arr, (i * 2) as u32, k_ref);
            ctx.vm.heap.array_write_u32(arr, (i * 2 + 1) as u32, v_ref);
        }
        arr
    } else {
        0
    }
}

fn mutex_get_id(_ctx: &mut NativeContext<'_>, _name: u32) -> i32 {
    0
}
fn mutex_lock(_ctx: &mut NativeContext<'_>, _id: i32) {}
fn mutex_unlock(_ctx: &mut NativeContext<'_>, _id: i32) {}

fn garbage_collect_images(ctx: &mut NativeContext<'_>, _full_gc: bool) {
    ctx.vm.force_gc = true;
}

pub fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.lcdui.ImageDataFactory.garbageCollectImages(Z)V" => garbage_collect_images(full_gc: bool) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ImageData.initSurface(II)V" => image_init_surface(this: u32, width: i32, height: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ImageData.writePixels([IIIIIIIZ)V" => image_write_pixels(this: u32, rgb_data: u32, offset: i32, scanlength: i32, x: i32, y: i32, width: i32, height: i32, process_alpha: bool) -> ()),
        crate::native_bind!(static "javax.microedition.lcdui.ImageDataFactory.markImmutableImageDataCopy(Ljavax/microedition/lcdui/ImageData;Ljavax/microedition/lcdui/ImageData;)V" => mark_image_copy(dest: u32, source: u32) -> ()),
        crate::native_bind!(static "javax.microedition.lcdui.ImageDataFactory.markImmutableImageDataRegion(Ljavax/microedition/lcdui/ImageData;Ljavax/microedition/lcdui/ImageData;IIIII)V" => mark_image_region(dest: u32, source: u32, x: i32, y: i32, width: i32, height: i32, transform: i32) -> ()),
        crate::native_bind!(async instance "javax.microedition.lcdui.ImageDataFactory.createImmutableImageDecodeImage(Ljavax/microedition/lcdui/ImageData;[BII)V" => image_decode(this: u32, image_data: u32, bytes: u32, offset: i32, length: i32) -> ()),
        crate::native_bind!(async instance "javax.microedition.lcdui.ImageData.getRGB([IIIIIII)V" => image_get_rgb(this: u32, rgb_data: u32, offset: i32, scanlength: i32, x: i32, y: i32, width: i32, height: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.SuiteImageCacheImpl.loadAndCreateImmutableImageDataFromCache0(Ljavax/microedition/lcdui/ImageData;ILjava/lang/String;)Z" => suite_image_cache_load(this: u32, image_data: u32, id: i32, filename: u32) -> bool),
        crate::native_bind!(static "com.sun.midp.midletsuite.MIDletSuiteStorage.suiteIdToString(I)Ljava/lang/String;" => suite_id_to_string(suite_id: i32) -> u32),
        crate::native_bind!(static "com.sun.midp.midletsuite.MIDletSuiteStorage.getMidletSuiteStorageId(I)I" => midlet_suite_storage_id(suite_id: i32) -> i32),
        crate::native_bind!(static "com.sun.midp.midletsuite.MIDletSuiteImpl.lockMIDletSuite(IZ)V" => lock_midlet_suite(id: i32, locked: bool) -> ()),
        crate::native_bind!(static "com.sun.midp.midletsuite.MIDletSuiteImpl.unlockMIDletSuite(I)V" => unlock_midlet_suite(id: i32) -> ()),
        crate::native_bind!(instance "com.sun.midp.midletsuite.InstallInfo.load()V" => install_info_load(this: u32) -> ()),
        crate::native_bind!(static "com.sun.midp.util.isolate.InterIsolateMutex.getID0(Ljava/lang/String;)I" => mutex_get_id(name: u32) -> i32),
        crate::native_bind!(static "com.sun.midp.util.isolate.InterIsolateMutex.lock0(I)V" => mutex_lock(id: i32) -> ()),
        crate::native_bind!(static "com.sun.midp.util.isolate.InterIsolateMutex.unlock0(I)V" => mutex_unlock(id: i32) -> ()),
        crate::native_bind!(instance "com.sun.midp.midletsuite.SuiteProperties.load()[Ljava/lang/String;" => suite_properties_load(this: u32) -> u32),
    ];
    registry.register_bindings(&bindings);
}
