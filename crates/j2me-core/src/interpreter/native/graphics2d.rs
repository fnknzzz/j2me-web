//! Platform-neutral LCDUI tessellation and GPU vertex-packet encoding.
//!
//! Coordinates entering this module are logical LCDUI pixels. The generated
//! vertices match the shared WGSL/Metal shader layouts, so host renderers only
//! select a pipeline, upload bytes, set the clip, and draw.

use crate::vm::{GraphicsState, SharedVm};

pub const PIPELINE_COLOR: u32 = 0;
pub const PIPELINE_COLOR_AA: u32 = 1;
pub const PIPELINE_LINE_AA: u32 = 2;
pub const PIPELINE_ELLIPSE_AA: u32 = 3;
pub const PIPELINE_ROUND_RECT_AA: u32 = 4;
pub const PIPELINE_ROUND_RECT_STROKE_AA: u32 = 5;
pub const PIPELINE_ROUND_RECT_DOTTED_AA: u32 = 6;
pub const PIPELINE_ARC_FILL_AA: u32 = 7;
pub const PIPELINE_ARC_STROKE_AA: u32 = 8;
pub const PIPELINE_ARC_DOTTED_AA: u32 = 9;

type Point = (f64, f64);

fn color(value: i32) -> [f32; 4] {
    let c = value as u32;
    let raw_alpha = (c >> 24) & 255;
    [
        ((c >> 16) & 255) as f32 / 255.0,
        ((c >> 8) & 255) as f32 / 255.0,
        (c & 255) as f32 / 255.0,
        (if raw_alpha == 0 { 255 } else { raw_alpha }) as f32 / 255.0,
    ]
}

fn ndc(point: Point, width: i32, height: i32) -> [f32; 2] {
    [
        (point.0 * 2.0 / width.max(1) as f64 - 1.0) as f32,
        (1.0 - point.1 * 2.0 / height.max(1) as f64) as f32,
    ]
}

fn push_color_vertex(
    out: &mut Vec<f32>,
    p: Point,
    alpha: f32,
    state: GraphicsState,
    size: (i32, i32),
) {
    let p = ndc(p, size.0, size.1);
    let mut c = color(state.color);
    c[3] *= alpha;
    out.extend_from_slice(&[p[0], p[1], c[0], c[1], c[2], c[3]]);
}

pub trait ShapePacketSink {
    fn append_shape_packet(
        &mut self,
        state: GraphicsState,
        pipeline: u32,
        stride: u32,
        vertices: &[f32],
    );
}

impl ShapePacketSink for SharedVm {
    fn append_shape_packet(
        &mut self,
        state: GraphicsState,
        pipeline: u32,
        stride: u32,
        vertices: &[f32],
    ) {
        SharedVm::append_shape_packet(self, state, pipeline, stride, vertices);
    }
}

impl ShapePacketSink for &mut SharedVm {
    fn append_shape_packet(
        &mut self,
        state: GraphicsState,
        pipeline: u32,
        stride: u32,
        vertices: &[f32],
    ) {
        SharedVm::append_shape_packet(self, state, pipeline, stride, vertices);
    }
}

pub fn emit_packet(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    pipeline: u32,
    stride: u32,
    vertices: &[f32],
) {
    sink.append_shape_packet(state, pipeline, stride, vertices);
}

fn emit_color(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    size: (i32, i32),
    points: &[Point],
) {
    let mut vertices = Vec::with_capacity(points.len() * 6);
    for &point in points {
        push_color_vertex(&mut vertices, point, 1.0, state, size);
    }
    emit_packet(sink, state, PIPELINE_COLOR, 6, &vertices);
}

pub fn emit_line(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    size: (i32, i32),
    a: Point,
    b: Point,
) {
    emit_lines(sink, state, size, &[(a, b)]);
}

