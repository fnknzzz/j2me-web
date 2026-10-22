use crate::cheat_engine;
use crate::heap;
use crate::heap::gc;
use crate::interpreter;
use crate::scheduler;
use crate::scheduler::virtual_thread::ThreadId;
use crate::scheduler::Scheduler;
use crate::vm;
use crate::vm::SharedVm;
use crate::{call_host_current_time_millis, call_host_get_media_time};
use serde::{Deserialize, Serialize};
use std::cell::RefCell;
use std::ptr::NonNull;
use std::rc::Rc;

/// Result of running JVM threads to completion.
#[derive(Debug)]
pub(crate) enum RunResult {
    /// Thread(s) completed normally.
    Done,
    /// Execution is suspended waiting for a host async operation (e.g. image decode, RMS load).
    AsyncPending,
    /// The step limit was reached before the thread finished.
    StepLimitReached,
}

impl RunResult {
    fn into_result(self) -> Result<(), String> {
        match self {
            RunResult::Done => Ok(()),
            RunResult::AsyncPending => Err("ASYNC_PENDING".to_string()),
            RunResult::StepLimitReached => Err("STEP_LIMIT".to_string()),
        }
    }
}

#[derive(Debug, Clone)]
struct MidletDescriptor {
    index: u32,
    name: String,
    icon: String,
    class_name: String,
}

fn parse_manifest_attributes(manifest: &str) -> std::collections::HashMap<String, String> {
    let mut unfolded: Vec<String> = Vec::new();
    for raw_line in manifest.replace("\r\n", "\n").split('\n') {
        if raw_line.is_empty() {
            continue;
        }
        if let Some(rest) = raw_line.strip_prefix(' ') {
            if let Some(last) = unfolded.last_mut() {
                last.push_str(rest);
            }
        } else {
            unfolded.push(raw_line.to_string());
        }
    }

    let mut attrs = std::collections::HashMap::new();
    for line in unfolded {
        let Some((key, value)) = line.split_once(':') else {
            continue;
        };
        attrs.insert(key.trim().to_string(), value.trim().to_string());
    }
    attrs
}

fn midlet_descriptors_from_attributes(
    attrs: &std::collections::HashMap<String, String>,
) -> Vec<MidletDescriptor> {
    let mut entries = Vec::new();
    for (key, value) in attrs {
        let Some(index_text) = key.strip_prefix("MIDlet-") else {
            continue;
        };
        let Ok(index) = index_text.parse::<u32>() else {
            continue;
        };
        let parts: Vec<&str> = value.split(',').map(str::trim).collect();
        if parts.len() < 3 || parts[2].is_empty() {
            continue;
        }
        entries.push(MidletDescriptor {
            index,
            name: parts[0].to_string(),
            icon: parts[1].to_string(),
            class_name: parts[2].to_string(),
        });
    }
    entries.sort_by_key(|entry| entry.index);
    entries
}

#[cfg(not(target_arch = "wasm32"))]
#[cfg(debug_assertions)]
const SYSTEM_CLASSES_JAR: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/j2me-rt/classes.debug.jar"
));

#[cfg(not(target_arch = "wasm32"))]
#[cfg(not(debug_assertions))]
const SYSTEM_CLASSES_JAR: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/j2me-rt/classes.prod.jar"
));

#[cfg(target_arch = "wasm32")]
const SYSTEM_CLASSES_BOOT_IMAGE: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/classes.boot"));

#[derive(Clone, Serialize, Deserialize)]
struct MidletContext {
    class_name: String,
    midlet_ref: u32,
}

/// Wrapper for full instance state serialization (VM snapshot + instance-level fields).
#[derive(Serialize, Deserialize)]
struct CoreSaveState {
    snapshot: vm::VmSnapshot,
    main_thread_id: Option<ThreadId>,
    next_context_id: u32,
    midlet_contexts: std::collections::HashMap<u32, MidletContext>,
}

#[derive(Clone, Copy, Serialize, Deserialize)]
enum SavedActiveCore {
    Selector,
    App,
}

#[derive(Serialize, Deserialize)]
struct InstanceSaveState {
    active: SavedActiveCore,
    next_surface_id: i32,
    selector: Option<CoreSaveState>,
    app: Option<CoreSaveState>,
}

/// Container header for save state files.
/// Layout:
///   fixed header: [magic:4 = "clsv"][version:u32][timestamp_ms:u64][section_count:u32]
///   section entry: [type:u32][offset:u32][length:u32][param0:u32][param1:u32]
/// Section types:
///   1 = compressed VM state (param0 = uncompressed length)
///   2 = display surface 0 PNG (param0 = pixel width, param1 = pixel height)
const SAVE_MAGIC: &[u8; 4] = b"clsv";
// Version 2 stores the page-based heap layout with side metadata and a compact
// four-byte ordinary-object header. Version 1 heap addresses/layout are not
// binary-compatible.
const SAVE_VERSION: u32 = 2;
const SAVE_FIXED_HEADER_SIZE: usize = 20;
const SAVE_SECTION_ENTRY_SIZE: usize = 20;
const SAVE_META_SIZE: usize = SAVE_FIXED_HEADER_SIZE + SAVE_SECTION_ENTRY_SIZE * 2;
const SAVE_SECTION_VM_STATE: u32 = 1;
const SAVE_SECTION_DISPLAY_SURFACE_PNG: u32 = 2;

#[derive(Debug, Clone, Copy, Default)]
pub struct SaveMeta {
    pub version: u32,
    pub timestamp: u64,
    pub vm_state_offset: u32,
    pub vm_state_len: u32,
    pub vm_state_uncompressed_len: u32,
    pub display_png_offset: u32,
    pub display_png_len: u32,
    pub display_png_width: u32,
    pub display_png_height: u32,
}

#[repr(C)]
pub struct JvmSaveMeta {
    pub timestamp: u64,
    pub version: u32,
    pub vm_state_offset: u32,
    pub vm_state_len: u32,
    pub display_png_offset: u32,
    pub display_png_len: u32,
    pub display_png_width: u32,
    pub display_png_height: u32,
}

impl From<SaveMeta> for JvmSaveMeta {
    fn from(meta: SaveMeta) -> Self {
        Self {
            timestamp: meta.timestamp,
            version: meta.version,
            vm_state_offset: meta.vm_state_offset,
            vm_state_len: meta.vm_state_len,
            display_png_offset: meta.display_png_offset,
            display_png_len: meta.display_png_len,
            display_png_width: meta.display_png_width,
            display_png_height: meta.display_png_height,
        }
    }
}

#[derive(Debug, Clone, Copy)]
struct SaveSectionEntry {
    section_type: u32,
    offset: u32,
    length: u32,
    param0: u32,
    param1: u32,
}

/// Current save state compatibility version for Rust callers.
/// Keep this separate from the exported C symbol to avoid same-symbol recursion
/// in platform wrapper crates that re-export `jvm_save_state_version`.
pub fn save_state_version() -> u32 {
    SAVE_VERSION
}

/// Number of leading bytes needed to parse save state metadata.
pub fn save_meta_size() -> u32 {
    SAVE_META_SIZE as u32
}

pub struct JvmCore {
    gc_enabled: bool,
    vm: SharedVm,
    scheduler: Scheduler,
    main_thread_id: Option<ThreadId>,
    next_context_id: u32,
    midlet_contexts: std::collections::HashMap<u32, MidletContext>,
    async_thread_id: Option<ThreadId>,
    pending_start_after_init_context_id: Option<u32>,
    pub cheat_engine: cheat_engine::CheatEngine,
    pending_image_surface_snapshots: Vec<vm::ImageSurfaceSnapshot>,
}

impl JvmCore {
    pub fn new(_name: Option<&str>, debug: bool, surface_id_allocator: Rc<RefCell<i32>>) -> Self {
        let mut vm = SharedVm::new(surface_id_allocator, debug);
        #[cfg(not(target_arch = "wasm32"))]
        vm.load_jar(SYSTEM_CLASSES_JAR)
            .expect("failed to load system classes");
        #[cfg(target_arch = "wasm32")]
        assert!(
            vm.install_compact_boot_image(SYSTEM_CLASSES_BOOT_IMAGE),
            "system class boot image is invalid or incompatible"
        );
        vm.bootstrap_system_io();
        // Pre-allocate an OOM exception object so we can throw it without allocating when heap is full
        vm.preallocated_oom = vm.new_object("java.lang.OutOfMemoryError");
        Self {
            gc_enabled: true,
            vm,
            scheduler: Scheduler::new(),
            main_thread_id: None,
            next_context_id: 1,
            midlet_contexts: std::collections::HashMap::new(),
            async_thread_id: None,
            pending_start_after_init_context_id: None,
            cheat_engine: cheat_engine::CheatEngine::new(),
            pending_image_surface_snapshots: Vec::new(),
        }
    }

    pub fn repaint(&mut self) -> Result<(), String> {
        self.call_static("javax.microedition.lcdui.Display", "forceRepaint", "()V")
    }

    pub fn sync_host_ui_state_after_restore(&mut self) -> Result<(), String> {
        self.call_static(
            "javax.microedition.lcdui.Display",
            "syncHostStateAfterRestore",
            "()V",
        )
    }

    pub fn set_gc_enabled(&mut self, enabled: bool) {
        self.gc_enabled = enabled;
    }

    pub fn load_class(&mut self, data: &[u8]) -> Result<String, String> {
        self.vm.load_class(data)
    }

    pub fn load_jar(&mut self, data: &[u8]) -> Result<Vec<String>, String> {
        self.vm.load_jar(data)
    }

    fn populate_suite_launcher_static_fields(
        &mut self,
        entries: &[MidletDescriptor],
    ) -> Result<(), String> {
        const LAUNCHER_CLASS: &str = "com.jsjvm.midp.MidletSuiteLauncher";
        self.ensure_class_fully_initialized(LAUNCHER_CLASS)?;

        let int_array_class_id = self.vm.class_repo.get_or_register_array_class("[I");
        let string_array_class_id = self
            .vm
            .class_repo
            .get_or_register_array_class("[Ljava.lang.String;");
        let count = entries.len() as u32;
        let indices = self.vm.new_array(int_array_class_id, count, 4);
        let names = self.vm.new_array(string_array_class_id, count, 4);
        let class_names = self.vm.new_array(string_array_class_id, count, 4);
        let icons = self.vm.new_array(string_array_class_id, count, 4);

        for (i, entry) in entries.iter().enumerate() {
            self.vm
                .heap
                .array_write_i32(indices, i as u32, entry.index as i32);
            let name = self.vm.create_string(&entry.name);
            self.vm.heap.array_write_u32(names, i as u32, name);
            let class_name = self.vm.create_string(&entry.class_name);
            self.vm
                .heap
                .array_write_u32(class_names, i as u32, class_name);
            let icon = self.vm.create_string(&entry.icon);
            self.vm.heap.array_write_u32(icons, i as u32, icon);
        }

        self.write_static_ref("entryIndices", "[I", indices)?;
        self.write_static_ref("entryNames", "[Ljava/lang/String;", names)?;
        self.write_static_ref("entryClassNames", "[Ljava/lang/String;", class_names)?;
        self.write_static_ref("entryIcons", "[Ljava/lang/String;", icons)?;
        self.write_static_i32("pendingLaunchIndex", "I", 0)?;
        Ok(())
    }

    fn suite_launcher_static_field_addr(&self, field_name: &str, descriptor: &str) -> Option<u32> {
        const LAUNCHER_CLASS: &str = "com.jsjvm.midp.MidletSuiteLauncher";
        let field = self
            .vm
            .resolve_field(LAUNCHER_CLASS, field_name, descriptor)?;
        if !field.is_static {
            return None;
        }
        let base = self.vm.static_fields.get_addr(&field.class_name);
        if base == heap::NULL {
            return None;
        }
        Some(base + field.byte_offset)
    }

    fn write_static_ref(
        &mut self,
        field_name: &str,
        descriptor: &str,
        value: u32,
    ) -> Result<(), String> {
        let Some(addr) = self.suite_launcher_static_field_addr(field_name, descriptor) else {
            return Err(format!(
                "com.jsjvm.midp.MidletSuiteLauncher.{}{} not found",
                field_name, descriptor
            ));
        };
        self.vm.heap.write_u32(addr, value);
        Ok(())
    }

    fn write_static_i32(
        &mut self,
        field_name: &str,
        descriptor: &str,
        value: i32,
    ) -> Result<(), String> {
        let Some(addr) = self.suite_launcher_static_field_addr(field_name, descriptor) else {
            return Err(format!(
                "com.jsjvm.midp.MidletSuiteLauncher.{}{} not found",
                field_name, descriptor
            ));
        };
        self.vm.heap.write_i32(addr, value);
        Ok(())
    }

    fn take_pending_suite_launch_index(&mut self) -> Option<u32> {
        let addr = self.suite_launcher_static_field_addr("pendingLaunchIndex", "I")?;
        let value = self.vm.heap.read_i32(addr);
        if value <= 0 {
            return None;
        }
        self.vm.heap.write_i32(addr, 0);
        Some(value as u32)
    }

    pub fn load_resource(&mut self, name: &str, data: &[u8]) {
        self.vm.load_resource(name.to_string(), data.to_vec());
    }

    pub fn set_system_property(&mut self, key: &str, value: &str) {
        self.vm
            .system_properties
            .insert(key.to_string(), value.to_string());
    }

    pub fn start_main(&mut self, class_name: &str, args: &[&str]) -> Result<(), String> {
        let cn = class_name.replace('/', ".");
        let method_id = self
            .vm
            .class_repo
            .find_method(&cn, "main", "([Ljava/lang/String;)V")
            .or_else(|| self.vm.class_repo.find_method(&cn, "startApp", "()V"))
            .ok_or_else(|| format!("Entry method not found in class {}", cn))?;
        let is_main = self
            .vm
            .class_repo
            .get_method(method_id)
            .is_some_and(|method| self.vm.class_repo.get_string(method.name_id) == "main");

        let tid = if is_main {
            let ref_array_class_id = self.vm.class_repo.get_class_id("[L").unwrap_or(0);
            let args_array = self.vm.new_array(ref_array_class_id, args.len() as u32, 4);
            for (i, &arg) in args.iter().enumerate() {
                let str_handle = self.vm.create_string(arg);
                self.vm
                    .heap
                    .array_write_u32(args_array, i as u32, str_handle);
            }
            self.spawn_method_thread("main", false, method_id, 1, |stack| {
                stack.push(args_array);
            })
        } else {
            self.spawn_method_thread("main", false, method_id, 0, |_| {})
        };

        self.main_thread_id = Some(tid);
        Ok(())
    }

    pub fn create_midlet_context(&mut self, class_name: &str) -> Result<u32, String> {
        let cn = class_name.replace('/', ".");

        self.bootstrap_midlet_env(&cn)?;

        // Ensure the MIDlet class (and its parent chain) is fully initialized.
        // This mirrors what the NEW bytecode does in the interpreter.
        self.ensure_class_fully_initialized(&cn)?;

        let init_method_id = self
            .vm
            .class_repo
            .find_method_in_hierarchy(&cn, "<init>", "()V")
            .ok_or_else(|| format!("<init> not found for {}", cn))?;

        let midlet_ref = self.vm.new_object(&cn);

        let tid = self.spawn_method_thread("midlet-init", false, init_method_id, 1, |stack| {
            stack.push(midlet_ref);
        });
        let result = self.run_thread_to_completion(tid);

        // Clean up run_queue: run_thread_to_completion may leave terminated tid entries
        self.scheduler.run_queue.retain(|&tid_in_q| {
            self.scheduler.threads.get(&tid_in_q).map_or(false, |t| {
                t.state == scheduler::virtual_thread::JavaThreadState::Runnable
            })
        });

        let id = self.next_context_id;
        self.next_context_id += 1;
        self.midlet_contexts.insert(
            id,
            MidletContext {
                class_name: cn,
                midlet_ref,
            },
        );

        match result {
            RunResult::AsyncPending => {
                self.async_thread_id = Some(tid);
                Ok(id)
            }
            RunResult::Done | RunResult::StepLimitReached => Ok(id),
        }
    }

    pub fn start_midlet_class(&mut self, class_name: &str) -> Result<(), String> {
        let context_id = self.create_midlet_context(class_name)?;
        if self.async_thread_id.is_some() {
            self.pending_start_after_init_context_id = Some(context_id);
            Err("ASYNC_PENDING".to_string())
        } else {
            self.start_midlet(context_id)
        }
    }

