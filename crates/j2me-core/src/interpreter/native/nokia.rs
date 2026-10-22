use crate::heap::NULL;
use crate::interpreter::native::graphics2d;
use crate::interpreter::native::registry::{NativeContext, NativeRegistry, NativeResult};
use crate::scheduler::virtual_thread::YieldReason;
use crate::vm::{cmd, AsyncRequestType, AsyncResult, GraphicsState, SharedVm};

/// Get the GraphicsState for a DirectGraphicsImp by reading its `graphics` field.
fn get_dg_graphics_state(vm: &SharedVm, this_ref: u32) -> GraphicsState {
    let graphics_ref = vm
        .class_repo
        .find_field_offset("com.nokia.mid.ui.DirectGraphicsImp", "graphics")
        .map(|off| vm.heap.read_u32(this_ref + off))
        .unwrap_or(NULL);
    vm.graphics_states
        .get(&graphics_ref)
        .copied()
        .unwrap_or_else(|| GraphicsState::default_for_display(vm.display_width, vm.display_height))
}

/// Resolve an Image object heap ref to its surface_id via Image.imageData → image_surface_map.
/// Returns -1 if the image has no surface (e.g. decode failed).
fn resolve_image_surface_id(vm: &SharedVm, image_ref: u32) -> i32 {
    if image_ref == NULL {
        return -1;
    }
    let image_data_ref = vm
        .class_repo
        .find_field_offset("javax.microedition.lcdui.Image", "imageData")
        .map(|off| vm.heap.read_u32(image_ref + off))
        .unwrap_or(image_ref);
    vm.image_surface_map
        .get(&image_data_ref)
        .copied()
        .unwrap_or(-1)
}

fn read_image_width(vm: &SharedVm, image_ref: u32) -> i32 {
    vm.class_repo
        .find_field_offset("javax.microedition.lcdui.Image", "width")
        .map(|off| vm.heap.read_u32(image_ref + off) as i32)
        .unwrap_or(0)
}

fn read_image_height(vm: &SharedVm, image_ref: u32) -> i32 {
    vm.class_repo
        .find_field_offset("javax.microedition.lcdui.Image", "height")
        .map(|off| vm.heap.read_u32(image_ref + off) as i32)
        .unwrap_or(0)
}

/// Convert Nokia manipulation flags to MIDP transform (0-7).
/// Nokia rotations are counterclockwise, MIDP rotations are clockwise.
fn manipulation_to_midp_transform(manipulation: i32) -> i32 {
    let flip_h = (manipulation & 0x2000) != 0;
    let flip_v = (manipulation & 0x4000) != 0;
    let rot = manipulation & 0x0FFF; // extract rotation degrees if encoded directly

    match (flip_h, flip_v, rot) {
        // Horizontal + vertical flipping is equivalent to rotation by 180,
        // then compose with Nokia's counter-clockwise rotation.
        (true, true, 0) => 3,
        (true, true, 90) => 5,
        (true, true, 180) => 0,
        (true, true, 270) => 6,

        // Horizontal flip, with Nokia rotations converted to MIDP transforms.
        (true, false, 0) => 2,
        (true, false, 90) => 7,
        (true, false, 180) => 1,
        (true, false, 270) => 4,

        // Vertical flip is represented by the corresponding MIDP mirror+rotate.
        (false, true, 0) => 1,
        (false, true, 90) => 4,
        (false, true, 180) => 2,
        (false, true, 270) => 7,

        // No flipping: Nokia rotations are counter-clockwise; MIDP rotations are clockwise.
        (false, false, 0) => 0,
        (false, false, 90) => 6,
        (false, false, 180) => 3,
        (false, false, 270) => 5,

        _ => -1,
    }
}

