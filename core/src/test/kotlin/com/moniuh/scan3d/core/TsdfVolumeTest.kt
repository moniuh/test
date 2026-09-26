package com.moniuh.scan3d.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TsdfVolumeTest {

    private val width = 160
    private val height = 120
    private val fx = 140f
    private val fy = 140f
    private val cx = 80f
    private val cy = 60f

    /** Camera-to-world matrix (column-major) for a camera at [eye] looking at [target], +Y up. */
    private fun lookAt(eye: FloatArray, target: FloatArray): FloatArray {
        val f = normalize(floatArrayOf(target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]))
        val x = normalize(cross(f, floatArrayOf(0f, 1f, 0f)))
        val y = cross(x, f)
        return floatArrayOf(
            x[0], x[1], x[2], 0f,
            y[0], y[1], y[2], 0f,
            -f[0], -f[1], -f[2], 0f,
            eye[0], eye[1], eye[2], 1f,
        )
    }

    private fun cross(a: FloatArray, b: FloatArray) =
        floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private fun normalize(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    /** Renders a depth frame of a sphere; the top half is red and the bottom half blue. */
    private fun renderSphere(m: FloatArray, center: FloatArray, radius: Float): DepthFrame {
        val depth = ShortArray(width * height)
        val rgb = ByteArray(width * height * 3)
        val conf = ByteArray(width * height) { 255.toByte() }
        val ex = m[12] - center[0]
        val ey = m[13] - center[1]
        val ez = m[14] - center[2]
        for (v in 0 until height) for (u in 0 until width) {
            val rx = (u - cx) / fx
            val ry = (cy - v) / fy
            // World ray direction for camera direction (rx, ry, -1); ray parameter == z-depth.
            val dx = m[0] * rx + m[4] * ry - m[8]
            val dy = m[1] * rx + m[5] * ry - m[9]
            val dz = m[2] * rx + m[6] * ry - m[10]
            val a = dx * dx + dy * dy + dz * dz
            val b = 2 * (dx * ex + dy * ey + dz * ez)
            val c = ex * ex + ey * ey + ez * ez - radius * radius
            val disc = b * b - 4 * a * c
            if (disc < 0) continue
            val t = (-b - sqrt(disc)) / (2 * a)
            if (t <= 0) continue
            val i = v * width + u
            depth[i] = (t * 1000f).toInt().toShort()
            val hitY = m[13] + t * dy
            if (hitY > center[1]) rgb[i * 3] = 255.toByte() else rgb[i * 3 + 2] = 255.toByte()
        }
        return DepthFrame(width, height, depth, conf, rgb, fx, fy, cx, cy, m)
    }

    private fun scanSphere(voxel: Float, center: FloatArray, radius: Float): TsdfVolume {
        val volume = TsdfVolume(voxel)
        for (ring in 0 until 3) {
            val elevation = (ring - 1) * 0.5f
            for (k in 0 until 12) {
                val ang = k * (2 * Math.PI / 12).toFloat()
                val eye = floatArrayOf(
                    center[0] + cos(ang) * cos(elevation),
                    center[1] + sin(elevation),
                    center[2] + sin(ang) * cos(elevation),
                )
                val used = volume.integrate(renderSphere(lookAt(eye, center), center, radius), maxDepth = 3f)
                assertTrue(used > 0)
            }
        }
        return volume
    }

    @Test
    fun sphereMeshLiesOnSurfaceWithOutwardNormals() {
        val center = floatArrayOf(0.1f, 1.2f, -0.4f)
        val radius = 0.25f
        val voxel = 0.01f
        val mesh = scanSphere(voxel, center, radius).extractMesh(minWeight = 1f)

        assertTrue("too few vertices: ${mesh.vertexCount}", mesh.vertexCount > 2000)
        assertTrue("too few triangles: ${mesh.triangleCount}", mesh.triangleCount > 4000)

        var maxErr = 0f
        var outward = 0
        var topRed = 0
        var top = 0
        for (i in 0 until mesh.vertexCount) {
            val px = mesh.positions[i * 3] - center[0]
            val py = mesh.positions[i * 3 + 1] - center[1]
            val pz = mesh.positions[i * 3 + 2] - center[2]
            val r = sqrt(px * px + py * py + pz * pz)
            maxErr = maxOf(maxErr, abs(r - radius))
            val dot = px * mesh.normals[i * 3] + py * mesh.normals[i * 3 + 1] + pz * mesh.normals[i * 3 + 2]
            if (dot > 0) outward++
            if (py > 0.1f) {
                top++
                if ((mesh.colors[i * 3].toInt() and 0xFF) > 200 && (mesh.colors[i * 3 + 2].toInt() and 0xFF) < 50) topRed++
            }
        }
        assertTrue("surface error too large: $maxErr", maxErr < 1.5f * voxel)
        assertTrue("normals not outward: $outward / ${mesh.vertexCount}", outward > mesh.vertexCount * 0.99)
        assertTrue("top should be red: $topRed / $top", topRed > top * 0.95)

        // A fully observed sphere is a closed surface: every edge is shared by exactly two triangles.
        val edges = HashMap<Long, Int>()
        for (t in 0 until mesh.triangleCount) for (k in 0 until 3) {
            val a = mesh.indices[t * 3 + k]
            val b = mesh.indices[t * 3 + (k + 1) % 3]
            val key = minOf(a, b).toLong() shl 32 or maxOf(a, b).toLong()
            edges[key] = (edges[key] ?: 0) + 1
        }
        val boundary = edges.values.count { it != 2 }
        assertTrue("sphere should be (almost) closed, open edges: $boundary", boundary < edges.size * 0.01)
    }

    @Test
    fun smallComponentsAreRemoved() {
        val mesh = scanSphere(0.02f, floatArrayOf(0f, 0f, 0f), 0.3f).extractMesh()
        val cleaned = MeshCleanup.removeSmallComponents(mesh, mesh.triangleCount + 1)
        assertEquals(0, cleaned.triangleCount)
        val kept = MeshCleanup.removeSmallComponents(mesh, 10)
        assertEquals(mesh.triangleCount, kept.triangleCount)
    }

    @Test
    fun surfaceVoxelPreviewIsThinnedToLimit() {
        val volume = scanSphere(0.02f, floatArrayOf(0f, 0f, 0f), 0.3f)
        val all = volume.extractSurfaceVoxels(1f, Int.MAX_VALUE)
        assertTrue(all.size > 500)
        val thin = volume.extractSurfaceVoxels(1f, 100)
        assertEquals(100, thin.size)
    }

    @Test
    fun exportersProduceWellFormedFiles() {
        val mesh = scanSphere(0.03f, floatArrayOf(0f, 0f, 0f), 0.3f).extractMesh()
        assertTrue(mesh.triangleCount > 0)

        val ply = ByteArrayOutputStream().also { Exporters.writePlyMesh(mesh, it) }.toByteArray()
        val plyHeaderEnd = String(ply, Charsets.US_ASCII).indexOf("end_header\n") + "end_header\n".length
        assertEquals(plyHeaderEnd + mesh.vertexCount * 27 + mesh.triangleCount * 13, ply.size)

        val pts = ByteArrayOutputStream().also { Exporters.write(ExportFormat.PLY_POINTS, mesh, it) }.toByteArray()
        val ptsHeaderEnd = String(pts, Charsets.US_ASCII).indexOf("end_header\n") + "end_header\n".length
        assertEquals(ptsHeaderEnd + mesh.vertexCount * 27, pts.size)

        val stl = ByteArrayOutputStream().also { Exporters.writeStl(mesh, it) }.toByteArray()
        assertEquals(84 + 50 * mesh.triangleCount, stl.size)

        val obj = ByteArrayOutputStream().also { Exporters.writeObj(mesh, it) }.toString(Charsets.US_ASCII)
        val lines = obj.lines()
        assertEquals(mesh.vertexCount, lines.count { it.startsWith("v ") })
        assertEquals(mesh.vertexCount, lines.count { it.startsWith("vn ") })
        assertEquals(mesh.triangleCount, lines.count { it.startsWith("f ") })
        assertEquals(7, lines.first { it.startsWith("v ") }.split(' ').size)
    }

    @Test
    fun fixedFormatting() {
        fun f(v: Float, d: Int) = Exporters.appendFixed(StringBuilder(), v, d).toString()
        assertEquals("1.50000", f(1.5f, 5))
        assertEquals("-0.00010", f(-0.0001f, 5))
        assertEquals("0.0000", f(0f, 4))
        assertEquals("-12.3", f(-12.3f, 1))
    }

    @Test
    fun longIntMapMatchesHashMap() {
        val map = LongIntMap(4)
        val ref = HashMap<Long, Int>()
        val rnd = java.util.Random(1)
        repeat(20000) {
            val k = packXyz(rnd.nextInt(200) - 100, rnd.nextInt(200) - 100, rnd.nextInt(200) - 100)
            val v = rnd.nextInt(1000)
            map.put(k, v)
            ref[k] = v
        }
        assertEquals(ref.size, map.size)
        for ((k, v) in ref) assertEquals(v, map.get(k))
        assertEquals(-1, map.get(packXyz(500, 500, 500)))
        val key = packXyz(-123, 456, -789)
        assertEquals(-123, unpackX(key))
        assertEquals(456, unpackY(key))
        assertEquals(-789, unpackZ(key))
    }
}
