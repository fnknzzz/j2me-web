use std::collections::HashMap;

use crate::scheduler::virtual_thread::YieldReason;
use crate::scheduler::ThreadStack;
use crate::vm::SharedVm;

/// Unified native method handler signature.
/// Returns `None` if handled synchronously, `Some(yr)` if the method needs to yield.
pub type NativeHandler =
    fn(stack: &mut ThreadStack, vm: &mut SharedVm, thread_id: u32) -> Option<YieldReason>;

/// JVM invocation form declared by a typed native binding.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum InvocationKind {
    Static,
    Instance,
}

/// A native handler together with its ABI declaration, produced by `native_bind!`.
pub struct NativeBinding {
    pub key: &'static str,
    pub invocation_kind: InvocationKind,
    pub handler: NativeHandler,
}

/// Context provided to typed native handlers.
pub struct NativeContext<'a> {
    pub vm: &'a mut SharedVm,
    pub thread_id: u32,
    pub stack: &'a ThreadStack,
}

/// Result for typed native handlers that may suspend execution.
pub enum NativeResult<T> {
    Ready(T),
    Yield(YieldReason),
}

/// Registry mapping native method keys to handler IDs and function pointers.
pub struct NativeRegistry {
    handlers: Vec<NativeHandler>,
    m3g_handler_ids: Vec<bool>,
    key_to_id: HashMap<String, u32>,
    binding_kinds: HashMap<String, InvocationKind>,
}

impl NativeRegistry {
    pub fn new() -> Self {
        Self {
            handlers: Vec::new(),
            m3g_handler_ids: Vec::new(),
            key_to_id: HashMap::new(),
            binding_kinds: HashMap::new(),
        }
    }

    /// Register typed bindings produced by `native_bind!` and retain their
    /// invocation form for class-loading validation.
    pub fn register_bindings(&mut self, bindings: &[NativeBinding]) {
        for binding in bindings {
            #[cfg(test)]
            assert!(
                !self.key_to_id.contains_key(binding.key),
                "duplicate native binding: {}",
                binding.key
            );

            let id = self.handlers.len() as u32;
            self.handlers.push(binding.handler);
            self.m3g_handler_ids.push(false);
            self.key_to_id.insert(binding.key.to_string(), id);
            self.binding_kinds
                .insert(binding.key.to_string(), binding.invocation_kind);
        }
    }

    pub fn register_m3g(&mut self) {
        let first_m3g_handler = self.handlers.len();
        super::m3g::register(self);
        for is_m3g_handler in &mut self.m3g_handler_ids[first_m3g_handler..] {
            *is_m3g_handler = true;
        }
    }

    /// Look up a native_method_id by key string. Returns u32::MAX if not found.
    #[inline]
    pub fn lookup(&self, key: &str) -> u32 {
        self.key_to_id.get(key).copied().unwrap_or(u32::MAX)
    }

    /// Get the handler for the given native_method_id.
    #[inline]
    pub fn get_handler(&self, id: u32) -> Option<NativeHandler> {
        self.handlers.get(id as usize).copied()
    }

    #[inline]
    pub fn is_m3g_handler(&self, id: u32) -> bool {
        self.m3g_handler_ids
            .get(id as usize)
            .copied()
            .unwrap_or(false)
    }

    /// Get the key_to_id map (for use in class loading where borrow checker prevents &self).
    #[inline]
    pub fn key_to_id_map(&self) -> &HashMap<String, u32> {
        &self.key_to_id
    }

    #[inline]
    pub fn binding_kind_map(&self) -> &HashMap<String, InvocationKind> {
        &self.binding_kinds
    }

    /// Register all native methods from all modules.
    pub fn register_all(&mut self) {
        super::bluetooth::register(self);
        super::lang::register(self);
        super::io::register(self);
        super::cldc::register(self);
        super::math::register(self);
        super::rms::register(self);
        super::display::register(self);
        super::graphics::register(self);
        super::midp_system::register(self);
        super::image::register(self);
        super::resource::register(self);
        super::nio::register(self);
        super::media::register(self);
        super::nokia::register(self);
        super::mascot::register(self);
        self.register_m3g();
    }
}

impl Default for NativeRegistry {
    fn default() -> Self {
        Self::new()
    }
}
