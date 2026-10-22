diagnostic(off, derivative_uniformity);

fn positive_mod(value: f32, divisor: f32) -> f32 {
  return value - floor(value / divisor) * divisor;
}

fn ellipse_signed_distance(p: vec2f, center: vec2f, radiiValue: vec2f) -> f32 {
  let radii = max(radiiValue, vec2f(0.0001));
  let normalized = (p - center) / radii;
  return (length(normalized) - 1.0) / max(length(normalized / radii), 0.0001);
}

fn rect_signed_distance(p: vec2f, bounds: vec4f) -> f32 {
  let outside = vec2f(
    max(max(bounds.x - p.x, p.x - bounds.z), 0.0),
    max(max(bounds.y - p.y, p.y - bounds.w), 0.0),
  );
  let outsideDistance = length(outside);
  let insideDistance = min(
    min(p.x - bounds.x, bounds.z - p.x),
    min(p.y - bounds.y, bounds.w - p.y),
  );
  return select(-insideDistance, outsideDistance, outsideDistance > 0.0);
}

fn round_rect_signed_distance(p: vec2f, bounds: vec4f, radiiValue: vec2f) -> f32 {
  let radii = max(radiiValue, vec2f(0.0001));
  let innerLeft = bounds.x + radii.x;
  let innerRight = bounds.z - radii.x;
  let innerTop = bounds.y + radii.y;
  let innerBottom = bounds.w - radii.y;
  let middleX = (bounds.x + bounds.z) * 0.5;
  let middleY = (bounds.y + bounds.w) * 0.5;
  var distance: f32;

  if (innerLeft >= innerRight && p.y < innerTop) {
    distance = ellipse_signed_distance(p, vec2f(middleX, innerTop), radii);
  } else if (innerLeft >= innerRight && p.y > innerBottom) {
    distance = ellipse_signed_distance(p, vec2f(middleX, innerBottom), radii);
  } else if (innerTop >= innerBottom && p.x < innerLeft) {
    distance = ellipse_signed_distance(p, vec2f(innerLeft, middleY), radii);
  } else if (innerTop >= innerBottom && p.x > innerRight) {
    distance = ellipse_signed_distance(p, vec2f(innerRight, middleY), radii);
  } else if (p.x < innerLeft && p.y < innerTop) {
    distance = ellipse_signed_distance(p, vec2f(innerLeft, innerTop), radii);
  } else if (p.x > innerRight && p.y < innerTop) {
    distance = ellipse_signed_distance(p, vec2f(innerRight, innerTop), radii);
  } else if (p.x < innerLeft && p.y > innerBottom) {
    distance = ellipse_signed_distance(p, vec2f(innerLeft, innerBottom), radii);
  } else if (p.x > innerRight && p.y > innerBottom) {
    distance = ellipse_signed_distance(p, vec2f(innerRight, innerBottom), radii);
  } else {
    distance = rect_signed_distance(p, bounds);
  }
  return distance;
}

fn line_coverage(p: vec2f, params0: vec4f, params1: vec4f) -> f32 {
  let lineStart = params0.xy;
  let lineDelta = params0.zw;
  let lineLength = max(params1.x, 0.0001);
  let halfWidth = params1.y;
  let lineDir = lineDelta / lineLength;
  let along = dot(p - lineStart, lineDir);
  let distanceToLine = distance(p, lineStart + lineDir * along);
  let distancePixel = max(fwidth(distanceToLine), 0.0001);
  let alongPixel = max(fwidth(along), 0.0001);
  let strokeAlpha = 1.0 - smoothstep(halfWidth - distancePixel * 0.5, halfWidth + distancePixel * 0.5, distanceToLine);
  let startAlpha = smoothstep(-alongPixel * 0.5, alongPixel * 0.5, along);
  let endAlpha = 1.0 - smoothstep(lineLength - alongPixel * 0.5, lineLength + alongPixel * 0.5, along);
  return strokeAlpha * startAlpha * endAlpha;
}

fn round_rect_coverage(p: vec2f, params0: vec4f, params1: vec4f) -> f32 {
  let distance = abs(round_rect_signed_distance(p, params0, params1.xy));
  let pixelWidth = max(fwidth(distance), 0.0001);
  return 1.0 - smoothstep(params1.z - pixelWidth * 0.5, params1.z + pixelWidth * 0.5, distance);
}

fn arc_coverage(p: vec2f, params0: vec4f, params1: vec4f, params2: vec4f) -> f32 {
  let center = params0.xy;
  let radii = max(params0.zw, vec2f(0.0001));
  let normalized = (p - center) / radii;
  let ellipseDistance = abs((length(normalized) - 1.0) / max(length(normalized / radii), 0.0001));
  let ellipsePixel = max(fwidth(ellipseDistance), 0.0001);
  let halfWidth = params1.w;
  let strokeAlpha = 1.0 - smoothstep(halfWidth - ellipsePixel * 0.5, halfWidth + ellipsePixel * 0.5, ellipseDistance);
  let tau = 6.283185307179586;
  let theta = atan2(normalized.y, normalized.x);
  var progress = theta - params1.x;
  if (params1.y < 0.0) {
    progress = params1.x - theta;
  }
  progress = positive_mod(progress, tau);
  let isFullArc = params1.z >= tau - 0.0001;
  let insideArc = isFullArc || progress <= params1.z;
  let capDistance = min(distance(p, params2.xy), distance(p, params2.zw));
  let capPixel = max(fwidth(capDistance), 0.0001);
  let capAlpha = select(
    1.0 - smoothstep(halfWidth - capPixel * 0.5, halfWidth + capPixel * 0.5, capDistance),
    0.0,
    isFullArc,
  );
  return select(capAlpha, max(strokeAlpha, capAlpha), insideArc);
}

@fragment
fn fs_main(
  @location(0) color: vec4f,
  @location(1) physicalPosition: vec2f,
  @location(2) params0: vec4f,
  @location(3) params1: vec4f,
  @location(4) params2: vec4f,
  @location(5) @interpolate(flat) kind: u32,
) -> @location(0) vec4f {
  if (kind == 2u) {
    return vec4f(color.rgb, color.a * line_coverage(physicalPosition, params0, params1));
  }
  if (kind == 5u) {
    return vec4f(color.rgb, color.a * round_rect_coverage(physicalPosition, params0, params1));
  }
  if (kind == 8u) {
    return vec4f(color.rgb, color.a * arc_coverage(physicalPosition, params0, params1, params2));
  }
  return color;
}

