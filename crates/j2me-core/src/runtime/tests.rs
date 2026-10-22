#[cfg(test)]
/// Read a length-prefixed, 4-byte aligned string from a command buffer at the given offset.
/// Returns (string_bytes_consumed_including_padding, the_string).
fn read_string_from_buf(buf: &[u8], offset: usize) -> Option<(usize, String)> {
    if offset + 4 > buf.len() {
        return None;
    }
    let len = u32::from_le_bytes([
        buf[offset],
        buf[offset + 1],
        buf[offset + 2],
        buf[offset + 3],
    ]) as usize;
    let start = offset + 4;
    if start + len > buf.len() {
        return None;
    }
    let s = std::str::from_utf8(&buf[start..start + len])
        .ok()?
        .to_string();
    let padding = (4 - (len % 4)) % 4;
    Some((4 + len + padding, s))
}

#[cfg(test)]
/// Compute the byte size to skip for a given command ID (excluding the command byte itself).
fn command_payload_size(cmd_id: u8, buf: &[u8], offset: usize) -> Option<usize> {
    use vm::cmd::*;

    // Helper: read a length-prefixed, 4-byte-aligned string size
    let string_size = |off: usize| -> Option<usize> {
        let (sz, _) = read_string_from_buf(buf, off)?;
        Some(sz)
    };

    // GraphicsState sizes: write_text_state_to_buf = 48 bytes (9×i32 base + 3×i32 font)
    const GFX_STATE: usize = 48;

    match cmd_id {
        // Graphics commands: GFX_STATE(48) + command-specific params
        LCDUI_DRAW_SHAPE_STREAM => {
            let read_u32 = |off: usize| -> Option<u32> {
                Some(u32::from_le_bytes(buf.get(off..off + 4)?.try_into().ok()?))
            };
            let packet_count = read_u32(offset + GFX_STATE)? as usize;
            let mut pos = offset + GFX_STATE + 4;
            for _ in 0..packet_count {
                let stride = read_u32(pos + 4)? as usize;
                let vertices = read_u32(pos + 8)? as usize;
                pos += 12 + stride * vertices * 4;
            }
            Some(pos - offset)
        }
        DRAW_STRING => {
            // state(48) + string + x,y,anchor(12)
            let str_size = string_size(offset + GFX_STATE)?;
            Some(GFX_STATE + str_size + 12)
        }
        DRAW_IMAGE_STREAM => {
            let count = u32::from_le_bytes(
                buf.get(offset + GFX_STATE + 4..offset + GFX_STATE + 8)?
                    .try_into()
                    .ok()?,
            ) as usize;
            Some(GFX_STATE + 8 + count * 12)
        }
        DRAW_RGBA => {
            let fixed = GFX_STATE + 20;
            let pc = u32::from_le_bytes(
                buf.get(offset + fixed - 4..offset + fixed)?
                    .try_into()
                    .ok()?,
            ) as usize;
            Some(fixed + pc * 4)
        }
        DRAW_REGION => Some(GFX_STATE + 36), // imgId,xs,ys,ws,hs,transform,xd,yd,anchor
        COPY_AREA => Some(GFX_STATE + 28),   // xs,ys,w,h,xd,yd,anchor
        FLUSH => Some(4),                    // surfaceId (no state)
        CREATE_SURFACE => Some(12),          // surfaceId,w,h (no state)
        // Text output
        PRINTLN => string_size(offset),
        PRINT_CHAR => Some(4),
        // Display hooks
        DISPLAY_GET_WIDTH | DISPLAY_GET_HEIGHT => Some(4),
        DISPLAY_SIZE_CHANGED => Some(8),
        // Menu: [display_id:i32][left_label][right_label]
        MENU_UPDATE_COMMANDS => {
            if offset + 4 > buf.len() {
                return None;
            }
            let mut sz = 4; // display_id
            for _ in 0..2 {
                let string_size = string_size(offset + sz)?;
                sz += string_size;
            }
            Some(sz)
        }
        // RMS — still length-prefixed string
        RMS_LOAD | RMS_KEYS => string_size(offset),
        // Media commands — binary format
        MEDIA_CREATE_PLAYER => {
            // [handle:i32][locator_len:u32][locator+pad]
            if offset + 8 > buf.len() {
                return None;
            }
            let loc_len = u32::from_le_bytes([
                buf[offset + 4],
                buf[offset + 5],
                buf[offset + 6],
                buf[offset + 7],
            ]) as usize;
            Some(8 + ((loc_len + 3) & !3))
        }
        MEDIA_CLOSE | MEDIA_REALIZE | MEDIA_PREFETCH | MEDIA_STOP => Some(4), // [handle:i32]
        MEDIA_PLAY => Some(12), // [handle:i32][time_lo:i32][time_hi:i32]
        MEDIA_SET_VOLUME | MEDIA_SET_SEQUENCE => Some(8), // [handle:i32][value:i32]
        MEDIA_SET_MUTED => Some(8), // [handle:i32][muted:u8][pad:3]
        MEDIA_PLAY_TONE => Some(12), // [note:i32][duration:i32][volume:i32]
        MEDIA_SET_LOOP_COUNT => Some(8), // [handle:i32][count:i32]
        MEDIA_CREATE_PLAYER_BINARY => {
            // [handle:i32][loopCount:i32][volume:i32][ct_len:u32][ct+pad][data_len:u32][data+pad]
            if offset + 16 > buf.len() {
                return None;
            }
            let ct_len = u32::from_le_bytes([
                buf[offset + 12],
                buf[offset + 13],
                buf[offset + 14],
                buf[offset + 15],
            ]) as usize;
            let ct_padded = (ct_len + 3) & !3;
            let data_off = 16 + ct_padded;
            if offset + data_off + 4 > buf.len() {
                return None;
            }
            let data_len = u32::from_le_bytes([
                buf[offset + data_off],
                buf[offset + data_off + 1],
                buf[offset + data_off + 2],
                buf[offset + data_off + 3],
            ]) as usize;
            Some(data_off + 4 + ((data_len + 3) & !3))
        }
        // LCDUI
        LCDUI_SET_VISIBLE => Some(3), // 1 byte visible + 2 bytes padding
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn new_test_core(debug: bool) -> JvmCore {
        JvmCore::new(None, debug, Rc::new(RefCell::new(1)))
    }

    fn new_instance_test_core(instance: &JvmInstance, name: &str) -> JvmCore {
        JvmCore::new(
            Some(name),
            instance.debug,
            Rc::clone(&instance.next_surface_id),
        )
    }

    /// Encode a CoreSaveState into the compressed format that load_state expects.
    fn encode_save_state_for_test(state: &CoreSaveState) -> Vec<u8> {
        use flate2::write::DeflateEncoder;
        use flate2::Compression;
        use std::io::Write;

        let uncompressed = bincode::serialize(state).expect("serialize save state");
        let mut encoder = DeflateEncoder::new(Vec::new(), Compression::default());
        encoder.write_all(&uncompressed).expect("compress");
        let compressed = encoder.finish().expect("finish compress");
        let section_count = 1u32;
        let vm_state_offset = SAVE_FIXED_HEADER_SIZE + SAVE_SECTION_ENTRY_SIZE;
        let mut output = Vec::with_capacity(vm_state_offset + compressed.len());
        output.extend_from_slice(SAVE_MAGIC);
        output.extend_from_slice(&SAVE_VERSION.to_le_bytes());
        output.extend_from_slice(&0u64.to_le_bytes()); // timestamp
        output.extend_from_slice(&section_count.to_le_bytes());
        JvmInstance::write_save_section_entry(
            &mut output,
            SaveSectionEntry {
                section_type: SAVE_SECTION_VM_STATE,
                offset: vm_state_offset as u32,
                length: compressed.len() as u32,
                param0: uncompressed.len() as u32,
                param1: 0,
            },
        );
        output.extend_from_slice(&compressed);
        output
    }

    #[test]
    fn test_system_classes_loaded() {
        let jvm = new_test_core(false);
        assert!(jvm.vm.class_repo.has_class("java.lang.Object"));
        assert!(jvm.vm.class_repo.has_class("java.lang.String"));
        assert!(jvm.vm.class_repo.has_class("java.lang.System"));
        assert!(jvm.vm.class_repo.has_class("java.lang.Integer"));
        assert!(jvm.vm.class_repo.has_class("java.io.PrintStream"));
        assert!(jvm.vm.class_repo.has_class("javax.bluetooth.UUID"));
        assert!(jvm.vm.class_repo.has_class("javax.bluetooth.LocalDevice"));
        assert!(jvm
            .vm
            .class_repo
            .has_class("javax.bluetooth.L2CAPConnection"));
        assert!(jvm
            .vm
            .class_repo
            .has_class("com.sun.cldc.io.j2me.btl2cap.Protocol"));
        assert!(jvm.vm.class_repo.class_count() > 50);
    }

    #[test]
    fn audit_native_binding_registry() {
        let vm = SharedVm::new(Rc::new(RefCell::new(1)), false);
        vm.validate_registered_native_bindings_in_jar(SYSTEM_CLASSES_JAR)
            .expect("native binding registry audit failed");
    }

    #[test]
    fn test_field_inheritance() {
        let jvm = new_test_core(false);
        let obj_size = jvm
            .vm
            .class_repo
            .get_class("java.lang.Object")
            .map(|c| c.size_of_fields)
            .unwrap_or(0);
        let throwable_size = jvm
            .vm
            .class_repo
            .get_class("java.lang.Throwable")
            .map(|c| c.size_of_fields)
            .unwrap_or(0);
        assert!(
            throwable_size >= obj_size,
            "Throwable should inherit Object fields"
        );
    }

    #[test]
    fn test_hello_world_output() {
        let mut jvm = new_test_core(false);
        let class_data = include_bytes!(concat!(env!("OUT_DIR"), "/test_classes/HelloWorld.class"));
        jvm.load_class(class_data).expect("load HelloWorld");
        jvm.start_main("HelloWorld", &[]).expect("start main");

        let mut total_steps = 0;
        for _ in 0..100000 {
            total_steps += 1;
            if jvm.step(1) == 0 {
                break;
            }
        }
        let output = jvm.get_output();
        assert!(
            output.contains("Hello, World!"),
            "output={:?} steps={}",
            output,
            total_steps
        );
        assert!(output.contains("1 + 2 = 3"), "output={:?}", output);
    }

    #[test]
    fn test_try_catch() {
        let mut jvm = new_test_core(true);
        let class_data = include_bytes!(concat!(env!("OUT_DIR"), "/test_classes/TryCatch.class"));
        jvm.load_class(class_data).expect("load TryCatch");
        jvm.start_main("TryCatch", &[]).expect("start main");

        for _ in 0..200000 {
            if jvm.step(1) == 0 {
                break;
            }
        }
        let output = jvm.get_output();
        assert!(output.contains("before try"), "output={:?}", output);
        assert!(output.contains("caught"), "output={:?}", output);
        assert!(output.contains("after catch"), "output={:?}", output);
    }

    #[test]
    fn test_thread_join() {
        let mut jvm = new_test_core(false);
        // Load all generated class files for ThreadJoinTest (main + Worker inner class)
        let worker = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/ThreadJoinTest$Worker.class"
        ));
        let main_class = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/ThreadJoinTest.class"
        ));
        jvm.load_class(worker).expect("load ThreadJoinTest$Worker");
        jvm.load_class(main_class).expect("load ThreadJoinTest");
        jvm.start_main("ThreadJoinTest", &[]).expect("start main");

        for _ in 0..500000 {
            if jvm.step(1) == 0 {
                break;
            }
        }
        let output = jvm.get_output();
        assert!(
            output.contains("PASS basic join"),
            "basic join failed; output={:?}",
            output
        );
        assert!(
            output.contains("PASS join already finished"),
            "join-already-finished failed; output={:?}",
            output
        );
        assert!(
            output.contains("PASS join ordering"),
            "join ordering failed; output={:?}",
            output
        );
        assert!(
            !output.contains("FAIL"),
            "unexpected failure; output={:?}",
            output
        );
    }

    #[test]
    fn test_primitive_array_type_checks() {
        let mut jvm = new_test_core(false);
        let class_data = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/PrimitiveArrayTypeCheckTest.class"
        ));
        jvm.load_class(class_data)
            .expect("load PrimitiveArrayTypeCheckTest");
        jvm.start_main("PrimitiveArrayTypeCheckTest", &[])
            .expect("start main");

        for _ in 0..200000 {
            if jvm.step(1) == 0 {
                break;
            }
        }

        let output = jvm.get_output();
        assert!(
            !output.contains("FAIL"),
            "array type checks failed; output={:?}",
            output
        );
        assert!(
            output.contains("ALL ARRAY TYPE CHECKS PASSED"),
            "array type checks did not complete; output={:?}",
            output
        );
    }

    #[test]
    fn test_midlet_lifecycle() {
        let mut jvm = new_test_core(false);
        let class_data = include_bytes!(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/tests/java/MIDlet01_Lifecycle.class"
        ));
        jvm.load_class(class_data).expect("load MIDlet01_Lifecycle");

        let ctx_id = jvm
            .create_midlet_context("MIDlet01_Lifecycle")
            .expect("create context");
        let output = jvm.get_output();
        assert!(
            output.contains("Constructor called"),
            "init output={:?}",
            output
        );

        jvm.start_midlet(ctx_id).expect("start midlet");
        let output = jvm.get_output();
        assert!(
            output.contains("startApp called"),
            "start output={:?}",
            output
        );

        jvm.pause_midlet(ctx_id).expect("pause midlet");
        let output = jvm.get_output();
        assert!(
            output.contains("pauseApp called"),
            "pause output={:?}",
            output
        );

        jvm.destroy_midlet(ctx_id, true).expect("destroy midlet");
        let output = jvm.get_output();
        assert!(
            output.contains("destroyApp called"),
            "destroy output={:?}",
            output
        );
    }

    #[test]
    fn test_advanced_ui_suite_lcdui() {
        // Register dummy font metrics callback needed for LCDUI rendering
        unsafe extern "C" fn dummy_font_metrics(_face: i32, _style: i32, _size: i32) -> i32 {
            // Return packed (height << 16) | baseline
            (16 << 16) | 12
        }
        unsafe extern "C" fn dummy_measure_text(
            _ptr: *const u8,
            text_len: i32,
            _face: i32,
            _style: i32,
            _size: i32,
        ) -> i32 {
            text_len * 8 // rough approximation: 8px per char
        }
        crate::host_native::set_font_metrics_callback(dummy_font_metrics);
        crate::host_native::set_measure_text_callback(dummy_measure_text);

        let jar = include_bytes!(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/tests/fixtures/demo-jars/AdvancedUISuite.jar"
        ));
        let mut jvm = new_test_core(false);
        jvm.load_jar(jar).expect("load jar");

        let ctx_id = jvm
            .create_midlet_context("AdvancedUISuite")
            .expect("create context");
        // AdvancedUISuite may request host-provided data (for example image pixels)
        // while startApp is still running. Resolve pending async requests the same
        // way the JS bridge would, then resume the suspended MIDlet thread.
        let mut result = jvm.start_midlet(ctx_id);
        for _ in 0..20 {
            if result.is_ok() {
                break;
            }
            if result.as_ref().err().map(|e| e.as_str()) != Some("ASYNC_PENDING") {
                break;
            }
            resolve_all_pending_async(&mut jvm);
            result = jvm.resume_async_thread().into_result();
        }
        assert!(result.is_ok(), "start_midlet failed: {:?}", result.err());

        // Step further to allow MIDlet initialization and setCurrent to complete
        for _ in 0..20 {
            if jvm.step(16) == 0 {
                break;
            }
        }

        // Advanced LCDUI is Java-painted on the primary canvas now. The old
        // DOM snapshot command must not be emitted for the setCurrent screen.
        let buf = &jvm.vm.command_buffer;
        const OLD_LCDUI_RENDER: u8 = 71;
        let mut saw_lcdui_render = false;
        let mut saw_graphics_draw = false;
        let mut i = 0;
        while i < buf.len() {
            let cmd_id = buf[i];
            let payload_off = i + 1;
            if let Some(payload_size) = command_payload_size(cmd_id, buf, payload_off) {
                if cmd_id == OLD_LCDUI_RENDER {
                    saw_lcdui_render = true;
                }
                if matches!(
                    cmd_id,
                    crate::vm::cmd::LCDUI_DRAW_SHAPE_STREAM
                        | crate::vm::cmd::DRAW_STRING
                        | crate::vm::cmd::DRAW_IMAGE_STREAM
                ) {
                    saw_graphics_draw = true;
                }
                i = payload_off + payload_size;
            } else {
                break; // unknown command — stop
            }
        }

        assert!(
            !saw_lcdui_render,
            "advanced LCDUI should not emit DOM snapshot command"
        );
        assert!(
            saw_graphics_draw,
            "advanced LCDUI should emit graphics commands"
        );

        let output = jvm.get_output();
        assert!(
            !output.contains("Unhandled exception"),
            "output={:?}",
            output
        );
    }

    #[test]
    fn test_rms_basic() {
        let mut jvm = new_test_core(true);
        let class_data = include_bytes!(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/tests/java/RMSBasicSuite.class"
        ));
        jvm.load_class(class_data).expect("load RMSBasicSuite");

        let ctx_id = jvm
            .create_midlet_context("RMSBasicSuite")
            .expect("create context");
        let _output = jvm.get_output();

        // start_midlet may yield for async RMS operations in test env;
        // resolve them with empty data and retry
        let mut result = jvm.start_midlet(ctx_id);
        for _ in 0..20 {
            if result.is_ok() {
                break;
            }
            if result.as_ref().err().map(|e| e.as_str()) != Some("ASYNC_PENDING") {
                break;
            }
            resolve_all_pending_async(&mut jvm);
            result = jvm.resume_async_thread().into_result();
        }
        let output = jvm.get_output();
        assert!(result.is_ok(), "start_midlet failed: {:?}", result.err());
        assert!(
            output.contains("RMS Basic Operations"),
            "output={:?}",
            output
        );
    }

    #[test]
    fn test_itable_inherit_override() {
        let mut jvm = new_test_core(false);
        let main_class = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/ItableInheritTest.class"
        ));
        let parent_class = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/ItableInheritTest$Parent.class"
        ));
        let child_class = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/ItableInheritTest$Child.class"
        ));
        let grandchild_class = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/ItableInheritTest$GrandChild.class"
        ));
        jvm.load_class(parent_class).expect("load Parent");
        jvm.load_class(child_class).expect("load Child");
        jvm.load_class(grandchild_class).expect("load GrandChild");
        jvm.load_class(main_class).expect("load ItableInheritTest");
        jvm.start_main("ItableInheritTest", &[])
            .expect("start main");

        for _ in 0..500000 {
            if jvm.step(1) == 0 {
                break;
            }
        }
        let output = jvm.get_output();
        let lines: Vec<&str> = output.lines().collect();
        // Expected: PARENT, CHILD, GRANDCHILD, PARENT, CHILD, GRANDCHILD
        assert_eq!(
            lines,
            vec![
                "PARENT",
                "CHILD",
                "GRANDCHILD",
                "PARENT",
                "CHILD",
                "GRANDCHILD"
            ],
            "invokeinterface must dispatch to overridden methods in subclasses, output={:?}",
            output
        );
    }

    #[test]
    fn test_abstract_vtable_dispatch() {
        let mut jvm = new_test_core(false);
        let main_class = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/AbstractVtableTest.class"
        ));
        let callable = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/AbstractVtableTest$Callable.class"
        ));
        let named = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/AbstractVtableTest$Named.class"
        ));
        let abstract_base = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/AbstractVtableTest$AbstractBase.class"
        ));
        let doubler = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/AbstractVtableTest$Doubler.class"
        ));
        let tripler = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/AbstractVtableTest$Tripler.class"
        ));
        let partial_base = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/AbstractVtableTest$PartialBase.class"
        ));
        let squarer = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/AbstractVtableTest$Squarer.class"
        ));
        jvm.load_class(callable).expect("load Callable");
        jvm.load_class(named).expect("load Named");
        jvm.load_class(abstract_base).expect("load AbstractBase");
        jvm.load_class(doubler).expect("load Doubler");
        jvm.load_class(tripler).expect("load Tripler");
        jvm.load_class(partial_base).expect("load PartialBase");
        jvm.load_class(squarer).expect("load Squarer");
        jvm.load_class(main_class).expect("load AbstractVtableTest");
        jvm.start_main("AbstractVtableTest", &[])
            .expect("start main");

        for _ in 0..500000 {
            if jvm.step(1) == 0 {
                break;
            }
        }
        let output = jvm.get_output();
        let lines: Vec<&str> = output.lines().collect();
        assert_eq!(lines, vec!["10", "15", "Doubler", "Tripler", "14", "21", "16", "Partial", "6", "9"],
            "INVOKEVIRTUAL through abstract class must dispatch to concrete implementations, output={:?}", output);
    }

    /// Static field pseudo-objects must survive GC.
    /// Regression test: GC previously reclaimed the pseudo-object holding static
    /// fields because `collect_static_roots` did not add the pseudo-object itself
    /// as a GC root, only the references stored *inside* it. After a collection
    /// the freed memory could be reused and zeroed, turning all static fields to NULL.
    #[test]
    fn test_gc_preserves_static_fields() {
        let mut jvm = new_test_core(false);

        // Pick a well-known system class that has at least one static reference field.
        // java.lang.System has `out` and `err` (both PrintStream references set during bootstrap).
        let rf = jvm
            .vm
            .resolve_field("java.lang.System", "out", "Ljava/io/PrintStream;")
            .expect("System.out field must exist");
        let base = jvm.vm.static_fields.get_addr(&rf.class_name);
        assert_ne!(base, 0, "static fields base for System must be allocated");
        let val_before = jvm.vm.heap.read_u32(base + rf.byte_offset);
        assert_ne!(
            val_before, 0,
            "System.out should be non-null after bootstrap"
        );

        // Force a GC cycle (no threads running, so empty thread list is fine)
        let threads: Vec<crate::scheduler::virtual_thread::VirtualThread> = Vec::new();
        let stats = gc::collect(&mut jvm.vm, &threads);
        // The GC should have run (there are allocated objects)
        assert!(stats.retained_count > 0, "GC should retain objects");

        // Verify static field survived
        let val_after = jvm.vm.heap.read_u32(base + rf.byte_offset);
        assert_eq!(
            val_before, val_after,
            "System.out must survive GC: before={}, after={}",
            val_before, val_after
        );

        // Also check that the pseudo-object itself is still in the live set
        assert!(
            jvm.vm.heap.all_objects().contains(&base),
            "static field pseudo-object @{} must remain in live objects after GC",
            base
        );
    }

    #[test]
    fn test_cheat_object_search_serializes_live_vm_objects() {
        let jvm = new_test_core(false);
        let result = crate::cheat_engine::search_objects_binary(&jvm.vm, &[0.0, 0.0], 0);
        assert!(result.len() >= 9);
        let shown = u32::from_le_bytes(result[0..4].try_into().unwrap());
        let total = u32::from_le_bytes(result[4..8].try_into().unwrap());
        assert!(
            shown > 0,
            "expected initialized VM objects with two zero fields"
        );
        assert!(total >= shown);
    }

    #[test]
    fn test_cheat_object_refine_only_checks_previous_candidates() {
        let mut jvm = new_test_core(false);
        let int_array_class_id = jvm
            .vm
            .class_repo
            .get_class_id("[I")
            .expect("int array class id");
        let first = jvm
            .vm
            .heap
            .alloc_array_typed(int_array_class_id, 2, 4, true);
        jvm.vm
            .heap
            .write_i32(first + crate::heap::ARRAY_HDR_SIZE, 123_456_789);
        jvm.vm
            .heap
            .write_i32(first + crate::heap::ARRAY_HDR_SIZE + 4, -987_654_321);

        let begin = jvm.cheat_engine.object_search_begin_binary(
            &jvm.vm,
            &[123_456_789.0, -987_654_321.0],
            0,
        );
        assert_eq!(u32::from_le_bytes(begin[4..8].try_into().unwrap()), 1);

        // A matching object allocated after the first pass must not enter the
        // candidate set during refinement.
        let second = jvm
            .vm
            .heap
            .alloc_array_typed(int_array_class_id, 2, 4, true);
        jvm.vm
            .heap
            .write_i32(second + crate::heap::ARRAY_HDR_SIZE, 123_456_789);
        jvm.vm
            .heap
            .write_i32(second + crate::heap::ARRAY_HDR_SIZE + 4, -987_654_320);
        jvm.vm
            .heap
            .write_i32(first + crate::heap::ARRAY_HDR_SIZE + 4, -987_654_320);

        let refined = jvm.cheat_engine.object_search_refine_binary(
            &jvm.vm,
            &[123_456_789.0, -987_654_320.0],
            0,
        );
        assert_eq!(u32::from_le_bytes(refined[4..8].try_into().unwrap()), 1);

        jvm.cheat_engine.on_heap_compacted();
        let cleared = jvm.cheat_engine.object_search_refine_binary(
            &jvm.vm,
            &[123_456_789.0, -987_654_320.0],
            0,
        );
        assert_eq!(u32::from_le_bytes(cleared[4..8].try_into().unwrap()), 0);
    }

    #[test]
    fn test_cheat_object_refine_requires_values_at_original_slots() {
        let mut jvm = new_test_core(false);
        let int_array_class_id = jvm
            .vm
            .class_repo
            .get_class_id("[I")
            .expect("int array class id");
        let array = jvm
            .vm
            .heap
            .alloc_array_typed(int_array_class_id, 6, 4, true);
        for (index, value) in [
            111_111_111,
            222_222_222,
            333_333_333,
            444_444_444,
            555_555_555,
            666_666_666,
        ]
        .into_iter()
        .enumerate()
        {
            jvm.vm.heap.write_i32(
                array + crate::heap::ARRAY_HDR_SIZE + index as u32 * 4,
                value,
            );
        }

        let begin = jvm.cheat_engine.object_search_begin_binary(
            &jvm.vm,
            &[111_111_111.0, 222_222_222.0, 333_333_333.0],
            0,
        );
        assert_eq!(u32::from_le_bytes(begin[4..8].try_into().unwrap()), 1);

        // The new values already exist elsewhere in the same array, but the
        // original three slots did not change, so this candidate must be removed.
        let refined = jvm.cheat_engine.object_search_refine_binary(
            &jvm.vm,
            &[444_444_444.0, 555_555_555.0, 666_666_666.0],
            0,
        );
        assert_eq!(u32::from_le_bytes(refined[4..8].try_into().unwrap()), 0);
    }

    #[test]
    fn test_gc_drops_stale_empty_monitors() {
        let mut jvm = new_test_core(false);
        let byte_array_class_id = jvm
            .vm
            .class_repo
            .get_class_id("[B")
            .expect("byte array class id");
        let stream_ref = jvm.vm.new_object("java.io.ByteArrayInputStream");
        let bytes_ref = jvm
            .vm
            .heap
            .alloc_array_typed(byte_array_class_id, 32, 1, true);
        let field = jvm
            .vm
            .resolve_field("java.io.ByteArrayInputStream", "buf", "[B")
            .expect("ByteArrayInputStream.buf field");
        jvm.vm
            .heap
            .write_u32(stream_ref + field.byte_offset, bytes_ref);
        jvm.vm
            .monitors
            .insert(stream_ref, scheduler::monitor::CoopMonitor::new());

        let threads: Vec<crate::scheduler::virtual_thread::VirtualThread> = Vec::new();
        let stats = gc::collect(&mut jvm.vm, &threads);
        assert!(
            stats.freed_count >= 2,
            "stale monitor should not keep stream and byte[] alive"
        );
        assert!(
            !jvm.vm.heap.all_objects().contains(&stream_ref),
            "empty monitor entry must not keep ByteArrayInputStream alive",
        );
        assert!(
            !jvm.vm.heap.all_objects().contains(&bytes_ref),
            "empty monitor entry must not keep its backing byte[] alive",
        );
        assert!(
            !jvm.vm.monitors.contains_key(&stream_ref),
            "empty stale monitor entry should be removed during cleanup",
        );
    }

    #[test]
    fn test_gc_preserves_async_request_object_refs() {
        use crate::vm::AsyncRequestType;

        let mut jvm = new_test_core(false);
        let date_field_ref = jvm.vm.new_object("javax.microedition.lcdui.DateField");
        let garbage_ref = jvm.vm.new_object("java.lang.Object");

        jvm.vm.issue_async_request(
            42,
            AsyncRequestType::DateInput {
                date_field_ref,
                mode: 1,
                initialized: false,
                value_millis: 0,
            },
        );

        let threads: Vec<crate::scheduler::virtual_thread::VirtualThread> = Vec::new();
        let _stats = gc::collect(&mut jvm.vm, &threads);

        assert!(
            jvm.vm.heap.all_objects().contains(&date_field_ref),
            "DateInput async request target must be a GC root",
        );
        assert!(
            !jvm.vm.heap.all_objects().contains(&garbage_ref),
            "unreferenced object should still be collected",
        );
    }

    #[test]
    fn test_save_load_state() {
        // Run SaveStateTest partially, save state, then restore and continue
        let class_data = include_bytes!(concat!(
            env!("OUT_DIR"),
            "/test_classes/SaveStateTest.class"
        ));

        // --- Phase 1: Run until some output is produced, then save ---
        let mut jvm = new_test_core(false);
        jvm.load_class(class_data).expect("load SaveStateTest");
        jvm.start_main("SaveStateTest", &[]).expect("start main");

        // Run a limited number of steps (enough to produce some but not all output)
        for _ in 0..5000 {
            if jvm.step(1) == 0 {
                break;
            }
        }
        let output_before_save = jvm.get_output();

        // Save state
        let snapshot = jvm.save_state(None);
        assert!(!snapshot.is_empty(), "snapshot should not be empty");

        // --- Phase 2: Continue original to completion ---
        for _ in 0..500000 {
            if jvm.step(1) == 0 {
                break;
            }
        }
        let output_original = jvm.get_output();
        let full_original = format!("{}{}", output_before_save, output_original);

        // --- Phase 3: Create fresh instance, load same class, restore state ---
        let mut jvm2 = new_test_core(false);
        jvm2.load_class(class_data)
            .expect("load SaveStateTest again");
        jvm2.load_state(&snapshot);

        // Clear any output accumulated during setup
        let _ = jvm2.get_output();

        // Continue from restored state
        for _ in 0..500000 {
            if jvm2.step(1) == 0 {
                break;
            }
        }
        let output_restored = jvm2.get_output();

        // The restored output should match what the original produced after the save point
        assert_eq!(
            output_restored, output_original,
            "Restored VM should produce same output as original after save point.\n\
             Full original: {:?}\n\
             After restore: {:?}",
            full_original, output_restored
        );

        // Verify final result is correct
        assert!(
            full_original.contains("done=15"),
            "Expected final output 'done=15', got: {:?}",
            full_original
        );
    }

    #[test]
    fn test_save_load_state_rebases_sleep_deadlines() {
        use crate::call_host_mono_time_millis;
        use crate::scheduler::virtual_thread::JavaThreadState;
        use std::cmp::Reverse;

        let mut jvm = new_test_core(false);
        let tid = jvm.scheduler.create_thread("sleepy".into(), 5, false);
        jvm.scheduler.run_queue.clear();
        let host_now = call_host_mono_time_millis();
        let original_now = 2_225_u64;
        let remaining_ms = 29_262_u64;
        if let Some(thread) = jvm.scheduler.threads.get_mut(&tid) {
            thread.state = JavaThreadState::TimedWaiting;
        }
        jvm.scheduler.host_time_base_ms = host_now;
        jvm.scheduler.effective_time_base_ms = original_now;
        jvm.scheduler.time_offset_ms = 0;
        jvm.vm.host_time_base_ms = host_now;
        jvm.vm.effective_time_base_ms = original_now;
        jvm.vm.time_offset_ms = 0;
        jvm.scheduler.sleep_queue.clear();
        jvm.scheduler
            .sleep_queue
            .push(Reverse((original_now + remaining_ms, tid)));

        assert_eq!(jvm.idle_delay(), remaining_ms as u32);

        let mut state = jvm.capture_core_state();
        state.snapshot.scheduler.host_time_base_ms = 999_999;
        state.snapshot.scheduler.effective_time_base_ms = 888_888;
        state.snapshot.host_time_base_ms = 777_777;
        state.snapshot.effective_time_base_ms = 666_666;
        let shifted_snapshot = encode_save_state_for_test(&state);

        let mut restored = new_test_core(false);
        restored.load_state(&shifted_snapshot);

        let restored_delay = restored.idle_delay();
        let tolerance = 500; // allow up to 500ms of wall-clock drift during test
        assert!(
            restored_delay <= remaining_ms as u32
                && restored_delay >= (remaining_ms as u32).saturating_sub(tolerance),
            "restored JVM should preserve remaining sleep time across clock rebasing, got {} expected ~{}",
            restored_delay, remaining_ms,
        );
    }

    #[test]
    fn test_load_state_restores_image_surface_snapshots() {
        use crate::vm::{cmd, ImageSurfaceSnapshot};

        let mut jvm = new_test_core(false);

        let mut state = jvm.capture_core_state();
        state.snapshot.image_surface_snapshots = vec![ImageSurfaceSnapshot {
            surface_id: 7,
            width: 2,
            height: 2,
            pixels: vec![
                0xFF112233_u32 as i32,
                0xFF445566_u32 as i32,
                0xFF778899_u32 as i32,
                0xFFAABBCC_u32 as i32,
            ],
        }];
        let shifted_snapshot = encode_save_state_for_test(&state);

        let mut restored = new_test_core(false);
        restored.load_state(&shifted_snapshot);

        let buf = &restored.vm.command_buffer;
        assert!(
            !buf.is_empty(),
            "restored VM should enqueue commands to rebuild image surfaces",
        );
        let draw_rgb_at = 0;
        assert_eq!(buf[draw_rgb_at], cmd::DRAW_RGBA);
        assert_eq!(
            u32::from_le_bytes(
                buf[draw_rgb_at + 1..draw_rgb_at + 5]
                    .try_into()
                    .expect("surface id bytes"),
            ),
            7,
            "DRAW_RGBA should target the restored image surface",
        );
        assert_eq!(
            i32::from_le_bytes(
                buf[draw_rgb_at + 57..draw_rgb_at + 61]
                    .try_into()
                    .expect("width bytes"),
            ),
            2,
        );
        assert_eq!(
            i32::from_le_bytes(
                buf[draw_rgb_at + 61..draw_rgb_at + 65]
                    .try_into()
                    .expect("height bytes"),
            ),
            2,
        );
        assert_eq!(
            u32::from_le_bytes(
                buf[draw_rgb_at + 65..draw_rgb_at + 69]
                    .try_into()
                    .expect("pixel count bytes"),
            ),
            4,
            "restored DRAW_RGBA should contain all saved pixels",
        );
    }

    #[test]
    fn test_save_state_skips_display_surface_snapshot_target() {
        let mut jvm = new_test_core(false);
        jvm.vm.ensure_display_surface();

        let targets = jvm.prepare_image_surface_snapshot_targets();

        assert!(
            targets.iter().all(|target| target.surface_id != 0),
            "surface 0 is supplied by native as a physical display snapshot and should not be requested as a logical surface target",
        );
    }

    #[test]
    fn test_save_state_keeps_provided_image_surface_snapshots() {
        let mut jvm = new_test_core(false);
        let image_data_ref = jvm.vm.new_object("javax.microedition.lcdui.ImageData");
        let width_off = jvm
            .vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "width")
            .expect("ImageData.width field offset");
        let height_off = jvm
            .vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "height")
            .expect("ImageData.height field offset");
        jvm.vm.heap.write_u32(image_data_ref + width_off, 2);
        jvm.vm.heap.write_u32(image_data_ref + height_off, 1);
        jvm.vm.image_surface_map.insert(image_data_ref, 77);
        jvm.vm.main_thread_obj = image_data_ref;
        jvm.add_pending_image_surface_snapshot(
            77,
            2,
            1,
            vec![0xFF010203_u32 as i32, 0xFF040506_u32 as i32],
        );

        let state = jvm.capture_core_state();

        assert_eq!(state.snapshot.image_surface_snapshots.len(), 1);
        assert_eq!(state.snapshot.image_surface_snapshots[0].surface_id, 77);
        assert_eq!(state.snapshot.image_surface_snapshots[0].pixels.len(), 2);
    }

    #[test]
    fn test_save_state_drops_dead_image_surface_map_entries() {
        let mut jvm = new_test_core(false);
        let dead_image_data_ref = jvm.vm.heap.alloc_object(1, 0);
        jvm.vm.image_surface_map.insert(dead_image_data_ref, 77);

        let state = jvm.capture_core_state();

        assert!(
            state.snapshot.image_surface_map.is_empty(),
            "save_state should GC and drop dead ImageData->surface mappings before snapshot",
        );
        assert!(
            state.snapshot.image_surface_snapshots.is_empty(),
            "save_state should not capture pixels for dead image surfaces",
        );
    }

    #[test]
    fn test_resource_backed_images_restore_without_pixel_snapshots() {
        let mut jvm = new_test_core(false);
        jvm.load_resource("icons/foo.png", &[1, 2, 3, 4]);
        let image_data_ref = jvm.vm.new_object("javax.microedition.lcdui.ImageData");
        let width_off = jvm
            .vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "width")
            .expect("ImageData.width field offset");
        let height_off = jvm
            .vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "height")
            .expect("ImageData.height field offset");
        jvm.vm.heap.write_u32(image_data_ref + width_off, 16);
        jvm.vm.heap.write_u32(image_data_ref + height_off, 16);
        jvm.vm.image_surface_map.insert(image_data_ref, 99);
        jvm.vm.image_surface_restore_sources.insert(
            image_data_ref,
            vm::ImageSurfaceRestoreSource::JarResource {
                name: "icons/foo.png".into(),
            },
        );
        jvm.vm.main_thread_obj = image_data_ref;

        let wrapper = jvm.capture_core_state();
        assert!(wrapper.snapshot.image_surface_snapshots.is_empty());
        assert_eq!(wrapper.snapshot.resource_image_surfaces.len(), 1);
        assert_eq!(
            wrapper.snapshot.resource_image_surfaces[0].resource_name,
            "icons/foo.png",
        );

        let snapshot = encode_save_state_for_test(&wrapper);
        let mut restored = new_test_core(false);
        restored.load_resource("icons/foo.png", &[1, 2, 3, 4]);
        restored.load_state(&snapshot);

        assert!(
            !restored
                .vm
                .command_buffer
                .iter()
                .any(|&cmd| cmd == crate::vm::cmd::DRAW_RGBA),
            "resource-backed images should not rebuild pixels via DRAW_RGBA snapshot replay",
        );
        let pending = restored.vm.take_pending_resource_image_restores();
        assert_eq!(pending.len(), 1);
        assert_eq!(pending[0].surface_id, 99);
        assert_eq!(pending[0].resource_name, "icons/foo.png");
    }

    #[test]
    fn test_derived_images_restore_without_pixel_snapshots() {
        let mut jvm = new_test_core(false);
        let src_ref = jvm.vm.new_object("javax.microedition.lcdui.ImageData");
        let dst_ref = jvm.vm.new_object("javax.microedition.lcdui.ImageData");
        let width_off = jvm
            .vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "width")
            .expect("ImageData.width field offset");
        let height_off = jvm
            .vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "height")
            .expect("ImageData.height field offset");
        jvm.vm.heap.write_u32(src_ref + width_off, 32);
        jvm.vm.heap.write_u32(src_ref + height_off, 32);
        jvm.vm.heap.write_u32(dst_ref + width_off, 16);
        jvm.vm.heap.write_u32(dst_ref + height_off, 16);
        jvm.vm.image_surface_map.insert(src_ref, 10);
        jvm.vm.image_surface_map.insert(dst_ref, 11);
        jvm.vm
            .mark_image_surface_as_region(dst_ref, src_ref, 4, 5, 16, 16, 0);
        jvm.vm.main_thread_obj = src_ref;
        jvm.vm.midp_display_ref = dst_ref;

        let wrapper = jvm.capture_core_state();
        assert!(wrapper.snapshot.image_surface_snapshots.is_empty());
        assert_eq!(wrapper.snapshot.derived_image_surfaces.len(), 1);
        assert_eq!(wrapper.snapshot.derived_image_surfaces[0].surface_id, 11);
        assert_eq!(
            wrapper.snapshot.derived_image_surfaces[0].source_surface_id,
            10
        );

        let snapshot = encode_save_state_for_test(&wrapper);
        let mut restored = new_test_core(false);
        restored.load_state(&snapshot);

        assert!(
            !restored
                .vm
                .command_buffer
                .iter()
                .any(|&cmd| cmd == crate::vm::cmd::DRAW_RGBA),
            "derived images should not rebuild pixels via DRAW_RGBA snapshot replay",
        );
        let pending = restored.vm.take_pending_derived_image_restores();
        assert_eq!(pending.len(), 1);
        assert_eq!(pending[0].surface_id, 11);
        assert_eq!(pending[0].source_surface_id, 10);
    }

    #[test]
    fn test_load_state_rehydrates_resource_decoders_from_resources() {
        let mut jvm = new_test_core(false);
        jvm.load_resource("foo.bin", &[1, 2, 3, 4, 5, 6]);
        jvm.vm.resource_decoders.insert(
            7,
            vm::ResourceDecoder {
                name: "foo.bin".into(),
                data: std::rc::Rc::new(vec![1, 2, 3, 4, 5, 6]),
                pos: 3,
            },
        );

        // Serialize/deserialize the snapshot directly (without GC) to test rehydration.
        // Note: capture_core_state triggers GC which correctly cleans up decoders
        // not referenced by live ResourceInputStream objects.
        let snapshot = jvm.vm.save_snapshot(&jvm.scheduler);
        let serialized = bincode::serialize(&snapshot).unwrap();
        let deserialized: vm::VmSnapshot = bincode::deserialize(&serialized).unwrap();

        let mut restored = new_test_core(false);
        restored.load_resource("foo.bin", &[1, 2, 3, 4, 5, 6]);
        let sched = restored.vm.restore_snapshot(deserialized);
        restored.scheduler = sched;

        let decoder = restored
            .vm
            .resource_decoders
            .get(&7)
            .expect("resource decoder should be restored");
        assert_eq!(decoder.name, "foo.bin");
        assert_eq!(decoder.pos, 3);
        assert_eq!(*decoder.data, vec![1, 2, 3, 4, 5, 6]);
    }

    #[test]
    fn test_claim_pending_async_is_exactly_once() {
        use crate::vm::AsyncRequestType;

        let mut jvm = new_test_core(false);
        let id1 = jvm.vm.issue_async_request(11, AsyncRequestType::RmsKeys);
        let id2 = jvm.vm.issue_async_request(
            12,
            AsyncRequestType::RmsLoad {
                key: "slot-a".into(),
            },
        );

        let first = jvm.vm.claim_pending_async_requests(8);
        assert_eq!(
            first.len(),
            2,
            "both pending requests should be claimed once"
        );
        assert_eq!(first[0].0, id1);
        assert_eq!(first[1].0, id2);

        let second = jvm.vm.claim_pending_async_requests(8);
        assert!(
            second.is_empty(),
            "claimed requests must not be returned a second time",
        );

        assert_eq!(jvm.get_async_state_counts(), (0, 2, 0));
    }

    #[test]
    fn test_resolve_async_requires_claimed_state() {
        use crate::vm::{AsyncRequestType, AsyncResult};

        let mut jvm = new_test_core(false);
        let request_id = jvm.vm.issue_async_request(7, AsyncRequestType::RmsKeys);

        assert!(
            !jvm.vm.resolve_async_request(
                request_id,
                AsyncResult::RmsKeysList {
                    keys: vec!["a".into()]
                },
            ),
            "Pending requests must not resolve before being claimed",
        );

        assert!(jvm.vm.claim_async_request(request_id));
        assert!(jvm.vm.resolve_async_request(
            request_id,
            AsyncResult::RmsKeysList {
                keys: vec!["a".into()]
            },
        ));
        assert_eq!(jvm.get_async_state_counts(), (0, 0, 1));
    }

    #[test]
    fn test_load_state_clears_async_runtime_state() {
        use crate::vm::{AsyncRequestType, AsyncResult};

        let mut jvm = new_test_core(false);
        let request_id = jvm.vm.issue_async_request(
            3,
            AsyncRequestType::DecodeImage {
                surface_id: 9,
                image_bytes: vec![1, 2, 3],
            },
        );
        assert!(jvm.vm.claim_async_request(request_id));
        assert!(jvm.vm.resolve_async_request(
            request_id,
            AsyncResult::ImageDecoded {
                width: 1,
                height: 1
            },
        ));
        assert_eq!(jvm.get_async_state_counts(), (0, 0, 1));

        let snapshot = jvm.save_state(None);
        let mut restored = new_test_core(false);
        restored.load_state(&snapshot);

        assert_eq!(
            restored.get_async_state_counts(),
            (0, 0, 0),
            "load_state must drop transient async lifecycle state",
        );
        assert_eq!(restored.get_async_state_counts(), (0, 0, 0));
    }

    /// 压力测试：模拟 150 次房间切换以复现 OOM，循环 10 万次 step(16)，
    /// debug 模式下耗时过长。请用 `cargo test --release -- --ignored` 运行。
    #[test]
    #[ignore]
    fn test_minimal_repro_no_oom() {
        let jar = include_bytes!(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/tests/fixtures/demo-jars/MinimalRepro.jar"
        ));
        let mut jvm = new_test_core(false);
        jvm.load_jar(jar).expect("load MinimalRepro jar");
        let ctx_id = jvm
            .create_midlet_context("MinimalRepro")
            .expect("create context");
        jvm.start_midlet(ctx_id).expect("start midlet");

        // Run for enough steps to complete 150 room switches
        for i in 0..100_000 {
            let s = jvm.step(16);
            resolve_all_pending_async(&mut jvm);
            if s == 0 {
                break;
            }

            if jvm.vm.heap.oom {
                let output = jvm.get_output();
                panic!("OOM at step {}: bump={}, allocated={}, threshold={}, live_objects={}\noutput: {}",
                    i, jvm.vm.heap.used_bytes(), jvm.vm.heap.allocated_bytes(),
                    jvm.vm.heap.gc_threshold(), jvm.vm.heap.live_object_count(), output);
            }
        }

        let output = jvm.get_output();
        eprintln!(
            "=== Heap stats: bump={}, allocated={}, threshold={}, live={} ===",
            jvm.vm.heap.used_bytes(),
            jvm.vm.heap.allocated_bytes(),
            jvm.vm.heap.gc_threshold(),
            jvm.vm.heap.live_object_count()
        );
        assert!(!output.contains("OutOfMemoryError"), "Got OOM: {}", output);
    }

    #[test]
    fn test_gc_leak_suite() {
        let jar = include_bytes!(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/tests/fixtures/demo-jars/GCLeakTestSuite.jar"
        ));
        let mut jvm = new_test_core(false);
        jvm.load_jar(jar).expect("load GCLeakTestSuite jar");
        // Provide the test resource so ResourceInputStream tests can open it
        jvm.load_resource("test_resource.dat", b"TESTDATA0123456789ABCDEF");
        let ctx_id = jvm
            .create_midlet_context("GCLeakTestSuite")
            .expect("create context");
        jvm.start_midlet(ctx_id).expect("start midlet");

        // Helper to send key press+release
        let send_key = |jvm: &mut JvmCore, code: i32| {
            jvm.push_native_event(vm::NativeEventData {
                event_type: 1,
                int_param1: 1,
                int_param2: code,
                int_param3: 0,
                int_param4: 1,
            });
            for _ in 0..50 {
                if jvm.step(16) <= 0 {
                    break;
                }
            }
            jvm.push_native_event(vm::NativeEventData {
                event_type: 1,
                int_param1: 2,
                int_param2: code,
                int_param3: 0,
                int_param4: 1,
            });
            for _ in 0..50 {
                if jvm.step(16) <= 0 {
                    break;
                }
            }
        };

        // Run test 5 (ResourceStream Leak Test, index 4)
        for _ in 0..4 {
            send_key(&mut jvm, -2);
        } // DOWN to index 4
        send_key(&mut jvm, -5); // FIRE

        // Run enough steps for the test to complete
        for _ in 0..10000 {
            let s = jvm.step(16);
            if s == 0 {
                break;
            }
        }

        // Trigger a final GC
        let threads: Vec<_> = jvm.scheduler.threads.values().cloned().collect();
        gc::collect(&mut jvm.vm, &threads);

        // Verify: no OOM, no unhandled exceptions
        assert!(!jvm.vm.heap.oom, "should not OOM during GCLeakTestSuite");
        let output = jvm.get_output();
        assert!(
            !output.contains("Unhandled exception"),
            "output={:?}",
            output
        );
        assert!(!output.contains("OutOfMemoryError"), "output={:?}", output);

        // Bug #5 verification: all ResourceInputStream decoders must be freed after close()
        assert_eq!(
            jvm.vm.resource_decoders.len(),
            0,
            "Bug #5: resource_decoders should be empty after all streams closed, got {}",
            jvm.vm.resource_decoders.len()
        );

        // Verify the Java-side test printed PASS marker
        assert!(
            output.contains("PASS: ResourceStream leak test"),
            "ResourceStream leak test did not pass: {:?}",
            output
        );
    }

    #[test]
    fn test_rewind_snapshot_preserves_instance_core_slots() {
        let mut instance = JvmInstance::new(Some("rewind-slots"), false);
        let mut selector = new_instance_test_core(&instance, "selector");
        selector.vm.display_width = 111;
        let mut app = new_instance_test_core(&instance, "app");
        app.vm.display_width = 222;
        instance.selector = Some(Box::new(selector));
        instance.app = Some(Box::new(app));
        instance.bind_current_to_app();

        let persistent_snapshot = instance.save_state_fast();
        let decoded =
            JvmInstance::decode_save_state_payload::<InstanceSaveState>(&persistent_snapshot)
                .expect("fast persistent snapshot");
        assert!(decoded.selector.is_some());
        assert!(decoded.app.is_some());
        let mut persistent_restore = JvmInstance::new(Some("rewind-fast-save-restore"), false);
        assert!(persistent_restore.load_state(&persistent_snapshot));
        assert!(persistent_restore.current_is_app());

        let snapshot = instance.capture_for_rewind();

        instance.selector = None;
        instance.app = None;
        instance.clear_current();

        assert!(instance.restore_from_rewind(*snapshot));
        assert!(instance.current_is_app());
        assert_eq!(instance.selector.as_ref().unwrap().vm.display_width, 111);
        assert_eq!(instance.app.as_ref().unwrap().vm.display_width, 222);
    }

    #[test]
    fn test_image_surface_snapshots_include_inactive_core_slots() {
        let mut instance = JvmInstance::new(Some("image-snapshot-slots"), false);
        let mut selector = new_instance_test_core(&instance, "selector");
        let selector_image = selector.vm.new_object("javax.microedition.lcdui.ImageData");
        let width_off = selector
            .vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "width")
            .expect("ImageData.width");
        let height_off = selector
            .vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "height")
            .expect("ImageData.height");
        selector.vm.heap.write_u32(selector_image + width_off, 2);
        selector.vm.heap.write_u32(selector_image + height_off, 2);
        selector.vm.image_surface_map.insert(selector_image, 77);
        selector.vm.main_thread_obj = selector_image;

        let mut app = new_instance_test_core(&instance, "app");
        let app_image = app.vm.new_object("javax.microedition.lcdui.ImageData");
        let app_width_off = app
            .vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "width")
            .expect("ImageData.width");
        let app_height_off = app
            .vm
            .class_repo
            .find_field_offset("javax.microedition.lcdui.ImageData", "height")
            .expect("ImageData.height");
        app.vm.heap.write_u32(app_image + app_width_off, 3);
        app.vm.heap.write_u32(app_image + app_height_off, 3);
        app.vm.image_surface_map.insert(app_image, 88);
        app.vm.main_thread_obj = app_image;

        instance.selector = Some(Box::new(selector));
        instance.app = Some(Box::new(app));
        instance.bind_current_to_app();

        let targets = instance.prepare_image_surface_snapshot_targets();
        assert!(
            targets.iter().any(|target| {
                target.surface_id == 77 && target.width == 2 && target.height == 2
            }),
            "inactive selector image surface should be exposed for host snapshot capture"
        );
        assert!(
            targets.iter().any(|target| {
                target.surface_id == 88 && target.width == 3 && target.height == 3
            }),
            "active app image surface should still be exposed for host snapshot capture"
        );

        instance.clear_pending_image_surface_snapshots();
        instance.add_pending_image_surface_snapshot(77, 2, 2, vec![1, 2, 3, 4]);
        instance.add_pending_image_surface_snapshot(88, 3, 3, vec![5; 9]);

        let state = instance.save_state(None);
        let decoded =
            JvmInstance::decode_save_state_payload::<InstanceSaveState>(&state).expect("state");
        assert_eq!(
            decoded
                .selector
                .expect("selector state")
                .snapshot
                .image_surface_snapshots
                .len(),
            1
        );
        assert_eq!(
            decoded
                .app
                .expect("app state")
                .snapshot
                .image_surface_snapshots
                .len(),
            1
        );
    }
}

