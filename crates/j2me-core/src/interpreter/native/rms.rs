use crate::heap::NULL;
use crate::scheduler::virtual_thread::YieldReason;
use crate::vm::{AsyncRequestType, AsyncResult, SharedVm};
use crate::{call_host_rms_remove, call_host_rms_save};

use super::registry::{NativeContext, NativeRegistry, NativeResult};

fn issue_async(vm: &mut SharedVm, thread_id: u32, req_type: AsyncRequestType) -> YieldReason {
    YieldReason::AsyncWait {
        request_id: vm.issue_async_request(thread_id, req_type),
    }
}

fn rms_load(ctx: &mut NativeContext<'_>, key: u32) -> NativeResult<u32> {
    if let Some((_request_id, _request, AsyncResult::RmsLoaded { data })) = ctx
        .vm
        .take_resolved_async_for_thread(ctx.thread_id, |result| {
            matches!(result, AsyncResult::RmsLoaded { .. })
        })
    {
        let Some(bytes) = data.filter(|bytes| !bytes.is_empty()) else {
            return NativeResult::Ready(NULL);
        };
        let class_id = ctx.vm.class_repo.get_class_id("[B").unwrap_or(0);
        let array = ctx.vm.new_array(class_id, bytes.len() as u32, 1);
        for (index, byte) in bytes.into_iter().enumerate() {
            ctx.vm.heap.array_write_i8(array, index as u32, byte as i8);
        }
        NativeResult::Ready(array)
    } else {
        let key = ctx.vm.get_string_value(key).unwrap_or_default();
        NativeResult::Yield(issue_async(
            ctx.vm,
            ctx.thread_id,
            AsyncRequestType::RmsLoad { key },
        ))
    }
}

fn rms_save(ctx: &mut NativeContext<'_>, key: u32, data: u32) {
    let key = ctx.vm.get_string_value(key).unwrap_or_default();
    let bytes = if data == NULL {
        Vec::new()
    } else {
        (0..ctx.vm.heap.array_length(data))
            .map(|index| ctx.vm.heap.array_read_i8(data, index) as u8)
            .collect()
    };
    call_host_rms_save(&key, &bytes);
}

fn rms_remove(ctx: &mut NativeContext<'_>, key: u32) {
    call_host_rms_remove(&ctx.vm.get_string_value(key).unwrap_or_default());
}

fn rms_keys(ctx: &mut NativeContext<'_>) -> NativeResult<u32> {
    if let Some((_request_id, _request, AsyncResult::RmsKeysList { keys })) = ctx
        .vm
        .take_resolved_async_for_thread(ctx.thread_id, |result| {
            matches!(result, AsyncResult::RmsKeysList { .. })
        })
    {
        let class_id = ctx.vm.class_repo.get_class_id("[L").unwrap_or(0);
        let array = ctx.vm.new_array(class_id, keys.len() as u32, 4);
        for (index, key) in keys.into_iter().enumerate() {
            let key = ctx.vm.create_string(&key);
            ctx.vm.heap.array_write_u32(array, index as u32, key);
        }
        NativeResult::Ready(array)
    } else {
        NativeResult::Yield(issue_async(
            ctx.vm,
            ctx.thread_id,
            AsyncRequestType::RmsKeys,
        ))
    }
}

fn registry_start_listening(_ctx: &mut NativeContext<'_>, _suite_id: i32, _store_name: u32) {}
fn registry_stop_listening(_ctx: &mut NativeContext<'_>, _suite_id: i32, _store_name: u32) {}
fn registry_send_event(
    _ctx: &mut NativeContext<'_>,
    _suite_id: i32,
    _store_name: u32,
    _change_type: i32,
    _record_id: i32,
) {
}
fn registry_get_listeners(_ctx: &mut NativeContext<'_>, _suite_id: i32, _store_name: u32) -> u32 {
    NULL
}
fn registry_ack_notifications(_ctx: &mut NativeContext<'_>, _task_id: i32) {}
fn registry_reset_counter(_ctx: &mut NativeContext<'_>, _task_id: i32) {}
fn registry_stop_all_listeners(_ctx: &mut NativeContext<'_>, _task_id: i32) {}

pub fn register(registry: &mut NativeRegistry) {
    registry.register_bindings(&[
        crate::native_bind!(async static "com.sun.midp.rms.RecordStoreImpl.nativeLoad(Ljava/lang/String;)[B" => rms_load(key: u32) -> u32),
        crate::native_bind!(static "com.sun.midp.rms.RecordStoreImpl.nativeSave(Ljava/lang/String;[B)V" => rms_save(key: u32, data: u32) -> ()),
        crate::native_bind!(static "com.sun.midp.rms.RecordStoreImpl.nativeRemove(Ljava/lang/String;)V" => rms_remove(key: u32) -> ()),
        crate::native_bind!(async static "com.sun.midp.rms.RecordStoreImpl.nativeKeys()[Ljava/lang/String;" => rms_keys() -> u32),
        crate::native_bind!(static "com.sun.midp.rms.RecordStoreRegistry.startRecordStoreListening(ILjava/lang/String;)V" => registry_start_listening(suite_id: i32, store_name: u32) -> ()),
        crate::native_bind!(static "com.sun.midp.rms.RecordStoreRegistry.stopRecordStoreListening(ILjava/lang/String;)V" => registry_stop_listening(suite_id: i32, store_name: u32) -> ()),
        crate::native_bind!(static "com.sun.midp.rms.RecordStoreRegistry.sendRecordStoreChangeEvent(ILjava/lang/String;II)V" => registry_send_event(suite_id: i32, store_name: u32, change_type: i32, record_id: i32) -> ()),
        crate::native_bind!(static "com.sun.midp.rms.RecordStoreRegistry.getRecordStoreListeners(ILjava/lang/String;)[I" => registry_get_listeners(suite_id: i32, store_name: u32) -> u32),
        crate::native_bind!(static "com.sun.midp.rms.RecordStoreRegistry.acknowledgeNotificationsDelivery(I)V" => registry_ack_notifications(task_id: i32) -> ()),
        crate::native_bind!(static "com.sun.midp.rms.RecordStoreRegistry.resetNotificationCounter(I)V" => registry_reset_counter(task_id: i32) -> ()),
        crate::native_bind!(static "com.sun.midp.rms.RecordStoreRegistry.stopAllRecordStoreListeners(I)V" => registry_stop_all_listeners(task_id: i32) -> ()),
    ]);
}
