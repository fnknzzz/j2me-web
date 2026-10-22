use crate::heap::NULL;
use crate::interpreter::native::registry::{NativeContext, NativeRegistry};
use crate::vm::ResourceDecoder;
use std::rc::Rc;

// --- Individual native handler functions ---

fn resource_open(ctx: &mut NativeContext<'_>, name_ref: u32) -> u32 {
    let name = ctx.vm.get_string_value(name_ref).unwrap_or_default();
    let lookup = if name.starts_with('/') {
        &name[1..]
    } else {
        &name
    };
    if let Some(data) = ctx.vm.get_resource(lookup) {
        let id = ctx.vm.alloc_decoder_id();
        ctx.vm.resource_decoders.insert(
            id,
            ResourceDecoder {
                name: lookup.to_string(),
                data,
                pos: 0,
            },
        );
        id
    } else {
        NULL
    }
}

fn resource_bytes_remain(ctx: &mut NativeContext<'_>, decoder_ref: u32) -> i32 {
    ctx.vm
        .resource_decoders
        .get(&decoder_ref)
        .map(|d| (d.data.len() - d.pos) as i32)
        .unwrap_or(0)
}

fn resource_read_byte(ctx: &mut NativeContext<'_>, decoder_ref: u32) -> i32 {
    if let Some(d) = ctx.vm.resource_decoders.get_mut(&decoder_ref) {
        if d.pos < d.data.len() {
            let b = d.data[d.pos] as i32;
            d.pos += 1;
            b
        } else {
            -1
        }
    } else {
        -1
    }
}

fn resource_read_bytes(
    ctx: &mut NativeContext<'_>,
    decoder_ref: u32,
    arr_ref: u32,
    off: i32,
    len: i32,
) -> i32 {
    let (Ok(off), Ok(len)) = (u32::try_from(off), u32::try_from(len)) else {
        return 0;
    };
    if let Some(d) = ctx.vm.resource_decoders.get_mut(&decoder_ref) {
        let remaining = d.data.len() - d.pos;
        if remaining == 0 {
            -1
        } else {
            let to_read = (len as usize).min(remaining);
            let arr_len = ctx.vm.heap.array_length(arr_ref);
            let actual = to_read.min(arr_len.saturating_sub(off) as usize);
            let Some(destination) = ctx.vm.heap.array_bytes_mut(arr_ref, off, actual as u32, 1)
            else {
                return 0;
            };
            destination.copy_from_slice(&d.data[d.pos..d.pos + actual]);
            d.pos += actual;
            actual as i32
        }
    } else {
        -1
    }
}

fn resource_clone(ctx: &mut NativeContext<'_>, source_ref: u32) -> u32 {
    if let Some(d) = ctx.vm.resource_decoders.get(&source_ref) {
        let cloned = ResourceDecoder {
            name: d.name.clone(),
            data: Rc::clone(&d.data),
            pos: d.pos,
        };
        let id = ctx.vm.alloc_decoder_id();
        ctx.vm.resource_decoders.insert(id, cloned);
        id
    } else {
        NULL
    }
}

fn resource_close(ctx: &mut NativeContext<'_>, decoder_ref: u32) {
    ctx.vm.resource_decoders.remove(&decoder_ref);
}

pub fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "com.sun.cldc.io.ResourceInputStream.open(Ljava/lang/String;)Ljava/lang/Object;" => resource_open(name: u32) -> u32),
        crate::native_bind!(static "com.sun.cldc.io.ResourceInputStream.bytesRemain(Ljava/lang/Object;)I" => resource_bytes_remain(decoder: u32) -> i32),
        crate::native_bind!(static "com.sun.cldc.io.ResourceInputStream.readByte(Ljava/lang/Object;)I" => resource_read_byte(decoder: u32) -> i32),
        crate::native_bind!(static "com.sun.cldc.io.ResourceInputStream.readBytes(Ljava/lang/Object;[BII)I" => resource_read_bytes(decoder: u32, buffer: u32, offset: i32, length: i32) -> i32),
        crate::native_bind!(static "com.sun.cldc.io.ResourceInputStream.clone(Ljava/lang/Object;)Ljava/lang/Object;" => resource_clone(source: u32) -> u32),
        crate::native_bind!(static "com.sun.cldc.io.ResourceInputStream.close0(Ljava/lang/Object;)V" => resource_close(decoder: u32) -> ()),
    ];
    registry.register_bindings(&bindings);
}
