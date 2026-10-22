mod command;

pub use command::{cmd, GraphicsState, NativeEventData};

use serde::{Deserialize, Deserializer, Serialize, Serializer};
use std::cell::RefCell;
use std::collections::HashMap;
use std::collections::HashSet;
use std::collections::VecDeque;
use std::ops::Range;
use std::rc::Rc;

use crate::call_host_get_locale;
use crate::classfile::class_repo::ClassRepository;
use crate::classfile::types::*;
use crate::heap::{Heap, NULL};
use crate::interpreter::native::m3g::resource_store::M3gResourceStore;
use crate::interpreter::native::registry::NativeRegistry;
use crate::scheduler::monitor::CoopMonitor;
use crate::scheduler::Scheduler;

/// Static field storage matching PluotSorbet's `staticObjectAddresses[classId]` pattern.
///
/// Each class's static fields are stored as a heap pseudo-object, addressed by class ID.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StaticFields {
    /// staticObjectAddresses: classId → heap address of the static fields pseudo-object
    addrs: Vec<u32>,
    /// Reverse map for name-based lookup (used by existing call sites)
    name_to_id: HashMap<String, u32>,
}

impl StaticFields {
    pub fn new() -> Self {
        Self {
            addrs: Vec::new(),
            name_to_id: HashMap::new(),
        }
    }

    pub fn ensure_class(
        &mut self,
        heap: &mut Heap,
        class_name: &str,
        size_of_static_fields: u32,
        class_id: u32,
    ) {
        if self.name_to_id.contains_key(class_name) {
            return;
        }
        let addr = heap.alloc_object(class_id, size_of_static_fields);
        // Ensure the Vec is large enough for class_id indexing
        let id = class_id as usize;
        if id >= self.addrs.len() {
            self.addrs.resize(id + 1, NULL);
        }
        self.addrs[id] = addr;
        self.name_to_id.insert(class_name.to_string(), class_id);
    }

    /// Get static field base address by class name (compatible API).
    pub fn get_addr(&self, class_name: &str) -> u32 {
        self.name_to_id
            .get(class_name)
            .and_then(|&id| self.addrs.get(id as usize).copied())
            .unwrap_or(NULL)
    }

    /// Get static field base address by class ID (PluotSorbet-style).
    pub fn get_addr_by_id(&self, class_id: u32) -> u32 {
        self.addrs.get(class_id as usize).copied().unwrap_or(NULL)
    }

    /// Iterate over all (class_id, heap_addr) pairs for GC root scanning
    pub fn iter_entries(&self) -> impl Iterator<Item = (u32, u32)> + '_ {
        self.name_to_id.values().map(move |&class_id| {
            let addr = self.addrs.get(class_id as usize).copied().unwrap_or(NULL);
            (class_id, addr)
        })
    }
}

impl Default for StaticFields {
    fn default() -> Self {
        Self::new()
    }
}

/// VM-owned backing storage for JSR-239 direct NIO buffers.
///
/// Java code retains offsets into this arena rather than host pointers, so
/// growing the Vec never invalidates an existing direct-buffer address.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DirectBufferArena {
    bytes: Vec<u8>,
}

impl DirectBufferArena {
    const ADDRESS_ALIGNMENT: usize = 4;

    pub fn allocate(&mut self, capacity: usize) -> Option<u32> {
        let aligned_start = self.bytes.len().checked_add(Self::ADDRESS_ALIGNMENT - 1)?
            & !(Self::ADDRESS_ALIGNMENT - 1);
        let storage_len = capacity.max(1);
        let end = aligned_start.checked_add(storage_len)?;
        if end > i32::MAX as usize {
            return None;
        }
        self.bytes
            .try_reserve_exact(end.checked_sub(self.bytes.len())?)
            .ok()?;
        self.bytes.resize(end, 0);
        Some(aligned_start as u32)
    }

    pub fn read_i8(&self, address: i32) -> Option<i8> {
        self.range(address, 1)
            .map(|range| self.bytes[range.start] as i8)
    }

    pub fn write_i8(&mut self, address: i32, value: i8) -> bool {
        let Some(range) = self.range(address, 1) else {
            return false;
        };
        self.bytes[range.start] = value as u8;
        true
    }

    pub fn read_i16(&self, address: i32) -> Option<i16> {
        self.read_exact::<2>(address).map(i16::from_le_bytes)
    }

    pub fn write_i16(&mut self, address: i32, value: i16) -> bool {
        self.write_exact(address, value.to_le_bytes())
    }

    pub fn read_i32(&self, address: i32) -> Option<i32> {
        self.read_exact::<4>(address).map(i32::from_le_bytes)
    }

    pub fn write_i32(&mut self, address: i32, value: i32) -> bool {
        self.write_exact(address, value.to_le_bytes())
    }

    pub fn read_f32(&self, address: i32) -> Option<f32> {
        self.read_exact::<4>(address)
            .map(|bytes| f32::from_bits(u32::from_le_bytes(bytes)))
    }

    pub fn write_f32(&mut self, address: i32, value: f32) -> bool {
        self.write_exact(address, value.to_bits().to_le_bytes())
    }

    pub fn copy_within(
        &mut self,
        source_address: i32,
        destination_address: i32,
        len: usize,
    ) -> bool {
        let Some(source) = self.range(source_address, len) else {
            return false;
        };
        let Some(destination) = self.range(destination_address, len) else {
            return false;
        };
        self.bytes.copy_within(source, destination.start);
        true
    }

    pub fn copy_to_slice(&self, address: i32, destination: &mut [u8]) -> bool {
        let Some(source) = self.range(address, destination.len()) else {
            return false;
        };
        destination.copy_from_slice(&self.bytes[source]);
        true
    }

    pub fn copy_from_slice(&mut self, address: i32, source: &[u8]) -> bool {
        let Some(destination) = self.range(address, source.len()) else {
            return false;
        };
        self.bytes[destination].copy_from_slice(source);
        true
    }

    pub fn bytes(&self, address: i32, len: usize) -> Option<&[u8]> {
        self.range(address, len).map(|range| &self.bytes[range])
    }

    fn read_exact<const N: usize>(&self, address: i32) -> Option<[u8; N]> {
        let range = self.range(address, N)?;
        self.bytes[range].try_into().ok()
    }

    fn write_exact<const N: usize>(&mut self, address: i32, value: [u8; N]) -> bool {
        self.copy_from_slice(address, &value)
    }

    fn range(&self, address: i32, len: usize) -> Option<Range<usize>> {
        let start = usize::try_from(address).ok()?;
        if start == 0 {
            return None;
        }
        let end = start.checked_add(len)?;
        (end <= self.bytes.len()).then_some(start..end)
    }
}

impl Default for DirectBufferArena {
    fn default() -> Self {
        Self { bytes: vec![0] }
    }
}

/// Decoded Mascot Capsule texture owned by the Rust Mascot resource store.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MascotTextureImage {
    pub width: u32,
    pub height: u32,
    pub rgba: Vec<u8>,
}

/// One queued Mascot figure, split into the two depth/blending passes expected
/// by the Mascot Capsule renderer.
#[derive(Clone, Debug, Default)]
pub struct MascotFigureDraw {
    pub opaque_packets: Vec<u8>,
    pub transparent_packets: Vec<u8>,
    pub texture_handles: Vec<i32>,
}

fn default_next_mascot_texture_handle() -> i32 {
    1
}

/// Async request types for JS-side processing
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum AsyncRequestType {
    DecodeImage {
        surface_id: i32,
        image_bytes: Vec<u8>,
    },
    GetPixels {
        surface_id: i32,
        x: i32,
        y: i32,
        width: i32,
        height: i32,
    },
    ReadM3gImage {
        image_handle: u32,
        width: i32,
        height: i32,
    },
    DecodeM3gExternalImage {
        resource_name: String,
        image_bytes: Vec<u8>,
    },
    RmsLoad {
        key: String,
    },
    RmsKeys,
    TextInput {
        text_field_ref: u32,
        constraints: i32,
        max_size: i32,
        text: String,
    },
    DateInput {
        date_field_ref: u32,
        mode: i32,
        initialized: bool,
        value_millis: i64,
    },
    Bluetooth {
        operation: String,
        payload: String,
    },
}

/// Async request lifecycle state owned by the VM.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum AsyncRequestState {
    Pending,
    Claimed,
    Resolved,
}

/// A VM-owned async request.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AsyncRequest {
    pub request_type: AsyncRequestType,
    pub thread_id: u32,
}

/// Result of an async operation resolved by JS
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum AsyncResult {
    ImageDecoded {
        width: i32,
        height: i32,
    },
    PixelsRead {
        pixels: Vec<i32>,
    },
    M3gImageRead {
        width: i32,
        height: i32,
        pixels: Vec<u8>,
    },
    RmsLoaded {
        data: Option<Vec<u8>>,
    },
    RmsKeysList {
        keys: Vec<String>,
    },
    TextInputCommitted {
        text: Option<String>,
    },
    DateInputCommitted {
        initialized: bool,
        value_millis: i64,
        committed: bool,
    },
    BluetoothCompleted {
        response: String,
    },
}

/// Full async request entry tracked by the VM.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AsyncRequestEntry {
    pub request: AsyncRequest,
    pub state: AsyncRequestState,
    pub result: Option<AsyncResult>,
}

pub struct SharedVm {
    pub heap: Heap,
    pub class_repo: ClassRepository,
    pub static_fields: StaticFields,
    pub monitors: HashMap<u32, CoopMonitor>,
    pub class_objects: HashMap<u32, String>,
    /// Reverse map: class name → Class object reference (for Class.forName caching)
    pub class_objects_by_name: HashMap<String, u32>,
    /// Java system properties (set by System.setProperty, read by System.getProperty)
    pub system_properties: HashMap<String, String>,
    /// Unified binary command buffer for ALL VM→JS communication.
    pub command_buffer: Vec<u8>,
    /// Byte range and state of the last shape stream. It stays appendable only
    /// while no non-shape command has been written after it.
    last_shape_stream: Option<(usize, usize, GraphicsState)>,
    /// Byte range, state and source surface of the last image stream.
    last_image_stream: Option<(usize, usize, GraphicsState, i32)>,
    /// While Graphics3D holds a bound target, M3G commands are diverted to
    /// this pending buffer so any LCDUI commands issued in the same window
    /// (undefined per JSR-184 §5.5, but a real MIDlet may still call them)
    /// are painted first and the M3G work overlays on top. Flushed into
    /// `command_buffer` by Graphics3D.releaseTarget().
    pub m3g_pending_command_buffer: Option<Vec<u8>>,
    /// Separate text output accumulator for get_output() (test-only).
    /// println() writes to both command_buffer and text_output so that
    /// get_output() can retrieve text without consuming the command buffer.
    pub text_output: String,
    /// Tracks whether the LCDUI overlay is currently visible on the JS side.
    /// Used to avoid sending redundant LCDUI_SET_VISIBLE commands every frame.
    pub lcdui_visible: bool,
    pub trace_output: String,
    pub step_count: u64,
    pub debug: bool,
    pub midp_display_ref: u32,
    pub graphics_states: HashMap<u32, GraphicsState>,
    /// Backing storage for JSR-239 direct ByteBuffer instances.
    pub direct_buffer_arena: DirectBufferArena,
    pub mascot_textures: HashMap<i32, MascotTextureImage>,
    pub mascot_texture_owners: HashMap<u32, i32>,
    pub mascot_uploaded_textures: HashSet<i32>,
    pub mascot_texture_reset_pending: bool,
    /// Figures submitted through Graphics3D.renderFigure. They remain native
    /// transient state until Graphics3D.flush replays all opaque work before
    /// all transparent work.
    pub mascot_pending_figures: Vec<MascotFigureDraw>,
    pub next_mascot_texture_handle: i32,
    next_native_id_counter: u32,
    display_surface_created: bool,
    pub resources: HashMap<String, Rc<Vec<u8>>>,
    /// Non-class JAR entries are indexed without inflation and decoded on first access.
    lazy_resource_jars: Vec<Rc<Vec<u8>>>,
    lazy_resource_entries: HashMap<String, usize>,
    pub resource_decoders: HashMap<u32, ResourceDecoder>,
    next_decoder_id: u32,
    pub media_players: HashMap<i32, MediaPlayerState>,
    /// Maps Player object ref → media player id
    pub player_obj_to_id: HashMap<u32, i32>,
    /// Maps Nokia Sound object ref → media player id
    pub nokia_sound_handles: HashMap<u32, i32>,
    next_media_player_id: i32,
    surface_id_allocator: Rc<RefCell<i32>>,
    /// Maps ImageData object ref → surface_id
    pub image_surface_map: HashMap<u32, i32>,
    /// Maps surface_id → (width, height) in physical pixels.
    pub surface_dims: HashMap<i32, (i32, i32)>,
    /// Tracks surfaces that can be recreated from JAR resources.
    pub image_surface_restore_sources: HashMap<u32, ImageSurfaceRestoreSource>,
    pub display_width: i32,
    pub display_height: i32,
    /// Async bridge fields
    pub async_request_counter: u32,
    pub async_requests: HashMap<u32, AsyncRequestEntry>,
    /// Pending notify wakeups: (obj_id, thread_id) pairs from notify/notifyAll.
    /// Processed by the main loop to move threads from Waiting to Runnable.
    pub pending_notify_wakeups: Vec<(u32, u32, u32)>,
    /// Threads that received monitor ownership via exit() transfer during this step
    pub monitor_transfer_wakeups: Vec<u32>,
    /// RMS in-memory store (legacy high-level interception — kept for compatibility)
    pub rms_stores: HashMap<String, RmsStore>,
    pub rms_obj_to_name: HashMap<u32, String>,
    /// RMS record listeners: store object ref -> list of listener object refs
    pub rms_listeners: HashMap<u32, Vec<u32>>,

