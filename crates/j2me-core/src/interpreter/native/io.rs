use crate::vm::cmd;

use super::registry::{NativeContext, NativeRegistry};

fn console_write(ctx: &mut NativeContext<'_>, _this: u32, value: i32) {
    let ch = (value as u32 & 0xff) as u8 as char;
    ctx.vm.text_output.push(ch);
    ctx.vm.command_buffer.push(cmd::PRINT_CHAR);
    ctx.vm
        .command_buffer
        .extend_from_slice(&(ch as u32).to_le_bytes());
}

pub fn register(registry: &mut NativeRegistry) {
    registry.register_bindings(&[
        crate::native_bind!(instance "com.sun.cldchi.io.ConsoleOutputStream.write(I)V" => console_write(this: u32, value: i32) -> ()),
    ]);
}
