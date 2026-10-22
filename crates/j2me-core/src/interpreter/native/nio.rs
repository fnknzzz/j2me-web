//! JSR-239 direct NIO buffer support.
//!
//! The reference runtime exposes direct buffers to Java as integer native
//! addresses.  In the WASM VM those addresses are offsets into
//! `SharedVm::direct_buffer_arena`, whose lifetime is VM-owned and snapshot
//! safe.

use crate::heap::NULL;
use crate::interpreter::native::registry::{NativeContext, NativeRegistry};

fn set_bounds_exception(ctx: &mut NativeContext<'_>) {
    ctx.vm.pending_exception_str = Some("java.lang.IndexOutOfBoundsException".to_string());
}

fn checked_len(length: i32, element_size: usize) -> Option<usize> {
    usize::try_from(length).ok()?.checked_mul(element_size)
}

fn array_range(
    ctx: &mut NativeContext<'_>,
    array: u32,
    offset: i32,
    length: i32,
) -> Option<(u32, u32)> {
    if array == NULL {
        ctx.vm.pending_exception_str = Some("java.lang.NullPointerException".to_string());
        return None;
    }
    let (Ok(offset), Ok(length)) = (u32::try_from(offset), u32::try_from(length)) else {
        set_bounds_exception(ctx);
        return None;
    };
    let end = offset.checked_add(length)?;
    if end > ctx.vm.heap.array_length(array) {
        set_bounds_exception(ctx);
        return None;
    }
    Some((offset, length))
}

fn alloc_native(ctx: &mut NativeContext<'_>, capacity: i32) -> i32 {
    let Some(capacity) = usize::try_from(capacity).ok() else {
        ctx.vm.pending_exception_str = Some("java.lang.OutOfMemoryError".to_string());
        return 0;
    };
    match ctx.vm.direct_buffer_arena.allocate(capacity) {
        Some(address) => address as i32,
        None => {
            ctx.vm.pending_exception_str = Some("java.lang.OutOfMemoryError".to_string());
            0
        }
    }
}

fn copy_bytes(
    ctx: &mut NativeContext<'_>,
    source_address: i32,
    destination_address: i32,
    bytes: i32,
) {
    let Some(bytes) = checked_len(bytes, 1) else {
        set_bounds_exception(ctx);
        return;
    };
    if !ctx
        .vm
        .direct_buffer_arena
        .copy_within(source_address, destination_address, bytes)
    {
        set_bounds_exception(ctx);
    }
}

fn get_byte(ctx: &mut NativeContext<'_>, address: i32) -> i32 {
    match ctx.vm.direct_buffer_arena.read_i8(address) {
        Some(value) => value as i32,
        None => {
            set_bounds_exception(ctx);
            0
        }
    }
}

fn put_byte(ctx: &mut NativeContext<'_>, address: i32, value: i32) {
    if !ctx.vm.direct_buffer_arena.write_i8(address, value as i8) {
        set_bounds_exception(ctx);
    }
}

fn get_short(ctx: &mut NativeContext<'_>, address: i32) -> i32 {
    match ctx.vm.direct_buffer_arena.read_i16(address) {
        Some(value) => value as i32,
        None => {
            set_bounds_exception(ctx);
            0
        }
    }
}

fn put_short(ctx: &mut NativeContext<'_>, address: i32, value: i32) {
    if !ctx.vm.direct_buffer_arena.write_i16(address, value as i16) {
        set_bounds_exception(ctx);
    }
}

fn get_int(ctx: &mut NativeContext<'_>, address: i32) -> i32 {
    match ctx.vm.direct_buffer_arena.read_i32(address) {
        Some(value) => value,
        None => {
            set_bounds_exception(ctx);
            0
        }
    }
}

fn put_int(ctx: &mut NativeContext<'_>, address: i32, value: i32) {
    if !ctx.vm.direct_buffer_arena.write_i32(address, value) {
        set_bounds_exception(ctx);
    }
}

fn get_float(ctx: &mut NativeContext<'_>, address: i32) -> f32 {
    match ctx.vm.direct_buffer_arena.read_f32(address) {
        Some(value) => value,
        None => {
            set_bounds_exception(ctx);
            0.0
        }
    }
}

fn put_float(ctx: &mut NativeContext<'_>, address: i32, value: f32) {
    if !ctx.vm.direct_buffer_arena.write_f32(address, value) {
        set_bounds_exception(ctx);
    }
}