    fn bootstrap_midlet_env(&mut self, _midlet_class: &str) -> Result<(), String> {
        self.call_static("com.jsjvm.midp.MidpBootstrap", "init", "()V")?;

        let handler_class = "com.sun.midp.midlet.MIDletStateHandler";
        self.vm.ensure_initialized(handler_class);

        let lock_obj = self.vm.new_object("java.lang.Object");
        self.call_init("java.lang.Object", lock_obj)?;
        if let Some(rf) =
            self.vm
                .resolve_field(handler_class, "createMIDletLock", "Ljava/lang/Object;")
        {
            let base = self.vm.static_fields.get_addr(&rf.class_name);
            self.vm.heap.write_u32(base + rf.byte_offset, lock_obj);
        }

        let peer = self.vm.new_object("com.sun.midp.midlet.MIDletPeer");
        if let Some(rf) = self.vm.resolve_field(
            handler_class,
            "newMidletPeer",
            "Lcom/sun/midp/midlet/MIDletPeer;",
        ) {
            let base = self.vm.static_fields.get_addr(&rf.class_name);
            self.vm.heap.write_u32(base + rf.byte_offset, peer);
        }

        // Set up a MIDletSuiteImpl so getMIDletSuite().getID() works
        let handler_ref = {
            if let Some(rf) = self.vm.resolve_field(
                handler_class,
                "stateHandler",
                "Lcom/sun/midp/midlet/MIDletStateHandler;",
            ) {
                let base = self.vm.static_fields.get_addr(&rf.class_name);
                let h = self.vm.heap.read_u32(base + rf.byte_offset);
                if h == 0 {
                    // Create handler instance if not yet created
                    let obj = self.vm.new_object(handler_class);
                    self.vm.heap.write_u32(base + rf.byte_offset, obj);
                    obj
                } else {
                    h
                }
            } else {
                0
            }
        };
        if handler_ref != 0 {
            let suite_class = "com.sun.midp.midletsuite.MIDletSuiteImpl";
            self.vm.ensure_initialized(suite_class);
            let suite = self.vm.new_object(suite_class);
            // Set id = 1
            if let Some(rf) = self.vm.resolve_field(suite_class, "id", "I") {
                self.vm.heap.write_u32(suite + rf.byte_offset, 1);
            }
            // Set locked = true so guaranteeMIDletSuiteLocked won't throw
            if let Some(rf) = self.vm.resolve_field(suite_class, "locked", "Z") {
                self.vm.heap.write_u32(suite + rf.byte_offset, 1);
            }
            // Create SuiteProperties so getAppProperty() works
            let sp_class = "com.sun.midp.midletsuite.SuiteProperties";
            self.vm.ensure_initialized(sp_class);
            let sp = self.vm.new_object(sp_class);
            if let Some(rf) = self.vm.resolve_field(sp_class, "suiteId", "I") {
                self.vm.heap.write_u32(sp + rf.byte_offset, 1);
            }
            if let Some(rf) = self.vm.resolve_field(
                suite_class,
                "suiteProperties",
                "Lcom/sun/midp/midletsuite/SuiteProperties;",
            ) {
                self.vm.heap.write_u32(suite + rf.byte_offset, sp);
            }
            // Write suite into handler.midletSuite
            if let Some(rf) = self.vm.resolve_field(
                handler_class,
                "midletSuite",
                "Lcom/sun/midp/midlet/MIDletSuite;",
            ) {
                self.vm.heap.write_u32(handler_ref + rf.byte_offset, suite);
            }

            // Ensure MIDletPeer.midletStateHandler points to the same handler instance
            // so that MIDlet.getAppProperty() -> peer.getMIDletSuite() works correctly
            let peer_class = "com.sun.midp.midlet.MIDletPeer";
            self.vm.ensure_initialized(peer_class);
            if let Some(rf) = self.vm.resolve_field(
                peer_class,
                "midletStateHandler",
                "Lcom/sun/midp/midlet/MIDletStateHandler;",
            ) {
                let base = self.vm.static_fields.get_addr(&rf.class_name);
                self.vm.heap.write_u32(base + rf.byte_offset, handler_ref);
            }
        }

        self.vm.mark_initialized(handler_class);

        Ok(())
    }

    fn call_init(&mut self, class_name: &str, obj_ref: u32) -> Result<(), String> {
        let method_id = match self
            .vm
            .class_repo
            .find_method_in_hierarchy(class_name, "<init>", "()V")
        {
            Some(method_id) => method_id,
            None => return Ok(()),
        };
        let tid = self.spawn_method_thread("init", false, method_id, 1, |stack| {
            stack.push(obj_ref);
        });
        self.run_thread_to_completion(tid).into_result()
    }

    fn call_static(
        &mut self,
        class_name: &str,
        method_name: &str,
        desc: &str,
    ) -> Result<(), String> {
        let method_id = self.resolve_static_call(class_name, method_name, desc)?;
        let tid = self.spawn_method_thread("call-static", false, method_id, 0, |_| {});
        self.run_thread_to_completion(tid).into_result()
    }

    pub(crate) fn resolve_static_call(
        &mut self,
        class_name: &str,
        method_name: &str,
        desc: &str,
    ) -> Result<u32, String> {
        self.vm.ensure_initialized(class_name);
        self.vm
            .class_repo
            .find_method(class_name, method_name, desc)
            .or_else(|| {
                self.vm
                    .class_repo
                    .find_method_in_hierarchy(class_name, method_name, desc)
            })
            .ok_or_else(|| format!("{}.{}{} not found", class_name, method_name, desc))
    }

    pub(crate) fn spawn_method_thread(
        &mut self,
        thread_name: &str,
        daemon: bool,
        method_id: u32,
        param_slots: usize,
        push_args: impl FnOnce(&mut crate::scheduler::ThreadStack),
    ) -> ThreadId {
        let method = self
            .vm
            .class_repo
            .get_method(method_id)
            .expect("method ID must resolve before spawning a thread");
        let max_locals = method.max_locals() as usize;
        let max_stack = method.max_stack() as usize;
        let tid = self.scheduler.create_thread(thread_name.into(), 5, daemon);
        {
            let thread = self.scheduler.threads.get_mut(&tid).unwrap();
            push_args(&mut thread.stack);
            thread
                .stack
                .push_frame_for_invoke(max_locals, max_stack, param_slots, method_id, 0);
            thread.pc = 0;
            thread.current_method = method_id;
        }
        tid
    }

    /// Fully initialize a class and its parent chain by running all pending <clinit> methods.
    fn ensure_class_fully_initialized(&mut self, class_name: &str) -> Result<(), String> {
        loop {
            let clinit = self.vm.ensure_initialized(class_name);
            match clinit {
                None => break,
                Some(method_id) => {
                    let tid = self.spawn_method_thread("clinit", false, method_id, 0, |_| {});
                    self.run_thread_to_completion_inner(tid, true)
                        .into_result()?;
                }
            }
        }
        Ok(())
    }

    pub fn start_midlet(&mut self, context_id: u32) -> Result<(), String> {
        let ctx = self
            .midlet_contexts
            .get(&context_id)
            .ok_or_else(|| format!("Unknown MIDlet context {}", context_id))?;

        let method_id = self
            .vm
            .class_repo
            .find_method_in_hierarchy(&ctx.class_name, "startApp", "()V")
            .ok_or_else(|| format!("startApp not found for {}", ctx.class_name))?;
        let midlet_ref = ctx.midlet_ref;

        let tid = self.spawn_method_thread("midlet-start", false, method_id, 1, |stack| {
            stack.push(midlet_ref);
        });
        // Run startApp with a small step limit — just enough for basic
        // initialization. The run loop will handle the rest via process_blocked_threads
        // deadlock detection.
        let result = self.run_thread_to_completion_with_limit(tid, 50_000);
        match &result {
            RunResult::AsyncPending => {
                // Store the thread id so we can resume it later
                self.async_thread_id = Some(tid);
                return Err("ASYNC_PENDING".to_string());
            }
            RunResult::StepLimitReached => {
                // Step limit reached during startApp — this is not fatal.
                // startApp may contain a game main loop (while(true){...sleep...}).
                // Hand off to the JS run loop's step() which has correct
                // process_blocked_threads logic to resolve monitor contention.

                // Reset time_offset to 0 — the fast-forwarded virtual time during
                // run_thread_to_completion is bogus for the run loop which uses real time.
                // Also clear the sleep_queue and re-enqueue sleeping threads as Runnable.
                let sleeping_tids: Vec<_> = self
                    .scheduler
                    .threads
                    .iter()
                    .filter(|(_, t)| {
                        t.state == scheduler::virtual_thread::JavaThreadState::TimedWaiting
                    })
                    .map(|(&id, _)| id)
                    .collect();
                for t_id in &sleeping_tids {
                    if let Some(t) = self.scheduler.threads.get_mut(t_id) {
                        t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                    }
                }
                self.scheduler.sleep_queue.clear();
                self.scheduler.time_offset_ms = 0;
                self.vm.time_offset_ms = 0;

                // Enqueue all Runnable threads into run_queue so step() can drive them.
                // Do NOT force-unblock Blocked/Waiting threads here — that corrupts
                // monitor state (recursion underflow, dual ownership). Let step()'s
                // process_blocked_threads() handle contention correctly.
                self.scheduler.run_queue.retain(|&tid_in_q| {
                    self.scheduler.threads.get(&tid_in_q).map_or(false, |t| {
                        t.state != scheduler::virtual_thread::JavaThreadState::Terminated
                    })
                });
                let runnable_tids: Vec<_> = self
                    .scheduler
                    .threads
                    .iter()
                    .filter(|(_, t)| {
                        t.state == scheduler::virtual_thread::JavaThreadState::Runnable
                    })
                    .map(|(&id, _)| id)
                    .collect();
                for t_id in runnable_tids {
                    if !self.scheduler.run_queue.contains(&t_id) {
                        self.scheduler.run_queue.push_back(t_id);
                    }
                }
                self.vm.ensure_display_surface();
                return Ok(());
            }
            _ => {}
        }
        if matches!(result, RunResult::Done) {
            self.vm.ensure_display_surface();
            // Clean up run_queue: remove terminated/non-existent threads
            self.scheduler.run_queue.retain(|&tid_in_q| {
                self.scheduler.threads.get(&tid_in_q).map_or(false, |t| {
                    t.state == scheduler::virtual_thread::JavaThreadState::Runnable
                })
            });
            // Ensure all threads spawned during startApp are available to the run loop.
            let runnable_tids: Vec<_> = self
                .scheduler
                .threads
                .iter()
                .filter(|(_, t)| t.state == scheduler::virtual_thread::JavaThreadState::Runnable)
                .map(|(&id, _)| id)
                .collect();
            for t_id in runnable_tids {
                if !self.scheduler.run_queue.contains(&t_id) {
                    self.scheduler.run_queue.push_back(t_id);
                }
            }
            if !self.has_runnable_app_threads() {
                self.process_events()?;
            }
            // Let the real scheduler drain the first batch of startup events so
            // simple MIDlets can produce their initial screen before start_midlet()
            // returns, without introducing an extra synthetic event thread.
            for _ in 0..128 {
                if !self.vm.command_buffer.is_empty() || self.scheduler.run_queue.is_empty() {
                    break;
                }
                let status = self.step(0);
                if status != 1 {
                    break;
                }
            }
        }
        result.into_result()
    }

    fn has_runnable_app_threads(&self) -> bool {
        let Some(target_off) = self
            .vm
            .class_repo
            .find_field_offset("java.lang.Thread", "target")
        else {
            return true;
        };

        self.scheduler.threads.iter().any(|(&tid, thread)| {
            if thread.state != scheduler::virtual_thread::JavaThreadState::Runnable {
                return false;
            }

            let Some(&thread_obj_ref) = self.vm.thread_id_to_object.get(&tid) else {
                return true;
            };
            let target_ref = self.vm.heap.read_u32(thread_obj_ref + target_off);
            if target_ref == 0 {
                return true;
            }

            !matches!(
                self.vm.get_class_name_of_object(target_ref),
                Some("com.sun.midp.events.EventQueue" | "com.sun.midp.events.NativeEventMonitor")
            )
        })
    }

    pub fn process_events(&mut self) -> Result<(), String> {
        let eq_class = "com.sun.midp.events.EventQueue";
        let field =
            match self
                .vm
                .resolve_field(eq_class, "eventQueue", "Lcom/sun/midp/events/EventQueue;")
            {
                Some(f) => f,
                None => return Ok(()),
            };
        let base = self.vm.static_fields.get_addr(&field.class_name);
        let eq_ref = self.vm.heap.read_u32(base + field.byte_offset);
        if eq_ref == 0 {
            return Ok(());
        }

        let method_id = match self.vm.class_repo.find_method_in_hierarchy(
            eq_class,
            "processAllPendingEvents",
            "()V",
        ) {
            Some(method_id) => method_id,
            None => return Ok(()),
        };

        let tid = self.spawn_method_thread("process-events", false, method_id, 1, |stack| {
            stack.push(eq_ref);
        });
        self.run_thread_to_completion(tid).into_result()
    }

    pub fn pause_midlet(&mut self, context_id: u32) -> Result<(), String> {
        let ctx = self
            .midlet_contexts
            .get(&context_id)
            .ok_or_else(|| format!("Unknown MIDlet context {}", context_id))?;
        let method_id = self
            .vm
            .class_repo
            .find_method_in_hierarchy(&ctx.class_name, "pauseApp", "()V")
            .ok_or_else(|| format!("pauseApp not found for {}", ctx.class_name))?;
        let midlet_ref = ctx.midlet_ref;

        let tid = self.spawn_method_thread("midlet-pause", false, method_id, 1, |stack| {
            stack.push(midlet_ref);
        });
        self.run_thread_to_completion(tid).into_result()
    }

    pub fn destroy_midlet(&mut self, context_id: u32, unconditional: bool) -> Result<(), String> {
        let ctx = self
            .midlet_contexts
            .remove(&context_id)
            .ok_or_else(|| format!("Unknown MIDlet context {}", context_id))?;
        let method_id = self
            .vm
            .class_repo
            .find_method_in_hierarchy(&ctx.class_name, "destroyApp", "(Z)V")
            .ok_or_else(|| format!("destroyApp not found for {}", ctx.class_name))?;

        let tid = self.spawn_method_thread("midlet-destroy", false, method_id, 2, |stack| {
            stack.push(ctx.midlet_ref);
            stack.push(if unconditional { 1 } else { 0 });
        });
        let result = self.run_thread_to_completion(tid);
        match &result {
            RunResult::AsyncPending => {
                self.async_thread_id = Some(tid);
                return Err("ASYNC_PENDING".to_string());
            }
            _ => {}
        }
        result.into_result()
    }

    /// Spawn any pending Java Thread objects as scheduler threads.
    fn process_pending_thread_spawns(&mut self) {
        let spawns = std::mem::take(&mut self.vm.pending_thread_spawns);
        for thread_obj_ref in spawns {
            let class_name = self
                .vm
                .get_class_name_of_object(thread_obj_ref)
                .unwrap_or("java.lang.Thread")
                .to_string();
            let run_method = self
                .vm
                .class_repo
                .find_method_in_hierarchy(&class_name, "run", "()V");
            if let Some(method_id) = run_method {
                // Read daemon flag from Java Thread object
                let is_daemon = if let Some(off) = self
                    .vm
                    .class_repo
                    .find_field_offset("java.lang.Thread", "daemon")
                {
                    self.vm.heap.read_u32(thread_obj_ref + off) != 0
                } else {
                    false
                };
                let new_tid = self.spawn_method_thread(
                    &format!("java-thread-{}", thread_obj_ref),
                    is_daemon,
                    method_id,
                    1,
                    |stack| {
                        stack.push(thread_obj_ref);
                    },
                );
                self.vm.thread_object_map.insert(thread_obj_ref, new_tid);
                self.vm.thread_id_to_object.insert(new_tid, thread_obj_ref);
                let thread = self.scheduler.threads.get_mut(&new_tid).unwrap();
                thread.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                self.scheduler.run_queue.push_back(new_tid);
            }
        }
    }

    fn run_thread_to_completion(&mut self, tid: ThreadId) -> RunResult {
        self.run_thread_to_completion_inner(tid, false)
    }

    fn run_thread_to_completion_with_limit(&mut self, tid: ThreadId, max_steps: u32) -> RunResult {
        self.run_thread_to_completion_inner_with_limit(tid, false, max_steps)
    }

    fn run_thread_to_completion_inner(
        &mut self,
        tid: ThreadId,
        auto_resolve_async: bool,
    ) -> RunResult {
        self.run_thread_to_completion_inner_with_limit(tid, auto_resolve_async, 2_000_000)
    }

