@fragment
fn fs_main(
  @location(0) color: vec4f,
  @location(1) physicalPosition: vec2f,
  @location(2) ellipseCenter: vec2f,
  @location(3) ellipseRadii: vec2f,
) -> @location(0) vec4f {
  let radii = max(ellipseRadii, vec2f(0.0001, 0.0001));
  let relative = physicalPosition - ellipseCenter;
  let normalized = relative / radii;
  let normalizedLength = length(normalized);
  let gradient = max(length(normalized / radii), 0.0001);
  let signedDistance = (normalizedLength - 1.0) / gradient;
  let pixelWidth = max(fwidth(signedDistance), 0.0001);
  let alpha = 1.0 - smoothstep(-pixelWidth * 0.5, pixelWidth * 0.5, signedDistance);
  return vec4f(color.rgb, color.a * alpha);
}
