@group(0) @binding(0) var sphereImage: texture_2d<f32>;

@fragment
fn fs_main(
  @location(0) color: vec3f,
  @location(1) brightness: f32,
  @location(2) sphereUv: vec2f,
  @location(3) sphereEnabled: f32,
) -> @location(0) vec4f {
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
  return vec4f(color * brightness + reflected, 1.0);
}
