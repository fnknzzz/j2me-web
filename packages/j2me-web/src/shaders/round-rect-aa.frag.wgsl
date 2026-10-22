fn ellipse_signed_distance(p: vec2f, center: vec2f, radii: vec2f) -> f32 {
  let relative = p - center;
  let normalized = relative / radii;
  let normalizedLength = length(normalized);
  let gradient = max(length(normalized / radii), 0.0001);
  return (normalizedLength - 1.0) / gradient;
}

fn rect_signed_distance(p: vec2f, bounds: vec4f) -> f32 {
  let left = bounds.x;
  let top = bounds.y;
  let right = bounds.z;
  let bottom = bounds.w;
  let outside = vec2f(
    max(max(left - p.x, p.x - right), 0.0),
    max(max(top - p.y, p.y - bottom), 0.0),
  );
  let outsideDistance = length(outside);
  let insideDistance = min(min(p.x - left, right - p.x), min(p.y - top, bottom - p.y));
  return select(-insideDistance, outsideDistance, outsideDistance > 0.0);
}

@fragment
fn fs_main(
  @location(0) color: vec4f,
  @location(1) physicalPosition: vec2f,
  @location(2) rectBounds: vec4f,
  @location(3) cornerRadii: vec2f,
) -> @location(0) vec4f {
  let left = rectBounds.x;
  let top = rectBounds.y;
  let right = rectBounds.z;
  let bottom = rectBounds.w;
  let radii = max(cornerRadii, vec2f(0.0001, 0.0001));
  let innerLeft = left + radii.x;
  let innerRight = right - radii.x;
  let innerTop = top + radii.y;
  let innerBottom = bottom - radii.y;
  let middleX = (left + right) * 0.5;
  let middleY = (top + bottom) * 0.5;
  var signedDistance: f32;

  if (innerLeft >= innerRight && physicalPosition.y < innerTop) {
    signedDistance = ellipse_signed_distance(physicalPosition, vec2f(middleX, innerTop), radii);
  } else if (innerLeft >= innerRight && physicalPosition.y > innerBottom) {
    signedDistance = ellipse_signed_distance(physicalPosition, vec2f(middleX, innerBottom), radii);
  } else if (innerTop >= innerBottom && physicalPosition.x < innerLeft) {
    signedDistance = ellipse_signed_distance(physicalPosition, vec2f(innerLeft, middleY), radii);
  } else if (innerTop >= innerBottom && physicalPosition.x > innerRight) {
    signedDistance = ellipse_signed_distance(physicalPosition, vec2f(innerRight, middleY), radii);
  } else if (physicalPosition.x < innerLeft && physicalPosition.y < innerTop) {
    signedDistance = ellipse_signed_distance(physicalPosition, vec2f(innerLeft, innerTop), radii);
  } else if (physicalPosition.x > innerRight && physicalPosition.y < innerTop) {
    signedDistance = ellipse_signed_distance(physicalPosition, vec2f(innerRight, innerTop), radii);
  } else if (physicalPosition.x < innerLeft && physicalPosition.y > innerBottom) {
    signedDistance = ellipse_signed_distance(physicalPosition, vec2f(innerLeft, innerBottom), radii);
  } else if (physicalPosition.x > innerRight && physicalPosition.y > innerBottom) {
    signedDistance = ellipse_signed_distance(physicalPosition, vec2f(innerRight, innerBottom), radii);
  } else {
    signedDistance = rect_signed_distance(physicalPosition, rectBounds);
  }

  let pixelWidth = max(fwidth(signedDistance), 0.0001);
  let alpha = 1.0 - smoothstep(-pixelWidth * 0.5, pixelWidth * 0.5, signedDistance);
  return vec4f(color.rgb, color.a * alpha);
}
