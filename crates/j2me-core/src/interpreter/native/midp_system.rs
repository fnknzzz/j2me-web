use crate::vm::SharedVm;

/// Fill a NativeEvent Java object's fields from a NativeEventData struct.
fn fill_native_event(vm: &mut SharedVm, event_ref: u32, data: &crate::vm::NativeEventData) {
    let cls = "com.sun.midp.events.NativeEvent";
    if let Some(off) = vm.class_repo.find_field_offset(cls, "type") {
        vm.heap.write_u32(event_ref + off, data.event_type as u32);
    }
    if let Some(off) = vm.class_repo.find_field_offset(cls, "intParam1") {
        vm.heap.write_u32(event_ref + off, data.int_param1 as u32);
    }
    if let Some(off) = vm.class_repo.find_field_offset(cls, "intParam2") {
        vm.heap.write_u32(event_ref + off, data.int_param2 as u32);
    }
    if let Some(off) = vm.class_repo.find_field_offset(cls, "intParam3") {
        vm.heap.write_u32(event_ref + off, data.int_param3 as u32);
    }
    if let Some(off) = vm.class_repo.find_field_offset(cls, "intParam4") {
        vm.heap.write_u32(event_ref + off, data.int_param4 as u32);
    }
}

use crate::interpreter::native::registry::{NativeContext, NativeRegistry, NativeResult};

// --- Individual native handler functions for ID-based dispatch ---

