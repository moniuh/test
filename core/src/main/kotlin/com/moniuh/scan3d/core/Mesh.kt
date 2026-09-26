package com.moniuh.scan3d.core

import kotlin.math.sqrt

/**
 * Indexed triangle mesh in metres.
 *
 * @param positions xyz per vertex.
 * @param normals xyz per vertex (unit length, pointing out of the scanned surface).
 * @param colors rgb per vertex, unsigned bytes.
 * @param indices three vertex indices per triangle, counter-clockwise seen from outside.
 */
class Mesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val colors: ByteArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    companion object {
        /** Area-weighted vertex normals computed from the triangles. */
        fun computeNormals(positions: FloatArray, indices: IntArray): FloatArray {
            val n = FloatArray(positions.size)
            var t = 0
            while (t < indices.size) {
                val a = indices[t] * 3
                val b = indices[t + 1] * 3
                val c = indices[t + 2] * 3
                val e1x = positions[b] - positions[a]
                val e1y = positions[b + 1] - positions[a + 1]
                val e1z = positions[b + 2] - positions[a + 2]
                val e2x = positions[c] - positions[a]
                val e2y = positions[c + 1] - positions[a + 1]
                val e2z = positions[c + 2] - positions[a + 2]
                val nx = e1y * e2z - e1z * e2y
                val ny = e1z * e2x - e1x * e2z
                val nz = e1x * e2y - e1y * e2x
                for (v in intArrayOf(a, b, c)) {
                    n[v] += nx
                    n[v + 1] += ny
                    n[v + 2] += nz
                }
                t += 3
            }
            var i = 0
            while (i < n.size) {
                val len = sqrt(n[i] * n[i] + n[i + 1] * n[i + 1] + n[i + 2] * n[i + 2])
                if (len > 1e-12f) {
                    n[i] /= len
                    n[i + 1] /= len
                    n[i + 2] /= len
                }
                i += 3
            }
            return n
        }
    }
}

/** Coloured point cloud in metres. */
class PointCloud(
    val positions: FloatArray,
    val colors: ByteArray,
    val normals: FloatArray? = null,
) {
    val size: Int get() = positions.size / 3
}