    fn run_thread_to_completion_inner_with_limit(
        &mut self,
        tid: ThreadId,
        auto_resolve_async: bool,
        max_steps: u32,
    ) -> RunResult {
        let mut active_tids: Vec<ThreadId> = vec![tid];
        let mut step_count: u32 = 0;
        let mut idle_iterations: u32 = 0;
        let max_idle: u32 = 1_000;

        loop {
            if step_count >= max_steps {
                return RunResult::StepLimitReached;
            }

            self.process_pending_thread_spawns();

            // Process pending notify wakeups (from Object.notify/notifyAll during execution)
            let wakeups = std::mem::take(&mut self.vm.pending_notify_wakeups);
            for (obj_id, notified_tid, saved_recursion) in wakeups {
                for ws in self.scheduler.wait_sets.values_mut() {
                    ws.retain(|&t| t != notified_tid);
                }
                self.scheduler.cancel_sleep_deadline(notified_tid);
                let already_owner = self
                    .vm
                    .monitors
                    .get(&obj_id)
                    .map(|m| m.owner == Some(notified_tid))
                    .unwrap_or(false);
                if already_owner {
                    if let Some(monitor) = self.vm.monitors.get_mut(&obj_id) {
                        monitor.recursion = saved_recursion;
                    }
                    if let Some(t) = self.scheduler.threads.get_mut(&notified_tid) {
                        t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                        t.resume_action = None;
                    }
                } else {
                    if let Some(t) = self.scheduler.threads.get_mut(&notified_tid) {
                        t.state = scheduler::virtual_thread::JavaThreadState::Blocked;
                        t.resume_action =
                            Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                                obj_id,
                                saved_recursion,
                            });
                    }
                    let monitor = self.vm.monitors.entry(obj_id).or_default();
                    if !monitor.blocked_queue.contains(&notified_tid) {
                        monitor.blocked_queue.push_back(notified_tid);
                    }
                }
            }

            for (&new_tid, t) in &self.scheduler.threads {
                if !active_tids.contains(&new_tid)
                    && (t.state == scheduler::virtual_thread::JavaThreadState::Runnable
                        || t.state == scheduler::virtual_thread::JavaThreadState::Blocked)
                {
                    active_tids.push(new_tid);
                }
            }

            // Process blocked threads (monitor re-entry) — shared logic
            self.process_blocked_threads_for_completion(&active_tids);

            let mut stepped_any = false;
            for i in 0..active_tids.len() {
                let current_tid = active_tids[i];

                let thread_state = match self.scheduler.threads.get(&current_tid) {
                    Some(t) => t.state,
                    None => continue,
                };
                if thread_state == scheduler::virtual_thread::JavaThreadState::Terminated {
                    continue;
                }
                if thread_state == scheduler::virtual_thread::JavaThreadState::Blocked
                    || thread_state == scheduler::virtual_thread::JavaThreadState::TimedWaiting
                    || thread_state == scheduler::virtual_thread::JavaThreadState::Waiting
                {
                    // Check if async result is ready for waiting threads
                    if thread_state == scheduler::virtual_thread::JavaThreadState::Waiting
                        || thread_state == scheduler::virtual_thread::JavaThreadState::TimedWaiting
                    {
                        let has_async = self.vm.has_resolved_async_for_thread(current_tid);
                        if has_async {
                            self.scheduler.threads.get_mut(&current_tid).unwrap().state =
                                scheduler::virtual_thread::JavaThreadState::Runnable;
                        } else {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }

                let thread = match self.scheduler.threads.get_mut(&current_tid) {
                    Some(t) if t.state == scheduler::virtual_thread::JavaThreadState::Runnable => t,
                    _ => continue,
                };

                self.vm.current_monitor_depth = thread.held_monitor_count;
                // Same strategy: large but bounded time slice for lock holders
                let steps = if thread.held_monitor_count > 0 {
                    50_000
                } else {
                    1_000
                };
                let result = interpreter::step(
                    &mut thread.stack,
                    &mut thread.pc,
                    &mut thread.current_method,
                    current_tid,
                    &mut self.vm,
                    steps,
                );
                let thread = self.scheduler.threads.get_mut(&current_tid).unwrap();
                thread.held_monitor_count = self.vm.current_monitor_depth;
                let weight = 1.0 / (thread.priority as f64);
                thread.virtual_runtime += weight * 1000.0;
                step_count += 1;
                stepped_any = true;

                self.scheduler.dispatch_result(current_tid, result);

                // Try GC at this safe-point
                self.try_gc();

                // Clean up alive_threads and monitors when a thread terminates
                if let Some(t) = self.scheduler.threads.get(&current_tid) {
                    if t.state == scheduler::virtual_thread::JavaThreadState::Terminated {
                        if let Some(&obj_ref) = self.vm.thread_id_to_object.get(&current_tid) {
                            self.vm.alive_threads.remove(&obj_ref);
                        }
                        let owned_objs: Vec<u32> = self
                            .vm
                            .monitors
                            .iter()
                            .filter(|(_, m)| m.owner == Some(current_tid))
                            .map(|(&obj, _)| obj)
                            .collect();
                        for obj_id in owned_objs {
                            if let Some(monitor) = self.vm.monitors.get_mut(&obj_id) {
                                monitor.owner = None;
                                monitor.recursion = 0;
                                if let Some(next) = monitor.blocked_queue.pop_front() {
                                    monitor.owner = Some(next);
                                    monitor.recursion = 1;
                                }
                            }
                        }
                    }
                }

                // Early exit: if the main thread we're waiting for has completed,
                // return immediately without wasting steps on background threads.
                let main_done = self.scheduler.threads.get(&tid).map_or(true, |t| {
                    t.state == scheduler::virtual_thread::JavaThreadState::Terminated
                });
                if main_done {
                    return RunResult::Done;
                }

                let wakeups = std::mem::take(&mut self.vm.pending_notify_wakeups);
                for (obj_id, notified_tid, saved_recursion) in wakeups {
                    for ws in self.scheduler.wait_sets.values_mut() {
                        ws.retain(|&t| t != notified_tid);
                    }
                    self.scheduler.cancel_sleep_deadline(notified_tid);
                    let already_owner = self
                        .vm
                        .monitors
                        .get(&obj_id)
                        .map(|m| m.owner == Some(notified_tid))
                        .unwrap_or(false);
                    if already_owner {
                        // Restore saved_recursion since wait() cleared it and exit()
                        // transferred ownership with rec=0
                        if let Some(monitor) = self.vm.monitors.get_mut(&obj_id) {
                            monitor.recursion = saved_recursion;
                        }
                        if let Some(t) = self.scheduler.threads.get_mut(&notified_tid) {
                            t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                            t.resume_action = None;
                            if !active_tids.contains(&notified_tid) {
                                active_tids.push(notified_tid);
                            }
                        }
                    } else {
                        if let Some(t) = self.scheduler.threads.get_mut(&notified_tid) {
                            t.state = scheduler::virtual_thread::JavaThreadState::Blocked;
                            t.resume_action =
                                Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                                    obj_id,
                                    saved_recursion,
                                });
                            if !active_tids.contains(&notified_tid) {
                                active_tids.push(notified_tid);
                            }
                        }
                        let monitor = self.vm.monitors.entry(obj_id).or_default();
                        if !monitor.blocked_queue.contains(&notified_tid) {
                            monitor.blocked_queue.push_back(notified_tid);
                        }
                    }
                }

                // Process monitor transfer wakeups from MONITOREXIT
                let transfers = std::mem::take(&mut self.vm.monitor_transfer_wakeups);
                for next_tid in transfers {
                    if let Some(t) = self.scheduler.threads.get_mut(&next_tid) {
                        if t.state == scheduler::virtual_thread::JavaThreadState::Blocked {
                            if let Some(
                                scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                                    obj_id,
                                    saved_recursion,
                                },
                            ) = t.resume_action.take()
                            {
                                if let Some(monitor) = self.vm.monitors.get_mut(&obj_id) {
                                    monitor.recursion = saved_recursion;
                                }
                            }
                            t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                            if !active_tids.contains(&next_tid) {
                                active_tids.push(next_tid);
                            }
                        }
                    }
                    for m in self.vm.monitors.values_mut() {
                        if m.owner == Some(next_tid) {
                            m.blocked_queue.retain(|&t| t != next_tid);
                        }
                    }
                }

