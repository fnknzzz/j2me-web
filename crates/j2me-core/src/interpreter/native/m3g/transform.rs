use crate::interpreter::native::registry::{NativeContext, NativeRegistry};

use super::{array_as_mut_ptr, array_as_ptr, ffi, M3G_MATRIX_BYTES};

fn identity(ctx: &mut NativeContext<'_>, matrix: u32) {
    if matrix != 0 {
        unsafe { ffi::m3gIdentityMatrix(array_as_mut_ptr(ctx.vm, matrix)) }
    }
}
fn multiply(ctx: &mut NativeContext<'_>, product: u32, left: u32, right: u32) {
    if product != 0 && left != 0 && right != 0 {
        let mut temporary = [0u8; M3G_MATRIX_BYTES];
        unsafe {
            ffi::m3gMatrixProduct(
                temporary.as_mut_ptr(),
                array_as_ptr(ctx.vm, left),
                array_as_ptr(ctx.vm, right),
            );
            std::ptr::copy_nonoverlapping(
                temporary.as_ptr(),
                array_as_mut_ptr(ctx.vm, product),
                temporary.len(),
            );
        }
    }
}
fn set_matrix(ctx: &mut NativeContext<'_>, matrix: u32, values: u32) {
    if matrix != 0 && values != 0 {
        unsafe {
            ffi::m3gSetMatrixRows(
                array_as_mut_ptr(ctx.vm, matrix),
                array_as_ptr(ctx.vm, values) as *const f32,
            )
        }
    }
}
fn get_matrix(ctx: &mut NativeContext<'_>, matrix: u32, values: u32) {
    if matrix != 0 && values != 0 {
        unsafe {
            ffi::m3gGetMatrixRows(
                array_as_ptr(ctx.vm, matrix),
                array_as_mut_ptr(ctx.vm, values) as *mut f32,
            )
        }
    }
}
fn transform_table(ctx: &mut NativeContext<'_>, matrix_ref: u32, values: u32) {
    if matrix_ref == 0 || values == 0 {
        return;
    }
    let mut matrix = [0.0; 16];
    unsafe { ffi::m3gGetMatrixRows(array_as_ptr(ctx.vm, matrix_ref), matrix.as_mut_ptr()) };
    for offset in (0..ctx.vm.heap.array_length(values)).step_by(4) {
        let x = f32::from_bits(ctx.vm.heap.array_read_u32(values, offset));
        let y = f32::from_bits(ctx.vm.heap.array_read_u32(values, offset + 1));
        let z = f32::from_bits(ctx.vm.heap.array_read_u32(values, offset + 2));
        let w = f32::from_bits(ctx.vm.heap.array_read_u32(values, offset + 3));
        ctx.vm.heap.array_write_u32(
            values,
            offset,
            (matrix[0] * x + matrix[1] * y + matrix[2] * z + matrix[3] * w).to_bits(),
        );
        ctx.vm.heap.array_write_u32(
            values,
            offset + 1,
            (matrix[4] * x + matrix[5] * y + matrix[6] * z + matrix[7] * w).to_bits(),
        );
        ctx.vm.heap.array_write_u32(
            values,
            offset + 2,
            (matrix[8] * x + matrix[9] * y + matrix[10] * z + matrix[11] * w).to_bits(),
        );
        ctx.vm.heap.array_write_u32(
            values,
            offset + 3,
            (matrix[12] * x + matrix[13] * y + matrix[14] * z + matrix[15] * w).to_bits(),
        );
    }
}
fn invert(ctx: &mut NativeContext<'_>, matrix: u32) {
    if matrix != 0 && unsafe { ffi::m3gInvertMatrix(array_as_mut_ptr(ctx.vm, matrix)) } == 0 {
        ctx.vm.pending_exception_str = Some("java.lang.ArithmeticException".to_string());
    }
}
fn transpose(ctx: &mut NativeContext<'_>, matrix: u32) {
    if matrix != 0 {
        let pointer = array_as_mut_ptr(ctx.vm, matrix);
        unsafe { ffi::m3gMatrixTranspose(pointer, pointer) }
    }
}
fn rotate(ctx: &mut NativeContext<'_>, matrix: u32, angle: f32, x: f32, y: f32, z: f32) {
    if matrix != 0 {
        unsafe { ffi::m3gPostRotateMatrix(array_as_mut_ptr(ctx.vm, matrix), angle, x, y, z) }
    }
}
fn rotate_quat(ctx: &mut NativeContext<'_>, matrix: u32, x: f32, y: f32, z: f32, w: f32) {
    if matrix != 0 {
        unsafe {
            ffi::m3gPostRotateMatrixQuat(array_as_mut_ptr(ctx.vm, matrix), [x, y, z, w].as_ptr())
        }
    }
}
fn scale(ctx: &mut NativeContext<'_>, matrix: u32, x: f32, y: f32, z: f32) {
    if matrix != 0 {
        unsafe { ffi::m3gPostScaleMatrix(array_as_mut_ptr(ctx.vm, matrix), x, y, z) }
    }
}
fn translate(ctx: &mut NativeContext<'_>, matrix: u32, x: f32, y: f32, z: f32) {
    if matrix != 0 {
        unsafe { ffi::m3gPostTranslateMatrix(array_as_mut_ptr(ctx.vm, matrix), x, y, z) }
    }
}
fn transform_array(ctx: &mut NativeContext<'_>, matrix: u32, array: i32, out: u32, w: bool) {
    if matrix != 0 && array != 0 && out != 0 {
        unsafe {
            ffi::m3gTransformArray(
                // VertexArray is Java-visible as a 32-bit opaque handle on native
                // iOS. Resolve it before entering m3gcore, just as the other M3G
                // resource APIs do; passing the raw Java int truncates the pointer.
                array as usize,
                array_as_mut_ptr(ctx.vm, matrix),
                array_as_mut_ptr(ctx.vm, out) as *mut f32,
                ctx.vm.heap.array_length(out) as i32,
                w as i32,
            )
        }
    }
}

