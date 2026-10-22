#[cfg(not(target_arch = "wasm32"))]
use std::cell::RefCell;
#[cfg(not(target_arch = "wasm32"))]
use std::collections::HashMap;
use std::ffi::c_void;
use std::sync::atomic::{AtomicU32, Ordering};

static SHADOW_HANDLE_COUNTER: AtomicU32 = AtomicU32::new(0x8000_0000);

#[cfg(not(target_arch = "wasm32"))]
thread_local! {
    static NATIVE_HANDLES: RefCell<NativeHandleTable> = RefCell::new(NativeHandleTable::default());
}

#[cfg(not(target_arch = "wasm32"))]
#[derive(Default)]
struct NativeHandleTable {
    next: u32,
    native_by_java: HashMap<u32, usize>,
    java_by_native: HashMap<usize, u32>,
}

/// Converts a Java-visible handle to the m3gcore pointer expected by C.
pub fn native_handle(handle: usize) -> usize {
    #[cfg(target_arch = "wasm32")]
    {
        handle
    }
    #[cfg(not(target_arch = "wasm32"))]
    {
        if handle == 0 {
            return 0;
        }
        let java_handle = handle as u32;
        NATIVE_HANDLES.with(|handles| {
            handles
                .borrow()
                .native_by_java
                .get(&java_handle)
                .copied()
                .unwrap_or(0)
        })
    }
}

/// Converts a m3gcore pointer to the stable 32-bit Java handle.
pub fn java_handle(handle: usize) -> u32 {
    #[cfg(target_arch = "wasm32")]
    {
        handle as u32
    }
    #[cfg(not(target_arch = "wasm32"))]
    {
        if handle == 0 {
            return 0;
        }
        NATIVE_HANDLES.with(|handles| {
            let mut handles = handles.borrow_mut();
            if let Some(existing) = handles.java_by_native.get(&handle) {
                return *existing;
            }
            let next = handles.next.max(1);
            handles.next = next.checked_add(1).expect("M3G handle space exhausted");
            handles.native_by_java.insert(next, handle);
            handles.java_by_native.insert(handle, next);
            next
        })
    }
}

/// Looks up an existing stable handle without creating a new Java-visible
/// object identity. GPU command capture must never manufacture object handles.
pub fn stable_handle(handle: usize) -> u32 {
    #[cfg(target_arch = "wasm32")]
    {
        handle as u32
    }
    #[cfg(not(target_arch = "wasm32"))]
    {
        if handle == 0 {
            return 0;
        }
        NATIVE_HANDLES.with(|handles| {
            handles
                .borrow()
                .java_by_native
                .get(&handle)
                .copied()
                .unwrap_or(0)
        })
    }
}

pub fn release_java_handle(handle: usize) {
    #[cfg(target_arch = "wasm32")]
    let _ = handle;
    #[cfg(not(target_arch = "wasm32"))]
    if handle != 0 {
        let java_handle = handle as u32;
        NATIVE_HANDLES.with(|handles| {
            let mut handles = handles.borrow_mut();
            if let Some(native) = handles.native_by_java.remove(&java_handle) {
                handles.java_by_native.remove(&native);
            }
        });
    }
}

pub fn next_shadow_handle() -> u32 {
    SHADOW_HANDLE_COUNTER.fetch_add(1, Ordering::Relaxed)
}

pub fn is_shadow_handle(handle: usize) -> bool {
    handle >= 0x8000_0000
}

#[cfg(all(test, not(target_arch = "wasm32")))]
mod tests {
    use super::{java_handle, native_handle, release_java_handle, stable_handle};

    #[test]
    fn native_handle_table_preserves_64_bit_pointers() {
        let native = 0x1234_5678_9abc_def0usize;
        let java = java_handle(native);

        assert_ne!(java, 0);
        assert_eq!(java_handle(native), java);
        assert_eq!(stable_handle(native), java);
        assert_eq!(native_handle(java as usize), native);

        release_java_handle(java as usize);
        assert_eq!(native_handle(java as usize), 0);
        assert_eq!(stable_handle(native), 0);
    }
}

#[repr(C)]
pub struct M3Gparams {
    pub malloc_func: Option<unsafe extern "C" fn(bytes: u32) -> *mut c_void>,
    pub free_func: Option<unsafe extern "C" fn(ptr: *mut c_void)>,
    pub obj_alloc_func: Option<unsafe extern "C" fn(bytes: u32) -> *mut c_void>,
    pub obj_free_func: Option<unsafe extern "C" fn(ptr: *mut c_void)>,
    pub obj_resolve_func: Option<unsafe extern "C" fn(handle: *mut c_void) -> *mut c_void>,
    pub error_func: Option<unsafe extern "C" fn(error: i32, user_data: *mut c_void)>,
    pub begin_render_func: Option<unsafe extern "C" fn(target: *mut c_void)>,
    pub end_render_func: Option<unsafe extern "C" fn(target: *mut c_void)>,
    pub release_target_func: Option<unsafe extern "C" fn(target: *mut c_void)>,
    pub user_context: *mut c_void,
}

macro_rules! m3g_api_type {
    (Handle) => {
        usize
    };
    (($ty:ty)) => {
        $ty
    };
    ($ty:ty) => {
        $ty
    };
}

macro_rules! m3g_raw_type {
    (Handle) => {
        usize
    };
    (($ty:ty)) => {
        $ty
    };
    ($ty:ty) => {
        $ty
    };
}

macro_rules! m3g_raw_arg {
    ($value:ident, Handle) => {
        native_handle($value as usize)
    };
    ($value:ident, ($ty:ty)) => {
        $value
    };
    ($value:ident, $ty:ty) => {
        $value
    };
}

