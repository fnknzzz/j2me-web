use crate::heap::NULL;

use super::registry::{NativeContext, NativeRegistry};

// --- Individual native handler functions ---

fn unchecked_arraycopy(
    ctx: &mut NativeContext<'_>,
    src: u32,
    src_pos: i32,
    dest: u32,
    dest_pos: i32,
    length: i32,
) {
    let length = length as u32;
    let src_pos = src_pos as u32;
    let dest_pos = dest_pos as u32;
    if length > 0 && src != NULL && dest != NULL {
        let class_id = ctx.vm.heap.get_class_id(src);
        let bpe = ctx.vm.class_repo.get_array_bpe(class_id);
        let bpe = if bpe == 0 { 4 } else { bpe as u32 };
        let _ = ctx
            .vm
            .heap
            .copy_array_elements(src, src_pos, dest, dest_pos, length, bpe);
    }
}

fn monotonic_time(ctx: &mut NativeContext<'_>) -> i64 {
    ctx.vm.effective_now() as i64
}

pub fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "com.sun.cldchi.jvm.JVM.unchecked_char_arraycopy([CI[CII)V" => unchecked_arraycopy(src: u32, src_pos: i32, dest: u32, dest_pos: i32, length: i32) -> ()),
        crate::native_bind!(static "com.sun.cldchi.jvm.JVM.unchecked_int_arraycopy([II[III)V" => unchecked_arraycopy(src: u32, src_pos: i32, dest: u32, dest_pos: i32, length: i32) -> ()),
        crate::native_bind!(static "com.sun.cldchi.jvm.JVM.unchecked_obj_arraycopy([Ljava/lang/Object;I[Ljava/lang/Object;II)V" => unchecked_arraycopy(src: u32, src_pos: i32, dest: u32, dest_pos: i32, length: i32) -> ()),
        crate::native_bind!(static "com.sun.cldchi.jvm.JVM.monotonicTimeMillis()J" => monotonic_time() -> i64),
    ];
    registry.register_bindings(&bindings);
}
