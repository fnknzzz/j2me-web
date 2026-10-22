fn positive_mod(value: f32, divisor: f32) -> f32 {
  return value - floor(value / divisor) * divisor;
}

fn distance_to_segment(p: vec2f, a: vec2f, b: vec2f) -> f32 {
  let ab = b - a;
  let denom = max(dot(ab, ab), 0.0001);
  let t = clamp(dot(p - a, ab) / denom, 0.0, 1.0);
  return distance(p, a + ab * t);
}

@fragment
fn fs_main(
  @location(0) color: vec4f,
  @location(1) physicalPosition: vec2f,
  @location(2) ellipseCenter: vec2f,
  @location(3) ellipseRadii: vec2f,
  @location(4) arcParams: vec3f,
  @location(5) startPoint: vec2f,
  @location(6) endPoint: vec2f,
) -> @location(0) vec4f {
  let radii = max(ellipseRadii, vec2f(0.0001, 0.0001));
  let relative = physicalPosition - ellipseCenter;
  let normalized = relative / radii;
  let normalizedLength = length(normalized);
  let gradient = max(length(normalized / radii), 0.0001);
  let ellipseSignedDistance = (normalizedLength - 1.0) / gradient;
  let ellipsePixel = max(fwidth(ellipseSignedDistance), 0.0001);
  let ellipseAlpha = 1.0 - smoothstep(-ellipsePixel * 0.5, ellipsePixel * 0.5, ellipseSignedDistance);

  let tau = 6.283185307179586;
  let theta = positive_mod(atan2(normalized.y, normalized.x), tau);
  let startTheta = positive_mod(atan2((startPoint.y - ellipseCenter.y) / radii.y, (startPoint.x - ellipseCenter.x) / radii.x), tau);
  var progress: f32;
  if (arcParams.y < 0.0) {
    progress = positive_mod(startTheta - theta, tau);
  } else {
    progress = positive_mod(theta - startTheta, tau);
  }
  let insideSector = progress <= arcParams.z || length(relative) <= 0.5;
  let radialDistance = min(
    distance_to_segment(physicalPosition, ellipseCenter, startPoint),
    distance_to_segment(physicalPosition, ellipseCenter, endPoint),
  );
  let sectorSignedDistance = select(radialDistance, -radialDistance, insideSector);
  let sectorPixel = max(fwidth(sectorSignedDistance), 0.0001);
  let sectorAlpha = 1.0 - smoothstep(-sectorPixel * 0.5, sectorPixel * 0.5, sectorSignedDistance);

  return vec4f(color.rgb, color.a * min(ellipseAlpha, sectorAlpha));
}