macro_rules! m3g_api {
    (fn $name:ident($($arg:ident: $ty:tt),* $(,)?) -> Handle;) => {
        #[allow(non_snake_case)]
        pub unsafe fn $name($($arg: m3g_api_type!($ty)),*) -> u32 {
            unsafe extern "C" {
                fn $name($($arg: m3g_raw_type!($ty)),*) -> usize;
            }
            java_handle(unsafe { $name($(m3g_raw_arg!($arg, $ty)),*) })
        }
    };
    (fn $name:ident($($arg:ident: $ty:tt),* $(,)?) -> $return_type:ty;) => {
        #[allow(non_snake_case)]
        pub unsafe fn $name($($arg: m3g_api_type!($ty)),*) -> $return_type {
            unsafe extern "C" {
                fn $name($($arg: m3g_raw_type!($ty)),*) -> $return_type;
            }
            unsafe { $name($(m3g_raw_arg!($arg, $ty)),*) }
        }
    };
    (fn $name:ident($($arg:ident: $ty:tt),* $(,)?);) => {
        #[allow(non_snake_case)]
        pub unsafe fn $name($($arg: m3g_api_type!($ty)),*) {
            unsafe extern "C" {
                fn $name($($arg: m3g_raw_type!($ty)),*);
            }
            unsafe { $name($(m3g_raw_arg!($arg, $ty)),*) }
        }
    };
}

macro_rules! m3g_raw_api {
    (fn $name:ident($($arg:ident: $ty:ty),* $(,)?) -> $return_type:ty;) => {
        unsafe extern "C" {
            pub fn $name($($arg: $ty),*) -> $return_type;
        }
    };
    (fn $name:ident($($arg:ident: $ty:ty),* $(,)?);) => {
        unsafe extern "C" {
            pub fn $name($($arg: $ty),*);
        }
    };
}

fn native_handles(handles: &[u32]) -> Vec<usize> {
    handles
        .iter()
        .map(|&handle| native_handle(handle as usize))
        .collect()
}

/// Duplicates an object while keeping m3gcore's temporary native-pointer
/// pairs entirely inside the FFI boundary.
pub unsafe fn m3gDuplicate(h_object: usize, pair_count: usize) -> u32 {
    unsafe extern "C" {
        fn m3gDuplicate(hObject: usize, hReferences: *mut usize) -> usize;
    }
    let mut pairs = vec![0usize; pair_count.saturating_mul(2)];
    let duplicate = unsafe {
        m3gDuplicate(
            native_handle(h_object),
            if pairs.is_empty() {
                std::ptr::null_mut()
            } else {
                pairs.as_mut_ptr()
            },
        )
    };
    // The pairs contain original/clone pointers. Register every clone now so
    // command capture can resolve duplicated child objects before Java asks
    // for them individually.
    for native in pairs {
        if native != 0 {
            java_handle(native);
        }
    }
    java_handle(duplicate)
}

/// Returns stable Java handles even though m3gcore writes native pointers.
pub unsafe fn m3gGetReferences(h_object: usize, references: Option<&mut [u32]>) -> i32 {
    unsafe extern "C" {
        fn m3gGetReferences(hObject: usize, references: *mut usize, length: i32) -> i32;
    }
    let native_object = native_handle(h_object);
    let Some(references) = references else {
        return unsafe { m3gGetReferences(native_object, std::ptr::null_mut(), 0) };
    };
    let mut native_references = vec![0usize; references.len()];
    let count = unsafe {
        m3gGetReferences(
            native_object,
            native_references.as_mut_ptr(),
            native_references.len() as i32,
        )
    };
    for (destination, native) in references.iter_mut().zip(native_references) {
        *destination = java_handle(native);
    }
    count
}

pub unsafe fn m3gImportObjects(loader: usize, references: &[u32]) {
    unsafe extern "C" {
        fn m3gImportObjects(loader: usize, n: i32, refs: *mut usize);
    }
    let mut native_references = native_handles(references);
    unsafe {
        m3gImportObjects(
            native_handle(loader),
            native_references.len() as i32,
            native_references.as_mut_ptr(),
        )
    };
}

pub unsafe fn m3gGetLoadedObjects(loader: usize) -> Vec<u32> {
    unsafe extern "C" {
        fn m3gGetLoadedObjects(loader: usize, buffer: *mut usize) -> i32;
    }
    let native_loader = native_handle(loader);
    let count = unsafe { m3gGetLoadedObjects(native_loader, std::ptr::null_mut()) };
    let mut native_objects = vec![0usize; count.max(0) as usize];
    if !native_objects.is_empty() {
        unsafe { m3gGetLoadedObjects(native_loader, native_objects.as_mut_ptr()) };
    }
    native_objects.into_iter().map(java_handle).collect()
}

pub unsafe fn m3gCreateMesh(
    interface: usize,
    vertices: usize,
    triangles: &[u32],
    appearances: Option<&[u32]>,
) -> u32 {
    unsafe extern "C" {
        fn m3gCreateMesh(
            interface: usize,
            hVertices: usize,
            hTriangles: *mut usize,
            hAppearances: *mut usize,
            trianglePatchCount: i32,
        ) -> usize;
    }
    let mut native_triangles = native_handles(triangles);
    let mut native_appearances = appearances.map(native_handles);
    java_handle(unsafe {
        m3gCreateMesh(
            native_handle(interface),
            native_handle(vertices),
            native_triangles.as_mut_ptr(),
            native_appearances
                .as_mut()
                .map_or(std::ptr::null_mut(), |handles| handles.as_mut_ptr()),
            native_triangles.len() as i32,
        )
    })
}

