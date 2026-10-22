#!/usr/bin/env node
// Generates combined/edge-case .m3g test files for M3GLoaderCombinedBoardMIDlet.
// Run: node generate-m3g.cjs

const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

const FILE_ID = Buffer.from([
  0xab, 0x4a, 0x53, 0x52, 0x31, 0x38, 0x34, 0xbb, 0x0d, 0x0a, 0x1a, 0x0a,
]);

class M3GWriter {
  constructor() {
    this.objects = [];
    this.compressed = false;
  }

  addObject(type, data) {
    this.objects.push({ type, data });
  }
  setCompressed(v) {
    this.compressed = v;
  }

  toBuffer() {
    const headerObj = this._makeHeaderObject();
    const headerSection = this._makeSection(headerObj, false);
    const sceneData = Buffer.concat(
      this.objects.map((o) => {
        const hdr = Buffer.alloc(5);
        hdr.writeUInt8(o.type, 0);
        hdr.writeUInt32LE(o.data.length, 1);
        return Buffer.concat([hdr, o.data]);
      }),
    );
    const sceneSection = this._makeSection(sceneData, this.compressed);
    return Buffer.concat([FILE_ID, headerSection, sceneSection]);
  }

  _makeHeaderObject() {
    const buf = Buffer.alloc(50);
    let pos = 0;
    buf.writeUInt8(0, pos);
    pos++;
    const lenPos = pos;
    pos += 4;
    buf.writeUInt8(1, pos);
    pos++; // major
    buf.writeUInt8(0, pos);
    pos++; // minor
    buf.writeUInt8(0, pos);
    pos++; // hasExternalReferences
    buf.writeUInt32LE(0, pos);
    pos += 4; // totalFileSize (unused by our parser)
    buf.writeUInt32LE(0, pos);
    pos += 4; // approximateContentSize
    buf.writeUInt8(0, pos);
    pos++; // authoringField empty
    buf.writeUInt32LE(pos - 5, lenPos);
    return buf.subarray(0, pos);
  }

  _makeSection(objectsData, compressed) {
    const uncompressedLen = objectsData.length;
    const payload = compressed ? zlib.deflateSync(objectsData) : objectsData;
    const totalLen = 1 + 4 + 4 + payload.length + 4;
    const buf = Buffer.alloc(totalLen);
    let pos = 0;
    buf.writeUInt8(compressed ? 1 : 0, pos);
    pos++;
    buf.writeUInt32LE(totalLen, pos);
    pos += 4;
    buf.writeUInt32LE(uncompressedLen, pos);
    pos += 4;
    payload.copy(buf, pos);
    pos += payload.length;
    buf.writeUInt32LE(adler32(buf.subarray(0, pos)), pos);
    return buf;
  }
}

function adler32(data) {
  let a = 1,
    b = 0;
  for (let i = 0; i < data.length; i++) {
    a = (a + data[i]) % 65521;
    b = (b + a) % 65521;
  }
  return ((b << 16) | a) >>> 0;
}

// --- Builders ---

function obj3dBase(userID = 0) {
  const b = Buffer.alloc(12);
  b.writeInt32LE(userID, 0);
  b.writeUInt32LE(0, 4); // animTracks = 0
  b.writeUInt32LE(0, 8); // userParams = 0
  return b;
}

function objRef(ref) {
  // M3G ObjectIndex counts the Header object. Test case builders use
  // scene-object numbers, so non-null references need one extra slot.
  return ref === 0 ? 0 : ref + 1;
}

function transformableIdentity() {
  return Buffer.from([0, 0]);
}

function nodeDefaults() {
  const b = Buffer.alloc(8);
  b.writeUInt8(1, 0); // enableRendering
  b.writeUInt8(1, 1); // enablePicking
  b.writeUInt8(0xff, 2); // alphaFactor
  b.writeUInt32LE(0xffffffff, 3); // scope
  b.writeUInt8(0, 7); // hasAlignment = false
  return b;
}

function vertexArray(compSize, compCount, vertCount, values) {
  const bufs = [obj3dBase()];
  const hdr = Buffer.alloc(5);
  hdr.writeUInt8(compSize, 0);
  hdr.writeUInt8(compCount, 1);
  hdr.writeUInt8(0, 2); // encoding raw
  hdr.writeUInt16LE(vertCount, 3);
  bufs.push(hdr);
  if (compSize === 1) {
    bufs.push(Buffer.from(values.map((v) => v & 0xff)));
  } else {
    const d = Buffer.alloc(values.length * 2);
    for (let i = 0; i < values.length; i++) d.writeInt16LE(values[i], i * 2);
    bufs.push(d);
  }
  return Buffer.concat(bufs);
}

