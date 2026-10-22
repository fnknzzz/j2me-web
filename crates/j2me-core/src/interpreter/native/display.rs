use crate::heap::NULL;
use crate::scheduler::virtual_thread::YieldReason;
use crate::vm::{cmd, AsyncRequestType, AsyncResult, SharedVm};

// ---------------------------------------------------------------------------
// Command types (MIDP spec)
// ---------------------------------------------------------------------------
const CMD_TYPE_BACK: i32 = 2;
const CMD_TYPE_CANCEL: i32 = 3;
const CMD_TYPE_STOP: i32 = 6;
const CMD_TYPE_EXIT: i32 = 7;
// ---------------------------------------------------------------------------
// Advanced LCDUI is Java-painted on the primary canvas. Native peer creation
// only needs to make sure the obsolete host DOM overlay stays hidden.
// ---------------------------------------------------------------------------
fn emit_lcdui_snapshot(vm: &mut SharedVm, lf_this: u32) {
    if lf_this == NULL {
        return;
    }
    vm.set_lcdui_visible(false);
}

use crate::interpreter::native::registry::{NativeContext, NativeRegistry, NativeResult};

// --- Individual native handler functions for ID-based dispatch ---

fn display_devices_ids(ctx: &mut NativeContext<'_>, _this: u32) -> u32 {
    let class_id = ctx.vm.class_repo.get_class_id("[I").unwrap_or(0);
    let array = ctx.vm.heap.alloc_array_typed(class_id, 1, 4, true);
    ctx.vm.heap.array_write_u32(array, 0, 0);
    array
}
fn display_name(ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> u32 {
    ctx.vm.create_string("display0")
}
fn display_primary(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> bool {
    true
}
fn display_builtin(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> bool {
    true
}
fn display_capabilities(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> i32 {
    0xff
}
fn display_pen_supported(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> bool {
    true
}
fn display_pen_motion_supported(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> bool {
    true
}
fn display_width(ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> i32 {
    ctx.vm.command_buffer.push(cmd::DISPLAY_GET_WIDTH);
    ctx.vm
        .command_buffer
        .extend_from_slice(&ctx.vm.display_width.to_le_bytes());
    ctx.vm.display_width
}
fn display_height(ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> i32 {
    ctx.vm.command_buffer.push(cmd::DISPLAY_GET_HEIGHT);
    ctx.vm
        .command_buffer
        .extend_from_slice(&ctx.vm.display_height.to_le_bytes());
    ctx.vm.display_height
}
fn reverse_orientation(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> bool {
    false
}
fn display_refresh(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    _hardware: i32,
    _display: i32,
    _x: i32,
    _y: i32,
    _width: i32,
    _height: i32,
) {
    ctx.vm.command_buffer.push(cmd::FLUSH);
    ctx.vm.command_buffer.extend_from_slice(&0u32.to_le_bytes());
}
fn set_full_screen(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _hardware: i32,
    _display: i32,
    _mode: bool,
) {
}
fn direct_flush(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    _hardware: i32,
    _graphics: u32,
    _image: u32,
    _wrap: i32,
) -> bool {
    ctx.vm.command_buffer.push(cmd::FLUSH);
    ctx.vm.command_buffer.extend_from_slice(&0u32.to_le_bytes());
    true
}
fn gained_foreground(ctx: &mut NativeContext<'_>, _this: u32, _hardware: i32, display: i32) {
    ctx.vm.foreground_display_id = display;
}
fn display_state_changed(_ctx: &mut NativeContext<'_>, _this: u32, _hardware: i32, _state: i32) {}
fn clamshell_handling(_ctx: &mut NativeContext<'_>, _this: u32) {}
fn draw_trusted_icon(_ctx: &mut NativeContext<'_>, _this: u32, _icon: i32, _enabled: bool) {}
fn display_set_title(_ctx: &mut NativeContext<'_>, _title: u32) {}
fn native_menu_update_commands(
    ctx: &mut NativeContext<'_>,
    item_cmds_ref: u32,
    num_item_cmds: i32,
    screen_cmds_ref: u32,
    num_screen_cmds: i32,
) {
    // Collect command data from heap
    struct CmdInfo {
        cmd_type: i32,
        priority: i32,
        label: String,
    }
    fn read_commands_binary(vm: &mut SharedVm, arr_ref: u32, count: i32, out: &mut Vec<CmdInfo>) {
        if arr_ref == NULL || count <= 0 {
            return;
        }
        let arr_len = vm.heap.array_length(arr_ref);
        let n = std::cmp::min(count as u32, arr_len);
        for i in 0..n {
            let cmd_ref = vm.heap.array_read_u32(arr_ref, i);
            if cmd_ref == NULL {
                continue;
            }
            let label = vm
                .class_repo
                .find_field_offset("javax.microedition.lcdui.Command", "shortLabel")
                .and_then(|off| vm.get_string_value(vm.heap.read_u32(cmd_ref + off)))
                .unwrap_or_default();
            let cmd_type = vm
                .class_repo
                .find_field_offset("javax.microedition.lcdui.Command", "commandType")
                .map(|off| vm.heap.read_u32(cmd_ref + off) as i32)
                .unwrap_or(0);
            let priority = vm
                .class_repo
                .find_field_offset("javax.microedition.lcdui.Command", "priority")
                .map(|off| vm.heap.read_u32(cmd_ref + off) as i32)
                .unwrap_or(0);
            out.push(CmdInfo {
                cmd_type,
                priority,
                label,
            });
        }
    }
    let mut cmds: Vec<CmdInfo> = Vec::new();
    read_commands_binary(ctx.vm, item_cmds_ref, num_item_cmds, &mut cmds);
    read_commands_binary(ctx.vm, screen_cmds_ref, num_screen_cmds, &mut cmds);

    fn is_exit_command(command: &CmdInfo) -> bool {
        command.cmd_type == CMD_TYPE_BACK
            || command.cmd_type == CMD_TYPE_CANCEL
            || command.cmd_type == CMD_TYPE_STOP
            || command.cmd_type == CMD_TYPE_EXIT
    }
    cmds.sort_by_key(|c| c.priority);
    let right = if let Some(index) = cmds.iter().position(is_exit_command) {
        Some(cmds.remove(index))
    } else {
        cmds.pop()
    };

    let left_label = if cmds.len() == 1 {
        cmds[0].label.clone()
    } else if cmds.len() > 1 {
        "菜单".to_string()
    } else {
        String::new()
    };
    let right_label = right
        .as_ref()
        .map(|command| command.label.clone())
        .unwrap_or_default();

    push_soft_key_labels(ctx.vm, &left_label, &right_label);
}

fn push_soft_key_labels(vm: &mut SharedVm, left_label: &str, right_label: &str) {
    // Binary format: [cmd=40:u8][display_id:i32][left_label][right_label]
    let display_id = vm.foreground_display_id;
    let buf = &mut vm.command_buffer;
    buf.push(cmd::MENU_UPDATE_COMMANDS);
    buf.extend_from_slice(&display_id.to_le_bytes());

    for label in [&left_label, &right_label] {
        let label_bytes = label.as_bytes();
        buf.extend_from_slice(&(label_bytes.len() as u32).to_le_bytes());
        buf.extend_from_slice(label_bytes);
        let padding = (4 - (label_bytes.len() % 4)) % 4;
        for _ in 0..padding {
            buf.push(0);
        }
    }
}

fn native_menu_update_soft_key_labels(ctx: &mut NativeContext<'_>, left: u32, right: u32) {
    let left_label = ctx.vm.get_string_value(left).unwrap_or_default();
    let right_label = ctx.vm.get_string_value(right).unwrap_or_default();
    push_soft_key_labels(ctx.vm, &left_label, &right_label);
}

fn text_input_show(
    ctx: &mut NativeContext<'_>,
    text_field_ref: u32,
    text_ref: u32,
    constraints: i32,
    max_size: i32,
) -> NativeResult<u32> {
    if let Some((_rid, _request, result)) =
        ctx.vm.take_resolved_async_for_thread(ctx.thread_id, |r| {
            matches!(r, AsyncResult::TextInputCommitted { .. })
        })
    {
        if let AsyncResult::TextInputCommitted { text } = result {
            return NativeResult::Ready(text.map_or(NULL, |value| ctx.vm.create_string(&value)));
        }
        unreachable!("filtered async result must be TextInputCommitted");
    }

    let text = ctx.vm.get_string_value(text_ref).unwrap_or_default();

    let request_id = ctx.vm.issue_async_request(
        ctx.thread_id,
        AsyncRequestType::TextInput {
            text_field_ref,
            constraints,
            max_size,
            text,
        },
    );
    NativeResult::Yield(YieldReason::AsyncWait { request_id })
}

fn text_input_hide(ctx: &mut NativeContext<'_>) {
    ctx.vm
        .command_buffer
        .extend_from_slice(&[cmd::TEXT_INPUT, 0, 0, 0, 0]);
}

fn date_input_show(
    ctx: &mut NativeContext<'_>,
    date_field_ref: u32,
    mode: i32,
    initialized: bool,
    value_millis: i64,
) -> NativeResult<u32> {
    if let Some((_rid, _request, result)) =
        ctx.vm.take_resolved_async_for_thread(ctx.thread_id, |r| {
            matches!(r, AsyncResult::DateInputCommitted { .. })
        })
    {
        let arr_class = ctx.vm.class_repo.get_class_id("[J").unwrap_or(0);
        let arr = ctx.vm.new_array(arr_class, 3, 8);
        if let AsyncResult::DateInputCommitted {
            initialized,
            value_millis,
            committed,
        } = result
        {
            ctx.vm
                .heap
                .array_write_i64(arr, 0, if committed { 1 } else { 0 });
            ctx.vm
                .heap
                .array_write_i64(arr, 1, if initialized { 1 } else { 0 });
            ctx.vm.heap.array_write_i64(arr, 2, value_millis);
        }
        return NativeResult::Ready(arr);
    }

    let request_id = ctx.vm.issue_async_request(
        ctx.thread_id,
        AsyncRequestType::DateInput {
            date_field_ref,
            mode,
            initialized,
            value_millis,
        },
    );
    NativeResult::Yield(YieldReason::AsyncWait { request_id })
}

fn date_input_hide(ctx: &mut NativeContext<'_>) {
    ctx.vm
        .command_buffer
        .extend_from_slice(&[cmd::DATE_INPUT, 0, 0, 0, 0]);
}
fn vibrate(_ctx: &mut NativeContext<'_>, _this: u32, _hardware: i32, _on: bool) -> bool {
    false
}
fn backlight_supported(_ctx: &mut NativeContext<'_>, _this: u32, _hardware: i32) -> bool {
    false
}
#[allow(non_snake_case)]
fn displayable_delete(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) {}
fn displayable_initialize(_ctx: &mut NativeContext<'_>) {}
fn displayable_finalize(_ctx: &mut NativeContext<'_>, _this: u32) {}
fn displayable_set_title(_ctx: &mut NativeContext<'_>, _id: i32, _title: u32) {}
fn displayable_set_ticker(_ctx: &mut NativeContext<'_>, _id: i32, _ticker: u32) {}
#[allow(non_snake_case)]
fn canvas_create(ctx: &mut NativeContext<'_>, _this: u32, _title: u32, _path: u32) -> i32 {
    ctx.vm.set_lcdui_visible(false);
    ctx.vm.next_native_id() as i32
}
fn form_set_scroll_position(_ctx: &mut NativeContext<'_>, _this: u32, _position: i32) {}
fn form_get_scroll_position(_ctx: &mut NativeContext<'_>, _this: u32) -> i32 {
    0
}
fn form_set_current_item(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _height: i32,
    _offset: i32,
) {
}
fn form_create(_ctx: &mut NativeContext<'_>, _this: u32, _title: u32, _path: u32) -> i32 {
    1
}
fn form_show(
    ctx: &mut NativeContext<'_>,
    this: u32,
    _x: i32,
    _y: i32,
    _width: i32,
    _height: i32,
    _java_paint_only: bool,
) {
    emit_lcdui_snapshot(ctx.vm, this);
}
fn form_viewport_height(ctx: &mut NativeContext<'_>, _this: u32) -> i32 {
    ctx.vm.display_height
}
fn alert_create(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    _title: u32,
    _text: u32,
    _kind: i32,
) -> i32 {
    ctx.vm.next_native_id() as i32
}
fn alert_set_contents(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _image: u32,
    _bounds: u32,
    _text: u32,
) -> bool {
    false
}
fn alert_show(ctx: &mut NativeContext<'_>, _this: u32, _id: i32) {
    ctx.vm.set_lcdui_visible(false);
}
fn item_set_size(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32, _width: i32, _height: i32) {}
fn item_set_location(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32, _x: i32, _y: i32) {}
fn item_show(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) {}
fn item_hide(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) {}
fn datefield_create(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    _owner_id: i32,
    _label: u32,
    _layout: i32,
    _datetime: i64,
    _display_mode: i32,
    _time_zone: u32,
) -> i32 {
    ctx.vm.next_native_id() as i32
}
fn datefield_set_date(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32, _date: i64) {}
fn datefield_set_input_mode(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32, _mode: i32) {}
fn datefield_get_date(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> i64 {
    0
}
fn gauge_create(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    _owner_id: i32,
    _label: u32,
    _layout: i32,
    _interactive: bool,
    _value: i32,
    _max_value: i32,
) -> i32 {
    ctx.vm.next_native_id() as i32
}
fn textfield_create(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    _owner_id: i32,
    _label: u32,
    _constraints: i32,
    _buffer: u32,
    _max_size: i32,
    _text: u32,
) -> i32 {
    ctx.vm.next_native_id() as i32
}
fn imageitem_create(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    _owner_id: i32,
    _label: u32,
    _layout: i32,
    _image_data: u32,
    _alt_text: u32,
    _appearance_mode: i32,
) -> i32 {
    ctx.vm.next_native_id() as i32
}
fn choicegroup_create(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    _owner_id: i32,
    _label: u32,
    _layout: i32,
    _choice_type: i32,
    _fit_policy: i32,
    _elements: u32,
    _count: i32,
    _selected_index: i32,
) -> i32 {
    ctx.vm.next_native_id() as i32
}
fn choicegroup_insert(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _element: i32,
    _text: u32,
    _image: u32,
    _selected: bool,
) {
}
fn choicegroup_delete(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _element: i32,
    _selected: i32,
) {
}
fn choicegroup_delete_all(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) {}
fn choicegroup_set(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _element: i32,
    _text: u32,
    _image: u32,
    _selected: bool,
) {
}
fn choicegroup_set_selected_index(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _element: i32,
    _selected: bool,
) {
}
fn choicegroup_set_selected_flags(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _selected: u32,
    _count: i32,
) {
}
fn choicegroup_set_fit_policy(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _fit_policy: i32,
) {
}
fn choicegroup_set_font(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _element: i32,
    _face: i32,
    _style: i32,
    _size: i32,
) {
}
fn choicegroup_selected_index(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> i32 {
    0
}
fn choicegroup_selected_flags(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _selected: u32,
    _count: i32,
) -> i32 {
    0
}
fn choicegroup_is_selected(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _element: i32,
) -> bool {
    false
}
fn stringitem_create(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    _owner_id: i32,
    _label: u32,
    _layout: i32,
    _text: u32,
    _appearance_mode: i32,
    _face: i32,
    _style: i32,
    _size: i32,
) -> i32 {
    ctx.vm.next_native_id() as i32
}
fn stringitem_set_content_simple(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _text: u32,
    _appearance_mode: i32,
) {
}
fn stringitem_set_font(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _font: i32,
    _max_width: i32,
    _appearance_mode: i32,
) {
}
fn imageitem_set_content(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _image: u32,
    _alt_text: u32,
    _appearance_mode: i32,
) {
}
fn item_delete(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) {}
fn item_minimum_width(ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> i32 {
    ctx.vm.display_width
}
fn item_minimum_height(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> i32 {
    20
}
fn item_preferred_width(ctx: &mut NativeContext<'_>, _this: u32, _id: i32, _height: i32) -> i32 {
    ctx.vm.display_width
}
fn item_preferred_height(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32, _width: i32) -> i32 {
    20
}
fn item_set_label(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32, _label: u32) {}
fn textfield_get_string(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32, _buffer: u32) -> bool {
    false
}
fn textfield_set_string(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32, _buffer: u32) {}
fn textfield_set_max_size(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32, _max_size: i32) {}
fn textfield_caret_position(_ctx: &mut NativeContext<'_>, _this: u32, _id: i32) -> i32 {
    0
}
fn textfield_set_constraints(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _constraints: i32,
) {
}
fn gauge_set_value(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _id: i32,
    _value: i32,
    _max_value: i32,
) {
}
fn customitem_create(
    ctx: &mut NativeContext<'_>,
    _owner_id: i32,
    _label: u32,
    _layout: i32,
) -> i32 {
    ctx.vm.next_native_id() as i32
}
fn customitem_refresh(
    _ctx: &mut NativeContext<'_>,
    _id: i32,
    _x: i32,
    _y: i32,
    _width: i32,
    _height: i32,
) {
}
fn customitem_label_height(_ctx: &mut NativeContext<'_>, _id: i32, _width: i32) -> i32 {
    0
}
fn customitem_label_width(_ctx: &mut NativeContext<'_>, _id: i32, _width: i32) -> i32 {
    0
}
fn customitem_pad(_ctx: &mut NativeContext<'_>, _id: i32) -> i32 {
    0
}
fn customitem_set_content_buffer(_ctx: &mut NativeContext<'_>, _id: i32, _image: u32) {}

/// Register native methods for this module.
pub fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDeviceContainer.getDisplayDevicesIds0()[I" => display_devices_ids(this: u32) -> u32),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.getDisplayName0(I)Ljava/lang/String;" => display_name(this: u32, id: i32) -> u32),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.isDisplayPrimary0(I)Z" => display_primary(this: u32, id: i32) -> bool),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.isbuildInDisplay0(I)Z" => display_builtin(this: u32, id: i32) -> bool),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.getDisplayCapabilities0(I)I" => display_capabilities(this: u32, id: i32) -> i32),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.isDisplayPenSupported0(I)Z" => display_pen_supported(this: u32, id: i32) -> bool),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.isDisplayPenMotionSupported0(I)Z" => display_pen_motion_supported(this: u32, id: i32) -> bool),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.getScreenWidth0(I)I" => display_width(this: u32, id: i32) -> i32),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.getScreenHeight0(I)I" => display_height(this: u32, id: i32) -> i32),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.getReverseOrientation0(I)Z" => reverse_orientation(this: u32, id: i32) -> bool),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.reverseOrientation0(I)Z" => reverse_orientation(this: u32, id: i32) -> bool),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.refresh0(IIIIII)V" => display_refresh(this: u32, hardware: i32, display: i32, x: i32, y: i32, width: i32, height: i32) -> ()),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.setFullScreen0(IIZ)V" => set_full_screen(this: u32, hardware: i32, display: i32, mode: bool) -> ()),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.directFlush0(ILjavax/microedition/lcdui/Graphics;Ljavax/microedition/lcdui/Image;I)Z" => direct_flush(this: u32, hardware: i32, graphics: u32, image: u32, wrap: i32) -> bool),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.gainedForeground0(II)V" => gained_foreground(this: u32, hardware: i32, display: i32) -> ()),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.displayStateChanged0(II)V" => display_state_changed(this: u32, hardware: i32, state: i32) -> ()),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDevice.clamshellHandling0()V" => clamshell_handling(this: u32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.Display.drawTrustedIcon0(IZ)V" => draw_trusted_icon(this: u32, icon: i32, enabled: bool) -> ()),
        crate::native_bind!(static "javax.microedition.lcdui.Display.setTitle(Ljava/lang/String;)V" => display_set_title(title: u32) -> ()),
        crate::native_bind!(static "javax.microedition.lcdui.NativeMenu.updateCommands([Ljavax/microedition/lcdui/Command;I[Ljavax/microedition/lcdui/Command;I)V" => native_menu_update_commands(item_commands: u32, item_count: i32, screen_commands: u32, screen_count: i32) -> ()),
        crate::native_bind!(static "javax.microedition.lcdui.NativeMenu.updateSoftKeyLabels(Ljava/lang/String;Ljava/lang/String;)V" => native_menu_update_soft_key_labels(left: u32, right: u32) -> ()),
        crate::native_bind!(async static "javax.microedition.lcdui.NativeTextInput.show0(Ljava/lang/Object;Ljava/lang/String;II)Ljava/lang/String;" => text_input_show(text_field: u32, text: u32, constraints: i32, max_size: i32) -> u32),
        crate::native_bind!(static "javax.microedition.lcdui.NativeTextInput.hide()V" => text_input_hide() -> ()),
        crate::native_bind!(async static "javax.microedition.lcdui.NativeDateInput.show0(Ljava/lang/Object;IZJ)[J" => date_input_show(date_field: u32, mode: i32, initialized: bool, value_millis: i64) -> u32),
        crate::native_bind!(static "javax.microedition.lcdui.NativeDateInput.hide()V" => date_input_hide() -> ()),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDeviceAccess.vibrate0(IZ)Z" => vibrate(this: u32, hardware: i32, on: bool) -> bool),
        crate::native_bind!(instance "com.sun.midp.lcdui.DisplayDeviceAccess.isBacklightSupported0(I)Z" => backlight_supported(this: u32, hardware: i32) -> bool),
        crate::native_bind!(instance "javax.microedition.lcdui.DisplayableLFImpl.deleteNativeResource0(I)V" => displayable_delete(this: u32, id: i32) -> ()),
        crate::native_bind!(static "javax.microedition.lcdui.DisplayableLFImpl.initialize0()V" => displayable_initialize() -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.DisplayableLFImpl.finalize()V" => displayable_finalize(this: u32) -> ()),
        crate::native_bind!(static "javax.microedition.lcdui.DisplayableLFImpl.setTitle0(ILjava/lang/String;)V" => displayable_set_title(id: i32, title: u32) -> ()),
        crate::native_bind!(static "javax.microedition.lcdui.DisplayableLFImpl.setTicker0(ILjava/lang/String;)V" => displayable_set_ticker(id: i32, ticker: u32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.CanvasLFImpl.createNativeResource0(Ljava/lang/String;Ljava/lang/String;)I" => canvas_create(this: u32, title: u32, path: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.FormLFImpl.setScrollPosition0(I)V" => form_set_scroll_position(this: u32, position: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.FormLFImpl.getScrollPosition0()I" => form_get_scroll_position(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.FormLFImpl.setCurrentItem0(III)V" => form_set_current_item(this: u32, id: i32, height: i32, offset: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.FormLFImpl.createNativeResource0(Ljava/lang/String;Ljava/lang/String;)I" => form_create(this: u32, title: u32, path: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.FormLFImpl.showNativeResource0(IIIIZ)V" => form_show(this: u32, x: i32, y: i32, width: i32, height: i32, java_paint_only: bool) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.FormLFImpl.getViewportHeight0()I" => form_viewport_height(this: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.AlertLFImpl.createNativeResource0(Ljava/lang/String;Ljava/lang/String;I)I" => alert_create(this: u32, title: u32, text: u32, kind: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.AlertLFImpl.setNativeContents0(ILjavax/microedition/lcdui/ImageData;[ILjava/lang/String;)Z" => alert_set_contents(this: u32, id: i32, image: u32, bounds: u32, text: u32) -> bool),
        crate::native_bind!(instance "javax.microedition.lcdui.AlertLFImpl.showNativeResource0(I)V" => alert_show(this: u32, id: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ItemLFImpl.setSize0(III)V" => item_set_size(this: u32, id: i32, width: i32, height: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ItemLFImpl.setLocation0(III)V" => item_set_location(this: u32, id: i32, x: i32, y: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ItemLFImpl.show0(I)V" => item_show(this: u32, id: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ItemLFImpl.hide0(I)V" => item_hide(this: u32, id: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ItemLFImpl.delete0(I)V" => item_delete(this: u32, id: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ItemLFImpl.getMinimumWidth0(I)I" => item_minimum_width(this: u32, id: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.ItemLFImpl.getMinimumHeight0(I)I" => item_minimum_height(this: u32, id: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.ItemLFImpl.getPreferredWidth0(II)I" => item_preferred_width(this: u32, id: i32, height: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.ItemLFImpl.getPreferredHeight0(II)I" => item_preferred_height(this: u32, id: i32, width: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.ItemLFImpl.setLabel0(ILjava/lang/String;)V" => item_set_label(this: u32, id: i32, label: u32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.DateFieldLFImpl.createNativeResource0(ILjava/lang/String;IJILjava/lang/String;)I" => datefield_create(this: u32, owner_id: i32, label: u32, layout: i32, datetime: i64, display_mode: i32, time_zone: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.DateFieldLFImpl.setDate0(IJ)V" => datefield_set_date(this: u32, id: i32, date: i64) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.DateFieldLFImpl.setInputMode0(II)V" => datefield_set_input_mode(this: u32, id: i32, mode: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.DateFieldLFImpl.getDate0(I)J" => datefield_get_date(this: u32, id: i32) -> i64),
        crate::native_bind!(instance "javax.microedition.lcdui.GaugeLFImpl.createNativeResource0(ILjava/lang/String;IZII)I" => gauge_create(this: u32, owner_id: i32, label: u32, layout: i32, interactive: bool, value: i32, max_value: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.TextFieldLFImpl.createNativeResource0(ILjava/lang/String;ILcom/sun/midp/lcdui/DynamicCharacterArray;ILjava/lang/String;)I" => textfield_create(this: u32, owner_id: i32, label: u32, constraints: i32, buffer: u32, max_size: i32, text: u32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.ImageItemLFImpl.createNativeResource0(ILjava/lang/String;ILjavax/microedition/lcdui/ImageData;Ljava/lang/String;I)I" => imageitem_create(this: u32, owner_id: i32, label: u32, layout: i32, image_data: u32, alt_text: u32, appearance_mode: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.createNativeResource0(ILjava/lang/String;III[Ljavax/microedition/lcdui/ChoiceGroup$CGElement;II)I" => choicegroup_create(this: u32, owner_id: i32, label: u32, layout: i32, choice_type: i32, fit_policy: i32, elements: u32, count: i32, selected_index: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.insert0(IILjava/lang/String;Ljavax/microedition/lcdui/ImageData;Z)V" => choicegroup_insert(this: u32, id: i32, element: i32, text: u32, image: u32, selected: bool) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.delete0(III)V" => choicegroup_delete(this: u32, id: i32, element: i32, selected: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.deleteAll0(I)V" => choicegroup_delete_all(this: u32, id: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.set0(IILjava/lang/String;Ljavax/microedition/lcdui/ImageData;Z)V" => choicegroup_set(this: u32, id: i32, element: i32, text: u32, image: u32, selected: bool) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.setSelectedIndex0(IIZ)V" => choicegroup_set_selected_index(this: u32, id: i32, element: i32, selected: bool) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.setSelectedFlags0(I[ZI)V" => choicegroup_set_selected_flags(this: u32, id: i32, selected: u32, count: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.setFitPolicy0(II)V" => choicegroup_set_fit_policy(this: u32, id: i32, fit_policy: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.setFont0(IIIII)V" => choicegroup_set_font(this: u32, id: i32, element: i32, face: i32, style: i32, size: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.getSelectedIndex0(I)I" => choicegroup_selected_index(this: u32, id: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.getSelectedFlags0(I[ZI)I" => choicegroup_selected_flags(this: u32, id: i32, selected: u32, count: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.ChoiceGroupLFImpl.isSelected0(II)Z" => choicegroup_is_selected(this: u32, id: i32, element: i32) -> bool),
        crate::native_bind!(instance "javax.microedition.lcdui.StringItemLFImpl.createNativeResource0(ILjava/lang/String;ILjava/lang/String;IIII)I" => stringitem_create(this: u32, owner_id: i32, label: u32, layout: i32, text: u32, appearance_mode: i32, face: i32, style: i32, size: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.StringItemLFImpl.setContent0(ILjava/lang/String;I)V" => stringitem_set_content_simple(this: u32, id: i32, text: u32, appearance_mode: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.StringItemLFImpl.setFont0(IIII)V" => stringitem_set_font(this: u32, id: i32, font: i32, max_width: i32, appearance_mode: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.ImageItemLFImpl.setContent0(ILjavax/microedition/lcdui/ImageData;Ljava/lang/String;I)V" => imageitem_set_content(this: u32, id: i32, image: u32, alt_text: u32, appearance_mode: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.TextFieldLFImpl.getString0(ILcom/sun/midp/lcdui/DynamicCharacterArray;)Z" => textfield_get_string(this: u32, id: i32, buffer: u32) -> bool),
        crate::native_bind!(instance "javax.microedition.lcdui.TextFieldLFImpl.setString0(ILcom/sun/midp/lcdui/DynamicCharacterArray;)V" => textfield_set_string(this: u32, id: i32, buffer: u32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.TextFieldLFImpl.setMaxSize0(II)V" => textfield_set_max_size(this: u32, id: i32, max_size: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.TextFieldLFImpl.getCaretPosition0(I)I" => textfield_caret_position(this: u32, id: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.TextFieldLFImpl.setConstraints0(II)V" => textfield_set_constraints(this: u32, id: i32, constraints: i32) -> ()),
        crate::native_bind!(instance "javax.microedition.lcdui.GaugeLFImpl.setValue0(III)V" => gauge_set_value(this: u32, id: i32, value: i32, max_value: i32) -> ()),
        crate::native_bind!(static "javax.microedition.lcdui.CustomItemLFImpl.createNativeResource0(ILjava/lang/String;I)I" => customitem_create(owner_id: i32, label: u32, layout: i32) -> i32),
        crate::native_bind!(static "javax.microedition.lcdui.CustomItemLFImpl.refresh0(IIIII)V" => customitem_refresh(id: i32, x: i32, y: i32, width: i32, height: i32) -> ()),
        crate::native_bind!(static "javax.microedition.lcdui.CustomItemLFImpl.getLabelHeight0(II)I" => customitem_label_height(id: i32, width: i32) -> i32),
        crate::native_bind!(static "javax.microedition.lcdui.CustomItemLFImpl.getLabelWidth0(II)I" => customitem_label_width(id: i32, width: i32) -> i32),
        crate::native_bind!(static "javax.microedition.lcdui.CustomItemLFImpl.getItemPad0(I)I" => customitem_pad(id: i32) -> i32),
        crate::native_bind!(static "javax.microedition.lcdui.CustomItemLFImpl.setContentBuffer0(ILjavax/microedition/lcdui/ImageData;)V" => customitem_set_content_buffer(id: i32, image: u32) -> ()),
    ];
    registry.register_bindings(&bindings);
}