pub fn emit_fill_rect(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    size: (i32, i32),
    x: i32,
    y: i32,
    width: i32,
    height: i32,
) {
    if width <= 0 || height <= 0 {
        return;
    }
    let left = (x + state.translate_x) as f64;
    let top = (y + state.translate_y) as f64;
    emit_color(
        sink,
        state,
        size,
        &quad(left, top, left + width as f64, top + height as f64),
    );
}

pub fn emit_lines(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    size: (i32, i32),
    lines: &[(Point, Point)],
) {
    let mut packets = Vec::new();
    for &(a, b) in lines {
        let a = (
            a.0 + state.translate_x as f64,
            a.1 + state.translate_y as f64,
        );
        let b = (
            b.0 + state.translate_x as f64,
            b.1 + state.translate_y as f64,
        );
        append_line(&mut packets, state, size, a, b);
    }
    emit_shape_packets(sink, state, packets);
}

struct ShapePacket {
    pipeline: u32,
    stride: u32,
    vertices: Vec<f32>,
}

fn append_packet_vertices(
    packets: &mut Vec<ShapePacket>,
    pipeline: u32,
    stride: u32,
    vertices: &[f32],
) {
    if let Some(packet) = packets.last_mut() {
        if packet.pipeline == pipeline && packet.stride == stride {
            packet.vertices.extend_from_slice(vertices);
            return;
        }
    }
    packets.push(ShapePacket {
        pipeline,
        stride,
        vertices: vertices.to_vec(),
    });
}

fn emit_shape_packets(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    packets: Vec<ShapePacket>,
) {
    for packet in packets {
        emit_packet(
            sink,
            state,
            packet.pipeline,
            packet.stride,
            &packet.vertices,
        );
    }
}

fn append_line(
    packets: &mut Vec<ShapePacket>,
    state: GraphicsState,
    size: (i32, i32),
    a: Point,
    b: Point,
) {
    let dx = b.0 - a.0;
    let dy = b.1 - a.1;
    let len = dx.hypot(dy);
    if len == 0.0 {
        let mut vertices = Vec::with_capacity(36);
        for point in quad(a.0, a.1, a.0 + 1.0, a.1 + 1.0) {
            push_color_vertex(&mut vertices, point, 1.0, state, size);
        }
        append_packet_vertices(packets, PIPELINE_COLOR, 6, &vertices);
        return;
    }
    if state.stroke_style == 1 {
        let mut start = 0.0;
        while start <= len {
            let end = (start + 2.0).min(len);
            append_line_segment(
                packets,
                state,
                size,
                (a.0 + dx * start / len, a.1 + dy * start / len),
                (a.0 + dx * end / len, a.1 + dy * end / len),
                0.5,
            );
            start += 4.0;
        }
    } else {
        append_line_segment(packets, state, size, a, b, 0.5);
    }
}