    /// Cursor position for media list iterators (content types, protocols)
    pub media_list_cursors: std::collections::HashMap<i32, usize>,
    pub media_list_next_id: i32,
    /// Weak/soft reference map: reference object addr → referent addr
    pub reference_map: HashMap<u32, u32>,
    /// Throwable backtrace storage: throwable object addr → list of (class_name, method_name)
    pub throwable_backtraces: HashMap<u32, Vec<(String, String)>>,
    /// Exception thrown from native/intercept code, to be handled by interpreter
    pub pending_exception: Option<u32>,
    /// Exception description string (class_name + optional message) to be thrown by interpreter
    pub pending_exception_str: Option<String>,
    /// Pending thread spawns: Java Thread object refs to start
    pub pending_thread_spawns: Vec<u32>,
    /// Monitor depth for currently executing thread (used by interpreter).
    /// step() loads from thread.held_monitor_count before execution, stores back after.
    pub current_monitor_depth: u32,
    /// Java Thread object → scheduler thread ID mapping (for isAlive/join)
    pub thread_object_map: HashMap<u32, u32>,
    /// Reverse: scheduler thread ID → Java Thread object ref
    pub thread_id_to_object: HashMap<u32, u32>,
    /// Set of Java Thread object refs that are currently alive
    pub alive_threads: std::collections::HashSet<u32>,
    /// Set of Java Thread object refs that have been interrupted
    pub interrupted_threads: std::collections::HashSet<u32>,
    /// Cached main thread object for Thread.currentThread()
    pub main_thread_obj: u32,
    /// Virtual time in ms, synchronized from the scheduler
    pub virtual_time_ms: u64,
    /// Speed multiplier for time-based operations (1.0 = normal speed)
    pub speed_multiplier: f64,
    /// Host monotonic time corresponding to `effective_time_base_ms`.
    pub host_time_base_ms: u64,
    /// Effective time when `host_time_base_ms` was recorded.
    pub effective_time_base_ms: u64,
    /// Accumulated time offset from step(time_ms) calls (synced from Scheduler).
    pub time_offset_ms: u64,
    /// Return value saved by the interpreter when a thread's last frame returns
    /// via IRETURN/FRETURN/ARETURN. Read by JvmInstance after run_thread_to_completion.
    pub last_thread_return_value: u32,
    /// Native event queue for MIDP NativeEventMonitor.
    pub native_event_queue: VecDeque<NativeEventData>,
    /// Thread ID of the NativeEventMonitor thread (set when it first calls waitForNativeEvent).
    pub native_event_monitor_tid: Option<u32>,
    /// The MIDP display ID of the current foreground display (set by gainedForeground0).
    pub foreground_display_id: i32,
    /// Native method registry: ID-based dispatch table for zero-string-comparison native calls.
    pub native_registry: NativeRegistry,
    /// Resource-backed image surfaces that should be redrawn by the host after load_state().
    pub pending_resource_image_restores: Vec<ResourceImageSurfaceSnapshot>,
    /// Image surfaces reconstructed from another surface after load_state().
    pub pending_derived_image_restores: Vec<DerivedImageSurfaceSnapshot>,
    /// Flag to force GC on next allocation check (set by garbageCollectImages / System.gc)
    pub force_gc: bool,
    /// Flag set when MIDlet calls notifyDestroyed() — signals that the app wants to exit.
    pub midlet_destroyed: bool,
    /// Pre-allocated OutOfMemoryError object to use when heap is too full to allocate one
    pub preallocated_oom: u32,
    /// M3G resource store for wasm stub mode (command buffer based rendering)
    pub m3g_resources: M3gResourceStore,
    pub m3g_interface_handle: usize,
}

const REPORTED_TOTAL_MEMORY: u64 = 2 * 1024 * 1024;

#[derive(Serialize, Deserialize)]
struct ResourceDecoderSnapshot {
    name: String,
    pos: usize,
}

#[derive(Debug, Clone)]
pub struct ResourceDecoder {
    pub name: String,
    pub data: Rc<Vec<u8>>,
    pub pos: usize,
}

impl Serialize for ResourceDecoder {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        ResourceDecoderSnapshot {
            name: self.name.clone(),
            pos: self.pos,
        }
        .serialize(serializer)
    }
}

impl<'de> Deserialize<'de> for ResourceDecoder {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let snapshot = ResourceDecoderSnapshot::deserialize(deserializer)?;
        Ok(Self {
            name: snapshot.name,
            data: Rc::new(Vec::new()),
            pos: snapshot.pos,
        })
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct MediaPlayerState {
    pub volume: i32,
    pub muted: bool,
    pub state: i32,
    pub locator: String,
    pub content_type: String,
    pub data: Option<Vec<u8>>,
    pub listeners: Vec<u32>,
    pub media_time_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RmsStore {
    pub records: HashMap<i32, Vec<u8>>,
    pub next_record_id: i32,
    pub version: i32,
    pub last_modified: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ImageSurfaceRestoreSource {
    JarResource {
        name: String,
    },
    ImmutableCopy {
        source_image_data_ref: u32,
    },
    ImmutableRegion {
        source_image_data_ref: u32,
        x: i32,
        y: i32,
        width: i32,
        height: i32,
        transform: i32,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResourceImageSurfaceSnapshot {
    pub surface_id: i32,
    pub width: i32,
    pub height: i32,
    pub resource_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DerivedImageSurfaceSnapshot {
    pub surface_id: i32,
    pub width: i32,
    pub height: i32,
    pub source_surface_id: i32,
    pub x: i32,
    pub y: i32,
    pub region_width: i32,
    pub region_height: i32,
    pub transform: i32,
}

#[derive(Serialize, Deserialize)]
struct ImageSurfaceSnapshotSerde {
    surface_id: i32,
    width: i32,
    height: i32,
    encoding: u8,
    payload: Vec<u8>,
}

#[derive(Debug, Clone)]
pub struct ImageSurfaceSnapshot {
    pub surface_id: i32,
    pub width: i32,
    pub height: i32,
    pub pixels: Vec<i32>,
}

#[derive(Debug, Clone)]
pub struct DisplaySurfacePngSnapshot {
    pub width: i32,
    pub height: i32,
    pub data: Vec<u8>,
}

#[derive(Debug, Clone, Copy)]
pub struct ImageSurfaceSnapshotTarget {
    pub surface_id: i32,
    pub width: i32,
    pub height: i32,
}

impl Serialize for ImageSurfaceSnapshot {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        let raw_payload = encode_surface_pixels_raw(&self.pixels);
        let rle_payload = encode_surface_pixels_rle(&self.pixels);
        let (encoding, payload) = if rle_payload.len() < raw_payload.len() {
            (1u8, rle_payload)
        } else {
            (0u8, raw_payload)
        };

        ImageSurfaceSnapshotSerde {
            surface_id: self.surface_id,
            width: self.width,
            height: self.height,
            encoding,
            payload,
        }
        .serialize(serializer)
    }
}

impl<'de> Deserialize<'de> for ImageSurfaceSnapshot {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let snapshot = ImageSurfaceSnapshotSerde::deserialize(deserializer)?;
        let expected_pixels = (snapshot.width as usize).saturating_mul(snapshot.height as usize);
        let pixels = match snapshot.encoding {
            0 => decode_surface_pixels_raw(&snapshot.payload, expected_pixels)
                .map_err(serde::de::Error::custom)?,
            1 => decode_surface_pixels_rle(&snapshot.payload, expected_pixels)
                .map_err(serde::de::Error::custom)?,
            _ => return Err(serde::de::Error::custom("unknown image surface encoding")),
        };
        Ok(Self {
            surface_id: snapshot.surface_id,
            width: snapshot.width,
            height: snapshot.height,
            pixels,
        })
    }
}

fn encode_surface_pixels_raw(pixels: &[i32]) -> Vec<u8> {
    let mut payload = Vec::with_capacity(pixels.len() * std::mem::size_of::<i32>());
    for &pixel in pixels {
        payload.extend_from_slice(&pixel.to_le_bytes());
    }
    payload
}

fn decode_surface_pixels_raw(
    payload: &[u8],
    expected_pixels: usize,
) -> Result<Vec<i32>, &'static str> {
    if payload.len() != expected_pixels.saturating_mul(std::mem::size_of::<i32>()) {
        return Err("image surface raw payload length mismatch");
    }
    let mut pixels = Vec::with_capacity(expected_pixels);
    for chunk in payload.chunks_exact(std::mem::size_of::<i32>()) {
        pixels.push(i32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]));
    }
    Ok(pixels)
}

fn encode_surface_pixels_rle(pixels: &[i32]) -> Vec<u8> {
    let mut payload = Vec::new();
    let mut index = 0usize;
    while index < pixels.len() {
        let pixel = pixels[index];
        let mut run_len = 1usize;
        while index + run_len < pixels.len() && pixels[index + run_len] == pixel {
            run_len += 1;
        }
        payload.extend_from_slice(&(run_len as u32).to_le_bytes());
        payload.extend_from_slice(&pixel.to_le_bytes());
        index += run_len;
    }
    payload
}

fn decode_surface_pixels_rle(
    payload: &[u8],
    expected_pixels: usize,
) -> Result<Vec<i32>, &'static str> {
    if payload.len() % (std::mem::size_of::<u32>() + std::mem::size_of::<i32>()) != 0 {
        return Err("image surface rle payload length mismatch");
    }
    let mut pixels = Vec::with_capacity(expected_pixels);
    for chunk in payload.chunks_exact(std::mem::size_of::<u32>() + std::mem::size_of::<i32>()) {
        let run_len = u32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]) as usize;
        let pixel = i32::from_le_bytes([chunk[4], chunk[5], chunk[6], chunk[7]]);
        if run_len == 0 {
            return Err("image surface rle run length cannot be zero");
        }
        if pixels.len().saturating_add(run_len) > expected_pixels {
            return Err("image surface rle expands beyond expected pixel count");
        }
        pixels.extend(std::iter::repeat_n(pixel, run_len));
    }
    if pixels.len() != expected_pixels {
        return Err("image surface rle pixel count mismatch");
    }
    Ok(pixels)
}

