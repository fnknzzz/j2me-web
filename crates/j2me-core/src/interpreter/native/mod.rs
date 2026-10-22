mod bluetooth;
mod cldc;
pub mod display;
mod graphics;
mod graphics2d;
mod image;
mod io;
mod lang;
pub mod m3g;
mod mascot;
mod math;
mod media;
mod midp_system;
mod nio;
mod nokia;
pub mod registry;
mod resource;
mod rms;

use crate::classfile::types::RuntimeMethod;
use crate::scheduler::virtual_thread::YieldReason;
use crate::scheduler::ThreadStack;
use crate::vm::SharedVm;
use registry::NativeRegistry;

/// Returns Some(YieldReason) if the native method needs to yield (e.g. async wait).
pub fn dispatch(
    stack: &mut ThreadStack,
    vm: &mut SharedVm,
    method: &RuntimeMethod,
    thread_id: u32,
) -> Option<YieldReason> {
    let native_id = method.native_method_id;
    if native_id != u32::MAX {
        if let Some(handler) = vm.native_registry.get_handler(native_id) {
            let is_m3g_handler = vm.native_registry.is_m3g_handler(native_id);
            let yield_reason = handler(stack, vm, thread_id);
            if is_m3g_handler {
                m3g::check_pending_error(vm);
            }
            return yield_reason;
        }
    }

    // Unregistered native method — pop args, push default return, warn
    let param_slots = method.param_slot_count();
    for _ in 0..param_slots {
        stack.pop();
    }
    let desc = vm.class_repo.get_string(method.descriptor_id);
    let ret = RuntimeMethod::return_descriptor_from(desc);
    match ret {
        "V" => {}
        "J" | "D" => {
            push_long(stack, 0);
        }
        _ => {
            stack.push(0);
        }
    }
    vm.println(&format!(
        "[WARN] Unimplemented native: {}.{}{}",
        vm.class_repo
            .get_class_name_by_id(method.class_id)
            .unwrap_or(""),
        vm.class_repo.get_string(method.name_id),
        vm.class_repo.get_string(method.descriptor_id)
    ));
    None
}

fn push_long(stack: &mut ThreadStack, val: i64) {
    stack.push(val as u32);
    stack.push((val >> 32) as u32);
}

/// Create and populate the native registry with all registered handlers.
pub fn create_native_registry() -> NativeRegistry {
    let mut registry = NativeRegistry::new();
    registry.register_all();
    registry
}