/// Apply manipulation (rotation/flip) to an ARGB pixel array.
/// Returns (output_pixels, out_w, out_h).
fn apply_manipulation(
    pixels: &[i32],
    width: i32,
    height: i32,
    manipulation: i32,
) -> (Vec<i32>, i32, i32) {
    let transform = manipulation_to_midp_transform(manipulation);
    if transform == 0 {
        return (pixels.to_vec(), width, height);
    }

    let (out_w, out_h) = if transform >= 4 {
        (height, width)
    } else {
        (width, height)
    };

    let mut out = vec![0i32; (out_w * out_h) as usize];

    for row in 0..height {
        for col in 0..width {
            let src_idx = (row * width + col) as usize;
            if src_idx >= pixels.len() {
                continue;
            }
            let pixel = pixels[src_idx];

            let (ox, oy) = match transform {
                1 => (col, height - 1 - row),
                2 => (width - 1 - col, row),
                3 => (width - 1 - col, height - 1 - row),
                4 => (row, col),
                5 => (height - 1 - row, col),
                6 => (row, width - 1 - col),
                7 => (height - 1 - row, width - 1 - col),
                _ => (col, row),
            };

            let dst_idx = (oy * out_w + ox) as usize;
            if dst_idx < out.len() {
                out[dst_idx] = pixel;
            }
        }
    }

    (out, out_w, out_h)
}

/// Write an ARGB pixel slice as host-ready RGBA8.
fn emit_draw_rgb(
    vm: &mut SharedVm,
    state: &GraphicsState,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    pixels: &[i32],
    process_alpha: i32,
) {
    vm.append_draw_rgba(*state, x, y, width, height, process_alpha != 0, pixels);
}

/// Convert a 16-bit short pixel to 32-bit ARGB based on format.
fn short_to_argb(s: u16, format: i32) -> i32 {
    match format {
        // TYPE_USHORT_4444_ARGB
        4444 => {
            let a = ((s >> 12) & 0xF) as u32 * 17;
            let r = ((s >> 8) & 0xF) as u32 * 17;
            let g = ((s >> 4) & 0xF) as u32 * 17;
            let b = (s & 0xF) as u32 * 17;
            ((a << 24) | (r << 16) | (g << 8) | b) as i32
        }
        // TYPE_USHORT_555_RGB
        555 => {
            let r = ((s >> 10) & 0x1F) as u32 * 255 / 31;
            let g = ((s >> 5) & 0x1F) as u32 * 255 / 31;
            let b = (s & 0x1F) as u32 * 255 / 31;
            (0xFF00_0000 | (r << 16) | (g << 8) | b) as i32
        }
        // TYPE_USHORT_1555_ARGB
        1555 => {
            let a: u32 = if (s >> 15) != 0 { 0xFF } else { 0x00 };
            let r = ((s >> 10) & 0x1F) as u32 * 255 / 31;
            let g = ((s >> 5) & 0x1F) as u32 * 255 / 31;
            let b = (s & 0x1F) as u32 * 255 / 31;
            ((a << 24) | (r << 16) | (g << 8) | b) as i32
        }
        // TYPE_USHORT_565_RGB (default)
        _ => {
            let r = ((s >> 11) & 0x1F) as u32 * 255 / 31;
            let g = ((s >> 5) & 0x3F) as u32 * 255 / 63;
            let b = (s & 0x1F) as u32 * 255 / 31;
            (0xFF00_0000 | (r << 16) | (g << 8) | b) as i32
        }
    }
}

/// Convert 32-bit ARGB to 16-bit short based on format.
fn argb_to_short(argb: i32, format: i32) -> i16 {
    let a = ((argb >> 24) & 0xFF) as u32;
    let r = ((argb >> 16) & 0xFF) as u32;
    let g = ((argb >> 8) & 0xFF) as u32;
    let b = (argb & 0xFF) as u32;
    match format {
        4444 => {
            let sa = (a >> 4) & 0xF;
            let sr = (r >> 4) & 0xF;
            let sg = (g >> 4) & 0xF;
            let sb = (b >> 4) & 0xF;
            ((sa << 12) | (sr << 8) | (sg << 4) | sb) as i16
        }
        555 => {
            let sr = (r * 31 / 255) & 0x1F;
            let sg = (g * 31 / 255) & 0x1F;
            let sb = (b * 31 / 255) & 0x1F;
            ((sr << 10) | (sg << 5) | sb) as i16
        }
        1555 => {
            let sa: u32 = if a >= 128 { 1 } else { 0 };
            let sr = (r * 31 / 255) & 0x1F;
            let sg = (g * 31 / 255) & 0x1F;
            let sb = (b * 31 / 255) & 0x1F;
            ((sa << 15) | (sr << 10) | (sg << 5) | sb) as i16
        }
        // 565 default
        _ => {
            let sr = (r * 31 / 255) & 0x1F;
            let sg = (g * 63 / 255) & 0x3F;
            let sb = (b * 31 / 255) & 0x1F;
            ((sr << 11) | (sg << 5) | sb) as i16
        }
    }
}

