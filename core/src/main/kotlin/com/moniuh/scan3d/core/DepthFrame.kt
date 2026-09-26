package com.moniuh.scan3d.core

/**
 * A single depth observation copied out of the AR framework, ready to be fused.
 *
 * Camera convention (same as ARCore / OpenGL): the camera looks down -Z, +Y is up and
 * +X is right in the image. Pixel (u, v) has its origin in the top-left corner of the image.
 *
 * @param depthMm depth along the optical axis in millimetres, row-major, 0 = no data.
 * @param confidence per-pixel confidence 0..255 (unsigned bytes), or null if every pixel is trusted.
 * @param rgb per-pixel colour (3 unsigned bytes per pixel) sampled from the colour camera, or null.
 * @param cameraToWorld column-major 4x4 rigid transform (as produced by `Pose.toMatrix`).
 */
class DepthFrame(
    val width: Int,
    val height: Int,
    val depthMm: ShortArray,
    val confidence: ByteArray?,
    val rgb: ByteArray?,
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val cameraToWorld: FloatArray,
) {
    init {
        require(depthMm.size == width * height) { "depth size mismatch" }
        require(confidence == null || confidence.size == width * height) { "confidence size mismatch" }
        require(rgb == null || rgb.size == width * height * 3) { "rgb size mismatch" }
        require(cameraToWorld.size == 16) { "cameraToWorld must be a 4x4 matrix" }
    }

    /** Inverse of [cameraToWorld], assuming it is rigid (rotation + translation). */
    val worldToCamera: FloatArray = invertRigid(cameraToWorld)

    fun depthMetersAt(index: Int): Float = (depthMm[index].toInt() and 0xFFFF) * 0.001f

    fun confidenceAt(index: Int): Int = confidence?.let { it[index].toInt() and 0xFF } ?: 255

    companion object {
        fun invertRigid(m: FloatArray): FloatArray {
            // Column-major: element (row r, col c) is m[c * 4 + r].
            val out = FloatArray(16)
            for (r in 0 until 3) for (c in 0 until 3) out[c * 4 + r] = m[r * 4 + c]
            val tx = m[12]
            val ty = m[13]
            val tz = m[14]
            for (r in 0 until 3) {
                out[12 + r] = -(out[r] * tx + out[4 + r] * ty + out[8 + r] * tz)
            }
            out[15] = 1f
            return out
        }
    }
}
