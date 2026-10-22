package com.mascotcapsule.micro3d.v3;

/** Matrix helpers kept in Java; Android's Matrix class is not required. */
final class MascotMatrix {
	private MascotMatrix() {}
	static void multiplyMM(float[] out, int outOffset, float[] lhs, int lhsOffset, float[] rhs, int rhsOffset) {
		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++) {
				float value = 0;
				for (int i = 0; i < 4; i++) value += lhs[lhsOffset + i * 4 + row] * rhs[rhsOffset + col * 4 + i];
				out[outOffset + col * 4 + row] = value;
			}
		}
	}
	static void multiplyMV(float[] out, int outOffset, float[] matrix, int matrixOffset, float[] vector, int vectorOffset) {
		for (int row = 0; row < 4; row++) {
			out[outOffset + row] = matrix[matrixOffset + row] * vector[vectorOffset]
					+ matrix[matrixOffset + 4 + row] * vector[vectorOffset + 1]
					+ matrix[matrixOffset + 8 + row] * vector[vectorOffset + 2]
					+ matrix[matrixOffset + 12 + row] * vector[vectorOffset + 3];
		}
	}
}