fn append_line_segment(
    packets: &mut Vec<ShapePacket>,
    state: GraphicsState,
    size: (i32, i32),
    a: Point,
    b: Point,
    half_width: f64,
) {
    if half_width > 0.0 && a.1 == b.1 {
        let x0 = a.0.min(b.0);
        let x1 = a.0.max(b.0) + 1.0;
        let mut vertices = Vec::with_capacity(36);
        for point in [
            (x0, a.1),
            (x1, a.1),
            (x0, a.1 + 1.0),
            (x0, a.1 + 1.0),
            (x1, a.1),
            (x1, a.1 + 1.0),
        ] {
            push_color_vertex(&mut vertices, point, 1.0, state, size);
        }
        append_packet_vertices(packets, PIPELINE_COLOR, 6, &vertices);
        return;
    }
    if half_width > 0.0 && a.0 == b.0 {
        let y0 = a.1.min(b.1);
        let y1 = a.1.max(b.1) + 1.0;
        let mut vertices = Vec::with_capacity(36);
        for point in [
            (a.0, y0),
            (a.0 + 1.0, y0),
            (a.0, y1),
            (a.0, y1),
            (a.0 + 1.0, y0),
            (a.0 + 1.0, y1),
        ] {
            push_color_vertex(&mut vertices, point, 1.0, state, size);
        }
        append_packet_vertices(packets, PIPELINE_COLOR, 6, &vertices);
        return;
    }
    let dx = b.0 - a.0;
    let dy = b.1 - a.1;
    let len = dx.hypot(dy);
    // The fragment shader derives physical-pixel coverage from these logical
    // coordinates. One logical pixel of padding is enough at every raster scale.
    let q = half_width + 0.5;
    let ux = dx / len;
    let uy = dy / len;
    let nx = -uy;
    let ny = ux;
    let points = [
        (a.0 - ux * q + nx * q, a.1 - uy * q + ny * q),
        (b.0 + ux * q + nx * q, b.1 + uy * q + ny * q),
        (a.0 - ux * q - nx * q, a.1 - uy * q - ny * q),
        (a.0 - ux * q - nx * q, a.1 - uy * q - ny * q),
        (b.0 + ux * q + nx * q, b.1 + uy * q + ny * q),
        (b.0 + ux * q - nx * q, b.1 + uy * q - ny * q),
    ];
    let c = color(state.color);
    let mut vertices = Vec::with_capacity(84);
    for p in points {
        let n = ndc(p, size.0, size.1);
        vertices.extend_from_slice(&[
            n[0],
            n[1],
            c[0],
            c[1],
            c[2],
            c[3],
            p.0 as f32,
            p.1 as f32,
            a.0 as f32,
            a.1 as f32,
            dx as f32,
            dy as f32,
            len as f32,
            half_width as f32,
        ]);
    }
    append_packet_vertices(packets, PIPELINE_LINE_AA, 14, &vertices);
}

fn distance_to_line(p: Point, a: Point, b: Point) -> f64 {
    let dx = b.0 - a.0;
    let dy = b.1 - a.1;
    let len = dx.hypot(dy);
    if len == 0.0 {
        return (p.0 - a.0).hypot(p.1 - a.1);
    }
    (dx * (a.1 - p.1) - (a.0 - p.0) * dy).abs() / len
}
fn axis_aligned(a: Point, b: Point) -> bool {
    a.0 == b.0 || a.1 == b.1
}
fn same_point(a: Point, b: Point) -> bool {
    (a.0 - b.0).abs() < 1e-7 && (a.1 - b.1).abs() < 1e-7
}
fn original_edge(a: Point, b: Point, polygon: &[Point]) -> bool {
    (0..polygon.len()).any(|i| {
        let j = (i + 1) % polygon.len();
        (same_point(a, polygon[i]) && same_point(b, polygon[j]))
            || (same_point(b, polygon[i]) && same_point(a, polygon[j]))
    })
}

fn emit_triangles_aa(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    size: (i32, i32),
    triangles: &[[Point; 3]],
    boundaries: Option<&[Vec<Point>]>,
) {
    let c = color(state.color);
    let mut out = Vec::with_capacity(triangles.len() * 27);
    for triangle in triangles {
        let mut h = [
            distance_to_line(triangle[0], triangle[1], triangle[2]),
            distance_to_line(triangle[1], triangle[0], triangle[2]),
            distance_to_line(triangle[2], triangle[0], triangle[1]),
        ];
        let edges = [
            (triangle[1], triangle[2]),
            (triangle[0], triangle[2]),
            (triangle[0], triangle[1]),
        ];
        for edge in 0..3 {
            let internal = boundaries
                .map(|polys| {
                    !polys
                        .iter()
                        .any(|p| original_edge(edges[edge].0, edges[edge].1, p))
                })
                .unwrap_or(false);
            if internal || axis_aligned(edges[edge].0, edges[edge].1) {
                h[edge] = 10_000.0;
            }
        }
        for vertex in 0..3 {
            let p = ndc(triangle[vertex], size.0, size.1);
            let mut bary = [0.0f32; 3];
            for edge in 0..3 {
                bary[edge] = if h[edge] >= 10_000.0 {
                    10_000.0
                } else if edge == vertex {
                    h[edge] as f32
                } else {
                    0.0
                };
            }
            out.extend_from_slice(&[
                p[0], p[1], c[0], c[1], c[2], c[3], bary[0], bary[1], bary[2],
            ]);
        }
    }
    emit_packet(sink, state, PIPELINE_COLOR_AA, 9, &out);
}