/// Snapshot of all VM dynamic state for save/load state functionality.
/// ClassRepository and NativeRegistry are NOT included — they are rebuilt from JAR on restore.
#[derive(Serialize, Deserialize)]
pub struct VmSnapshot {
    pub heap: Heap,
    pub scheduler: Scheduler,
    pub static_fields: StaticFields,
    pub monitors: HashMap<u32, CoopMonitor>,
    pub class_objects: HashMap<u32, String>,
    pub class_objects_by_name: HashMap<String, u32>,
    pub system_properties: HashMap<String, String>,
    pub graphics_states: HashMap<u32, GraphicsState>,
    #[serde(default)]
    pub direct_buffer_arena: DirectBufferArena,
    #[serde(default)]
    pub mascot_textures: HashMap<i32, MascotTextureImage>,
    #[serde(default)]
    pub mascot_texture_owners: HashMap<u32, i32>,
    #[serde(default = "default_next_mascot_texture_handle")]
    pub next_mascot_texture_handle: i32,
    pub display_width: i32,
    pub display_height: i32,
    pub midp_display_ref: u32,
    pub resource_decoders: HashMap<u32, ResourceDecoder>,
    pub next_decoder_id: u32,
    pub media_players: HashMap<i32, MediaPlayerState>,
    pub player_obj_to_id: HashMap<u32, i32>,
    pub next_media_player_id: i32,
    pub image_surface_map: HashMap<u32, i32>,
    pub resource_image_surfaces: Vec<ResourceImageSurfaceSnapshot>,
    pub derived_image_surfaces: Vec<DerivedImageSurfaceSnapshot>,
    pub image_surface_snapshots: Vec<ImageSurfaceSnapshot>,
    pub rms_stores: HashMap<String, RmsStore>,
    pub rms_obj_to_name: HashMap<u32, String>,
    pub rms_listeners: HashMap<u32, Vec<u32>>,
    pub reference_map: HashMap<u32, u32>,
    pub throwable_backtraces: HashMap<u32, Vec<(String, String)>>,
    pub thread_object_map: HashMap<u32, u32>,
    pub thread_id_to_object: HashMap<u32, u32>,
    pub alive_threads: std::collections::HashSet<u32>,
    pub interrupted_threads: std::collections::HashSet<u32>,
    pub main_thread_obj: u32,
    pub virtual_time_ms: u64,
    pub speed_multiplier: f64,
    pub host_time_base_ms: u64,
    pub effective_time_base_ms: u64,
    pub time_offset_ms: u64,
    pub saved_effective_now: u64,
    pub native_event_queue: VecDeque<NativeEventData>,
    pub native_event_monitor_tid: Option<u32>,
    pub foreground_display_id: i32,
    pub step_count: u64,
    pub next_native_id_counter: u32,
    pub display_surface_created: bool,
    pub async_request_counter: u32,
    pub media_list_cursors: HashMap<i32, usize>,
    pub media_list_next_id: i32,
    pub lcdui_visible: bool,
    #[serde(default)]
    pub initialized_class_ids: Vec<u32>,
}

impl SharedVm {
    pub fn encode_compact_boot_image(&self, source_jar: &[u8]) -> Result<Vec<u8>, String> {
        let resource_jar = Self::build_resource_only_jar(source_jar)?;
        self.class_repo.encode_compact_boot_image(&resource_jar)
    }

    pub fn install_compact_boot_image(&mut self, image: &[u8]) -> bool {
        let Some((repo, resources)) = ClassRepository::decode_compact_boot_image(image) else {
            return false;
        };
        if self.index_jar_resources(resources).is_err() {
            return false;
        }
        self.class_repo = repo;
        for (name, size, class_id) in self.class_repo.static_layouts() {
            self.static_fields
                .ensure_class(&mut self.heap, name, size, class_id);
        }
        true
    }

    fn build_resource_only_jar(source_jar: &[u8]) -> Result<Vec<u8>, String> {
        use std::io::{Cursor, Read, Write};

        let mut source = zip::ZipArchive::new(Cursor::new(source_jar))
            .map_err(|error| format!("Failed to read source JAR: {error}"))?;
        let mut output = Vec::new();
        {
            let mut writer = zip::ZipWriter::new(Cursor::new(&mut output));
            let options = zip::write::FileOptions::default()
                .compression_method(zip::CompressionMethod::Deflated);
            for index in 0..source.len() {
                let mut file = source
                    .by_index(index)
                    .map_err(|error| format!("Failed to read source JAR entry: {error}"))?;
                let name = file.name().to_string();
                if name.ends_with('/') || name.ends_with(".class") {
                    continue;
                }
                let mut data = Vec::with_capacity(file.size() as usize);
                file.read_to_end(&mut data)
                    .map_err(|error| format!("Failed to read source JAR entry data: {error}"))?;
                writer.start_file(name, options).map_err(|error| {
                    format!("Failed to create boot resource JAR entry: {error}")
                })?;
                writer
                    .write_all(&data)
                    .map_err(|error| format!("Failed to write boot resource JAR entry: {error}"))?;
            }
            writer
                .finish()
                .map_err(|error| format!("Failed to finish boot resource JAR: {error}"))?;
        }
        Ok(output)
    }

    pub fn new(surface_id_allocator: Rc<RefCell<i32>>, debug: bool) -> Self {
        let now = crate::call_host_mono_time_millis();
        Self {
            heap: Heap::new(),
            class_repo: ClassRepository::new(),
            static_fields: StaticFields::new(),
            monitors: HashMap::new(),
            class_objects: HashMap::new(),
            class_objects_by_name: HashMap::new(),
            system_properties: {
                let mut props = HashMap::new();
                let locale = call_host_get_locale();
                props.insert("java.version".into(), "1.3".into());
                props.insert("java.vendor".into(), "jsjvm".into());
                props.insert("microedition.locale".into(), locale);
                props.insert("microedition.encoding".into(), "ISO8859_1".into());
                props.insert("file.encoding".into(), "ISO8859_1".into());
                props.insert("microedition.platform".into(), "Nokia6300".into());
                props.insert("microedition.configuration".into(), "CLDC-1.1".into());
                props.insert("microedition.profiles".into(), "MIDP-2.0".into());
                props.insert("microedition.hostname".into(), "localhost".into());
                props.insert(
                    "com.sun.cldc.util.j2me.TimeZoneImpl.timezone".into(),
                    "GMT".into(),
                );
                props
            },
            command_buffer: Vec::with_capacity(4096),
            last_shape_stream: None,
            last_image_stream: None,
            m3g_pending_command_buffer: None,
            text_output: String::new(),
            lcdui_visible: false,
            trace_output: String::new(),
            step_count: 0,
            debug,
            midp_display_ref: NULL,
            graphics_states: HashMap::new(),
            direct_buffer_arena: DirectBufferArena::default(),
            mascot_textures: HashMap::new(),
            mascot_texture_owners: HashMap::new(),
            mascot_uploaded_textures: HashSet::new(),
            mascot_texture_reset_pending: false,
            mascot_pending_figures: Vec::new(),
            next_mascot_texture_handle: default_next_mascot_texture_handle(),
            next_native_id_counter: 1,
            display_surface_created: false,
            resources: HashMap::new(),
            lazy_resource_jars: Vec::new(),
            lazy_resource_entries: HashMap::new(),
            resource_decoders: HashMap::new(),
            next_decoder_id: 1,
            media_players: HashMap::new(),
            next_media_player_id: 1,
            player_obj_to_id: HashMap::new(),
            nokia_sound_handles: HashMap::new(),
            surface_id_allocator,
            image_surface_map: HashMap::new(),
            surface_dims: HashMap::new(),
            image_surface_restore_sources: HashMap::new(),
            display_width: 240,
            display_height: 320,
            async_request_counter: 1,
            async_requests: HashMap::new(),
            pending_notify_wakeups: Vec::new(),
            monitor_transfer_wakeups: Vec::new(),
            rms_stores: HashMap::new(),
            rms_obj_to_name: HashMap::new(),
            rms_listeners: HashMap::new(),
            media_list_cursors: std::collections::HashMap::new(),
            media_list_next_id: 1,
            reference_map: HashMap::new(),
            throwable_backtraces: HashMap::new(),
            pending_exception: None,
            pending_exception_str: None,
            pending_thread_spawns: Vec::new(),
            current_monitor_depth: 0,
            thread_object_map: HashMap::new(),
            thread_id_to_object: HashMap::new(),
            alive_threads: std::collections::HashSet::new(),
            interrupted_threads: std::collections::HashSet::new(),
            main_thread_obj: 0,
            virtual_time_ms: 0,
            speed_multiplier: 1.0,
            host_time_base_ms: now,
            effective_time_base_ms: now,
            time_offset_ms: 0,
            last_thread_return_value: 0,
            native_event_queue: VecDeque::new(),
            native_event_monitor_tid: None,
            foreground_display_id: 0,
            native_registry: crate::interpreter::native::create_native_registry(),
            pending_resource_image_restores: Vec::new(),
            pending_derived_image_restores: Vec::new(),
            force_gc: false,
            midlet_destroyed: false,
            preallocated_oom: NULL,
            m3g_resources: M3gResourceStore::new(),
            m3g_interface_handle: 0,
        }
    }

    pub fn trace_event(&mut self, _msg: String) {}

    pub fn drain_trace_ring(&mut self) -> String {
        String::new()
    }

    pub fn issue_async_request(&mut self, thread_id: u32, request_type: AsyncRequestType) -> u32 {
        let request_id = self.async_request_counter;
        self.async_request_counter += 1;
        self.async_requests.insert(
            request_id,
            AsyncRequestEntry {
                request: AsyncRequest {
                    request_type,
                    thread_id,
                },
                state: AsyncRequestState::Pending,
                result: None,
            },
        );
        request_id
    }

    pub fn claim_pending_async_requests(&mut self, max_count: usize) -> Vec<(u32, AsyncRequest)> {
        if max_count == 0 {
            return Vec::new();
        }

        let mut ids: Vec<u32> = self
            .async_requests
            .iter()
            .filter(|(_, entry)| entry.state == AsyncRequestState::Pending)
            .map(|(&id, _)| id)
            .collect();
        ids.sort_unstable();
        ids.truncate(max_count);

        let mut claimed = Vec::with_capacity(ids.len());
        for id in ids {
            if let Some(entry) = self.async_requests.get_mut(&id) {
                if entry.state == AsyncRequestState::Pending {
                    entry.state = AsyncRequestState::Claimed;
                    claimed.push((id, entry.request.clone()));
                }
            }
        }
        claimed
    }

    pub fn claim_async_request(&mut self, request_id: u32) -> bool {
        match self.async_requests.get_mut(&request_id) {
            Some(entry) if entry.state == AsyncRequestState::Pending => {
                entry.state = AsyncRequestState::Claimed;
                true
            }
            _ => false,
        }
    }

    pub fn resolve_async_request(&mut self, request_id: u32, result: AsyncResult) -> bool {
        match self.async_requests.get_mut(&request_id) {
            Some(entry) if entry.state == AsyncRequestState::Claimed => {
                entry.result = Some(result);
                entry.state = AsyncRequestState::Resolved;
                true
            }
            _ => false,
        }
    }

    pub fn resolve_async_request_legacy(&mut self, request_id: u32, result: AsyncResult) -> bool {
        if self.claim_async_request(request_id) {
            return self.resolve_async_request(request_id, result);
        }
        self.resolve_async_request(request_id, result)
    }

    pub fn get_async_request(&self, request_id: u32) -> Option<&AsyncRequest> {
        self.async_requests
            .get(&request_id)
            .map(|entry| &entry.request)
    }

    pub fn take_resolved_async_for_thread(
        &mut self,
        thread_id: u32,
        pred: fn(&AsyncResult) -> bool,
    ) -> Option<(u32, AsyncRequest, AsyncResult)> {
        let id = self.async_requests.iter().find_map(|(&id, entry)| {
            if entry.request.thread_id != thread_id || entry.state != AsyncRequestState::Resolved {
                return None;
            }
            entry
                .result
                .as_ref()
                .filter(|result| pred(result))
                .map(|_| id)
        })?;
        let entry = self.async_requests.remove(&id)?;
        Some((
            id,
            entry.request,
            entry
                .result
                .expect("resolved async request must carry a result"),
        ))
    }

    pub fn has_resolved_async_for_thread(&self, thread_id: u32) -> bool {
        self.async_requests.values().any(|entry| {
            entry.request.thread_id == thread_id && entry.state == AsyncRequestState::Resolved
        })
    }

    pub fn get_async_state_counts(&self) -> (usize, usize, usize) {
        let mut pending = 0;
        let mut claimed = 0;
        let mut resolved = 0;
        for entry in self.async_requests.values() {
            match entry.state {
                AsyncRequestState::Pending => pending += 1,
                AsyncRequestState::Claimed => claimed += 1,
                AsyncRequestState::Resolved => resolved += 1,
            }
        }
        (pending, claimed, resolved)
    }

    /// Fast snapshot for rewind: skips heap compaction (just clone).
    /// Produces a slightly larger snapshot but is significantly faster.
    pub fn save_snapshot_fast(&self, scheduler: &Scheduler) -> VmSnapshot {
        self.save_snapshot_inner(scheduler, false, &[])
    }

    pub fn save_snapshot_fast_with_image_surface_snapshots(
        &self,
        scheduler: &Scheduler,
        image_surface_snapshots: &[ImageSurfaceSnapshot],
    ) -> VmSnapshot {
        self.save_snapshot_inner(scheduler, false, image_surface_snapshots)
    }

    pub fn save_snapshot(&self, scheduler: &Scheduler) -> VmSnapshot {
        self.save_snapshot_inner(scheduler, true, &[])
    }