/// Resolve all pending async requests with empty/default results (for tests).
#[cfg(test)]
fn resolve_all_pending_async(jvm: &mut JvmCore) {
    use vm::{AsyncRequestType, AsyncResult};
    let pending = jvm.vm.claim_pending_async_requests(usize::MAX);
    let pending: Vec<(u32, vm::AsyncRequestType)> = pending
        .into_iter()
        .map(|(id, req)| (id, req.request_type))
        .collect();
    for (id, req_type) in pending {
        match req_type {
            AsyncRequestType::DecodeImage { .. } => {
                assert!(jvm.vm.resolve_async_request(
                    id,
                    AsyncResult::ImageDecoded {
                        width: 0,
                        height: 0
                    }
                ));
            }
            AsyncRequestType::RmsLoad { .. } => {
                assert!(jvm
                    .vm
                    .resolve_async_request(id, AsyncResult::RmsLoaded { data: None }));
            }
            AsyncRequestType::RmsKeys => {
                assert!(jvm
                    .vm
                    .resolve_async_request(id, AsyncResult::RmsKeysList { keys: Vec::new() }));
            }
            AsyncRequestType::GetPixels { width, height, .. } => {
                assert!(jvm.vm.resolve_async_request(
                    id,
                    AsyncResult::PixelsRead {
                        pixels: vec![0; (width.max(0) * height.max(0)) as usize]
                    }
                ));
            }
            AsyncRequestType::ReadM3gImage { width, height, .. } => {
                assert!(jvm.vm.resolve_async_request(
                    id,
                    AsyncResult::M3gImageRead {
                        width,
                        height,
                        pixels: vec![0; (width.max(0) * height.max(0) * 4) as usize]
                    }
                ));
            }
            AsyncRequestType::DecodeM3gExternalImage { .. } => {
                assert!(jvm.vm.resolve_async_request(
                    id,
                    AsyncResult::M3gImageRead {
                        width: 0,
                        height: 0,
                        pixels: Vec::new(),
                    }
                ));
            }
            AsyncRequestType::TextInput { .. } => {
                assert!(jvm
                    .vm
                    .resolve_async_request(id, AsyncResult::TextInputCommitted { text: None },));
            }
            AsyncRequestType::DateInput {
                initialized,
                value_millis,
                ..
            } => {
                assert!(jvm.vm.resolve_async_request(
                    id,
                    AsyncResult::DateInputCommitted {
                        initialized,
                        value_millis,
                        committed: false,
                    },
                ));
            }
            AsyncRequestType::Bluetooth { .. } => {
                assert!(jvm.vm.resolve_async_request(
                    id,
                    AsyncResult::BluetoothCompleted {
                        response: "ERR\tBluetooth unavailable".to_string(),
                    },
                ));
            }
        }
    }
}
