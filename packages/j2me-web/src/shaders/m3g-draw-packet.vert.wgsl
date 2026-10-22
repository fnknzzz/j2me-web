struct VertexInput {
  @location(0) position: vec4<f32>,
  @location(1) color: vec4<f32>,
  @location(5) mvp0: vec4<f32>,
  @location(6) mvp1: vec4<f32>,
  @location(7) mvp2: vec4<f32>,
  @location(8) mvp3: vec4<f32>,
}

struct DrawPacketVertexOutput {
  @builtin(position) position: vec4<f32>,
  @location(0) color: vec4<f32>,
}

@vertex
fn vs_main(input: VertexInput) -> DrawPacketVertexOutput {
  var output: DrawPacketVertexOutput;
  let mvp = mat4x4<f32>(input.mvp0, input.mvp1, input.mvp2, input.mvp3);
  output.position = mvp * input.position;
  output.color = input.color;
  return output;
}
