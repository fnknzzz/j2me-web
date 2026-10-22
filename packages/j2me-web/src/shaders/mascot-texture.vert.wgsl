struct VertexOut {
  @builtin(position) position: vec4f,
  @location(0) uv: vec2f,
  @location(1) brightness: f32,
  @location(2) sphereUv: vec2f,
  @location(3) sphereEnabled: f32,
};

struct Lighting {
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

@group(1) @binding(0) var<uniform> lighting: Lighting;

@vertex
fn vs_main(
  @location(0) position: vec4f,
  @location(1) uv: vec2f,
  @location(2) normal: vec3f,
  @location(3) materialLighting: f32,
  @location(4) materialReflection: f32,
) -> VertexOut {
  var out: VertexOut;
  out.position = position;
  out.uv = uv;
  let transformedNormal = vec3f(
    dot(lighting.normalMatrix0.xyz, normal),
    dot(lighting.normalMatrix1.xyz, normal),
    dot(lighting.normalMatrix2.xyz, normal),
  );
  out.sphereUv = (normalize(transformedNormal).xy + vec2f(1.0)) * 0.5;
  out.sphereEnabled = materialReflection;
  if (lighting.light.w < 0.0 || materialLighting < 0.5) {
    out.brightness = 1.0;
  } else {
    let lambert = max(
      dot(normalize(transformedNormal), normalize(-lighting.light.xyz)),
      0.0,
    );
    let ambient = clamp(lighting.light.w / 4096.0, 0.0, 1.0);
    let directional = clamp(lighting.directional / 4096.0, 0.0, 4.0);
    out.brightness = min(ambient + directional * lambert, 1.0);
    if (lighting.toonThreshold >= 0.0) {
      let threshold = lighting.toonThreshold / 255.0;
      out.brightness = select(
        lighting.toonLow / 255.0,
        lighting.toonHigh / 255.0,
        out.brightness >= threshold,
      );
    }
  }
  return out;
}
