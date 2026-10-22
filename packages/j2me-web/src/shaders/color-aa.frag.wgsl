@fragment
fn fs_main(@location(0) color: vec4f, @location(1) bary: vec3f) -> @location(0) vec4f {
  // bary contains logical distances from each edge. Derivatives convert the
  // coverage width to framebuffer pixels without a host-provided scale.
  let d = min(bary.x, min(bary.y, bary.z));
  let pixelWidth = max(fwidth(d), 0.0001);
  let alpha = smoothstep(0.0, 1.5 * pixelWidth, d);
  return vec4f(color.rgb, color.a * alpha);
}