    pub fn save_snapshot_with_image_surface_snapshots(
        &self,
        scheduler: &Scheduler,
        image_surface_snapshots: &[ImageSurfaceSnapshot],
    ) -> VmSnapshot {
        self.save_snapshot_inner(scheduler, true, image_surface_snapshots)
    }

    fn save_snapshot_inner(
        &self,
        scheduler: &Scheduler,
        compact_heap: bool,
        provided_image_surface_snapshots: &[ImageSurfaceSnapshot],
    ) -> VmSnapshot {
        let live_image_surface_map = self.capture_live_image_surface_map();
        let resource_image_surfaces =
            self.capture_resource_image_surface_snapshots(&live_image_surface_map);
        let derived_image_surfaces =
            self.capture_derived_image_surface_snapshots(&live_image_surface_map);
        let image_surface_snapshots = self.filter_image_surface_snapshots(
            provided_image_surface_snapshots,
            &live_image_surface_map,
            &resource_image_surfaces,
            &derived_image_surfaces,
        );
        let snapshot_heap = if compact_heap {
            self.heap.compact_for_snapshot()
        } else {
            self.heap.clone()
        };

        VmSnapshot {
            heap: snapshot_heap,
            scheduler: scheduler.clone(),
            static_fields: self.static_fields.clone(),
            monitors: self.monitors.clone(),
            class_objects: self.class_objects.clone(),
            class_objects_by_name: self.class_objects_by_name.clone(),
            system_properties: self.system_properties.clone(),
            graphics_states: self.graphics_states.clone(),
            direct_buffer_arena: self.direct_buffer_arena.clone(),
            mascot_textures: self.mascot_textures.clone(),
            mascot_texture_owners: self.mascot_texture_owners.clone(),
            next_mascot_texture_handle: self.next_mascot_texture_handle,
            display_width: self.display_width,
            display_height: self.display_height,
            midp_display_ref: self.midp_display_ref,
            resource_decoders: self.resource_decoders.clone(),
            next_decoder_id: self.next_decoder_id,
            media_players: self.media_players.clone(),
            player_obj_to_id: self.player_obj_to_id.clone(),
            next_media_player_id: self.next_media_player_id,
            image_surface_map: live_image_surface_map,
            resource_image_surfaces: resource_image_surfaces.clone(),
            derived_image_surfaces: derived_image_surfaces.clone(),
            image_surface_snapshots,
            rms_stores: self.rms_stores.clone(),
            rms_obj_to_name: self.rms_obj_to_name.clone(),
            rms_listeners: self.rms_listeners.clone(),
            reference_map: self.reference_map.clone(),
            throwable_backtraces: self.throwable_backtraces.clone(),
            thread_object_map: self.thread_object_map.clone(),
            thread_id_to_object: self.thread_id_to_object.clone(),
            alive_threads: self.alive_threads.clone(),
            interrupted_threads: self.interrupted_threads.clone(),
            main_thread_obj: self.main_thread_obj,
            virtual_time_ms: self.virtual_time_ms,
            speed_multiplier: self.speed_multiplier,
            host_time_base_ms: self.host_time_base_ms,
            effective_time_base_ms: self.effective_time_base_ms,
            time_offset_ms: self.time_offset_ms,
            saved_effective_now: scheduler.effective_now(),
            native_event_queue: self.native_event_queue.clone(),
            native_event_monitor_tid: self.native_event_monitor_tid,
            foreground_display_id: self.foreground_display_id,
            step_count: self.step_count,
            next_native_id_counter: self.next_native_id_counter,
            display_surface_created: self.display_surface_created,
            async_request_counter: self.async_request_counter,
            media_list_cursors: self.media_list_cursors.clone(),
            media_list_next_id: self.media_list_next_id,
            lcdui_visible: self.lcdui_visible,
            initialized_class_ids: self.class_repo.initialized_class_ids(),
        }
    }

    pub fn collect_image_surface_snapshot_targets(&self) -> Vec<ImageSurfaceSnapshotTarget> {
        let live_image_surface_map = self.capture_live_image_surface_map();
        let resource_image_surfaces =
            self.capture_resource_image_surface_snapshots(&live_image_surface_map);
        let derived_image_surfaces =
            self.capture_derived_image_surface_snapshots(&live_image_surface_map);
        let mut targets: Vec<_> = self
            .eligible_image_surface_snapshot_dimensions(
                &live_image_surface_map,
                &resource_image_surfaces,
                &derived_image_surfaces,
            )
            .into_iter()
            .filter(|(surface_id, _)| *surface_id != 0)
            .map(|(surface_id, (width, height))| ImageSurfaceSnapshotTarget {
                surface_id,
                width,
                height,
            })
            .collect();
        targets.sort_by_key(|target| target.surface_id);
        targets
    }

    pub fn restore_snapshot(&mut self, snapshot: VmSnapshot) -> Scheduler {
        let image_surface_snapshots = snapshot.image_surface_snapshots.clone();
        let resource_image_surfaces = snapshot.resource_image_surfaces.clone();
        let derived_image_surfaces = snapshot.derived_image_surfaces.clone();
        let mut scheduler = snapshot.scheduler;
        self.heap = snapshot.heap;
        // Inline cache stores heap addresses of LDC string constants that are now stale.
        self.class_repo.clear_ldc_string_caches();
        self.static_fields = snapshot.static_fields;
        self.class_repo
            .restore_initialized_class_ids(&snapshot.initialized_class_ids);
        self.monitors = snapshot.monitors;
        self.class_objects = snapshot.class_objects;
        self.class_objects_by_name = snapshot.class_objects_by_name;
        self.system_properties = snapshot.system_properties;
        self.graphics_states = snapshot.graphics_states;
        self.direct_buffer_arena = snapshot.direct_buffer_arena;
        self.mascot_textures = snapshot.mascot_textures;
        self.mascot_texture_owners = snapshot.mascot_texture_owners;
        self.next_mascot_texture_handle = snapshot.next_mascot_texture_handle;
        self.mascot_uploaded_textures.clear();
        self.mascot_texture_reset_pending = true;
        self.display_width = snapshot.display_width;
        self.display_height = snapshot.display_height;
        self.midp_display_ref = snapshot.midp_display_ref;
        self.resource_decoders = snapshot.resource_decoders;
        let decoder_resources: Vec<_> = self
            .resource_decoders
            .iter()
            .map(|(&id, decoder)| (id, decoder.name.clone()))
            .collect();
        for (id, name) in decoder_resources {
            let data = self.get_resource(&name);
            let decoder = self.resource_decoders.get_mut(&id).unwrap();
            if let Some(data) = data {
                decoder.data = data;
                decoder.pos = decoder.pos.min(decoder.data.len());
            } else {
                decoder.data = Rc::new(Vec::new());
                decoder.pos = 0;
            }
        }
        self.next_decoder_id = snapshot.next_decoder_id;
        self.media_players = snapshot.media_players;
        self.player_obj_to_id = snapshot.player_obj_to_id;
        self.next_media_player_id = snapshot.next_media_player_id;
        self.image_surface_map = snapshot.image_surface_map;
        self.image_surface_restore_sources.clear();
        for resource_surface in &resource_image_surfaces {
            let restore_source = ImageSurfaceRestoreSource::JarResource {
                name: resource_surface.resource_name.clone(),
            };
            for (&image_data_ref, &surface_id) in &self.image_surface_map {
                if surface_id == resource_surface.surface_id {
                    self.image_surface_restore_sources
                        .insert(image_data_ref, restore_source.clone());
                }
            }
        }
        for derived_surface in &derived_image_surfaces {
            let Some((&image_data_ref, _)) =
                self.image_surface_map
                    .iter()
                    .find(|(_, &candidate_surface_id)| {
                        candidate_surface_id == derived_surface.surface_id
                    })
            else {
                continue;
            };
            let Some((&source_image_data_ref, _)) =
                self.image_surface_map
                    .iter()
                    .find(|(_, &candidate_surface_id)| {
                        candidate_surface_id == derived_surface.source_surface_id
                    })
            else {
                continue;
            };
            let restore_source = if derived_surface.x == 0
                && derived_surface.y == 0
                && derived_surface.transform == 0
            {
                ImageSurfaceRestoreSource::ImmutableCopy {
                    source_image_data_ref,
                }
            } else {
                ImageSurfaceRestoreSource::ImmutableRegion {
                    source_image_data_ref,
                    x: derived_surface.x,
                    y: derived_surface.y,
                    width: derived_surface.region_width,
                    height: derived_surface.region_height,
                    transform: derived_surface.transform,
                }
            };
            self.image_surface_restore_sources
                .insert(image_data_ref, restore_source);
        }
        self.rms_stores = snapshot.rms_stores;
        self.rms_obj_to_name = snapshot.rms_obj_to_name;
        self.rms_listeners = snapshot.rms_listeners;
        self.reference_map = snapshot.reference_map;
        self.throwable_backtraces = snapshot.throwable_backtraces;
        self.thread_object_map = snapshot.thread_object_map;
        self.thread_id_to_object = snapshot.thread_id_to_object;
        self.alive_threads = snapshot.alive_threads;
        self.interrupted_threads = snapshot.interrupted_threads;
        self.main_thread_obj = snapshot.main_thread_obj;
        self.virtual_time_ms = snapshot.virtual_time_ms;
        self.speed_multiplier = snapshot.speed_multiplier;
        self.host_time_base_ms = snapshot.host_time_base_ms;
        self.effective_time_base_ms = snapshot.effective_time_base_ms;
        self.time_offset_ms = snapshot.time_offset_ms;
        self.native_event_queue = snapshot.native_event_queue;
        self.native_event_monitor_tid = snapshot.native_event_monitor_tid;
        self.foreground_display_id = snapshot.foreground_display_id;
        self.step_count = snapshot.step_count;
        self.next_native_id_counter = snapshot.next_native_id_counter;
        self.display_surface_created = snapshot.display_surface_created;
        self.async_request_counter = snapshot.async_request_counter;
        self.media_list_cursors = snapshot.media_list_cursors;
        self.media_list_next_id = snapshot.media_list_next_id;
        self.lcdui_visible = snapshot.lcdui_visible;
        // Clear transient state
        self.async_requests.clear();
        self.pending_notify_wakeups.clear();
        self.monitor_transfer_wakeups.clear();
        self.pending_exception = None;
        self.pending_exception_str = None;
        self.pending_thread_spawns.clear();
        self.command_buffer.clear();
        self.last_shape_stream = None;
        self.last_image_stream = None;
        self.m3g_pending_command_buffer = None;
        self.mascot_pending_figures.clear();
        self.text_output.clear();
        self.trace_output.clear();
        self.current_monitor_depth = 0;
        self.last_thread_return_value = 0;
        self.pending_resource_image_restores = resource_image_surfaces;
        self.pending_derived_image_restores = derived_image_surfaces;
        self.restore_image_surface_snapshots(&image_surface_snapshots);
        self.restore_resource_image_surfaces();
        self.restore_derived_image_surfaces();
        scheduler.rebase_effective_now_to_current_host(snapshot.saved_effective_now);
        self.rebase_effective_now_to_current_host(snapshot.saved_effective_now);
        scheduler
    }

    pub fn load_class(&mut self, data: &[u8]) -> Result<String, String> {
        let parsed = crate::classfile::parser::parse_class(data)?;
        self.load_parsed_class(parsed)
    }

    fn load_parsed_class(
        &mut self,
        parsed: crate::classfile::parser::ParsedClassFile,
    ) -> Result<String, String> {
        let name = self.class_repo.link_parsed_class(parsed)?;
        // Assign native_method_id to registered native methods for ID-based dispatch.
        // Split borrow: native_registry (immutable) and class_repo (mutable) are disjoint fields.
        let native_map: *const std::collections::HashMap<String, u32> =
            self.native_registry.key_to_id_map();
        let native_invocation_kinds: *const std::collections::HashMap<
            String,
            crate::interpreter::native::registry::InvocationKind,
        > = self.native_registry.binding_kind_map();
        // SAFETY: native_map points to self.native_registry.key_to_id which is not modified
        // by assign_native_ids (which only mutates self.class_repo). The two fields are disjoint.
        self.class_repo
            .assign_native_ids(&name, unsafe { &*native_map }, unsafe {
                &*native_invocation_kinds
            })?;
        let (size_of_static_fields, class_id) = {
            let class = self.class_repo.get_class(&name).unwrap();
            (class.size_of_static_fields, class.class_id)
        };
        self.static_fields
            .ensure_class(&mut self.heap, &name, size_of_static_fields, class_id);
        Ok(name)
    }

