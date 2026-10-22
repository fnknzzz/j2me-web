/*
 * Copyright 2020 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mascotcapsule.micro3d.v3;


import com.mascotcapsule.micro3d.v3.RenderNode.FigureNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Stack;


public class Figure {
	Stack stack = new Stack();
	Model data;
	Texture[] textures;
	int selectedTex = -1;
	int currentPattern;

	public Figure(byte[] b) {
		if (b == null) {
			throw new NullPointerException();
		}
		try {
			init(b);
		} catch (Exception e) {
			MascotNative.logError("Error loading figure data");
			throw new RuntimeException(e.toString());
		}
	}

	public Figure(String name) throws IOException {
		byte[] bytes = MascotNative.loadResource(name);
		if (bytes == null) {
			throw new IOException("Error reading resource: " + name);
		}
		try {
			init(bytes);
		} catch (Exception e) {
			MascotNative.logError("Error loading figure data from [" + name + "]");
			throw new RuntimeException(e.toString());
		}
	}

	private synchronized void init(byte[] bytes) throws IOException {
		data = Loader.loadMbacData(bytes);
		Utils.transform(data.originalVertices, data.vertices,
				data.originalNormals, data.normals, data.bones, null);
		sortPolygons();
		fillTexCoordBuffer();
	}

	public final void dispose() {
		data = null;
	}

	private void sortPolygons() {
		Model.Polygon[] polygonsT = data.polygonsT;
		MascotArrays.sort(polygonsT, new MascotArrays.Comparator() { public int compare(Object ao, Object bo) { Model.Polygon a=(Model.Polygon)ao, b=(Model.Polygon)bo; int cmp=a.blendMode-b.blendMode; if(cmp!=0)return cmp; cmp=a.face-b.face; return cmp!=0?cmp:a.doubleFace-b.doubleFace; } });
		int[][][] subMeshesLengthsT = data.subMeshesLengthsT;
		int[] indexArray = data.indices;
		int pos = 0;
		for (Model.Polygon p : polygonsT) {
			int[] indices = p.indices;
			int length = indices.length;
			subMeshesLengthsT[p.blendMode >> 1][p.face][p.doubleFace] += length;
			System.arraycopy(indices, 0, indexArray, pos, length);
			pos += length;
		}

		Model.Polygon[] polygonsC = data.polygonsC;
		MascotArrays.sort(polygonsC, new MascotArrays.Comparator() { public int compare(Object ao, Object bo) { Model.Polygon a=(Model.Polygon)ao, b=(Model.Polygon)bo; int cmp=a.blendMode-b.blendMode; return cmp!=0?cmp:a.doubleFace-b.doubleFace; } });
		int[][] subMeshesLengthsC = data.subMeshesLengthsC;
		for (Model.Polygon p : polygonsC) {
			int[] indices = p.indices;
			int length = indices.length;
			subMeshesLengthsC[p.blendMode >> 1][p.doubleFace] += length;
			System.arraycopy(indices, 0, indexArray, pos, length);
			pos += length;
		}
	}

	public synchronized final void setPosture(ActionTable actionTable, int action, int frame) {
		if (actionTable == null) {
			throw new NullPointerException();
		} else if (action < 0 || action >= actionTable.getNumActions()) {
			throw new IllegalArgumentException();
		}
		Action act = actionTable.actions[action];
		final MascotIntMap dynamic = act.dynamic;
		if (dynamic != null) {
			int iFrame = frame < 0 ? 0 : frame >> 16;
			for (int i = dynamic.size() - 1; i >= 0; i--) {
				if (dynamic.keyAt(i) <= iFrame) {
					currentPattern = dynamic.valueAt(i);
					applyPattern();
					break;
				}
			}
		}
		//noinspection ManualMinMaxCalculation
		applyBoneAction(act, frame < 0 ? 0 : frame);
	}

	private void applyPattern() {
		int[] indexArray = data.indices;
		int pos = 0;
		int invalid = data.vertices.capacity() / 3 - 1;
		for (Model.Polygon p : data.polygonsT) {
			int[] indices = p.indices;
			int length = indices.length;
			int pp = p.pattern;
			if ((pp & currentPattern) == pp) {
				for (int i = 0; i < length; i++) {
					indexArray[pos++] = indices[i];
				}
			} else {
				while (length > 0) {
					indexArray[pos++] = invalid;
					length--;
				}
			}
		}

		for (Model.Polygon p : data.polygonsC) {
			int[] indices = p.indices;
			int length = indices.length;
			int pp = p.pattern;
			if ((pp & currentPattern) == pp) {
				for (int i = 0; i < length; i++) {
					indexArray[pos++] = indices[i];
				}
			} else {
				while (length > 0) {
					indexArray[pos++] = invalid;
					length--;
				}
			}
		}
	}

	public final Texture getTexture() {
		if (selectedTex < 0) {
			return null;
		}
		return textures[selectedTex];
	}

	public final void setTexture(Texture tex) {
		if (tex == null)
			throw new NullPointerException();
		if (tex.isSphere)
			throw new IllegalArgumentException();

		textures = new Texture[]{tex};
		selectedTex = 0;
	}

	public final void setTexture(Texture[] t) {
		if (t == null) throw new NullPointerException();
		if (t.length == 0) throw new IllegalArgumentException();
		for (Texture texture : t) {
			if (texture == null) throw new NullPointerException();
			if (texture.isSphere) throw new IllegalArgumentException();
		}
		textures = t;
		selectedTex = -1;
	}

	public final int getNumTextures() {
		if (textures == null) {
			return 0;
		}
		return textures.length;
	}

	public final void selectTexture(int idx) {
		if (idx < 0 || idx >= getNumTextures()) {
			throw new IllegalArgumentException();
		}
		selectedTex = idx;
	}

	public final int getNumPattern() {
		return data.numPatterns;
	}

	public synchronized final void setPattern(int idx) {
		currentPattern = idx;
		applyPattern();
	}

	private void applyBoneAction(Action act, int frame) {
		Action.Bone[] actionBones = act.boneActions;
		if (actionBones.length == 0) return;
		synchronized (act.matrices) {
			for (final Action.Bone actionBone : actionBones) {
				actionBone.setFrame(frame);
			}
			Utils.transform(data.originalVertices, data.vertices,
					data.originalNormals, data.normals, data.bones, act.matrices);
		}
	}

	private void fillTexCoordBuffer() {
		ByteBuffer buffer = data.texCoordArray;
		buffer.rewind();
		for (Model.Polygon poly : data.polygonsT) {
			buffer.put(poly.texCoords);
		}
		for (Model.Polygon poly : data.polygonsC) {
			buffer.put(poly.texCoords);
		}
		buffer.rewind();
	}

	synchronized FloatBuffer getVertexData() {
		if (data.vertexArray == null) {
			data.vertexArray = ByteBuffer.allocateDirect(data.vertexArrayCapacity)
					.asFloatBuffer();
		}
		Utils.fillBuffer(data.vertexArray, data.vertices, data.indices);
		return data.vertexArray;
	}

	synchronized FloatBuffer getNormalsData() {
		if (data.originalNormals == null) {
			return null;
		}
		if (data.normalsArray == null) {
			data.normalsArray = ByteBuffer.allocateDirect(data.vertexArrayCapacity)
					.asFloatBuffer();
		}
		Utils.fillBuffer(data.normalsArray, data.normals, data.indices);
		return data.normalsArray;
	}
}
