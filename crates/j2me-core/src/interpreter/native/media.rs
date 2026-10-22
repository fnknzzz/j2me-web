use crate::heap::NULL;
use crate::interpreter::native::registry::{NativeContext, NativeRegistry};
use crate::vm::{cmd, SharedVm};

// --- Binary media command helpers ---

/// Write a media command with only a handle: [cmd:u8][handle:i32]
fn emit_media_cmd(vm: &mut SharedVm, cmd_id: u8, handle: i32) {
    vm.command_buffer.push(cmd_id);
    vm.command_buffer.extend_from_slice(&handle.to_le_bytes());
}

/// Write a media command with handle + one i32 value: [cmd:u8][handle:i32][value:i32]
fn emit_media_cmd_i32(vm: &mut SharedVm, cmd_id: u8, handle: i32, value: i32) {
    vm.command_buffer.push(cmd_id);
    vm.command_buffer.extend_from_slice(&handle.to_le_bytes());
    vm.command_buffer.extend_from_slice(&value.to_le_bytes());
}

/// Write a media command with handle + one bool: [cmd:u8][handle:i32][bool:u8][pad:3]
fn emit_media_cmd_bool(vm: &mut SharedVm, cmd_id: u8, handle: i32, value: bool) {
    vm.command_buffer.push(cmd_id);
    vm.command_buffer.extend_from_slice(&handle.to_le_bytes());
    vm.command_buffer.push(if value { 1 } else { 0 });
    vm.command_buffer.push(0);
    vm.command_buffer.push(0);
    vm.command_buffer.push(0);
}