    pub fn load_jar(&mut self, jar_data: &[u8]) -> Result<Vec<String>, String> {
        self.load_jar_impl(jar_data)
    }

    fn index_jar_resources(&mut self, jar_data: &[u8]) -> Result<(), String> {
        let jar_id = self.lazy_resource_jars.len();
        let jar_bytes = Rc::new(jar_data.to_vec());
        let mut archive = zip::ZipArchive::new(std::io::Cursor::new(jar_data))
            .map_err(|error| format!("Failed to read JAR: {error}"))?;
        for index in 0..archive.len() {
            let file = archive
                .by_index(index)
                .map_err(|error| format!("Failed to read JAR entry: {error}"))?;
            let name = file.name().to_string();
            if !name.ends_with('/') && !name.ends_with(".class") {
                self.resources.remove(&name);
                self.lazy_resource_entries.insert(name, jar_id);
            }
        }
        self.lazy_resource_jars.push(jar_bytes);
        Ok(())
    }

    fn load_jar_impl(&mut self, jar_data: &[u8]) -> Result<Vec<String>, String> {
        use std::io::Cursor;
        let reader = Cursor::new(jar_data);
        let mut archive =
            zip::ZipArchive::new(reader).map_err(|e| format!("Failed to read JAR: {}", e))?;

        let mut entries: Vec<(String, crate::classfile::parser::ParsedClassFile)> = Vec::new();
        for i in 0..archive.len() {
            let mut file = archive
                .by_index(i)
                .map_err(|e| format!("Failed to read JAR entry: {}", e))?;
            let name = file.name().to_string();
            if name.ends_with('/') {
                continue; // skip directories
            }
            if name.ends_with(".class") {
                let mut data = Vec::with_capacity(file.size() as usize);
                std::io::Read::read_to_end(&mut file, &mut data)
                    .map_err(|e| format!("Failed to read JAR entry data: {}", e))?;
                let parsed = crate::classfile::parser::parse_class(&data)
                    .map_err(|error| format!("Failed to parse JAR entry {name}: {error}"))?;
                entries.push((name, parsed));
            }
        }
        drop(archive);
        self.index_jar_resources(jar_data)?;

        let mut loaded = Vec::new();
        let mut pending: Vec<(String, crate::classfile::parser::ParsedClassFile)> =
            entries.into_iter().collect();
        let mut failures = Vec::new();
        while !pending.is_empty() {
            let mut failed = Vec::new();
            let mut made_progress = false;
            for (name, parsed) in std::mem::take(&mut pending) {
                if !self.class_repo.parsed_dependencies_loaded(&parsed) {
                    failed.push((name, parsed));
                    continue;
                }
                match self.load_parsed_class(parsed) {
                    Ok(class_name) => loaded.push(class_name),
                    Err(error) if error.starts_with("native binding mismatch:") => {
                        failures.push((name, error));
                    }
                    Err(error) => failures.push((name, error)),
                }
                made_progress = true;
            }
            if failed.is_empty() {
                break;
            }
            if !made_progress {
                for (name, parsed) in failed {
                    let class_name = ClassRepository::parsed_class_name(&parsed);
                    failures.push((
                        name,
                        format!("unresolved superclass or interface for {class_name}"),
                    ));
                }
                break;
            }
            pending = failed;
        }
        if failures.is_empty() {
            return Ok(loaded);
        }

        let failures = failures
            .into_iter()
            .map(|(name, error)| format!("  {name}: {error}"))
            .collect::<Vec<_>>();
        Err(format!(
            "failed to load {} class(es):\n{}",
            failures.len(),
            failures.join("\n")
        ))
    }

    /// Get a JAR resource, inflating and caching it only on first access.
    pub fn get_resource(&mut self, name: &str) -> Option<Rc<Vec<u8>>> {
        if let Some(data) = self.resources.get(name) {
            return Some(Rc::clone(data));
        }
        let jar_id = *self.lazy_resource_entries.get(name)?;
        let jar = Rc::clone(self.lazy_resource_jars.get(jar_id)?);
        let mut archive = zip::ZipArchive::new(std::io::Cursor::new(jar.as_slice())).ok()?;
        let mut entry = archive.by_name(name).ok()?;
        let mut data = Vec::with_capacity(entry.size() as usize);
        std::io::Read::read_to_end(&mut entry, &mut data).ok()?;
        let data = Rc::new(data);
        self.resources.insert(name.to_string(), Rc::clone(&data));
        Some(data)
    }

    /// Test-only audit for stale or misspelled native registrations.
    ///
    /// The registry carries compatibility bindings for multiple J2ME variants, so normal
    /// class loading must not require every binding to exist in the selected system JAR.
    #[cfg(test)]
    pub fn validate_registered_native_bindings_in_jar(
        &self,
        jar_data: &[u8],
    ) -> Result<(), String> {
        use std::io::Cursor;

        let mut archive = zip::ZipArchive::new(Cursor::new(jar_data))
            .map_err(|error| format!("failed to read JAR: {error}"))?;
        let mut declared_native_keys = std::collections::BTreeSet::new();
        let mut failures = Vec::new();
        for index in 0..archive.len() {
            let mut file = archive
                .by_index(index)
                .map_err(|error| format!("failed to read JAR entry: {error}"))?;
            let name = file.name().to_string();
            if !name.ends_with(".class") {
                continue;
            }
            let mut data = Vec::new();
            std::io::Read::read_to_end(&mut file, &mut data)
                .map_err(|error| format!("failed to read JAR entry data: {error}"))?;
            match self.class_repo.declared_method_keys(&data) {
                Ok(keys) => declared_native_keys.extend(keys),
                Err(error) => failures.push(format!("  {name}: {error}")),
            }
        }

        let registered_native_keys: std::collections::BTreeSet<_> = self
            .native_registry
            .key_to_id_map()
            .keys()
            .cloned()
            .collect();
        let extra_rust_bindings: Vec<_> = registered_native_keys
            .difference(&declared_native_keys)
            .cloned()
            .collect();
        if !extra_rust_bindings.is_empty() {
            failures.push(format!(
                "  Rust registers binding(s) absent from system classes:\n    {}",
                extra_rust_bindings.join("\n    ")
            ));
        }

        if failures.is_empty() {
            Ok(())
        } else {
            Err(format!(
                "native binding registry audit failed:\n{}",
                failures.join("\n")
            ))
        }
    }

    pub fn resolve_field(
        &self,
        class_name: &str,
        field_name: &str,
        descriptor: &str,
    ) -> Option<ResolvedField> {
        if let Some((field, owner_class)) = self
            .class_repo
            .find_field_in_hierarchy(class_name, field_name, descriptor)
        {
            return Some(ResolvedField {
                class_name: owner_class,
                byte_offset: field.byte_offset,
                is_wide: field.is_wide(),
                is_static: field.is_static(),
            });
        }
        None
    }

    /// 创建 Java String 对象（不驻留到 intern pool）。
    pub fn create_string(&mut self, value: &str) -> u32 {
        let chars: Vec<u16> = value.encode_utf16().collect();
        let str_class_id = self
            .class_repo
            .get_class_id("java.lang.String")
            .unwrap_or(0);
        let char_array_class_id = self.class_repo.get_class_id("[C").unwrap_or(0);
        let char_array =
            self.heap
                .alloc_array_typed(char_array_class_id, chars.len() as u32, 2, true);
        for (i, &ch) in chars.iter().enumerate() {
            self.heap.array_write_u16(char_array, i as u32, ch);
        }
        let str_size = self
            .class_repo
            .get_class("java.lang.String")
            .map_or(12, |c| c.size_of_fields);
        let str_addr = self.heap.alloc_object(str_class_id, str_size);

        let value_offset = self
            .class_repo
            .find_field_offset("java.lang.String", "value")
            .unwrap_or(0);
        let count_offset = self
            .class_repo
            .find_field_offset("java.lang.String", "count")
            .unwrap_or(8);
        self.heap.write_u32(str_addr + value_offset, char_array);
        self.heap
            .write_u32(str_addr + count_offset, chars.len() as u32);

        str_addr
    }

    pub fn get_string_value(&self, addr: u32) -> Option<String> {
        if addr == NULL {
            return None;
        }
        let value_offset = self
            .class_repo
            .find_field_offset("java.lang.String", "value")
            .unwrap_or(0);
        let offset_offset = self
            .class_repo
            .find_field_offset("java.lang.String", "offset")
            .unwrap_or(4);
        let count_offset = self
            .class_repo
            .find_field_offset("java.lang.String", "count")
            .unwrap_or(8);
        let char_array = self.heap.read_u32(addr + value_offset);
        if char_array == NULL {
            return None;
        }
        let start = self.heap.read_u32(addr + offset_offset);
        let len = self.heap.read_u32(addr + count_offset);
        let mut chars = Vec::with_capacity(len as usize);
        for i in 0..len {
            chars.push(self.heap.array_read_u16(char_array, start + i));
        }
        String::from_utf16(&chars).ok()
    }

    pub fn get_class_name_of_object(&self, addr: u32) -> Option<&str> {
        if addr == NULL {
            return None;
        }
        let class_id = self.heap.get_class_id(addr);
        self.class_repo.get_class_name_by_id(class_id)
    }

    pub fn bootstrap_system_io(&mut self) {
        let cos = self.new_object("com.sun.cldchi.io.ConsoleOutputStream");

        let writer = self.new_object("com.sun.cldc.i18n.j2me.ISO8859_1_Writer");
        if let Some(off) = self
            .class_repo
            .find_field_offset("com.sun.cldc.i18n.StreamWriter", "out")
        {
            self.heap.write_u32(writer + off, cos);
        }
        if let Some(off) = self.class_repo.find_field_offset("java.io.Writer", "lock") {
            self.heap.write_u32(writer + off, writer);
        }

        let osw = self.new_object("java.io.OutputStreamWriter");
        if let Some(off) = self
            .class_repo
            .find_field_offset("java.io.OutputStreamWriter", "out")
        {
            self.heap.write_u32(osw + off, writer);
        }
        if let Some(off) = self.class_repo.find_field_offset("java.io.Writer", "lock") {
            self.heap.write_u32(osw + off, osw);
        }

        let ps = self.new_object("java.io.PrintStream");
        if let Some(off) = self
            .class_repo
            .find_field_offset("java.io.PrintStream", "charOut")
        {
            self.heap.write_u32(ps + off, osw);
        }
        if let Some(off) = self
            .class_repo
            .find_field_offset("java.io.PrintStream", "byteOut")
        {
            self.heap.write_u32(ps + off, cos);
        }

        let sys_class = "java.lang.System";
        self.ensure_initialized(sys_class);
        if let Some(rf) = self.resolve_field(sys_class, "out", "Ljava/io/PrintStream;") {
            let base = self.static_fields.get_addr(&rf.class_name);
            self.heap.write_u32(base + rf.byte_offset, ps);
        }
        if let Some(rf) = self.resolve_field(sys_class, "err", "Ljava/io/PrintStream;") {
            let base = self.static_fields.get_addr(&rf.class_name);
            self.heap.write_u32(base + rf.byte_offset, ps);
        }
        self.mark_initialized(sys_class);
    }

    /// Effective monotonic time: real host time + accumulated step offset.
    pub fn effective_now(&self) -> u64 {
        let host_now = crate::call_host_mono_time_millis();
        let elapsed = host_now.saturating_sub(self.host_time_base_ms);
        self.effective_time_base_ms
            .saturating_add(elapsed)
            .saturating_add(self.time_offset_ms)
    }

    pub fn rebase_effective_now_to_current_host(&mut self, effective_now: u64) {
        self.host_time_base_ms = crate::call_host_mono_time_millis();
        self.effective_time_base_ms = effective_now.saturating_sub(self.time_offset_ms);
    }

    pub fn new_object(&mut self, class_name: &str) -> u32 {
        let (class_id, size_of_fields) = match self.class_repo.get_class(class_name) {
            Some(c) => (c.class_id, c.size_of_fields),
            None => (0, 0),
        };
        self.heap.alloc_object(class_id, size_of_fields)
    }

    pub fn new_array(&mut self, class_id: u32, length: u32, bytes_per_element: u32) -> u32 {
        // Determine if this is a primitive (atomic) array — GC skips scanning internals
        let is_atomic = self
            .class_repo
            .get_class_name_by_id(class_id)
            .map(|name| is_primitive_array_class(name))
            .unwrap_or(false);
        self.heap
            .alloc_array_typed(class_id, length, bytes_per_element, is_atomic)
    }

