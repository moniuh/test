package com.moniuh.scan3d.core

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

enum class ExportFormat(val extension: String, val mimeType: String) {
    /** Binary PLY mesh with per-vertex normals and colours (MeshLab, Blender, CloudCompare). */
    PLY_MESH("ply", "application/octet-stream"),

    /** Wavefront OBJ mesh with vertex colours ("v x y z r g b") and normals. */
    OBJ("obj", "model/obj"),

    /** Binary STL mesh (no colour), ready for 3D printing slicers. */
    STL("stl", "model/stl"),

    /** Binary PLY point cloud (surface points with normals and colours). */
    PLY_POINTS("ply", "application/octet-stream"),
}

object Exporters {

    fun write(format: ExportFormat, mesh: Mesh, out: OutputStream) = when (format) {
        ExportFormat.PLY_MESH -> writePlyMesh(mesh, out)
        ExportFormat.OBJ -> writeObj(mesh, out)
        ExportFormat.STL -> writeStl(mesh, out)
        ExportFormat.PLY_POINTS -> writePlyPoints(PointCloud(mesh.positions, mesh.colors, mesh.normals), out)
    }

    fun writePlyMesh(mesh: Mesh, out: OutputStream) {
        val header = buildString {
            append("ply\n")
            append("format binary_little_endian 1.0\n")
            append("comment Created by Skaner 3D (ARCore depth + TSDF fusion), units: metres\n")
            append("element vertex ${mesh.vertexCount}\n")
            append("property float x\nproperty float y\nproperty float z\n")
            append("property float nx\nproperty float ny\nproperty float nz\n")
            append("property uchar red\nproperty uchar green\nproperty uchar blue\n")
            append("element face ${mesh.triangleCount}\n")
            append("property list uchar int vertex_indices\n")
            append("end_header\n")
        }
        val bos = BufferedOutputStream(out, 1 shl 16)
        bos.write(header.toByteArray(Charsets.US_ASCII))
        val buf = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until mesh.vertexCount) {
            if (buf.remaining() < 27) flush(buf, bos)
            buf.putFloat(mesh.positions[i * 3]).putFloat(mesh.positions[i * 3 + 1]).putFloat(mesh.positions[i * 3 + 2])
            buf.putFloat(mesh.normals[i * 3]).putFloat(mesh.normals[i * 3 + 1]).putFloat(mesh.normals[i * 3 + 2])
            buf.put(mesh.colors[i * 3]).put(mesh.colors[i * 3 + 1]).put(mesh.colors[i * 3 + 2])
        }
        for (t in 0 until mesh.triangleCount) {
            if (buf.remaining() < 13) flush(buf, bos)
            buf.put(3.toByte())
            buf.putInt(mesh.indices[t * 3]).putInt(mesh.indices[t * 3 + 1]).putInt(mesh.indices[t * 3 + 2])
        }
        flush(buf, bos)
        bos.flush()
    }

    fun writePlyPoints(cloud: PointCloud, out: OutputStream) {
        val normals = cloud.normals
        val header = buildString {
            append("ply\n")
            append("format binary_little_endian 1.0\n")
            append("comment Created by Skaner 3D (ARCore depth + TSDF fusion), units: metres\n")
            append("element vertex ${cloud.size}\n")
            append("property float x\nproperty float y\nproperty float z\n")
            if (normals != null) append("property float nx\nproperty float ny\nproperty float nz\n")
            append("property uchar red\nproperty uchar green\nproperty uchar blue\n")
            append("end_header\n")
        }
        val bos = BufferedOutputStream(out, 1 shl 16)
        bos.write(header.toByteArray(Charsets.US_ASCII))
        val buf = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until cloud.size) {
            if (buf.remaining() < 27) flush(buf, bos)
            buf.putFloat(cloud.positions[i * 3]).putFloat(cloud.positions[i * 3 + 1]).putFloat(cloud.positions[i * 3 + 2])
            if (normals != null) buf.putFloat(normals[i * 3]).putFloat(normals[i * 3 + 1]).putFloat(normals[i * 3 + 2])
            buf.put(cloud.colors[i * 3]).put(cloud.colors[i * 3 + 1]).put(cloud.colors[i * 3 + 2])
        }
        flush(buf, bos)
        bos.flush()
    }

    fun writeObj(mesh: Mesh, out: OutputStream) {
        val w: Writer = OutputStreamWriter(BufferedOutputStream(out, 1 shl 16), Charsets.US_ASCII)
        val sb = StringBuilder(128)
        w.write("# Created by Skaner 3D (ARCore depth + TSDF fusion), units: metres\n")
        w.write("# ${mesh.vertexCount} vertices, ${mesh.triangleCount} triangles\n")
        for (i in 0 until mesh.vertexCount) {
            sb.setLength(0)
            sb.append("v ")
            appendFixed(sb, mesh.positions[i * 3], 5).append(' ')
            appendFixed(sb, mesh.positions[i * 3 + 1], 5).append(' ')
            appendFixed(sb, mesh.positions[i * 3 + 2], 5).append(' ')
            appendFixed(sb, (mesh.colors[i * 3].toInt() and 0xFF) / 255f, 4).append(' ')
            appendFixed(sb, (mesh.colors[i * 3 + 1].toInt() and 0xFF) / 255f, 4).append(' ')
            appendFixed(sb, (mesh.colors[i * 3 + 2].toInt() and 0xFF) / 255f, 4).append('\n')
            w.append(sb)
        }
        for (i in 0 until mesh.vertexCount) {
            sb.setLength(0)
            sb.append("vn ")
            appendFixed(sb, mesh.normals[i * 3], 4).append(' ')
            appendFixed(sb, mesh.normals[i * 3 + 1], 4).append(' ')
            appendFixed(sb, mesh.normals[i * 3 + 2], 4).append('\n')
            w.append(sb)
        }
        for (t in 0 until mesh.triangleCount) {
            val a = mesh.indices[t * 3] + 1
            val b = mesh.indices[t * 3 + 1] + 1
            val c = mesh.indices[t * 3 + 2] + 1
            sb.setLength(0)
            sb.append("f ").append(a).append("//").append(a).append(' ')
                .append(b).append("//").append(b).append(' ')
                .append(c).append("//").append(c).append('\n')
            w.append(sb)
        }
        w.flush()
    }

    fun writeStl(mesh: Mesh, out: OutputStream) {
        val bos = BufferedOutputStream(out, 1 shl 16)
        val header = ByteArray(80)
        val title = "Skaner 3D binary STL, units: metres".toByteArray(Charsets.US_ASCII)
        System.arraycopy(title, 0, header, 0, title.size)
        bos.write(header)
        val buf = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(mesh.triangleCount)
        val p = mesh.positions
        for (t in 0 until mesh.triangleCount) {
            if (buf.remaining() < 50) flush(buf, bos)
            val a = mesh.indices[t * 3] * 3
            val b = mesh.indices[t * 3 + 1] * 3
            val c = mesh.indices[t * 3 + 2] * 3
            val e1x = p[b] - p[a]
            val e1y = p[b + 1] - p[a + 1]
            val e1z = p[b + 2] - p[a + 2]
            val e2x = p[c] - p[a]
            val e2y = p[c + 1] - p[a + 1]
            val e2z = p[c + 2] - p[a + 2]
            var nx = e1y * e2z - e1z * e2y
            var ny = e1z * e2x - e1x * e2z
            var nz = e1x * e2y - e1y * e2x
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 0f) {
                nx /= len; ny /= len; nz /= len
            }
            buf.putFloat(nx).putFloat(ny).putFloat(nz)
            for (v in intArrayOf(a, b, c)) buf.putFloat(p[v]).putFloat(p[v + 1]).putFloat(p[v + 2])
            buf.putShort(0)
        }
        flush(buf, bos)
        bos.flush()
    }

    private fun flush(buf: ByteBuffer, out: OutputStream) {
        out.write(buf.array(), 0, buf.position())
        buf.clear()
    }

    /** Fast locale-independent fixed-point formatting. */
    internal fun appendFixed(sb: StringBuilder, value: Float, decimals: Int): StringBuilder {
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val scaled = (value.toDouble() * scale).roundToLong()
        if (scaled < 0) sb.append('-')
        val a = abs(scaled)
        sb.append(a / scale)
        if (decimals > 0) {
            sb.append('.')
            val frac = (a % scale).toString()
            repeat(decimals - frac.length) { sb.append('0') }
            sb.append(frac)
        }
        return sb
    }
}
