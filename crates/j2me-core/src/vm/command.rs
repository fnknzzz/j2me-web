use serde::{Deserialize, Serialize};

/// A native event to be delivered to the Java NativeEventMonitor.
/// Mirrors the fields of com.sun.midp.events.NativeEvent.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct NativeEventData {
    pub event_type: i32,
    pub int_param1: i32,
    pub int_param2: i32,
    pub int_param3: i32,
    pub int_param4: i32,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct GraphicsState {
    pub surface_id: i32,
    pub color: i32,
    pub clip_x: i32,
    pub clip_y: i32,
    pub clip_w: i32,
    pub clip_h: i32,
    pub translate_x: i32,
    pub translate_y: i32,
    pub stroke_style: i32,
    pub font_face: i32,
    pub font_style: i32,
    pub font_size: i32,
}

/// Binary command IDs for the unified command buffer protocol.
/// ALL VM→JS communication goes through the command buffer.
#[allow(dead_code)]
pub mod cmd {
    // Graphics commands (1-19)
    pub const DRAW_STRING: u8 = 4;
    pub const DRAW_REGION: u8 = 12;
    pub const COPY_AREA: u8 = 13;
    pub const FLUSH: u8 = 14;
    pub const CREATE_SURFACE: u8 = 15;
    /// RGBA8 pixels ready for direct host texture upload.
    pub const DRAW_RGBA: u8 = 16;
    pub const DESTROY_SURFACE: u8 = 17;
    /// Consecutive GPU-ready LCDUI packets sharing one GraphicsState.
    pub const LCDUI_DRAW_SHAPE_STREAM: u8 = 18;
    /// Consecutive image draws sharing one source surface and GraphicsState.
    pub const DRAW_IMAGE_STREAM: u8 = 19;

    // Text output (20-21)
    pub const PRINTLN: u8 = 20;
    pub const PRINT_CHAR: u8 = 21;

    // Display hooks (30-32)
    pub const DISPLAY_GET_WIDTH: u8 = 30;
    pub const DISPLAY_GET_HEIGHT: u8 = 31;
    pub const DISPLAY_SIZE_CHANGED: u8 = 32;

    // Menu hooks (40)
    pub const MENU_UPDATE_COMMANDS: u8 = 40;

    // RMS hooks (50-51)
    pub const RMS_LOAD: u8 = 50;
    pub const RMS_KEYS: u8 = 51;

    // Media hooks (60-69)
    pub const MEDIA_CREATE_PLAYER: u8 = 60;
    pub const MEDIA_CLOSE: u8 = 61;
    pub const MEDIA_REALIZE: u8 = 62;
    pub const MEDIA_PREFETCH: u8 = 63;
    pub const MEDIA_PLAY: u8 = 64;
    pub const MEDIA_STOP: u8 = 65;
    pub const MEDIA_SET_VOLUME: u8 = 66;
    pub const MEDIA_SET_MUTED: u8 = 67;
    pub const MEDIA_SET_SEQUENCE: u8 = 68;
    pub const MEDIA_PLAY_TONE: u8 = 69;
    pub const MEDIA_SET_LOOP_COUNT: u8 = 70;

    // LCDUI visibility hook (72)
    pub const LCDUI_SET_VISIBLE: u8 = 72;

    // Binary media (73)
    pub const MEDIA_CREATE_PLAYER_BINARY: u8 = 73;

    // Host input overlays (74-75)
    pub const TEXT_INPUT: u8 = 74;
    pub const DATE_INPUT: u8 = 75;

    // M3G context commands (128-139)
    pub const M3G_CLEAR: u8 = 131;
    pub const M3G_BIND_TARGET: u8 = 135;
    pub const M3G_RELEASE_TARGET: u8 = 136;
    pub const M3G_BIND_IMAGE_TARGET: u8 = 137;

    // M3G resource commands (140-159)
    pub const M3G_CREATE_VERTEX_ARRAY: u8 = 140;
    pub const M3G_DELETE_RESOURCE: u8 = 148;
    pub const M3G_INVALIDATE_IMAGE_TEXTURES: u8 = 149;
    pub const M3G_CREATE_INDEX_BUFFER: u8 = 150;

    // M3G render commands (160-169)
    pub const M3G_BEGIN_WGPU_BATCH: u8 = 164;
    pub const M3G_END_WGPU_BATCH: u8 = 165;

    // M3G camera/background (170-179)
    pub const M3G_CREATE_BACKGROUND: u8 = 175;
    pub const M3G_BG_SET_COLOR: u8 = 176;
    pub const M3G_BG_SET_ENABLE: u8 = 178;

    // Mascot Capsule primitive renderer commands (190-199)
    pub const MASCOT_BIND_TARGET: u8 = 190;
    pub const MASCOT_RELEASE_TARGET: u8 = 191;
    pub const MASCOT_DRAW_PRIMITIVES: u8 = 192;
    pub const MASCOT_FLUSH: u8 = 193;
    pub const MASCOT_UPLOAD_TEXTURE: u8 = 194;
    pub const MASCOT_DRAW_TEXTURED: u8 = 195;
    pub const MASCOT_DELETE_TEXTURE: u8 = 196;
    pub const MASCOT_RESET_TEXTURES: u8 = 197;
    pub const MASCOT_DRAW_COLOR_MATERIAL: u8 = 198;
    pub const MASCOT_CLEAR_DEPTH: u8 = 199;
}

impl GraphicsState {
    pub fn default_for_display(width: i32, height: i32) -> Self {
        Self {
            surface_id: 0,
            color: 0,
            clip_x: 0,
            clip_y: 0,
            clip_w: width,
            clip_h: height,
            translate_x: 0,
            translate_y: 0,
            stroke_style: 0,
            font_face: 0,
            font_style: 0,
            font_size: 0,
        }
    }

    /// Write the 36-byte binary state (matching hostbridge protocol layout).
    /// Layout: surfaceId(u32) clipX(i32) clipY(i32) clipW(i32) clipH(i32)
    ///         translateX(i32) translateY(i32) color(i32) strokeStyle(i32)
    #[inline]
    pub fn write_to_buf(&self, buf: &mut Vec<u8>) {
        buf.extend_from_slice(&(self.surface_id as u32).to_le_bytes());
        buf.extend_from_slice(&self.clip_x.to_le_bytes());
        buf.extend_from_slice(&self.clip_y.to_le_bytes());
        buf.extend_from_slice(&self.clip_w.to_le_bytes());
        buf.extend_from_slice(&self.clip_h.to_le_bytes());
        buf.extend_from_slice(&self.translate_x.to_le_bytes());
        buf.extend_from_slice(&self.translate_y.to_le_bytes());
        buf.extend_from_slice(&self.color.to_le_bytes());
        buf.extend_from_slice(&self.stroke_style.to_le_bytes());
    }

    /// Write the 48-byte text state (36B base + fontFace/fontStyle/fontSize).
    #[inline]
    pub fn write_text_state_to_buf(&self, buf: &mut Vec<u8>) {
        self.write_to_buf(buf);
        buf.extend_from_slice(&self.font_face.to_le_bytes());
        buf.extend_from_slice(&self.font_style.to_le_bytes());
        buf.extend_from_slice(&self.font_size.to_le_bytes());
    }
}
