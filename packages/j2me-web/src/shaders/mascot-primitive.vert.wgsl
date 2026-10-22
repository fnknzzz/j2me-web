struct VertexOut {
  @builtin(position) position: vec4f,
};

@vertex
fn vs_main(@location(0) position: vec4f) -> VertexOut {
  var out: VertexOut;
  out.position = position;
  return out;
}
