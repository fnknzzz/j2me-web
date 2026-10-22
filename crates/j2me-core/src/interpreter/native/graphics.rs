use crate::heap::{Heap, NULL};
use crate::interpreter::native::graphics2d;
use crate::interpreter::native::registry::{NativeContext, NativeRegistry};
use crate::vm::{cmd, GraphicsState, SharedVm};

/// Convert heap int[] ARGB pixels directly into tightly packed RGBA8 bytes.
/// Reads pixels row by row according to offset and scanlength.
fn write_rgba_pixels_to_buf(
    buf: &mut Vec<u8>,
    heap: &Heap,
    array_ref: u32,
    offset: i32,
    w: i32,
    h: i32,
    scanlength: i32,
    process_alpha: bool,
) {
    if array_ref == NULL || w <= 0 || h <= 0 {
        return;
    }
    for row in 0..h {
        let Some(row_offset) = row
            .checked_mul(scanlength)
            .and_then(|row_offset| offset.checked_add(row_offset))
        else {
            return;
        };
        let (Ok(row_offset), Ok(width)) = (u32::try_from(row_offset), u32::try_from(w)) else {
            return;
        };
        let Some(pixels) = heap.array_bytes(array_ref, row_offset, width, 4) else {
            return;
        };
        for pixel in pixels.chunks_exact(4) {
            // Java ARGB i32 is B,G,R,A in the little-endian heap.
            buf.extend_from_slice(&[
                pixel[2],
                pixel[1],
                pixel[0],
                if process_alpha { pixel[3] } else { 255 },
            ]);
        }
    }
}

/// Resolve an Image object heap ref to its surface_id via Image.imageData → image_surface_map
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

fn read_font_field(vm: &SharedVm, font_ref: u32, field_name: &str) -> i32 {
    vm.class_repo
        .find_field_offset("javax.microedition.lcdui.Font", field_name)
        .map(|off| vm.heap.read_u32(font_ref + off) as i32)
        .unwrap_or(0)
}

fn compute_font_metrics(face: i32, style: i32, size: i32) -> (i32, i32) {
    let (h, bl) = crate::call_host_font_metrics(face, style, size);
    if h > 0 {
        (h, bl)
    } else {
        (16, 12)
    }
}

fn write_font_height(vm: &mut SharedVm, this_ref: u32, height: i32, baseline: i32) {
    if let Some(off) = vm
        .class_repo
        .find_field_offset("javax.microedition.lcdui.Font", "height")
    {
        vm.heap.write_u32(this_ref + off, height as u32);
    }
    if let Some(off) = vm
        .class_repo
        .find_field_offset("javax.microedition.lcdui.Font", "baseline")
    {
        vm.heap.write_u32(this_ref + off, baseline as u32);
    }
}

fn create_default_font(vm: &mut SharedVm) -> u32 {
    create_font_with(vm, 0, 0, 0)
}

fn create_font_with(vm: &mut SharedVm, face: i32, style: i32, size: i32) -> u32 {
    let font = vm.new_object("javax.microedition.lcdui.Font");
    let (h, bl) = compute_font_metrics(face, style, size);
    write_font_height(vm, font, h, bl);
    write_font_fields(vm, font, face, style, size);
    font
}

fn write_font_fields(vm: &mut SharedVm, this_ref: u32, face: i32, style: i32, size: i32) {
    if let Some(off) = vm
        .class_repo
        .find_field_offset("javax.microedition.lcdui.Font", "face")
    {
        vm.heap.write_u32(this_ref + off, face as u32);
    }
    if let Some(off) = vm
        .class_repo
        .find_field_offset("javax.microedition.lcdui.Font", "style")
    {
        vm.heap.write_u32(this_ref + off, style as u32);
    }
    if let Some(off) = vm
        .class_repo
        .find_field_offset("javax.microedition.lcdui.Font", "size")
    {
        vm.heap.write_u32(this_ref + off, size as u32);
    }
}