// --- Individual native handler functions ---

fn dg_draw_image(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    image_ref: u32,
    x: i32,
    y: i32,
    anchor: i32,
    manipulation: i32,
) {
    let vm = &mut ctx.vm;

    let state = get_dg_graphics_state(vm, this_ref);
    let sid = resolve_image_surface_id(vm, image_ref);
    if sid < 0 {
        return;
    }

    if manipulation == 0 {
        vm.append_draw_image(state, sid, x, y, anchor);
    } else {
        let w = read_image_width(vm, image_ref);
        let h = read_image_height(vm, image_ref);
        let transform = manipulation_to_midp_transform(manipulation);
        let buf = &mut vm.command_buffer;
        buf.push(cmd::DRAW_REGION);
        state.write_text_state_to_buf(buf);
        buf.extend_from_slice(&(sid as u32).to_le_bytes());
        buf.extend_from_slice(&0i32.to_le_bytes()); // xSrc
        buf.extend_from_slice(&0i32.to_le_bytes()); // ySrc
        buf.extend_from_slice(&w.to_le_bytes()); // wSrc
        buf.extend_from_slice(&h.to_le_bytes()); // hSrc
        buf.extend_from_slice(&transform.to_le_bytes());
        buf.extend_from_slice(&x.to_le_bytes()); // xDest
        buf.extend_from_slice(&y.to_le_bytes()); // yDest
        buf.extend_from_slice(&anchor.to_le_bytes());
    }
}

fn dg_draw_pixels_int(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    pixels_ref: u32,
    _transparency: bool,
    offset: i32,
    scanlength: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    manipulation: i32,
    _format: i32,
) {
    let vm = &mut ctx.vm;

    let state = get_dg_graphics_state(vm, this_ref);
    if pixels_ref == NULL || width <= 0 || height <= 0 {
        return;
    }

    let mut src_pixels = Vec::with_capacity((width * height) as usize);
    for row in 0..height {
        for col in 0..width {
            let idx = (offset + row * scanlength + col) as u32;
            src_pixels.push(vm.heap.array_read_i32(pixels_ref, idx));
        }
    }

    if manipulation == 0 {
        emit_draw_rgb(vm, &state, x, y, width, height, &src_pixels, 1);
    } else {
        let (out_pixels, out_w, out_h) =
            apply_manipulation(&src_pixels, width, height, manipulation);
        emit_draw_rgb(vm, &state, x, y, out_w, out_h, &out_pixels, 1);
    }
}

fn dg_draw_pixels_short(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    pixels_ref: u32,
    _transparency: bool,
    offset: i32,
    scanlength: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    manipulation: i32,
    format: i32,
) {
    let vm = &mut ctx.vm;

    let state = get_dg_graphics_state(vm, this_ref);
    if pixels_ref == NULL || width <= 0 || height <= 0 {
        return;
    }

    let mut src_pixels = Vec::with_capacity((width * height) as usize);
    for row in 0..height {
        for col in 0..width {
            let idx = (offset + row * scanlength + col) as u32;
            let s = vm.heap.array_read_u16(pixels_ref, idx);
            src_pixels.push(short_to_argb(s, format));
        }
    }

    if manipulation == 0 {
        emit_draw_rgb(vm, &state, x, y, width, height, &src_pixels, 1);
    } else {
        let (out_pixels, out_w, out_h) =
            apply_manipulation(&src_pixels, width, height, manipulation);
        emit_draw_rgb(vm, &state, x, y, out_w, out_h, &out_pixels, 1);
    }
}

