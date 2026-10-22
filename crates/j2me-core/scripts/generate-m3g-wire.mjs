import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const packageDir = path.resolve(scriptDir, '..');
const repoDir = path.resolve(packageDir, '..', '..');
const schemaPath = path.join(
  packageDir,
  'src/gles1_webgpu/schema/m3g_webgpu_wire.json',
);
const args = process.argv.slice(2);
const cOutputIndex = args.indexOf('--c-output');
const cOutputPath = cOutputIndex === -1 ? null : args[cOutputIndex + 1];
const swiftOutputIndex = args.indexOf('--swift-output');
const swiftOutputPath =
  swiftOutputIndex === -1 ? null : args[swiftOutputIndex + 1];
const validateOnly = args.includes('--validate');
if (cOutputIndex !== -1 && !cOutputPath) {
  throw new Error('--c-output requires a path');
}
if (swiftOutputIndex !== -1 && !swiftOutputPath) {
  throw new Error('--swift-output requires a path');
}
if (!validateOnly && !cOutputPath && !swiftOutputPath) {
  throw new Error(
    'Pass --validate, --c-output <path>, or --swift-output <path>',
  );
}
const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'));
const drawPacket = schema.drawPacket;
const derivedIndexBuffer = schema.derivedIndexBuffer;
const clear = schema.clear;
const pipelineKey = schema.pipelineKey;
const drawPacketRequiredOffsets = [
  'topology',
  'pipelineKey',
  'positionHandle',
  'positionVersion',
  'colorHandle',
  'colorVersion',
  'normalHandle',
  'normalVersion',
  'texcoord0Handle',
  'texcoord0Version',
  'indexHandle',
  'indexVersion',
  'texture0Handle',
  'texture0Version',
  'texture0SourceImageHandle',
  'texcoord1Handle',
  'texcoord1Version',
  'texture1Handle',
  'texture1Version',
  'texture1SourceImageHandle',
  'viewportX',
  'viewportY',
  'viewportWidth',
  'viewportHeight',
  'scissorX',
  'scissorY',
  'scissorWidth',
  'scissorHeight',
  'indexCount',
  'instanceCount',
  'frameVertexStart',
  'frameVertexCount',
  'frameSourceFlags',
  'frameBytes',
  'texture0WrapS',
  'texture0WrapT',
  'texture0MinFilter',
  'texture0MagFilter',
  'texture1WrapS',
  'texture1WrapT',
  'texture1MinFilter',
  'texture1MagFilter',
  'polygonOffsetFactor',
  'polygonOffsetUnits',
  'defaultColor',
  'uniform',
];

function resolveFieldLayout(fields, layoutName) {
  if (!Array.isArray(fields)) {
    throw new Error(`${layoutName} must be an array`);
  }
  let bytes = 0;
  const offset = {};
  const size = {};
  for (const field of fields) {
    if (!Array.isArray(field) || field.length !== 2) {
      throw new Error(`${layoutName} fields must be [name, bytes] pairs`);
    }
    const [fieldName, fieldBytes] = field;
    if (
      typeof fieldName !== 'string' ||
      fieldName.length === 0 ||
      typeof fieldBytes !== 'number' ||
      !Number.isInteger(fieldBytes) ||
      fieldBytes <= 0 ||
      fieldBytes % 4 !== 0
    ) {
      throw new Error(
        `${layoutName}.${fieldName} must be a positive 4-byte-aligned size`,
      );
    }
    if (Object.hasOwn(offset, fieldName)) {
      throw new Error(`${layoutName} has duplicate ${fieldName}`);
    }
    offset[fieldName] = bytes;
    size[fieldName] = fieldBytes;
    bytes += fieldBytes;
  }
  return { bytes, offset, size };
}

function assertCommandSchema(command, requiredFields, headerLayout, name) {
  if (
    !command ||
    !Number.isInteger(command.commandId) ||
    command.commandId < 0 ||
    command.commandId > 255
  ) {
    throw new Error(`${name} has an invalid commandId`);
  }
  for (const fieldName of requiredFields) {
    if (!Object.hasOwn(headerLayout.offset, fieldName)) {
      throw new Error(`${name} is missing ${fieldName}`);
    }
  }
}