function vertexBuffer(posRef, bias, scale, normRef, colRef, tcArrays) {
  const bufs = [obj3dBase()];
  // defaultColor RGBA
  bufs.push(Buffer.from([0xff, 0xff, 0xff, 0xff]));
  const b = Buffer.alloc(4 + 16 + 4 + 4 + 4);
  let p = 0;
  b.writeUInt32LE(objRef(posRef), p);
  p += 4;
  b.writeFloatLE(bias[0], p);
  p += 4;
  b.writeFloatLE(bias[1], p);
  p += 4;
  b.writeFloatLE(bias[2], p);
  p += 4;
  b.writeFloatLE(scale, p);
  p += 4;
  b.writeUInt32LE(objRef(normRef), p);
  p += 4;
  b.writeUInt32LE(objRef(colRef), p);
  p += 4;
  const tcCount = tcArrays ? tcArrays.length : 0;
  b.writeUInt32LE(tcCount, p);
  p += 4;
  bufs.push(b.subarray(0, p));
  if (tcArrays) {
    for (const tc of tcArrays) {
      const t = Buffer.alloc(20);
      t.writeUInt32LE(objRef(tc.ref), 0);
      t.writeFloatLE(tc.bias[0], 4);
      t.writeFloatLE(tc.bias[1], 8);
      t.writeFloatLE(tc.bias[2], 12);
      t.writeFloatLE(tc.scale, 16);
      bufs.push(t);
    }
  }
  return Buffer.concat(bufs);
}

