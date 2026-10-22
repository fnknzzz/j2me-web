struct VertexIn {
  @location(0) position: vec2f,
  @location(1) color: vec4f,
  @location(2) physicalPosition: vec2f,
  @location(3) params0: vec4f,
  @location(4) params1: vec4f,
  @location(5) params2: vec4f,
  @location(6) kind: f32,
};

struct VertexOut {
  @builtin(position) position: vec4f,
  @location(0) color: vec4f,
  @location(1) physicalPosition: vec2f,
  @location(2) params0: vec4f,
  @location(3) params1: vec4f,
  @location(4) params2: vec4f,
  @location(5) @interpolate(flat) kind: u32,
};

@vertex
fn vs_main(input: VertexIn) -> VertexOut {
  var out: VertexOut;
  out.position = vec4f(input.position, 0.0, 1.0);
  out.color = input.color;
  out.physicalPosition = input.physicalPosition;
  out.params0 = input.params0;
  out.params1 = input.params1;
  out.params2 = input.params2;
  out.kind = u32(input.kind);
  return out;
}
