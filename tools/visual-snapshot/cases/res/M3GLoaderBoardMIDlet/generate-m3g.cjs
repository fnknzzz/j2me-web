#!/usr/bin/env node
// Generates .m3g test files for M3GLoaderBoardMIDlet.
// Run: node generate-m3g.cjs

const fs = require('node:fs');
const path = require('node:path');

const FILE_ID = Buffer.from([
  0xab, 0x4a, 0x53, 0x52, 0x31, 0x38, 0x34, 0xbb, 0x0d, 0x0a, 0x1a, 0x0a,
]);

class M3GWriter {
  constructor() {
    this.objects = [];
  }
  addObject(type, data) {
    this.objects.push({ type, data });
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
    const sceneSection = this._makeSection(sceneData, false);
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
    pos++;
    buf.writeUInt8(0, pos);
    pos++;
    buf.writeUInt8(0, pos);
    pos++;
    buf.writeUInt32LE(0, pos);
    pos += 4;
    buf.writeUInt32LE(0, pos);
    pos += 4;
    buf.writeUInt8(0, pos);
    pos++;
    buf.writeUInt32LE(pos - 5, lenPos);
    return buf.subarray(0, pos);
  }
  _makeSection(objectsData, _compressed) {
    const totalLen = 1 + 4 + 4 + objectsData.length + 4;
    const buf = Buffer.alloc(totalLen);
    let pos = 0;
    buf.writeUInt8(0, pos);
    pos++;
    buf.writeUInt32LE(totalLen, pos);
    pos += 4;
    buf.writeUInt32LE(objectsData.length, pos);
    pos += 4;
    objectsData.copy(buf, pos);
    pos += objectsData.length;
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
  b.writeUInt32LE(0, 4);
  b.writeUInt32LE(0, 8);
  return b;
}

function transformableIdentity() {
  return Buffer.from([0, 0]);
}

function nodeDefaults() {
  const b = Buffer.alloc(8);
  b.writeUInt8(1, 0);
  b.writeUInt8(1, 1);
  b.writeUInt8(0xff, 2);
  b.writeUInt32LE(0xffffffff, 3);
  b.writeUInt8(0, 7);
  return b;
}

// color param is 0xAARRGGBB, file stores RGBA
function colorToRGBA(color) {
  const r = (color >> 16) & 0xff;
  const g = (color >> 8) & 0xff;
  const b = color & 0xff;
  const a = (color >> 24) & 0xff;
  return Buffer.from([r, g, b, a]);
}

function objRef(ref) {
  // M3G ObjectIndex counts the Header object. Test case builders use
  // scene-object numbers, so non-null references need one extra slot.
  return ref === 0 ? 0 : ref + 1;
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
  bufs.push(colorToRGBA(0xffffffff)); // defaultColor white
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
  p++;
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

function triStripExplicit(indices, strips) {
  const bufs = [obj3dBase()];
  const b = Buffer.alloc(1 + 4 + indices.length * 4 + 4 + strips.length * 4);
  let p = 0;
  b.writeUInt8(128, p);
  p++;
  b.writeUInt32LE(indices.length, p);
  p += 4;
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

function appearance(compRef, fogRef, polyRef, matRef, texRefs) {
  const bufs = [obj3dBase()];
  const texCount = texRefs ? texRefs.length : 0;
  const b = Buffer.alloc(1 + 20 + 4 + texCount * 4);
  let p = 0;
  b.writeUInt8(0, p);
  p++;
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

function material(ambient, diffuse, emissive, specular, shininess) {
  const bufs = [obj3dBase()];
  const b = Buffer.alloc(18);
  let p = 0;
  // ambient RGB
  b.writeUInt8((ambient >> 16) & 0xff, p++);
  b.writeUInt8((ambient >> 8) & 0xff, p++);
  b.writeUInt8(ambient & 0xff, p++);
  // diffuse RGBA
  b.writeUInt8((diffuse >> 16) & 0xff, p++);
  b.writeUInt8((diffuse >> 8) & 0xff, p++);
  b.writeUInt8(diffuse & 0xff, p++);
  b.writeUInt8((diffuse >> 24) & 0xff, p++);
  // emissive RGB
  b.writeUInt8((emissive >> 16) & 0xff, p++);
  b.writeUInt8((emissive >> 8) & 0xff, p++);
  b.writeUInt8(emissive & 0xff, p++);
  // specular RGB
  b.writeUInt8((specular >> 16) & 0xff, p++);
  b.writeUInt8((specular >> 8) & 0xff, p++);
  b.writeUInt8(specular & 0xff, p++);
  b.writeFloatLE(shininess, p);
  p += 4;
  b.writeUInt8(0, p);
  p++; // vertexColorTracking
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

function meshWithOrientation(vbRef, submeshes, angle, ax, ay, az) {
  const bufs = [obj3dBase()];
  const tBuf = Buffer.alloc(42);
  let p = 0;
  tBuf.writeUInt8(1, p);
  p++;
  tBuf.writeFloatLE(0, p);
  p += 4;
  tBuf.writeFloatLE(0, p);
  p += 4;
  tBuf.writeFloatLE(0, p);
  p += 4;
  tBuf.writeFloatLE(1, p);
  p += 4;
  tBuf.writeFloatLE(1, p);
  p += 4;
  tBuf.writeFloatLE(1, p);
  p += 4;
  tBuf.writeFloatLE(angle, p);
  p += 4;
  tBuf.writeFloatLE(ax, p);
  p += 4;
  tBuf.writeFloatLE(ay, p);
  p += 4;
  tBuf.writeFloatLE(az, p);
  p += 4;
  tBuf.writeUInt8(0, p);
  p++;
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
  p++; // imageModeX/Y
  b.writeInt32LE(0, p);
  p += 4;
  b.writeInt32LE(0, p);
  p += 4;
  b.writeInt32LE(0, p);
  p += 4;
  b.writeInt32LE(0, p);
  p += 4;
  bufs.push(b.subarray(0, p));
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

function genSingleTriangle() {
  // Minimal: 1 VertexArray + 1 VB + 1 TSA + 1 Appearance + 1 Mesh (no World)
  const w = new M3GWriter();
  // 1: positions (3 verts)
  w.addObject(20, vertexArray(2, 3, 3, [0, 80, 0, -60, -60, 0, 60, -60, 0]));
  // 2: VB
  w.addObject(21, vertexBuffer(1, [0, 0, 0], 1.0, 0, 0, null));
  // 3: TSA
  w.addObject(11, triStripImplicit(0, [3]));
  // 4: Appearance
  w.addObject(3, appearance(0, 0, 0, 0, null));
  // 5: Mesh
  w.addObject(14, mesh(2, [{ idx: 3, app: 4 }]));
  return w.toBuffer();
}

function genColorTriangle() {
  // Vertex colors: positions + colors + VB + TSA + Appearance + Mesh
  const w = new M3GWriter();
  // 1: positions
  w.addObject(20, vertexArray(2, 3, 3, [0, 80, 0, -60, -60, 0, 60, -60, 0]));
  // 2: colors (RGB bytes: red, green, blue)
  w.addObject(20, vertexArray(1, 3, 3, [255, 0, 0, 0, 255, 0, 0, 0, 255]));
  // 3: VB with colors
  w.addObject(21, vertexBuffer(1, [0, 0, 0], 1.0, 0, 2, null));
  // 4: TSA
  w.addObject(11, triStripImplicit(0, [3]));
  // 5: Appearance
  w.addObject(3, appearance(0, 0, 0, 0, null));
  // 6: Mesh
  w.addObject(14, mesh(3, [{ idx: 4, app: 5 }]));
  return w.toBuffer();
}

function genWorldCamera() {
  // World with Camera, Background, and a simple triangle Mesh
  const w = new M3GWriter();
  // 1: positions
  w.addObject(20, vertexArray(2, 3, 3, [0, 50, 0, -40, -40, 0, 40, -40, 0]));
  // 2: VB
  w.addObject(21, vertexBuffer(1, [0, 0, 0], 1.0, 0, 0, null));
  // 3: TSA
  w.addObject(11, triStripImplicit(0, [3]));
  // 4: Appearance
  w.addObject(3, appearance(0, 0, 0, 0, null));
  // 5: Mesh
  w.addObject(14, mesh(2, [{ idx: 3, app: 4 }]));
  // 6: Camera (perspective, translated back on Z)
  w.addObject(5, cameraWithTranslation(50, 60.0, 1.0, 1.0, 1000.0, 0, 0, 200));
  // 7: Background (dark blue)
  w.addObject(4, background(0xff000044, true, true));
  // 8: World (mesh + camera children)
  w.addObject(22, world([5, 6], 6, 7));
  return w.toBuffer();
}

function genMaterialLit() {
  // World with Material + Light
  const w = new M3GWriter();
  // 1: positions
  w.addObject(20, vertexArray(2, 3, 3, [0, 60, 0, -50, -50, 0, 50, -50, 0]));
  // 2: normals (all facing Z+)
  w.addObject(20, vertexArray(1, 3, 3, [0, 0, 127, 0, 0, 127, 0, 0, 127]));
  // 3: VB
  w.addObject(21, vertexBuffer(1, [0, 0, 0], 1.0, 2, 0, null));
  // 4: TSA
  w.addObject(11, triStripImplicit(0, [3]));
  // 5: Material (red diffuse)
  w.addObject(13, material(0x333333, 0xffff3333, 0x000000, 0xffffff, 10.0));
  // 6: Appearance with material
  w.addObject(3, appearance(0, 0, 0, 5, null));
  // 7: Mesh
  w.addObject(14, mesh(3, [{ idx: 4, app: 6 }]));
  // 8: Light (directional, white)
  w.addObject(12, light(129, 0xffffff, 1.0));
  // 9: Camera
  w.addObject(5, cameraWithTranslation(50, 60.0, 1.0, 1.0, 1000.0, 0, 0, 200));
  // 10: Background (dark green)
  w.addObject(4, background(0xff002200, true, true));
  // 11: World
  w.addObject(22, world([7, 8, 9], 9, 10));
  return w.toBuffer();
}

function genCubeWorld() {
  // Cube with vertex colors in a World
  const w = new M3GWriter();
  // 8 vertices of a 50-unit cube centered at origin
  const s = 50;
  // 1: positions
  w.addObject(
    20,
    vertexArray(2, 3, 8, [
      -s,
      -s,
      -s,
      s,
      -s,
      -s,
      -s,
      s,
      -s,
      s,
      s,
      -s,
      -s,
      -s,
      s,
      s,
      -s,
      s,
      -s,
      s,
      s,
      s,
      s,
      s,
    ]),
  );
  // 2: colors (one per vertex)
  w.addObject(
    20,
    vertexArray(
      1,
      3,
      8,
      [
        255, 0, 0, 0, 255, 0, 0, 0, 255, 255, 255, 0, 255, 0, 255, 0, 255, 255,
        128, 128, 128, 255, 255, 255,
      ],
    ),
  );
  // 3: VB with colors
  w.addObject(21, vertexBuffer(1, [0, 0, 0], 1.0, 0, 2, null));
  // 4: TSA (6 faces as 6 tri-strips of 4 verts each)
  w.addObject(
    11,
    triStripExplicit(
      [0, 1, 2, 3, 4, 5, 6, 7, 0, 4, 2, 6, 1, 5, 3, 7, 0, 1, 4, 5, 2, 3, 6, 7],
      [4, 4, 4, 4, 4, 4],
    ),
  );
  // 5: Appearance
  w.addObject(3, appearance(0, 0, 0, 0, null));
  // 6: Mesh with component orientation (tests Transformable loader path)
  w.addObject(
    14,
    meshWithOrientation(3, [{ idx: 4, app: 5 }], 38.0, 1.0, 1.0, 0.0),
  );
  // 7: Camera
  w.addObject(5, cameraWithTranslation(50, 60.0, 1.0, 1.0, 1000.0, 0, 0, 200));
  // 8: Background
  w.addObject(4, background(0xff111111, true, true));
  // 9: World
  w.addObject(22, world([6, 7], 7, 8));
  return w.toBuffer();
}

// ========== Write ==========
const outDir = __dirname;
const files = {
  'single_triangle.m3g': genSingleTriangle(),
  'color_triangle.m3g': genColorTriangle(),
  'world_camera.m3g': genWorldCamera(),
  'material_lit.m3g': genMaterialLit(),
  'cube_world.m3g': genCubeWorld(),
};

for (const [name, buf] of Object.entries(files)) {
  fs.writeFileSync(path.join(outDir, name), buf);
  console.log(`wrote ${name} (${buf.length} bytes)`);
}
console.log('done');
