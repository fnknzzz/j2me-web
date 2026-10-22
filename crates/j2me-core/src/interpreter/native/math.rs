use super::registry::{NativeContext, NativeRegistry};

fn float_to_int_bits(_ctx: &mut NativeContext<'_>, value: f32) -> i32 {
    value.to_bits() as i32
}

fn int_bits_to_float(_ctx: &mut NativeContext<'_>, bits: i32) -> f32 {
    f32::from_bits(bits as u32)
}

fn double_to_long_bits(_ctx: &mut NativeContext<'_>, value: f64) -> i64 {
    value.to_bits() as i64
}

fn long_bits_to_double(_ctx: &mut NativeContext<'_>, bits: i64) -> f64 {
    f64::from_bits(bits as u64)
}

fn floor(_ctx: &mut NativeContext<'_>, value: f64) -> f64 {
    value.floor()
}

fn ceil(_ctx: &mut NativeContext<'_>, value: f64) -> f64 {
    value.ceil()
}

fn sqrt(_ctx: &mut NativeContext<'_>, value: f64) -> f64 {
    value.sqrt()
}

fn sin(_ctx: &mut NativeContext<'_>, value: f64) -> f64 {
    value.sin()
}

fn cos(_ctx: &mut NativeContext<'_>, value: f64) -> f64 {
    value.cos()
}

fn tan(_ctx: &mut NativeContext<'_>, value: f64) -> f64 {
    value.tan()
}

fn asin(_ctx: &mut NativeContext<'_>, value: f64) -> f64 {
    value.asin()
}

fn acos(_ctx: &mut NativeContext<'_>, value: f64) -> f64 {
    value.acos()
}

fn atan(_ctx: &mut NativeContext<'_>, value: f64) -> f64 {
    value.atan()
}

fn atan2(_ctx: &mut NativeContext<'_>, y: f64, x: f64) -> f64 {
    y.atan2(x)
}

pub fn register(registry: &mut NativeRegistry) {
    registry.register_bindings(&[
        crate::native_bind!(static "java.lang.Float.floatToIntBits(F)I" => float_to_int_bits(value: f32) -> i32),
        crate::native_bind!(static "java.lang.Float.intBitsToFloat(I)F" => int_bits_to_float(bits: i32) -> f32),
        crate::native_bind!(static "java.lang.Double.doubleToLongBits(D)J" => double_to_long_bits(value: f64) -> i64),
        crate::native_bind!(static "java.lang.Double.longBitsToDouble(J)D" => long_bits_to_double(bits: i64) -> f64),
        crate::native_bind!(static "java.lang.Math.floor(D)D" => floor(value: f64) -> f64),
        crate::native_bind!(static "java.lang.Math.ceil(D)D" => ceil(value: f64) -> f64),
        crate::native_bind!(static "java.lang.Math.sqrt(D)D" => sqrt(value: f64) -> f64),
        crate::native_bind!(static "java.lang.Math.sin(D)D" => sin(value: f64) -> f64),
        crate::native_bind!(static "java.lang.Math.cos(D)D" => cos(value: f64) -> f64),
        crate::native_bind!(static "java.lang.Math.tan(D)D" => tan(value: f64) -> f64),
        crate::native_bind!(static "java.lang.Math.asin(D)D" => asin(value: f64) -> f64),
        crate::native_bind!(static "java.lang.Math.acos(D)D" => acos(value: f64) -> f64),
        crate::native_bind!(static "java.lang.Math.atan(D)D" => atan(value: f64) -> f64),
        crate::native_bind!(static "java.lang.Math.atan2(DD)D" => atan2(y: f64, x: f64) -> f64),
    ]);
}
