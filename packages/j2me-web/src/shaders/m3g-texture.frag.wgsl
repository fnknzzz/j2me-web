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

fn apply_texture_env(
  base: vec4<f32>,
  texel: vec4<f32>,
  mode: u32,
  envColor: vec4<f32>,
) -> vec4<f32> {
  if (mode == 0x2100u) {
    return vec4<f32>(base.rgb * texel.rgb, base.a * texel.a);
  }
  if (mode == 0x2101u) {
    return vec4<f32>(mix(base.rgb, texel.rgb, texel.a), base.a);
  }
  if (mode == 0x0be2u) {
    return vec4<f32>(mix(base.rgb, envColor.rgb, texel.rgb), base.a * texel.a);
  }
  if (mode == 0x0104u) {
    return vec4<f32>(min(base.rgb + texel.rgb, vec3<f32>(1.0)), base.a * texel.a);
  }
  if (mode == 0x1e01u) {
    return texel;
  }
  return texel;
}

fn positive_mod(value: i32, divisor: i32) -> i32 {
  let modded = value % divisor;
  if (modded < 0) {
    return modded + divisor;
  }
  return modded;
}

fn texture_index(value: i32, size: u32, wrap: u32) -> u32 {
  let max_index = i32(size) - 1;
  if (wrap == 0x2901u) {
    return u32(positive_mod(value, i32(size)));
  }
  return u32(clamp(value, 0, max_index));
}

fn sample_nearest(
  source: texture_2d<f32>,
  coord: vec2<f32>,
  sampling: vec4<u32>,
) -> vec4<f32> {
  let size = textureDimensions(source);
  let x = texture_index(i32(floor(coord.x * f32(size.x))), size.x, sampling.x);
  let y = texture_index(i32(floor(coord.y * f32(size.y))), size.y, sampling.y);
  return textureLoad(source, vec2<i32>(i32(x), i32(y)), 0);
}

fn sample_linear(
  source: texture_2d<f32>,
  coord: vec2<f32>,
  sampling: vec4<u32>,
) -> vec4<f32> {
  let size = textureDimensions(source);
  let x = coord.x * f32(size.x) - 0.5;
  let y = coord.y * f32(size.y) - 0.5;
  let x0Raw = i32(floor(x));
  let y0Raw = i32(floor(y));
  let x0 = texture_index(x0Raw, size.x, sampling.x);
  let y0 = texture_index(y0Raw, size.y, sampling.y);
  let x1 = texture_index(x0Raw + 1, size.x, sampling.x);
  let y1 = texture_index(y0Raw + 1, size.y, sampling.y);
  let fx = x - floor(x);
  let fy = y - floor(y);
  let top = mix(
    textureLoad(source, vec2<i32>(i32(x0), i32(y0)), 0),
    textureLoad(source, vec2<i32>(i32(x1), i32(y0)), 0),
    fx,
  );
  let bottom = mix(
    textureLoad(source, vec2<i32>(i32(x0), i32(y1)), 0),
    textureLoad(source, vec2<i32>(i32(x1), i32(y1)), 0),
    fx,
  );
  return mix(top, bottom, fy);
}

fn uses_linear_filter(sampling: vec4<u32>) -> bool {
  return sampling.z == 0x2601u
    || sampling.z == 0x2701u
    || sampling.z == 0x2703u
    || sampling.w == 0x2601u;
}

fn sample_texture(
  source: texture_2d<f32>,
  coord: vec2<f32>,
  sampling: vec4<u32>,
) -> vec4<f32> {
  if (uses_linear_filter(sampling)) {
    return sample_linear(source, coord, sampling);
  }
  return sample_nearest(source, coord, sampling);
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
  var color = apply_texture_env(
    input.color,
    sample_texture(tex, input.uv, uniforms.textureSampling0),
    uniforms.envMode,
    uniforms.envColor,
  );
  if (uniforms.hasTex1 != 0u) {
    color = apply_texture_env(
      color,
      sample_texture(tex1, input.uv1, uniforms.textureSampling1),
      uniforms.envMode1,
      uniforms.envColor1,
    );
  }
  if (uniforms.alphaRef > 0.0 && color.a < uniforms.alphaRef) {
    discard;
  }
  // Only surface the real alpha to the blend unit when the pipeline is set
  // up for it; otherwise pin alpha to 1.0 so the shared surface target stays
  // opaque (LCDUI compositing later would treat sub-1 alpha as transparent).
  if (uniforms.alphaBlend == 0u) {
    color.a = 1.0;
  }
  return apply_fog(color, input.viewZ);
}
