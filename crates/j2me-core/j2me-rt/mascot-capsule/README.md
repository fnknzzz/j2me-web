# Mascot Capsule Micro3D runtime sources

This source root contains the vendor-specific `com.mascotcapsule.micro3d.v3`
API. It is intentionally separate from `jsr-184`, which implements the
standard JSR-184/M3G API.

The first import is the dependency-free 20.12 fixed-point math layer. Model
decoding, animation, API facade classes, Rust native peers, and WebGPU command
emission will be added incrementally. Source derived from J2ME-Loader retains
its Apache-2.0 copyright notice.

Set `MASCOT_CAPSULE=0` when invoking `make` to exclude this optional API.