fn config_list_content_types_open(ctx: &mut NativeContext<'_>, _this: u32, _proto: u32) -> i32 {
    let vm = &mut ctx.vm;
    let id = vm.media_list_next_id;
    vm.media_list_next_id += 1;
    vm.media_list_cursors.insert(id, 0);
    id
}
fn config_list_content_types_next(ctx: &mut NativeContext<'_>, _this: u32, id: i32) -> u32 {
    let vm = &mut ctx.vm;
    let types = ["audio/midi", "audio/x-tone-seq", "audio/basic"];
    let idx = vm
        .media_list_cursors
        .get(&id)
        .copied()
        .unwrap_or(types.len());
    if idx < types.len() {
        vm.media_list_cursors.insert(id, idx + 1);
        let s = vm.create_string(types[idx]);
        s
    } else {
        NULL
    }
}
fn config_list_content_types_close(ctx: &mut NativeContext<'_>, _this: u32, id: i32) {
    ctx.vm.media_list_cursors.remove(&id);
}
fn config_list_protocols_open(ctx: &mut NativeContext<'_>, _this: u32, _ct: u32) -> i32 {
    let vm = &mut ctx.vm;
    let id = vm.media_list_next_id;
    vm.media_list_next_id += 1;
    vm.media_list_cursors.insert(id, 0);
    id
}
fn config_list_protocols_next(ctx: &mut NativeContext<'_>, _this: u32, id: i32) -> u32 {
    let vm = &mut ctx.vm;
    let protos = ["file", "http", "https", "device", "memory"];
    let idx = vm
        .media_list_cursors
        .get(&id)
        .copied()
        .unwrap_or(protos.len());
    if idx < protos.len() {
        vm.media_list_cursors.insert(id, idx + 1);
        let s = vm.create_string(protos[idx]);
        s
    } else {
        NULL
    }
}
fn config_list_protocols_close(ctx: &mut NativeContext<'_>, _this: u32, id: i32) {
    ctx.vm.media_list_cursors.remove(&id);
}
fn config_is_radio_supported(_ctx: &mut NativeContext<'_>, _this: u32) -> bool {
    false
}
fn player_impl_init(
    ctx: &mut NativeContext<'_>,
    this_ref: u32,
    _app_id: i32,
    _p_id: i32,
    uri_ref: u32,
) -> i32 {
    let vm = &mut ctx.vm;
    let uri = vm.get_string_value(uri_ref).unwrap_or_default();
    let id = vm.alloc_media_player_id();
    vm.media_players.insert(
        id,
        crate::vm::MediaPlayerState {
            volume: 100,
            muted: false,
            state: 100,
            locator: uri.clone(),
            content_type: String::new(),
            data: None,
            listeners: Vec::new(),
            media_time_ms: 0,
        },
    );
    vm.player_obj_to_id.insert(this_ref, id);
    if !uri.is_empty() {
        // Binary: [cmd:u8][handle:i32][locator_len:u32][locator_utf8][pad_to_4]
        vm.command_buffer.push(cmd::MEDIA_CREATE_PLAYER);
        vm.command_buffer.extend_from_slice(&id.to_le_bytes());
        let locator_bytes = uri.as_bytes();
        vm.command_buffer
            .extend_from_slice(&(locator_bytes.len() as u32).to_le_bytes());
        vm.command_buffer.extend_from_slice(locator_bytes);
        let padding = (4 - (locator_bytes.len() % 4)) % 4;
        for _ in 0..padding {
            vm.command_buffer.push(0);
        }
    }
    id
}
fn player_impl_realize(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) {
    let vm = &mut ctx.vm;
    if let Some(ps) = vm.media_players.get_mut(&handle) {
        ps.state = 200;
    }
}
fn player_impl_prefetch(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) {
    let vm = &mut ctx.vm;
    if let Some(ps) = vm.media_players.get_mut(&handle) {
        ps.state = 300;
    }
    emit_media_cmd(vm, cmd::MEDIA_PREFETCH, handle);
}
fn player_impl_start(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) {
    let vm = &mut ctx.vm;
    if let Some(ps) = vm.media_players.get_mut(&handle) {
        ps.state = 400;
    }
    // Binary: [cmd:u8][handle:i32][time_ms_lo:i32][time_ms_hi:i32]
    vm.command_buffer.push(cmd::MEDIA_PLAY);
    vm.command_buffer.extend_from_slice(&handle.to_le_bytes());
    vm.command_buffer.extend_from_slice(&0_i32.to_le_bytes()); // time_ms = 0 (play from current)
    vm.command_buffer.extend_from_slice(&0_i32.to_le_bytes());
}
fn player_impl_stop(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) {
    let vm = &mut ctx.vm;
    if let Some(ps) = vm.media_players.get_mut(&handle) {
        ps.state = 300;
    }
    emit_media_cmd(vm, cmd::MEDIA_STOP, handle);
}
fn player_impl_close(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) {
    let vm = &mut ctx.vm;
    vm.media_players.remove(&handle);
    vm.player_obj_to_id.retain(|_, &mut v| v != handle);
    vm.nokia_sound_handles.retain(|_, &mut v| v != handle);
    emit_media_cmd(vm, cmd::MEDIA_CLOSE, handle);
}
fn player_impl_deallocate(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) {
    let vm = &mut ctx.vm;
    let was_started = vm
        .media_players
        .get(&handle)
        .map_or(false, |ps| ps.state == 400);
    if was_started {
        emit_media_cmd(vm, cmd::MEDIA_STOP, handle);
    }
    if let Some(ps) = vm.media_players.get_mut(&handle) {
        ps.state = 200;
    }
}
fn player_impl_get_state(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> i32 {
    ctx.vm.media_players.get(&handle).map_or(100, |ps| ps.state)
}
fn player_impl_get_duration(_ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> i64 {
    let duration_ms = crate::call_host_get_media_duration(handle);
    let duration_us = duration_ms * 1000; // convert ms to microseconds
    duration_us
}
fn player_impl_get_media_time(_ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> i64 {
    let time_ms = crate::call_host_get_media_time(handle);
    let time_us = time_ms * 1000; // convert ms to microseconds
    time_us
}
fn player_impl_set_media_time(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    handle: i32,
    time_us: i64,
) -> i64 {
    let time_ms = time_us / 1000;
    crate::call_host_set_media_time(handle, time_ms);
    time_us
}
fn player_impl_get_content_type(ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> u32 {
    ctx.vm.create_string("audio/midi")
}
fn player_impl_set_loop_count(ctx: &mut NativeContext<'_>, _this: u32, handle: i32, count: i32) {
    emit_media_cmd_i32(ctx.vm, cmd::MEDIA_SET_LOOP_COUNT, handle, count);
}
fn player_impl_get_volume(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> i32 {
    let vm = &mut ctx.vm;
    let vol = vm
        .media_players
        .get(&handle)
        .map(|p| p.volume)
        .unwrap_or(100);
    vol
}
fn player_impl_set_volume(ctx: &mut NativeContext<'_>, _this: u32, handle: i32, level: i32) -> i32 {
    let vm = &mut ctx.vm;
    if let Some(p) = vm.media_players.get_mut(&handle) {
        p.volume = level;
    }
    emit_media_cmd_i32(vm, cmd::MEDIA_SET_VOLUME, handle, level);
    level
}
fn player_impl_is_muted(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> bool {
    let vm = &mut ctx.vm;
    let muted = vm
        .media_players
        .get(&handle)
        .map(|p| p.muted)
        .unwrap_or(false);
    muted
}
fn player_impl_set_mute(ctx: &mut NativeContext<'_>, _this: u32, handle: i32, muted: bool) {
    let vm = &mut ctx.vm;
    if let Some(p) = vm.media_players.get_mut(&handle) {
        p.muted = muted;
    }
    emit_media_cmd_bool(vm, cmd::MEDIA_SET_MUTED, handle, muted);
}
fn player_impl_set_sequence(ctx: &mut NativeContext<'_>, _this: u32, handle: i32, seq_ref: u32) {
    let vm = &mut ctx.vm;
    let len = if seq_ref != NULL {
        vm.heap.array_length(seq_ref)
    } else {
        0
    };
    emit_media_cmd_i32(vm, cmd::MEDIA_SET_SEQUENCE, handle, len as i32);
}
fn direct_is_tone_control_supported(ctx: &mut NativeContext<'_>, handle: i32) -> bool {
    let vm = &mut ctx.vm;
    let supported = vm
        .media_players
        .get(&handle)
        .map_or(false, |p| p.locator.contains("tone"));
    supported
}
fn direct_is_volume_control_supported(_ctx: &mut NativeContext<'_>, _handle: i32) -> bool {
    true
}
fn direct_static_unsupported_control(_ctx: &mut NativeContext<'_>, _handle: i32) -> bool {
    false
}
fn direct_unsupported_control(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> bool {
    false
}
fn direct_acquire_device(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> bool {
    true
}
fn direct_release_device(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) {}
fn direct_flush_buffer(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> bool {
    true
}
fn direct_start(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> bool {
    let vm = &mut ctx.vm;
    vm.command_buffer.push(cmd::MEDIA_PLAY);
    vm.command_buffer.extend_from_slice(&handle.to_le_bytes());
    vm.command_buffer.extend_from_slice(&0_i32.to_le_bytes());
    vm.command_buffer.extend_from_slice(&0_i32.to_le_bytes());
    true
}
fn direct_stop(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> bool {
    emit_media_cmd(ctx.vm, cmd::MEDIA_STOP, handle);
    true
}
fn direct_pause(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> bool {
    emit_media_cmd(ctx.vm, cmd::MEDIA_STOP, handle);
    true
}
fn direct_resume(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> bool {
    true
}
fn direct_get_media_time(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> i32 {
    0
}
fn direct_set_media_time(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32, _ms: i64) -> i32 {
    0
}
fn direct_get_duration(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> i32 {
    1000
}
fn direct_get_content_type(ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> u32 {
    ctx.vm.create_string("audio/midi")
}
fn direct_acquire_radio_access(_ctx: &mut NativeContext<'_>, _this: u32) -> bool {
    true
}
fn direct_release_radio_access(_ctx: &mut NativeContext<'_>, _this: u32) {}
fn direct_switch_ground(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _handle: i32,
    _options: i32,
) -> bool {
    true
}
fn direct_term(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> i32 {
    let vm = &mut ctx.vm;
    vm.media_players.remove(&handle);
    emit_media_cmd(vm, cmd::MEDIA_CLOSE, handle);
    1
}
fn direct_prefetch(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> bool {
    true
}
fn direct_get_width(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> i32 {
    0
}
fn direct_get_height(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> i32 {
    0
}
fn direct_finalize(_ctx: &mut NativeContext<'_>, _this: u32) {}
fn direct_set_location(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _handle: i32,
    _x: i32,
    _y: i32,
    _w: i32,
    _h: i32,
) -> bool {
    true
}
fn direct_set_full_screen_mode(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _handle: i32,
    _fullscreen: bool,
) -> bool {
    true
}
fn direct_set_visible(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _handle: i32,
    _visible: bool,
) -> bool {
    true
}
fn direct_set_color_key(
    _ctx: &mut NativeContext<'_>,
    _this: u32,
    _handle: i32,
    _on: bool,
    _color_key: i32,
) -> bool {
    true
}
fn direct_start_snapshot(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32, _image_type: u32) {
}
fn direct_get_snapshot_data(_ctx: &mut NativeContext<'_>, _this: u32, _handle: i32) -> u32 {
    NULL
}
fn direct_volume_get(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> i32 {
    let vm = &mut ctx.vm;
    let vol = vm
        .media_players
        .get(&handle)
        .map(|p| p.volume)
        .unwrap_or(100);
    vol
}
fn direct_volume_set(ctx: &mut NativeContext<'_>, _this: u32, handle: i32, level: i32) -> i32 {
    let vm = &mut ctx.vm;
    if let Some(p) = vm.media_players.get_mut(&handle) {
        p.volume = level;
    }
    emit_media_cmd_i32(vm, cmd::MEDIA_SET_VOLUME, handle, level);
    level
}
fn direct_volume_is_muted(ctx: &mut NativeContext<'_>, _this: u32, handle: i32) -> bool {
    let vm = &mut ctx.vm;
    let muted = vm
        .media_players
        .get(&handle)
        .map(|p| p.muted)
        .unwrap_or(false);
    muted
}
fn direct_volume_set_mute(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    handle: i32,
    muted: bool,
) -> bool {
    let vm = &mut ctx.vm;
    if let Some(p) = vm.media_players.get_mut(&handle) {
        p.muted = muted;
    }
    emit_media_cmd_bool(vm, cmd::MEDIA_SET_MUTED, handle, muted);
    true
}
fn direct_tone_set_sequence(ctx: &mut NativeContext<'_>, handle: i32, seq_ref: u32) {
    let vm = &mut ctx.vm;
    let len = if seq_ref != NULL {
        vm.heap.array_length(seq_ref)
    } else {
        0
    };
    emit_media_cmd_i32(vm, cmd::MEDIA_SET_SEQUENCE, handle, len as i32);
}
fn tone_player_play(
    ctx: &mut NativeContext<'_>,
    _this: u32,
    _app_id: i32,
    note: i32,
    dur: i32,
    vol: i32,
) -> bool {
    let vm = &mut ctx.vm;
    // Binary: [cmd:u8][note:i32][duration:i32][volume:i32]
    vm.command_buffer.push(cmd::MEDIA_PLAY_TONE);
    vm.command_buffer.extend_from_slice(&note.to_le_bytes());
    vm.command_buffer.extend_from_slice(&dur.to_le_bytes());
    vm.command_buffer.extend_from_slice(&vol.to_le_bytes());
    true
}
fn tone_player_stop(_ctx: &mut NativeContext<'_>, _this: u32, _app_id: i32) -> bool {
    true
}
fn tone_player_finalize(_ctx: &mut NativeContext<'_>, _this: u32) {}
fn manager_send_stream_data(ctx: &mut NativeContext<'_>, handle: i32, data_ref: u32, ct_ref: u32) {
    let vm = &mut ctx.vm;
    let ct = vm.get_string_value(ct_ref).unwrap_or_default();
    let audio_data = if data_ref != NULL {
        let len = vm.heap.array_length(data_ref) as usize;
        let bytes: Vec<u8> = (0..len)
            .map(|i| vm.heap.read_i8(data_ref + 8 + i as u32) as u8)
            .collect();
        Some(bytes)
    } else {
        None
    };
    // Save content_type and data into MediaPlayerState for snapshot restore
    if let Some(ps) = vm.media_players.get_mut(&handle) {
        ps.content_type = ct.clone();
        ps.data = audio_data.clone();
    }
    // Write binary command: MEDIA_CREATE_PLAYER_BINARY
    // Format: [cmd:u8][handle:i32][loopCount:i32][volume:i32][ct_len:u32][ct+pad][data_len:u32][data+pad]
    let ct_bytes = ct.as_bytes();
    let ct_padded = (ct_bytes.len() + 3) & !3;
    let data_bytes = audio_data.as_deref().unwrap_or(&[]);
    let data_padded = (data_bytes.len() + 3) & !3;

    let buf = &mut vm.command_buffer;
    buf.push(cmd::MEDIA_CREATE_PLAYER_BINARY);
    buf.extend_from_slice(&handle.to_le_bytes());
    buf.extend_from_slice(&1_i32.to_le_bytes()); // loopCount = 1 (default)
    buf.extend_from_slice(&100_i32.to_le_bytes()); // volume = 100 (default)
                                                   // content type (length-prefixed, 4-byte aligned)
    buf.extend_from_slice(&(ct_bytes.len() as u32).to_le_bytes());
    buf.extend_from_slice(ct_bytes);
    for _ in 0..(ct_padded - ct_bytes.len()) {
        buf.push(0);
    }
    // audio data (length-prefixed, 4-byte aligned)
    buf.extend_from_slice(&(data_bytes.len() as u32).to_le_bytes());
    buf.extend_from_slice(data_bytes);
    for _ in 0..(data_padded - data_bytes.len()) {
        buf.push(0);
    }
}
fn media_download_get_size(_ctx: &mut NativeContext<'_>, _handle: i32) -> i32 {
    0
}
fn media_download_buffering(
    _ctx: &mut NativeContext<'_>,
    _handle: i32,
    _buf: u32,
    _offset: i32,
    _size: i32,
) -> i32 {
    0
}
fn media_download_need_more_data(_ctx: &mut NativeContext<'_>, _handle: i32) -> bool {
    false
}
fn media_download_set_content_size(_ctx: &mut NativeContext<'_>, _handle: i32, _content_size: i64) {
}

pub fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(instance "com.sun.mmedia.DefaultConfiguration.nListContentTypesOpen(Ljava/lang/String;)I" => config_list_content_types_open(this: u32, protocol: u32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.DefaultConfiguration.nListContentTypesNext(I)Ljava/lang/String;" => config_list_content_types_next(this: u32, handler: i32) -> u32),
        crate::native_bind!(instance "com.sun.mmedia.DefaultConfiguration.nListContentTypesClose(I)V" => config_list_content_types_close(this: u32, handler: i32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.DefaultConfiguration.nListProtocolsOpen(Ljava/lang/String;)I" => config_list_protocols_open(this: u32, mime: u32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.DefaultConfiguration.nListProtocolsNext(I)Ljava/lang/String;" => config_list_protocols_next(this: u32, handler: i32) -> u32),
        crate::native_bind!(instance "com.sun.mmedia.DefaultConfiguration.nListProtocolsClose(I)V" => config_list_protocols_close(this: u32, handler: i32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.DefaultConfiguration.nIsRadioSupported()Z" => config_is_radio_supported(this: u32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nInit(IILjava/lang/String;)I" => player_impl_init(this: u32, app_id: i32, player_id: i32, uri: u32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nRealize(I)V" => player_impl_realize(this: u32, handle: i32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nPrefetch(I)V" => player_impl_prefetch(this: u32, handle: i32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nStart(I)V" => player_impl_start(this: u32, handle: i32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nStop(I)V" => player_impl_stop(this: u32, handle: i32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nClose(I)V" => player_impl_close(this: u32, handle: i32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nDeallocate(I)V" => player_impl_deallocate(this: u32, handle: i32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nGetState(I)I" => player_impl_get_state(this: u32, handle: i32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nGetDuration(I)J" => player_impl_get_duration(this: u32, handle: i32) -> i64),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nGetMediaTime(I)J" => player_impl_get_media_time(this: u32, handle: i32) -> i64),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nSetMediaTime(IJ)J" => player_impl_set_media_time(this: u32, handle: i32, time: i64) -> i64),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nGetContentType(I)Ljava/lang/String;" => player_impl_get_content_type(this: u32, handle: i32) -> u32),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nSetLoopCount(II)V" => player_impl_set_loop_count(this: u32, handle: i32, count: i32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nGetVolume(I)I" => player_impl_get_volume(this: u32, handle: i32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nSetVolume(II)I" => player_impl_set_volume(this: u32, handle: i32, level: i32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nIsMuted(I)Z" => player_impl_is_muted(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nSetMute(IZ)V" => player_impl_set_mute(this: u32, handle: i32, muted: bool) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.PlayerImpl.nSetSequence(I[B)V" => player_impl_set_sequence(this: u32, handle: i32, sequence: u32) -> ()),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsToneControlSupported(I)Z" => direct_is_tone_control_supported(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsVolumeControlSupported(I)Z" => direct_is_volume_control_supported(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsMIDIControlSupported(I)Z" => direct_static_unsupported_control(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsFramePositioningControlSupported(I)Z" => direct_static_unsupported_control(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsMetaDataControlSupported(I)Z" => direct_static_unsupported_control(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsPitchControlSupported(I)Z" => direct_static_unsupported_control(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsRateControlSupported(I)Z" => direct_static_unsupported_control(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsRecordControlSupported(I)Z" => direct_static_unsupported_control(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsStopTimeControlSupported(I)Z" => direct_static_unsupported_control(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsTempoControlSupported(I)Z" => direct_static_unsupported_control(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectPlayer.nIsVideoControlSupported(I)Z" => direct_static_unsupported_control(handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nPcmAudioPlayback(I)Z" => direct_unsupported_control(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nIsNeedBuffering(I)Z" => direct_unsupported_control(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nAcquireDevice(I)Z" => direct_acquire_device(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nReleaseDevice(I)V" => direct_release_device(this: u32, handle: i32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nFlushBuffer(I)Z" => direct_flush_buffer(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nStart(I)Z" => direct_start(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nStop(I)Z" => direct_stop(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nPause(I)Z" => direct_pause(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nResume(I)Z" => direct_resume(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nGetMediaTime(I)I" => direct_get_media_time(this: u32, handle: i32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nSetMediaTime(IJ)I" => direct_set_media_time(this: u32, handle: i32, time: i64) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nGetDuration(I)I" => direct_get_duration(this: u32, handle: i32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nGetContentType(I)Ljava/lang/String;" => direct_get_content_type(this: u32, handle: i32) -> u32),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nAcquireRadioAccess()Z" => direct_acquire_radio_access(this: u32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nReleaseRadioAccess()V" => direct_release_radio_access(this: u32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nSwitchToForeground(II)Z" => direct_switch_ground(this: u32, handle: i32, options: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nSwitchToBackground(II)Z" => direct_switch_ground(this: u32, handle: i32, options: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nTerm(I)I" => direct_term(this: u32, handle: i32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nPrefetch(I)Z" => direct_prefetch(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nGetWidth(I)I" => direct_get_width(this: u32, handle: i32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nGetHeight(I)I" => direct_get_height(this: u32, handle: i32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.finalize()V" => direct_finalize(this: u32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nSetLocation(IIIII)Z" => direct_set_location(this: u32, handle: i32, x: i32, y: i32, width: i32, height: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nSetFullScreenMode(IZ)Z" => direct_set_full_screen_mode(this: u32, handle: i32, fullscreen: bool) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nSetVisible(IZ)Z" => direct_set_visible(this: u32, handle: i32, visible: bool) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nSetColorKey(IZI)Z" => direct_set_color_key(this: u32, handle: i32, on: bool, color_key: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nStartSnapshot(ILjava/lang/String;)V" => direct_start_snapshot(this: u32, handle: i32, image_type: u32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.DirectPlayer.nGetSnapshotData(I)[B" => direct_get_snapshot_data(this: u32, handle: i32) -> u32),
        crate::native_bind!(instance "com.sun.mmedia.DirectVolume.nGetVolume(I)I" => direct_volume_get(this: u32, handle: i32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.DirectVolume.nSetVolume(II)I" => direct_volume_set(this: u32, handle: i32, level: i32) -> i32),
        crate::native_bind!(instance "com.sun.mmedia.DirectVolume.nIsMuted(I)Z" => direct_volume_is_muted(this: u32, handle: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.DirectVolume.nSetMute(IZ)Z" => direct_volume_set_mute(this: u32, handle: i32, muted: bool) -> bool),
        crate::native_bind!(static "com.sun.mmedia.DirectTone.nSetSequence(I[B)V" => direct_tone_set_sequence(handle: i32, sequence: u32) -> ()),
        crate::native_bind!(instance "com.sun.mmedia.NativeTonePlayer.nPlayTone(IIII)Z" => tone_player_play(this: u32, app_id: i32, note: i32, duration: i32, volume: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.NativeTonePlayer.nStopTone(I)Z" => tone_player_stop(this: u32, app_id: i32) -> bool),
        crate::native_bind!(instance "com.sun.mmedia.NativeTonePlayer.finalize()V" => tone_player_finalize(this: u32) -> ()),
        crate::native_bind!(static "javax.microedition.media.Manager.nSendPlayerStreamData(I[BLjava/lang/String;)V" => manager_send_stream_data(handle: i32, data: u32, content_type: u32) -> ()),
        crate::native_bind!(static "com.sun.mmedia.MediaDownload.nGetJavaBufferSize(I)I" => media_download_get_size(handle: i32) -> i32),
        crate::native_bind!(static "com.sun.mmedia.MediaDownload.nGetFirstPacketSize(I)I" => media_download_get_size(handle: i32) -> i32),
        crate::native_bind!(static "com.sun.mmedia.MediaDownload.nBuffering(I[BII)I" => media_download_buffering(handle: i32, buffer: u32, offset: i32, size: i32) -> i32),
        crate::native_bind!(static "com.sun.mmedia.MediaDownload.nNeedMoreDataImmediatelly(I)Z" => media_download_need_more_data(handle: i32) -> bool),
        crate::native_bind!(static "com.sun.mmedia.MediaDownload.nSetWholeContentSize(IJ)V" => media_download_set_content_size(handle: i32, content_size: i64) -> ()),
    ];
    registry.register_bindings(&bindings);
}