// --- Helper: get or create default GraphicsState for a this_ref ---
#[inline]
fn get_state(vm: &SharedVm, this_ref: u32) -> GraphicsState {
    vm.graphics_states
        .get(&this_ref)
        .copied()
        .unwrap_or_else(|| GraphicsState::default_for_display(vm.display_width, vm.display_height))
}

fn surface_size(vm: &SharedVm, state: GraphicsState) -> (i32, i32) {
    vm.surface_dims
        .get(&state.surface_id)
        .copied()
        .unwrap_or((vm.display_width, vm.display_height))
}

// --- Individual native handler functions for ID-based dispatch ---

fn graphics_init_screen0(ctx: &mut NativeContext<'_>, this_ref: u32, _display_id: i32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        vm.ensure_display_surface();
        vm.graphics_states.insert(
            this_ref,
            GraphicsState::default_for_display(vm.display_width, vm.display_height),
        );
    }
}
fn graphics_init_image0(ctx: &mut NativeContext<'_>, this_ref: u32, image_ref: u32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL && image_ref != NULL {
        let image_data_ref = vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.Image", "imageData")
            .map(|off| vm.heap.read_u32(image_ref + off))
            .unwrap_or(image_ref);
        let sid = vm
            .image_surface_map
            .get(&image_data_ref)
            .copied()
            .unwrap_or(0);
        let w = vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.Image", "width")
            .map(|off| vm.heap.read_u32(image_ref + off) as i32)
            .unwrap_or(0);
        let h = vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.Image", "height")
            .map(|off| vm.heap.read_u32(image_ref + off) as i32)
            .unwrap_or(0);
        vm.graphics_states.insert(
            this_ref,
            GraphicsState {
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
            },
        );
    }
}
fn graphics_set_dimensions(ctx: &mut NativeContext<'_>, this_ref: u32, w: i32, h: i32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        state.clip_x = state.translate_x;
        state.clip_y = state.translate_y;
        state.clip_w = w.max(0);
        state.clip_h = h.max(0);
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_set_creator(_ctx: &mut NativeContext<'_>, _this: u32, _creator: u32) {}
fn graphics_translate(ctx: &mut NativeContext<'_>, this_ref: u32, dx: i32, dy: i32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        state.translate_x = state.translate_x.saturating_add(dx);
        state.translate_y = state.translate_y.saturating_add(dy);
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_get_translate_x(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    get_state(ctx.vm, this_ref).translate_x
}
fn graphics_get_translate_y(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    get_state(ctx.vm, this_ref).translate_y
}
fn graphics_get_color(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    get_state(ctx.vm, this_ref).color
}
fn graphics_set_color_rgb(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    red: i32,
    green: i32,
    blue: i32,
) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let rgb = ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff);
        let mut state = get_state(vm, this_ref);
        state.color = rgb;
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_set_color_i(ctx: &mut NativeContext<'_>, this_ref: u32, rgb: i32) {
    let vm = &mut ctx.vm;
    let rgb = rgb & 0x00ff_ffff;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        state.color = rgb;
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_set_stroke_style(ctx: &mut NativeContext<'_>, this_ref: u32, style: i32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        state.stroke_style = style;
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_get_stroke_style(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    get_state(ctx.vm, this_ref).stroke_style
}
fn graphics_set_font(ctx: &mut NativeContext<'_>, this_ref: u32, font_ref: u32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        if font_ref == NULL {
            state.font_face = 0;
            state.font_style = 0;
            state.font_size = 0;
        } else {
            state.font_face = read_font_field(vm, font_ref, "face");
            state.font_style = read_font_field(vm, font_ref, "style");
            state.font_size = read_font_field(vm, font_ref, "size");
        }
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_get_font(ctx: &mut NativeContext<'_>, this_ref: u32) -> u32 {
    let state = get_state(ctx.vm, this_ref);
    create_font_with(ctx.vm, state.font_face, state.font_style, state.font_size)
}
fn graphics_set_clip(ctx: &mut NativeContext<'_>, this_ref: u32, x: i32, y: i32, w: i32, h: i32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        state.clip_x = x.saturating_add(state.translate_x);
        state.clip_y = y.saturating_add(state.translate_y);
        state.clip_w = w.max(0);
        state.clip_h = h.max(0);
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_clip_rect(ctx: &mut NativeContext<'_>, this_ref: u32, x: i32, y: i32, w: i32, h: i32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        let new_x = x.saturating_add(state.translate_x);
        let new_y = y.saturating_add(state.translate_y);
        let new_w = w.max(0);
        let new_h = h.max(0);
        let x1 = state.clip_x.max(new_x);
        let y1 = state.clip_y.max(new_y);
        let x2 = (state.clip_x + state.clip_w).min(new_x + new_w);
        let y2 = (state.clip_y + state.clip_h).min(new_y + new_h);
        state.clip_x = x1;
        state.clip_y = y1;
        state.clip_w = (x2 - x1).max(0);
        state.clip_h = (y2 - y1).max(0);
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_get_clip_x(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    let s = get_state(ctx.vm, this_ref);
    s.clip_x - s.translate_x
}
fn graphics_get_clip_y(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    let s = get_state(ctx.vm, this_ref);
    s.clip_y - s.translate_y
}
fn graphics_get_clip_width(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    get_state(ctx.vm, this_ref).clip_w
}
fn graphics_get_clip_height(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    get_state(ctx.vm, this_ref).clip_h
}
fn graphics_draw_line(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    x1: i32,
    y1: i32,
    x2: i32,
    y2: i32,
) {
    let vm = &mut ctx.vm;
    let state = get_state(vm, this_ref);
    let size = surface_size(vm, state);
    graphics2d::emit_line(
        vm,
        state,
        size,
        (x1 as f64, y1 as f64),
        (x2 as f64, y2 as f64),
    );
}
fn graphics_fill_rect(ctx: &mut NativeContext<'_>, this_ref: u32, x: i32, y: i32, w: i32, h: i32) {
    let vm = &mut ctx.vm;
    let state = get_state(vm, this_ref);
    let size = surface_size(vm, state);
    graphics2d::emit_fill_rect(vm, state, size, x, y, w, h);
}

fn graphics_draw_rect(ctx: &mut NativeContext<'_>, this_ref: u32, x: i32, y: i32, w: i32, h: i32) {
    let vm = &mut ctx.vm;
    if w < 0 || h < 0 {
        return;
    }
    let state = get_state(vm, this_ref);
    let size = surface_size(vm, state);
    graphics2d::emit_lines(
        vm,
        state,
        size,
        &[
            ((x as f64, y as f64), ((x + w) as f64, y as f64)),
            ((x as f64, (y + h) as f64), ((x + w) as f64, (y + h) as f64)),
            ((x as f64, y as f64), (x as f64, (y + h) as f64)),
            (((x + w) as f64, y as f64), ((x + w) as f64, (y + h) as f64)),
        ],
    );
}
fn graphics_draw_string(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    s_ref: u32,
    x: i32,
    y: i32,
    anchor: i32,
) {
    let vm = &mut ctx.vm;
    let text = vm.get_string_value(s_ref).unwrap_or_default();
    let state = get_state(vm, this_ref);
    let text_bytes = text.as_bytes();
    let buf = &mut vm.command_buffer;
    buf.push(cmd::DRAW_STRING);
    state.write_text_state_to_buf(buf);
    buf.extend_from_slice(&(text_bytes.len() as u32).to_le_bytes());
    buf.extend_from_slice(text_bytes);
    let padding = (4 - (text_bytes.len() % 4)) % 4;
    for _ in 0..padding {
        buf.push(0);
    }
    buf.extend_from_slice(&x.to_le_bytes());
    buf.extend_from_slice(&y.to_le_bytes());
    buf.extend_from_slice(&anchor.to_le_bytes());
}
fn graphics_draw_image(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    image_ref: u32,
    x: i32,
    y: i32,
    anchor: i32,
) {
    let vm = &mut ctx.vm;
    let sid = resolve_image_surface_id(vm, image_ref);
    if sid < 0 {
        return;
    }
    let state = get_state(vm, this_ref);
    vm.append_draw_image(state, sid, x, y, anchor);
}
fn graphics_reset0(ctx: &mut NativeContext<'_>, this_ref: u32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        state.translate_x = 0;
        state.translate_y = 0;
        state.clip_x = 0;
        state.clip_y = 0;
        state.clip_w = vm.display_width;
        state.clip_h = vm.display_height;
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_reset4(ctx: &mut NativeContext<'_>, this_ref: u32, x1: i32, y1: i32, x2: i32, y2: i32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        state.translate_x = 0;
        state.translate_y = 0;
        state.clip_x = x1;
        state.clip_y = y1;
        state.clip_w = x2 - x1;
        state.clip_h = y2 - y1;
        vm.graphics_states.insert(this_ref, state);
    }
}
#[allow(non_snake_case)]
fn graphics_resetGC(ctx: &mut NativeContext<'_>, this_ref: u32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        state.color = 0;
        state.stroke_style = 0;
        state.font_face = 0;
        state.font_style = 0;
        state.font_size = 0;
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_get_red_component(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    (get_state(ctx.vm, this_ref).color >> 16) & 0xFF
}
fn graphics_get_green_component(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    (get_state(ctx.vm, this_ref).color >> 8) & 0xFF
}
fn graphics_get_blue_component(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    get_state(ctx.vm, this_ref).color & 0xFF
}
fn graphics_get_gray_scale(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    let c = get_state(ctx.vm, this_ref).color;
    let r = (c >> 16) & 0xFF;
    let g = (c >> 8) & 0xFF;
    let b = c & 0xFF;
    (r * 76 + g * 150 + b * 30) >> 8
}
fn graphics_set_gray_scale(ctx: &mut NativeContext<'_>, this_ref: u32, gray: i32) {
    let vm = &mut ctx.vm;
    let gray = gray & 0xFF;
    if this_ref != NULL {
        let mut state = get_state(vm, this_ref);
        state.color = gray | (gray << 8) | (gray << 16);
        vm.graphics_states.insert(this_ref, state);
    }
}
fn graphics_get_display_color(_ctx: &mut NativeContext<'_>, _this: u32, color: i32) -> i32 {
    color
}
fn graphics_get_max_width(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    get_state(ctx.vm, this_ref).clip_w
}
fn graphics_get_max_height(ctx: &mut NativeContext<'_>, this_ref: u32) -> i32 {
    get_state(ctx.vm, this_ref).clip_h
}
fn graphics_get_creator(_ctx: &mut NativeContext<'_>, _this: u32) -> u32 {
    NULL
}
fn graphics_get_clip(ctx: &mut NativeContext<'_>, this_ref: u32, arr: u32) {
    let vm = &mut ctx.vm;
    let s = get_state(vm, this_ref);
    vm.heap
        .array_write_u32(arr, 0, (s.clip_x - s.translate_x) as u32);
    vm.heap
        .array_write_u32(arr, 1, (s.clip_y - s.translate_y) as u32);
    vm.heap.array_write_u32(arr, 2, s.clip_w as u32);
    vm.heap.array_write_u32(arr, 3, s.clip_h as u32);
}
fn graphics_draw_substring(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    s_ref: u32,
    offset: i32,
    len: i32,
    x: i32,
    y: i32,
    anchor: i32,
) {
    let vm = &mut ctx.vm;
    let full = vm.get_string_value(s_ref).unwrap_or_default();
    let text: String = full
        .chars()
        .skip(offset as usize)
        .take(len as usize)
        .collect();
    let state = get_state(vm, this_ref);
    let text_bytes = text.as_bytes();
    let buf = &mut vm.command_buffer;
    buf.push(cmd::DRAW_STRING);
    state.write_text_state_to_buf(buf);
    buf.extend_from_slice(&(text_bytes.len() as u32).to_le_bytes());
    buf.extend_from_slice(text_bytes);
    let padding = (4 - (text_bytes.len() % 4)) % 4;
    for _ in 0..padding {
        buf.push(0);
    }
    buf.extend_from_slice(&x.to_le_bytes());
    buf.extend_from_slice(&y.to_le_bytes());
    buf.extend_from_slice(&anchor.to_le_bytes());
}
fn graphics_draw_chars(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    arr: u32,
    offset: i32,
    len: i32,
    x: i32,
    y: i32,
    anchor: i32,
) {
    let vm = &mut ctx.vm;
    let mut chars = Vec::with_capacity(len as usize);
    for i in 0..len as u32 {
        chars.push(vm.heap.array_read_u16(arr, offset as u32 + i));
    }
    let text = String::from_utf16_lossy(&chars);
    let state = get_state(vm, this_ref);
    let text_bytes = text.as_bytes();
    let buf = &mut vm.command_buffer;
    buf.push(cmd::DRAW_STRING);
    state.write_text_state_to_buf(buf);
    buf.extend_from_slice(&(text_bytes.len() as u32).to_le_bytes());
    buf.extend_from_slice(text_bytes);
    let padding = (4 - (text_bytes.len() % 4)) % 4;
    for _ in 0..padding {
        buf.push(0);
    }
    buf.extend_from_slice(&x.to_le_bytes());
    buf.extend_from_slice(&y.to_le_bytes());
    buf.extend_from_slice(&anchor.to_le_bytes());
}
fn graphics_draw_char(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    ch: i32,
    x: i32,
    y: i32,
    anchor: i32,
) {
    let vm = &mut ctx.vm;
    let text = char::from_u32(ch as u32).unwrap_or('\0').to_string();
    let state = get_state(vm, this_ref);
    let text_bytes = text.as_bytes();
    let buf = &mut vm.command_buffer;
    buf.push(cmd::DRAW_STRING);
    state.write_text_state_to_buf(buf);
    buf.extend_from_slice(&(text_bytes.len() as u32).to_le_bytes());
    buf.extend_from_slice(text_bytes);
    let padding = (4 - (text_bytes.len() % 4)) % 4;
    for _ in 0..padding {
        buf.push(0);
    }
    buf.extend_from_slice(&x.to_le_bytes());
    buf.extend_from_slice(&y.to_le_bytes());
    buf.extend_from_slice(&anchor.to_le_bytes());
}
fn graphics_fill_triangle(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    x1: i32,
    y1: i32,
    x2: i32,
    y2: i32,
    x3: i32,
    y3: i32,
) {
    let vm = &mut ctx.vm;
    let state = get_state(vm, this_ref);
    let size = surface_size(vm, state);
    graphics2d::emit_triangle(
        vm,
        state,
        size,
        [
            (x1 as f64, y1 as f64),
            (x2 as f64, y2 as f64),
            (x3 as f64, y3 as f64),
        ],
    );
}
fn graphics_draw_round_rect(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    arc_w: i32,
    arc_h: i32,
) {
    let vm = &mut ctx.vm;
    let state = get_state(vm, this_ref);
    let size = surface_size(vm, state);
    graphics2d::emit_round_rect(vm, state, size, x, y, w, h, arc_w, arc_h, false);
}
fn graphics_fill_round_rect(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    arc_w: i32,
    arc_h: i32,
) {
    let vm = &mut ctx.vm;
    let state = get_state(vm, this_ref);
    let size = surface_size(vm, state);
    graphics2d::emit_round_rect(vm, state, size, x, y, w, h, arc_w, arc_h, true);
}
fn graphics_draw_arc(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    start_angle: i32,
    arc_angle: i32,
) {
    let vm = &mut ctx.vm;
    let state = get_state(vm, this_ref);
    let size = surface_size(vm, state);
    graphics2d::emit_arc(vm, state, size, x, y, w, h, start_angle, arc_angle, false);
}
fn graphics_fill_arc(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    start_angle: i32,
    arc_angle: i32,
) {
    let vm = &mut ctx.vm;
    let state = get_state(vm, this_ref);
    let size = surface_size(vm, state);
    graphics2d::emit_arc(vm, state, size, x, y, w, h, start_angle, arc_angle, true);
}
#[allow(non_snake_case)]
fn graphics_drawRGB(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    rgb_data_ref: u32,
    offset: i32,
    scanlength: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    process_alpha: bool,
) {
    let vm = &mut ctx.vm;
    let state = get_state(vm, this_ref);
    let buf = &mut vm.command_buffer;
    buf.push(cmd::DRAW_RGBA);
    state.write_text_state_to_buf(buf);
    buf.extend_from_slice(&x.to_le_bytes());
    buf.extend_from_slice(&y.to_le_bytes());
    buf.extend_from_slice(&width.to_le_bytes());
    buf.extend_from_slice(&height.to_le_bytes());
    let pixel_count = (width * height) as u32;
    buf.extend_from_slice(&pixel_count.to_le_bytes());
    write_rgba_pixels_to_buf(
        buf,
        &vm.heap,
        rgb_data_ref,
        offset,
        width,
        height,
        scanlength,
        process_alpha,
    );
}
fn graphics_draw_region(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    image_ref: u32,
    x_src: i32,
    y_src: i32,
    w_src: i32,
    h_src: i32,
    transform: i32,
    x_dest: i32,
    y_dest: i32,
    anchor: i32,
) {
    let vm = &mut ctx.vm;
    let sid = resolve_image_surface_id(vm, image_ref);
    if sid < 0 {
        return;
    }
    let state = get_state(vm, this_ref);
    let buf = &mut vm.command_buffer;
    buf.push(cmd::DRAW_REGION);
    state.write_text_state_to_buf(buf);
    buf.extend_from_slice(&(sid as u32).to_le_bytes());
    buf.extend_from_slice(&x_src.to_le_bytes());
    buf.extend_from_slice(&y_src.to_le_bytes());
    buf.extend_from_slice(&w_src.to_le_bytes());
    buf.extend_from_slice(&h_src.to_le_bytes());
    buf.extend_from_slice(&transform.to_le_bytes());
    buf.extend_from_slice(&x_dest.to_le_bytes());
    buf.extend_from_slice(&y_dest.to_le_bytes());
    buf.extend_from_slice(&anchor.to_le_bytes());
}
fn graphics_copy_area(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    x_src: i32,
    y_src: i32,
    w: i32,
    h: i32,
    x_dest: i32,
    y_dest: i32,
    anchor: i32,
) {
    let vm = &mut ctx.vm;
    let state = get_state(vm, this_ref);
    let buf = &mut vm.command_buffer;
    buf.push(cmd::COPY_AREA);
    state.write_text_state_to_buf(buf);
    buf.extend_from_slice(&x_src.to_le_bytes());
    buf.extend_from_slice(&y_src.to_le_bytes());
    buf.extend_from_slice(&w.to_le_bytes());
    buf.extend_from_slice(&h.to_le_bytes());
    buf.extend_from_slice(&x_dest.to_le_bytes());
    buf.extend_from_slice(&y_dest.to_le_bytes());
    buf.extend_from_slice(&anchor.to_le_bytes());
}
fn graphics_render(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    image_ref: u32,
    x: i32,
    y: i32,
    anchor: i32,
) -> bool {
    let vm = &mut ctx.vm;
    let sid = resolve_image_surface_id(vm, image_ref);
    if sid < 0 {
        return false;
    }
    let state = get_state(vm, this_ref);
    vm.append_draw_image(state, sid, x, y, anchor);
    true
}
fn font_get_default(ctx: &mut NativeContext<'_>) -> u32 {
    create_default_font(ctx.vm)
}
fn font_init(ctx: &mut NativeContext<'_>, this_ref: u32, face: i32, style: i32, size: i32) {
    let vm = &mut ctx.vm;
    if this_ref != NULL {
        let (h, bl) = compute_font_metrics(face, style, size);
        write_font_height(vm, this_ref, h, bl);
        write_font_fields(vm, this_ref, face, style, size);
    }
}
fn font_char_width(ctx: &mut NativeContext<'_>, this_ref: u32, ch: i32) -> i32 {
    let vm = &mut ctx.vm;
    let face = read_font_field(vm, this_ref, "face");
    let style = read_font_field(vm, this_ref, "style");
    let size = read_font_field(vm, this_ref, "size");
    let s = char::from_u32(ch as u32)
        .map(|c| c.to_string())
        .unwrap_or_default();
    let w = crate::call_host_measure_text(&s, face, style, size);
    if w > 0 {
        w
    } else {
        8
    }
}
fn font_chars_width(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    arr_ref: u32,
    off: i32,
    len: i32,
) -> i32 {
    let vm = &mut ctx.vm;
    let face = read_font_field(vm, this_ref, "face");
    let style = read_font_field(vm, this_ref, "style");
    let size = read_font_field(vm, this_ref, "size");
    let text: String = (off..off + len)
        .filter_map(|i| {
            let v = vm.heap.array_read_u16(arr_ref, i as u32);
            char::from_u32(v as u32)
        })
        .collect();
    let w = crate::call_host_measure_text(&text, face, style, size);
    if w > 0 {
        w
    } else {
        len * 8
    }
}
fn font_string_width(ctx: &mut NativeContext<'_>, this_ref: u32, s_ref: u32) -> i32 {
    let vm = &mut ctx.vm;
    let face = read_font_field(vm, this_ref, "face");
    let style = read_font_field(vm, this_ref, "style");
    let size = read_font_field(vm, this_ref, "size");
    let text = vm.get_string_value(s_ref).unwrap_or_default();
    let w = crate::call_host_measure_text(&text, face, style, size);
    if w > 0 {
        w
    } else {
        (text.chars().count() as i32) * 8
    }
}
fn font_substring_width(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    s_ref: u32,
    off: i32,
    len: i32,
) -> i32 {
    let vm = &mut ctx.vm;
    let face = read_font_field(vm, this_ref, "face");
    let style = read_font_field(vm, this_ref, "style");
    let size = read_font_field(vm, this_ref, "size");
    let full = vm.get_string_value(s_ref).unwrap_or_default();
    let sub: String = full.chars().skip(off as usize).take(len as usize).collect();
    let w = crate::call_host_measure_text(&sub, face, style, size);
    if w > 0 {
        w
    } else {
        len * 8
    }
}

/// Register native methods for this module.
pub fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.initScreen0(I)V" => graphics_init_screen0(this: u32, display_id: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.initImage0(Ljavax/microedition/lcdui/Image;)V" => graphics_init_image0(this: u32, image: u32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.setDimensions(II)V" => graphics_set_dimensions(this: u32, width: i32, height: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.setCreator(Ljava/lang/Object;)V" => graphics_set_creator(this: u32, creator: u32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.translate(II)V" => graphics_translate(this: u32, dx: i32, dy: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getTranslateX()I" => graphics_get_translate_x(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getTranslateY()I" => graphics_get_translate_y(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getColor()I" => graphics_get_color(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.setColor(III)V" => graphics_set_color_rgb(this: u32, red: i32, green: i32, blue: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.setColor(I)V" => graphics_set_color_i(this: u32, rgb: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.setStrokeStyle(I)V" => graphics_set_stroke_style(this: u32, style: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getStrokeStyle()I" => graphics_get_stroke_style(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.setFont(Ljavax/microedition/lcdui/Font;)V" => graphics_set_font(this: u32, font: u32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getFont()Ljavax/microedition/lcdui/Font;" => graphics_get_font(this: u32) -> u32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.setClip(IIII)V" => graphics_set_clip(this: u32, x: i32, y: i32, width: i32, height: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.clipRect(IIII)V" => graphics_clip_rect(this: u32, x: i32, y: i32, width: i32, height: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getClipX()I" => graphics_get_clip_x(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getClipY()I" => graphics_get_clip_y(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getClipWidth()I" => graphics_get_clip_width(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getClipHeight()I" => graphics_get_clip_height(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawLine(IIII)V" => graphics_draw_line(this: u32, x1: i32, y1: i32, x2: i32, y2: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.fillRect(IIII)V" => graphics_fill_rect(this: u32, x: i32, y: i32, width: i32, height: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawRect(IIII)V" => graphics_draw_rect(this: u32, x: i32, y: i32, width: i32, height: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawString(Ljava/lang/String;III)V" => graphics_draw_string(this: u32, text: u32, x: i32, y: i32, anchor: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawImage(Ljavax/microedition/lcdui/Image;III)V" => graphics_draw_image(this: u32, image: u32, x: i32, y: i32, anchor: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.reset()V" => graphics_reset0(this: u32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.reset(IIII)V" => graphics_reset4(this: u32, x1: i32, y1: i32, x2: i32, y2: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.resetGC()V" => graphics_resetGC(this: u32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getRedComponent()I" => graphics_get_red_component(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getGreenComponent()I" => graphics_get_green_component(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getBlueComponent()I" => graphics_get_blue_component(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getGrayScale()I" => graphics_get_gray_scale(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.setGrayScale(I)V" => graphics_set_gray_scale(this: u32, gray: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getDisplayColor(I)I" => graphics_get_display_color(this: u32, color: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getMaxWidth()S" => graphics_get_max_width(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getMaxHeight()S" => graphics_get_max_height(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getCreator()Ljava/lang/Object;" => graphics_get_creator(this: u32) -> u32),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.getClip([I)V" => graphics_get_clip(this: u32, clip: u32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawSubstring(Ljava/lang/String;IIIII)V" => graphics_draw_substring(this: u32, text: u32, offset: i32, length: i32, x: i32, y: i32, anchor: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawChars([CIIIII)V" => graphics_draw_chars(this: u32, chars: u32, offset: i32, length: i32, x: i32, y: i32, anchor: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawChar(CIII)V" => graphics_draw_char(this: u32, character: i32, x: i32, y: i32, anchor: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.fillTriangle(IIIIII)V" => graphics_fill_triangle(this: u32, x1: i32, y1: i32, x2: i32, y2: i32, x3: i32, y3: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawRoundRect(IIIIII)V" => graphics_draw_round_rect(this: u32, x: i32, y: i32, width: i32, height: i32, arc_width: i32, arc_height: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.fillRoundRect(IIIIII)V" => graphics_fill_round_rect(this: u32, x: i32, y: i32, width: i32, height: i32, arc_width: i32, arc_height: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawArc(IIIIII)V" => graphics_draw_arc(this: u32, x: i32, y: i32, width: i32, height: i32, start_angle: i32, arc_angle: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.fillArc(IIIIII)V" => graphics_fill_arc(this: u32, x: i32, y: i32, width: i32, height: i32, start_angle: i32, arc_angle: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawRGB([IIIIIIIZ)V" => graphics_drawRGB(this: u32, rgb_data: u32, offset: i32, scanlength: i32, x: i32, y: i32, width: i32, height: i32, process_alpha: bool) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.drawRegion(Ljavax/microedition/lcdui/Image;IIIIIIII)V" => graphics_draw_region(this: u32, image: u32, x_src: i32, y_src: i32, width: i32, height: i32, transform: i32, x_dest: i32, y_dest: i32, anchor: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.copyArea(IIIIIII)V" => graphics_copy_area(this: u32, x_src: i32, y_src: i32, width: i32, height: i32, x_dest: i32, y_dest: i32, anchor: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Graphics.render(Ljavax/microedition/lcdui/Image;III)Z" => graphics_render(this: u32, image: u32, x: i32, y: i32, anchor: i32) -> bool),
        crate::native_bind!(static "javax.microedition.lcdui.Font.getDefaultFont()Ljavax/microedition/lcdui/Font;" => font_get_default() -> u32),
        crate::native_bind!(instance "javax.microedition.lcdui.Font.init(III)V" => font_init(this: u32, face: i32, style: i32, size: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Font.charWidth(C)I" => font_char_width(this: u32, character: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Font.charsWidth([CII)I" => font_chars_width(this: u32, chars: u32, offset: i32, length: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Font.stringWidth(Ljava/lang/String;)I" => font_string_width(this: u32, text: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.Font.substringWidth(Ljava/lang/String;II)I" => font_substring_width(this: u32, text: u32, offset: i32, length: i32) -> i32),
    ];
    registry.register_bindings(&bindings);
}