fn segment_intersection(a: Point, b: Point, c: Point, d: Point) -> Option<Point> {
    let ab = (b.0 - a.0, b.1 - a.1);
    let cd = (d.0 - c.0, d.1 - c.1);
    let denominator = ab.0 * cd.1 - ab.1 * cd.0;
    if denominator.abs() < 1e-10 {
        return None;
    }
    let t = ((c.0 - a.0) * cd.1 - (c.1 - a.1) * cd.0) / denominator;
    let u = ((c.0 - a.0) * ab.1 - (c.1 - a.1) * ab.0) / denominator;
    (t > 0.0 && t < 1.0 && u > 0.0 && u < 1.0).then_some((a.0 + t * ab.0, a.1 + t * ab.1))
}
fn decompose(polygon: &[Point]) -> Vec<Vec<Point>> {
    if polygon.len() < 4 {
        return vec![polygon.to_vec()];
    };
    let n = polygon.len();
    for i in 0..n {
        let i2 = (i + 1) % n;
        for j in i + 2..n {
            if i == 0 && j == n - 1 {
                continue;
            }
            let j2 = (j + 1) % n;
            if let Some(p) = segment_intersection(polygon[i], polygon[i2], polygon[j], polygon[j2])
            {
                let mut a = vec![p];
                let mut k = i2;
                loop {
                    a.push(polygon[k]);
                    if k == j {
                        break;
                    }
                    k = (k + 1) % n;
                }
                let mut b = vec![p];
                k = j2;
                loop {
                    b.push(polygon[k]);
                    if k == i {
                        break;
                    }
                    k = (k + 1) % n;
                }
                let mut result = decompose(&a);
                result.extend(decompose(&b));
                return result;
            }
        }
    }
    vec![polygon.to_vec()]
}
fn triangulate(polygon: &[Point]) -> Vec<[Point; 3]> {
    let coords: Vec<f64> = polygon.iter().flat_map(|p| [p.0, p.1]).collect();
    earcutr::earcut(&coords, &[], 2)
        .unwrap_or_default()
        .chunks_exact(3)
        .map(|i| [polygon[i[0]], polygon[i[1]], polygon[i[2]]])
        .collect()
}

pub fn emit_fill_polygon(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    size: (i32, i32),
    points: &[Point],
) {
    if points.len() < 3 {
        return;
    }
    let translated: Vec<Point> = points
        .iter()
        .map(|p| {
            (
                p.0 + state.translate_x as f64,
                p.1 + state.translate_y as f64,
            )
        })
        .collect();
    let polygons = decompose(&translated);
    let triangles: Vec<[Point; 3]> = polygons.iter().flat_map(|p| triangulate(p)).collect();
    let interior: Vec<Point> = triangles
        .iter()
        .flat_map(|triangle| triangle.iter().copied())
        .collect();
    emit_color(sink, state, size, &interior);

    // Cover only the original contour with the derivative-aware line shader.
    // Triangulation edges remain opaque, avoiding seams at concave vertices.
    let mut packets = Vec::new();
    for i in 0..translated.len() {
        let next = (i + 1) % translated.len();
        append_line_segment(
            &mut packets,
            state,
            size,
            translated[i],
            translated[next],
            0.0,
        );
    }
    emit_shape_packets(sink, state, packets);
}
pub fn emit_draw_polygon(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    size: (i32, i32),
    points: &[Point],
) {
    if points.len() < 2 {
        return;
    }
    let lines: Vec<(Point, Point)> = (0..points.len())
        .map(|i| (points[i], points[(i + 1) % points.len()]))
        .collect();
    emit_lines(sink, state, size, &lines);
}

