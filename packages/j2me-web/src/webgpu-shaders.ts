/// <reference path="./wgsl.d.ts" />

import arcDottedStrokeAAFragmentShader from './shaders/arc-dotted-stroke-aa.frag.wgsl';
import arcFillAAFragmentShader from './shaders/arc-fill-aa.frag.wgsl';
import arcFillAAVertexShader from './shaders/arc-fill-aa.vert.wgsl';
import arcStrokeAAFragmentShader from './shaders/arc-stroke-aa.frag.wgsl';
import arcStrokeAAVertexShader from './shaders/arc-stroke-aa.vert.wgsl';
import colorFragmentShader from './shaders/color.frag.wgsl';
import colorVertexShader from './shaders/color.vert.wgsl';
import colorAAFragmentShader from './shaders/color-aa.frag.wgsl';
import colorAAVertexShader from './shaders/color-aa.vert.wgsl';
import ellipseAAFragmentShader from './shaders/ellipse-aa.frag.wgsl';
import ellipseAAVertexShader from './shaders/ellipse-aa.vert.wgsl';
import lineAAFragmentShader from './shaders/line-aa.frag.wgsl';
import lineAAVertexShader from './shaders/line-aa.vert.wgsl';
import mascotColorMaterialFragmentShader from './shaders/mascot-color-material.frag.wgsl';
import mascotColorMaterialVertexShader from './shaders/mascot-color-material.vert.wgsl';
import mascotPrimitiveFragmentShader from './shaders/mascot-primitive.frag.wgsl';
import mascotPrimitiveVertexShader from './shaders/mascot-primitive.vert.wgsl';
import mascotTextureFragmentShader from './shaders/mascot-texture.frag.wgsl';
import mascotTextureVertexShader from './shaders/mascot-texture.vert.wgsl';
import roundRectAAFragmentShader from './shaders/round-rect-aa.frag.wgsl';
import roundRectAAVertexShader from './shaders/round-rect-aa.vert.wgsl';
import roundRectDottedStrokeAAFragmentShader from './shaders/round-rect-dotted-stroke-aa.frag.wgsl';
import roundRectStrokeAAFragmentShader from './shaders/round-rect-stroke-aa.frag.wgsl';
import roundRectStrokeAAVertexShader from './shaders/round-rect-stroke-aa.vert.wgsl';
import shapeBatchFragmentShader from './shaders/shape-batch.frag.wgsl';
import shapeBatchVertexShader from './shaders/shape-batch.vert.wgsl';
import textAlphaFragmentShader from './shaders/text-alpha.frag.wgsl';
import textAlphaVertexShader from './shaders/text-alpha.vert.wgsl';
import textureFragmentShader from './shaders/texture.frag.wgsl';
import textureVertexShader from './shaders/texture.vert.wgsl';
import textureStraightFragmentShader from './shaders/texture-straight.frag.wgsl';

export const COLOR_VERTEX_SHADER = colorVertexShader;
export const COLOR_FRAGMENT_SHADER = colorFragmentShader;
export const COLOR_AA_VERTEX_SHADER = colorAAVertexShader;
export const COLOR_AA_FRAGMENT_SHADER = colorAAFragmentShader;
export const LINE_AA_VERTEX_SHADER = lineAAVertexShader;
export const LINE_AA_FRAGMENT_SHADER = lineAAFragmentShader;
export const ELLIPSE_AA_VERTEX_SHADER = ellipseAAVertexShader;
export const ELLIPSE_AA_FRAGMENT_SHADER = ellipseAAFragmentShader;
export const ROUND_RECT_AA_VERTEX_SHADER = roundRectAAVertexShader;
export const ROUND_RECT_AA_FRAGMENT_SHADER = roundRectAAFragmentShader;
export const ROUND_RECT_STROKE_AA_VERTEX_SHADER = roundRectStrokeAAVertexShader;
export const ROUND_RECT_STROKE_AA_FRAGMENT_SHADER =
  roundRectStrokeAAFragmentShader;
export const ROUND_RECT_DOTTED_STROKE_AA_FRAGMENT_SHADER =
  roundRectDottedStrokeAAFragmentShader;
export const ARC_FILL_AA_VERTEX_SHADER = arcFillAAVertexShader;
export const ARC_FILL_AA_FRAGMENT_SHADER = arcFillAAFragmentShader;
export const ARC_STROKE_AA_VERTEX_SHADER = arcStrokeAAVertexShader;
export const ARC_STROKE_AA_FRAGMENT_SHADER = arcStrokeAAFragmentShader;
export const ARC_DOTTED_STROKE_AA_FRAGMENT_SHADER =
  arcDottedStrokeAAFragmentShader;
export const SHAPE_BATCH_VERTEX_SHADER = shapeBatchVertexShader;
export const SHAPE_BATCH_FRAGMENT_SHADER = shapeBatchFragmentShader;
export const TEXTURE_VERTEX_SHADER = textureVertexShader;
export const TEXTURE_FRAGMENT_SHADER = textureFragmentShader;
export const STRAIGHT_TEXTURE_FRAGMENT_SHADER = textureStraightFragmentShader;
export const TEXT_ALPHA_FRAGMENT_SHADER = textAlphaFragmentShader;
export const TEXT_ALPHA_VERTEX_SHADER = textAlphaVertexShader;
export const MASCOT_PRIMITIVE_VERTEX_SHADER = mascotPrimitiveVertexShader;
export const MASCOT_PRIMITIVE_FRAGMENT_SHADER = mascotPrimitiveFragmentShader;
export const MASCOT_TEXTURE_VERTEX_SHADER = mascotTextureVertexShader;
export const MASCOT_TEXTURE_FRAGMENT_SHADER = mascotTextureFragmentShader;
export const MASCOT_COLOR_MATERIAL_VERTEX_SHADER =
  mascotColorMaterialVertexShader;
export const MASCOT_COLOR_MATERIAL_FRAGMENT_SHADER =
  mascotColorMaterialFragmentShader;
