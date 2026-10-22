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

fn round_rect_signed_distance(
  physicalPosition: vec2f,
  rectBounds: vec4f,
  cornerRadii: vec2f,
) -> f32 {
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
  return signedDistance;
}

fn stroke_coverage(physicalPosition: vec2f, rectBounds: vec4f, cornerRadii: vec2f, halfWidth: f32) -> f32 {
  let signedDistance = round_rect_signed_distance(physicalPosition, rectBounds, cornerRadii);
  let distance = abs(signedDistance);
  let pixelWidth = max(fwidth(distance), 0.0001);
  return 1.0 - smoothstep(halfWidth - pixelWidth * 0.5, halfWidth + pixelWidth * 0.5, distance);
}

fn positive_mod(value: f32, divisor: f32) -> f32 {
  return value - floor(value / divisor) * divisor;
}

fn rounded_rect_distance_along(physicalPosition: vec2f, rectBounds: vec4f, cornerRadii: vec2f) -> f32 {
  let left = rectBounds.x;
  let top = rectBounds.y;
  let right = rectBounds.z;
  let bottom = rectBounds.w;
  let radii = max(cornerRadii, vec2f(0.0001, 0.0001));
  let innerLeft = left + radii.x;
  let innerRight = right - radii.x;
  let innerTop = top + radii.y;
  let innerBottom = bottom - radii.y;
  let topLength = max(0.0, innerRight - innerLeft);
  let rightLength = max(0.0, innerBottom - innerTop);
  let bottomLength = topLength;
  let leftLength = rightLength;
  let cornerLength = 1.5707963267948966 * (radii.x + radii.y) * 0.5;

  if (physicalPosition.y <= innerTop && physicalPosition.x >= innerLeft && physicalPosition.x <= innerRight) {
    return physicalPosition.x - innerLeft;
  }
  if (physicalPosition.x > innerRight && physicalPosition.y < innerTop) {
    let theta = atan2((physicalPosition.y - innerTop) / radii.y, (physicalPosition.x - innerRight) / radii.x);
    return topLength + (theta + 1.5707963267948966) * ((radii.x + radii.y) * 0.5);
  }
  if (physicalPosition.x >= innerRight && physicalPosition.y >= innerTop && physicalPosition.y <= innerBottom) {
    return topLength + cornerLength + physicalPosition.y - innerTop;
  }
  if (physicalPosition.x > innerRight && physicalPosition.y > innerBottom) {
    let theta = atan2((physicalPosition.y - innerBottom) / radii.y, (physicalPosition.x - innerRight) / radii.x);
    return topLength + cornerLength + rightLength + theta * ((radii.x + radii.y) * 0.5);
  }
  if (physicalPosition.y >= innerBottom && physicalPosition.x >= innerLeft && physicalPosition.x <= innerRight) {
    return topLength + cornerLength + rightLength + cornerLength + (innerRight - physicalPosition.x);
  }
  if (physicalPosition.x < innerLeft && physicalPosition.y > innerBottom) {
    let theta = atan2((physicalPosition.y - innerBottom) / radii.y, (physicalPosition.x - innerLeft) / radii.x);
    return topLength + cornerLength + rightLength + cornerLength + bottomLength + (theta - 1.5707963267948966) * ((radii.x + radii.y) * 0.5);
  }
  if (physicalPosition.x <= innerLeft && physicalPosition.y >= innerTop && physicalPosition.y <= innerBottom) {
    return topLength + cornerLength + rightLength + cornerLength + bottomLength + cornerLength + (innerBottom - physicalPosition.y);
  }

  let theta = atan2((physicalPosition.y - innerTop) / radii.y, (physicalPosition.x - innerLeft) / radii.x);
  return topLength + cornerLength + rightLength + cornerLength + bottomLength + cornerLength + leftLength + (theta - 3.141592653589793) * ((radii.x + radii.y) * 0.5);
}

@fragment
fn fs_main(
  @location(0) color: vec4f,
  @location(1) physicalPosition: vec2f,
  @location(2) rectBounds: vec4f,
  @location(3) cornerRadii: vec2f,
  @location(4) strokeParams: vec2f,
) -> @location(0) vec4f {
  let halfWidth = strokeParams.x;
  let coverage = stroke_coverage(physicalPosition, rectBounds, cornerRadii, halfWidth);
  let scale = max(1.0, halfWidth * 2.0);
  let distanceAlong = rounded_rect_distance_along(physicalPosition, rectBounds, cornerRadii);
  let dashPhase = positive_mod(distanceAlong, 4.0 * scale);
  let dotLength = 2.0 * scale;
  let dashPixel = max(fwidth(distanceAlong), 0.0001);
  let dashAlpha = 1.0 - smoothstep(dotLength - dashPixel * 0.5, dotLength + dashPixel * 0.5, dashPhase);
  return vec4f(color.rgb, color.a * coverage * dashAlpha);
}