fn quad(left: f64, top: f64, right: f64, bottom: f64) -> [Point; 6] {
    [
        (left, top),
        (right, top),
        (left, bottom),
        (left, bottom),
        (right, top),
        (right, bottom),
    ]
}
fn push_shape_prefix(out: &mut Vec<f32>, p: Point, state: GraphicsState, size: (i32, i32)) {
    let n = ndc(p, size.0, size.1);
    let c = color(state.color);
    out.extend_from_slice(&[n[0], n[1], c[0], c[1], c[2], c[3], p.0 as f32, p.1 as f32]);
}

pub fn emit_round_rect(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    size: (i32, i32),
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    aw: i32,
    ah: i32,
    fill: bool,
) {
    if w <= 0 || h <= 0 {
        return;
    }
    let rx = (w.min(aw).max(0) / 2) as f64;
    let ry = (h.min(ah).max(0) / 2) as f64;
    if rx < 1.0 || ry < 1.0 {
        if fill {
            let l = (x + state.translate_x) as f64;
            let t = (y + state.translate_y) as f64;
            emit_color(sink, state, size, &quad(l, t, l + w as f64, t + h as f64));
        } else {
            emit_lines(
                sink,
                state,
                size,
                &[
                    ((x as f64, y as f64), ((x + w) as f64, y as f64)),
                    ((x as f64, (y + h) as f64), ((x + w) as f64, (y + h) as f64)),
                    ((x as f64, y as f64), (x as f64, (y + h) as f64)),
                    (((x + w) as f64, y as f64), ((x + w) as f64, (y + h) as f64)),
                ],
            );
        }
        return;
    }
    let left = (x + state.translate_x) as f64;
    let top = (y + state.translate_y) as f64;
    let right = left + w as f64;
    let bottom = top + h as f64;
    let half = 0.5;
    let fringe = if fill { 0.5 } else { 1.0 };
    let mut out = Vec::new();
    for p in quad(left - fringe, top - fringe, right + fringe, bottom + fringe) {
        push_shape_prefix(&mut out, p, state, size);
        out.extend_from_slice(&[
            left as f32,
            top as f32,
            right as f32,
            bottom as f32,
            rx.max(0.5) as f32,
            ry.max(0.5) as f32,
        ]);
        if fill {
            out.extend_from_slice(&[0.0, 0.0]);
        } else {
            out.extend_from_slice(&[half as f32, 0.0, 0.0, 0.0]);
        }
    }
    let pipeline = if fill {
        PIPELINE_ROUND_RECT_AA
    } else if state.stroke_style == 1 {
        PIPELINE_ROUND_RECT_DOTTED_AA
    } else {
        PIPELINE_ROUND_RECT_STROKE_AA
    };
    emit_packet(sink, state, pipeline, if fill { 16 } else { 18 }, &out);
}