function assertPipelineKeySchema(value) {
  const requiredFields = [
    'hasVertexColor',
    'hasTexture',
    'cullFace',
    'cullFront',
    'frontCW',
    'depthTestAlways',
    'depthWriteOff',
    'hasLighting',
    'hasFog',
    'hasAlphaTest',
    'flatShading',
    'perspectiveCorrection',
    'blendShift',
    'blendMask',
    'writeMaskShift',
  ];
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('M3G pipeline key must be an object');
  }
  for (const fieldName of requiredFields) {
    const fieldValue = value[fieldName];
    if (
      !Number.isInteger(fieldValue) ||
      fieldValue < 0 ||
      fieldValue > 0xffff_ffff
    ) {
      throw new Error(`M3G pipeline key ${fieldName} must be a uint32 integer`);
    }
  }
  const stateFlags = requiredFields
    .slice(0, 12)
    .map((fieldName) => value[fieldName]);
  for (const flag of stateFlags) {
    if (flag === 0 || (flag & (flag - 1)) !== 0) {
      throw new Error('M3G pipeline key state flags must be single bits');
    }
  }
  if (new Set(stateFlags).size !== stateFlags.length) {
    throw new Error('M3G pipeline key state flags must be unique');
  }
  if (
    value.blendMask === 0 ||
    value.blendMask > 0xf ||
    value.blendShift + 3 > value.writeMaskShift ||
    value.writeMaskShift + 4 > 32
  ) {
    throw new Error('M3G pipeline key blend/write-mask layout is invalid');
  }
}

const headerLayout = resolveFieldLayout(
  drawPacket.header,
  'M3G draw packet header',
);
assertCommandSchema(
  drawPacket,
  drawPacketRequiredOffsets,
  headerLayout,
  'M3G draw packet wire schema',
);
const derivedIndexBufferRequiredOffsets = [
  'handle',
  'version',
  'sourceKind',
  'sourceHandle',
  'sourceVersion',
  'indexCount',
];
const derivedIndexBufferHeaderLayout = resolveFieldLayout(
  derivedIndexBuffer.header,
  'M3G derived index buffer header',
);
assertCommandSchema(
  derivedIndexBuffer,
  derivedIndexBufferRequiredOffsets,
  derivedIndexBufferHeaderLayout,
  'M3G derived index buffer wire schema',
);
const clearHeaderLayout = resolveFieldLayout(clear.header, 'M3G clear header');
assertCommandSchema(
  clear,
  [
    'contextHandle',
    'backgroundHandle',
    'scissorX',
    'scissorY',
    'scissorWidth',
    'scissorHeight',
    'viewportX',
    'viewportY',
    'viewportWidth',
    'viewportHeight',
  ],
  clearHeaderLayout,
  'M3G clear wire schema',
);
if (
  new Set([drawPacket.commandId, derivedIndexBuffer.commandId, clear.commandId])
    .size !== 3
) {
  throw new Error('M3G wire command IDs must be unique');
}
assertPipelineKeySchema(pipelineKey);
const instanceLayout = resolveFieldLayout(
  drawPacket.instance,
  'M3G draw packet instance',
);
if (
  instanceLayout.bytes !== 16 * 4 ||
  headerLayout.offset.uniform + headerLayout.size.uniform !== headerLayout.bytes
) {
  throw new Error('M3G draw packet wire schema is inconsistent');
}

