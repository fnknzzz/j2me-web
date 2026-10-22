struct VertexIn {
  @location(0) position: vec2f,
  @location(1) color: vec4f,
  @location(2) physicalPosition: vec2f,
  @location(3) lineStart: vec2f,
  @location(4) lineDelta: vec2f,
  @location(5) lineParams: vec2f,
};

struct VertexOut {
  @builtin(position) position: vec4f,
  @location(0) color: vec4f,
  @location(1) physicalPosition: vec2f,
  @location(2) lineStart: vec2f,
  @location(3) lineDelta: vec2f,
  @location(4) lineParams: vec2f,
};

@vertex
fn vs_main(input: VertexIn) -> VertexOut {
  var out: VertexOut;
  out.position = vec4f(input.position, 0.0, 1.0);
  out.color = input.color;
  out.physicalPosition = input.physicalPosition;
  out.lineStart = input.lineStart;
  out.lineDelta = input.lineDelta;
  out.lineParams = input.lineParams;
  return out;
}