                break;
            }

            if !stepped_any {
                idle_iterations += 1;

                let main_done = self.scheduler.threads.get(&tid).map_or(true, |t| {
                    t.state == scheduler::virtual_thread::JavaThreadState::Terminated
                });

                // If main thread is done, exit immediately regardless of other threads
                if main_done {
                    return RunResult::Done;
                }

                // Check ASYNC_PENDING first — if the main tid is Waiting for async,
                // we must return (or auto-resolve) regardless of idle_iterations.
                let tid_state = self.scheduler.threads.get(&tid).map(|t| t.state);
                let tid_waiting =
                    tid_state == Some(scheduler::virtual_thread::JavaThreadState::Waiting);
                let tid_timed_waiting =
                    tid_state == Some(scheduler::virtual_thread::JavaThreadState::TimedWaiting);

                if tid_timed_waiting {
                    // Main thread is sleeping — advance virtual clock to wake it
                    self.scheduler.advance_time_to_next_deadline();
                    self.scheduler.wake_expired_sleepers();
                    self.process_blocked_threads_for_completion(&active_tids);
                    idle_iterations = 0;
                    continue;
                }

                if tid_waiting {
                    if auto_resolve_async {
                        let resolved_any = self.auto_resolve_pending_async();
                        if resolved_any {
                            if let Some(t) = self.scheduler.threads.get_mut(&tid) {
                                t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                            }
                            continue;
                        }
                    }
                    return RunResult::AsyncPending;
                }

                if idle_iterations >= max_idle {
                    // Silently exit — background threads (Timers etc.) may remain
                    // idle during synchronous init, which is expected.
                    return RunResult::Done;
                }

                let any_timed = active_tids.iter().any(|&atid| {
                    self.scheduler.threads.get(&atid).map_or(false, |t| {
                        matches!(
                            t.state,
                            scheduler::virtual_thread::JavaThreadState::TimedWaiting
                        )
                    })
                });
                if any_timed {
                    self.scheduler.wake_expired_sleepers();
                    self.process_blocked_threads_for_completion(&active_tids);
                    continue;
                }

                return RunResult::Done; // No progress possible — all threads blocked/waiting without timed
            } else {
                idle_iterations = 0;
            }

            // Check if the main thread is Waiting (async) — auto-resolve if enabled
            let tid_state = self.scheduler.threads.get(&tid).map(|t| t.state);
            if tid_state == Some(scheduler::virtual_thread::JavaThreadState::Waiting) {
                if auto_resolve_async {
                    let resolved = self.auto_resolve_pending_async();
                    if resolved {
                        if let Some(t) = self.scheduler.threads.get_mut(&tid) {
                            t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                        }
                        continue;
                    }
                }
                return RunResult::AsyncPending;
            }

            // Check if the main thread for this invocation is done
            if tid_state == Some(scheduler::virtual_thread::JavaThreadState::Terminated)
                || tid_state.is_none()
            {
                return RunResult::Done;
            }

            let all_done = active_tids.iter().all(|&atid| {
                self.scheduler.threads.get(&atid).map_or(true, |t| {
                    t.daemon || t.state == scheduler::virtual_thread::JavaThreadState::Terminated
                })
            });
            if all_done {
                return RunResult::Done;
            }
        }
    }

    /// Execute one scheduling round.
    /// Returns: 0 = all threads terminated, 1 = stepped (has runnable threads), 2 = alive but idle (no runnable threads)
    pub fn step(&mut self, time_ms: u32) -> i32 {
        self.scheduler.time_offset_ms += time_ms as u64;
        self.vm.time_offset_ms = self.scheduler.time_offset_ms; // sync for native methods
        self.scheduler.wake_expired_sleepers();

        self.vm.step_count += 1;

        // Process any pending thread spawns
        self.process_pending_thread_spawns();

        // Process notify wakeups
        let wakeups = std::mem::take(&mut self.vm.pending_notify_wakeups);
        for (obj_id, notified_tid, saved_recursion) in wakeups {
            for ws in self.scheduler.wait_sets.values_mut() {
                ws.retain(|&t| t != notified_tid);
            }
            self.scheduler.cancel_sleep_deadline(notified_tid);
            // Check if this thread already owns the monitor (via transfer in exit())
            let already_owner = self
                .vm
                .monitors
                .get(&obj_id)
                .map(|m| m.owner == Some(notified_tid))
                .unwrap_or(false);
            if already_owner {
                // Thread already got the monitor via transfer — just mark Runnable
                // and restore saved_recursion since wait() cleared it
                if let Some(monitor) = self.vm.monitors.get_mut(&obj_id) {
                    monitor.recursion = saved_recursion;
                }
                if let Some(t) = self.scheduler.threads.get_mut(&notified_tid) {
                    t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                    t.resume_action = None;
                    if !self.scheduler.run_queue.contains(&notified_tid) {
                        self.scheduler.run_queue.push_back(notified_tid);
                    }
                }
            } else {
                if let Some(t) = self.scheduler.threads.get_mut(&notified_tid) {
                    t.state = scheduler::virtual_thread::JavaThreadState::Blocked;
                    t.resume_action =
                        Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                            obj_id,
                            saved_recursion,
                        });
                    self.scheduler.run_queue.push_back(notified_tid);
                }
                // notify_all() already adds to blocked_queue, so only add if not already there
                let monitor = self.vm.monitors.entry(obj_id).or_default();
                if !monitor.blocked_queue.contains(&notified_tid) {
                    monitor.blocked_queue.push_back(notified_tid);
                }
            }
        }

        // Process monitor transfer wakeups from previous step
        let transfers = std::mem::take(&mut self.vm.monitor_transfer_wakeups);
        for next_tid in transfers {
            if let Some(t) = self.scheduler.threads.get_mut(&next_tid) {
                if t.state == scheduler::virtual_thread::JavaThreadState::Blocked {
                    // If this was a ReenterAfterWait thread, restore saved_recursion
                    // since exit() now sets rec=0 on transfer.
                    if let Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                        obj_id,
                        saved_recursion,
                    }) = t.resume_action.take()
                    {
                        if let Some(monitor) = self.vm.monitors.get_mut(&obj_id) {
                            monitor.recursion = saved_recursion;
                        }
                    }
                    t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                    if !self.scheduler.run_queue.contains(&next_tid) {
                        self.scheduler.run_queue.push_back(next_tid);
                    }
                }
            }
            // Remove from blocked_queue since it's now the owner
            for m in self.vm.monitors.values_mut() {
                if m.owner == Some(next_tid) {
                    m.blocked_queue.retain(|&t| t != next_tid);
                }
            }
        }

        // Handle blocked threads that can now proceed
        self.process_blocked_threads();

        // Resume threads whose async requests have been resolved by JS
        self.resume_resolved_async_threads();

        // If no thread is runnable, return idle status.
        // Don't auto-advance virtual time here — let the JS run loop
        // control pacing via jvm_idle_delay() + setTimeout. This preserves
        // real-time behavior for Thread.sleep() (e.g., 60fps game loops).
        // Virtual time advancement is only done in run_thread_to_completion()
        // for initialization sequences where real-time doesn't matter.
        if self.scheduler.run_queue.is_empty() {
            // Deadlock detection: if all alive threads are Blocked,
            // detect circular wait and break it by forcing one thread to proceed.
            if self.try_break_deadlock() {
                // A deadlock was broken, thread added to run_queue
            } else if self.scheduler.has_alive_threads() {
                self.apply_cheat_tick();
                return 2; // IDLE — threads alive but none runnable
            } else {
                self.apply_cheat_tick();
                return 0; // All threads terminated
            }
        }

        if let Some(tid) = self.scheduler.pick_next_thread() {
            let thread = self.scheduler.threads.get_mut(&tid).unwrap();

            // Load monitor depth for this thread
            self.vm.current_monitor_depth = thread.held_monitor_count;

            // Key insight from PluotSorbet: never preempt while holding monitors.
            // In cooperative scheduling, AB-BA "deadlocks" only occur when a thread
            // is preempted mid-critical-section. PluotSorbet uses 5ms preemption
            // intervals (~50K instructions), making mid-sync preemption nearly
            // impossible. We replicate this: 50K steps while holding monitors is
            // enough to complete virtually any synchronized block. If a thread
            // somehow exceeds 50K steps while holding a lock (unusual), the
            // try_break_deadlock fallback handles any resulting contention.
            let max_steps = if thread.held_monitor_count > 0 {
                50_000
            } else {
                1_500
            };

            let result = interpreter::step(
                &mut thread.stack,
                &mut thread.pc,
                &mut thread.current_method,
                tid,
                &mut self.vm,
                max_steps,
            );

            // Store monitor depth back
            let thread = self.scheduler.threads.get_mut(&tid).unwrap();
            thread.held_monitor_count = self.vm.current_monitor_depth;
            let weight = 1.0 / (thread.priority as f64);
            thread.virtual_runtime += weight * 1000.0;

            self.scheduler.dispatch_result(tid, result);

            // Process any notify wakeups that were generated during this step.
            // Without this, threads woken by notifyAll() during interpreter execution
            // would remain Blocked/Waiting until the *next* step() call, potentially
            // causing the scheduler to report IDLE even though work is pending.
            let wakeups = std::mem::take(&mut self.vm.pending_notify_wakeups);
            for (obj_id, notified_tid, saved_recursion) in wakeups {
                for ws in self.scheduler.wait_sets.values_mut() {
                    ws.retain(|&t| t != notified_tid);
                }
                self.scheduler.cancel_sleep_deadline(notified_tid);
                // Check if this thread already owns the monitor (via transfer in exit())
                let already_owner = self
                    .vm
                    .monitors
                    .get(&obj_id)
                    .map(|m| m.owner == Some(notified_tid))
                    .unwrap_or(false);
                if already_owner {
                    // Thread already got the monitor via transfer — just mark Runnable
                    // and restore saved_recursion since wait() cleared it
                    if let Some(monitor) = self.vm.monitors.get_mut(&obj_id) {
                        monitor.recursion = saved_recursion;
                    }
                    if let Some(t) = self.scheduler.threads.get_mut(&notified_tid) {
                        t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                        t.resume_action = None;
                        if !self.scheduler.run_queue.contains(&notified_tid) {
                            self.scheduler.run_queue.push_back(notified_tid);
                        }
                    }
                } else {
                    if let Some(t) = self.scheduler.threads.get_mut(&notified_tid) {
                        t.state = scheduler::virtual_thread::JavaThreadState::Blocked;
                        t.resume_action =
                            Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                                obj_id,
                                saved_recursion,
                            });
                        if !self.scheduler.run_queue.contains(&notified_tid) {
                            self.scheduler.run_queue.push_back(notified_tid);
                        }
                    }
                    let monitor = self.vm.monitors.entry(obj_id).or_default();
                    if !monitor.blocked_queue.contains(&notified_tid) {
                        monitor.blocked_queue.push_back(notified_tid);
                    }
                }
            }
            // Process monitor transfer wakeups: when MONITOREXIT transfers ownership
            // to a blocked thread, that thread can now proceed. If it has a
            // ReenterAfterWait resume_action, the recursion was already set by exit().
            // Just mark it Runnable so pick_next_thread can select it.
            let transfers = std::mem::take(&mut self.vm.monitor_transfer_wakeups);
            for next_tid in transfers {
                if let Some(t) = self.scheduler.threads.get_mut(&next_tid) {
                    if t.state == scheduler::virtual_thread::JavaThreadState::Blocked {
                        // If this was a ReenterAfterWait thread, restore saved_recursion
                        // since exit() now sets rec=0 on transfer.
                        if let Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                            obj_id,
                            saved_recursion,
                        }) = t.resume_action.take()
                        {
                            if let Some(monitor) = self.vm.monitors.get_mut(&obj_id) {
                                monitor.recursion = saved_recursion;
                            }
                        }
                        t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                        if !self.scheduler.run_queue.contains(&next_tid) {
                            self.scheduler.run_queue.push_back(next_tid);
                        }
                    }
                }
                // Remove from blocked_queue since it's now the owner
                for m in self.vm.monitors.values_mut() {
                    if m.owner == Some(next_tid) {
                        m.blocked_queue.retain(|&t| t != next_tid);
                    }
                }
            }
            self.process_blocked_threads();

            // Also process any new thread spawns generated during this step
            self.process_pending_thread_spawns();

            // Try GC at this safe-point
            self.try_gc();

            // Clean up alive_threads and monitors when a thread terminates
            if let Some(t) = self.scheduler.threads.get(&tid) {
                if t.state == scheduler::virtual_thread::JavaThreadState::Terminated {
                    if let Some(&obj_ref) = self.vm.thread_id_to_object.get(&tid) {
                        self.vm.alive_threads.remove(&obj_ref);
                    }
                    // Release all monitors owned by this thread
                    let owned_objs: Vec<u32> = self
                        .vm
                        .monitors
                        .iter()
                        .filter(|(_, m)| m.owner == Some(tid))
                        .map(|(&obj, _)| obj)
                        .collect();
                    for obj_id in owned_objs {
                        if let Some(monitor) = self.vm.monitors.get_mut(&obj_id) {
                            monitor.owner = None;
                            monitor.recursion = 0;
                            // Transfer to next blocked thread if any
                            if let Some(next) = monitor.blocked_queue.pop_front() {
                                monitor.owner = Some(next);
                                monitor.recursion = 1;
                                // Also wake the thread so it can proceed
                                self.vm.monitor_transfer_wakeups.push(next);
                            }
                        }
                    }
                }
            }
        }

        self.apply_cheat_tick();

        if self.vm.midlet_destroyed {
            self.vm
                .trace_output
                .push_str("[STEP] returning DEAD (MIDlet notifyDestroyed called)\n");
            return 0;
        }

        if self.scheduler.has_alive_threads() {
            1
        } else {
            self.vm
                .trace_output
                .push_str("[STEP] returning DEAD (no alive threads)\n");
            0
        }
    }

    fn apply_cheat_tick(&mut self) {
        self.cheat_engine.apply_freezes(&mut self.vm.heap);
        self.cheat_engine.watch_update(&self.vm.heap);
    }

    /// Wake threads that were parked on AsyncWait and now have results.
    fn resume_resolved_async_threads(&mut self) {
        // Collect thread IDs that have resolved results
        let resolved: Vec<(u32, ThreadId)> = self
            .vm
            .async_requests
            .iter()
            .filter_map(|(&rid, entry)| {
                (entry.state == vm::AsyncRequestState::Resolved)
                    .then_some((rid, entry.request.thread_id))
            })
            .collect();

        for (_rid, tid) in resolved {
            if let Some(thread) = self.scheduler.threads.get_mut(&tid) {
                if thread.state == scheduler::virtual_thread::JavaThreadState::Waiting {
                    thread.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                    if !self.scheduler.run_queue.contains(&tid) {
                        self.scheduler.run_queue.push_back(tid);
                    }
                }
            }
        }
    }

    fn process_blocked_threads(&mut self) {
        let blocked_tids: Vec<u32> = self
            .scheduler
            .threads
            .iter()
            .filter(|(_, t)| t.state == scheduler::virtual_thread::JavaThreadState::Blocked)
            .map(|(&tid, _)| tid)
            .collect();

        for btid in blocked_tids {
            let resume = self
                .scheduler
                .threads
                .get_mut(&btid)
                .unwrap()
                .resume_action
                .take();
            match resume {
                Some(scheduler::virtual_thread::ResumeAction::RetryMonitorEnter { obj_id }) => {
                    let monitor = self.vm.monitors.entry(obj_id).or_default();
                    if monitor.owner.is_none() || monitor.owner == Some(btid) {
                        monitor.owner = Some(btid);
                        // rec=0: interpreter retries MONITORENTER → try_enter sets rec=1
                        monitor.recursion = 0;
                        monitor.blocked_queue.retain(|&t| t != btid);
                        monitor.wait_set.retain(|&t| t != btid);
                        self.scheduler.threads.get_mut(&btid).unwrap().state =
                            scheduler::virtual_thread::JavaThreadState::Runnable;
                        if !self.scheduler.run_queue.contains(&btid) {
                            self.scheduler.run_queue.push_back(btid);
                        }
                    } else {
                        let owner_tid = monitor.owner.unwrap();
                        let owner_state = self.scheduler.threads.get(&owner_tid).map(|t| t.state);

                        // If owner is Terminated, release the monitor and let this thread have it
                        if owner_state
                            == Some(scheduler::virtual_thread::JavaThreadState::Terminated)
                            || owner_state.is_none()
                        {
                            let monitor = self.vm.monitors.entry(obj_id).or_default();
                            monitor.owner = Some(btid);
                            // rec=0: interpreter retries MONITORENTER → try_enter sets rec=1
                            monitor.recursion = 0;
                            monitor.blocked_queue.retain(|&t| t != btid);
                            monitor.wait_set.retain(|&t| t != btid);
                            self.scheduler.threads.get_mut(&btid).unwrap().state =
                                scheduler::virtual_thread::JavaThreadState::Runnable;
                            if !self.scheduler.run_queue.contains(&btid) {
                                self.scheduler.run_queue.push_back(btid);
                            }
                        } else {
                            // Add to monitor's blocked_queue so exit() can transfer ownership
                            let monitor = self.vm.monitors.entry(obj_id).or_default();
                            if !monitor.blocked_queue.contains(&btid) {
                                monitor.blocked_queue.push_back(btid);
                            }
                            self.scheduler.threads.get_mut(&btid).unwrap().resume_action =
                                Some(scheduler::virtual_thread::ResumeAction::RetryMonitorEnter {
                                    obj_id,
                                });
                        }
                    }
                }
                Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                    obj_id,
                    saved_recursion,
                }) => {
                    let monitor = self.vm.monitors.entry(obj_id).or_default();
                    if monitor.owner.is_none() || monitor.owner == Some(btid) {
                        monitor.owner = Some(btid);
                        monitor.recursion = saved_recursion;
                        monitor.blocked_queue.retain(|&t| t != btid);
                        monitor.wait_set.retain(|&t| t != btid);
                        self.scheduler.threads.get_mut(&btid).unwrap().state =
                            scheduler::virtual_thread::JavaThreadState::Runnable;
                        if !self.scheduler.run_queue.contains(&btid) {
                            self.scheduler.run_queue.push_back(btid);
                        }
                    } else {
                        let owner_tid = monitor.owner.unwrap();
                        let owner_state = self.scheduler.threads.get(&owner_tid).map(|t| t.state);

                        // If owner is Terminated, release the monitor and let this thread have it
                        if owner_state
                            == Some(scheduler::virtual_thread::JavaThreadState::Terminated)
                            || owner_state.is_none()
                        {
                            let monitor = self.vm.monitors.entry(obj_id).or_default();
                            monitor.owner = Some(btid);
                            monitor.recursion = saved_recursion;
                            monitor.blocked_queue.retain(|&t| t != btid);
                            monitor.wait_set.retain(|&t| t != btid);
                            self.scheduler.threads.get_mut(&btid).unwrap().state =
                                scheduler::virtual_thread::JavaThreadState::Runnable;
                            if !self.scheduler.run_queue.contains(&btid) {
                                self.scheduler.run_queue.push_back(btid);
                            }
                        } else {
                            // Add to monitor's blocked_queue so exit() can transfer ownership
                            let monitor = self.vm.monitors.entry(obj_id).or_default();
                            if !monitor.blocked_queue.contains(&btid) {
                                monitor.blocked_queue.push_back(btid);
                            }
                            self.scheduler.threads.get_mut(&btid).unwrap().resume_action =
                                Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                                    obj_id,
                                    saved_recursion,
                                });
                        }
                    }
                }
                _ => {}
            }
        }
    }

    /// Detect AB-BA deadlock among Blocked threads and break it.
    ///
    /// In cooperative scheduling, AB-BA "deadlocks" aren't real deadlocks — they
    /// occur because we serialize thread execution. In a real multi-threaded JVM,
    /// both threads would run in parallel and eventually release their locks.
    ///
    /// Strategy: let start_tid acquire the monitor it needs (which victim holds).
    /// Since only one thread runs at a time, there's no real concurrent access.
    /// start_tid will execute its synchronized block, release the lock via
    /// MONITOREXIT, and then victim (still in blocked_queue) will get it back.
    /// Victim keeps its ownership of `waited_obj` — we don't touch it.
    fn try_break_deadlock(&mut self) -> bool {
        // Collect blocked threads and what monitor they're waiting on
        let blocked: Vec<(u32, u32)> = self
            .scheduler
            .threads
            .iter()
            .filter(|(_, t)| t.state == scheduler::virtual_thread::JavaThreadState::Blocked)
            .filter_map(|(&tid, t)| match &t.resume_action {
                Some(scheduler::virtual_thread::ResumeAction::RetryMonitorEnter { obj_id }) => {
                    Some((tid, *obj_id))
                }
                Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                    obj_id, ..
                }) => Some((tid, *obj_id)),
                _ => None,
            })
            .collect();

        if blocked.len() < 2 {
            return false;
        }

        // Detect cycle by following the wait-for chain
        for &(start_tid, waited_obj) in &blocked {
            let mut visited = vec![start_tid];
            let mut current_obj = waited_obj;
            loop {
                let owner_tid = match self.vm.monitors.get(&current_obj).and_then(|m| m.owner) {
                    Some(t) => t,
                    None => break,
                };
                if owner_tid == start_tid {
                    // Cycle detected!
                    // start_tid needs `waited_obj`, which is held by victim_tid.
                    // In a real JVM both would run concurrently. We simulate this by
                    // letting start_tid "borrow" the monitor: set it as owner so it
                    // can proceed. Victim stays Blocked — when start_tid eventually
                    // MONITOREXITs, victim will get ownership back via blocked_queue.
                    let victim_tid = self
                        .vm
                        .monitors
                        .get(&waited_obj)
                        .and_then(|m| m.owner)
                        .unwrap();

                    if let Some(monitor) = self.vm.monitors.get_mut(&waited_obj) {
                        // Put victim in blocked_queue — it will get the lock back
                        // when start_tid releases it
                        monitor.blocked_queue.retain(|&t| t != start_tid);
                        if !monitor.blocked_queue.contains(&victim_tid) {
                            monitor.blocked_queue.push_front(victim_tid);
                        }
                        monitor.owner = Some(start_tid);
                        monitor.recursion = 1;
                    }

                    // Make start_tid Runnable
                    if let Some(t) = self.scheduler.threads.get_mut(&start_tid) {
                        t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                        t.resume_action = None;
                    }
                    if !self.scheduler.run_queue.contains(&start_tid) {
                        self.scheduler.run_queue.push_back(start_tid);
                    }

                    // Victim stays Blocked with RetryMonitorEnter for waited_obj
                    if let Some(t) = self.scheduler.threads.get_mut(&victim_tid) {
                        t.state = scheduler::virtual_thread::JavaThreadState::Blocked;
                        t.resume_action =
                            Some(scheduler::virtual_thread::ResumeAction::RetryMonitorEnter {
                                obj_id: waited_obj,
                            });
                    }

                    let log = format!(
                        "[DEADLOCK] cycle detected: tid={} borrows mon[{}] from tid={}\n",
                        start_tid, waited_obj, victim_tid
                    );
                    self.vm.text_output.push_str(&log);
                    self.vm.trace_output.push_str(&log);

                    return true;
                }
                if visited.contains(&owner_tid) {
                    break;
                }
                visited.push(owner_tid);
                match blocked.iter().find(|&&(t, _)| t == owner_tid) {
                    Some(&(_, next_obj)) => current_obj = next_obj,
                    None => break,
                }
            }
        }
        false
    }

    fn process_blocked_threads_for_completion(&mut self, active_tids: &[ThreadId]) {
        let blocked_tids: Vec<u32> = active_tids
            .iter()
            .copied()
            .filter(|&tid| {
                self.scheduler.threads.get(&tid).map_or(false, |t| {
                    t.state == scheduler::virtual_thread::JavaThreadState::Blocked
                })
            })
            .collect();

        for btid in blocked_tids {
            let resume = self
                .scheduler
                .threads
                .get_mut(&btid)
                .unwrap()
                .resume_action
                .take();
            match resume {
                Some(scheduler::virtual_thread::ResumeAction::RetryMonitorEnter { obj_id }) => {
                    let monitor = self.vm.monitors.entry(obj_id).or_default();
                    if monitor.owner.is_none() || monitor.owner == Some(btid) {
                        monitor.owner = Some(btid);
                        // rec=0: interpreter retries MONITORENTER → try_enter sets rec=1
                        monitor.recursion = 0;
                        monitor.blocked_queue.retain(|&t| t != btid);
                        self.scheduler.threads.get_mut(&btid).unwrap().state =
                            scheduler::virtual_thread::JavaThreadState::Runnable;
                    } else {
                        let monitor = self.vm.monitors.entry(obj_id).or_default();
                        if !monitor.blocked_queue.contains(&btid) {
                            monitor.blocked_queue.push_back(btid);
                        }
                        self.scheduler.threads.get_mut(&btid).unwrap().resume_action =
                            Some(scheduler::virtual_thread::ResumeAction::RetryMonitorEnter {
                                obj_id,
                            });
                    }
                }
                Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                    obj_id,
                    saved_recursion,
                }) => {
                    let monitor = self.vm.monitors.entry(obj_id).or_default();
                    if monitor.owner.is_none() || monitor.owner == Some(btid) {
                        monitor.owner = Some(btid);
                        monitor.recursion = saved_recursion;
                        monitor.blocked_queue.retain(|&t| t != btid);
                        self.scheduler.threads.get_mut(&btid).unwrap().state =
                            scheduler::virtual_thread::JavaThreadState::Runnable;
                    } else {
                        let monitor = self.vm.monitors.entry(obj_id).or_default();
                        if !monitor.blocked_queue.contains(&btid) {
                            monitor.blocked_queue.push_back(btid);
                        }
                        self.scheduler.threads.get_mut(&btid).unwrap().resume_action =
                            Some(scheduler::virtual_thread::ResumeAction::ReenterAfterWait {
                                obj_id,
                                saved_recursion,
                            });
                    }
                }
                _ => {}
            }
        }
    }

    /// Run a GC cycle if heap usage exceeds threshold or force_gc is set
    fn try_gc(&mut self) {
        if self.gc_enabled && (self.vm.heap.needs_gc() || self.vm.force_gc) {
            self.vm.force_gc = false;
            let threads: Vec<_> = self.scheduler.threads.values().cloned().collect();
            let _stats = gc::collect(&mut self.vm, &threads);
        }
    }

    /// Push a native event into the queue and wake the NativeEventMonitor thread.
    pub fn push_native_event(&mut self, event: vm::NativeEventData) {
        self.vm.native_event_queue.push_back(event);
        // Wake the NativeEventMonitor thread if it's suspended in NativeEventWait.
        // This is analogous to PluotSorbet's sendNativeEvent resolving the Promise.
        if let Some(monitor_tid) = self.vm.native_event_monitor_tid {
            if let Some(t) = self.scheduler.threads.get_mut(&monitor_tid) {
                if t.state == scheduler::virtual_thread::JavaThreadState::Waiting {
                    t.state = scheduler::virtual_thread::JavaThreadState::Runnable;
                    self.scheduler.run_queue.push_back(monitor_tid);
                }
            }
        }
    }

    pub fn get_output(&mut self) -> String {
        std::mem::take(&mut self.vm.text_output)
    }

    pub fn get_command_buffer(&mut self) -> Vec<u8> {
        self.vm.flush_command_buffer()
    }

    fn clear_softkey_labels(&mut self) {
        self.vm.command_buffer.push(vm::cmd::MENU_UPDATE_COMMANDS);
        self.vm
            .command_buffer
            .extend_from_slice(&self.vm.foreground_display_id.to_le_bytes());
        self.vm.write_string_to_command_buffer("");
        self.vm.write_string_to_command_buffer("");
    }

    /// Returns the number of virtual ms until the nearest sleeping thread
    /// wakes up.  Returns 0 when a sleeper is already due.
    /// Returns u32::MAX when no timed sleepers exist but threads are alive
    /// (indefinite wait — only woken by external events like key press, notify, async resolve).
    pub fn idle_delay(&self) -> u32 {
        if let Some(&std::cmp::Reverse((deadline, _))) = self.scheduler.sleep_queue.peek() {
            let now = self.scheduler.effective_now();
            if deadline > now {
                return (deadline - now) as u32;
            }
            // deadline <= now: a sleeper is already due, needs immediate processing
            return 0;
        }
        // No timed sleepers. If threads are alive they're in indefinite wait
        // (NativeEventWait, Wait without timeout, Join, AsyncWait).
        // Return i32::MAX (largest value representable as positive in JS/WASM interop)
        // so JS uses setTimeout instead of busy-polling.
        // External events (pushNativeEvent, resolveAsync) will actively re-trigger the tick loop.
        if self.scheduler.has_alive_threads() {
            i32::MAX as u32
        } else {
            0
        }
    }

    pub fn set_speed_multiplier(&mut self, speed: f64) {
        self.scheduler.speed_multiplier = speed;
        self.vm.speed_multiplier = speed;
    }

    pub fn dump_threads(&self) -> String {
        use crate::scheduler::thread_stack::METHOD_ID_MASK;
        let mut diag = String::new();
        for (&tid, t) in &self.scheduler.threads {
            let s = match t.state {
                scheduler::virtual_thread::JavaThreadState::Runnable => "Runnable",
                scheduler::virtual_thread::JavaThreadState::Blocked => "Blocked",
                scheduler::virtual_thread::JavaThreadState::Waiting => "Waiting",
                scheduler::virtual_thread::JavaThreadState::TimedWaiting => "TimedWaiting",
                scheduler::virtual_thread::JavaThreadState::Terminated => "Terminated",
                _ => "?",
            };
            let action = t
                .resume_action
                .as_ref()
                .map(|a| format!("{:?}", a))
                .unwrap_or_default();
            diag.push_str(&format!(
                "  tid={} state={} name={} {}\n",
                tid, s, t.name, action
            ));
            // Print stack trace for non-terminated threads
            if t.state != scheduler::virtual_thread::JavaThreadState::Terminated {
                let stack = &t.stack;
                let mut fp = stack.fp;
                let mut depth = 0;
                let mut frame_pc = t.pc;
                while depth < 10 && fp < stack.data.len() {
                    let raw_mid = stack.data[fp + 2]; // METHOD_ID slot
                    let mid = raw_mid & METHOD_ID_MASK;
                    if let Some(m) = self.vm.class_repo.get_method(mid) {
                        diag.push_str(&format!(
                            "    at {}.{}{} pc={}\n",
                            self.vm
                                .class_repo
                                .get_class_name_by_id(m.class_id)
                                .unwrap_or(""),
                            self.vm.class_repo.get_string(m.name_id),
                            self.vm.class_repo.get_string(m.descriptor_id),
                            frame_pc
                        ));
                    } else {
                        diag.push_str(&format!("    at method_id={}\n", mid));
                    }
                    frame_pc = stack.data[fp] as usize; // CALLER_PC
                    let caller_fp = stack.data[fp + 1] as usize; // CALLER_FP
                    if caller_fp == fp || caller_fp == 0 {
                        break;
                    }
                    fp = caller_fp;
                    depth += 1;
                }
            }
        }
        diag.push_str(&format!(
            "  runQ={:?} sleepQ={} vtime={} step_calls={} effective_now={}\n",
            self.scheduler.run_queue,
            self.scheduler.sleep_queue.len(),
            self.scheduler.virtual_time_ms,
            self.vm.step_count,
            self.scheduler.effective_now()
        ));
        // Show monitor info right after runQ (before sleepQ detail) to avoid truncation
        for (&obj, m) in &self.vm.monitors {
            if m.owner.is_some()
                || !m.blocked_queue.is_empty()
                || !m.wait_set.is_empty()
                || m.pending_notifies > 0
            {
                let class_name = self.vm.get_class_name_of_object(obj).unwrap_or_default();
                let owner_state = m
                    .owner
                    .and_then(|o| self.scheduler.threads.get(&o))
                    .map(|t| format!("{:?}", t.state))
                    .unwrap_or_default();
                diag.push_str(&format!(
                    "  mon[{}] class={} owner={:?}({}) rec={} bq={:?} ws={:?} pn={}\n",
                    obj,
                    class_name,
                    m.owner,
                    owner_state,
                    m.recursion,
                    m.blocked_queue,
                    m.wait_set,
                    m.pending_notifies
                ));
            }
        }
        for (obj, ws) in &self.scheduler.wait_sets {
            if !ws.is_empty() {
                diag.push_str(&format!("  waitSet[{}]={:?}\n", obj, ws));
            }
        }
        // Append recent trace output (exceptions, warnings)
        if !self.vm.trace_output.is_empty() {
            diag.push_str("  trace_output:\n");
            for line in self.vm.trace_output.lines().rev().take(80) {
                diag.push_str(&format!("    {}\n", line));
            }
        }
        if !self.vm.text_output.is_empty() {
            diag.push_str("  text_output(last 30 lines):\n");
            for line in self.vm.text_output.lines().rev().take(30) {
                diag.push_str(&format!("    {}\n", line));
            }
        }
        diag
    }

    fn capture_core_state(&mut self) -> CoreSaveState {
        // Capture media player current time for playing players BEFORE snapshotting
        let playing_handles: Vec<i32> = self
            .vm
            .media_players
            .iter()
            .filter(|(_, ps)| ps.state >= 400)
            .map(|(&h, _)| h)
            .collect();
        for handle in playing_handles {
            if let Some(ps) = self.vm.media_players.get_mut(&handle) {
                ps.media_time_ms = call_host_get_media_time(handle);
            }
        }

        let threads: Vec<_> = self.scheduler.threads.values().cloned().collect();
        let _stats = gc::collect(&mut self.vm, &threads);
        let image_surface_snapshots = std::mem::take(&mut self.pending_image_surface_snapshots);
        let snapshot = self
            .vm
            .save_snapshot_with_image_surface_snapshots(&self.scheduler, &image_surface_snapshots);

        CoreSaveState {
            snapshot,
            main_thread_id: self.main_thread_id,
            next_context_id: self.next_context_id,
            midlet_contexts: self.midlet_contexts.clone(),
        }
    }

    pub fn prepare_image_surface_snapshot_targets(
        &mut self,
    ) -> Vec<vm::ImageSurfaceSnapshotTarget> {
        let threads: Vec<_> = self.scheduler.threads.values().cloned().collect();
        let _stats = gc::collect(&mut self.vm, &threads);
        self.vm.collect_image_surface_snapshot_targets()
    }

    fn image_surface_snapshot_targets_for_rewind(&self) -> Vec<vm::ImageSurfaceSnapshotTarget> {
        self.vm.collect_image_surface_snapshot_targets()
    }

    pub fn clear_pending_image_surface_snapshots(&mut self) {
        self.pending_image_surface_snapshots.clear();
    }

    pub fn add_pending_image_surface_snapshot(
        &mut self,
        surface_id: i32,
        width: i32,
        height: i32,
        pixels: Vec<i32>,
    ) {
        self.pending_image_surface_snapshots
            .push(vm::ImageSurfaceSnapshot {
                surface_id,
                width,
                height,
                pixels,
            });
    }

    fn write_save_section_entry(output: &mut Vec<u8>, entry: SaveSectionEntry) {
        output.extend_from_slice(&entry.section_type.to_le_bytes());
        output.extend_from_slice(&entry.offset.to_le_bytes());
        output.extend_from_slice(&entry.length.to_le_bytes());
        output.extend_from_slice(&entry.param0.to_le_bytes());
        output.extend_from_slice(&entry.param1.to_le_bytes());
    }

    fn read_save_section_entry(data: &[u8], offset: usize) -> Option<SaveSectionEntry> {
        if offset.checked_add(SAVE_SECTION_ENTRY_SIZE)? > data.len() {
            return None;
        }
        Some(SaveSectionEntry {
            section_type: u32::from_le_bytes(data[offset..offset + 4].try_into().ok()?),
            offset: u32::from_le_bytes(data[offset + 4..offset + 8].try_into().ok()?),
            length: u32::from_le_bytes(data[offset + 8..offset + 12].try_into().ok()?),
            param0: u32::from_le_bytes(data[offset + 12..offset + 16].try_into().ok()?),
            param1: u32::from_le_bytes(data[offset + 16..offset + 20].try_into().ok()?),
        })
    }

    pub fn save_state(
        &mut self,
        display_png_snapshot: Option<vm::DisplaySurfacePngSnapshot>,
    ) -> Vec<u8> {
        use flate2::write::DeflateEncoder;
        use flate2::Compression;
        use std::io::Write;

        let state = self.capture_core_state();
        let uncompressed = bincode::serialize(&state).unwrap_or_default();

        // save_snapshot() compacts only the cloned heap written into the save file.
        // The live heap used by the running game is not relocated, so cheat/freezes
        // must stay valid across auto-save. Marking them stale here makes freezes
        // stop being applied after the next auto-save tick.

        // Compress with deflate
        let mut encoder = DeflateEncoder::new(Vec::new(), Compression::default());
        let _ = encoder.write_all(&uncompressed);
        let compressed = encoder.finish().unwrap_or_default();

        // Build output: fixed header + section table + section payloads.
        let timestamp = call_host_current_time_millis() as u64;
        let display_png = display_png_snapshot.as_ref().filter(|snapshot| {
            snapshot.width > 0 && snapshot.height > 0 && !snapshot.data.is_empty()
        });
        let section_count = if display_png.is_some() { 2u32 } else { 1u32 };
        let section_table_size = (section_count as usize) * SAVE_SECTION_ENTRY_SIZE;
        let vm_state_offset = SAVE_FIXED_HEADER_SIZE + section_table_size;
        let display_png_offset = vm_state_offset + compressed.len();

        let mut output = Vec::with_capacity(
            display_png_offset + display_png.map_or(0, |snapshot| snapshot.data.len()),
        );
        output.extend_from_slice(SAVE_MAGIC);
        output.extend_from_slice(&SAVE_VERSION.to_le_bytes());
        output.extend_from_slice(&timestamp.to_le_bytes());
        output.extend_from_slice(&section_count.to_le_bytes());
        Self::write_save_section_entry(
            &mut output,
            SaveSectionEntry {
                section_type: SAVE_SECTION_VM_STATE,
                offset: vm_state_offset as u32,
                length: compressed.len() as u32,
                param0: uncompressed.len() as u32,
                param1: 0,
            },
        );
        if let Some(snapshot) = display_png {
            Self::write_save_section_entry(
                &mut output,
                SaveSectionEntry {
                    section_type: SAVE_SECTION_DISPLAY_SURFACE_PNG,
                    offset: display_png_offset as u32,
                    length: snapshot.data.len() as u32,
                    param0: snapshot.width as u32,
                    param1: snapshot.height as u32,
                },
            );
        }
        output.extend_from_slice(&compressed);
        if let Some(snapshot) = display_png {
            output.extend_from_slice(&snapshot.data);
        }
        output
    }

    pub fn load_state(&mut self, data: &[u8]) -> bool {
        use flate2::read::DeflateDecoder;
        use std::io::Read;

        let meta = match Self::parse_save_meta(data) {
            Some(meta) if meta.version == SAVE_VERSION => meta,
            _ => return false,
        };
        let vm_state_offset = meta.vm_state_offset as usize;
        let vm_state_len = meta.vm_state_len as usize;
        let vm_state_end = match vm_state_offset.checked_add(vm_state_len) {
            Some(end) if end <= data.len() => end,
            _ => return false,
        };
        let compressed = &data[vm_state_offset..vm_state_end];
        let uncompressed_len = meta.vm_state_uncompressed_len as usize;

        // Decompress
        let mut decoder = DeflateDecoder::new(compressed);
        let mut uncompressed = Vec::with_capacity(uncompressed_len);
        if decoder.read_to_end(&mut uncompressed).is_err() {
            return false;
        }

        // Deserialize
        let state: CoreSaveState = match bincode::deserialize(&uncompressed) {
            Ok(s) => s,
            Err(_) => return false,
        };

        self.restore_core_state(state)
    }

    fn restore_core_state(&mut self, state: CoreSaveState) -> bool {
        let new_scheduler = self.vm.restore_snapshot(state.snapshot);
        self.scheduler = new_scheduler;
        self.main_thread_id = state.main_thread_id;
        self.next_context_id = state.next_context_id;
        self.midlet_contexts = state.midlet_contexts;
        self.async_thread_id = None;

        // Cheat entries store raw heap addresses and are not part of save states.
        // Loading replaces the live heap with the saved snapshot, so existing
        // search/watch/freeze addresses from the previous runtime may no longer
        // point to the same values.
        self.cheat_engine.on_heap_compacted();

        // Re-allocate the pre-allocated OOM exception after restore
        self.vm.preallocated_oom = self.vm.new_object("java.lang.OutOfMemoryError");

        // Re-emit media player creation commands so host can rebuild players
        self.emit_media_players_restore();
        let _ = self.sync_host_ui_state_after_restore();

        true
    }

    /// Capture VM snapshot for rewind: skips GC and heap compaction.
    fn capture_core_state_for_rewind(&mut self) -> CoreSaveState {
        // Capture media player current time for playing players
        let playing_handles: Vec<i32> = self
            .vm
            .media_players
            .iter()
            .filter(|(_, ps)| ps.state >= 400)
            .map(|(&h, _)| h)
            .collect();
        for handle in playing_handles {
            if let Some(ps) = self.vm.media_players.get_mut(&handle) {
                ps.media_time_ms = call_host_get_media_time(handle);
            }
        }

        // Skip GC, use fast snapshot (no heap compaction), but keep the same
        // host-provided image surface pixels that normal save states use.
        let image_surface_snapshots = std::mem::take(&mut self.pending_image_surface_snapshots);
        let snapshot = self.vm.save_snapshot_fast_with_image_surface_snapshots(
            &self.scheduler,
            &image_surface_snapshots,
        );

        CoreSaveState {
            snapshot,
            main_thread_id: self.main_thread_id,
            next_context_id: self.next_context_id,
            midlet_contexts: self.midlet_contexts.clone(),
        }
    }

    /// Restore VM state from a rewind snapshot (no deserialization needed).
    fn restore_core_state_from_rewind(&mut self, state: CoreSaveState) -> bool {
        let new_scheduler = self.vm.restore_snapshot(state.snapshot);
        self.scheduler = new_scheduler;
        self.main_thread_id = state.main_thread_id;
        self.next_context_id = state.next_context_id;
        self.midlet_contexts = state.midlet_contexts;
        self.async_thread_id = None;

        // Rewind restore also swaps the live heap/scheduler. Cheat state is
        // session-local and address-based, so mark existing entries stale rather
        // than continuing to write into a restored memory image blindly.
        self.cheat_engine.on_heap_compacted();

        // Re-allocate the pre-allocated OOM exception after restore
        self.vm.preallocated_oom = self.vm.new_object("java.lang.OutOfMemoryError");

        // Re-emit media player creation commands so host can rebuild players
        self.emit_media_players_restore();
        let _ = self.sync_host_ui_state_after_restore();

        true
    }

    fn emit_media_players_restore(&mut self) {
        use vm::cmd;
        // Collect player info first to avoid borrow issues
        let players: Vec<(i32, String, String, Option<Vec<u8>>, i32, i32, bool, i64)> = self
            .vm
            .media_players
            .iter()
            .filter(|(_, ps)| ps.state >= 200)
            .map(|(&handle, ps)| {
                (
                    handle,
                    ps.locator.clone(),
                    ps.content_type.clone(),
                    ps.data.clone(),
                    ps.state,
                    ps.volume,
                    ps.muted,
                    ps.media_time_ms,
                )
            })
            .collect();

        for (handle, locator, content_type, data, player_state, volume, muted, media_time_ms) in
            players
        {
            // Emit createPlayer command
            if let Some(ref audio_data) = data {
                // InputStream-based player: use MEDIA_CREATE_PLAYER_BINARY (cmd 73)
                // Format: [cmd:u8][handle:i32][loopCount:i32][volume:i32][ct_len:u32][ct+pad][data_len:u32][data+pad]
                let ct_bytes = content_type.as_bytes();
                let ct_padded = (ct_bytes.len() + 3) & !3;
                let data_padded = (audio_data.len() + 3) & !3;

                let buf = &mut self.vm.command_buffer;
                buf.push(cmd::MEDIA_CREATE_PLAYER_BINARY);
                buf.extend_from_slice(&handle.to_le_bytes());
                buf.extend_from_slice(&1_i32.to_le_bytes()); // loopCount = 1
                buf.extend_from_slice(&volume.to_le_bytes());
                buf.extend_from_slice(&(ct_bytes.len() as u32).to_le_bytes());
                buf.extend_from_slice(ct_bytes);
                for _ in 0..(ct_padded - ct_bytes.len()) {
                    buf.push(0);
                }
                buf.extend_from_slice(&(audio_data.len() as u32).to_le_bytes());
                buf.extend_from_slice(audio_data);
                for _ in 0..(data_padded - audio_data.len()) {
                    buf.push(0);
                }
            } else if !locator.is_empty() {
                // URI-based player: [cmd:u8][handle:i32][locator_len:u32][locator_utf8][pad]
                let locator_bytes = locator.as_bytes();
                let padding = (4 - (locator_bytes.len() % 4)) % 4;
                let buf = &mut self.vm.command_buffer;
                buf.push(cmd::MEDIA_CREATE_PLAYER);
                buf.extend_from_slice(&handle.to_le_bytes());
                buf.extend_from_slice(&(locator_bytes.len() as u32).to_le_bytes());
                buf.extend_from_slice(locator_bytes);
                for _ in 0..padding {
                    buf.push(0);
                }
            } else {
                continue;
            }

            // Set volume if not default (already set in BINARY cmd for data players)
            if data.is_none() && volume != 100 {
                // [cmd:u8][handle:i32][level:i32]
                self.vm.command_buffer.push(cmd::MEDIA_SET_VOLUME);
                self.vm
                    .command_buffer
                    .extend_from_slice(&handle.to_le_bytes());
                self.vm
                    .command_buffer
                    .extend_from_slice(&volume.to_le_bytes());
            }

            // Set muted if true
            if muted {
                // [cmd:u8][handle:i32][muted:u8][pad:3]
                self.vm.command_buffer.push(cmd::MEDIA_SET_MUTED);
                self.vm
                    .command_buffer
                    .extend_from_slice(&handle.to_le_bytes());
                self.vm.command_buffer.push(1);
                self.vm.command_buffer.push(0);
                self.vm.command_buffer.push(0);
                self.vm.command_buffer.push(0);
            }

            // If player was started, play from saved position
            if player_state >= 400 {
                // [cmd:u8][handle:i32][time_ms_lo:i32][time_ms_hi:i32]
                self.vm.command_buffer.push(cmd::MEDIA_PLAY);
                self.vm
                    .command_buffer
                    .extend_from_slice(&handle.to_le_bytes());
                self.vm
                    .command_buffer
                    .extend_from_slice(&(media_time_ms as i32).to_le_bytes());
                self.vm
                    .command_buffer
                    .extend_from_slice(&((media_time_ms >> 32) as i32).to_le_bytes());
            }
        }
    }

    /// Parse save state container metadata without decompressing VM state.
    /// This intentionally does not reject older save versions so callers can
    /// decide compatibility at the UI layer.
    pub fn parse_save_meta(data: &[u8]) -> Option<SaveMeta> {
        if data.len() < SAVE_FIXED_HEADER_SIZE {
            return None;
        }
        if &data[0..4] != SAVE_MAGIC {
            return None;
        }
        let version = u32::from_le_bytes([data[4], data[5], data[6], data[7]]);
        let timestamp = u64::from_le_bytes([
            data[8], data[9], data[10], data[11], data[12], data[13], data[14], data[15],
        ]);
        let section_count = u32::from_le_bytes([data[16], data[17], data[18], data[19]]) as usize;
        if section_count == 0 {
            return None;
        }
        let table_end = SAVE_FIXED_HEADER_SIZE
            .checked_add(section_count.checked_mul(SAVE_SECTION_ENTRY_SIZE)?)?;
        if table_end > data.len() {
            return None;
        }

        let mut meta = SaveMeta {
            version,
            timestamp,
            ..Default::default()
        };
        for index in 0..section_count {
            let entry_offset = SAVE_FIXED_HEADER_SIZE + index * SAVE_SECTION_ENTRY_SIZE;
            let entry = Self::read_save_section_entry(data, entry_offset)?;
            if entry.length == 0 {
                continue;
            }
            match entry.section_type {
                SAVE_SECTION_VM_STATE => {
                    meta.vm_state_offset = entry.offset;
                    meta.vm_state_len = entry.length;
                    meta.vm_state_uncompressed_len = entry.param0;
                }
                SAVE_SECTION_DISPLAY_SURFACE_PNG => {
                    meta.display_png_offset = entry.offset;
                    meta.display_png_len = entry.length;
                    meta.display_png_width = entry.param0;
                    meta.display_png_height = entry.param1;
                }
                _ => {}
            }
        }
        if meta.vm_state_len == 0 || meta.vm_state_uncompressed_len == 0 {
            return None;
        }
        Some(meta)
    }

    /// Binary: [count:u32] per entry: [surface_id:i32][width:i32][height:i32][name_len:u32][name_utf8][pad_to_4]
    pub fn take_pending_resource_image_restores(&mut self) -> Vec<u8> {
        let entries = self.vm.take_pending_resource_image_restores();
        if entries.is_empty() {
            return Vec::new();
        }
        let mut buf = Vec::with_capacity(4 + entries.len() * 20);
        buf.extend_from_slice(&(entries.len() as u32).to_le_bytes());
        for e in &entries {
            buf.extend_from_slice(&e.surface_id.to_le_bytes());
            buf.extend_from_slice(&e.width.to_le_bytes());
            buf.extend_from_slice(&e.height.to_le_bytes());
            let name_bytes = e.resource_name.as_bytes();
            buf.extend_from_slice(&(name_bytes.len() as u32).to_le_bytes());
            buf.extend_from_slice(name_bytes);
            let padding = (4 - (name_bytes.len() % 4)) % 4;
            for _ in 0..padding {
                buf.push(0);
            }
        }
        buf
    }

    /// Binary: [count:u32] per entry: [surface_id:i32][width:i32][height:i32][source_surface_id:i32]
    ///   [x:i32][y:i32][region_width:i32][region_height:i32][transform:i32]
    pub fn take_pending_derived_image_restores(&mut self) -> Vec<u8> {
        let entries = self.vm.take_pending_derived_image_restores();
        if entries.is_empty() {
            return Vec::new();
        }
        let mut buf = Vec::with_capacity(4 + entries.len() * 36);
        buf.extend_from_slice(&(entries.len() as u32).to_le_bytes());
        for e in &entries {
            buf.extend_from_slice(&e.surface_id.to_le_bytes());
            buf.extend_from_slice(&e.width.to_le_bytes());
            buf.extend_from_slice(&e.height.to_le_bytes());
            buf.extend_from_slice(&e.source_surface_id.to_le_bytes());
            buf.extend_from_slice(&e.x.to_le_bytes());
            buf.extend_from_slice(&e.y.to_le_bytes());
            buf.extend_from_slice(&e.region_width.to_le_bytes());
            buf.extend_from_slice(&e.region_height.to_le_bytes());
            buf.extend_from_slice(&e.transform.to_le_bytes());
        }
        buf
    }

    fn auto_resolve_pending_async(&mut self) -> bool {
        use vm::{AsyncRequestType, AsyncResult};

        let pending: Vec<(u32, AsyncRequestType)> = self
            .vm
            .claim_pending_async_requests(usize::MAX)
            .into_iter()
            .map(|(id, req)| (id, req.request_type))
            .collect();
        let mut resolved_any = false;
        for (id, request_type) in pending {
            let result = match request_type {
                AsyncRequestType::DecodeImage { .. } => AsyncResult::ImageDecoded {
                    width: 0,
                    height: 0,
                },
                AsyncRequestType::GetPixels { width, height, .. } => AsyncResult::PixelsRead {
                    pixels: vec![0; (width.max(0) * height.max(0)) as usize],
                },
                AsyncRequestType::ReadM3gImage { width, height, .. } => AsyncResult::M3gImageRead {
                    width,
                    height,
                    pixels: vec![0; (width.max(0) * height.max(0) * 4) as usize],
                },
                AsyncRequestType::DecodeM3gExternalImage { .. } => AsyncResult::M3gImageRead {
                    width: 0,
                    height: 0,
                    pixels: Vec::new(),
                },
                AsyncRequestType::RmsLoad { .. } => AsyncResult::RmsLoaded { data: None },
                AsyncRequestType::RmsKeys => AsyncResult::RmsKeysList { keys: vec![] },
                AsyncRequestType::TextInput { .. } => {
                    AsyncResult::TextInputCommitted { text: None }
                }
                AsyncRequestType::DateInput {
                    initialized,
                    value_millis,
                    ..
                } => AsyncResult::DateInputCommitted {
                    initialized,
                    value_millis,
                    committed: false,
                },
                AsyncRequestType::Bluetooth { .. } => AsyncResult::BluetoothCompleted {
                    response: "ERR\tBluetooth unavailable".to_string(),
                },
            };
            resolved_any |= self.vm.resolve_async_request(id, result);
        }
        resolved_any
    }

    /// Claim pending async requests as binary buffer.
    /// Format: [count:u32] then per request:
    ///   decode_image: [id:u32][type=0:u8][pad:3][surface_id:i32][bytes_len:u32]
    ///   rms_load:     [id:u32][type=1:u8][pad:3][key_len:u32][key_utf8][pad_to_4]
    ///   rms_keys:     [id:u32][type=2:u8][pad:3]
    ///   get_pixels:   [id:u32][type=3:u8][pad:3][surface_id:i32][x:i32][y:i32][width:i32][height:i32]
    ///   text_input:   [id:u32][type=4:u8][pad:3][text_field:u32][constraints:i32][max_size:i32][text_len:u32][text_utf8][pad_to_4]
    ///   date_input:   [id:u32][type=5:u8][pad:3][date_field:u32][mode:i32][initialized:u8][pad:3][value_millis:i64]
    ///   read_m3g_image:[id:u32][type=6:u8][pad:3][image_handle:u32][width:i32][height:i32]
    ///   decode_m3g_external_image:[id:u32][type=7:u8][pad:3][name_len:u32][name_utf8][pad_to_4][bytes_len:u32]
    ///   bluetooth: [id:u32][type=8:u8][pad:3][operation_len:u32][operation_utf8][pad_to_4][payload_len:u32][payload_utf8][pad_to_4]
    pub fn claim_pending_async(&mut self, max_count: usize) -> Vec<u8> {
        use vm::AsyncRequestType;

        let requests: Vec<_> = self.vm.claim_pending_async_requests(max_count);
        if requests.is_empty() {
            return Vec::new();
        }
        let mut buf = Vec::with_capacity(4 + requests.len() * 16);
        buf.extend_from_slice(&(requests.len() as u32).to_le_bytes());
        for (id, req) in &requests {
            buf.extend_from_slice(&id.to_le_bytes());
            match &req.request_type {
                AsyncRequestType::DecodeImage {
                    surface_id,
                    image_bytes,
                } => {
                    buf.push(0);
                    buf.push(0);
                    buf.push(0);
                    buf.push(0); // type=0 + pad
                    buf.extend_from_slice(&surface_id.to_le_bytes());
                    buf.extend_from_slice(&(image_bytes.len() as u32).to_le_bytes());
                }
                AsyncRequestType::RmsLoad { key } => {
                    buf.push(1);
                    buf.push(0);
                    buf.push(0);
                    buf.push(0); // type=1 + pad
                    let key_bytes = key.as_bytes();
                    buf.extend_from_slice(&(key_bytes.len() as u32).to_le_bytes());
                    buf.extend_from_slice(key_bytes);
                    let padding = (4 - (key_bytes.len() % 4)) % 4;
                    for _ in 0..padding {
                        buf.push(0);
                    }
                }
                AsyncRequestType::RmsKeys => {
                    buf.push(2);
                    buf.push(0);
                    buf.push(0);
                    buf.push(0); // type=2 + pad
                }
                AsyncRequestType::GetPixels {
                    surface_id,
                    x,
                    y,
                    width,
                    height,
                } => {
                    buf.push(3);
                    buf.push(0);
                    buf.push(0);
                    buf.push(0); // type=3 + pad
                    buf.extend_from_slice(&surface_id.to_le_bytes());
                    buf.extend_from_slice(&x.to_le_bytes());
                    buf.extend_from_slice(&y.to_le_bytes());
                    buf.extend_from_slice(&width.to_le_bytes());
                    buf.extend_from_slice(&height.to_le_bytes());
                }
                AsyncRequestType::ReadM3gImage {
                    image_handle,
                    width,
                    height,
                } => {
                    buf.push(6);
                    buf.push(0);
                    buf.push(0);
                    buf.push(0); // type=6 + pad
                    buf.extend_from_slice(&image_handle.to_le_bytes());
                    buf.extend_from_slice(&width.to_le_bytes());
                    buf.extend_from_slice(&height.to_le_bytes());
                }
                AsyncRequestType::DecodeM3gExternalImage {
                    resource_name,
                    image_bytes,
                } => {
                    buf.push(7);
                    buf.push(0);
                    buf.push(0);
                    buf.push(0); // type=7 + pad
                    let name_bytes = resource_name.as_bytes();
                    buf.extend_from_slice(&(name_bytes.len() as u32).to_le_bytes());
                    buf.extend_from_slice(name_bytes);
                    let padding = (4 - (name_bytes.len() % 4)) % 4;
                    for _ in 0..padding {
                        buf.push(0);
                    }
                    buf.extend_from_slice(&(image_bytes.len() as u32).to_le_bytes());
                }
                AsyncRequestType::TextInput {
                    text_field_ref,
                    constraints,
                    max_size,
                    text,
                } => {
                    buf.push(4);
                    buf.push(0);
                    buf.push(0);
                    buf.push(0); // type=4 + pad
                    buf.extend_from_slice(&text_field_ref.to_le_bytes());
                    buf.extend_from_slice(&constraints.to_le_bytes());
                    buf.extend_from_slice(&max_size.to_le_bytes());
                    let text_bytes = text.as_bytes();
                    buf.extend_from_slice(&(text_bytes.len() as u32).to_le_bytes());
                    buf.extend_from_slice(text_bytes);
                    let padding = (4 - (text_bytes.len() % 4)) % 4;
                    for _ in 0..padding {
                        buf.push(0);
                    }
                }
                AsyncRequestType::DateInput {
                    date_field_ref,
                    mode,
                    initialized,
                    value_millis,
                } => {
                    buf.push(5);
                    buf.push(0);
                    buf.push(0);
                    buf.push(0); // type=5 + pad
                    buf.extend_from_slice(&date_field_ref.to_le_bytes());
                    buf.extend_from_slice(&mode.to_le_bytes());
                    buf.push(if *initialized { 1 } else { 0 });
                    buf.push(0);
                    buf.push(0);
                    buf.push(0);
                    buf.extend_from_slice(&value_millis.to_le_bytes());
                }
                AsyncRequestType::Bluetooth { operation, payload } => {
                    buf.push(8);
                    buf.extend_from_slice(&[0, 0, 0]);
                    for value in [operation, payload] {
                        let bytes = value.as_bytes();
                        buf.extend_from_slice(&(bytes.len() as u32).to_le_bytes());
                        buf.extend_from_slice(bytes);
                        buf.resize(buf.len() + (4 - bytes.len() % 4) % 4, 0);
                    }
                }
            }
        }
        buf
    }

    /// Get the raw image bytes for a pending async request
    pub fn get_async_image_bytes(&self, request_id: u32) -> Option<&[u8]> {
        use vm::AsyncRequestType;
        self.vm
            .get_async_request(request_id)
            .and_then(|req| match &req.request_type {
                AsyncRequestType::DecodeImage { image_bytes, .. }
                | AsyncRequestType::DecodeM3gExternalImage { image_bytes, .. } => {
                    Some(image_bytes.as_slice())
                }
                _ => None,
            })
    }

    /// Resolve an async request with image decode result
    pub fn resolve_async_image(&mut self, request_id: u32, width: i32, height: i32) -> bool {
        self.vm.resolve_async_request_legacy(
            request_id,
            vm::AsyncResult::ImageDecoded { width, height },
        )
    }

    pub fn resolve_async_pixels(&mut self, request_id: u32, pixels: Vec<i32>) -> bool {
        self.vm
            .resolve_async_request_legacy(request_id, vm::AsyncResult::PixelsRead { pixels })
    }

    pub fn resolve_async_m3g_image(
        &mut self,
        request_id: u32,
        width: i32,
        height: i32,
        pixels: Vec<u8>,
    ) -> bool {
        self.vm.resolve_async_request_legacy(
            request_id,
            vm::AsyncResult::M3gImageRead {
                width,
                height,
                pixels,
            },
        )
    }

    /// Resolve an RmsLoad async request with optional data
    pub fn resolve_async_rms_load(&mut self, request_id: u32, data: Option<Vec<u8>>) -> bool {
        self.vm
            .resolve_async_request_legacy(request_id, vm::AsyncResult::RmsLoaded { data })
    }

    /// Resolve an RmsKeys async request with a list of keys
    pub fn resolve_async_rms_keys(&mut self, request_id: u32, keys: Vec<String>) -> bool {
        self.vm
            .resolve_async_request_legacy(request_id, vm::AsyncResult::RmsKeysList { keys })
    }

    pub fn resolve_async_text_input(&mut self, request_id: u32, text: Option<String>) -> bool {
        self.vm
            .resolve_async_request_legacy(request_id, vm::AsyncResult::TextInputCommitted { text })
    }

    pub fn resolve_async_date_input(
        &mut self,
        request_id: u32,
        committed: bool,
        initialized: bool,
        value_millis: i64,
    ) -> bool {
        self.vm.resolve_async_request_legacy(
            request_id,
            vm::AsyncResult::DateInputCommitted {
                initialized,
                value_millis,
                committed,
            },
        )
    }

    pub fn resolve_async_bluetooth(&mut self, request_id: u32, response: String) -> bool {
        self.vm.resolve_async_request_legacy(
            request_id,
            vm::AsyncResult::BluetoothCompleted { response },
        )
    }

    pub fn get_async_state_counts(&self) -> (usize, usize, usize) {
        self.vm.get_async_state_counts()
    }

    /// Resume the thread that was waiting for async.
    /// Returns RunResult::AsyncPending if another async wait is hit, RunResult::Done if done.
    pub(crate) fn resume_async_thread(&mut self) -> RunResult {
        if let Some(tid) = self.async_thread_id.take() {
            let result = self.run_thread_to_completion(tid);
            // Clean up run_queue: run_thread_to_completion may leave terminated tid entries
            self.scheduler.run_queue.retain(|&tid_in_q| {
                self.scheduler.threads.get(&tid_in_q).map_or(false, |t| {
                    t.state == scheduler::virtual_thread::JavaThreadState::Runnable
                })
            });
            match &result {
                RunResult::AsyncPending => {
                    self.async_thread_id = Some(tid);
                    return result;
                }
                _ => {}
            }
            if matches!(result, RunResult::Done | RunResult::StepLimitReached) {
                if let Some(context_id) = self.pending_start_after_init_context_id.take() {
                    return match self.start_midlet(context_id) {
                        Ok(()) => RunResult::Done,
                        Err(msg) if msg == "ASYNC_PENDING" => RunResult::AsyncPending,
                        Err(err) => {
                            self.vm
                                .println(&format!("[ERROR] failed to start MIDlet: {}", err));
                            RunResult::Done
                        }
                    };
                }
            }
            return result;
        }
        RunResult::Done
    }
}

