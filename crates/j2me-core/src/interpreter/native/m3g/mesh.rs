use crate::interpreter::native::registry::{NativeContext, NativeRegistry};

use super::{ffi, read_i32_array};

fn mesh_ctor(
    ctx: &mut NativeContext<'_>,
    interface: i32,
    vertices: i32,
    triangles: u32,
    appearances: u32,
) -> i32 {
    let index_buffers: Vec<u32> = if triangles == 0 {
        Vec::new()
    } else {
        read_i32_array(ctx.vm, triangles)
            .into_iter()
            .map(|value| value as u32)
            .collect()
    };
    let appearance_handles: Vec<u32> = if appearances == 0 {
        Vec::new()
    } else {
        read_i32_array(ctx.vm, appearances)
            .into_iter()
            .map(|value| value as u32)
            .collect()
    };
    let handle = unsafe {
        ffi::m3gCreateMesh(
            interface as usize,
            vertices as usize,
            &index_buffers,
            (!appearance_handles.is_empty()).then_some(appearance_handles.as_slice()),
        )
    };
    ctx.vm
        .m3g_resources
        .create_mesh(handle, vertices as u32, index_buffers, appearance_handles);
    (handle) as i32
}
fn set_appearance(_ctx: &mut NativeContext<'_>, handle: i32, index: i32, appearance: i32) {
    unsafe { ffi::m3gSetAppearance(handle as usize, index, appearance as usize) }
}
fn get_appearance(_ctx: &mut NativeContext<'_>, handle: i32, index: i32) -> i32 {
    (unsafe { ffi::m3gGetAppearance(handle as usize, index) }) as i32
}
fn get_index_buffer(_ctx: &mut NativeContext<'_>, handle: i32, index: i32) -> i32 {
    (unsafe { ffi::m3gGetIndexBuffer(handle as usize, index) }) as i32
}
fn get_vertex_buffer(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    (unsafe { ffi::m3gGetVertexBuffer(handle as usize) }) as i32
}
fn get_submesh_count(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    unsafe { ffi::m3gGetSubmeshCount(handle as usize) }
}

fn sprite_ctor(
    _ctx: &mut NativeContext<'_>,
    interface: i32,
    scaled: bool,
    image: i32,
    appearance: i32,
) -> i32 {
    (unsafe {
        ffi::m3gCreateSprite(
            interface as usize,
            scaled as i32,
            image as usize,
            appearance as usize,
        )
    }) as i32
}
fn sprite_is_scaled(_ctx: &mut NativeContext<'_>, handle: i32) -> bool {
    unsafe { ffi::m3gIsScaledSprite(handle as usize) != 0 }
}
fn sprite_set_appearance(_ctx: &mut NativeContext<'_>, handle: i32, appearance: i32) {
    unsafe { ffi::m3gSetSpriteAppearance(handle as usize, appearance as usize) }
}
fn sprite_get_appearance(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    (unsafe { ffi::m3gGetSpriteAppearance(handle as usize) }) as i32
}
fn sprite_set_image(_ctx: &mut NativeContext<'_>, handle: i32, image: i32) {
    unsafe { ffi::m3gSetSpriteImage(handle as usize, image as usize) };
}
fn sprite_get_image(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    (unsafe { ffi::m3gGetSpriteImage(handle as usize) }) as i32
}
fn sprite_set_crop(
    _ctx: &mut NativeContext<'_>,
    handle: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
) {
    unsafe { ffi::m3gSetCrop(handle as usize, x, y, width, height) }
}
fn sprite_get_crop(_ctx: &mut NativeContext<'_>, handle: i32, which: i32) -> i32 {
    unsafe { ffi::m3gGetCrop(handle as usize, which) }
}