fn camera_ctor(ctx: &mut NativeContext<'_>, interface: i32) -> i32 {
    let handle = unsafe { ffi::m3gCreateCamera(interface as usize) };
    ctx.vm.m3g_resources.create_camera_node(handle);
    handle as i32
}
fn camera_parallel(
    _ctx: &mut NativeContext<'_>,
    handle: i32,
    height: f32,
    aspect: f32,
    near: f32,
    far: f32,
) {
    if handle != 0 {
        unsafe { ffi::m3gSetParallel(handle as usize, height, aspect, near, far) }
    }
}
fn camera_perspective(
    _ctx: &mut NativeContext<'_>,
    handle: i32,
    fovy: f32,
    aspect: f32,
    near: f32,
    far: f32,
) {
    if handle != 0 {
        unsafe { ffi::m3gSetPerspective(handle as usize, fovy, aspect, near, far) }
    }
}
fn camera_generic(ctx: &mut NativeContext<'_>, handle: i32, matrix: u32) {
    if handle != 0 && matrix != 0 {
        unsafe { ffi::m3gSetProjectionMatrix(handle as usize, array_as_ptr(ctx.vm, matrix)) }
    }
}
fn camera_projection_matrix(ctx: &mut NativeContext<'_>, handle: i32, matrix: u32) -> i32 {
    if matrix != 0 && ctx.vm.heap.array_length(matrix) < M3G_MATRIX_BYTES as u32 {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return 0;
    }
    if handle != 0 {
        unsafe { ffi::m3gGetProjectionAsMatrix(handle as usize, array_as_mut_ptr(ctx.vm, matrix)) }
    } else {
        0
    }
}
fn camera_projection_params(ctx: &mut NativeContext<'_>, handle: i32, params: u32) -> i32 {
    if params != 0 && ctx.vm.heap.array_length(params) < 4 {
        ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
        return 0;
    }
    if handle != 0 {
        unsafe {
            ffi::m3gGetProjectionAsParams(
                handle as usize,
                array_as_mut_ptr(ctx.vm, params) as *mut f32,
            )
        }
    } else {
        0
    }
}