fn dg_draw_pixels_byte(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    pixels_ref: u32,
    mask_ref: u32,
    offset: i32,
    scanlength: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    manipulation: i32,
    _format: i32,
) {
    let vm = &mut ctx.vm;

    let state = get_dg_graphics_state(vm, this_ref);
    if pixels_ref == NULL || width <= 0 || height <= 0 {
        return;
    }

    let mut src_pixels = Vec::with_capacity((width * height) as usize);
    for row in 0..height {
        for col in 0..width {
            let idx = (offset + row * scanlength + col) as u32;
            let g = (vm.heap.array_read_i8(pixels_ref, idx) as u8) as u32;
            let a = if mask_ref != NULL {
                (vm.heap.array_read_i8(mask_ref, idx) as u8) as u32
            } else {
                0xFF
            };
            src_pixels.push(((a << 24) | (g << 16) | (g << 8) | g) as i32);
        }
    }

    if manipulation == 0 {
        emit_draw_rgb(vm, &state, x, y, width, height, &src_pixels, 1);
    } else {
        let (out_pixels, out_w, out_h) =
            apply_manipulation(&src_pixels, width, height, manipulation);
        emit_draw_rgb(vm, &state, x, y, out_w, out_h, &out_pixels, 1);
    }
}

fn dg_get_pixels_int(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    pixels_ref: u32,
    offset: i32,
    scanlength: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    _format: i32,
) -> NativeResult<()> {
    if let Some((_rid, _request, result)) =
        ctx.vm.take_resolved_async_for_thread(ctx.thread_id, |r| {
            matches!(r, AsyncResult::PixelsRead { .. })
        })
    {
        if pixels_ref != NULL {
            if let AsyncResult::PixelsRead { pixels } = result {
                for row in 0..height {
                    for col in 0..width {
                        let src_idx = (row * width + col) as usize;
                        let dst_idx = (offset + row * scanlength + col) as u32;
                        let argb = pixels.get(src_idx).copied().unwrap_or(0);
                        ctx.vm.heap.array_write_i32(pixels_ref, dst_idx, argb);
                    }
                }
            }
        }
        return NativeResult::Ready(());
    }

    let state = get_dg_graphics_state(ctx.vm, this_ref);
    if pixels_ref == NULL || width <= 0 || height <= 0 {
        return NativeResult::Ready(());
    }

    let request_id = ctx.vm.issue_async_request(
        ctx.thread_id,
        AsyncRequestType::GetPixels {
            surface_id: state.surface_id,
            x: x + state.translate_x,
            y: y + state.translate_y,
            width,
            height,
        },
    );
    NativeResult::Yield(YieldReason::AsyncWait { request_id })
}

fn dg_get_pixels_short(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    pixels_ref: u32,
    offset: i32,
    scanlength: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    format: i32,
) -> NativeResult<()> {
    if let Some((_rid, _request, result)) =
        ctx.vm.take_resolved_async_for_thread(ctx.thread_id, |r| {
            matches!(r, AsyncResult::PixelsRead { .. })
        })
    {
        if pixels_ref != NULL {
            if let AsyncResult::PixelsRead { pixels } = result {
                for row in 0..height {
                    for col in 0..width {
                        let src_idx = (row * width + col) as usize;
                        let dst_idx = (offset + row * scanlength + col) as u32;
                        let argb = pixels.get(src_idx).copied().unwrap_or(0);
                        ctx.vm.heap.array_write_i16(
                            pixels_ref,
                            dst_idx,
                            argb_to_short(argb, format),
                        );
                    }
                }
            }
        }
        return NativeResult::Ready(());
    }

    let state = get_dg_graphics_state(ctx.vm, this_ref);
    if pixels_ref == NULL || width <= 0 || height <= 0 {
        return NativeResult::Ready(());
    }

    let request_id = ctx.vm.issue_async_request(
        ctx.thread_id,
        AsyncRequestType::GetPixels {
            surface_id: state.surface_id,
            x: x + state.translate_x,
            y: y + state.translate_y,
            width,
            height,
        },
    );
    NativeResult::Yield(YieldReason::AsyncWait { request_id })
}