pub unsafe fn m3gCreateMorphingMesh(
    interface: usize,
    vertices: usize,
    targets: &[u32],
    triangles: &[u32],
    appearances: Option<&[u32]>,
) -> u32 {
    unsafe extern "C" {
        fn m3gCreateMorphingMesh(
            interface: usize,
            hVertices: usize,
            hTargets: *mut usize,
            hTriangles: *mut usize,
            hAppearances: *mut usize,
            trianglePatchCount: i32,
            targetCount: i32,
        ) -> usize;
    }
    let mut native_targets = native_handles(targets);
    let mut native_triangles = native_handles(triangles);
    let mut native_appearances = appearances.map(native_handles);
    java_handle(unsafe {
        m3gCreateMorphingMesh(
            native_handle(interface),
            native_handle(vertices),
            native_targets.as_mut_ptr(),
            native_triangles.as_mut_ptr(),
            native_appearances
                .as_mut()
                .map_or(std::ptr::null_mut(), |handles| handles.as_mut_ptr()),
            native_triangles.len() as i32,
            native_targets.len() as i32,
        )
    })
}

pub unsafe fn m3gCreateSkinnedMesh(
    interface: usize,
    vertices: usize,
    triangles: &[u32],
    appearances: Option<&[u32]>,
    skeleton: usize,
) -> u32 {
    unsafe extern "C" {
        fn m3gCreateSkinnedMesh(
            interface: usize,
            hVertices: usize,
            hTriangles: *mut usize,
            hAppearances: *mut usize,
            trianglePatchCount: i32,
            hSkeleton: usize,
        ) -> usize;
    }
    let mut native_triangles = native_handles(triangles);
    let mut native_appearances = appearances.map(native_handles);
    java_handle(unsafe {
        m3gCreateSkinnedMesh(
            native_handle(interface),
            native_handle(vertices),
            native_triangles.as_mut_ptr(),
            native_appearances
                .as_mut()
                .map_or(std::ptr::null_mut(), |handles| handles.as_mut_ptr()),
            native_triangles.len() as i32,
            native_handle(skeleton),
        )
    })
}

m3g_api! { fn m3gCreateInterface(params: (*const M3Gparams)) -> Handle; }
m3g_api! { fn m3gDeleteInterface(interface: Handle); }
m3g_api! { fn m3gGetError(interface: Handle) -> u32; }

m3g_api! { fn m3gGetClass(hObject: Handle) -> i32; }
m3g_api! { fn m3gDeleteObject(hObject: Handle); }
m3g_api! { fn m3gAddRef(hObject: Handle); }
m3g_api! { fn m3gDeleteRef(hObject: Handle); }
m3g_api! { fn m3gAnimate(hObject: Handle, time: i32) -> i32; }
m3g_api! { fn m3gSetUserID(hObject: Handle, userID: i32); }
m3g_api! { fn m3gGetUserID(hObject: Handle) -> i32; }
m3g_api! { fn m3gAddAnimationTrack(hObject: Handle, hTrack: Handle) -> i32; }
m3g_api! { fn m3gRemoveAnimationTrack(hObject: Handle, hTrack: Handle); }
m3g_api! { fn m3gGetAnimationTrackCount(hObject: Handle) -> i32; }
m3g_api! { fn m3gGetAnimationTrack(hObject: Handle, idx: i32) -> Handle; }
m3g_api! { fn m3gFind(hObject: Handle, userID: i32) -> Handle; }