function triStripImplicit(start, strips) {
  const bufs = [obj3dBase()];
  const b = Buffer.alloc(1 + 4 + 4 + strips.length * 4);
  let p = 0;
  b.writeUInt8(0, p);
  p++; // encoding=0 implicit u32
  b.writeUInt32LE(start, p);
  p += 4;
  b.writeUInt32LE(strips.length, p);
  p += 4;
  for (const s of strips) {
    b.writeUInt32LE(s, p);
    p += 4;
  }
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function _triStripExplicit(indices, strips) {
  const bufs = [obj3dBase()];
  const b = Buffer.alloc(
    1 + 4 + 4 + indices.length * 4 + 4 + strips.length * 4,
  );
  let p = 0;
  b.writeUInt8(128, p);
  p++; // encoding=128 explicit u32
  b.writeUInt32LE(indices.length, p);
  p += 4; // count
  for (const idx of indices) {
    b.writeUInt32LE(idx, p);
    p += 4;
  }
  b.writeUInt32LE(strips.length, p);
  p += 4;
  for (const s of strips) {
    b.writeUInt32LE(s, p);
    p += 4;
  }
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function material(ambient, diffuse, emissive, specular, shininess) {
  const bufs = [obj3dBase()];
  const b = Buffer.alloc(18);
  let p = 0;
  b.writeUInt8((ambient >> 16) & 0xff, p++);
  b.writeUInt8((ambient >> 8) & 0xff, p++);
  b.writeUInt8(ambient & 0xff, p++);
  b.writeUInt8((diffuse >> 16) & 0xff, p++);
  b.writeUInt8((diffuse >> 8) & 0xff, p++);
  b.writeUInt8(diffuse & 0xff, p++);
  b.writeUInt8((diffuse >> 24) & 0xff, p++);
  b.writeUInt8((emissive >> 16) & 0xff, p++);
  b.writeUInt8((emissive >> 8) & 0xff, p++);
  b.writeUInt8(emissive & 0xff, p++);
  b.writeUInt8((specular >> 16) & 0xff, p++);
  b.writeUInt8((specular >> 8) & 0xff, p++);
  b.writeUInt8(specular & 0xff, p++);
  b.writeFloatLE(shininess, p);
  p += 4;
  b.writeUInt8(0, p);
  p++; // vertexColorTracking = false
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function appearance(compRef, fogRef, polyRef, matRef, texRefs) {
  const bufs = [obj3dBase()];
  const texCount = texRefs ? texRefs.length : 0;
  const b = Buffer.alloc(1 + 20 + 4 + texCount * 4);
  let p = 0;
  b.writeUInt8(0, p);
  p++; // layer
  b.writeUInt32LE(objRef(compRef), p);
  p += 4;
  b.writeUInt32LE(objRef(fogRef), p);
  p += 4;
  b.writeUInt32LE(objRef(polyRef), p);
  p += 4;
  b.writeUInt32LE(objRef(matRef), p);
  p += 4;
  b.writeUInt32LE(texCount, p);
  p += 4;
  if (texRefs)
    for (const r of texRefs) {
      b.writeUInt32LE(objRef(r), p);
      p += 4;
    }
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function compositingMode(depthTest, depthWrite, blending, alphaThresh) {
  const bufs = [obj3dBase()];
  const b = Buffer.alloc(10);
  let p = 0;
  b.writeUInt8(depthTest ? 1 : 0, p++);
  b.writeUInt8(depthWrite ? 1 : 0, p++);
  b.writeUInt8(1, p++); // colorWrite
  b.writeUInt8(1, p++); // alphaWrite
  b.writeUInt8(blending, p++); // blending mode (64=REPLACE, 65=ALPHA, etc)
  b.writeUInt8(alphaThresh, p++);
  b.writeFloatLE(0.0, p);
  p += 4; // depthOffsetFactor
  // need depthOffsetUnits too
  bufs.push(b.subarray(0, p));
  // depthOffsetUnits
  const u = Buffer.alloc(4);
  u.writeFloatLE(0.0, 0);
  bufs.push(u);
  return Buffer.concat(bufs);
}

function polygonMode(culling, shading, winding) {
  const bufs = [obj3dBase()];
  const b = Buffer.alloc(6);
  b.writeUInt8(culling, 0); // 160=CULL_BACK, 161=CULL_FRONT, 162=CULL_NONE
  b.writeUInt8(shading, 1); // 164=SHADE_FLAT, 165=SHADE_SMOOTH
  b.writeUInt8(winding, 2); // 168=WINDING_CCW, 169=WINDING_CW
  b.writeUInt8(0, 3); // twoSidedLighting = false
  b.writeUInt8(0, 4); // localCameraLighting = false
  b.writeUInt8(1, 5); // perspectiveCorrection = true
  bufs.push(b);
  return Buffer.concat(bufs);
}

function image2D(format, width, height, pixels) {
  // format: 96=ALPHA, 97=LUMINANCE, 98=LUMINANCE_ALPHA, 99=RGB, 100=RGBA
  const bufs = [obj3dBase()];
  const hdr = Buffer.alloc(10);
  hdr.writeUInt8(format, 0);
  hdr.writeUInt8(0, 1); // isMutable = false
  hdr.writeUInt32LE(width, 2);
  hdr.writeUInt32LE(height, 6);
  bufs.push(hdr);
  // palette (empty)
  const palLen = Buffer.alloc(4);
  palLen.writeUInt32LE(0, 0);
  bufs.push(palLen);
  // pixels
  const pixLen = Buffer.alloc(4);
  pixLen.writeUInt32LE(pixels.length, 0);
  bufs.push(pixLen);
  bufs.push(Buffer.from(pixels));
  return Buffer.concat(bufs);
}

function texture2D(imageRef, blending, wrapS, wrapT) {
  // blending: 228=MODULATE, 229=REPLACE, etc.
  // wrap: 240=WRAP_CLAMP, 241=WRAP_REPEAT
  const bufs = [obj3dBase()];
  bufs.push(transformableIdentity());
  const b = Buffer.alloc(4 + 3 + 5);
  let p = 0;
  b.writeUInt32LE(objRef(imageRef), p);
  p += 4;
  // blendColor RGB
  b.writeUInt8(0, p++);
  b.writeUInt8(0, p++);
  b.writeUInt8(0, p++);
  b.writeUInt8(blending, p++);
  b.writeUInt8(wrapS, p++);
  b.writeUInt8(wrapT, p++);
  b.writeUInt8(209, p++); // levelFilter: BASE_LEVEL=209
  b.writeUInt8(210, p++); // imageFilter: NEAREST=210 (actually 210=NEAREST, 211=LINEAR)
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function mesh(vbRef, submeshes) {
  const bufs = [obj3dBase(), transformableIdentity(), nodeDefaults()];
  const b = Buffer.alloc(4 + 4 + submeshes.length * 8);
  let p = 0;
  b.writeUInt32LE(objRef(vbRef), p);
  p += 4;
  b.writeUInt32LE(submeshes.length, p);
  p += 4;
  for (const sm of submeshes) {
    b.writeUInt32LE(objRef(sm.idx), p);
    p += 4;
    b.writeUInt32LE(objRef(sm.app), p);
    p += 4;
  }
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function meshWithTransform(vbRef, submeshes, tx, ty, tz) {
  const bufs = [obj3dBase()];
  // hasComponentTransform = true
  const tBuf = Buffer.alloc(2 + 40);
  let p = 0;
  tBuf.writeUInt8(1, p);
  p++; // hasComponentTransform
  // translation
  tBuf.writeFloatLE(tx, p);
  p += 4;
  tBuf.writeFloatLE(ty, p);
  p += 4;
  tBuf.writeFloatLE(tz, p);
  p += 4;
  // scale (1,1,1)
  tBuf.writeFloatLE(1, p);
  p += 4;
  tBuf.writeFloatLE(1, p);
  p += 4;
  tBuf.writeFloatLE(1, p);
  p += 4;
  // orientation (0 degrees around 0,0,1)
  tBuf.writeFloatLE(0, p);
  p += 4;
  tBuf.writeFloatLE(0, p);
  p += 4;
  tBuf.writeFloatLE(0, p);
  p += 4;
  tBuf.writeFloatLE(1, p);
  p += 4;
  tBuf.writeUInt8(0, p);
  p++; // hasGeneralTransform = false
  bufs.push(tBuf.subarray(0, p));
  bufs.push(nodeDefaults());
  const b = Buffer.alloc(4 + 4 + submeshes.length * 8);
  p = 0;
  b.writeUInt32LE(objRef(vbRef), p);
  p += 4;
  b.writeUInt32LE(submeshes.length, p);
  p += 4;
  for (const sm of submeshes) {
    b.writeUInt32LE(objRef(sm.idx), p);
    p += 4;
    b.writeUInt32LE(objRef(sm.app), p);
    p += 4;
  }
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function _camera(projType, fovy, aspect, near, far) {
  const bufs = [obj3dBase(), transformableIdentity(), nodeDefaults()];
  const b = Buffer.alloc(17);
  let p = 0;
  b.writeUInt8(projType, p);
  p++;
  b.writeFloatLE(fovy, p);
  p += 4;
  b.writeFloatLE(aspect, p);
  p += 4;
  b.writeFloatLE(near, p);
  p += 4;
  b.writeFloatLE(far, p);
  p += 4;
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function cameraWithTranslation(projType, fovy, aspect, near, far, tx, ty, tz) {
  const bufs = [obj3dBase()];
  // hasComponentTransform with translation
  const tBuf = Buffer.alloc(42);
  let p = 0;
  tBuf.writeUInt8(1, p);
  p++;
  tBuf.writeFloatLE(tx, p);
  p += 4;
  tBuf.writeFloatLE(ty, p);
  p += 4;
  tBuf.writeFloatLE(tz, p);
  p += 4;
  tBuf.writeFloatLE(1, p);
  p += 4;
  tBuf.writeFloatLE(1, p);
  p += 4;
  tBuf.writeFloatLE(1, p);
  p += 4;
  tBuf.writeFloatLE(0, p);
  p += 4;
  tBuf.writeFloatLE(0, p);
  p += 4;
  tBuf.writeFloatLE(0, p);
  p += 4;
  tBuf.writeFloatLE(1, p);
  p += 4;
  tBuf.writeUInt8(0, p);
  p++;
  bufs.push(tBuf.subarray(0, p));
  bufs.push(nodeDefaults());
  const b = Buffer.alloc(17);
  p = 0;
  b.writeUInt8(projType, p);
  p++;
  b.writeFloatLE(fovy, p);
  p += 4;
  b.writeFloatLE(aspect, p);
  p += 4;
  b.writeFloatLE(near, p);
  p += 4;
  b.writeFloatLE(far, p);
  p += 4;
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function background(color, colorClear, depthClear) {
  const bufs = [obj3dBase()];
  const b = Buffer.alloc(26);
  let p = 0;
  b.writeUInt8((color >> 16) & 0xff, p++);
  b.writeUInt8((color >> 8) & 0xff, p++);
  b.writeUInt8(color & 0xff, p++);
  b.writeUInt8((color >> 24) & 0xff, p++);
  b.writeUInt32LE(0, p);
  p += 4; // imageRef null
  b.writeUInt8(32, p);
  p++;
  b.writeUInt8(32, p);
  p++; // imageModeX/Y = BORDER
  b.writeInt32LE(0, p);
  p += 4;
  b.writeInt32LE(0, p);
  p += 4;
  b.writeInt32LE(0, p);
  p += 4;
  b.writeInt32LE(0, p);
  p += 4;
  bufs.push(b.subarray(0, p));
  // depthClearEnabled, colorClearEnabled (note spec order)
  bufs.push(Buffer.from([depthClear ? 1 : 0, colorClear ? 1 : 0]));
  return Buffer.concat(bufs);
}

function light(mode, color, intensity) {
  const bufs = [obj3dBase(), transformableIdentity(), nodeDefaults()];
  const b = Buffer.alloc(28);
  let p = 0;
  b.writeFloatLE(1.0, p);
  p += 4;
  b.writeFloatLE(0, p);
  p += 4;
  b.writeFloatLE(0, p);
  p += 4;
  b.writeUInt8((color >> 16) & 0xff, p++);
  b.writeUInt8((color >> 8) & 0xff, p++);
  b.writeUInt8(color & 0xff, p++);
  b.writeUInt8(mode, p++);
  b.writeFloatLE(intensity, p);
  p += 4;
  b.writeFloatLE(45, p);
  p += 4;
  b.writeFloatLE(0, p);
  p += 4;
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function group(children) {
  const bufs = [obj3dBase(), transformableIdentity(), nodeDefaults()];
  const b = Buffer.alloc(4 + children.length * 4);
  let p = 0;
  b.writeUInt32LE(children.length, p);
  p += 4;
  for (const c of children) {
    b.writeUInt32LE(objRef(c), p);
    p += 4;
  }
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

function world(children, camRef, bgRef) {
  const bufs = [obj3dBase(), transformableIdentity(), nodeDefaults()];
  const b = Buffer.alloc(4 + children.length * 4 + 8);
  let p = 0;
  b.writeUInt32LE(children.length, p);
  p += 4;
  for (const c of children) {
    b.writeUInt32LE(objRef(c), p);
    p += 4;
  }
  b.writeUInt32LE(objRef(camRef), p);
  p += 4;
  b.writeUInt32LE(objRef(bgRef), p);
  p += 4;
  bufs.push(b.subarray(0, p));
  return Buffer.concat(bufs);
}

// ========== Test cases ==========

function genTexturedMesh() {
  // Textured quad (2 triangles) with a 4x4 checkerboard pattern RGB image
  const w = new M3GWriter();
  // 1: positions (4 verts for a quad)
  w.addObject(
    20,
    vertexArray(2, 3, 4, [-60, -60, 0, 60, -60, 0, -60, 60, 0, 60, 60, 0]),
  );
  // 2: texcoords (0,0 -> 1,1 mapped as short*scale)
  w.addObject(20, vertexArray(2, 2, 4, [0, 0, 256, 0, 0, 256, 256, 256]));
  // 3: VB with texcoord (scale=1/256 so shorts 0-256 map to 0.0-1.0)
  w.addObject(
    21,
    vertexBuffer(1, [0, 0, 0], 1.0, 0, 0, [
      { ref: 2, bias: [0, 0, 0], scale: 1.0 / 256 },
    ]),
  );
  // 4: TSA (strip of 4 = 2 triangles)
  w.addObject(11, triStripImplicit(0, [4]));
  // 5: Image2D (4x4 RGB checkerboard)
  const pixels = [];
  for (let y = 0; y < 4; y++)
    for (let x = 0; x < 4; x++) {
      const c = (x + y) % 2 === 0 ? [255, 200, 0] : [0, 50, 200];
      pixels.push(...c);
    }
  w.addObject(10, image2D(99, 4, 4, pixels)); // 99 = RGB
  // 6: Texture2D (MODULATE blending)
  w.addObject(17, texture2D(5, 228, 241, 241)); // 228=MODULATE, 241=WRAP_REPEAT
  // 7: Appearance with texture
  w.addObject(3, appearance(0, 0, 0, 0, [6]));
  // 8: Mesh
  w.addObject(14, mesh(3, [{ idx: 4, app: 7 }]));
  return w.toBuffer();
}

function genMultiSubmesh() {
  // One mesh with 2 submeshes (different colored materials)
  const w = new M3GWriter();
  // 1: positions (6 verts: 2 triangles side by side)
  w.addObject(
    20,
    vertexArray(2, 3, 6, [
      -80,
      60,
      0,
      -80,
      -60,
      0,
      -10,
      -60,
      0, // left triangle
      10,
      60,
      0,
      10,
      -60,
      0,
      80,
      -60,
      0, // right triangle
    ]),
  );
  // 2: normals
  w.addObject(
    20,
    vertexArray(
      1,
      3,
      6,
      [0, 0, 127, 0, 0, 127, 0, 0, 127, 0, 0, 127, 0, 0, 127, 0, 0, 127],
    ),
  );
  // 3: VB
  w.addObject(21, vertexBuffer(1, [0, 0, 0], 1.0, 2, 0, null));
  // 4: TSA for first submesh (verts 0-2)
  w.addObject(11, triStripImplicit(0, [3]));
  // 5: TSA for second submesh (verts 3-5)
  w.addObject(11, triStripImplicit(3, [3]));
  // 6: Material red
  w.addObject(13, material(0x333333, 0xffff0000, 0x000000, 0xffffff, 5.0));
  // 7: Material blue
  w.addObject(13, material(0x333333, 0xff0000ff, 0x000000, 0xffffff, 5.0));
  // 8: Appearance red
  w.addObject(3, appearance(0, 0, 0, 6, null));
  // 9: Appearance blue
  w.addObject(3, appearance(0, 0, 0, 7, null));
  // 10: Mesh with 2 submeshes
  w.addObject(
    14,
    mesh(3, [
      { idx: 4, app: 8 },
      { idx: 5, app: 9 },
    ]),
  );
  // 11: Light
  w.addObject(12, light(129, 0xffffff, 1.0));
  // 12: Camera
  w.addObject(5, cameraWithTranslation(50, 60.0, 1.0, 1.0, 1000.0, 0, 0, 200));
  // 13: Background
  w.addObject(4, background(0xff181818, true, true));
  // 14: World
  w.addObject(22, world([10, 11, 12], 12, 13));
  return w.toBuffer();
}

function genNestedGroups() {
  // World > Group > Mesh (tests Group hierarchy with transform)
  const w = new M3GWriter();
  // 1: positions
  w.addObject(20, vertexArray(2, 3, 3, [0, 50, 0, -40, -40, 0, 40, -40, 0]));
  // 2: VB
  w.addObject(21, vertexBuffer(1, [0, 0, 0], 1.0, 0, 0, null));
  // 3: TSA
  w.addObject(11, triStripImplicit(0, [3]));
  // 4: Appearance
  w.addObject(3, appearance(0, 0, 0, 0, null));
  // 5: Mesh (translated left)
  w.addObject(14, meshWithTransform(2, [{ idx: 3, app: 4 }], -40, 0, 0));
  // 6: Mesh (translated right)
  w.addObject(14, meshWithTransform(2, [{ idx: 3, app: 4 }], 40, 0, 0));
  // 7: Group containing both meshes
  w.addObject(9, group([5, 6]));
  // 8: Camera
  w.addObject(5, cameraWithTranslation(50, 60.0, 1.0, 1.0, 1000.0, 0, 0, 200));
  // 9: Background
  w.addObject(4, background(0xff002200, true, true));
  // 10: World (group + camera)
  w.addObject(22, world([7, 8], 8, 9));
  return w.toBuffer();
}

function genFullAppearance() {
  // Mesh with CompositingMode + PolygonMode + Material (full Appearance chain)
  const w = new M3GWriter();
  // 1: positions (quad)
  w.addObject(
    20,
    vertexArray(2, 3, 4, [-50, -50, 0, 50, -50, 0, -50, 50, 0, 50, 50, 0]),
  );
  // 2: normals
  w.addObject(
    20,
    vertexArray(1, 3, 4, [0, 0, 127, 0, 0, 127, 0, 0, 127, 0, 0, 127]),
  );
  // 3: VB
  w.addObject(21, vertexBuffer(1, [0, 0, 0], 1.0, 2, 0, null));
  // 4: TSA
  w.addObject(11, triStripImplicit(0, [4]));
  // 5: CompositingMode (depth test on, REPLACE blending)
  w.addObject(6, compositingMode(true, true, 64, 0));
  // 6: PolygonMode (CULL_BACK, SMOOTH, CCW)
  w.addObject(8, polygonMode(160, 165, 168));
  // 7: Material (green)
  w.addObject(13, material(0x333333, 0xff00cc00, 0x000000, 0xffffff, 20.0));
  // 8: Appearance with all components
  w.addObject(3, appearance(5, 0, 6, 7, null));
  // 9: Mesh
  w.addObject(14, mesh(3, [{ idx: 4, app: 8 }]));
  // 10: Light
  w.addObject(12, light(129, 0xffffff, 1.0));
  // 11: Camera
  w.addObject(5, cameraWithTranslation(50, 60.0, 1.0, 1.0, 1000.0, 0, 0, 200));
  // 12: Background
  w.addObject(4, background(0xff000033, true, true));
  // 13: World
  w.addObject(22, world([9, 10, 11], 11, 12));
  return w.toBuffer();
}

function genCompressedSection() {
  // Same as single triangle but with zlib compressed section
  const w = new M3GWriter();
  w.setCompressed(true);
  w.addObject(20, vertexArray(2, 3, 3, [0, 80, 0, -60, -60, 0, 60, -60, 0]));
  w.addObject(21, vertexBuffer(1, [0, 0, 0], 1.0, 0, 0, null));
  w.addObject(11, triStripImplicit(0, [3]));
  w.addObject(3, appearance(0, 0, 0, 0, null));
  w.addObject(14, mesh(2, [{ idx: 3, app: 4 }]));
  return w.toBuffer();
}

function genEmptyWorld() {
  // World with camera and background but NO mesh children (edge case)
  const w = new M3GWriter();
  // 1: Camera
  w.addObject(5, cameraWithTranslation(50, 60.0, 1.0, 1.0, 1000.0, 0, 0, 200));
  // 2: Background (bright magenta - visible if clear works)
  w.addObject(4, background(0xffff00ff, true, true));
  // 3: World (no mesh children, just camera)
  w.addObject(22, world([1], 1, 2));
  return w.toBuffer();
}

function genScaleBias() {
  // Tests VertexBuffer positionScale and positionBias
  // Positions stored as small integers, scaled/biased to real coords
  const w = new M3GWriter();
  // 1: positions stored as bytes, will be scaled by 2.0 and biased by (-50, -50, 0)
  w.addObject(20, vertexArray(1, 3, 3, [50, 100, 0, 0, 0, 0, 100, 0, 0]));
  // 2: VB with scale=2.0, bias=[-100, -100, 0]
  w.addObject(21, vertexBuffer(1, [-100, -100, 0], 2.0, 0, 0, null));
  // 3: TSA
  w.addObject(11, triStripImplicit(0, [3]));
  // 4: Appearance
  w.addObject(3, appearance(0, 0, 0, 0, null));
  // 5: Mesh
  w.addObject(14, mesh(2, [{ idx: 3, app: 4 }]));
  return w.toBuffer();
}

function genOffsetBytes() {
  // Valid M3G data starts at a non-zero byte offset in the resource.
  const w = new M3GWriter();
  w.addObject(20, vertexArray(2, 3, 3, [0, 70, 0, -55, -55, 0, 55, -55, 0]));
  w.addObject(21, vertexBuffer(1, [0, 0, 0], 1.0, 0, 0, null));
  w.addObject(11, triStripImplicit(0, [3]));
  w.addObject(3, appearance(0, 0, 0, 0, null));
  w.addObject(14, mesh(2, [{ idx: 3, app: 4 }]));
  return Buffer.concat([
    Buffer.from([0x4f, 0x46, 0x46, 0x53, 0x45, 0x54, 0x21]),
    w.toBuffer(),
  ]);
}

// ========== Write ==========
const outDir = __dirname;
const files = {
  'textured_mesh.m3g': genTexturedMesh(),
  'multi_submesh.m3g': genMultiSubmesh(),
  'nested_groups.m3g': genNestedGroups(),
  'full_appearance.m3g': genFullAppearance(),
  'compressed.m3g': genCompressedSection(),
  'empty_world.m3g': genEmptyWorld(),
  'scale_bias.m3g': genScaleBias(),
  'offset_bytes.m3g': genOffsetBytes(),
};

for (const [name, buf] of Object.entries(files)) {
  fs.writeFileSync(path.join(outDir, name), buf);
  console.log(`wrote ${name} (${buf.length} bytes)`);
}
console.log('done');
