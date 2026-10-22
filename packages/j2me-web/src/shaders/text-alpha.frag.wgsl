@group(0) @binding(0) var alphaMask: texture_2d<f32>;
@group(0) @binding(1) var maskSampler: sampler;

@fragment
fn fs_main(
  @location(0) uv: vec2f,
  @location(1) color: vec4f,
) -> @location(0) vec4f {
  let alpha = textureSample(alphaMask, maskSampler, uv).a;
  return vec4f(color.rgb, color.a * alpha);
}