const cOutput = `/* Generated by scripts/generate-m3g-wire.mjs from ${path.relative(repoDir, schemaPath)}. Do not edit. */
#ifndef M3G_WEBGPU_WIRE_H
#define M3G_WEBGPU_WIRE_H

#define M3G_DRAW_PACKET_COMMAND_ID ${drawPacket.commandId}
#define M3G_DRAW_PACKET_HEADER_BYTES ${headerLayout.bytes}u
#define M3G_DRAW_PACKET_INSTANCE_BYTES ${instanceLayout.bytes}u
#define M3G_DRAW_PACKET_UNIFORM_BYTES ${headerLayout.size.uniform}u
#define M3G_DRAW_PACKET_TOPOLOGY_OFFSET ${headerLayout.offset.topology}u
#define M3G_DRAW_PACKET_PIPELINE_KEY_OFFSET ${headerLayout.offset.pipelineKey}u
#define M3G_DRAW_PACKET_POSITION_HANDLE_OFFSET ${headerLayout.offset.positionHandle}u
#define M3G_DRAW_PACKET_POSITION_VERSION_OFFSET ${headerLayout.offset.positionVersion}u
#define M3G_DRAW_PACKET_COLOR_HANDLE_OFFSET ${headerLayout.offset.colorHandle}u
#define M3G_DRAW_PACKET_COLOR_VERSION_OFFSET ${headerLayout.offset.colorVersion}u
#define M3G_DRAW_PACKET_NORMAL_HANDLE_OFFSET ${headerLayout.offset.normalHandle}u
#define M3G_DRAW_PACKET_NORMAL_VERSION_OFFSET ${headerLayout.offset.normalVersion}u
#define M3G_DRAW_PACKET_TEXCOORD0_HANDLE_OFFSET ${headerLayout.offset.texcoord0Handle}u
#define M3G_DRAW_PACKET_TEXCOORD0_VERSION_OFFSET ${headerLayout.offset.texcoord0Version}u
#define M3G_DRAW_PACKET_INDEX_HANDLE_OFFSET ${headerLayout.offset.indexHandle}u
#define M3G_DRAW_PACKET_INDEX_VERSION_OFFSET ${headerLayout.offset.indexVersion}u
#define M3G_DRAW_PACKET_TEXTURE0_HANDLE_OFFSET ${headerLayout.offset.texture0Handle}u
#define M3G_DRAW_PACKET_TEXTURE0_VERSION_OFFSET ${headerLayout.offset.texture0Version}u
#define M3G_DRAW_PACKET_TEXTURE0_SOURCE_IMAGE_HANDLE_OFFSET ${headerLayout.offset.texture0SourceImageHandle}u
#define M3G_DRAW_PACKET_TEXCOORD1_HANDLE_OFFSET ${headerLayout.offset.texcoord1Handle}u
#define M3G_DRAW_PACKET_TEXCOORD1_VERSION_OFFSET ${headerLayout.offset.texcoord1Version}u
#define M3G_DRAW_PACKET_TEXTURE1_HANDLE_OFFSET ${headerLayout.offset.texture1Handle}u
#define M3G_DRAW_PACKET_TEXTURE1_VERSION_OFFSET ${headerLayout.offset.texture1Version}u
#define M3G_DRAW_PACKET_TEXTURE1_SOURCE_IMAGE_HANDLE_OFFSET ${headerLayout.offset.texture1SourceImageHandle}u
#define M3G_DRAW_PACKET_VIEWPORT_X_OFFSET ${headerLayout.offset.viewportX}u
#define M3G_DRAW_PACKET_VIEWPORT_Y_OFFSET ${headerLayout.offset.viewportY}u
#define M3G_DRAW_PACKET_VIEWPORT_WIDTH_OFFSET ${headerLayout.offset.viewportWidth}u
#define M3G_DRAW_PACKET_VIEWPORT_HEIGHT_OFFSET ${headerLayout.offset.viewportHeight}u
#define M3G_DRAW_PACKET_SCISSOR_X_OFFSET ${headerLayout.offset.scissorX}u
#define M3G_DRAW_PACKET_SCISSOR_Y_OFFSET ${headerLayout.offset.scissorY}u
#define M3G_DRAW_PACKET_SCISSOR_WIDTH_OFFSET ${headerLayout.offset.scissorWidth}u
#define M3G_DRAW_PACKET_SCISSOR_HEIGHT_OFFSET ${headerLayout.offset.scissorHeight}u
#define M3G_DRAW_PACKET_INDEX_COUNT_OFFSET ${headerLayout.offset.indexCount}u
#define M3G_DRAW_PACKET_INSTANCE_COUNT_OFFSET ${headerLayout.offset.instanceCount}u
#define M3G_DRAW_PACKET_FRAME_VERTEX_START_OFFSET ${headerLayout.offset.frameVertexStart}u
#define M3G_DRAW_PACKET_FRAME_VERTEX_COUNT_OFFSET ${headerLayout.offset.frameVertexCount}u
#define M3G_DRAW_PACKET_FRAME_SOURCE_FLAGS_OFFSET ${headerLayout.offset.frameSourceFlags}u
#define M3G_DRAW_PACKET_FRAME_BYTES_OFFSET ${headerLayout.offset.frameBytes}u
#define M3G_DRAW_PACKET_TEXTURE0_WRAP_S_OFFSET ${headerLayout.offset.texture0WrapS}u
#define M3G_DRAW_PACKET_TEXTURE0_WRAP_T_OFFSET ${headerLayout.offset.texture0WrapT}u
#define M3G_DRAW_PACKET_TEXTURE0_MIN_FILTER_OFFSET ${headerLayout.offset.texture0MinFilter}u
#define M3G_DRAW_PACKET_TEXTURE0_MAG_FILTER_OFFSET ${headerLayout.offset.texture0MagFilter}u
#define M3G_DRAW_PACKET_TEXTURE1_WRAP_S_OFFSET ${headerLayout.offset.texture1WrapS}u
#define M3G_DRAW_PACKET_TEXTURE1_WRAP_T_OFFSET ${headerLayout.offset.texture1WrapT}u
#define M3G_DRAW_PACKET_TEXTURE1_MIN_FILTER_OFFSET ${headerLayout.offset.texture1MinFilter}u
#define M3G_DRAW_PACKET_TEXTURE1_MAG_FILTER_OFFSET ${headerLayout.offset.texture1MagFilter}u
#define M3G_DRAW_PACKET_POLYGON_OFFSET_FACTOR_OFFSET ${headerLayout.offset.polygonOffsetFactor}u
#define M3G_DRAW_PACKET_POLYGON_OFFSET_UNITS_OFFSET ${headerLayout.offset.polygonOffsetUnits}u
#define M3G_DRAW_PACKET_DEFAULT_COLOR_OFFSET ${headerLayout.offset.defaultColor}u
#define M3G_DRAW_PACKET_UNIFORM_OFFSET ${headerLayout.offset.uniform}u
#define M3G_DRAW_PACKET_BYTES(frame_bytes, instance_count) \\
    (1u + M3G_DRAW_PACKET_HEADER_BYTES + (unsigned int)(frame_bytes) + \\
     ((unsigned int)(instance_count) * M3G_DRAW_PACKET_INSTANCE_BYTES))
#define M3G_DERIVED_INDEX_BUFFER_COMMAND_ID ${derivedIndexBuffer.commandId}
#define M3G_DERIVED_INDEX_BUFFER_HEADER_BYTES ${derivedIndexBufferHeaderLayout.bytes}u
#define M3G_DERIVED_INDEX_BUFFER_HANDLE_OFFSET ${derivedIndexBufferHeaderLayout.offset.handle}u
#define M3G_DERIVED_INDEX_BUFFER_VERSION_OFFSET ${derivedIndexBufferHeaderLayout.offset.version}u
#define M3G_DERIVED_INDEX_BUFFER_SOURCE_KIND_OFFSET ${derivedIndexBufferHeaderLayout.offset.sourceKind}u
#define M3G_DERIVED_INDEX_BUFFER_SOURCE_HANDLE_OFFSET ${derivedIndexBufferHeaderLayout.offset.sourceHandle}u
#define M3G_DERIVED_INDEX_BUFFER_SOURCE_VERSION_OFFSET ${derivedIndexBufferHeaderLayout.offset.sourceVersion}u
#define M3G_DERIVED_INDEX_BUFFER_INDEX_COUNT_OFFSET ${derivedIndexBufferHeaderLayout.offset.indexCount}u
#define M3G_DERIVED_INDEX_BUFFER_BYTES(index_count) \\
    (1u + M3G_DERIVED_INDEX_BUFFER_HEADER_BYTES + \\
     ((unsigned int)(index_count) * 4u))
#define M3G_CLEAR_COMMAND_ID ${clear.commandId}
#define M3G_CLEAR_HEADER_BYTES ${clearHeaderLayout.bytes}u
#define M3G_CLEAR_CONTEXT_HANDLE_OFFSET ${clearHeaderLayout.offset.contextHandle}u
#define M3G_CLEAR_BACKGROUND_HANDLE_OFFSET ${clearHeaderLayout.offset.backgroundHandle}u
#define M3G_CLEAR_SCISSOR_X_OFFSET ${clearHeaderLayout.offset.scissorX}u
#define M3G_CLEAR_SCISSOR_Y_OFFSET ${clearHeaderLayout.offset.scissorY}u
#define M3G_CLEAR_SCISSOR_WIDTH_OFFSET ${clearHeaderLayout.offset.scissorWidth}u
#define M3G_CLEAR_SCISSOR_HEIGHT_OFFSET ${clearHeaderLayout.offset.scissorHeight}u
#define M3G_CLEAR_VIEWPORT_X_OFFSET ${clearHeaderLayout.offset.viewportX}u
#define M3G_CLEAR_VIEWPORT_Y_OFFSET ${clearHeaderLayout.offset.viewportY}u
#define M3G_CLEAR_VIEWPORT_WIDTH_OFFSET ${clearHeaderLayout.offset.viewportWidth}u
#define M3G_CLEAR_VIEWPORT_HEIGHT_OFFSET ${clearHeaderLayout.offset.viewportHeight}u
#define M3G_WGPU_PIPELINE_HAS_VERTEX_COLOR ${pipelineKey.hasVertexColor}u
#define M3G_WGPU_PIPELINE_HAS_TEXTURE ${pipelineKey.hasTexture}u
#define M3G_WGPU_PIPELINE_CULL_FACE ${pipelineKey.cullFace}u
#define M3G_WGPU_PIPELINE_CULL_FRONT ${pipelineKey.cullFront}u
#define M3G_WGPU_PIPELINE_FRONT_CW ${pipelineKey.frontCW}u
#define M3G_WGPU_PIPELINE_DEPTH_TEST_ALWAYS ${pipelineKey.depthTestAlways}u
#define M3G_WGPU_PIPELINE_DEPTH_WRITE_OFF ${pipelineKey.depthWriteOff}u
#define M3G_WGPU_PIPELINE_HAS_LIGHTING ${pipelineKey.hasLighting}u
#define M3G_WGPU_PIPELINE_HAS_FOG ${pipelineKey.hasFog}u
#define M3G_WGPU_PIPELINE_HAS_ALPHA_TEST ${pipelineKey.hasAlphaTest}u
#define M3G_WGPU_PIPELINE_FLAT_SHADING ${pipelineKey.flatShading}u
#define M3G_WGPU_PIPELINE_PERSPECTIVE_CORRECTION ${pipelineKey.perspectiveCorrection}u
#define M3G_WGPU_PIPELINE_BLEND_SHIFT ${pipelineKey.blendShift}u
#define M3G_WGPU_PIPELINE_BLEND_MASK ${pipelineKey.blendMask}u
#define M3G_WGPU_PIPELINE_WRITE_MASK_SHIFT ${pipelineKey.writeMaskShift}u

#endif
`;