fn morphing_mesh_ctor(
    ctx: &mut NativeContext<'_>,
    interface: i32,
    vertices: i32,
    targets: u32,
    triangles: u32,
    appearances: u32,
) -> i32 {
    let targets: Vec<u32> = if targets == 0 {
        Vec::new()
    } else {
        read_i32_array(ctx.vm, targets)
            .into_iter()
            .map(|value| value as u32)
            .collect()
    };
    let indices: Vec<u32> = if triangles == 0 {
        Vec::new()
    } else {
        read_i32_array(ctx.vm, triangles)
            .into_iter()
            .map(|value| value as u32)
            .collect()
    };
    let appearances: Vec<u32> = if appearances == 0 {
        Vec::new()
    } else {
        read_i32_array(ctx.vm, appearances)
            .into_iter()
            .map(|value| value as u32)
            .collect()
    };
    (unsafe {
        ffi::m3gCreateMorphingMesh(
            interface as usize,
            vertices as usize,
            &targets,
            &indices,
            (!appearances.is_empty()).then_some(appearances.as_slice()),
        )
    }) as i32
}
fn morphing_mesh_set_weights(ctx: &mut NativeContext<'_>, handle: i32, weights: u32) {
    if weights != 0 {
        unsafe {
            ffi::m3gSetWeights(
                handle as usize,
                super::array_as_mut_ptr(ctx.vm, weights) as *mut f32,
                ctx.vm.heap.array_length(weights) as i32,
            )
        }
    }
}
fn morphing_mesh_get_weights(ctx: &mut NativeContext<'_>, handle: i32, weights: u32) {
    if weights != 0 {
        unsafe {
            ffi::m3gGetWeights(
                handle as usize,
                super::array_as_mut_ptr(ctx.vm, weights) as *mut f32,
                ctx.vm.heap.array_length(weights) as i32,
            )
        }
    }
}
fn morphing_mesh_get_target(_ctx: &mut NativeContext<'_>, handle: i32, index: i32) -> i32 {
    (unsafe { ffi::m3gGetMorphTarget(handle as usize, index) }) as i32
}
fn morphing_mesh_get_target_count(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    unsafe { ffi::m3gGetMorphTargetCount(handle as usize) }
}