// AnimationController / AnimationTrack / KeyframeSequence
m3g_api! { fn m3gCreateAnimationController(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetActiveInterval(handle: Handle, worldTimeMin: i32, worldTimeMax: i32); }
m3g_api! { fn m3gGetActiveIntervalStart(handle: Handle) -> i32; }
m3g_api! { fn m3gGetActiveIntervalEnd(handle: Handle) -> i32; }
m3g_api! { fn m3gSetSpeed(handle: Handle, factor: f32, worldTime: i32); }
m3g_api! { fn m3gGetSpeed(handle: Handle) -> f32; }
m3g_api! { fn m3gSetPosition(handle: Handle, sequenceTime: f32, worldTime: i32); }
m3g_api! { fn m3gGetPosition(handle: Handle, worldTime: i32) -> f32; }
m3g_api! { fn m3gSetWeight(handle: Handle, weight: f32); }
m3g_api! { fn m3gGetWeight(handle: Handle) -> f32; }
m3g_api! { fn m3gGetRefWorldTime(handle: Handle) -> i32; }
m3g_api! { fn m3gCreateAnimationTrack(interface: Handle, hSequence: Handle, property: i32) -> Handle; }
m3g_api! { fn m3gGetController(handle: Handle) -> Handle; }
m3g_api! { fn m3gGetSequence(handle: Handle) -> Handle; }
m3g_api! { fn m3gGetTargetProperty(handle: Handle) -> i32; }
m3g_api! { fn m3gSetController(handle: Handle, hController: Handle); }
m3g_api! { fn m3gCreateKeyframeSequence(
    interface: Handle,
    numKeyframes: i32,
    numComponents: i32,
    interpolation: i32,
) -> Handle; }
m3g_api! { fn m3gSetKeyframe(handle: Handle, index: i32, time: i32, valueSize: i32, value: (*const f32)); }
m3g_api! { fn m3gSetValidRange(handle: Handle, first: i32, last: i32); }
m3g_api! { fn m3gGetValidRange(handle: Handle, first: (*mut i32), last: (*mut i32)); }
m3g_api! { fn m3gSetDuration(handle: Handle, duration: i32); }
m3g_api! { fn m3gGetDuration(handle: Handle) -> i32; }
m3g_api! { fn m3gSetRepeatMode(handle: Handle, mode: i32); }
m3g_api! { fn m3gGetRepeatMode(handle: Handle) -> i32; }
m3g_api! { fn m3gGetComponentCount(handle: Handle) -> i32; }
m3g_api! { fn m3gGetInterpolationType(handle: Handle) -> i32; }
m3g_api! { fn m3gGetKeyframe(handle: Handle, index: i32, value: (*mut f32)) -> i32; }
m3g_api! { fn m3gGetKeyframeCount(handle: Handle) -> i32; }

// Node / Group
m3g_api! { fn m3gCreateGroup(interface: Handle) -> Handle; }
m3g_api! { fn m3gAddChild(handle: Handle, hNode: Handle); }
m3g_api! { fn m3gRemoveChild(handle: Handle, hNode: Handle); }
m3g_api! { fn m3gGetChildCount(handle: Handle) -> i32; }
m3g_api! { fn m3gGetChild(handle: Handle, idx: i32) -> Handle; }
m3g_api! { fn m3gGetTransformTo(handle: Handle, hTarget: Handle, transform: (*mut u8)) -> i32; }
m3g_api! { fn m3gSetAlignment(
    handle: Handle,
    hZReference: Handle,
    zTarget: i32,
    hYReference: Handle,
    yTarget: i32,
); }
m3g_api! { fn m3gAlignNode(handle: Handle, hRef: Handle); }
m3g_api! { fn m3gSetAlphaFactor(handle: Handle, alphaFactor: f32); }
m3g_api! { fn m3gGetAlphaFactor(handle: Handle) -> f32; }
m3g_api! { fn m3gEnable(handle: Handle, which: i32, enable: i32); }
m3g_api! { fn m3gIsEnabled(handle: Handle, which: i32) -> i32; }
m3g_api! { fn m3gSetScope(handle: Handle, id: i32); }
m3g_api! { fn m3gGetScope(handle: Handle) -> i32; }
m3g_api! { fn m3gGetParent(handle: Handle) -> Handle; }
m3g_api! { fn m3gGetYRef(handle: Handle) -> Handle; }
m3g_api! { fn m3gGetZRef(handle: Handle) -> Handle; }
m3g_api! { fn m3gGetSubtreeSize(handle: Handle) -> i32; }
m3g_api! { fn m3gGetAlignmentTarget(handle: Handle, axis: i32) -> i32; }
m3g_api! { fn m3gPick2D(
    handle: Handle,
    mask: i32,
    x: f32,
    y: f32,
    hCamera: Handle,
    result: (*mut f32),
) -> Handle; }
m3g_api! { fn m3gPick3D(handle: Handle, mask: i32, ray: (*mut f32), result: (*mut f32)) -> Handle; }

m3g_api! { fn m3gCreateContext(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetViewport(hCtx: Handle, x: i32, y: i32, width: i32, height: i32); }
m3g_api! { fn m3gClear(context: Handle, hBackground: Handle); }
m3g_api! { fn m3gClearLights(context: Handle); }
m3g_api! { fn m3gSetCamera(context: Handle, hCamera: Handle, transform: (*mut u8)); }
m3g_api! { fn m3gAddLight(hCtx: Handle, hLight: Handle, transform: (*const u8)) -> i32; }
m3g_api! { fn m3gSetLight(context: Handle, lightIndex: i32, hLight: Handle, transform: (*const u8)); }
m3g_api! { fn m3gRender(
    context: Handle,
    hVertices: Handle,
    hIndices: Handle,
    hAppearance: Handle,
    transform: (*const u8),
    alphaFactor: f32,
    scope: i32,
); }
m3g_api! { fn m3gRenderNode(context: Handle, hNode: Handle, transform: (*const u8)); }
m3g_api! { fn m3gRenderWorld(context: Handle, hWorld: Handle); }
m3g_raw_api! { fn m3gWasmEndCommandCapture(); }
m3g_raw_api! { fn m3gWasmGetScissor(x: *mut i32, y: *mut i32, width: *mut i32, height: *mut i32); }
m3g_raw_api! { fn m3gWasmRegisterExternalVertexArray(handle: u32); }
m3g_raw_api! { fn m3gWasmBumpResourceVersion(kind: u32, handle: u32) -> u32; }
m3g_raw_api! { fn m3gWasmReleaseResourceVersion(kind: u32, handle: u32); }

pub unsafe fn m3gWasmBeginCommandCapture(owner: usize, sink: *mut c_void) -> i32 {
    unsafe extern "C" {
        fn m3gWasmBeginCommandCapture(owner: *mut c_void, sink: *mut c_void) -> i32;
    }
    unsafe { m3gWasmBeginCommandCapture(native_handle(owner) as *mut c_void, sink) }
}
m3g_api! { fn m3gSetDepthRange(hCtx: Handle, depthNear: f32, depthFar: f32); }
m3g_api! { fn m3gGetDepthRange(hCtx: Handle, depthNear: (*mut f32), depthFar: (*mut f32)); }
m3g_api! { fn m3gGetViewport(hCtx: Handle, x: (*mut i32), y: (*mut i32), w: (*mut i32), h: (*mut i32)); }
m3g_api! { fn m3gGetCamera(hCtx: Handle) -> Handle; }
m3g_api! { fn m3gGetLightTransform(hCtx: Handle, lightIndex: i32, transform: (*mut f32)) -> Handle; }
m3g_api! { fn m3gGetLightCount(hCtx: Handle) -> i32; }
m3g_api! { fn m3gGetViewTransform(hCtx: Handle, transform: (*mut f32)); }
m3g_api! { fn m3gSetRenderBuffers(hCtx: Handle, bufferBits: u32) -> i32; }
m3g_api! { fn m3gSetRenderHints(hCtx: Handle, modeBits: u32) -> i32; }
m3g_api! { fn m3gBindWasmTarget(hCtx: Handle, handle: u32, width: i32, height: i32) -> i32; }
m3g_api! { fn m3gBindImageTarget(hCtx: Handle, hImage: Handle); }
m3g_api! { fn m3gReleaseTarget(context: Handle); }
m3g_api! { fn m3gReleaseWasmTarget(context: Handle); }
m3g_api! { fn m3gSetDisplayArea(hCtx: Handle, width: i32, height: i32); }
m3g_api! { fn m3gSetClipRect(hCtx: Handle, x: i32, y: i32, width: i32, height: i32); }
m3g_api! { fn m3gFreeGLESResources(ctx: Handle); }
m3g_api! { fn m3gIsAntialiasingSupported(interface: Handle) -> i32; }

// Loader
m3g_api! { fn m3gCreateLoader(interface: Handle) -> Handle; }
m3g_api! { fn m3gDecodeData(loader: Handle, bytes: i32, data: (*const u8)) -> i32; }

// VertexArray
m3g_api! { fn m3gCreateVertexArray(
    interface: Handle,
    count: i32,
    size: i32,
    componentType: i32,
) -> Handle; }
m3g_api! { fn m3gSetVertexArrayElements(
    handle: Handle,
    first: i32,
    count: i32,
    srcLength: i32,
    srcType: i32,
    src: (*const u8),
); }
m3g_api! { fn m3gGetVertexArrayElements(
    handle: Handle,
    first: i32,
    count: i32,
    dstLength: i32,
    dstType: i32,
    dst: (*mut u8),
); }
m3g_api! { fn m3gGetVertexArrayParams(
    handle: Handle,
    count: (*mut i32),
    size: (*mut i32),
    dataType: (*mut i32),
    stride: (*mut i32),
); }

// VertexBuffer
m3g_api! { fn m3gCreateVertexBuffer(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetVertexArray(
    hBuffer: Handle,
    hArray: Handle,
    scale: f32,
    bias: (*const f32),
    biasLength: i32,
); }
m3g_api! { fn m3gSetNormalArray(hBuffer: Handle, hArray: Handle); }
m3g_api! { fn m3gSetColorArray(hBuffer: Handle, hArray: Handle); }
m3g_api! { fn m3gSetTexCoordArray(
    hBuffer: Handle,
    unit: i32,
    hArray: Handle,
    scale: f32,
    bias: (*const f32),
    biasLength: i32,
); }
m3g_api! { fn m3gSetVertexDefaultColor(handle: Handle, argb: u32); }
m3g_api! { fn m3gGetVertexDefaultColor(handle: Handle) -> u32; }
m3g_api! { fn m3gGetVertexArray(
    hBuffer: Handle,
    which: i32,
    scaleBias: (*mut f32),
    sbLength: i32,
) -> Handle; }
m3g_api! { fn m3gGetVertexCount(handle: Handle) -> i32; }

// IndexBuffer (TriangleStripArray)
m3g_api! { fn m3gCreateImplicitStripBuffer(
    interface: Handle,
    stripCount: i32,
    stripLengths: (*const i32),
    firstIndex: i32,
) -> Handle; }
m3g_api! { fn m3gCreateStripBuffer(
    interface: Handle,
    primitive: i32,
    stripCount: i32,
    stripLengths: (*const i32),
    indexType: i32,
    numIndices: i32,
    indices: (*const u8),
) -> Handle; }
m3g_api! { fn m3gGetStripIndexCount(handle: Handle) -> i32; }
m3g_api! { fn m3gGetStripIndices(handle: Handle, indices: (*mut i32)); }

// Appearance
m3g_api! { fn m3gCreateAppearance(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetCompositingMode(hApp: Handle, hMode: Handle); }
m3g_api! { fn m3gSetFog(hApp: Handle, hFog: Handle); }
m3g_api! { fn m3gSetMaterial(hApp: Handle, hMaterial: Handle); }
m3g_api! { fn m3gSetPolygonMode(hApp: Handle, hMode: Handle); }
m3g_api! { fn m3gSetTexture(hApp: Handle, unit: i32, hTexture: Handle); }
m3g_api! { fn m3gSetLayer(hApp: Handle, layer: i32); }
m3g_api! { fn m3gGetCompositingMode(hApp: Handle) -> Handle; }
m3g_api! { fn m3gGetFog(hApp: Handle) -> Handle; }
m3g_api! { fn m3gGetMaterial(hApp: Handle) -> Handle; }
m3g_api! { fn m3gGetPolygonMode(hApp: Handle) -> Handle; }
m3g_api! { fn m3gGetTexture(hApp: Handle, unit: i32) -> Handle; }
m3g_api! { fn m3gGetLayer(hApp: Handle) -> i32; }

// Image2D / Texture2D
m3g_api! { fn m3gCreateImage(
    interface: Handle,
    srcFormat: i32,
    width: i32,
    height: i32,
    flags: u32,
) -> Handle; }
m3g_api! { fn m3gSetImage(hImage: Handle, srcPixels: (*const u8)); }
m3g_api! { fn m3gSetImagePalette(hImage: Handle, paletteLength: i32, srcPalette: (*const u8)); }
m3g_api! { fn m3gCommitImage(hImage: Handle); }
m3g_api! { fn m3gIsMutable(hImage: Handle) -> i32; }
m3g_api! { fn m3gSetSubImage(
    hImage: Handle,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    length: i32,
    pixels: (*const u8),
); }
m3g_api! { fn m3gCreateTexture(interface: Handle, hImage: Handle) -> Handle; }
m3g_api! { fn m3gSetTextureImage(hTexture: Handle, hImage: Handle); }
m3g_api! { fn m3gSetFiltering(hTexture: Handle, levelFilter: i32, imageFilter: i32); }
m3g_api! { fn m3gGetFiltering(hTexture: Handle, levelFilter: (*mut i32), imageFilter: (*mut i32)); }
m3g_api! { fn m3gSetWrapping(hTexture: Handle, wrapS: i32, wrapT: i32); }
m3g_api! { fn m3gGetWrappingS(hTexture: Handle) -> i32; }
m3g_api! { fn m3gGetWrappingT(hTexture: Handle) -> i32; }
m3g_api! { fn m3gTextureSetBlending(hTexture: Handle, func: i32); }
m3g_api! { fn m3gTextureGetBlending(hTexture: Handle) -> i32; }
m3g_api! { fn m3gSetBlendColor(hTexture: Handle, rgb: u32); }
m3g_api! { fn m3gGetBlendColor(hTexture: Handle) -> u32; }

// Material
m3g_api! { fn m3gCreateMaterial(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetColor(hMaterial: Handle, target: i32, argb: u32); }
m3g_api! { fn m3gGetColor(hMaterial: Handle, target: i32) -> u32; }
m3g_api! { fn m3gSetShininess(hMaterial: Handle, shininess: f32); }
m3g_api! { fn m3gGetShininess(hMaterial: Handle) -> f32; }
m3g_api! { fn m3gSetVertexColorTrackingEnable(hMaterial: Handle, enable: i32); }
m3g_api! { fn m3gIsVertexColorTrackingEnabled(hMaterial: Handle) -> i32; }

// Light
m3g_api! { fn m3gCreateLight(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetIntensity(handle: Handle, intensity: f32); }
m3g_api! { fn m3gGetIntensity(handle: Handle) -> f32; }
m3g_api! { fn m3gSetLightColor(handle: Handle, rgb: u32); }
m3g_api! { fn m3gGetLightColor(handle: Handle) -> u32; }
m3g_api! { fn m3gSetLightMode(handle: Handle, mode: i32); }
m3g_api! { fn m3gGetLightMode(handle: Handle) -> i32; }
m3g_api! { fn m3gSetSpotAngle(handle: Handle, angle: f32); }
m3g_api! { fn m3gGetSpotAngle(handle: Handle) -> f32; }
m3g_api! { fn m3gSetSpotExponent(handle: Handle, exponent: f32); }
m3g_api! { fn m3gGetSpotExponent(handle: Handle) -> f32; }
m3g_api! { fn m3gSetAttenuation(handle: Handle, constant: f32, linear: f32, quadratic: f32); }
m3g_api! { fn m3gGetAttenuation(handle: Handle, which: i32) -> f32; }

// Fog
m3g_api! { fn m3gCreateFog(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetFogMode(handle: Handle, mode: i32); }
m3g_api! { fn m3gGetFogMode(handle: Handle) -> i32; }
m3g_api! { fn m3gSetFogLinear(handle: Handle, fogNear: f32, fogFar: f32); }
m3g_api! { fn m3gGetFogDistance(handle: Handle, which: i32) -> f32; }
m3g_api! { fn m3gSetFogDensity(handle: Handle, density: f32); }
m3g_api! { fn m3gGetFogDensity(handle: Handle) -> f32; }
m3g_api! { fn m3gSetFogColor(handle: Handle, rgb: u32); }
m3g_api! { fn m3gGetFogColor(handle: Handle) -> u32; }

// World
m3g_api! { fn m3gCreateWorld(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetActiveCamera(handle: Handle, hCamera: Handle); }
m3g_api! { fn m3gGetActiveCamera(handle: Handle) -> Handle; }
m3g_api! { fn m3gSetBackground(handle: Handle, hBackground: Handle); }
m3g_api! { fn m3gGetBackground(handle: Handle) -> Handle; }

// Camera
m3g_api! { fn m3gCreateCamera(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetParallel(
    handle: Handle,
    height: f32,
    aspectRatio: f32,
    clipNear: f32,
    clipFar: f32,
); }
m3g_api! { fn m3gSetPerspective(
    handle: Handle,
    fovy: f32,
    aspectRatio: f32,
    clipNear: f32,
    clipFar: f32,
); }
m3g_api! { fn m3gSetProjectionMatrix(handle: Handle, transform: (*const u8)); }
m3g_api! { fn m3gGetProjectionAsParams(handle: Handle, params: (*mut f32)) -> i32; }
m3g_api! { fn m3gGetProjectionAsMatrix(handle: Handle, transform: (*mut u8)) -> i32; }

// Background
m3g_api! { fn m3gCreateBackground(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetBgColor(handle: Handle, argb: u32); }
m3g_api! { fn m3gGetBgColor(handle: Handle) -> u32; }
m3g_api! { fn m3gSetBgImage(handle: Handle, hImage: Handle); }
m3g_api! { fn m3gGetBgImage(handle: Handle) -> Handle; }
m3g_api! { fn m3gSetBgMode(handle: Handle, modeX: i32, modeY: i32); }
m3g_api! { fn m3gGetBgMode(handle: Handle, which: i32) -> i32; }
m3g_api! { fn m3gSetBgEnable(handle: Handle, which: i32, enable: i32); }
m3g_api! { fn m3gIsBgEnabled(handle: Handle, which: i32) -> i32; }
m3g_api! { fn m3gSetBgCrop(handle: Handle, x: i32, y: i32, w: i32, h: i32); }
m3g_api! { fn m3gGetBgCrop(handle: Handle, which: i32) -> i32; }

// Mesh
m3g_api! { fn m3gGetVertexBuffer(handle: Handle) -> Handle; }
m3g_api! { fn m3gGetIndexBuffer(handle: Handle, idx: i32) -> Handle; }
m3g_api! { fn m3gGetAppearance(handle: Handle, idx: i32) -> Handle; }
m3g_api! { fn m3gSetAppearance(handle: Handle, idx: i32, hAppearance: Handle); }
m3g_api! { fn m3gGetSubmeshCount(handle: Handle) -> i32; }

// Sprite3D
m3g_api! { fn m3gCreateSprite(
    interface: Handle,
    scaled: i32,
    hImage: Handle,
    hAppearance: Handle,
) -> Handle; }
m3g_api! { fn m3gIsScaledSprite(handle: Handle) -> i32; }
m3g_api! { fn m3gSetSpriteAppearance(handle: Handle, hAppearance: Handle); }
m3g_api! { fn m3gSetSpriteImage(handle: Handle, hImage: Handle) -> i32; }
m3g_api! { fn m3gSetCrop(handle: Handle, cropX: i32, cropY: i32, width: i32, height: i32); }
m3g_api! { fn m3gGetCrop(handle: Handle, which: i32) -> i32; }
m3g_api! { fn m3gGetSpriteAppearance(handle: Handle) -> Handle; }
m3g_api! { fn m3gGetSpriteImage(handle: Handle) -> Handle; }

// MorphingMesh
m3g_api! { fn m3gSetWeights(handle: Handle, weights: (*mut f32), numWeights: i32); }
m3g_api! { fn m3gGetWeights(handle: Handle, weights: (*mut f32), numWeights: i32); }
m3g_api! { fn m3gGetMorphTarget(handle: Handle, idx: i32) -> Handle; }
m3g_api! { fn m3gGetMorphTargetCount(handle: Handle) -> i32; }

// SkinnedMesh
m3g_api! { fn m3gAddTransform(
    handle: Handle,
    hNode: Handle,
    weight: i32,
    firstVertex: i32,
    numVertices: i32,
); }
m3g_api! { fn m3gGetSkeleton(handle: Handle) -> Handle; }
m3g_api! { fn m3gGetBoneTransform(handle: Handle, hBone: Handle, transform: (*mut u8)); }
m3g_api! { fn m3gGetBoneVertices(
    handle: Handle,
    hBone: Handle,
    indices: (*mut i32),
    weights: (*mut f32),
) -> i32; }

// Transformable
m3g_api! { fn m3gSetOrientation(handle: Handle, angle: f32, ax: f32, ay: f32, az: f32); }
m3g_api! { fn m3gPostRotate(handle: Handle, angle: f32, ax: f32, ay: f32, az: f32); }
m3g_api! { fn m3gPreRotate(handle: Handle, angle: f32, ax: f32, ay: f32, az: f32); }
m3g_api! { fn m3gGetOrientation(handle: Handle, angleAxis: (*mut f32)); }
m3g_api! { fn m3gSetScale(handle: Handle, sx: f32, sy: f32, sz: f32); }
m3g_api! { fn m3gScale(handle: Handle, sx: f32, sy: f32, sz: f32); }
m3g_api! { fn m3gGetScale(handle: Handle, scale: (*mut f32)); }
m3g_api! { fn m3gSetTranslation(handle: Handle, tx: f32, ty: f32, tz: f32); }
m3g_api! { fn m3gTranslate(handle: Handle, tx: f32, ty: f32, tz: f32); }
m3g_api! { fn m3gGetTranslation(handle: Handle, translation: (*mut f32)); }
m3g_api! { fn m3gSetTransform(handle: Handle, transform: (*const u8)); }
m3g_api! { fn m3gGetTransform(handle: Handle, transform: (*mut u8)); }
m3g_api! { fn m3gGetCompositeTransform(handle: Handle, transform: (*mut u8)); }

// Transform (Matrix operations)
m3g_raw_api! { fn m3gIdentityMatrix(mtx: *mut u8); }
m3g_raw_api! { fn m3gPostMultiplyMatrix(mtx: *mut u8, other: *const u8); }
m3g_raw_api! { fn m3gPostRotateMatrix(mtx: *mut u8, angle: f32, ax: f32, ay: f32, az: f32); }
m3g_raw_api! { fn m3gPostRotateMatrixQuat(mtx: *mut u8, quat: *const f32); }
m3g_raw_api! { fn m3gPostScaleMatrix(mtx: *mut u8, sx: f32, sy: f32, sz: f32); }
m3g_raw_api! { fn m3gPostTranslateMatrix(mtx: *mut u8, tx: f32, ty: f32, tz: f32); }
m3g_raw_api! { fn m3gInvertMatrix(mtx: *mut u8) -> i32; }
m3g_raw_api! { fn m3gMatrixTranspose(dst: *mut u8, src: *const u8); }
m3g_raw_api! { fn m3gGetMatrixRows(mtx: *const u8, dst: *mut f32); }
m3g_raw_api! { fn m3gSetMatrixRows(mtx: *mut u8, src: *const f32); }
m3g_raw_api! { fn m3gMatrixProduct(dst: *mut u8, left: *const u8, right: *const u8); }
m3g_api! { fn m3gTransformArray(
    handle: Handle,
    transform: (*mut u8),
    out: (*mut f32),
    outLength: i32,
    w: i32,
); }

// PolygonMode
m3g_api! { fn m3gCreatePolygonMode(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetCulling(handle: Handle, mode: i32); }
m3g_api! { fn m3gGetCulling(handle: Handle) -> i32; }
m3g_api! { fn m3gSetWinding(handle: Handle, mode: i32); }
m3g_api! { fn m3gGetWinding(handle: Handle) -> i32; }
m3g_api! { fn m3gSetShading(handle: Handle, mode: i32); }
m3g_api! { fn m3gGetShading(handle: Handle) -> i32; }
m3g_api! { fn m3gSetTwoSidedLightingEnable(handle: Handle, enable: i32); }
m3g_api! { fn m3gIsTwoSidedLightingEnabled(handle: Handle) -> i32; }
m3g_api! { fn m3gSetLocalCameraLightingEnable(handle: Handle, enable: i32); }
m3g_api! { fn m3gIsLocalCameraLightingEnabled(handle: Handle) -> i32; }
m3g_api! { fn m3gSetPerspectiveCorrectionEnable(handle: Handle, enable: i32); }
m3g_api! { fn m3gIsPerspectiveCorrectionEnabled(handle: Handle) -> i32; }

// CompositingMode
m3g_api! { fn m3gCreateCompositingMode(interface: Handle) -> Handle; }
m3g_api! { fn m3gSetBlending(handle: Handle, mode: i32); }
m3g_api! { fn m3gGetBlending(handle: Handle) -> i32; }
m3g_api! { fn m3gSetAlphaThreshold(handle: Handle, threshold: f32); }
m3g_api! { fn m3gGetAlphaThreshold(handle: Handle) -> f32; }
m3g_api! { fn m3gSetAlphaWriteEnable(handle: Handle, enable: i32); }
m3g_api! { fn m3gIsAlphaWriteEnabled(handle: Handle) -> i32; }
m3g_api! { fn m3gEnableDepthTest(handle: Handle, enable: i32); }
m3g_api! { fn m3gIsDepthTestEnabled(handle: Handle) -> i32; }
m3g_api! { fn m3gEnableDepthWrite(handle: Handle, enable: i32); }
m3g_api! { fn m3gIsDepthWriteEnabled(handle: Handle) -> i32; }
m3g_api! { fn m3gEnableColorWrite(handle: Handle, enable: i32); }
m3g_api! { fn m3gIsColorWriteEnabled(handle: Handle) -> i32; }
m3g_api! { fn m3gSetDepthOffset(handle: Handle, factor: f32, units: f32); }
m3g_api! { fn m3gGetDepthOffsetFactor(handle: Handle) -> f32; }
m3g_api! { fn m3gGetDepthOffsetUnits(handle: Handle) -> f32; }
// Allocator callbacks for m3gCreateInterface. The C API frees by pointer only,
// so keep the Rust allocation size in an aligned prefix.
const M3G_ALLOC_ALIGN: usize = 8;
const M3G_ALLOC_HEADER_SIZE: usize = 8;

unsafe extern "C" fn m3g_malloc(bytes: u32) -> *mut c_void {
    let Some(total_size) = (bytes as usize).checked_add(M3G_ALLOC_HEADER_SIZE) else {
        return std::ptr::null_mut();
    };
    let Ok(layout) = std::alloc::Layout::from_size_align(total_size, M3G_ALLOC_ALIGN) else {
        return std::ptr::null_mut();
    };
    unsafe {
        let physical = std::alloc::alloc(layout);
        if physical.is_null() {
            return std::ptr::null_mut();
        }
        (physical as *mut usize).write(total_size);
        physical.add(M3G_ALLOC_HEADER_SIZE) as *mut c_void
    }
}

unsafe extern "C" fn m3g_free(ptr: *mut c_void) {
    if !ptr.is_null() {
        unsafe {
            let physical = (ptr as *mut u8).sub(M3G_ALLOC_HEADER_SIZE);
            let total_size = (physical as *const usize).read();
            let layout = std::alloc::Layout::from_size_align_unchecked(total_size, M3G_ALLOC_ALIGN);
            std::alloc::dealloc(physical, layout);
        }
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn m3gWasmAllocateBytes(bytes: u32) -> *mut c_void {
    unsafe { m3g_malloc(bytes) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn m3gWasmFreeBytes(ptr: *mut c_void) {
    unsafe { m3g_free(ptr) }
}

/// Resolves a native m3gcore object pointer to the stable 32-bit identity used
/// by the cross-platform GPU command wire. This is an identity conversion on
/// wasm32 and a lookup in the native handle table on 64-bit hosts.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn m3gWasmResolveStableHandle(object: *const c_void) -> u32 {
    stable_handle(object as usize)
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn m3gWasmAppendCommandBytes(
    sink: *mut c_void,
    ptr: *const u8,
    len: u32,
) -> i32 {
    if sink.is_null() || ptr.is_null() || len == 0 {
        return 0;
    }
    unsafe {
        let out = &mut *(sink as *mut Vec<u8>);
        let bytes = std::slice::from_raw_parts(ptr, len as usize);
        out.extend_from_slice(bytes);
    }
    1
}

pub fn create_interface() -> u32 {
    let params = M3Gparams {
        malloc_func: Some(m3g_malloc),
        free_func: Some(m3g_free),
        obj_alloc_func: None,
        obj_free_func: None,
        obj_resolve_func: None,
        error_func: None,
        begin_render_func: None,
        end_render_func: None,
        release_target_func: None,
        user_context: std::ptr::null_mut(),
    };
    unsafe { m3gCreateInterface(&params) }
}