fn matrix_to_angle_axis(m: &[f32; 16]) -> (f32, f32, f32, f32) {
    let angle = ((m[0] + m[5] + m[10] - 1.0) / 2.0).clamp(-1.0, 1.0).acos();
    let degrees = angle * 180.0 / std::f32::consts::PI;
    if angle.abs() < 1e-6 {
        return (0.0, 0.0, 0.0, 1.0);
    }
    let sin = angle.sin();
    if sin.abs() < 1e-6 {
        let x = ((m[0] + 1.0) / 2.0).max(0.0).sqrt();
        let y = ((m[5] + 1.0) / 2.0)
            .max(0.0)
            .sqrt()
            .copysign(if m[4] < 0.0 { -1.0 } else { 1.0 });
        let z = ((m[10] + 1.0) / 2.0)
            .max(0.0)
            .sqrt()
            .copysign(if m[8] < 0.0 { -1.0 } else { 1.0 });
        return (degrees, x, y, z);
    }
    let denominator = 2.0 * sin;
    (
        degrees,
        (m[6] - m[9]) / denominator,
        (m[8] - m[2]) / denominator,
        (m[1] - m[4]) / denominator,
    )
}

fn set_orientation(
    ctx: &mut NativeContext<'_>,
    h: i32,
    a: f32,
    x: f32,
    y: f32,
    z: f32,
    absolute: bool,
) {
    if absolute {
        unsafe { ffi::m3gSetOrientation(h as usize, a, x, y, z) }
    } else {
        unsafe { ffi::m3gPostRotate(h as usize, a, x, y, z) }
    }
    if let Some(n) = ctx.vm.m3g_resources.nodes.get_mut(&(h as u32)) {
        if absolute {
            n.transformable.orientation = [a, x, y, z]
        } else {
            let old = super::resource_store::angle_axis_to_matrix(
                n.transformable.orientation[0],
                n.transformable.orientation[1],
                n.transformable.orientation[2],
                n.transformable.orientation[3],
            );
            let rotation = super::resource_store::angle_axis_to_matrix(a, x, y, z);
            let (angle, x, y, z) =
                matrix_to_angle_axis(&super::resource_store::mat4_multiply(&old, &rotation));
            n.transformable.orientation = [angle, x, y, z];
        }
    }
}
fn pre_rotate(ctx: &mut NativeContext<'_>, h: i32, a: f32, x: f32, y: f32, z: f32) {
    unsafe { ffi::m3gPreRotate(h as usize, a, x, y, z) }
    if let Some(n) = ctx.vm.m3g_resources.nodes.get_mut(&(h as u32)) {
        let old = super::resource_store::angle_axis_to_matrix(
            n.transformable.orientation[0],
            n.transformable.orientation[1],
            n.transformable.orientation[2],
            n.transformable.orientation[3],
        );
        let rotation = super::resource_store::angle_axis_to_matrix(a, x, y, z);
        let (angle, x, y, z) =
            matrix_to_angle_axis(&super::resource_store::mat4_multiply(&rotation, &old));
        n.transformable.orientation = [angle, x, y, z];
    }
}
fn get_orientation(ctx: &mut NativeContext<'_>, h: i32, out: u32) {
    unsafe { ffi::m3gGetOrientation(h as usize, array_as_mut_ptr(ctx.vm, out) as *mut f32) }
}
fn set_scale(ctx: &mut NativeContext<'_>, h: i32, x: f32, y: f32, z: f32, absolute: bool) {
    if absolute {
        unsafe { ffi::m3gSetScale(h as usize, x, y, z) }
    } else {
        unsafe { ffi::m3gScale(h as usize, x, y, z) }
    }
    if let Some(n) = ctx.vm.m3g_resources.nodes.get_mut(&(h as u32)) {
        if absolute {
            n.transformable.scale = [x, y, z]
        } else {
            n.transformable.scale[0] *= x;
            n.transformable.scale[1] *= y;
            n.transformable.scale[2] *= z
        }
    }
}
fn get_scale(ctx: &mut NativeContext<'_>, h: i32, out: u32) {
    unsafe { ffi::m3gGetScale(h as usize, array_as_mut_ptr(ctx.vm, out) as *mut f32) }
}
fn set_translation(ctx: &mut NativeContext<'_>, h: i32, x: f32, y: f32, z: f32, absolute: bool) {
    if absolute {
        unsafe { ffi::m3gSetTranslation(h as usize, x, y, z) }
    } else {
        unsafe { ffi::m3gTranslate(h as usize, x, y, z) }
    }
    if let Some(n) = ctx.vm.m3g_resources.nodes.get_mut(&(h as u32)) {
        if absolute {
            n.transformable.translation = [x, y, z]
        } else {
            n.transformable.translation[0] += x;
            n.transformable.translation[1] += y;
            n.transformable.translation[2] += z
        }
    }
}
fn get_translation(ctx: &mut NativeContext<'_>, h: i32, out: u32) {
    unsafe { ffi::m3gGetTranslation(h as usize, array_as_mut_ptr(ctx.vm, out) as *mut f32) }
}
fn set_transform(ctx: &mut NativeContext<'_>, h: i32, input: u32) {
    unsafe { ffi::m3gSetTransform(h as usize, array_as_ptr(ctx.vm, input)) };
    let matrix = if input == 0 {
        None
    } else {
        let p = array_as_ptr(ctx.vm, input) as *const f32;
        let mut m = [0.0; 16];
        unsafe {
            for (i, v) in m.iter_mut().enumerate() {
                *v = *p.add(i)
            }
        }
        Some(m)
    };
    if let Some(n) = ctx.vm.m3g_resources.nodes.get_mut(&(h as u32)) {
        if let Some(m) = matrix {
            n.transformable.transform = m;
            n.transformable.has_transform = true
        } else {
            n.transformable.transform = super::resource_store::identity_matrix();
            n.transformable.has_transform = false
        }
    }
}
fn get_transform(ctx: &mut NativeContext<'_>, h: i32, out: u32) {
    unsafe { ffi::m3gGetTransform(h as usize, array_as_mut_ptr(ctx.vm, out)) }
}
fn get_composite(ctx: &mut NativeContext<'_>, h: i32, out: u32) {
    unsafe { ffi::m3gGetCompositeTransform(h as usize, array_as_mut_ptr(ctx.vm, out)) }
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.Transform._setIdentity([B)V" => identity(matrix: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._mul([B[B[B)V" => multiply(product: u32, left: u32, right: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._setMatrix([B[F)V" => set_matrix(matrix: u32, values: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._getMatrix([B[F)V" => get_matrix(matrix: u32, values: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._transformTable([B[F)V" => transform_table(matrix: u32, values: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._invert([B)V" => invert(matrix: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._transpose([B)V" => transpose(matrix: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._rotate([BFFFF)V" => rotate(matrix: u32, angle: f32, x: f32, y: f32, z: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._rotateQuat([BFFFF)V" => rotate_quat(matrix: u32, x: f32, y: f32, z: f32, w: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._scale([BFFF)V" => scale(matrix: u32, x: f32, y: f32, z: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._translate([BFFF)V" => translate(matrix: u32, x: f32, y: f32, z: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Transform._transformArray([BI[FZ)V" => transform_array(matrix: u32, array: i32, out: u32, w: bool) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Camera._ctor(I)I" => camera_ctor(interface: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Camera._setParallel(IFFFF)V" => camera_parallel(handle: i32, height: f32, aspect: f32, near: f32, far: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Camera._setPerspective(IFFFF)V" => camera_perspective(handle: i32, fovy: f32, aspect: f32, near: f32, far: f32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Camera._setGeneric(I[B)V" => camera_generic(handle: i32, matrix: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Camera._getProjectionAsTransform(I[B)I" => camera_projection_matrix(handle: i32, matrix: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Camera._getProjectionAsParams(I[F)I" => camera_projection_params(handle: i32, params: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Transformable._setOrientation(IFFFFZ)V"=>set_orientation(handle:i32,angle:f32,x:f32,y:f32,z:f32,absolute:bool)->()),
        crate::native_bind!(static "javax.microedition.m3g.Transformable._preRotate(IFFFF)V"=>pre_rotate(handle:i32,angle:f32,x:f32,y:f32,z:f32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Transformable._getOrientation(I[F)V"=>get_orientation(handle:i32,out:u32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Transformable._setScale(IFFFZ)V"=>set_scale(handle:i32,x:f32,y:f32,z:f32,absolute:bool)->()),
        crate::native_bind!(static "javax.microedition.m3g.Transformable._getScale(I[F)V"=>get_scale(handle:i32,out:u32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Transformable._setTranslation(IFFFZ)V"=>set_translation(handle:i32,x:f32,y:f32,z:f32,absolute:bool)->()),
        crate::native_bind!(static "javax.microedition.m3g.Transformable._getTranslation(I[F)V"=>get_translation(handle:i32,out:u32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Transformable._setTransform(I[B)V"=>set_transform(handle:i32,input:u32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Transformable._getTransform(I[B)V"=>get_transform(handle:i32,out:u32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Transformable._getComposite(I[B)V"=>get_composite(handle:i32,out:u32)->()),
    ];
    registry.register_bindings(&bindings);
}