fn eventqueue_reset(_ctx: &mut NativeContext<'_>, _this: u32) {}
fn eventqueue_get_handle(_ctx: &mut NativeContext<'_>, _this: u32) -> i32 {
    0
}
fn eventqueue_send_native_event(
    ctx: &mut NativeContext<'_>,
    this: u32,
    _event: u32,
    _isolate_id: i32,
) {
    let monitor = ctx.vm.monitors.entry(this).or_default();
    if let Some((tid, saved_recursion)) = monitor.notify() {
        ctx.vm
            .pending_notify_wakeups
            .push((this, tid, saved_recursion));
    }
}
fn eventqueue_send_shutdown(_ctx: &mut NativeContext<'_>, _this: u32) {}
fn eventqueue_finalize(_ctx: &mut NativeContext<'_>, _this: u32) {}
fn eventqueue_handle_fatal_error(_ctx: &mut NativeContext<'_>, _throwable: u32) {}
fn event_monitor_wait(ctx: &mut NativeContext<'_>, event: u32) -> NativeResult<i32> {
    ctx.vm.native_event_monitor_tid = Some(ctx.thread_id);
    if let Some(evt) = ctx.vm.native_event_queue.pop_front() {
        fill_native_event(ctx.vm, event, &evt);
        NativeResult::Ready(ctx.vm.native_event_queue.len() as i32)
    } else {
        NativeResult::Yield(crate::scheduler::virtual_thread::YieldReason::NativeEventWait)
    }
}
fn event_monitor_read(ctx: &mut NativeContext<'_>, event: u32) -> bool {
    if let Some(evt) = ctx.vm.native_event_queue.pop_front() {
        fill_native_event(ctx.vm, event, &evt);
        true
    } else {
        false
    }
}

fn ams_isolate_id(_ctx: &mut NativeContext<'_>) -> i32 {
    1
}
fn isolate_id(_ctx: &mut NativeContext<'_>) -> i32 {
    1
}
fn is_ams_isolate(_ctx: &mut NativeContext<'_>) -> bool {
    false
}
fn max_isolates(_ctx: &mut NativeContext<'_>) -> i32 {
    1
}
fn register_ams_isolate_id(_ctx: &mut NativeContext<'_>) {}
fn vm_begin_startup(_ctx: &mut NativeContext<'_>, _isolate_id: i32) {}
fn vm_end_startup(_ctx: &mut NativeContext<'_>, _isolate_id: i32) {}

fn configuration_get_property(ctx: &mut NativeContext<'_>, key: u32) -> u32 {
    let key_str = ctx.vm.get_string_value(key).unwrap_or_default();
    if let Some(val) = ctx.vm.system_properties.get(&key_str).cloned() {
        return ctx.vm.create_string(&val);
    }
    let val = match key_str.as_str() {
        "microedition.platform" => "j2me",
        "microedition.configuration" => "CLDC-1.1",
        "microedition.profiles" => "MIDP-2.0",
        "microedition.hostname" => "localhost",
        _ => {
            return 0;
        }
    };
    ctx.vm.create_string(val)
}
fn midlet_platform_request(_ctx: &mut NativeContext<'_>, _this: u32, _url: u32) -> bool {
    false
}
fn midlet_notify_destroyed(ctx: &mut NativeContext<'_>, _this: u32) {
    ctx.vm.midlet_destroyed = true;
}
fn command_state_restore(_ctx: &mut NativeContext<'_>, _state: u32) {}
fn command_state_exit(_ctx: &mut NativeContext<'_>, _exit_code: i32) {}
fn all_midlets_killed(_ctx: &mut NativeContext<'_>, _this: u32) -> bool {
    false
}
fn reset_foreground(_ctx: &mut NativeContext<'_>, _this: u32) {}
fn set_foreground(_ctx: &mut NativeContext<'_>, _this: u32, _isolate_id: i32, _display_id: i32) {}
fn load_romized_resource(_ctx: &mut NativeContext<'_>, _name: u32) -> u32 {
    0
}
fn localized_content(ctx: &mut NativeContext<'_>, index: i32) -> u32 {
    let text = match index {
        0 => "Done",
        1 => "Exit",
        2 => "OK",
        3 => "Sun",
        4 => "Mon",
        5 => "Tue",
        6 => "Wed",
        7 => "Thu",
        8 => "Fri",
        9 => "Sat",
        10 => "Jan",
        11 => "Feb",
        12 => "Mar",
        13 => "Apr",
        14 => "May",
        15 => "Jun",
        16 => "Jul",
        17 => "Aug",
        18 => "Sep",
        19 => "Oct",
        20 => "Nov",
        21 => "Dec",
        _ => "",
    };
    ctx.vm.create_string(text)
}
fn connection_registry_poll(_ctx: &mut NativeContext<'_>, _this: u32, _time: i64) -> i32 {
    0
}

fn key_converter_system_key(_ctx: &mut NativeContext<'_>, key_code: i32) -> i32 {
    let system_key = match key_code {
        -6 => 1,
        -7 => 2,
        -8 => 4,
        -9 => 5,
        _ => 0,
    };
    system_key
}
fn key_converter_key_code(_ctx: &mut NativeContext<'_>, game_action: i32) -> i32 {
    let key_code = match game_action {
        1 => -1i32,
        6 => -2,
        2 => -3,
        5 => -4,
        8 => -5,
        9 => 49,
        10 => 51,
        11 => 55,
        12 => 57,
        _ => 0,
    };
    key_code
}
fn key_converter_key_name(ctx: &mut NativeContext<'_>, key_code: i32) -> u32 {
    let name = match key_code {
        -1 => "Up",
        -2 => "Down",
        -3 => "Left",
        -4 => "Right",
        -5 => "Select",
        -6 => "Soft1",
        -7 => "Soft2",
        -8 => "Clear",
        -9 => "Send",
        48 => "0",
        49 => "1",
        50 => "2",
        51 => "3",
        52 => "4",
        53 => "5",
        54 => "6",
        55 => "7",
        56 => "8",
        57 => "9",
        42 => "*",
        35 => "#",
        _ => "",
    };
    if name.is_empty() {
        ctx.vm.create_string(&format!("Key{}", key_code))
    } else {
        ctx.vm.create_string(name)
    }
}
fn key_converter_game_action(_ctx: &mut NativeContext<'_>, key_code: i32) -> i32 {
    let game_action = match key_code {
        -1 | 50 => 1,
        -2 | 56 => 6,
        -3 | 52 => 2,
        -4 | 54 => 5,
        -5 | 53 => 8,
        49 => 9,
        51 => 10,
        55 => 11,
        57 => 12,
        _ => 0,
    };
    game_action
}
fn game_canvas_set_suppress(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _canvas: u32,
    _suppress: bool,
) {
}

pub fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(instance "com.sun.midp.events.EventQueue.resetNativeEventQueue()V" => eventqueue_reset(this: u32) -> ()),
        crate::native_bind!(instance "com.sun.midp.events.EventQueue.getNativeEventQueueHandle()I" => eventqueue_get_handle(this: u32) -> i32),
        crate::native_bind!(instance "com.sun.midp.events.EventQueue.sendNativeEventToIsolate(Lcom/sun/midp/events/NativeEvent;I)V" => eventqueue_send_native_event(this: u32, event: u32, isolate_id: i32) -> ()),
        crate::native_bind!(instance "com.sun.midp.events.EventQueue.sendShutdownEvent()V" => eventqueue_send_shutdown(this: u32) -> ()),
        crate::native_bind!(instance "com.sun.midp.events.EventQueue.finalize()V" => eventqueue_finalize(this: u32) -> ()),
        crate::native_bind!(static "com.sun.midp.events.EventQueue.handleFatalError(Ljava/lang/Throwable;)V" => eventqueue_handle_fatal_error(throwable: u32) -> ()),
        crate::native_bind!(async static "com.sun.midp.events.NativeEventMonitor.waitForNativeEvent(Lcom/sun/midp/events/NativeEvent;)I" => event_monitor_wait(event: u32) -> i32),
        crate::native_bind!(static "com.sun.midp.events.NativeEventMonitor.readNativeEvent(Lcom/sun/midp/events/NativeEvent;)Z" => event_monitor_read(event: u32) -> bool),
        crate::native_bind!(static "com.sun.midp.main.MIDletSuiteUtils.getAmsIsolateId()I" => ams_isolate_id() -> i32),
        crate::native_bind!(static "com.sun.midp.main.MIDletSuiteUtils.getIsolateId()I" => isolate_id() -> i32),
        crate::native_bind!(static "com.sun.midp.main.MIDletSuiteUtils.isAmsIsolate()Z" => is_ams_isolate() -> bool),
        crate::native_bind!(static "com.sun.midp.main.MIDletSuiteUtils.getMaxIsolates()I" => max_isolates() -> i32),
        crate::native_bind!(static "com.sun.midp.main.MIDletSuiteUtils.registerAmsIsolateId()V" => register_ams_isolate_id() -> ()),
        crate::native_bind!(static "com.sun.midp.main.MIDletSuiteUtils.vmBeginStartUp(I)V" => vm_begin_startup(isolate_id: i32) -> ()),
        crate::native_bind!(static "com.sun.midp.main.MIDletSuiteUtils.vmEndStartUp(I)V" => vm_end_startup(isolate_id: i32) -> ()),
        crate::native_bind!(static "com.sun.midp.main.Configuration.getProperty0(Ljava/lang/String;)Ljava/lang/String;" => configuration_get_property(key: u32) -> u32),
        crate::native_bind!(instance "com.sun.midp.midlet.MIDletPeer.platformRequest(Ljava/lang/String;)Z" => midlet_platform_request(this: u32, url: u32) -> bool),
        crate::native_bind!(instance "com.sun.midp.midlet.MIDletPeer.notifyDestroyedNative()V" => midlet_notify_destroyed(this: u32) -> ()),
        crate::native_bind!(static "com.sun.midp.main.CommandState.restoreCommandState(Lcom/sun/midp/main/CommandState;)V" => command_state_restore(state: u32) -> ()),
        crate::native_bind!(static "com.sun.midp.main.CommandState.exitInternal(I)V" => command_state_exit(exit_code: i32) -> ()),
        crate::native_bind!(instance "com.sun.midp.suspend.SuspendSystem$MIDPSystem.allMidletsKilled()Z" => all_midlets_killed(this: u32) -> bool),
        crate::native_bind!(instance "com.sun.midp.main.MIDletProxyList.resetForegroundInNativeState()V" => reset_foreground(this: u32) -> ()),
        crate::native_bind!(instance "com.sun.midp.main.MIDletProxyList.setForegroundInNativeState(II)V" => set_foreground(this: u32, isolate_id: i32, display_id: i32) -> ()),
        crate::native_bind!(static "com.sun.midp.util.ResourceHandler.loadRomizedResource0(Ljava/lang/String;)[B" => load_romized_resource(name: u32) -> u32),
        crate::native_bind!(static "com.sun.midp.l10n.LocalizedStringsBase.getContent(I)Ljava/lang/String;" => localized_content(index: i32) -> u32),
        crate::native_bind!(instance "com.sun.midp.io.j2me.push.ConnectionRegistry.poll0(J)I" => connection_registry_poll(this: u32, time: i64) -> i32),
        crate::native_bind!(static "javax.microedition.lcdui.KeyConverter.getSystemKey(I)I" => key_converter_system_key(key_code: i32) -> i32),
        crate::native_bind!(static "javax.microedition.lcdui.KeyConverter.getKeyCode(I)I" => key_converter_key_code(game_action: i32) -> i32),
        crate::native_bind!(static "javax.microedition.lcdui.KeyConverter.getKeyName(I)Ljava/lang/String;" => key_converter_key_name(key_code: i32) -> u32),
        crate::native_bind!(static "javax.microedition.lcdui.KeyConverter.getGameAction(I)I" => key_converter_game_action(key_code: i32) -> i32),
        crate::native_bind!(instance "javax.microedition.lcdui.game.GameCanvas.setSuppressKeyEvents(Ljavax/microedition/lcdui/Canvas;Z)V" => game_canvas_set_suppress(this: u32, canvas: u32, suppress: bool) -> ()),
    ];
    registry.register_bindings(&bindings);
}
