struct VertexIn {
  @location(0) position: vec2f,
  @location(1) color: vec4f,
  @location(2) physicalPosition: vec2f,
  @location(3) ellipseCenter: vec2f,
  @location(4) ellipseRadii: vec2f,
};

struct VertexOut {
  @builtin(position) position: vec4f,
  @location(0) color: vec4f,
  @location(1) physicalPosition: vec2f,
  @location(2) ellipseCenter: vec2f,
  @location(3) ellipseRadii: vec2f,
};

@vertex
fn vs_main(input: VertexIn) -> VertexOut {
  var out: VertexOut;
  out.position = vec4f(input.position, 0.0, 1.0);
  out.color = input.color;
  out.physicalPosition = input.physicalPosition;
  out.ellipseCenter = input.ellipseCenter;
  out.ellipseRadii = input.ellipseRadii;
  return out;
}