fn get_bytes(
    ctx: &mut NativeContext<'_>,
    address: i32,
    destination: u32,
    offset: i32,
    length: i32,
) {
    let Some((offset, length)) = array_range(ctx, destination, offset, length) else {
        return;
    };
    let Some(bytes) = checked_len(length as i32, 1) else {
        set_bounds_exception(ctx);
        return;
    };
    let Some(source) = ctx.vm.direct_buffer_arena.bytes(address, bytes) else {
        set_bounds_exception(ctx);
        return;
    };
    let Some(destination) = ctx.vm.heap.array_bytes_mut(destination, offset, length, 1) else {
        set_bounds_exception(ctx);
        return;
    };
    destination.copy_from_slice(source);
}

fn put_bytes(ctx: &mut NativeContext<'_>, address: i32, source: u32, offset: i32, length: i32) {
    let Some((offset, length)) = array_range(ctx, source, offset, length) else {
        return;
    };
    if checked_len(length as i32, 1).is_none() {
        set_bounds_exception(ctx);
        return;
    }
    let Some(source) = ctx.vm.heap.array_bytes(source, offset, length, 1) else {
        set_bounds_exception(ctx);
        return;
    };
    if !ctx.vm.direct_buffer_arena.copy_from_slice(address, source) {
        set_bounds_exception(ctx);
    }
}

fn get_shorts(
    ctx: &mut NativeContext<'_>,
    address: i32,
    destination: u32,
    offset: i32,
    length: i32,
) {
    let Some((offset, length)) = array_range(ctx, destination, offset, length) else {
        return;
    };
    let Some(bytes) = checked_len(length as i32, 2) else {
        set_bounds_exception(ctx);
        return;
    };
    let Some(source) = ctx.vm.direct_buffer_arena.bytes(address, bytes) else {
        set_bounds_exception(ctx);
        return;
    };
    let Some(destination) = ctx.vm.heap.array_bytes_mut(destination, offset, length, 2) else {
        set_bounds_exception(ctx);
        return;
    };
    destination.copy_from_slice(source);
}

fn put_shorts(ctx: &mut NativeContext<'_>, address: i32, source: u32, offset: i32, length: i32) {
    let Some((offset, length)) = array_range(ctx, source, offset, length) else {
        return;
    };
    if checked_len(length as i32, 2).is_none() {
        set_bounds_exception(ctx);
        return;
    }
    let Some(source) = ctx.vm.heap.array_bytes(source, offset, length, 2) else {
        set_bounds_exception(ctx);
        return;
    };
    if !ctx.vm.direct_buffer_arena.copy_from_slice(address, source) {
        set_bounds_exception(ctx);
    }
}

fn get_ints(ctx: &mut NativeContext<'_>, address: i32, destination: u32, offset: i32, length: i32) {
    let Some((offset, length)) = array_range(ctx, destination, offset, length) else {
        return;
    };
    let Some(bytes) = checked_len(length as i32, 4) else {
        set_bounds_exception(ctx);
        return;
    };
    let Some(source) = ctx.vm.direct_buffer_arena.bytes(address, bytes) else {
        set_bounds_exception(ctx);
        return;
    };
    let Some(destination) = ctx.vm.heap.array_bytes_mut(destination, offset, length, 4) else {
        set_bounds_exception(ctx);
        return;
    };
    destination.copy_from_slice(source);
}

fn put_ints(ctx: &mut NativeContext<'_>, address: i32, source: u32, offset: i32, length: i32) {
    let Some((offset, length)) = array_range(ctx, source, offset, length) else {
        return;
    };
    if checked_len(length as i32, 4).is_none() {
        set_bounds_exception(ctx);
        return;
    }
    let Some(source) = ctx.vm.heap.array_bytes(source, offset, length, 4) else {
        set_bounds_exception(ctx);
        return;
    };
    if !ctx.vm.direct_buffer_arena.copy_from_slice(address, source) {
        set_bounds_exception(ctx);
    }
}

fn get_floats(
    ctx: &mut NativeContext<'_>,
    address: i32,
    destination: u32,
    offset: i32,
    length: i32,
) {
    let Some((offset, length)) = array_range(ctx, destination, offset, length) else {
        return;
    };
    let Some(bytes) = checked_len(length as i32, 4) else {
        set_bounds_exception(ctx);
        return;
    };
    let Some(source) = ctx.vm.direct_buffer_arena.bytes(address, bytes) else {
        set_bounds_exception(ctx);
        return;
    };
    let Some(destination) = ctx.vm.heap.array_bytes_mut(destination, offset, length, 4) else {
        set_bounds_exception(ctx);
        return;
    };
    destination.copy_from_slice(source);
}