    pub fn ensure_initialized(&mut self, class_name: &str) -> Option<u32> {
        {
            let class = self.class_repo.get_class(class_name);
            match class {
                Some(c) if c.initialized => return None,
                None => return None,
                _ => {}
            }
        }

        let parent_name = {
            let super_id = self
                .class_repo
                .get_class(class_name)
                .filter(|c| c.super_class_id > 0)
                .map(|c| c.super_class_id);
            super_id.and_then(|id| {
                self.class_repo
                    .get_class_name_by_id(id)
                    .map(|s| s.to_string())
            })
        };
        if let Some(parent) = parent_name {
            if let Some(c) = self.class_repo.get_class(&parent) {
                if !c.initialized {
                    let parent_clinit = self.ensure_initialized(&parent);
                    if parent_clinit.is_some() {
                        return parent_clinit;
                    }
                }
            }
        }

        if let Some(c) = self.class_repo.get_class_mut(class_name) {
            c.initialized = true;
        }

        let (size_of_static_fields, class_id) = self
            .class_repo
            .get_class(class_name)
            .map(|c| (c.size_of_static_fields, c.class_id))
            .unwrap_or((0, 0));
        self.static_fields.ensure_class(
            &mut self.heap,
            class_name,
            size_of_static_fields,
            class_id,
        );

        self.class_repo.find_method(class_name, "<clinit>", "()V")
    }

    pub fn mark_initialized(&mut self, class_name: &str) {
        if let Some(c) = self.class_repo.get_class_mut(class_name) {
            c.initialized = true;
        }
    }

    pub fn mark_initialized_by_id(&mut self, class_id: u32) {
        if let Some(c) = self.class_repo.get_class_by_id_mut(class_id) {
            c.initialized = true;
        }
    }

    pub fn println(&mut self, s: &str) {
        self.text_output.push_str(s);
        self.text_output.push('\n');
        self.command_buffer.push(cmd::PRINTLN);
        self.write_string_to_command_buffer(s);
    }

    /// Emit LCDUI_SET_VISIBLE command only when the visibility state changes.
    pub fn set_lcdui_visible(&mut self, visible: bool) {
        if self.lcdui_visible != visible {
            self.lcdui_visible = visible;
            self.command_buffer.push(cmd::LCDUI_SET_VISIBLE);
            self.command_buffer.push(if visible { 1 } else { 0 });
            self.command_buffer.push(0);
            self.command_buffer.push(0);
        }
    }

    /// Write a length-prefixed, 4-byte aligned UTF-8 string to command_buffer.
    pub fn write_string_to_command_buffer(&mut self, s: &str) {
        let bytes = s.as_bytes();
        let len = bytes.len() as u32;
        self.command_buffer.extend_from_slice(&len.to_le_bytes());
        self.command_buffer.extend_from_slice(bytes);
        // Pad to 4-byte alignment
        let padding = (4 - (bytes.len() % 4)) % 4;
        for _ in 0..padding {
            self.command_buffer.push(0);
        }
    }

    /// Return the current command buffer contents and reset the write pointer.
    /// The returned Vec is consumed by the caller; the internal buffer is replaced
    /// with a fresh pre-allocated Vec (arena-style reuse).
    pub fn flush_command_buffer(&mut self) -> Vec<u8> {
        let old = std::mem::replace(&mut self.command_buffer, Vec::with_capacity(4096));
        self.last_shape_stream = None;
        self.last_image_stream = None;
        old
    }

    /// Append one image draw, coalescing adjacent draws with identical state
    /// and source surface into a compact stream.
    pub fn append_draw_image(
        &mut self,
        state: GraphicsState,
        surface_id: i32,
        x: i32,
        y: i32,
        anchor: i32,
    ) {
        let append_to =
            self.last_image_stream
                .filter(|(_, end, previous_state, previous_surface)| {
                    *end == self.command_buffer.len()
                        && *previous_state == state
                        && *previous_surface == surface_id
                });
        let stream_start = if let Some((start, _, _, _)) = append_to {
            let count_offset = start + 1 + 48 + 4;
            let count = u32::from_le_bytes(
                self.command_buffer[count_offset..count_offset + 4]
                    .try_into()
                    .unwrap(),
            );
            self.command_buffer[count_offset..count_offset + 4]
                .copy_from_slice(&(count + 1).to_le_bytes());
            start
        } else {
            let start = self.command_buffer.len();
            self.command_buffer.push(cmd::DRAW_IMAGE_STREAM);
            state.write_text_state_to_buf(&mut self.command_buffer);
            self.command_buffer
                .extend_from_slice(&surface_id.to_le_bytes());
            self.command_buffer.extend_from_slice(&1_u32.to_le_bytes());
            start
        };
        self.command_buffer.extend_from_slice(&x.to_le_bytes());
        self.command_buffer.extend_from_slice(&y.to_le_bytes());
        self.command_buffer.extend_from_slice(&anchor.to_le_bytes());
        self.last_image_stream = Some((stream_start, self.command_buffer.len(), state, surface_id));
    }

    /// Append one tessellated shape packet, coalescing adjacent packets that
    /// share the same clip/target state into a single wire command.
    pub fn append_shape_packet(
        &mut self,
        state: GraphicsState,
        pipeline: u32,
        stride: u32,
        vertices: &[f32],
    ) {
        if vertices.is_empty() || stride == 0 || vertices.len() % stride as usize != 0 {
            return;
        }

        let append_to = self.last_shape_stream.filter(|(_, end, previous_state)| {
            *end == self.command_buffer.len()
                && previous_state.surface_id == state.surface_id
                && previous_state.clip_x == state.clip_x
                && previous_state.clip_y == state.clip_y
                && previous_state.clip_w == state.clip_w
                && previous_state.clip_h == state.clip_h
        });
        let stream_start = if let Some((start, _, _)) = append_to {
            let count_offset = start + 1 + 48;
            let packet_count = u32::from_le_bytes(
                self.command_buffer[count_offset..count_offset + 4]
                    .try_into()
                    .unwrap(),
            );
            self.command_buffer[count_offset..count_offset + 4]
                .copy_from_slice(&(packet_count + 1).to_le_bytes());
            start
        } else {
            let start = self.command_buffer.len();
            self.command_buffer.push(cmd::LCDUI_DRAW_SHAPE_STREAM);
            state.write_text_state_to_buf(&mut self.command_buffer);
            self.command_buffer.extend_from_slice(&1_u32.to_le_bytes());
            start
        };

        self.command_buffer
            .extend_from_slice(&pipeline.to_le_bytes());
        self.command_buffer.extend_from_slice(&stride.to_le_bytes());
        self.command_buffer
            .extend_from_slice(&((vertices.len() / stride as usize) as u32).to_le_bytes());
        for value in vertices {
            self.command_buffer.extend_from_slice(&value.to_le_bytes());
        }
        self.last_shape_stream = Some((stream_start, self.command_buffer.len(), state));
    }

    /// Encode ARGB Java pixels as tightly packed RGBA8 for direct host upload.
    pub fn append_draw_rgba(
        &mut self,
        state: GraphicsState,
        x: i32,
        y: i32,
        width: i32,
        height: i32,
        process_alpha: bool,
        pixels: &[i32],
    ) {
        if width <= 0 || height <= 0 {
            return;
        }
        let pixel_count = (width as usize).saturating_mul(height as usize);
        self.command_buffer.push(cmd::DRAW_RGBA);
        state.write_text_state_to_buf(&mut self.command_buffer);
        self.command_buffer.extend_from_slice(&x.to_le_bytes());
        self.command_buffer.extend_from_slice(&y.to_le_bytes());
        self.command_buffer.extend_from_slice(&width.to_le_bytes());
        self.command_buffer.extend_from_slice(&height.to_le_bytes());
        self.command_buffer
            .extend_from_slice(&(pixel_count as u32).to_le_bytes());
        for index in 0..pixel_count {
            let argb = pixels.get(index).copied().unwrap_or(0) as u32;
            self.command_buffer.extend_from_slice(&[
                ((argb >> 16) & 0xff) as u8,
                ((argb >> 8) & 0xff) as u8,
                (argb & 0xff) as u8,
                if process_alpha {
                    (argb >> 24) as u8
                } else {
                    255
                },
            ]);
        }
    }

    pub fn flush_trace(&mut self) -> String {
        std::mem::take(&mut self.trace_output)
    }

    /// Called by Graphics3D.bindTarget: start diverting M3G commands into
    /// a per-target pending buffer.
    pub fn begin_m3g_target_batch(&mut self) {
        if self.m3g_pending_command_buffer.is_none() {
            self.m3g_pending_command_buffer = Some(Vec::with_capacity(4096));
        }
    }

    /// Return the buffer M3G commands should currently be written to. When
    /// a target is bound, this is the pending buffer; otherwise the main one.
    pub fn m3g_command_buffer_mut(&mut self) -> &mut Vec<u8> {
        match self.m3g_pending_command_buffer.as_mut() {
            Some(buf) => buf,
            None => &mut self.command_buffer,
        }
    }

    /// Called by Graphics3D.releaseTarget: append the pending M3G commands
    /// to the main command buffer, restoring the invariant that non-M3G
    /// commands emitted during the bind window are painted before the M3G
    /// output overlays on top.
    pub fn flush_m3g_target_batch(&mut self) {
        if let Some(pending) = self.m3g_pending_command_buffer.take() {
            self.command_buffer.extend_from_slice(&pending);
        }
    }

    pub fn next_native_id(&mut self) -> u32 {
        let id = self.next_native_id_counter;
        self.next_native_id_counter += 1;
        id
    }

    pub fn ensure_display_surface(&mut self) {
        if !self.display_surface_created {
            self.display_surface_created = true;
            self.register_surface(0, self.display_width, self.display_height);
        }
    }

    /// Allocate / re-allocate a host surface and remember its physical dimensions.
    pub fn register_surface(&mut self, surface_id: i32, width: i32, height: i32) {
        self.surface_dims.insert(surface_id, (width, height));
        crate::call_host_create_surface(surface_id, width, height);
    }

    pub fn load_resource(&mut self, name: String, data: Vec<u8>) {
        self.resources.insert(name, Rc::new(data));
    }

    pub fn try_mark_image_surface_as_jar_resource(
        &mut self,
        image_data_ref: u32,
        image_bytes: &[u8],
    ) {
        if let Some(name) = self.find_matching_resource_name(image_bytes) {
            self.image_surface_restore_sources.insert(
                image_data_ref,
                ImageSurfaceRestoreSource::JarResource { name },
            );
        } else {
            self.image_surface_restore_sources.remove(&image_data_ref);
        }
    }

    pub fn clear_image_surface_restore_source(&mut self, image_data_ref: u32) {
        self.image_surface_restore_sources.remove(&image_data_ref);
    }

    pub fn take_pending_resource_image_restores(&mut self) -> Vec<ResourceImageSurfaceSnapshot> {
        std::mem::take(&mut self.pending_resource_image_restores)
    }

    pub fn take_pending_derived_image_restores(&mut self) -> Vec<DerivedImageSurfaceSnapshot> {
        std::mem::take(&mut self.pending_derived_image_restores)
    }

    pub fn mark_image_surface_as_copy(&mut self, image_data_ref: u32, source_image_data_ref: u32) {
        self.image_surface_restore_sources.insert(
            image_data_ref,
            ImageSurfaceRestoreSource::ImmutableCopy {
                source_image_data_ref,
            },
        );
    }

    pub fn mark_image_surface_as_region(
        &mut self,
        image_data_ref: u32,
        source_image_data_ref: u32,
        x: i32,
        y: i32,
        width: i32,
        height: i32,
        transform: i32,
    ) {
        self.image_surface_restore_sources.insert(
            image_data_ref,
            ImageSurfaceRestoreSource::ImmutableRegion {
                source_image_data_ref,
                x,
                y,
                width,
                height,
                transform,
            },
        );
    }

    pub fn alloc_decoder_id(&mut self) -> u32 {
        let id = self.next_decoder_id;
        self.next_decoder_id += 1;
        id
    }

    pub fn alloc_media_player_id(&mut self) -> i32 {
        let id = self.next_media_player_id;
        self.next_media_player_id += 1;
        id
    }

    pub fn alloc_surface_id(&mut self) -> i32 {
        let mut next_surface_id = self.surface_id_allocator.borrow_mut();
        let id = *next_surface_id;
        *next_surface_id += 1;
        id
    }

