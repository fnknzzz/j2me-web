import m3gWebgpuWireSchema from '@jsjvm/j2me-core/m3g-wire';
import { describe, expect, it } from 'vitest';
import { resolveSizedWireLayout } from '../wire-layout';

describe('M3G draw packet wire schema', () => {
  it('derives the triangle fan index resource payload from the shared schema', () => {
    const raw = m3gWebgpuWireSchema.derivedIndexBuffer;
    const header = resolveSizedWireLayout(
      raw.header,
      'derivedIndexBuffer.header',
    );
    const indexCount = 6;
    const payload = new Uint8Array(header.bytes + indexCount * 4);
    const view = new DataView(payload.buffer);

    view.setUint32(header.offset.handle, 0xf0000001, true);
    view.setUint32(header.offset.version, 3, true);
    view.setUint32(header.offset.sourceKind, 4, true);
    view.setUint32(header.offset.sourceHandle, 42, true);
    view.setUint32(header.offset.sourceVersion, 7, true);
    view.setUint32(header.offset.indexCount, indexCount, true);
    view.setUint32(header.bytes + 5 * 4, 9, true);

    expect(raw.commandId).toBe(180);
    expect(header.bytes).toBe(24);
    expect(payload.byteLength).toBe(header.bytes + indexCount * 4);
    expect(view.getUint32(header.offset.sourceHandle, true)).toBe(42);
    expect(view.getUint32(header.offset.indexCount, true)).toBe(indexCount);
    expect(view.getUint32(header.bytes + 5 * 4, true)).toBe(9);
  });

  it('freezes clear scissor and viewport in the shared clear command', () => {
    const raw = m3gWebgpuWireSchema.clear;
    const header = resolveSizedWireLayout(raw.header, 'clear.header');
    const payload = new Uint8Array(header.bytes);
    const view = new DataView(payload.buffer);

    view.setUint32(header.offset.contextHandle, 41, true);
    view.setUint32(header.offset.backgroundHandle, 42, true);
    view.setInt32(header.offset.scissorX, 3, true);
    view.setInt32(header.offset.scissorY, 4, true);
    view.setInt32(header.offset.scissorWidth, 234, true);
    view.setInt32(header.offset.scissorHeight, 312, true);
    view.setInt32(header.offset.viewportX, 0, true);
    view.setInt32(header.offset.viewportY, 0, true);
    view.setInt32(header.offset.viewportWidth, 240, true);
    view.setInt32(header.offset.viewportHeight, 320, true);

    expect(raw.commandId).toBe(131);
    expect(header.bytes).toBe(40);
    expect(view.getUint32(header.offset.backgroundHandle, true)).toBe(42);
    expect(view.getInt32(header.offset.scissorWidth, true)).toBe(234);
    expect(view.getInt32(header.offset.viewportWidth, true)).toBe(240);
    expect(header.offset.viewportX).toBe(
      header.offset.scissorHeight + header.size.scissorHeight,
    );
  });

  it('keeps the pipeline key state bits separate from blend and write masks', () => {
    const key = m3gWebgpuWireSchema.pipelineKey;
    const stateFlags = [
      key.hasVertexColor,
      key.hasTexture,
      key.cullFace,
      key.cullFront,
      key.frontCW,
      key.depthTestAlways,
      key.depthWriteOff,
      key.hasLighting,
      key.hasFog,
      key.hasAlphaTest,
      key.flatShading,
      key.perspectiveCorrection,
    ];
    const stateMask = stateFlags.reduce((mask, flag) => mask | flag, 0);
    const blendMask = key.blendMask << key.blendShift;
    const writeMask = 0xf << key.writeMaskShift;

    expect(new Set(stateFlags).size).toBe(stateFlags.length);
    expect(stateMask & blendMask).toBe(0);
    expect(stateMask & writeMask).toBe(0);
    expect(blendMask & writeMask).toBe(0);
  });

  it('encodes a fixture with an instance matrix payload', () => {
    const raw = m3gWebgpuWireSchema.drawPacket;
    const header = resolveSizedWireLayout(raw.header, 'drawPacket.header');
    const instance = resolveSizedWireLayout(
      raw.instance,
      'drawPacket.instance',
    );
    const schema = {
      commandId: raw.commandId,
      headerBytes: header.bytes,
      instanceBytes: instance.bytes,
      uniformBytes: header.size.uniform,
      offset: header.offset,
    };
    const instanceCount = 2;
    const payload = new Uint8Array(
      schema.headerBytes + schema.instanceBytes * instanceCount,
    );
    const view = new DataView(payload.buffer);

    view.setUint32(schema.offset.topology, 1, true);
    view.setUint32(schema.offset.pipelineKey, 0x829, true);
    view.setUint32(schema.offset.positionHandle, 101, true);
    view.setUint32(schema.offset.positionVersion, 1, true);
    view.setUint32(schema.offset.colorHandle, 102, true);
    view.setUint32(schema.offset.colorVersion, 2, true);
    view.setUint32(schema.offset.normalHandle, 103, true);
    view.setUint32(schema.offset.normalVersion, 3, true);
    view.setUint32(schema.offset.texcoord0Handle, 104, true);
    view.setUint32(schema.offset.texcoord0Version, 4, true);
    view.setUint32(schema.offset.indexHandle, 105, true);
    view.setUint32(schema.offset.indexVersion, 5, true);
    view.setUint32(schema.offset.texture0Handle, 106, true);
    view.setUint32(schema.offset.texture0Version, 6, true);
    view.setUint32(schema.offset.texture0SourceImageHandle, 107, true);
    view.setUint32(schema.offset.texcoord1Handle, 108, true);
    view.setUint32(schema.offset.texcoord1Version, 7, true);
    view.setUint32(schema.offset.texture1Handle, 109, true);
    view.setUint32(schema.offset.texture1Version, 8, true);
    view.setUint32(schema.offset.texture1SourceImageHandle, 110, true);
    view.setInt32(schema.offset.viewportX, 4, true);
    view.setInt32(schema.offset.viewportY, 5, true);
    view.setInt32(schema.offset.viewportWidth, 240, true);
    view.setInt32(schema.offset.viewportHeight, 320, true);
    view.setInt32(schema.offset.scissorX, 6, true);
    view.setInt32(schema.offset.scissorY, 7, true);
    view.setInt32(schema.offset.scissorWidth, 230, true);
    view.setInt32(schema.offset.scissorHeight, 310, true);
    view.setUint32(schema.offset.indexCount, 36, true);
    view.setUint32(schema.offset.instanceCount, instanceCount, true);
    view.setUint32(schema.offset.texture0WrapS, 0x2901, true);
    view.setUint32(schema.offset.texture0WrapT, 0x812f, true);
    view.setUint32(schema.offset.texture1WrapS, 0x812f, true);
    view.setUint32(schema.offset.texture1WrapT, 0x2901, true);
    view.setFloat32(schema.offset.polygonOffsetFactor, 1.5, true);
    view.setFloat32(schema.offset.polygonOffsetUnits, -2, true);
    view.setFloat32(schema.offset.defaultColor + 3 * 4, 1, true);
    view.setFloat32(schema.offset.uniform, 0.5, true);
    view.setFloat32(
      schema.offset.uniform + schema.uniformBytes - 4,
      0.75,
      true,
    );
    view.setFloat32(schema.headerBytes + 15 * 4, 1, true);

    expect(payload.byteLength).toBe(
      schema.headerBytes + schema.instanceBytes * instanceCount,
    );
    expect(view.getUint32(schema.offset.positionHandle, true)).toBe(101);
    expect(view.getUint32(schema.offset.pipelineKey, true) & 0x800).toBe(0x800);
    expect(view.getUint32(schema.offset.colorHandle, true)).toBe(102);
    expect(view.getUint32(schema.offset.normalHandle, true)).toBe(103);
    expect(view.getUint32(schema.offset.texcoord0Handle, true)).toBe(104);
    expect(view.getUint32(schema.offset.indexHandle, true)).toBe(105);
    expect(view.getUint32(schema.offset.texture0Handle, true)).toBe(106);
    expect(view.getUint32(schema.offset.texture0Version, true)).toBe(6);
    expect(view.getUint32(schema.offset.texture0SourceImageHandle, true)).toBe(
      107,
    );
    expect(view.getUint32(schema.offset.texcoord1Handle, true)).toBe(108);
    expect(view.getUint32(schema.offset.texture1Handle, true)).toBe(109);
    expect(view.getUint32(schema.offset.texture1Version, true)).toBe(8);
    expect(view.getUint32(schema.offset.texture1SourceImageHandle, true)).toBe(
      110,
    );
    expect(view.getFloat32(schema.offset.polygonOffsetFactor, true)).toBe(1.5);
    expect(view.getFloat32(schema.offset.polygonOffsetUnits, true)).toBe(-2);
    expect(view.getInt32(schema.offset.viewportWidth, true)).toBe(240);
    expect(view.getInt32(schema.offset.scissorHeight, true)).toBe(310);
    expect(view.getUint32(schema.offset.indexCount, true)).toBe(36);
    expect(view.getUint32(schema.offset.instanceCount, true)).toBe(
      instanceCount,
    );
    expect(view.getFloat32(schema.offset.uniform, true)).toBe(0.5);
    expect(
      view.getFloat32(schema.offset.uniform + schema.uniformBytes - 4, true),
    ).toBe(0.75);
    expect(view.getFloat32(schema.headerBytes + 15 * 4, true)).toBe(1);
  });

  it('encodes an ordinary draw as a one-instance packet', () => {
    const raw = m3gWebgpuWireSchema.drawPacket;
    const header = resolveSizedWireLayout(raw.header, 'drawPacket.header');
    const instance = resolveSizedWireLayout(
      raw.instance,
      'drawPacket.instance',
    );
    const schema = {
      commandId: raw.commandId,
      headerBytes: header.bytes,
      instanceBytes: instance.bytes,
      uniformBytes: header.size.uniform,
      offset: header.offset,
    };
    const payload = new Uint8Array(schema.headerBytes + schema.instanceBytes);
    const view = new DataView(payload.buffer);

    view.setUint32(schema.offset.positionHandle, 101, true);
    view.setUint32(schema.offset.positionVersion, 2, true);
    view.setUint32(schema.offset.colorHandle, 102, true);
    view.setUint32(schema.offset.colorVersion, 3, true);
    view.setUint32(schema.offset.indexHandle, 103, true);
    view.setUint32(schema.offset.indexVersion, 4, true);
    view.setUint32(schema.offset.indexCount, 36, true);
    view.setUint32(schema.offset.instanceCount, 1, true);
    view.setFloat32(schema.offset.defaultColor + 3 * 4, 1, true);

    expect(payload.byteLength).toBe(schema.headerBytes + schema.instanceBytes);
    expect(schema.commandId).toBe(179);
    expect(view.getUint32(schema.offset.positionVersion, true)).toBe(2);
    expect(view.getUint32(schema.offset.indexVersion, true)).toBe(4);
    expect(view.getUint32(schema.offset.indexCount, true)).toBe(36);
    expect(view.getUint32(schema.offset.instanceCount, true)).toBe(1);
    expect(view.getFloat32(schema.offset.defaultColor + 3 * 4, true)).toBe(1);
  });

  it('places a frame-owned attribute and index blob before instances', () => {
    const raw = m3gWebgpuWireSchema.drawPacket;
    const header = resolveSizedWireLayout(raw.header, 'drawPacket.header');
    const instance = resolveSizedWireLayout(
      raw.instance,
      'drawPacket.instance',
    );
    const indexCount = 6;
    const vertexCount = 4;
    const frameFlags = 0x01 | 0x02 | 0x08 | 0x10;
    const frameBytes =
      indexCount * 4 +
      vertexCount * 4 * 4 +
      vertexCount * 3 * 4 +
      vertexCount * 4 * 4;
    const payload = new Uint8Array(header.bytes + frameBytes + instance.bytes);
    const view = new DataView(payload.buffer);
    const frameOffset = header.bytes;
    const instanceOffset = frameOffset + frameBytes;

    view.setUint32(header.offset.indexCount, indexCount, true);
    view.setUint32(header.offset.instanceCount, 1, true);
    view.setUint32(header.offset.frameVertexStart, 12, true);
    view.setUint32(header.offset.frameVertexCount, vertexCount, true);
    view.setUint32(header.offset.frameSourceFlags, frameFlags, true);
    view.setUint32(header.offset.frameBytes, frameBytes, true);
    view.setUint32(frameOffset, 0, true);
    view.setUint32(frameOffset + (indexCount - 1) * 4, 3, true);
    view.setFloat32(frameOffset + indexCount * 4, 1.25, true);
    view.setFloat32(instanceOffset + 15 * 4, 1, true);

    expect(payload.byteLength).toBe(header.bytes + frameBytes + instance.bytes);
    expect(view.getUint32(header.offset.frameVertexStart, true)).toBe(12);
    expect(view.getUint32(header.offset.frameVertexCount, true)).toBe(
      vertexCount,
    );
    expect(view.getUint32(header.offset.frameSourceFlags, true)).toBe(
      frameFlags,
    );
    expect(view.getUint32(header.offset.frameBytes, true)).toBe(frameBytes);
    expect(view.getUint32(frameOffset + (indexCount - 1) * 4, true)).toBe(3);
    expect(view.getFloat32(frameOffset + indexCount * 4, true)).toBe(1.25);
    expect(view.getFloat32(instanceOffset + 15 * 4, true)).toBe(1);
  });

  it('keeps the frozen textured uniform inside the packet header', () => {
    const raw = m3gWebgpuWireSchema.drawPacket;
    const header = resolveSizedWireLayout(raw.header, 'drawPacket.header');

    expect(header.offset.uniform).toBe(header.offset.defaultColor + 16);
    expect(header.offset.uniform + header.size.uniform).toBe(header.bytes);
  });

  it('derives every offset and packet size from JSON field sizes', () => {
    const raw = m3gWebgpuWireSchema.drawPacket;
    const header = resolveSizedWireLayout(raw.header, 'drawPacket.header');
    const instance = resolveSizedWireLayout(
      raw.instance,
      'drawPacket.instance',
    );
    const headerBytes = raw.header.reduce(
      (total, [, fieldBytes]) => total + Number(fieldBytes),
      0,
    );
    const instanceBytes = raw.instance.reduce(
      (total, [, fieldBytes]) => total + Number(fieldBytes),
      0,
    );

    expect(header.bytes).toBe(headerBytes);
    expect(instance.bytes).toBe(instanceBytes);
    expect(header.offset.topology).toBe(0);
    expect(header.offset.pipelineKey).toBe(raw.header[0][1]);
    expect(header.offset.uniform).toBe(headerBytes - header.size.uniform);
    expect(header.offset.polygonOffsetFactor).toBe(
      header.offset.texture1MagFilter + header.size.texture1MagFilter,
    );
  });
});