fn skinned_mesh_ctor(
    ctx: &mut NativeContext<'_>,
    interface: i32,
    vertices: i32,
    triangles: u32,
    appearances: u32,
    skeleton: i32,
) -> i32 {
    let indices: Vec<u32> = if triangles == 0 {
        Vec::new()
    } else {
        read_i32_array(ctx.vm, triangles)
            .into_iter()
            .map(|value| value as u32)
            .collect()
    };
    let appearances: Vec<u32> = if appearances == 0 {
        Vec::new()
    } else {
        read_i32_array(ctx.vm, appearances)
            .into_iter()
            .map(|value| value as u32)
            .collect()
    };
    let handle = unsafe {
        ffi::m3gCreateSkinnedMesh(
            interface as usize,
            vertices as usize,
            &indices,
            (!appearances.is_empty()).then_some(appearances.as_slice()),
            skeleton as usize,
        )
    };
    if handle != 0 {
        ctx.vm
            .m3g_resources
            .create_mesh(handle, vertices as u32, indices, appearances);
    }
    (handle) as i32
}
fn skinned_mesh_add_transform(
    _ctx: &mut NativeContext<'_>,
    handle: i32,
    bone: i32,
    weight: i32,
    first_vertex: i32,
    num_vertices: i32,
) {
    if handle != 0 {
        unsafe {
            ffi::m3gAddTransform(
                handle as usize,
                bone as usize,
                weight,
                first_vertex,
                num_vertices,
            )
        }
    }
}
fn skinned_mesh_get_skeleton(_ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    if handle != 0 {
        (unsafe { ffi::m3gGetSkeleton(handle as usize) }) as i32
    } else {
        0
    }
}
fn skinned_mesh_get_bone_transform(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    bone: i32,
    transform: u32,
) {
    if handle != 0 && transform != 0 {
        unsafe {
            ffi::m3gGetBoneTransform(
                handle as usize,
                bone as usize,
                super::array_as_mut_ptr(ctx.vm, transform),
            )
        }
    }
}
fn skinned_mesh_get_bone_vertices(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    bone: i32,
    indices: u32,
    weights: u32,
) -> i32 {
    let ip = if indices != 0 {
        super::array_as_mut_ptr(ctx.vm, indices) as *mut i32
    } else {
        std::ptr::null_mut()
    };
    let wp = if weights != 0 {
        super::array_as_mut_ptr(ctx.vm, weights) as *mut f32
    } else {
        std::ptr::null_mut()
    };
    if handle != 0 {
        unsafe { ffi::m3gGetBoneVertices(handle as usize, bone as usize, ip, wp) as i32 }
    } else {
        0
    }
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.Mesh._ctor(II[I[I)I"=>mesh_ctor(interface:i32,vertices:i32,triangles:u32,appearances:u32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Mesh._setAppearance(III)V"=>set_appearance(handle:i32,index:i32,appearance:i32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Mesh._getAppearance(II)I"=>get_appearance(handle:i32,index:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Mesh._getIndexBuffer(II)I"=>get_index_buffer(handle:i32,index:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Mesh._getVertexBuffer(I)I"=>get_vertex_buffer(handle:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Mesh._getSubmeshCount(I)I"=>get_submesh_count(handle:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Sprite3D._ctor(IZII)I"=>sprite_ctor(interface:i32,scaled:bool,image:i32,appearance:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Sprite3D._isScaled(I)Z"=>sprite_is_scaled(handle:i32)->bool),
        crate::native_bind!(static "javax.microedition.m3g.Sprite3D._setAppearance(II)V"=>sprite_set_appearance(handle:i32,appearance:i32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Sprite3D._getAppearance(I)I"=>sprite_get_appearance(handle:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Sprite3D._setImage(II)V"=>sprite_set_image(handle:i32,image:i32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Sprite3D._getImage(I)I"=>sprite_get_image(handle:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.Sprite3D._setCrop(IIIII)V"=>sprite_set_crop(handle:i32,x:i32,y:i32,width:i32,height:i32)->()),
        crate::native_bind!(static "javax.microedition.m3g.Sprite3D._getCrop(II)I"=>sprite_get_crop(handle:i32,which:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.MorphingMesh._ctor(II[I[I[I)I"=>morphing_mesh_ctor(interface:i32,vertices:i32,targets:u32,triangles:u32,appearances:u32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.MorphingMesh._setWeights(I[F)V"=>morphing_mesh_set_weights(handle:i32,weights:u32)->()),
        crate::native_bind!(static "javax.microedition.m3g.MorphingMesh._getWeights(I[F)V"=>morphing_mesh_get_weights(handle:i32,weights:u32)->()),
        crate::native_bind!(static "javax.microedition.m3g.MorphingMesh._getMorphTarget(II)I"=>morphing_mesh_get_target(handle:i32,index:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.MorphingMesh._getMorphTargetCount(I)I"=>morphing_mesh_get_target_count(handle:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.SkinnedMesh._ctor(II[I[II)I"=>skinned_mesh_ctor(interface:i32,vertices:i32,triangles:u32,appearances:u32,skeleton:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.SkinnedMesh._addTransform(IIIII)V"=>skinned_mesh_add_transform(handle:i32,bone:i32,weight:i32,first_vertex:i32,num_vertices:i32)->()),
        crate::native_bind!(static "javax.microedition.m3g.SkinnedMesh._getSkeleton(I)I"=>skinned_mesh_get_skeleton(handle:i32)->i32),
        crate::native_bind!(static "javax.microedition.m3g.SkinnedMesh._getBoneTransform(II[B)V"=>skinned_mesh_get_bone_transform(handle:i32,bone:i32,transform:u32)->()),
        crate::native_bind!(static "javax.microedition.m3g.SkinnedMesh._getBoneVertices(II[I[F)I"=>skinned_mesh_get_bone_vertices(handle:i32,bone:i32,indices:u32,weights:u32)->i32),
    ];
    registry.register_bindings(&bindings)
}