    fn find_matching_resource_name(&self, image_bytes: &[u8]) -> Option<String> {
        self.resources.iter().find_map(|(name, data)| {
            (data.len() == image_bytes.len() && data.as_slice() == image_bytes)
                .then(|| name.clone())
        })
    }

    fn capture_live_image_surface_map(&self) -> HashMap<u32, i32> {
        let live_image_refs: std::collections::HashSet<u32> =
            self.heap.all_objects().iter().copied().collect();

        self.image_surface_map
            .iter()
            .filter_map(|(&image_data_ref, &surface_id)| {
                (surface_id > 0 && live_image_refs.contains(&image_data_ref))
                    .then_some((image_data_ref, surface_id))
            })
            .collect()
    }

    fn capture_resource_image_surface_snapshots(
        &self,
        live_image_surface_map: &HashMap<u32, i32>,
    ) -> Vec<ResourceImageSurfaceSnapshot> {
        let width_off = match self
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "width")
        {
            Some(offset) => offset,
            None => return Vec::new(),
        };
        let height_off = match self
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "height")
        {
            Some(offset) => offset,
            None => return Vec::new(),
        };

        let mut snapshots = Vec::new();
        let mut seen_surface_ids = std::collections::HashSet::new();
        for (&image_data_ref, &surface_id) in live_image_surface_map {
            if surface_id <= 0 || !seen_surface_ids.insert(surface_id) {
                continue;
            }
            let Some(ImageSurfaceRestoreSource::JarResource { name }) =
                self.image_surface_restore_sources.get(&image_data_ref)
            else {
                continue;
            };

            let width = self.heap.read_u32(image_data_ref + width_off) as i32;
            let height = self.heap.read_u32(image_data_ref + height_off) as i32;
            if width <= 0 || height <= 0 {
                continue;
            }

            snapshots.push(ResourceImageSurfaceSnapshot {
                surface_id,
                width,
                height,
                resource_name: name.clone(),
            });
        }

        snapshots
    }

    fn capture_derived_image_surface_snapshots(
        &self,
        live_image_surface_map: &HashMap<u32, i32>,
    ) -> Vec<DerivedImageSurfaceSnapshot> {
        let width_off = match self
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "width")
        {
            Some(offset) => offset,
            None => return Vec::new(),
        };
        let height_off = match self
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "height")
        {
            Some(offset) => offset,
            None => return Vec::new(),
        };

        let mut snapshots = Vec::new();
        let mut seen_surface_ids = std::collections::HashSet::new();
        for (&image_data_ref, &surface_id) in live_image_surface_map {
            if surface_id <= 0 || !seen_surface_ids.insert(surface_id) {
                continue;
            }
            let Some(restore_source) = self.image_surface_restore_sources.get(&image_data_ref)
            else {
                continue;
            };
            let (source_image_data_ref, x, y, region_width, region_height, transform) =
                match restore_source {
                    ImageSurfaceRestoreSource::ImmutableCopy {
                        source_image_data_ref,
                    } => {
                        let width = self.heap.read_u32(image_data_ref + width_off) as i32;
                        let height = self.heap.read_u32(image_data_ref + height_off) as i32;
                        (*source_image_data_ref, 0, 0, width, height, 0)
                    }
                    ImageSurfaceRestoreSource::ImmutableRegion {
                        source_image_data_ref,
                        x,
                        y,
                        width,
                        height,
                        transform,
                    } => (*source_image_data_ref, *x, *y, *width, *height, *transform),
                    ImageSurfaceRestoreSource::JarResource { .. } => continue,
                };

            let width = self.heap.read_u32(image_data_ref + width_off) as i32;
            let height = self.heap.read_u32(image_data_ref + height_off) as i32;
            if width <= 0 || height <= 0 || region_width <= 0 || region_height <= 0 {
                continue;
            }
            let Some(&source_surface_id) = live_image_surface_map.get(&source_image_data_ref)
            else {
                continue;
            };

            snapshots.push(DerivedImageSurfaceSnapshot {
                surface_id,
                width,
                height,
                source_surface_id,
                x,
                y,
                region_width,
                region_height,
                transform,
            });
        }

        snapshots
    }

    fn eligible_image_surface_snapshot_dimensions(
        &self,
        live_image_surface_map: &HashMap<u32, i32>,
        resource_image_surfaces: &[ResourceImageSurfaceSnapshot],
        derived_image_surfaces: &[DerivedImageSurfaceSnapshot],
    ) -> HashMap<i32, (i32, i32)> {
        let width_off = match self
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "width")
        {
            Some(offset) => offset,
            None => return HashMap::new(),
        };
        let height_off = match self
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "height")
        {
            Some(offset) => offset,
            None => return HashMap::new(),
        };

        let skipped_surface_ids: std::collections::HashSet<i32> = resource_image_surfaces
            .iter()
            .map(|surface| surface.surface_id)
            .chain(
                derived_image_surfaces
                    .iter()
                    .map(|surface| surface.surface_id),
            )
            .collect();
        let mut dimensions = HashMap::new();
        for (&image_data_ref, &surface_id) in live_image_surface_map {
            if surface_id <= 0 || skipped_surface_ids.contains(&surface_id) {
                continue;
            }
            let width = self.heap.read_u32(image_data_ref + width_off) as i32;
            let height = self.heap.read_u32(image_data_ref + height_off) as i32;
            if width <= 0 || height <= 0 {
                continue;
            }
            dimensions.entry(surface_id).or_insert((width, height));
        }
        dimensions
    }

    fn filter_image_surface_snapshots(
        &self,
        snapshots: &[ImageSurfaceSnapshot],
        live_image_surface_map: &HashMap<u32, i32>,
        resource_image_surfaces: &[ResourceImageSurfaceSnapshot],
        derived_image_surfaces: &[DerivedImageSurfaceSnapshot],
    ) -> Vec<ImageSurfaceSnapshot> {
        let eligible_dimensions = self.eligible_image_surface_snapshot_dimensions(
            live_image_surface_map,
            resource_image_surfaces,
            derived_image_surfaces,
        );
        let mut seen_surface_ids = std::collections::HashSet::new();
        snapshots
            .iter()
            .filter_map(|snapshot| {
                let &(width, height) = eligible_dimensions.get(&snapshot.surface_id)?;
                if snapshot.width != width || snapshot.height != height {
                    return None;
                }
                let expected_pixels = (width as usize).saturating_mul(height as usize);
                if expected_pixels == 0 || snapshot.pixels.len() != expected_pixels {
                    return None;
                }
                if !seen_surface_ids.insert(snapshot.surface_id) {
                    return None;
                }
                Some(snapshot.clone())
            })
            .collect()
    }

    fn restore_resource_image_surfaces(&mut self) {
        let entries: Vec<(i32, i32, i32)> = self
            .pending_resource_image_restores
            .iter()
            .filter(|r| r.surface_id > 0 && r.width > 0 && r.height > 0)
            .map(|r| (r.surface_id, r.width, r.height))
            .collect();
        for (id, w, h) in entries {
            self.register_surface(id, w, h);
        }
    }

    fn restore_derived_image_surfaces(&mut self) {
        let entries: Vec<(i32, i32, i32)> = self
            .pending_derived_image_restores
            .iter()
            .filter(|r| r.surface_id > 0 && r.width > 0 && r.height > 0)
            .map(|r| (r.surface_id, r.width, r.height))
            .collect();
        for (id, w, h) in entries {
            self.register_surface(id, w, h);
        }
    }

    fn restore_image_surface_snapshots(&mut self, snapshots: &[ImageSurfaceSnapshot]) {
        for snapshot in snapshots {
            if snapshot.surface_id <= 0 || snapshot.width <= 0 || snapshot.height <= 0 {
                continue;
            }

            let expected_pixels =
                (snapshot.width as usize).saturating_mul(snapshot.height as usize);
            if expected_pixels == 0 || snapshot.pixels.len() != expected_pixels {
                continue;
            }

            self.register_surface(snapshot.surface_id, snapshot.width, snapshot.height);

            let state = GraphicsState {
                surface_id: snapshot.surface_id,
                clip_x: 0,
                clip_y: 0,
                clip_w: snapshot.width,
                clip_h: snapshot.height,
                ..Default::default()
            };
            self.append_draw_rgba(
                state,
                0,
                0,
                snapshot.width,
                snapshot.height,
                true,
                &snapshot.pixels,
            );
        }
    }

    pub fn free_memory(&self) -> u64 {
        REPORTED_TOTAL_MEMORY.saturating_sub(self.heap.used_bytes() as u64)
    }

    pub fn total_memory(&self) -> u64 {
        REPORTED_TOTAL_MEMORY // Report 2MB to avoid being detected as emulator
    }

    /// Resolve a class name from constant pool on demand.
    pub fn resolve_class_name_cached(&self, class_name: &str, index: u16) -> Option<String> {
        self.class_repo.resolve_cp_class(class_name, index)
    }

    /// O(1) variant using class_id directly (no HashMap lookup).
    pub fn resolve_class_name_cached_by_id(&self, class_id: u32, index: u16) -> Option<String> {
        self.class_repo.resolve_cp_class_by_id(class_id, index)
    }

    // ============ ID-based methods (Phase 2) ============

    /// Get the class_id of an object directly from its heap header (O(1), no String allocation)
    pub fn get_class_id_of_object(&self, addr: u32) -> u32 {
        if addr == NULL {
            return 0;
        }
        self.heap.get_class_id(addr)
    }

    /// Create a new object by class_id (O(1), no HashMap lookup)
    pub fn new_object_by_id(&mut self, class_id: u32) -> u32 {
        let size_of_fields = self
            .class_repo
            .get_class_by_id(class_id)
            .map(|c| c.size_of_fields)
            .unwrap_or(0);
        self.heap.alloc_object(class_id, size_of_fields)
    }

    /// Ensure class is initialized by class_id. Returns clinit method if needs to be run.
    pub fn ensure_initialized_by_id(&mut self, class_id: u32) -> Option<u32> {
        // Check if already initialized
        {
            let class = self.class_repo.get_class_by_id(class_id);
            match class {
                Some(c) if c.initialized => return None,
                None => return None,
                _ => {}
            }
        }

        // Initialize parent first (by ID traversal)
        let super_class_id = self.class_repo.get_class_by_id(class_id).and_then(|c| {
            if c.super_class_id > 0 {
                Some(c.super_class_id)
            } else {
                None
            }
        });
        if let Some(parent_id) = super_class_id {
            if let Some(parent_class) = self.class_repo.get_class_by_id(parent_id) {
                if !parent_class.initialized {
                    let parent_clinit = self.ensure_initialized_by_id(parent_id);
                    if parent_clinit.is_some() {
                        return parent_clinit;
                    }
                }
            }
        }

        // Mark as initialized
        if let Some(c) = self.class_repo.get_class_by_id_mut(class_id) {
            c.initialized = true;
        }

        // Ensure static fields storage
        let (size_of_static_fields, class_name) = self
            .class_repo
            .get_class_by_id(class_id)
            .map(|c| (c.size_of_static_fields, c.name.clone()))
            .unwrap_or((0, String::new()));
        if !class_name.is_empty() {
            self.static_fields.ensure_class(
                &mut self.heap,
                &class_name,
                size_of_static_fields,
                class_id,
            );
        }

        // Return clinit method
        self.class_repo.get_class_by_id(class_id).and_then(|c| {
            c.methods.iter().copied().find(|&method_id| {
                self.class_repo.get_method(method_id).is_some_and(|method| {
                    self.class_repo.get_string(method.name_id) == "<clinit>"
                        && self.class_repo.get_string(method.descriptor_id) == "()V"
                })
            })
        })
    }

    /// Resolve a field by class_id, returning ID-based result (zero String allocation)
    pub fn resolve_field_by_ids(
        &self,
        class_id: u32,
        name_id: u32,
        descriptor_id: u32,
    ) -> Option<ResolvedFieldById> {
        if let Some((field, owner_class_id)) =
            self.class_repo
                .find_field_in_hierarchy_by_id(class_id, name_id, descriptor_id)
        {
            return Some(ResolvedFieldById {
                class_id: owner_class_id,
                byte_offset: field.byte_offset,
                is_wide: field.is_wide(),
                is_static: field.is_static(),
            });
        }
        None
    }

    // ============ ID-based CP cache resolution (Phase 3) ============

    /// Resolve a field reference with class_id. Returns None if class not loaded yet.
    pub fn resolve_fieldref_cached_by_id(
        &self,
        class_name: &str,
        index: u16,
    ) -> Option<ResolvedRef> {
        let (cn, fn_, desc) = self.class_repo.resolve_cp_fieldref(class_name, index)?;
        let class_id = self.class_repo.get_class_id(&cn)?;
        if !self.class_repo.is_class_loaded(class_id) {
            return None;
        }
        Some(ResolvedRef {
            class_id,
            class_name: cn,
            name: fn_,
            descriptor: desc,
        })
    }

    /// Resolve a class reference with class_id. Array classes are VM-created and
    /// are registered lazily from their descriptors instead of being loaded from
    /// class files.
    pub fn resolve_class_cached_by_id(&mut self, class_name: &str, index: u16) -> Option<u32> {
        let cn = self.class_repo.resolve_cp_class(class_name, index)?;
        let class_id = if cn.starts_with('[') {
            self.class_repo.get_or_register_array_class(&cn)
        } else {
            self.class_repo.get_class_id(&cn)?
        };
        if !self.class_repo.is_class_loaded(class_id) {
            return None;
        }
        Some(class_id)
    }

    // ============ Inline cache methods ============

    /// Resolve a field ref and populate inline cache. Returns cached entry directly.
    /// First call: resolves hierarchy + populates cache. Subsequent calls: O(1) Vec index.
    pub fn resolve_field_inline(
        &mut self,
        caller_class_id: u32,
        index: u16,
    ) -> Option<CachedCpEntry> {
        // Fast path: check inline cache
        let cached = self.class_repo.get_inline_cache(caller_class_id, index);
        if !matches!(cached, CachedCpEntry::Empty) {
            return Some(cached);
        }

        // Slow path: resolve from CP + hierarchy
        let (class_id, name_id, descriptor_id) = self
            .class_repo
            .resolve_cp_fieldref_ids_by_id(caller_class_id, index)?;
        let rf = self.resolve_field_by_ids(class_id, name_id, descriptor_id)?;
        let entry = CachedCpEntry::Field {
            owner_class_id: rf.class_id,
            byte_offset: rf.byte_offset,
            is_wide: rf.is_wide,
            is_static: rf.is_static,
        };
        self.class_repo
            .set_inline_cache(caller_class_id, index, entry);
        Some(entry)
    }

    /// Resolve a method ref and populate inline cache. Returns cached entry directly.
    pub fn resolve_method_inline(
        &mut self,
        caller_class_id: u32,
        index: u16,
    ) -> Option<CachedCpEntry> {
        // Fast path
        let cached = self.class_repo.get_inline_cache(caller_class_id, index);
        if !matches!(cached, CachedCpEntry::Empty) {
            return Some(cached);
        }

        // Slow path
        let (target_class_id, name_id, descriptor_id) = self
            .class_repo
            .resolve_cp_methodref_ids_by_id(caller_class_id, index)?;
        if !self.class_repo.is_class_loaded(target_class_id) {
            return None;
        }

        let (method_id, resolved_vtable_index) = self
            .class_repo
            .find_virtual_method_by_ids(target_class_id, name_id, descriptor_id)
            .or_else(|| {
                self.class_repo
                    .find_method_by_ids(target_class_id, name_id, descriptor_id)
                    .and_then(|method_id| {
                        let vtable_index = self.class_repo.get_method(method_id)?.vtable_index;
                        Some((method_id, vtable_index))
                    })
            })?;
        let method = self.class_repo.get_method(method_id)?;

        let entry = CachedCpEntry::Method {
            method_id,
            vtable_index: resolved_vtable_index,
            imethod_id: method.imethod_id,
        };
        self.class_repo
            .set_inline_cache(caller_class_id, index, entry);
        Some(entry)
    }

    /// Resolve a class ref and populate inline cache. Returns class_id directly.
    pub fn resolve_class_inline(&mut self, caller_class_id: u32, index: u16) -> Option<u32> {
        // Fast path
        let cached = self.class_repo.get_inline_cache(caller_class_id, index);
        if let CachedCpEntry::Class { class_id } = cached {
            return Some(class_id);
        }

        // Slow path
        let caller_name = self
            .class_repo
            .get_class_name_by_id(caller_class_id)?
            .to_string();
        let class_id = self.resolve_class_cached_by_id(&caller_name, index)?;
        self.class_repo
            .set_inline_cache(caller_class_id, index, CachedCpEntry::Class { class_id });
        Some(class_id)
    }
}

