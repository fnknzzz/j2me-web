fn apply_fog(color: vec4<f32>, viewZ: f32) -> vec4<f32> {
  if (uniforms.hasFog == 0u) {
    return color;
  }
  var fogFactor: f32;
  if (uniforms.fogMode == 0x2601u) {
    let denom = uniforms.fogParams.z - uniforms.fogParams.y;
    if (denom <= 0.0) {
      fogFactor = 1.0;
    } else {
      fogFactor = clamp((uniforms.fogParams.z - viewZ) / denom, 0.0, 1.0);
    }
  } else {
    fogFactor = clamp(exp(-uniforms.fogParams.x * viewZ), 0.0, 1.0);
  }
  return vec4<f32>(mix(uniforms.fogColor.rgb, color.rgb, fogFactor), color.a);
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
  if (uniforms.alphaRef > 0.0 && input.color.a < uniforms.alphaRef) {
    discard;
  }
  return apply_fog(input.color, input.viewZ);
}
