use crate::interpreter::native::registry::{NativeContext, NativeRegistry};

use super::resource_store::{Image2DData, Texture2DData};
use super::{
    array_as_ptr, ffi, image_bytes_per_pixel, image_data_is_too_short,
    write_invalidate_image_textures_cmd,
};

fn ctor_size_pixels(
    ctx: &mut NativeContext<'_>,
    interface: i32,
    format: i32,
    width: i32,
    height: i32,
    pixels: u32,
) -> i32 {
    if pixels == 0 {
        ctx.vm.pending_exception_str = Some("java.lang.NullPointerException".to_string());
        return 0;
    }
    if let Some(bytes_per_pixel) = image_bytes_per_pixel(format) {
        if image_data_is_too_short(ctx.vm, pixels, width, height, bytes_per_pixel) {
            ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
            return 0;
        }
    }
    let native = unsafe { ffi::m3gCreateImage(interface as usize, format, width, height, 0) };
    if native != 0 {
        unsafe {
            ffi::m3gSetImage(native as usize, array_as_ptr(ctx.vm, pixels));
            ffi::m3gCommitImage(native as usize);
        }
        let handle = native;
        write_invalidate_image_textures_cmd(ctx.vm, handle);
        ctx.vm.m3g_resources.images.insert(
            handle,
            Image2DData {
                format,
                width,
                height,
            },
        );
    }
    (native) as i32
}
fn ctor_size_pixels_palette(
    ctx: &mut NativeContext<'_>,
    interface: i32,
    format: i32,
    width: i32,
    height: i32,
    pixels: u32,
    palette: u32,
) -> i32 {
    if pixels == 0 || palette == 0 {
        ctx.vm.pending_exception_str = Some("java.lang.NullPointerException".to_string());
        return 0;
    }
    if let Some(color_components) = image_bytes_per_pixel(format) {
        if image_data_is_too_short(ctx.vm, pixels, width, height, 1) {
            ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
            return 0;
        }
        let palette_bytes = ctx.vm.heap.array_length(palette);
        let max_palette_bytes = 256 * color_components;
        if palette_bytes < max_palette_bytes && palette_bytes % color_components != 0 {
            ctx.vm.pending_exception_str = Some("java.lang.IllegalArgumentException".to_string());
            return 0;
        }
    }
    let native = unsafe { ffi::m3gCreateImage(interface as usize, format, width, height, 0x10) };
    if native != 0 {
        let color_components = image_bytes_per_pixel(format).unwrap_or(1);
        let palette_entries = (ctx.vm.heap.array_length(palette) / color_components).min(256);
        unsafe {
            ffi::m3gSetImage(native as usize, array_as_ptr(ctx.vm, pixels));
            ffi::m3gSetImagePalette(
                native as usize,
                palette_entries as i32,
                array_as_ptr(ctx.vm, palette),
            );
            ffi::m3gCommitImage(native as usize);
        }
        let handle = native;
        write_invalidate_image_textures_cmd(ctx.vm, handle);
        ctx.vm.m3g_resources.images.insert(
            handle,
            Image2DData {
                format,
                width,
                height,
            },
        );
    }
    (native) as i32
}
fn ctor_size(
    ctx: &mut NativeContext<'_>,
    interface: i32,
    format: i32,
    width: i32,
    height: i32,
) -> i32 {
    let native = unsafe { ffi::m3gCreateImage(interface as usize, format, width, height, 0) };
    if native != 0 {
        let handle = native;
        ctx.vm.m3g_resources.images.insert(
            handle,
            Image2DData {
                format,
                width,
                height,
            },
        );
    }
    (native) as i32
}
fn set(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    pixels: u32,
) {
    if handle != 0 && pixels != 0 {
        let length = ctx.vm.heap.array_length(pixels) as i32;
        unsafe {
            ffi::m3gSetSubImage(
                handle as usize,
                x,
                y,
                width,
                height,
                length,
                array_as_ptr(ctx.vm, pixels),
            )
        };
        write_invalidate_image_textures_cmd(ctx.vm, handle as u32);
    }
}
fn is_mutable(_: &mut NativeContext<'_>, handle: i32) -> bool {
    handle != 0 && unsafe { ffi::m3gIsMutable(handle as usize) != 0 }
}
fn get_format(ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    ctx.vm
        .m3g_resources
        .images
        .get(&(handle as u32))
        .map_or(0, |image| image.format)
}
fn get_width(ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    ctx.vm
        .m3g_resources
        .images
        .get(&(handle as u32))
        .map_or(0, |image| image.width)
}
fn get_height(ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    ctx.vm
        .m3g_resources
        .images
        .get(&(handle as u32))
        .map_or(0, |image| image.height)
}

