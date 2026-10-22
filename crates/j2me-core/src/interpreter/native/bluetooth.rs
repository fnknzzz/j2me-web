use crate::scheduler::virtual_thread::YieldReason;
use crate::vm::{AsyncRequestType, AsyncResult};

use super::registry::{NativeContext, NativeRegistry, NativeResult};

fn request(ctx: &mut NativeContext<'_>, operation_ref: u32, payload_ref: u32) -> NativeResult<u32> {
    if let Some((_id, _request, AsyncResult::BluetoothCompleted { response })) = ctx
        .vm
        .take_resolved_async_for_thread(ctx.thread_id, |result| {
            matches!(result, AsyncResult::BluetoothCompleted { .. })
        })
    {
        return NativeResult::Ready(ctx.vm.create_string(&response));
    }

    let operation = ctx.vm.get_string_value(operation_ref).unwrap_or_default();
    let payload = ctx.vm.get_string_value(payload_ref).unwrap_or_default();
    let request_id = ctx.vm.issue_async_request(
        ctx.thread_id,
        AsyncRequestType::Bluetooth { operation, payload },
    );
    NativeResult::Yield(YieldReason::AsyncWait { request_id })
}

pub fn register(registry: &mut NativeRegistry) {
    registry.register_bindings(&[
        crate::native_bind!(async static "com.sun.jsjvm.bluetooth.BluetoothBridge.request(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;" => request(operation: u32, payload: u32) -> u32),
    ]);
}
