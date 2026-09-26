package com.moniuh.scan3d.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Sparse truncated signed distance field (TSDF) volume, in the spirit of KinectFusion / voxel hashing.
 *
 * Space is split into blocks of 8x8x8 voxels that are only allocated around observed surfaces,
 * so the memory use grows with the scanned surface area, not with the bounding box.
 *
 * Signed distance convention: positive in free space (between the camera and the surface),
 * negative behind the surface. Values are normalised by the truncation distance to [-1, 1].
 *
 * Not thread-safe: all calls must come from one thread.
 */
class TsdfVolume(
    val voxelSize: Float,
    truncationVoxels: Float = 4f,
    private val maxWeight: Float = 64f,
) {
    val truncation: Float = voxelSize * truncationVoxels
    private val blockSize = voxelSize * BLOCK_DIM
    private val blocks = HashMap<Long, Block>()

    /** Number of allocated 8x8x8 blocks. */
    val blockCount: Int get() = blocks.size

    /** Approximate memory used by the voxel data, in bytes. */
    val approximateBytes: Long get() = blocks.size.toLong() * Block.BYTES

    private class Block {
        val tsdf = FloatArray(VOXELS) { 1f }
        val weight = FloatArray(VOXELS)
        val colorWeight = FloatArray(VOXELS)
        val rgb = ByteArray(VOXELS * 3)

        companion object {
            const val BYTES = VOXELS * (4 + 4 + 4 + 3) + 64
        }
    }

    fun clear() = blocks.clear()

    /**
     * Fuses one depth frame into the volume.
     *
     * @return number of depth pixels that contributed.
     */
    fun integrate(
        frame: DepthFrame,
        minDepth: Float = 0.1f,
        maxDepth: Float = 3f,
        minConfidence: Int = 0,
    ): Int {
        val w = frame.width
        val h = frame.height
        val m = frame.cameraToWorld
        val frameKeys = LongIntMap(4096)
        val frameBlocks = ArrayList<Block>()
        val frameBlockKeys = LongList()
        val step = blockSize * 0.5f
        var used = 0

        // 1. Allocate every block that the truncation band around each measurement touches.
        for (v in 0 until h) {
            val ry = (frame.cy - v) / frame.fy
            for (u in 0 until w) {
                val i = v * w + u
                val d = frame.depthMetersAt(i)
                if (d < minDepth || d > maxDepth) continue
                if (frame.confidenceAt(i) < minConfidence) continue
                used++
                val rx = (u - frame.cx) / frame.fx
                var t = d - truncation
                val tEnd = d + truncation
                while (true) {
                    val px = rx * t
                    val py = ry * t
                    val pz = -t
                    val wx = m[0] * px + m[4] * py + m[8] * pz + m[12]
                    val wy = m[1] * px + m[5] * py + m[9] * pz + m[13]
                    val wz = m[2] * px + m[6] * py + m[10] * pz + m[14]
                    val key = packXyz(
                        floor(wx / blockSize).toInt(),
                        floor(wy / blockSize).toInt(),
                        floor(wz / blockSize).toInt(),
                    )
                    if (frameKeys.get(key) < 0) {
                        frameKeys.put(key, frameBlocks.size)
                        frameBlocks.add(blocks.getOrPut(key) { Block() })
                        frameBlockKeys.add(key)
                    }
                    if (t >= tEnd) break
                    t = min(t + step, tEnd)
                }
            }
        }
        if (used == 0) return 0

        // 2. Project every voxel of the touched blocks into the depth image and update it.
        val c = frame.worldToCamera
        val depth = frame.depthMm
        val conf = frame.confidence
        val rgb = frame.rgb
        val colorBand = truncation * 0.5f
        for (bi in frameBlocks.indices) {
            val block = frameBlocks[bi]
            val key = frameBlockKeys[bi]
            val ox = unpackX(key) * BLOCK_DIM
            val oy = unpackY(key) * BLOCK_DIM
            val oz = unpackZ(key) * BLOCK_DIM
            for (lz in 0 until BLOCK_DIM) {
                val wz = (oz + lz + 0.5f) * voxelSize
                for (ly in 0 until BLOCK_DIM) {
                    val wy = (oy + ly + 0.5f) * voxelSize
                    for (lx in 0 until BLOCK_DIM) {
                        val wx = (ox + lx + 0.5f) * voxelSize
                        val cxp = c[0] * wx + c[4] * wy + c[8] * wz + c[12]
                        val cyp = c[1] * wx + c[5] * wy + c[9] * wz + c[13]
                        val z = -(c[2] * wx + c[6] * wy + c[10] * wz + c[14])
                        if (z < minDepth) continue
                        val iu = floor(frame.fx * cxp / z + frame.cx + 0.5f).toInt()
                        val iv = floor(frame.cy - frame.fy * cyp / z + 0.5f).toInt()
                        if (iu < 0 || iv < 0 || iu >= w || iv >= h) continue
                        val pi = iv * w + iu
                        val d = (depth[pi].toInt() and 0xFFFF) * 0.001f
                        if (d < minDepth || d > maxDepth) continue
                        val cf = if (conf == null) 255 else conf[pi].toInt() and 0xFF
                        if (cf < minConfidence) continue
                        val sdf = d - z
                        if (sdf < -truncation) continue
                        val obs = min(1f, sdf / truncation)
                        val wObs = max(cf / 255f, 0.05f)
                        val vi = lx + BLOCK_DIM * (ly + BLOCK_DIM * lz)
                        val wOld = block.weight[vi]
                        val wNew = wOld + wObs
                        block.tsdf[vi] = (block.tsdf[vi] * wOld + obs * wObs) / wNew
                        block.weight[vi] = min(wNew, maxWeight)
                        if (rgb != null && abs(sdf) < colorBand) {
                            val cwOld = block.colorWeight[vi]
                            val cwNew = cwOld + wObs
                            val ci = vi * 3
                            val si = pi * 3
                            for (k in 0 until 3) {
                                val old = block.rgb[ci + k].toInt() and 0xFF
                                val new = rgb[si + k].toInt() and 0xFF
                                block.rgb[ci + k] = ((old * cwOld + new * wObs) / cwNew + 0.5f).toInt().toByte()
                            }
                            block.colorWeight[vi] = min(cwNew, maxWeight)
                        }
                    }
                }
            }
        }
        return used
    }

    /**
     * Voxel centres lying within about one voxel of the surface. Cheap; meant for live preview.
     */
    fun extractSurfaceVoxels(minWeight: Float, maxPoints: Int): PointCloud {
        val band = voxelSize / truncation
        val pos = FloatList(64 * 1024)
        val col = ByteList(64 * 1024)
        for ((key, block) in blocks) {
            val ox = unpackX(key) * BLOCK_DIM
            val oy = unpackY(key) * BLOCK_DIM
            val oz = unpackZ(key) * BLOCK_DIM
            for (vi in 0 until VOXELS) {
                if (block.weight[vi] < minWeight || abs(block.tsdf[vi]) > band) continue
                val lx = vi and 7
                val ly = (vi shr 3) and 7
                val lz = vi shr 6
                pos.add((ox + lx + 0.5f) * voxelSize)
                pos.add((oy + ly + 0.5f) * voxelSize)
                pos.add((oz + lz + 0.5f) * voxelSize)
                appendColor(block, vi, col)
            }
        }
        val count = pos.size / 3
        if (count <= maxPoints) return PointCloud(pos.toArray(), col.toArray())
        // Uniformly thin out the preview.
        val outPos = FloatArray(maxPoints * 3)
        val outCol = ByteArray(maxPoints * 3)
        val src = pos.data
        val srcCol = col.data
        for (i in 0 until maxPoints) {
            val s = (i.toLong() * count / maxPoints).toInt()
            System.arraycopy(src, s * 3, outPos, i * 3, 3)
            System.arraycopy(srcCol, s * 3, outCol, i * 3, 3)
        }
        return PointCloud(outPos, outCol)
    }

    private fun appendColor(block: Block, vi: Int, out: ByteList) {
        if (block.colorWeight[vi] > 0f) {
            out.add(block.rgb[vi * 3])
            out.add(block.rgb[vi * 3 + 1])
            out.add(block.rgb[vi * 3 + 2])
        } else {
            out.add(NO_COLOR)
            out.add(NO_COLOR)
            out.add(NO_COLOR)
        }
    }

    /**
     * Extracts a watertight-where-observed triangle mesh of the zero level set using Naive Surface Nets:
     * one vertex per cell crossing the surface, one quad per voxel edge crossing the surface.
     *
     * @param minWeight voxels observed with less accumulated confidence are treated as unknown.
     */
    fun extractMesh(minWeight: Float = 1f): Mesh {
        val cellToVertex = LongIntMap(64 * 1024)
        val cellKeys = LongList(64 * 1024)
        val cellMasks = IntList(64 * 1024)
        val pos = FloatList(192 * 1024)
        val col = ByteList(192 * 1024)

        val neighbours = arrayOfNulls<Block>(8)
        val cornerD = FloatArray(8)
        val cornerBlock = arrayOfNulls<Block>(8)
        val cornerIdx = IntArray(8)

        // Pass 1: one vertex per cell with a sign change among its 8 (all observed) corners.
        for ((key, block) in blocks) {
            val bx = unpackX(key)
            val by = unpackY(key)
            val bz = unpackZ(key)
            for (n in 0 until 8) {
                neighbours[n] = if (n == 0) block else blocks[packXyz(bx + (n and 1), by + ((n shr 1) and 1), bz + ((n shr 2) and 1))]
            }
            for (lz in 0 until BLOCK_DIM) for (ly in 0 until BLOCK_DIM) for (lx in 0 until BLOCK_DIM) {
                var mask = 0
                var valid = true
                for (cIdx in 0 until 8) {
                    val x = lx + (cIdx and 1)
                    val y = ly + ((cIdx shr 1) and 1)
                    val z = lz + ((cIdx shr 2) and 1)
                    val nb = (if (x == BLOCK_DIM) 1 else 0) or (if (y == BLOCK_DIM) 2 else 0) or (if (z == BLOCK_DIM) 4 else 0)
                    val b = neighbours[nb]
                    if (b == null) {
                        valid = false
                        break
                    }
                    val vi = (x and 7) + BLOCK_DIM * ((y and 7) + BLOCK_DIM * (z and 7))
                    if (b.weight[vi] < minWeight) {
                        valid = false
                        break
                    }
                    val d = b.tsdf[vi]
                    cornerD[cIdx] = d
                    cornerBlock[cIdx] = b
                    cornerIdx[cIdx] = vi
                    if (d < 0f) mask = mask or (1 shl cIdx)
                }
                if (!valid || mask == 0 || mask == 0xFF) continue

                // Average of the edge crossings, in cell-local coordinates [0, 1]^3.
                var sx = 0f
                var sy = 0f
                var sz = 0f
                var crossings = 0
                for (e in EDGES.indices step 2) {
                    val a = EDGES[e]
                    val b = EDGES[e + 1]
                    val da = cornerD[a]
                    val db = cornerD[b]
                    if ((da < 0f) == (db < 0f)) continue
                    val t = da / (da - db)
                    sx += (a and 1) + t * ((b and 1) - (a and 1))
                    sy += ((a shr 1) and 1) + t * (((b shr 1) and 1) - ((a shr 1) and 1))
                    sz += ((a shr 2) and 1) + t * (((b shr 2) and 1) - ((a shr 2) and 1))
                    crossings++
                }
                val gx = bx * BLOCK_DIM + lx
                val gy = by * BLOCK_DIM + ly
                val gz = bz * BLOCK_DIM + lz
                pos.add((gx + 0.5f + sx / crossings) * voxelSize)
                pos.add((gy + 0.5f + sy / crossings) * voxelSize)
                pos.add((gz + 0.5f + sz / crossings) * voxelSize)

                // Colour: corners closest to the surface dominate.
                var r = 0f
                var g = 0f
                var bl = 0f
                var ws = 0f
                for (cIdx in 0 until 8) {
                    val cb = cornerBlock[cIdx]!!
                    val vi = cornerIdx[cIdx]
                    if (cb.colorWeight[vi] <= 0f) continue
                    val wgt = 1f / (abs(cornerD[cIdx]) + 0.05f)
                    r += (cb.rgb[vi * 3].toInt() and 0xFF) * wgt
                    g += (cb.rgb[vi * 3 + 1].toInt() and 0xFF) * wgt
                    bl += (cb.rgb[vi * 3 + 2].toInt() and 0xFF) * wgt
                    ws += wgt
                }
                if (ws > 0f) {
                    col.add((r / ws + 0.5f).toInt().toByte())
                    col.add((g / ws + 0.5f).toInt().toByte())
                    col.add((bl / ws + 0.5f).toInt().toByte())
                } else {
                    col.add(NO_COLOR)
                    col.add(NO_COLOR)
                    col.add(NO_COLOR)
                }

                val cellKey = packXyz(gx, gy, gz)
                cellToVertex.put(cellKey, cellKeys.size)
                cellKeys.add(cellKey)
                cellMasks.add(mask)
            }
        }

        // Pass 2: for each voxel edge leaving corner 0 of a cell along +x, +y, +z that crosses the
        // surface, connect the four cells sharing that edge. Winding makes normals point to free space.
        val tris = IntList(cellKeys.size * 6)
        val quad = IntArray(4)
        for (i in 0 until cellKeys.size) {
            val key = cellKeys[i]
            val mask = cellMasks[i]
            val gx = unpackX(key)
            val gy = unpackY(key)
            val gz = unpackZ(key)
            val inside0 = mask and 1 != 0
            for (axis in 0 until 3) {
                val other = 1 shl axis // corner index of (gx,gy,gz) + unit step along axis
                val insideOther = mask and (1 shl other) != 0
                if (inside0 == insideOther) continue
                val ok = when (axis) {
                    0 -> fillQuad(cellToVertex, quad, gx, gy - 1, gz - 1, gx, gy, gz - 1, gx, gy, gz, gx, gy - 1, gz)
                    1 -> fillQuad(cellToVertex, quad, gx - 1, gy, gz - 1, gx - 1, gy, gz, gx, gy, gz, gx, gy, gz - 1)
                    else -> fillQuad(cellToVertex, quad, gx - 1, gy - 1, gz, gx, gy - 1, gz, gx, gy, gz, gx - 1, gy, gz)
                }
                if (!ok) continue
                // The listed order is counter-clockwise seen from +axis, i.e. the normal points along
                // +axis, which is correct when the start corner is inside and the far corner outside.
                if (inside0) {
                    tris.add(quad[0]); tris.add(quad[1]); tris.add(quad[2])
                    tris.add(quad[0]); tris.add(quad[2]); tris.add(quad[3])
                } else {
                    tris.add(quad[0]); tris.add(quad[2]); tris.add(quad[1])
                    tris.add(quad[0]); tris.add(quad[3]); tris.add(quad[2])
                }
            }
        }

        val positions = pos.toArray()
        val indices = tris.toArray()
        return Mesh(positions, Mesh.computeNormals(positions, indices), col.toArray(), indices)
    }

    private fun fillQuad(
        map: LongIntMap,
        out: IntArray,
        x0: Int, y0: Int, z0: Int,
        x1: Int, y1: Int, z1: Int,
        x2: Int, y2: Int, z2: Int,
        x3: Int, y3: Int, z3: Int,
    ): Boolean {
        out[0] = map.get(packXyz(x0, y0, z0)).also { if (it < 0) return false }
        out[1] = map.get(packXyz(x1, y1, z1)).also { if (it < 0) return false }
        out[2] = map.get(packXyz(x2, y2, z2)).also { if (it < 0) return false }
        out[3] = map.get(packXyz(x3, y3, z3)).also { if (it < 0) return false }
        return true
    }

    companion object {
        const val BLOCK_DIM = 8
        private const val VOXELS = BLOCK_DIM * BLOCK_DIM * BLOCK_DIM
        private const val NO_COLOR: Byte = 0xB4.toByte()

        /** The 12 cube edges as pairs of corner indices (corner = x + 2y + 4z). */
        private val EDGES = intArrayOf(
            0, 1, 2, 3, 4, 5, 6, 7, // along x
            0, 2, 1, 3, 4, 6, 5, 7, // along y
            0, 4, 1, 5, 2, 6, 3, 7, // along z
        )
    }
}
