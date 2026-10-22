struct Color {
  value: vec4f,
};

@group(0) @binding(0) var<uniform> color: Color;

@fragment
fn fs_main() -> @location(0) vec4f {
  return color.value;
}
