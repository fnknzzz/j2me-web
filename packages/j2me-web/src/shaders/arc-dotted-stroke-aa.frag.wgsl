fn positive_mod(value: f32, divisor: f32) -> f32 {
  return value - floor(value / divisor) * divisor;
}

@fragment
fn fs_main(
  @location(0) color: vec4f,
  @location(1) physicalPosition: vec2f,
  @location(2) ellipseCenter: vec2f,
  @location(3) ellipseRadii: vec2f,
  @location(4) arcParams: vec4f,
  @location(5) startPoint: vec2f,
  @location(6) endPoint: vec2f,
) -> @location(0) vec4f {
  let radii = max(ellipseRadii, vec2f(0.0001, 0.0001));
  let relative = physicalPosition - ellipseCenter;
  let normalized = relative / radii;
  let normalizedLength = length(normalized);
  let gradient = max(length(normalized / radii), 0.0001);
  let ellipseSignedDistance = (normalizedLength - 1.0) / gradient;
  let halfWidth = arcParams.w;
  let ellipseDistance = abs(ellipseSignedDistance);
  let ellipsePixel = max(fwidth(ellipseDistance), 0.0001);
  let strokeAlpha = 1.0 - smoothstep(halfWidth - ellipsePixel * 0.5, halfWidth + ellipsePixel * 0.5, ellipseDistance);

  let tau = 6.283185307179586;
  let theta = atan2(normalized.y, normalized.x);
  var progress = theta - arcParams.x;
  if (arcParams.y < 0.0) {
    progress = arcParams.x - theta;
  }
  progress = positive_mod(progress, tau);

  let isFullArc = arcParams.z >= tau - 0.0001;
  let insideArc = isFullArc || progress <= arcParams.z;
  let capDistance = min(distance(physicalPosition, startPoint), distance(physicalPosition, endPoint));
  let capPixel = max(fwidth(capDistance), 0.0001);
  let capAlpha = select(1.0 - smoothstep(halfWidth - capPixel * 0.5, halfWidth + capPixel * 0.5, capDistance), 0.0, isFullArc);

  let averageRadius = (radii.x + radii.y) * 0.5;
  let dashDistance = progress * averageRadius;
  let scale = max(1.0, halfWidth * 2.0);
  let dotLength = 2.0 * scale;
  let dashPhase = positive_mod(dashDistance, 4.0 * scale);
  let dashPixel = max(fwidth(dashDistance), 0.0001);
  let dashAlpha = 1.0 - smoothstep(dotLength - dashPixel * 0.5, dotLength + dashPixel * 0.5, dashPhase);

  var alpha = capAlpha;
  if (insideArc) {
    alpha = max(strokeAlpha * dashAlpha, capAlpha);
  }
  return vec4f(color.rgb, color.a * alpha);
}
