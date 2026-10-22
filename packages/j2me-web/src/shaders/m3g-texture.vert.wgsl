struct Light {
  state: vec4<f32>,
  ambient: vec4<f32>,
  diffuse: vec4<f32>,
  specular: vec4<f32>,
  position: vec4<f32>,
  spotDirection: vec4<f32>,
  attenuation: vec4<f32>,
}

struct Uniforms {
  mvp: mat4x4<f32>,
  modelView: mat4x4<f32>,
  textureMatrix0: mat4x4<f32>,
  textureMatrix1: mat4x4<f32>,
  viewport: vec4<f32>,
  envColor: vec4<f32>,
  envColor1: vec4<f32>,
  envMode: u32,
  envMode1: u32,
  alphaBlend: u32,
  hasTex1: u32,
  alphaRef: f32,
  fogMode: u32,
  hasFog: u32,
  hasLighting: u32,
  fogParams: vec4<f32>,
  fogColor: vec4<f32>,
  lightingFlags: vec4<u32>,
  lightModelAmbient: vec4<f32>,
  materialAmbient: vec4<f32>,
  materialDiffuse: vec4<f32>,
  materialEmission: vec4<f32>,
  materialSpecular: vec4<f32>,
  materialParams: vec4<f32>,
  lights: array<Light, 8>,
  textureSampling0: vec4<u32>,
  textureSampling1: vec4<u32>,
}

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(0) var texSampler: sampler;
@group(1) @binding(1) var tex: texture_2d<f32>;
@group(1) @binding(2) var texSampler1: sampler;
@group(1) @binding(3) var tex1: texture_2d<f32>;

struct VertexInput {
  @location(0) position: vec4<f32>,
  @location(1) color: vec4<f32>,
  @location(2) uv: vec4<f32>,
  @location(3) uv1: vec4<f32>,
  @location(4) normal: vec3<f32>,
}

struct VertexOutput {
  @builtin(position) position: vec4<f32>,
  @location(0) color: vec4<f32>,
  @location(1) uv: vec2<f32>,
  @location(2) uv1: vec2<f32>,
  @location(3) viewZ: f32,
}

fn safe_normalize(v: vec3<f32>) -> vec3<f32> {
  let len = length(v);
  if (len > 0.000001) {
    return v / len;
  }
  return v;
}

fn apply_lighting(baseColor: vec4<f32>, position: vec3<f32>, normal: vec3<f32>) -> vec4<f32> {
  if (uniforms.hasLighting == 0u) {
    return baseColor;
  }
  let posEye = (uniforms.modelView * vec4<f32>(position, 1.0)).xyz;
  var normalEye = (uniforms.modelView * vec4<f32>(normal, 0.0)).xyz;
  if (uniforms.lightingFlags.y != 0u) {
    normalEye = safe_normalize(normalEye);
  }
  let matAmbient = select(uniforms.materialAmbient, baseColor, uniforms.lightingFlags.z != 0u);
  let matDiffuse = select(uniforms.materialDiffuse, baseColor, uniforms.lightingFlags.z != 0u);
  var litRgb = (uniforms.materialEmission + matAmbient * uniforms.lightModelAmbient).rgb;
  let litAlpha = 1.0;
  for (var i = 0u; i < 8u; i = i + 1u) {
    let light = uniforms.lights[i];
    if (light.state.x == 0.0) {
      continue;
    }
    litRgb = litRgb + matAmbient.rgb * light.ambient.rgb;
    var lvec: vec3<f32>;
    var attenuation = 1.0;
    var spotFactor = 1.0;
    if (light.position.w == 0.0) {
      lvec = safe_normalize(light.position.xyz);
    } else {
      let delta = light.position.xyz - posEye;
      let dist = length(delta);
      lvec = select(vec3<f32>(0.0, 0.0, 1.0), delta / dist, dist > 0.000001);
      attenuation = 1.0 / (light.state.w + light.attenuation.x * dist + light.attenuation.y * dist * dist);
      if (light.state.z < 180.0) {
        let cosAngle = dot(-lvec, safe_normalize(light.spotDirection.xyz));
        if (cosAngle < cos(light.state.z * 0.017453292519943295)) {
          spotFactor = 0.0;
        } else {
          spotFactor = pow(cosAngle, light.state.y);
        }
      }
    }
    var lightingNormal = normalEye;
    if (uniforms.lightingFlags.w != 0u && dot(lightingNormal, lvec) < 0.0) {
      lightingNormal = -lightingNormal;
    }
    let ndotl = dot(lightingNormal, lvec);
    if (ndotl > 0.0 && spotFactor > 0.0 && attenuation > 0.0) {
      let factor = ndotl * spotFactor * attenuation;
      var specFactor = 0.0;
      if (uniforms.materialParams.x > 0.0
          && (uniforms.materialSpecular.r > 0.0 || uniforms.materialSpecular.g > 0.0 || uniforms.materialSpecular.b > 0.0)) {
        let viewVec = vec3<f32>(0.0, 0.0, 1.0);
        let halfVec = safe_normalize(lvec + viewVec);
        specFactor = pow(clamp(dot(lightingNormal, halfVec), 0.0, 1.0), uniforms.materialParams.x) * spotFactor * attenuation;
      }
      litRgb = litRgb + matDiffuse.rgb * light.diffuse.rgb * factor;
      litRgb = litRgb + uniforms.materialSpecular.rgb * light.specular.rgb * specFactor;
    }
  }
  return vec4<f32>(clamp(litRgb, vec3<f32>(0.0), vec3<f32>(1.0)), clamp(litAlpha, 0.0, 1.0));
}

@vertex
fn vs_main(input: VertexInput) -> VertexOutput {
  var output: VertexOutput;
  let position = input.position.xyz;
  output.position = uniforms.mvp * vec4<f32>(position, 1.0);
  output.color = apply_lighting(input.color, position, input.normal);
  output.uv = (uniforms.textureMatrix0 * input.uv).xy;
  output.uv1 = (uniforms.textureMatrix1 * input.uv1).xy;
  output.viewZ = abs((uniforms.modelView * vec4<f32>(position, 1.0)).z);
  return output;
}