#[derive(Default)]
struct LoadedSuite {
    jar_data: Option<Vec<u8>>,
    class_data: Vec<Vec<u8>>,
    resources: Vec<(String, Vec<u8>)>,
    entries: Vec<MidletDescriptor>,
}

impl LoadedSuite {
    fn manifest_attributes(&self) -> Option<std::collections::HashMap<String, String>> {
        let data = if let Some((_, data)) = self
            .resources
            .iter()
            .find(|(name, _)| name.eq_ignore_ascii_case("META-INF/MANIFEST.MF"))
        {
            data.as_slice()
        } else if let Some(jar) = &self.jar_data {
            let cursor = std::io::Cursor::new(jar.as_slice());
            let mut archive = zip::ZipArchive::new(cursor).ok()?;
            let mut file = archive.by_name("META-INF/MANIFEST.MF").ok()?;
            let mut data = Vec::new();
            std::io::Read::read_to_end(&mut file, &mut data).ok()?;
            let manifest = String::from_utf8_lossy(&data);
            return Some(parse_manifest_attributes(&manifest));
        } else {
            return None;
        };
        let manifest = String::from_utf8_lossy(data);
        Some(parse_manifest_attributes(&manifest))
    }

    fn load_into_core(&self, core: &mut JvmCore) -> Result<(), String> {
        if let Some(jar) = &self.jar_data {
            core.load_jar(jar)?;
        }
        let mut remaining = self.class_data.iter().collect::<Vec<_>>();
        for _ in 0..10 {
            if remaining.is_empty() {
                break;
            }
            let previous_len = remaining.len();
            let mut failed = Vec::new();
            for data in remaining {
                if core.load_class(data).is_err() {
                    failed.push(data);
                }
            }
            if failed.is_empty() {
                remaining = failed;
                break;
            }
            if failed.len() == previous_len {
                return core.load_class(failed[0]).map(|_| ());
            }
            remaining = failed;
        }
        if let Some(data) = remaining.first() {
            core.load_class(data)?;
        }
        for (name, data) in &self.resources {
            core.load_resource(name, data);
        }
        Ok(())
    }
}