const swiftConstant = (name) => name;
const swiftOffsets = (layout, names) =>
  names
    .map(
      (name) =>
        `        static let ${swiftConstant(name)} = ${layout.offset[name]}`,
    )
    .join('\n');
const swiftPipelineConstants = Object.entries(pipelineKey)
  .map(
    ([name, value]) =>
      `        static let ${swiftConstant(name)}: UInt32 = ${value}`,
  )
  .join('\n');
const swiftOutput = `// Generated by packages/jvm-rust/scripts/generate-m3g-wire.mjs from ${path.relative(repoDir, schemaPath)}.
// Do not edit manually.

import Foundation

enum M3GWire {
    static let clearCommand: UInt8 = ${clear.commandId}
    static let bindTargetCommand: UInt8 = 135
    static let releaseTargetCommand: UInt8 = 136
    static let bindImageTargetCommand: UInt8 = 137
    static let createVertexArrayCommand: UInt8 = 140
    static let updateTextureCommand: UInt8 = 147
    static let deleteResourceCommand: UInt8 = 148
    static let invalidateImageTexturesCommand: UInt8 = 149
    static let createIndexBufferCommand: UInt8 = 150
    static let beginBatchCommand: UInt8 = 164
    static let endBatchCommand: UInt8 = 165
    static let createBackgroundCommand: UInt8 = 175
    static let backgroundColorCommand: UInt8 = 176
    static let backgroundEnableCommand: UInt8 = 178
    static let drawPacketCommand: UInt8 = ${drawPacket.commandId}
    static let createDerivedIndexCommand: UInt8 = ${derivedIndexBuffer.commandId}

    enum Clear {
        static let headerBytes = ${clearHeaderLayout.bytes}
${swiftOffsets(clearHeaderLayout, Object.keys(clearHeaderLayout.offset))}
    }

    enum Draw {
        static let headerBytes = ${headerLayout.bytes}
        static let instanceBytes = ${instanceLayout.bytes}
        static let uniformBytes = ${headerLayout.size.uniform}
${swiftOffsets(headerLayout, Object.keys(headerLayout.offset))}
    }

    enum DerivedIndex {
        static let headerBytes = ${derivedIndexBufferHeaderLayout.bytes}
${swiftOffsets(derivedIndexBufferHeaderLayout, Object.keys(derivedIndexBufferHeaderLayout.offset))}
    }

    enum PipelineKey {
${swiftPipelineConstants}
    }

    static let resourceVertexArray: UInt32 = 1
    static let resourceGLTexture: UInt32 = 2
    static let resourceImageTarget: UInt32 = 3
    static let resourceIndexBuffer: UInt32 = 4
}
`;

if (cOutputPath) {
  fs.mkdirSync(path.dirname(cOutputPath), { recursive: true });
  fs.writeFileSync(cOutputPath, cOutput);
  console.log(`Generated ${path.relative(repoDir, cOutputPath)}`);
}
if (swiftOutputPath) {
  fs.mkdirSync(path.dirname(swiftOutputPath), { recursive: true });
  fs.writeFileSync(swiftOutputPath, swiftOutput);
  console.log(`Generated ${path.relative(repoDir, swiftOutputPath)}`);
}