#[cfg(test)]
mod tests {
    use super::{cmd, DirectBufferArena, GraphicsState, ImageSurfaceSnapshot, SharedVm};
    use std::cell::RefCell;
    use std::io::Write;
    use std::rc::Rc;

    #[test]
    fn image_stream_coalesces_adjacent_draws() {
        let mut vm = SharedVm::new(Rc::new(RefCell::new(1)), false);
        let state = GraphicsState::default_for_display(240, 320);
        vm.append_draw_image(state, 7, 10, 20, 3);
        vm.append_draw_image(state, 7, 30, 40, 5);

        assert_eq!(vm.command_buffer[0], cmd::DRAW_IMAGE_STREAM);
        assert_eq!(
            i32::from_le_bytes(vm.command_buffer[49..53].try_into().unwrap()),
            7
        );
        assert_eq!(
            u32::from_le_bytes(vm.command_buffer[53..57].try_into().unwrap()),
            2
        );
        assert_eq!(vm.command_buffer.len(), 57 + 2 * 12);

        vm.command_buffer.push(cmd::FLUSH);
        vm.command_buffer.extend_from_slice(&0_i32.to_le_bytes());
        vm.append_draw_image(state, 7, 50, 60, 0);
        assert_eq!(vm.command_buffer[86], cmd::DRAW_IMAGE_STREAM);
    }

    #[test]
    fn draw_rgba_encodes_host_ready_channel_order_and_alpha() {
        let mut vm = SharedVm::new(Rc::new(RefCell::new(1)), false);
        vm.append_draw_rgba(
            GraphicsState::default_for_display(1, 1),
            0,
            0,
            1,
            1,
            true,
            &[0x7f12_34abu32 as i32],
        );
        assert_eq!(vm.command_buffer[0], cmd::DRAW_RGBA);
        assert_eq!(&vm.command_buffer[69..73], &[0x12, 0x34, 0xab, 0x7f]);

        vm.command_buffer.clear();
        vm.append_draw_rgba(
            GraphicsState::default_for_display(1, 1),
            0,
            0,
            1,
            1,
            false,
            &[0x0012_34abu32 as i32],
        );
        assert_eq!(&vm.command_buffer[69..73], &[0x12, 0x34, 0xab, 0xff]);
    }

    #[test]
    fn jar_resources_are_inflated_on_first_access() {
        let mut jar = Vec::new();
        {
            let cursor = std::io::Cursor::new(&mut jar);
            let mut writer = zip::ZipWriter::new(cursor);
            writer
                .start_file("asset.bin", zip::write::FileOptions::default())
                .unwrap();
            writer.write_all(b"lazy-resource").unwrap();
            writer.finish().unwrap();
        }

        let mut vm = SharedVm::new(Rc::new(RefCell::new(1)), false);
        vm.load_jar(&jar).unwrap();
        assert!(!vm.resources.contains_key("asset.bin"));
        assert!(vm.lazy_resource_entries.contains_key("asset.bin"));

        let data = vm.get_resource("asset.bin").unwrap();
        assert_eq!(data.as_slice(), b"lazy-resource");
        assert!(vm.resources.contains_key("asset.bin"));
    }

    #[test]
    fn compact_boot_image_restores_classes_and_lazy_resources() {
        let jar = include_bytes!(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/j2me-rt/classes.debug.jar"
        ));
        let allocator = Rc::new(RefCell::new(1));
        let mut source_vm = SharedVm::new(Rc::clone(&allocator), false);
        source_vm.load_jar(jar).unwrap();
        let image = source_vm.encode_compact_boot_image(jar).unwrap();
        let mut vm = SharedVm::new(Rc::clone(&allocator), false);
        assert!(vm.install_compact_boot_image(&image));
        let source_object = source_vm.class_repo.get_class("java.lang.Object").unwrap();
        let restored_object = vm.class_repo.get_class("java.lang.Object").unwrap();
        assert_eq!(restored_object.methods, source_object.methods);
        for &method_id in &restored_object.methods {
            let source_method = source_vm.class_repo.get_method(method_id).unwrap();
            let restored_method = vm.class_repo.get_method(method_id).unwrap();
            assert_eq!(restored_method.method_id, method_id);
            assert_eq!(restored_method.class_id, restored_object.class_id);
            assert_eq!(restored_method.name_id, source_method.name_id);
            assert_eq!(restored_method.descriptor_id, source_method.descriptor_id);
            assert_eq!(restored_method.bytecode(), source_method.bytecode());
        }
        assert!(vm
            .lazy_resource_entries
            .contains_key("META-INF/MANIFEST.MF"));
        assert!(!vm.resources.contains_key("META-INF/MANIFEST.MF"));
        assert!(!vm.get_resource("META-INF/MANIFEST.MF").unwrap().is_empty());

        let mut truncated = image;
        truncated.pop();
        let mut truncated_vm = SharedVm::new(allocator, false);
        assert!(!truncated_vm.install_compact_boot_image(&truncated));
    }

    #[test]
    fn direct_buffer_arena_keeps_addresses_stable_and_roundtrips() {
        let mut arena = DirectBufferArena::default();
        let first = arena.allocate(4).expect("first allocation");
        arena.write_i32(first as i32, 0x1234_5678);

        let second = arena.allocate(8).expect("second allocation");
        arena.write_f32(second as i32, 1.25);
        assert_eq!(arena.read_i32(first as i32), Some(0x1234_5678));
        assert_eq!(arena.read_f32(second as i32), Some(1.25));

        assert!(arena.copy_within(first as i32, second as i32 + 4, 4));
        assert_eq!(arena.read_i32(second as i32 + 4), Some(0x1234_5678));

        let bytes = bincode::serialize(&arena).expect("serialize arena");
        let restored: DirectBufferArena = bincode::deserialize(&bytes).expect("deserialize arena");
        assert_eq!(restored.read_i32(first as i32), Some(0x1234_5678));
        assert_eq!(restored.read_f32(second as i32), Some(1.25));
    }

    #[test]
    fn test_image_surface_snapshot_roundtrips_pixels() {
        let snapshot = ImageSurfaceSnapshot {
            surface_id: 7,
            width: 4,
            height: 2,
            pixels: vec![
                0xff00_0000u32 as i32,
                0xff00_0000u32 as i32,
                0xffff_ffffu32 as i32,
                0xffff_ffffu32 as i32,
                0x0000_0000,
                0x0000_0000,
                0xff12_3456u32 as i32,
                0xff12_3456u32 as i32,
            ],
        };

        let encoded = bincode::serialize(&snapshot).expect("serialize snapshot");
        let decoded: ImageSurfaceSnapshot =
            bincode::deserialize(&encoded).expect("deserialize snapshot");

        assert_eq!(decoded.surface_id, snapshot.surface_id);
        assert_eq!(decoded.width, snapshot.width);
        assert_eq!(decoded.height, snapshot.height);
        assert_eq!(decoded.pixels, snapshot.pixels);
    }

    #[test]
    fn test_image_surface_snapshot_rle_beats_raw_for_flat_pixels() {
        let snapshot = ImageSurfaceSnapshot {
            surface_id: 1,
            width: 64,
            height: 64,
            pixels: vec![0; 64 * 64],
        };

        let serialized_size = bincode::serialized_size(&snapshot).expect("serialize snapshot");
        let raw_bytes = (snapshot.pixels.len() * std::mem::size_of::<i32>()) as u64;
        assert!(
            serialized_size < raw_bytes,
            "rle snapshot should be smaller than raw pixels for flat surfaces",
        );
    }
}

/// Returns true if the class name represents a primitive array (e.g. "[B", "[I", "[C")
/// These arrays contain no object references and GC can skip scanning them.
fn is_primitive_array_class(name: &str) -> bool {
    matches!(name, "[B" | "[Z" | "[C" | "[S" | "[I" | "[J" | "[F" | "[D")
}

pub struct ResolvedField {
    pub class_name: String,
    pub byte_offset: u32,
    pub is_wide: bool,
    pub is_static: bool,
}

/// ID-based resolved field (Phase 2): uses class_id instead of class_name String
pub struct ResolvedFieldById {
    pub class_id: u32,
    pub byte_offset: u32,
    pub is_wide: bool,
    pub is_static: bool,
}

/// ID-based resolved CP reference (Phase 3): class_id + names for error messages
pub struct ResolvedRef {
    pub class_id: u32,
    pub class_name: String,
    pub name: String,
    pub descriptor: String,
}