pub struct JvmInstance {
    name: String,
    debug: bool,
    display_width: i32,
    display_height: i32,
    gc_enabled: bool,
    speed_multiplier: f64,
    suite: LoadedSuite,
    selector: Option<Box<JvmCore>>,
    app: Option<Box<JvmCore>>,
    current: NonNull<JvmCore>,
    launched: bool,
    next_surface_id: Rc<RefCell<i32>>,
}

impl JvmInstance {
    pub fn new(name: Option<&str>, debug: bool) -> Self {
        let name = name.unwrap_or("default").to_string();
        let next_surface_id = Rc::new(RefCell::new(1));
        Self {
            name: name.clone(),
            debug,
            display_width: 240,
            display_height: 320,
            gc_enabled: true,
            speed_multiplier: 1.0,
            suite: LoadedSuite::default(),
            selector: None,
            app: None,
            current: NonNull::dangling(),
            launched: false,
            next_surface_id,
        }
    }

    fn bind_current_to_selector(&mut self) {
        let selector = self.selector.as_mut().expect("selector VM missing");
        self.current = NonNull::from(selector.as_mut());
        self.launched = true;
    }

    fn bind_current_to_app(&mut self) {
        let app = self.app.as_mut().expect("app VM missing");
        self.current = NonNull::from(app.as_mut());
        self.launched = true;
    }