fn dg_get_pixels_byte(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    pixels_ref: u32,
    mask_ref: u32,
    offset: i32,
    scanlength: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    _format: i32,
) -> NativeResult<()> {
    if let Some((_rid, _request, result)) =
        ctx.vm.take_resolved_async_for_thread(ctx.thread_id, |r| {
            matches!(r, AsyncResult::PixelsRead { .. })
        })
    {
        if pixels_ref != NULL {
            if let AsyncResult::PixelsRead { pixels } = result {
                for row in 0..height {
                    for col in 0..width {
                        let src_idx = (row * width + col) as usize;
                        let dst_idx = (offset + row * scanlength + col) as u32;
                        let argb = pixels.get(src_idx).copied().unwrap_or(0);
                        let r = ((argb >> 16) & 0xFF) as u32;
                        let g = ((argb >> 8) & 0xFF) as u32;
                        let b = (argb & 0xFF) as u32;
                        let gray = ((r + g + b) / 3) as i8;
                        ctx.vm.heap.array_write_i8(pixels_ref, dst_idx, gray);
                        if mask_ref != NULL {
                            let a = ((argb >> 24) & 0xFF) as i8;
                            ctx.vm.heap.array_write_i8(mask_ref, dst_idx, a);
                        }
                    }
                }
            }
        }
        return NativeResult::Ready(());
    }

    let state = get_dg_graphics_state(ctx.vm, this_ref);
    if pixels_ref == NULL || width <= 0 || height <= 0 {
        return NativeResult::Ready(());
    }

    let request_id = ctx.vm.issue_async_request(
        ctx.thread_id,
        AsyncRequestType::GetPixels {
            surface_id: state.surface_id,
            x: x + state.translate_x,
            y: y + state.translate_y,
            width,
            height,
        },
    );
    NativeResult::Yield(YieldReason::AsyncWait { request_id })
}

fn du_set_pixels(ctx: &mut NativeContext<'_>, image_ref: u32, argb: i32) {
    let vm = &mut ctx.vm;
    if image_ref == NULL {
        return;
    }

    let w = read_image_width(vm, image_ref);
    let h = read_image_height(vm, image_ref);
    let sid = resolve_image_surface_id(vm, image_ref);

    if sid > 0 && w > 0 && h > 0 {
        let state = GraphicsState {
            surface_id: sid,
            color: 0,
            clip_x: 0,
            clip_y: 0,
            clip_w: w,
            clip_h: h,
            translate_x: 0,
            translate_y: 0,
            stroke_style: 0,
            font_face: 0,
            font_style: 0,
            font_size: 0,
        };
        let pixels = vec![argb; (w as usize).saturating_mul(h as usize)];
        vm.append_draw_rgba(state, 0, 0, w, h, true, &pixels);
    }
}