fn texture_ctor(ctx: &mut NativeContext<'_>, interface: i32, image: i32) -> i32 {
    let native = unsafe { ffi::m3gCreateTexture(interface as usize, image as usize) };
    if native != 0 {
        let handle = native;
        ctx.vm.m3g_resources.textures.insert(
            handle,
            Texture2DData {
                image: image as u32,
                level_filter: 208,
                image_filter: 210,
                wrap_s: 241,
                wrap_t: 241,
                blending: 227,
                blend_color: 0,
            },
        );
    }
    (native) as i32
}
fn texture_set_image(ctx: &mut NativeContext<'_>, handle: i32, image: i32) {
    if handle != 0 {
        unsafe { ffi::m3gSetTextureImage(handle as usize, image as usize) };
        if let Some(texture) = ctx.vm.m3g_resources.textures.get_mut(&(handle as u32)) {
            texture.image = image as u32;
        }
    }
}
fn texture_get_image(ctx: &mut NativeContext<'_>, handle: i32) -> i32 {
    ctx.vm
        .m3g_resources
        .textures
        .get(&(handle as u32))
        .map_or(0, |texture| texture.image as i32)
}
fn texture_set_filtering(
    ctx: &mut NativeContext<'_>,
    handle: i32,
    level_filter: i32,
    image_filter: i32,
) {
    unsafe { ffi::m3gSetFiltering(handle as usize, level_filter, image_filter) };
    if let Some(texture) = ctx.vm.m3g_resources.textures.get_mut(&(handle as u32)) {
        texture.level_filter = level_filter;
        texture.image_filter = image_filter;
    }
}
fn texture_set_wrapping(ctx: &mut NativeContext<'_>, handle: i32, wrap_s: i32, wrap_t: i32) {
    unsafe { ffi::m3gSetWrapping(handle as usize, wrap_s, wrap_t) };
    if let Some(texture) = ctx.vm.m3g_resources.textures.get_mut(&(handle as u32)) {
        texture.wrap_s = wrap_s;
        texture.wrap_t = wrap_t;
    }
}
fn texture_get_wrapping_s(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    unsafe { ffi::m3gGetWrappingS(handle as usize) }
}
fn texture_get_wrapping_t(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    unsafe { ffi::m3gGetWrappingT(handle as usize) }
}
fn texture_set_blending(ctx: &mut NativeContext<'_>, handle: i32, func: i32) {
    let core_func = match func {
        224 => 0,
        225 => 1,
        226 => 2,
        227 => 3,
        228 => 4,
        _ => func,
    };
    unsafe { ffi::m3gTextureSetBlending(handle as usize, core_func) };
    if let Some(texture) = ctx.vm.m3g_resources.textures.get_mut(&(handle as u32)) {
        texture.blending = func;
    }
}
fn texture_get_blending(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    match unsafe { ffi::m3gTextureGetBlending(handle as usize) } {
        0 => 224,
        1 => 225,
        2 => 226,
        3 => 227,
        4 => 228,
        other => other,
    }
}
fn texture_set_blend_color(ctx: &mut NativeContext<'_>, handle: i32, color: i32) {
    unsafe { ffi::m3gSetBlendColor(handle as usize, color as u32) };
    if let Some(texture) = ctx.vm.m3g_resources.textures.get_mut(&(handle as u32)) {
        texture.blend_color = color;
    }
}
fn texture_get_blend_color(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    unsafe { ffi::m3gGetBlendColor(handle as usize) as i32 }
}
fn texture_get_image_filter(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    let mut level = 0;
    let mut image = 0;
    unsafe { ffi::m3gGetFiltering(handle as usize, &mut level, &mut image) };
    image
}
fn texture_get_level_filter(_: &mut NativeContext<'_>, handle: i32) -> i32 {
    let mut level = 0;
    let mut image = 0;
    unsafe { ffi::m3gGetFiltering(handle as usize, &mut level, &mut image) };
    level
}

pub(super) fn register(registry: &mut NativeRegistry) {
    let bindings = [
        crate::native_bind!(static "javax.microedition.m3g.Image2D._ctorSizePixels(IIII[B)I" => ctor_size_pixels(interface: i32, format: i32, width: i32, height: i32, pixels: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Image2D._ctorSizePixelsPalette(IIII[B[B)I" => ctor_size_pixels_palette(interface: i32, format: i32, width: i32, height: i32, pixels: u32, palette: u32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Image2D._ctorSize(IIII)I" => ctor_size(interface: i32, format: i32, width: i32, height: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Image2D._set(IIIII[B)V" => set(handle: i32, x: i32, y: i32, width: i32, height: i32, pixels: u32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Image2D._isMutable(I)Z" => is_mutable(handle: i32) -> bool),
        crate::native_bind!(static "javax.microedition.m3g.Image2D._getFormat(I)I" => get_format(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Image2D._getWidth(I)I" => get_width(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Image2D._getHeight(I)I" => get_height(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._ctor(II)I" => texture_ctor(interface: i32, image: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._setImage(II)V" => texture_set_image(handle: i32, image: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._getImage(I)I" => texture_get_image(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._setFiltering(III)V" => texture_set_filtering(handle: i32, level_filter: i32, image_filter: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._setWrapping(III)V" => texture_set_wrapping(handle: i32, wrap_s: i32, wrap_t: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._getWrappingS(I)I" => texture_get_wrapping_s(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._getWrappingT(I)I" => texture_get_wrapping_t(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._setBlending(II)V" => texture_set_blending(handle: i32, func: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._getBlending(I)I" => texture_get_blending(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._setBlendColor(II)V" => texture_set_blend_color(handle: i32, color: i32) -> ()),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._getBlendColor(I)I" => texture_get_blend_color(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._getImageFilter(I)I" => texture_get_image_filter(handle: i32) -> i32),
        crate::native_bind!(static "javax.microedition.m3g.Texture2D._getLevelFilter(I)I" => texture_get_level_filter(handle: i32) -> i32),
    ];
    registry.register_bindings(&bindings);
}
