struct FragmentLighting {
  normalMatrix0: vec4f,
  normalMatrix1: vec4f,
  normalMatrix2: vec4f,
  light: vec4f,
  directional: f32,
  toonThreshold: f32,
  toonHigh: f32,
  toonLow: f32,
  colorKey: vec4f,
};

@group(0) @binding(0) var textureImage: texture_2d<f32>;
@group(0) @binding(1) var sphereImage: texture_2d<f32>;
@group(1) @binding(0) var<uniform> fragmentLighting: FragmentLighting;

@fragment
fn fs_main(
  @location(0) uv: vec2f,
  @location(1) brightness: f32,
  @location(2) sphereUv: vec2f,
  @location(3) sphereEnabled: f32,
) -> @location(0) vec4f {
  let dimensions = vec2<i32>(textureDimensions(textureImage));
  let texel = clamp(
    vec2<i32>(floor(uv)),
    vec2<i32>(0),
    dimensions - vec2<i32>(1),
  );
  let color = textureLoad(textureImage, texel, 0);
  if (
    fragmentLighting.colorKey.w > 0.5 &&
    all(abs(color.rgb - fragmentLighting.colorKey.xyz) < vec3f(0.5 / 255.0))
  ) {
    discard;
  }
  var reflected = vec3f(0.0);
  if (sphereEnabled > 0.5) {
    let sphereDimensions = vec2<i32>(textureDimensions(sphereImage));
    let sphereTexel = clamp(
      vec2<i32>(floor(sphereUv * vec2f(64.0))),
      vec2<i32>(0),
      sphereDimensions - vec2<i32>(1),
    );
    reflected = textureLoad(sphereImage, sphereTexel, 0).rgb;
  }
  return vec4f(color.rgb * brightness + reflected, color.a);
}