fn du_make_mutable(_ctx: &mut NativeContext<'_>, _image_ref: u32) {}

fn dc_start_vibra(_ctx: &mut NativeContext<'_>, _freq: i32, _duration: i64) {}

fn dc_stop_vibra(_ctx: &mut NativeContext<'_>) {}

fn sound_play(ctx: &mut NativeContext<'_>, this_ref: u32, loop_count: i32) {
    let vm = &mut ctx.vm;

    let state_off = vm
        .class_repo
        .find_field_offset("com.nokia.mid.sound.Sound", "state");
    let freq_off = vm
        .class_repo
        .find_field_offset("com.nokia.mid.sound.Sound", "freq");
    let duration_off = vm
        .class_repo
        .find_field_offset("com.nokia.mid.sound.Sound", "duration");
    let type_off = vm
        .class_repo
        .find_field_offset("com.nokia.mid.sound.Sound", "type");
    let gain_off = vm
        .class_repo
        .find_field_offset("com.nokia.mid.sound.Sound", "gain");
    let data_off = vm
        .class_repo
        .find_field_offset("com.nokia.mid.sound.Sound", "data");

    if let Some(off) = state_off {
        vm.heap.write_u32(this_ref + off, 0); // SOUND_PLAYING = 0
    }

    let typ = type_off
        .map(|off| vm.heap.read_u32(this_ref + off) as i32)
        .unwrap_or(0);
    let vol = gain_off
        .map(|off| vm.heap.read_u32(this_ref + off) as i32)
        .unwrap_or(100);

    // Check if data field has byte[] even when type says FORMAT_TONE.
    // Some games pass WAV/MIDI data with type=5 (FORMAT_TONE) — detect by data presence.
    let data_ref = data_off
        .map(|off| vm.heap.read_u32(this_ref + off))
        .unwrap_or(NULL);
    let has_data = data_ref != NULL;

    if typ == 5 && !has_data {
        // Pure FORMAT_TONE (freq + duration)
        let freq = freq_off
            .map(|off| vm.heap.read_u32(this_ref + off) as i32)
            .unwrap_or(0);
        let dur_lo = duration_off
            .map(|off| vm.heap.read_u32(this_ref + off))
            .unwrap_or(0);
        let dur_hi = duration_off
            .map(|off| vm.heap.read_u32(this_ref + off + 4))
            .unwrap_or(0);
        let duration = ((dur_hi as i64) << 32) | (dur_lo as u64 as i64);
        vm.command_buffer.push(cmd::MEDIA_PLAY_TONE);
        vm.command_buffer.extend_from_slice(&freq.to_le_bytes());
        vm.command_buffer
            .extend_from_slice(&(duration as i32).to_le_bytes());
        vm.command_buffer.extend_from_slice(&vol.to_le_bytes());
    } else if has_data {
        // Has byte[] data — play as audio (WAV or MIDI), regardless of type field
        let handle = vm
            .nokia_sound_handles
            .get(&this_ref)
            .copied()
            .unwrap_or_else(|| {
                let h = vm.alloc_media_player_id();
                vm.nokia_sound_handles.insert(this_ref, h);
                h
            });

        let len = vm.heap.array_length(data_ref) as usize;

        // Detect content type from data header
        let content_type = if len >= 4 {
            let b0 = vm.heap.read_i8(data_ref + 8) as u8;
            let b1 = vm.heap.read_i8(data_ref + 9) as u8;
            let b2 = vm.heap.read_i8(data_ref + 10) as u8;
            let b3 = vm.heap.read_i8(data_ref + 11) as u8;
            if b0 == b'R' && b1 == b'I' && b2 == b'F' && b3 == b'F' {
                "audio/x-wav"
            } else if b0 == b'M' && b1 == b'T' && b2 == b'h' && b3 == b'd' {
                "audio/midi"
            } else if typ == 7 {
                "audio/midi"
            } else {
                "audio/x-wav"
            }
        } else if typ == 7 {
            "audio/midi"
        } else {
            "audio/x-wav"
        };

        // Save audio data into MediaPlayerState for snapshot restore
        let audio_bytes: Vec<u8> = (0..len)
            .map(|i| vm.heap.read_i8(data_ref + 8 + i as u32) as u8)
            .collect();
        let ps = vm
            .media_players
            .entry(handle)
            .or_insert_with(|| crate::vm::MediaPlayerState {
                volume: 100,
                muted: false,
                state: 400,
                locator: String::new(),
                content_type: content_type.to_string(),
                data: Some(audio_bytes.clone()),
                listeners: Vec::new(),
                media_time_ms: 0,
            });
        ps.content_type = content_type.to_string();
        ps.data = Some(audio_bytes);
        ps.state = 400;

        // Write binary command directly — no JSON serialization
        let mmapi_vol = (vol * 100) / 255;
        let ct_bytes = content_type.as_bytes();
        let ct_padded = (ct_bytes.len() + 3) & !3;
        let data_padded = (len + 3) & !3;

        let buf = &mut vm.command_buffer;
        buf.push(cmd::MEDIA_CREATE_PLAYER_BINARY);
        buf.extend_from_slice(&(handle).to_le_bytes());
        buf.extend_from_slice(&loop_count.to_le_bytes());
        buf.extend_from_slice(&mmapi_vol.to_le_bytes());
        // content type (length-prefixed, 4-byte aligned)
        buf.extend_from_slice(&(ct_bytes.len() as u32).to_le_bytes());
        buf.extend_from_slice(ct_bytes);
        for _ in 0..(ct_padded - ct_bytes.len()) {
            buf.push(0);
        }
        // audio data (length-prefixed, 4-byte aligned)
        buf.extend_from_slice(&(len as u32).to_le_bytes());
        for i in 0..len {
            buf.push(vm.heap.read_i8(data_ref + 8 + i as u32) as u8);
        }
        for _ in 0..(data_padded - len) {
            buf.push(0);
        }

        // Nokia Sound.play() implies immediate playback — emit MEDIA_PLAY
        buf.push(cmd::MEDIA_PLAY);
        buf.extend_from_slice(&handle.to_le_bytes());
        buf.extend_from_slice(&0_i32.to_le_bytes());
        buf.extend_from_slice(&0_i32.to_le_bytes());
    }
}

fn dg_fill_polygon(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    x_points_ref: u32,
    x_offset: i32,
    y_points_ref: u32,
    y_offset: i32,
    n_points: i32,
    argb_color: i32,
) {
    let vm = &mut ctx.vm;

    if n_points < 3 {
        return;
    }
    let mut state = get_dg_graphics_state(vm, this_ref);

    // Override color from argbColor param if non-zero
    if argb_color != 0 {
        state.color = argb_color & 0x00FFFFFF;
    }

    // Read x and y arrays from heap into Vec first
    let x_len = vm.heap.array_length(x_points_ref) as i32;
    let y_len = vm.heap.array_length(y_points_ref) as i32;
    let n = n_points.min(x_len - x_offset).min(y_len - y_offset) as usize;
    if n < 3 {
        return;
    }

    let mut points: Vec<(i32, i32)> = Vec::with_capacity(n);
    for i in 0..n {
        let x = vm
            .heap
            .array_read_i32(x_points_ref, (x_offset as usize + i) as u32);
        let y = vm
            .heap
            .array_read_i32(y_points_ref, (y_offset as usize + i) as u32);
        points.push((x, y));
    }
    let size = vm
        .surface_dims
        .get(&state.surface_id)
        .copied()
        .unwrap_or((vm.display_width, vm.display_height));
    let gpu_points: Vec<(f64, f64)> = points.iter().map(|p| (p.0 as f64, p.1 as f64)).collect();
    graphics2d::emit_fill_polygon(vm, state, size, &gpu_points);
}

fn dg_draw_polygon(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    x_points_ref: u32,
    x_offset: i32,
    y_points_ref: u32,
    y_offset: i32,
    n_points: i32,
    argb_color: i32,
) {
    let vm = &mut ctx.vm;

    if n_points < 2 {
        return;
    }
    let mut state = get_dg_graphics_state(vm, this_ref);

    if argb_color != 0 {
        state.color = argb_color & 0x00FFFFFF;
    }

    let x_len = vm.heap.array_length(x_points_ref) as i32;
    let y_len = vm.heap.array_length(y_points_ref) as i32;
    let n = n_points.min(x_len - x_offset).min(y_len - y_offset) as usize;
    if n < 2 {
        return;
    }

    let mut points: Vec<(i32, i32)> = Vec::with_capacity(n);
    for i in 0..n {
        let x = vm
            .heap
            .array_read_i32(x_points_ref, (x_offset as usize + i) as u32);
        let y = vm
            .heap
            .array_read_i32(y_points_ref, (y_offset as usize + i) as u32);
        points.push((x, y));
    }
    let size = vm
        .surface_dims
        .get(&state.surface_id)
        .copied()
        .unwrap_or((vm.display_width, vm.display_height));
    let gpu_points: Vec<(f64, f64)> = points.iter().map(|p| (p.0 as f64, p.1 as f64)).collect();
    graphics2d::emit_draw_polygon(vm, state, size, &gpu_points);
}

fn dg_draw_triangle(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    x1: i32,
    y1: i32,
    x2: i32,
    y2: i32,
    x3: i32,
    y3: i32,
    argb_color: i32,
) {
    let vm = &mut ctx.vm;

    let mut state = get_dg_graphics_state(vm, this_ref);
    if argb_color != 0 {
        state.color = argb_color & 0x00FFFFFF;
    }

    let size = vm
        .surface_dims
        .get(&state.surface_id)
        .copied()
        .unwrap_or((vm.display_width, vm.display_height));
    let gpu_points = [
        (x1 as f64, y1 as f64),
        (x2 as f64, y2 as f64),
        (x3 as f64, y3 as f64),
    ];
    graphics2d::emit_draw_polygon(vm, state, size, &gpu_points);
}

pub fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(instance "com.nokia.mid.ui.DirectGraphicsImp.drawImage(Ljavax/microedition/lcdui/Image;IIII)V" => dg_draw_image(this: u32, image: u32, x: i32, y: i32, anchor: i32, manipulation: i32) -> ()),
        crate::native_bind!(instance "com.nokia.mid.ui.DirectGraphicsImp.drawPixels([IZIIIIIIII)V" => dg_draw_pixels_int(this: u32, pixels: u32, transparency: bool, offset: i32, scanlength: i32, x: i32, y: i32, width: i32, height: i32, manipulation: i32, format: i32) -> ()),
        crate::native_bind!(instance "com.nokia.mid.ui.DirectGraphicsImp.drawPixels([SZIIIIIIII)V" => dg_draw_pixels_short(this: u32, pixels: u32, transparency: bool, offset: i32, scanlength: i32, x: i32, y: i32, width: i32, height: i32, manipulation: i32, format: i32) -> ()),
        crate::native_bind!(instance "com.nokia.mid.ui.DirectGraphicsImp.drawPixels([B[BIIIIIIII)V" => dg_draw_pixels_byte(this: u32, pixels: u32, mask: u32, offset: i32, scanlength: i32, x: i32, y: i32, width: i32, height: i32, manipulation: i32, format: i32) -> ()),
        crate::native_bind!(async instance "com.nokia.mid.ui.DirectGraphicsImp.getPixels([IIIIIIII)V" => dg_get_pixels_int(this: u32, pixels: u32, offset: i32, scanlength: i32, x: i32, y: i32, width: i32, height: i32, format: i32) -> ()),
        crate::native_bind!(async instance "com.nokia.mid.ui.DirectGraphicsImp.getPixels([SIIIIIII)V" => dg_get_pixels_short(this: u32, pixels: u32, offset: i32, scanlength: i32, x: i32, y: i32, width: i32, height: i32, format: i32) -> ()),
        crate::native_bind!(async instance "com.nokia.mid.ui.DirectGraphicsImp.getPixels([B[BIIIIIII)V" => dg_get_pixels_byte(this: u32, pixels: u32, mask: u32, offset: i32, scanlength: i32, x: i32, y: i32, width: i32, height: i32, format: i32) -> ()),
        crate::native_bind!(static "com.nokia.mid.ui.DirectUtils.setPixels(Ljavax/microedition/lcdui/Image;I)V" => du_set_pixels(image: u32, argb: i32) -> ()),
        crate::native_bind!(static "com.nokia.mid.ui.DirectUtils.makeMutable(Ljavax/microedition/lcdui/Image;)V" => du_make_mutable(image: u32) -> ()),
        crate::native_bind!(static "com.nokia.mid.ui.DeviceControl.startVibra(IJ)V" => dc_start_vibra(freq: i32, duration: i64) -> ()),
        crate::native_bind!(static "com.nokia.mid.ui.DeviceControl.stopVibra()V" => dc_stop_vibra() -> ()),
        crate::native_bind!(instance "com.nokia.mid.sound.Sound.play(I)V" => sound_play(this: u32, loop_count: i32) -> ()),
        crate::native_bind!(instance "com.nokia.mid.ui.DirectGraphicsImp.fillPolygon([II[IIII)V" => dg_fill_polygon(this: u32, x_points: u32, x_offset: i32, y_points: u32, y_offset: i32, n_points: i32, argb_color: i32) -> ()),
        crate::native_bind!(instance "com.nokia.mid.ui.DirectGraphicsImp.drawPolygon([II[IIII)V" => dg_draw_polygon(this: u32, x_points: u32, x_offset: i32, y_points: u32, y_offset: i32, n_points: i32, argb_color: i32) -> ()),
        crate::native_bind!(instance "com.nokia.mid.ui.DirectGraphicsImp.drawTriangle(IIIIIII)V" => dg_draw_triangle(this: u32, x1: i32, y1: i32, x2: i32, y2: i32, x3: i32, y3: i32, argb_color: i32) -> ()),
    ];
    registry.register_bindings(&bindings);
}