pub fn emit_arc(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    size: (i32, i32),
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    start: i32,
    sweep: i32,
    fill: bool,
) {
    if w <= 0 || h <= 0 || sweep == 0 {
        return;
    }
    let left = (x + state.translate_x) as f64;
    let top = (y + state.translate_y) as f64;
    let right = left + w as f64;
    let bottom = top + h as f64;
    let half = 0.5;
    let fringe = if fill { 0.5 } else { 1.0 };
    let cx = left + w as f64 / 2.0;
    let cy = top + h as f64 / 2.0;
    let rx = (w as f64 / 2.0).max(0.5);
    let ry = (h as f64 / 2.0).max(0.5);
    let sr = -(start as f64).to_radians();
    let sw = -(sweep as f64).to_radians();
    let abs_sw = sw.abs().min(std::f64::consts::TAU);
    if fill && abs_sw >= std::f64::consts::TAU {
        let mut out = Vec::new();
        for p in quad(left - fringe, top - fringe, right + fringe, bottom + fringe) {
            push_shape_prefix(&mut out, p, state, size);
            out.extend_from_slice(&[cx as f32, cy as f32, rx as f32, ry as f32]);
        }
        emit_packet(sink, state, PIPELINE_ELLIPSE_AA, 12, &out);
        return;
    }
    let end = sr + sw;
    let sp = (cx + sr.cos() * rx, cy + sr.sin() * ry);
    let ep = (cx + end.cos() * rx, cy + end.sin() * ry);
    let mut out = Vec::new();
    for p in quad(left - fringe, top - fringe, right + fringe, bottom + fringe) {
        push_shape_prefix(&mut out, p, state, size);
        out.extend_from_slice(&[cx as f32, cy as f32, rx as f32, ry as f32]);
        if fill {
            out.extend_from_slice(&[
                0.0,
                sw as f32,
                abs_sw as f32,
                sp.0 as f32,
                sp.1 as f32,
                ep.0 as f32,
                ep.1 as f32,
            ]);
        } else {
            out.extend_from_slice(&[
                sr as f32,
                sw as f32,
                abs_sw as f32,
                half as f32,
                sp.0 as f32,
                sp.1 as f32,
                ep.0 as f32,
                ep.1 as f32,
            ]);
        }
    }
    let pipeline = if fill {
        PIPELINE_ARC_FILL_AA
    } else if state.stroke_style == 1 {
        PIPELINE_ARC_DOTTED_AA
    } else {
        PIPELINE_ARC_STROKE_AA
    };
    emit_packet(sink, state, pipeline, if fill { 19 } else { 20 }, &out);
}