    fn clear_current(&mut self) {
        self.current = NonNull::dangling();
        self.launched = false;
    }

    #[inline(always)]
    fn current_core_unchecked(&self) -> &JvmCore {
        debug_assert!(self.launched);
        unsafe { self.current.as_ref() }
    }

    #[inline(always)]
    fn current_core_mut_unchecked(&mut self) -> &mut JvmCore {
        debug_assert!(self.launched);
        unsafe { self.current.as_mut() }
    }

    fn current_core(&self) -> Option<&JvmCore> {
        if !self.launched {
            return None;
        }
        Some(unsafe { self.current.as_ref() })
    }

    fn current_core_mut(&mut self) -> Option<&mut JvmCore> {
        if !self.launched {
            return None;
        }
        Some(unsafe { self.current.as_mut() })
    }

    fn selector_ptr(&self) -> Option<NonNull<JvmCore>> {
        self.selector
            .as_ref()
            .map(|selector| NonNull::from(selector.as_ref()))
    }

    fn app_ptr(&self) -> Option<NonNull<JvmCore>> {
        self.app.as_ref().map(|app| NonNull::from(app.as_ref()))
    }

    fn current_is_selector(&self) -> bool {
        self.launched && self.selector_ptr() == Some(self.current)
    }

    fn current_is_app(&self) -> bool {
        self.launched && self.app_ptr() == Some(self.current)
    }

    fn current_save_slot(&self) -> SavedActiveCore {
        if self.current_is_selector() {
            SavedActiveCore::Selector
        } else if self.current_is_app() {
            SavedActiveCore::App
        } else {
            panic!("current JVM core must belong to selector or app");
        }
    }

    pub fn is_debug(&self) -> bool {
        self.debug
    }

    fn configure_core(&self, core: &mut JvmCore) {
        core.vm.display_width = self.display_width;
        core.vm.display_height = self.display_height;
        core.set_gc_enabled(self.gc_enabled);
        core.set_speed_multiplier(self.speed_multiplier);
    }

    fn new_loaded_core(&self, suffix: &str) -> Result<JvmCore, String> {
        let mut core = JvmCore::new(
            Some(&format!("{}-{}", self.name, suffix)),
            self.debug,
            Rc::clone(&self.next_surface_id),
        );
        self.configure_core(&mut core);
        self.suite.load_into_core(&mut core)?;
        Ok(core)
    }

    fn ensure_selector(&mut self) -> Result<(), String> {
        let needs_start = self
            .selector
            .as_ref()
            .map(|selector| selector.midlet_contexts.is_empty())
            .unwrap_or(true);
        if needs_start {
            let mut selector = match self.selector.take() {
                Some(selector) => *selector,
                None => self.new_loaded_core("selector")?,
            };
            self.configure_core(&mut selector);
            let result = selector
                .populate_suite_launcher_static_fields(&self.suite.entries)
                .and_then(|_| {
                    selector.start_midlet_class("com.jsjvm.midp.MidletSuiteSelectorMIDlet")
                });
            self.selector = Some(Box::new(selector));
            self.bind_current_to_selector();
            result?;
        }
        Ok(())
    }

    fn class_name_for_launch_mode(&self, launch_mode: u32) -> Result<String, String> {
        match launch_mode {
            0 => match self.suite.entries.len() {
                0 => Err("No MIDlet entries found in JAR manifest".to_string()),
                _ => Ok(self.suite.entries[0].class_name.clone()),
            },
            n => self
                .suite
                .entries
                .iter()
                .find(|entry| entry.index == n)
                .map(|entry| entry.class_name.clone())
                .ok_or_else(|| format!("MIDlet-{} not found in JAR manifest", n)),
        }
    }

    fn start_app(&mut self, launch_mode: u32) -> Result<(), String> {
        let class_name = self.class_name_for_launch_mode(launch_mode)?;
        let mut app = self.new_loaded_core("app")?;
        let result = app.start_midlet_class(&class_name);
        self.app = Some(Box::new(app));
        self.bind_current_to_app();
        result
    }

    fn main_class_from_manifest(&self) -> Result<String, String> {
        self.suite
            .manifest_attributes()
            .and_then(|attrs| attrs.get("Main-Class").cloned())
            .map(|class_name| class_name.replace('/', "."))
            .filter(|class_name| !class_name.is_empty())
            .ok_or_else(|| "Main-Class not found in JAR manifest".to_string())
    }

    pub fn start_main_class(&mut self, class_name: &str, args: &[String]) -> Result<(), String> {
        let mut app = self.new_loaded_core("main")?;
        let arg_refs = args.iter().map(String::as_str).collect::<Vec<_>>();
        let result = app.start_main(class_name, &arg_refs);
        self.app = Some(Box::new(app));
        self.bind_current_to_app();
        result
    }

    pub fn start_main_from_manifest(&mut self, args: &[String]) -> Result<(), String> {
        let class_name = self.main_class_from_manifest()?;
        self.start_main_class(&class_name, args)
    }

    fn activate_existing_selector_after_app_exit(&mut self) -> i32 {
        let app_output = self.app.as_mut().map(|app| app.get_output());
        if self.ensure_selector().is_err() {
            return -1;
        }
        self.bind_current_to_selector();
        self.app = None;
        if let Some(selector) = self.selector.as_mut() {
            let _ = selector.sync_host_ui_state_after_restore();
            let _ = selector.repaint();
        }
        if let Some(output) = app_output {
            if !output.is_empty() {
                if let Some(selector) = self.selector.as_mut() {
                    selector.vm.text_output.push_str(&output);
                }
            }
        }
        1
    }

    fn process_selector_launch(&mut self) -> Option<i32> {
        if !self.current_is_selector() {
            return None;
        }
        let index = self.selector.as_mut()?.take_pending_suite_launch_index()?;
        match self.start_app(index) {
            Ok(_) => Some(1),
            Err(msg) if msg == "ASYNC_PENDING" => Some(2),
            Err(err) => {
                if let Some(selector) = self.selector.as_mut() {
                    selector.vm.println(&format!(
                        "[ERROR] failed to launch MIDlet-{}: {}",
                        index, err
                    ));
                }
                Some(-1)
            }
        }
    }

    fn process_app_exit(&mut self, status: i32) -> i32 {
        if status == 0 {
            if let Some(core) = self.current_core_mut() {
                core.clear_softkey_labels();
            }
            if self.current_is_app() && self.selector.is_some() {
                return self.activate_existing_selector_after_app_exit();
            }
        }
        status
    }

    pub fn load_jar(&mut self, data: &[u8]) -> Result<Vec<String>, String> {
        self.suite.jar_data = Some(data.to_vec());
        self.suite.class_data.clear();
        self.suite.resources.clear();
        self.suite.entries = self
            .suite
            .manifest_attributes()
            .map(|attrs| midlet_descriptors_from_attributes(&attrs))
            .unwrap_or_default();
        let classes = zip::ZipArchive::new(std::io::Cursor::new(data))
            .map(|mut archive| {
                (0..archive.len())
                    .filter_map(|i| archive.by_index(i).ok().map(|file| file.name().to_string()))
                    .filter(|name| name.ends_with(".class"))
                    .collect::<Vec<_>>()
            })
            .unwrap_or_default();
        if let Some(core) = self.current_core_mut() {
            core.load_jar(data)?;
        }
        Ok(classes)
    }

    pub fn load_class(&mut self, data: &[u8]) -> Result<String, String> {
        if let Some(core) = self.current_core_mut() {
            core.load_class(data)?;
        }
        self.suite.class_data.push(data.to_vec());
        Ok(String::new())
    }

    pub fn load_resource(&mut self, name: &str, data: &[u8]) {
        self.suite.resources.push((name.to_string(), data.to_vec()));
        if let Some(core) = self.current_core_mut() {
            core.load_resource(name, data);
        }
        if name.eq_ignore_ascii_case("META-INF/MANIFEST.MF") {
            let manifest = String::from_utf8_lossy(data);
            let attrs = parse_manifest_attributes(&manifest);
            self.suite.entries = midlet_descriptors_from_attributes(&attrs);
        }
    }

    pub fn set_gc_enabled(&mut self, enabled: bool) {
        self.gc_enabled = enabled;
        if let Some(selector) = self.selector.as_mut() {
            selector.set_gc_enabled(enabled);
        }
        if let Some(app) = self.app.as_mut() {
            app.set_gc_enabled(enabled);
        }
    }

    pub fn set_display_size(&mut self, width: i32, height: i32) {
        self.display_width = width;
        self.display_height = height;
        if let Some(selector) = self.selector.as_mut() {
            selector.vm.display_width = width;
            selector.vm.display_height = height;
        }
        if let Some(app) = self.app.as_mut() {
            app.vm.display_width = width;
            app.vm.display_height = height;
        }
    }

    pub fn set_speed_multiplier(&mut self, speed: f64) {
        self.speed_multiplier = speed;
        if let Some(selector) = self.selector.as_mut() {
            selector.set_speed_multiplier(speed);
        }
        if let Some(app) = self.app.as_mut() {
            app.set_speed_multiplier(speed);
        }
    }

    pub fn start_suite(&mut self, launch_mode: u32) -> Result<(), String> {
        if launch_mode == 0 && self.suite.entries.len() > 1 {
            self.ensure_selector()?;
            self.bind_current_to_selector();
            return Ok(());
        }
        self.start_app(launch_mode)
    }

    pub fn pause_suite(&mut self) -> Result<(), String> {
        let context_id = self
            .current_core()
            .ok_or_else(|| "No active JVM core".to_string())?
            .midlet_contexts
            .keys()
            .next()
            .copied()
            .ok_or_else(|| "No active MIDlet context".to_string())?;
        self.current_core_mut_unchecked().pause_midlet(context_id)
    }

    pub fn destroy_suite(&mut self, unconditional: bool) -> Result<(), String> {
        let context_id = self
            .current_core()
            .ok_or_else(|| "No active JVM core".to_string())?
            .midlet_contexts
            .keys()
            .next()
            .copied()
            .ok_or_else(|| "No active MIDlet context".to_string())?;
        let result = self
            .current_core_mut_unchecked()
            .destroy_midlet(context_id, unconditional);
        if result.is_ok() {
            if let Some(core) = self.current_core_mut() {
                core.clear_softkey_labels();
            }
            if self.current_is_app() && self.selector.is_some() {
                self.activate_existing_selector_after_app_exit();
            }
        }
        result
    }