fn put_floats(ctx: &mut NativeContext<'_>, address: i32, source: u32, offset: i32, length: i32) {
    let Some((offset, length)) = array_range(ctx, source, offset, length) else {
        return;
    };
    if checked_len(length as i32, 4).is_none() {
        set_bounds_exception(ctx);
        return;
    }
    let Some(source) = ctx.vm.heap.array_bytes(source, offset, length, 4) else {
        set_bounds_exception(ctx);
        return;
    };
    if !ctx.vm.direct_buffer_arena.copy_from_slice(address, source) {
        set_bounds_exception(ctx);
    }
}

fn get_native_address(ctx: &mut NativeContext<'_>, buffer: u32, offset: i32) -> i32 {
    let Some(class_name) = ctx.vm.get_class_name_of_object(buffer) else {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return 0;
    };
    let (element_width, field_class) = match class_name {
        "java.nio.ByteBufferImpl" => (1_i32, "java.nio.ByteBuffer"),
        "java.nio.ShortBufferImpl" => (2, "java.nio.ShortBuffer"),
        "java.nio.IntBufferImpl" => (4, "java.nio.IntBuffer"),
        "java.nio.FloatBufferImpl" => (4, "java.nio.FloatBuffer"),
        _ => {
            ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
            return 0;
        }
    };
    let Some(is_direct_offset) = ctx.vm.class_repo.find_field_offset(field_class, "isDirect")
    else {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return 0;
    };
    if ctx.vm.heap.read_i32(buffer + is_direct_offset) == 0 {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return 0;
    }
    let Some(array_offset) = ctx
        .vm
        .class_repo
        .find_field_offset(field_class, "arrayOffset")
    else {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return 0;
    };
    let base_address = ctx.vm.heap.read_i32(buffer + array_offset);
    let Some(byte_offset) = offset.checked_mul(element_width) else {
        set_bounds_exception(ctx);
        return 0;
    };
    base_address.checked_add(byte_offset).unwrap_or_else(|| {
        set_bounds_exception(ctx);
        0
    })
}

fn finalize(_ctx: &mut NativeContext<'_>, _this: u32) {}

pub fn register(registry: &mut NativeRegistry) {
    registry.register_bindings(&[
        crate::native_bind!(static "java.nio.ByteBufferImpl._allocNative(I)I" => alloc_native(capacity: i32) -> i32),
        crate::native_bind!(static "java.nio.ByteBufferImpl._copyBytes(III)V" => copy_bytes(source_address: i32, destination_address: i32, bytes: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._getByte(I)B" => get_byte(address: i32) -> i32),
        crate::native_bind!(static "java.nio.ByteBufferImpl._getBytes(I[BII)V" => get_bytes(address: i32, destination: u32, offset: i32, length: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._putByte(IB)V" => put_byte(address: i32, value: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._putBytes(I[BII)V" => put_bytes(address: i32, source: u32, offset: i32, length: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._getShort(I)S" => get_short(address: i32) -> i32),
        crate::native_bind!(static "java.nio.ByteBufferImpl._getShorts(I[SII)V" => get_shorts(address: i32, destination: u32, offset: i32, length: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._putShort(IS)V" => put_short(address: i32, value: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._putShorts(I[SII)V" => put_shorts(address: i32, source: u32, offset: i32, length: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._getInt(I)I" => get_int(address: i32) -> i32),
        crate::native_bind!(static "java.nio.ByteBufferImpl._getInts(I[III)V" => get_ints(address: i32, destination: u32, offset: i32, length: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._putInt(II)V" => put_int(address: i32, value: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._putInts(I[III)V" => put_ints(address: i32, source: u32, offset: i32, length: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._getFloat(I)F" => get_float(address: i32) -> f32),
        crate::native_bind!(static "java.nio.ByteBufferImpl._getFloats(I[FII)V" => get_floats(address: i32, destination: u32, offset: i32, length: i32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._putFloat(IF)V" => put_float(address: i32, value: f32) -> ()),
        crate::native_bind!(static "java.nio.ByteBufferImpl._putFloats(I[FII)V" => put_floats(address: i32, source: u32, offset: i32, length: i32) -> ()),
        crate::native_bind!(instance "java.nio.ByteBufferImpl.finalize()V" => finalize(this: u32) -> ()),
        crate::native_bind!(static "com.sun.jsr239.BufferManager._getBytes(I[BII)V" => get_bytes(address: i32, destination: u32, offset: i32, length: i32) -> ()),
        crate::native_bind!(static "com.sun.jsr239.GL10Impl._getNativeAddress(Ljava/nio/Buffer;I)I" => get_native_address(buffer: u32, offset: i32) -> i32),
    ]);
}