pub fn emit_triangle(
    sink: &mut impl ShapePacketSink,
    state: GraphicsState,
    size: (i32, i32),
    points: [Point; 3],
) {
    let translated = [
        (
            points[0].0 + state.translate_x as f64,
            points[0].1 + state.translate_y as f64,
        ),
        (
            points[1].0 + state.translate_x as f64,
            points[1].1 + state.translate_y as f64,
        ),
        (
            points[2].0 + state.translate_x as f64,
            points[2].1 + state.translate_y as f64,
        ),
    ];
    emit_triangles_aa(sink, state, size, &[translated], None)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::vm::cmd;
    use std::{cell::RefCell, rc::Rc};

    fn state() -> GraphicsState {
        GraphicsState {
            color: 0x336699,
            clip_w: 240,
            clip_h: 320,
            ..Default::default()
        }
    }

    fn test_vm() -> SharedVm {
        SharedVm::new(Rc::new(RefCell::new(1)), false)
    }

    fn packet_header(stream: &[u8], offset: usize) -> (u32, u32, u32) {
        let read = |at| u32::from_le_bytes(stream[at..at + 4].try_into().unwrap());
        (read(offset), read(offset + 4), read(offset + 8))
    }

    #[test]
    fn diagonal_line_emits_shared_line_vertex_layout() {
        let mut vm = test_vm();
        emit_line(&mut vm, state(), (240, 320), (0.0, 0.0), (10.0, 10.0));
        assert_eq!(vm.command_buffer[0], cmd::LCDUI_DRAW_SHAPE_STREAM);
        assert_eq!(
            packet_header(&vm.command_buffer, 53),
            (PIPELINE_LINE_AA, 14, 6)
        );
        assert_eq!(vm.command_buffer.len(), 1 + 48 + 4 + 12 + 14 * 6 * 4);
    }

    #[test]
    fn shared_vm_coalesces_adjacent_shape_packets() {
        let mut vm = test_vm();
        emit_fill_rect(&mut vm, state(), (240, 320), 0, 0, 8, 8);
        emit_line(&mut vm, state(), (240, 320), (0.0, 0.0), (10.0, 10.0));
        let mut different_vertex_state = state();
        different_vertex_state.color = 0xff00aa;
        different_vertex_state.translate_x = 3;
        emit_line(
            &mut vm,
            different_vertex_state,
            (240, 320),
            (1.0, 1.0),
            (11.0, 11.0),
        );

        assert_eq!(vm.command_buffer[0], cmd::LCDUI_DRAW_SHAPE_STREAM);
        assert_eq!(
            u32::from_le_bytes(vm.command_buffer[49..53].try_into().unwrap()),
            3
        );
        let stream_len = 1 + 48 + 4 + (12 + 6 * 6 * 4) + 2 * (12 + 14 * 6 * 4);
        assert_eq!(vm.command_buffer.len(), stream_len);

        vm.command_buffer.push(cmd::FLUSH);
        vm.command_buffer.extend_from_slice(&0_u32.to_le_bytes());
        emit_line(&mut vm, state(), (240, 320), (2.0, 2.0), (12.0, 12.0));
        let second_stream = stream_len + 5;
        assert_eq!(
            vm.command_buffer[second_stream],
            cmd::LCDUI_DRAW_SHAPE_STREAM
        );
    }

    #[test]
    fn dotted_line_combines_all_dashes_into_one_packet() {
        let mut dotted = state();
        dotted.stroke_style = 1;
        let mut vm = test_vm();
        emit_line(&mut vm, dotted, (240, 320), (0.0, 0.0), (10.0, 10.0));
        assert_eq!(
            packet_header(&vm.command_buffer, 53),
            (PIPELINE_LINE_AA, 14, 24)
        );
        assert_eq!(vm.command_buffer.len(), 1 + 48 + 4 + 12 + 14 * 24 * 4);
    }

    #[test]
    fn dotted_axis_aligned_polygon_combines_all_edges_into_one_packet() {
        let mut dotted = state();
        dotted.stroke_style = 1;
        let mut vm = test_vm();
        emit_draw_polygon(
            &mut vm,
            dotted,
            (240, 320),
            &[(0.0, 0.0), (10.0, 0.0), (10.0, 10.0), (0.0, 10.0)],
        );
        assert_eq!(
            packet_header(&vm.command_buffer, 53),
            (PIPELINE_COLOR, 6, 72)
        );
        assert_eq!(vm.command_buffer.len(), 1 + 48 + 4 + 12 + 6 * 72 * 4);
    }

    #[test]
    fn concave_polygon_emits_interior_and_boundary_packets() {
        let mut vm = test_vm();
        emit_fill_polygon(
            &mut vm,
            state(),
            (240, 320),
            &[
                (0.0, 0.0),
                (20.0, 0.0),
                (10.0, 10.0),
                (20.0, 20.0),
                (0.0, 20.0),
            ],
        );
        assert_eq!(vm.command_buffer[0], cmd::LCDUI_DRAW_SHAPE_STREAM);
        assert_eq!(
            u32::from_le_bytes(vm.command_buffer[49..53].try_into().unwrap()),
            2
        );
        let (_, stride, count) = packet_header(&vm.command_buffer, 53);
        assert_eq!(stride, 6);
        let second = 53 + 12 + (stride * count * 4) as usize;
        assert_eq!(
            packet_header(&vm.command_buffer, second).0,
            PIPELINE_LINE_AA
        );
    }

    #[test]
    fn round_rect_and_arc_match_shader_strides() {
        let mut vm = test_vm();
        emit_round_rect(&mut vm, state(), (240, 320), 1, 2, 30, 20, 8, 8, true);
        assert_eq!(
            packet_header(&vm.command_buffer, 53),
            (PIPELINE_ROUND_RECT_AA, 16, 6)
        );
        vm.command_buffer.clear();
        emit_arc(&mut vm, state(), (240, 320), 1, 2, 30, 20, 0, 180, false);
        assert_eq!(
            packet_header(&vm.command_buffer, 53),
            (PIPELINE_ARC_STROKE_AA, 20, 6)
        );
    }
}
