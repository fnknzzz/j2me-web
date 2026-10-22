@fragment
fn fs_main(
  @location(0) color: vec4f,
  @location(1) physicalPosition: vec2f,
  @location(2) lineStart: vec2f,
  @location(3) lineDelta: vec2f,
  @location(4) lineParams: vec2f,
) -> @location(0) vec4f {
  let lineLength = max(lineParams.x, 0.0001);
  let halfWidth = lineParams.y;
  let lineDir = lineDelta / lineLength;
  let rel = physicalPosition - lineStart;
  let along = dot(rel, lineDir);
  let closest = lineStart + lineDir * along;
  let distanceToLine = distance(physicalPosition, closest);
  let distancePixel = max(fwidth(distanceToLine), 0.0001);
  let alongPixel = max(fwidth(along), 0.0001);
  let strokeAlpha = 1.0 - smoothstep(halfWidth - distancePixel * 0.5, halfWidth + distancePixel * 0.5, distanceToLine);
  let startAlpha = smoothstep(-alongPixel * 0.5, alongPixel * 0.5, along);
  let endAlpha = 1.0 - smoothstep(lineLength - alongPixel * 0.5, lineLength + alongPixel * 0.5, along);
  let alpha = strokeAlpha * startAlpha * endAlpha;
  return vec4f(color.rgb, color.a * alpha);
}
