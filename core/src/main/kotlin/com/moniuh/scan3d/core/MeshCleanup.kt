package com.moniuh.scan3d.core

object MeshCleanup {

    /**
     * Removes connected components with fewer than [minTriangles] triangles (floating noise),
     * and drops vertices no longer referenced by any triangle.
     */
    fun removeSmallComponents(mesh: Mesh, minTriangles: Int): Mesh {
        if (minTriangles <= 1 || mesh.triangleCount == 0) return mesh
        val n = mesh.vertexCount
        val parent = IntArray(n) { it }

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) {
                val next = parent[c]
                parent[c] = r
                c = next
            }
            return r
        }

        val idx = mesh.indices
        for (t in 0 until mesh.triangleCount) {
            val a = find(idx[t * 3])
            val b = find(idx[t * 3 + 1])
            val c = find(idx[t * 3 + 2])
            if (a != b) parent[a] = b
            val bb = find(b)
            if (find(c) != bb) parent[find(c)] = bb
        }
        val componentTriangles = IntArray(n)
        for (t in 0 until mesh.triangleCount) componentTriangles[find(idx[t * 3])]++

        val remap = IntArray(n) { -1 }
        var newVertexCount = 0
        val keptTriangles = IntList(idx.size)
        for (t in 0 until mesh.triangleCount) {
            if (componentTriangles[find(idx[t * 3])] < minTriangles) continue
            for (k in 0 until 3) {
                val v = idx[t * 3 + k]
                if (remap[v] < 0) remap[v] = newVertexCount++
                keptTriangles.add(remap[v])
            }
        }
        if (newVertexCount == n) return Mesh(mesh.positions, mesh.normals, mesh.colors, keptTriangles.toArray())

        val positions = FloatArray(newVertexCount * 3)
        val normals = FloatArray(newVertexCount * 3)
        val colors = ByteArray(newVertexCount * 3)
        for (v in 0 until n) {
            val r = remap[v]
            if (r < 0) continue
            System.arraycopy(mesh.positions, v * 3, positions, r * 3, 3)
            System.arraycopy(mesh.normals, v * 3, normals, r * 3, 3)
            System.arraycopy(mesh.colors, v * 3, colors, r * 3, 3)
        }
        return Mesh(positions, normals, colors, keptTriangles.toArray())
    }
}