    pub fn step(&mut self, time_ms: u32) -> i32 {
        if !self.launched {
            return 0;
        }
        let status = self.current_core_mut_unchecked().step(time_ms);
        if let Some(status) = self.process_selector_launch() {
            return status;
        }
        self.process_app_exit(status)
    }

    pub fn foreground_display_id(&self) -> i32 {
        self.current_core()
            .map(|core| core.vm.foreground_display_id)
            .unwrap_or(0)
    }

    pub fn force_gc(&mut self) {
        let Some(core) = self.current_core_mut() else {
            return;
        };
        let threads: Vec<_> = core.scheduler.threads.values().cloned().collect();
        gc::collect(&mut core.vm, &threads);
    }

    pub fn repaint(&mut self) -> Result<(), String> {
        self.current_core_mut()
            .ok_or_else(|| "No active JVM core".to_string())?
            .repaint()
    }

    fn println_to_active(&mut self, text: &str) {
        if let Some(core) = self.current_core_mut() {
            core.vm.println(text);
        }
    }

    pub fn get_output(&mut self) -> String {
        self.current_core_mut()
            .map(|core| core.get_output())
            .unwrap_or_default()
    }

    pub fn get_command_buffer(&mut self) -> Vec<u8> {
        self.current_core_mut()
            .map(|core| core.get_command_buffer())
            .unwrap_or_default()
    }

    pub fn idle_delay(&self) -> u32 {
        self.current_core()
            .map(|core| core.idle_delay())
            .unwrap_or(0)
    }

    pub fn dump_threads(&self) -> String {
        self.current_core()
            .map(|core| core.dump_threads())
            .unwrap_or_default()
    }

    fn encode_save_state_payload<T: Serialize>(
        state: &T,
        display_png_snapshot: Option<vm::DisplaySurfacePngSnapshot>,
        compression: flate2::Compression,
    ) -> Vec<u8> {
        use flate2::write::DeflateEncoder;
        use std::io::Write;

        let uncompressed = bincode::serialize(state).unwrap_or_default();
        let mut encoder = DeflateEncoder::new(Vec::new(), compression);
        let _ = encoder.write_all(&uncompressed);
        let compressed = encoder.finish().unwrap_or_default();

        let timestamp = call_host_current_time_millis() as u64;
        let display_png = display_png_snapshot.as_ref().filter(|snapshot| {
            snapshot.width > 0 && snapshot.height > 0 && !snapshot.data.is_empty()
        });
        let section_count = if display_png.is_some() { 2u32 } else { 1u32 };
        let section_table_size = (section_count as usize) * SAVE_SECTION_ENTRY_SIZE;
        let vm_state_offset = SAVE_FIXED_HEADER_SIZE + section_table_size;
        let display_png_offset = vm_state_offset + compressed.len();

        let mut output = Vec::with_capacity(
            display_png_offset + display_png.map_or(0, |snapshot| snapshot.data.len()),
        );
        output.extend_from_slice(SAVE_MAGIC);
        output.extend_from_slice(&SAVE_VERSION.to_le_bytes());
        output.extend_from_slice(&timestamp.to_le_bytes());
        output.extend_from_slice(&section_count.to_le_bytes());
        JvmCore::write_save_section_entry(
            &mut output,
            SaveSectionEntry {
                section_type: SAVE_SECTION_VM_STATE,
                offset: vm_state_offset as u32,
                length: compressed.len() as u32,
                param0: uncompressed.len() as u32,
                param1: 0,
            },
        );
        if let Some(snapshot) = display_png {
            JvmCore::write_save_section_entry(
                &mut output,
                SaveSectionEntry {
                    section_type: SAVE_SECTION_DISPLAY_SURFACE_PNG,
                    offset: display_png_offset as u32,
                    length: snapshot.data.len() as u32,
                    param0: snapshot.width as u32,
                    param1: snapshot.height as u32,
                },
            );
        }
        output.extend_from_slice(&compressed);
        if let Some(snapshot) = display_png {
            output.extend_from_slice(&snapshot.data);
        }
        output
    }

    fn decode_save_state_payload<T: for<'de> Deserialize<'de>>(data: &[u8]) -> Option<T> {
        use flate2::read::DeflateDecoder;
        use std::io::Read;

        let meta = match JvmCore::parse_save_meta(data) {
            Some(meta) if meta.version == SAVE_VERSION => meta,
            _ => return None,
        };
        let vm_state_offset = meta.vm_state_offset as usize;
        let vm_state_len = meta.vm_state_len as usize;
        let vm_state_end = vm_state_offset.checked_add(vm_state_len)?;
        if vm_state_end > data.len() {
            return None;
        }
        let compressed = &data[vm_state_offset..vm_state_end];
        let mut decoder = DeflateDecoder::new(compressed);
        let mut uncompressed = Vec::with_capacity(meta.vm_state_uncompressed_len as usize);
        if decoder.read_to_end(&mut uncompressed).is_err() {
            return None;
        }
        bincode::deserialize(&uncompressed).ok()
    }

    pub fn save_state(
        &mut self,
        display_png_snapshot: Option<vm::DisplaySurfacePngSnapshot>,
    ) -> Vec<u8> {
        let selector = self
            .selector
            .as_mut()
            .map(|selector| selector.capture_core_state());
        let app = self.app.as_mut().map(|app| app.capture_core_state());
        let state = InstanceSaveState {
            active: self.current_save_slot(),
            next_surface_id: *self.next_surface_id.borrow(),
            selector,
            app,
        };
        Self::encode_save_state_payload(
            &state,
            display_png_snapshot,
            flate2::Compression::default(),
        )
    }

    /// Persistable snapshot optimized for periodic auto-save. It keeps the
    /// regular save container format, but avoids GC/heap compaction and uses
    /// fast Deflate compression. Display PNG capture is intentionally omitted
    /// by the caller because restored VM state can repaint surface 0.
    pub fn save_state_fast(&mut self) -> Vec<u8> {
        let state = self.capture_for_rewind();
        Self::encode_save_state_payload(state.as_ref(), None, flate2::Compression::fast())
    }

    fn restore_core_slot(&self, suffix: &str, state: CoreSaveState) -> Option<Box<JvmCore>> {
        let mut core = self.new_loaded_core(suffix).ok()?;
        core.restore_core_state(state).then_some(Box::new(core))
    }

    fn advance_next_surface_id_past_core_surfaces(&mut self) {
        let mut next_surface_id = *self.next_surface_id.borrow();
        for core in [&self.selector, &self.app].into_iter().flatten() {
            for &surface_id in core.vm.image_surface_map.values() {
                if surface_id > 0 {
                    next_surface_id = next_surface_id.max(surface_id + 1);
                }
            }
        }
        *self.next_surface_id.borrow_mut() = next_surface_id.max(1);
    }

    pub fn load_state(&mut self, data: &[u8]) -> bool {
        let Some(state) = Self::decode_save_state_payload::<InstanceSaveState>(data) else {
            return false;
        };

        let selector = match state.selector {
            Some(core_state) => match self.restore_core_slot("selector", core_state) {
                Some(core) => Some(core),
                None => return false,
            },
            None => None,
        };
        let app = match state.app {
            Some(core_state) => match self.restore_core_slot("app", core_state) {
                Some(core) => Some(core),
                None => return false,
            },
            None => None,
        };
        let bind_active: fn(&mut JvmInstance) = match state.active {
            SavedActiveCore::Selector => {
                if selector.is_none() {
                    return false;
                }
                JvmInstance::bind_current_to_selector
            }
            SavedActiveCore::App => {
                if app.is_none() {
                    return false;
                }
                JvmInstance::bind_current_to_app
            }
        };

        self.clear_current();
        self.selector = selector;
        self.app = app;
        *self.next_surface_id.borrow_mut() = state.next_surface_id;
        self.advance_next_surface_id_past_core_surfaces();
        bind_active(self);
        let (display_width, display_height, speed_multiplier) = {
            let core = self.current_core_unchecked();
            (
                core.vm.display_width,
                core.vm.display_height,
                core.vm.speed_multiplier,
            )
        };
        self.display_width = display_width;
        self.display_height = display_height;
        self.speed_multiplier = speed_multiplier;
        true
    }

    pub fn prepare_image_surface_snapshot_targets(
        &mut self,
    ) -> Vec<vm::ImageSurfaceSnapshotTarget> {
        let mut targets = Vec::new();
        if let Some(selector) = self.selector.as_mut() {
            targets.extend(selector.prepare_image_surface_snapshot_targets());
        }
        if let Some(app) = self.app.as_mut() {
            targets.extend(app.prepare_image_surface_snapshot_targets());
        }
        targets.sort_by_key(|target| target.surface_id);
        targets.dedup_by(|a, b| {
            a.surface_id == b.surface_id && a.width == b.width && a.height == b.height
        });
        targets
    }

    pub fn image_surface_snapshot_targets_for_rewind(&self) -> Vec<vm::ImageSurfaceSnapshotTarget> {
        let mut targets = Vec::new();
        if let Some(selector) = self.selector.as_ref() {
            targets.extend(selector.image_surface_snapshot_targets_for_rewind());
        }
        if let Some(app) = self.app.as_ref() {
            targets.extend(app.image_surface_snapshot_targets_for_rewind());
        }
        targets.sort_by_key(|target| target.surface_id);
        targets.dedup_by(|a, b| {
            a.surface_id == b.surface_id && a.width == b.width && a.height == b.height
        });
        targets
    }

    pub fn clear_pending_image_surface_snapshots(&mut self) {
        if let Some(selector) = self.selector.as_mut() {
            selector.clear_pending_image_surface_snapshots();
        }
        if let Some(app) = self.app.as_mut() {
            app.clear_pending_image_surface_snapshots();
        }
    }

    pub fn add_pending_image_surface_snapshot(
        &mut self,
        surface_id: i32,
        width: i32,
        height: i32,
        pixels: Vec<i32>,
    ) {
        let mut added = false;
        if let Some(selector) = self.selector.as_mut() {
            let matches_selector = selector
                .image_surface_snapshot_targets_for_rewind()
                .iter()
                .any(|target| {
                    target.surface_id == surface_id
                        && target.width == width
                        && target.height == height
                });
            if matches_selector {
                selector.add_pending_image_surface_snapshot(
                    surface_id,
                    width,
                    height,
                    pixels.clone(),
                );
                added = true;
            }
        }
        if let Some(app) = self.app.as_mut() {
            let matches_app =
                app.image_surface_snapshot_targets_for_rewind()
                    .iter()
                    .any(|target| {
                        target.surface_id == surface_id
                            && target.width == width
                            && target.height == height
                    });
            if matches_app {
                app.add_pending_image_surface_snapshot(surface_id, width, height, pixels.clone());
                added = true;
            }
        }
        if !added {
            if let Some(core) = self.current_core_mut() {
                core.add_pending_image_surface_snapshot(surface_id, width, height, pixels);
            }
        }
    }

    fn capture_for_rewind(&mut self) -> Box<InstanceSaveState> {
        let selector = self
            .selector
            .as_mut()
            .map(|selector| selector.capture_core_state_for_rewind());
        let app = self
            .app
            .as_mut()
            .map(|app| app.capture_core_state_for_rewind());
        Box::new(InstanceSaveState {
            active: self.current_save_slot(),
            next_surface_id: *self.next_surface_id.borrow(),
            selector,
            app,
        })
    }

    fn restore_from_rewind(&mut self, state: InstanceSaveState) -> bool {
        let selector = match state.selector {
            Some(core_state) => {
                let mut core = match self.new_loaded_core("selector") {
                    Ok(core) => core,
                    Err(_) => return false,
                };
                if !core.restore_core_state_from_rewind(core_state) {
                    return false;
                }
                Some(Box::new(core))
            }
            None => None,
        };
        let app = match state.app {
            Some(core_state) => {
                let mut core = match self.new_loaded_core("app") {
                    Ok(core) => core,
                    Err(_) => return false,
                };
                if !core.restore_core_state_from_rewind(core_state) {
                    return false;
                }
                Some(Box::new(core))
            }
            None => None,
        };
        let bind_active: fn(&mut JvmInstance) = match state.active {
            SavedActiveCore::Selector => {
                if selector.is_none() {
                    return false;
                }
                JvmInstance::bind_current_to_selector
            }
            SavedActiveCore::App => {
                if app.is_none() {
                    return false;
                }
                JvmInstance::bind_current_to_app
            }
        };

        self.clear_current();
        self.selector = selector;
        self.app = app;
        *self.next_surface_id.borrow_mut() = state.next_surface_id;
        self.advance_next_surface_id_past_core_surfaces();
        bind_active(self);
        let (display_width, display_height, speed_multiplier) = {
            let core = self.current_core_unchecked();
            (
                core.vm.display_width,
                core.vm.display_height,
                core.vm.speed_multiplier,
            )
        };
        self.display_width = display_width;
        self.display_height = display_height;
        self.speed_multiplier = speed_multiplier;
        true
    }

    pub fn take_pending_resource_image_restores(&mut self) -> Vec<u8> {
        self.current_core_mut()
            .map(|core| core.take_pending_resource_image_restores())
            .unwrap_or_default()
    }

    pub fn take_pending_derived_image_restores(&mut self) -> Vec<u8> {
        self.current_core_mut()
            .map(|core| core.take_pending_derived_image_restores())
            .unwrap_or_default()
    }

    pub fn claim_pending_async(&mut self, max_count: usize) -> Vec<u8> {
        self.current_core_mut()
            .map(|core| core.claim_pending_async(max_count))
            .unwrap_or_default()
    }

    pub fn get_async_state_counts(&self) -> (usize, usize, usize) {
        self.current_core()
            .map(|core| core.get_async_state_counts())
            .unwrap_or((0, 0, 0))
    }

    pub fn get_async_image_bytes(&self, request_id: u32) -> Option<&[u8]> {
        self.current_core()
            .and_then(|core| core.get_async_image_bytes(request_id))
    }

    pub fn resolve_async_image(&mut self, request_id: u32, width: i32, height: i32) {
        if let Some(core) = self.current_core_mut() {
            core.resolve_async_image(request_id, width, height);
        }
    }

    pub fn resolve_async_pixels(&mut self, request_id: u32, pixels: Vec<i32>) {
        if let Some(core) = self.current_core_mut() {
            core.resolve_async_pixels(request_id, pixels);
        }
    }

    pub fn resolve_async_m3g_image(
        &mut self,
        request_id: u32,
        width: i32,
        height: i32,
        pixels: Vec<u8>,
    ) {
        if let Some(core) = self.current_core_mut() {
            core.resolve_async_m3g_image(request_id, width, height, pixels);
        }
    }

    pub fn resolve_async_rms_load(&mut self, request_id: u32, data: Option<Vec<u8>>) {
        if let Some(core) = self.current_core_mut() {
            core.resolve_async_rms_load(request_id, data);
        }
    }

    pub fn resolve_async_rms_keys(&mut self, request_id: u32, keys: Vec<String>) {
        if let Some(core) = self.current_core_mut() {
            core.resolve_async_rms_keys(request_id, keys);
        }
    }

    pub fn resolve_async_text_input(&mut self, request_id: u32, text: Option<String>) {
        if let Some(core) = self.current_core_mut() {
            core.resolve_async_text_input(request_id, text);
        }
    }

    pub fn resolve_async_date_input(
        &mut self,
        request_id: u32,
        committed: bool,
        initialized: bool,
        value_millis: i64,
    ) {
        if let Some(core) = self.current_core_mut() {
            core.resolve_async_date_input(request_id, committed, initialized, value_millis);
        }
    }

    pub fn resolve_async_bluetooth(&mut self, request_id: u32, response: String) {
        if let Some(core) = self.current_core_mut() {
            core.resolve_async_bluetooth(request_id, response);
        }
    }

    fn resume_async_thread(&mut self) -> RunResult {
        if !self.launched {
            return RunResult::Done;
        }
        let result = self.current_core_mut_unchecked().resume_async_thread();
        let app_destroy_completed = matches!(result, RunResult::Done)
            && self.current_is_app()
            && self.selector.is_some()
            && self
                .app
                .as_ref()
                .map(|app| app.midlet_contexts.is_empty())
                .unwrap_or(false);
        if app_destroy_completed {
            self.process_app_exit(0);
        }
        result
    }

    pub fn push_native_event(&mut self, event: vm::NativeEventData) {
        if let Some(core) = self.current_core_mut() {
            core.push_native_event(event);
        }
    }

    #[cfg(test)]
    fn write_save_section_entry(output: &mut Vec<u8>, entry: SaveSectionEntry) {
        JvmCore::write_save_section_entry(output, entry);
    }
}

include!("runtime/ffi.rs");

#[cfg(test)]
include!("runtime/tests.rs");
