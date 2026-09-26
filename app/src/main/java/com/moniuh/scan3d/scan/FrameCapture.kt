package com.moniuh.scan3d.scan

import android.media.Image
import com.google.ar.core.Camera
import com.moniuh.scan3d.core.DepthFrame
import java.nio.ByteOrder

/** Copies ARCore depth, confidence and colour images into a [DepthFrame] for the fusion thread. */
object FrameCapture {

    /**
     * @param depth DEPTH16 image (millimetres), raw or smoothed.
     * @param confidence raw depth confidence (8 bit), or null for smoothed depth.
     * @param color YUV_420_888 CPU camera image, or null if unavailable.
     */
    fun capture(depth: Image, confidence: Image?, color: Image?, camera: Camera): DepthFrame {
        val w = depth.width
        val h = depth.height

        val depthPlane = depth.planes[0]
        val depthBuf = depthPlane.buffer.order(ByteOrder.LITTLE_ENDIAN)
        val depthRow = depthPlane.rowStride
        val depthPix = depthPlane.pixelStride
        val depthMm = ShortArray(w * h)
        for (y in 0 until h) {
            val row = y * depthRow
            for (x in 0 until w) depthMm[y * w + x] = depthBuf.getShort(row + x * depthPix)
        }

        val conf = confidence?.let { img ->
            val plane = img.planes[0]
            val buf = plane.buffer
            val sx = img.width.toFloat() / w
            val sy = img.height.toFloat() / h
            ByteArray(w * h) { i ->
                val x = ((i % w + 0.5f) * sx).toInt().coerceAtMost(img.width - 1)
                val y = ((i / w + 0.5f) * sy).toInt().coerceAtMost(img.height - 1)
                buf.get(y * plane.rowStride + x * plane.pixelStride)
            }
        }

        val rgb = color?.let { sampleYuv(it, w, h) }

        // Depth is pixel-aligned with the camera texture; scale its intrinsics to the depth resolution.
        val intr = camera.textureIntrinsics
        val focal = intr.focalLength
        val principal = intr.principalPoint
        val dims = intr.imageDimensions
        val scaleX = w.toFloat() / dims[0]
        val scaleY = h.toFloat() / dims[1]

        val pose = FloatArray(16)
        camera.pose.toMatrix(pose, 0)

        return DepthFrame(
            width = w,
            height = h,
            depthMm = depthMm,
            confidence = conf,
            rgb = rgb,
            fx = focal[0] * scaleX,
            fy = focal[1] * scaleY,
            cx = principal[0] * scaleX,
            cy = principal[1] * scaleY,
            cameraToWorld = pose,
        )
    }

    /** Samples a YUV_420_888 image at the centres of a [w] x [h] grid and converts to RGB. */
    private fun sampleYuv(img: Image, w: Int, h: Int): ByteArray {
        val yPlane = img.planes[0]
        val uPlane = img.planes[1]
        val vPlane = img.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val iw = img.width
        val ih = img.height
        val out = ByteArray(w * h * 3)
        for (y in 0 until h) {
            val sy = ((y + 0.5f) * ih / h).toInt().coerceAtMost(ih - 1)
            for (x in 0 until w) {
                val sx = ((x + 0.5f) * iw / w).toInt().coerceAtMost(iw - 1)
                val yy = (yBuf.get(sy * yPlane.rowStride + sx * yPlane.pixelStride).toInt() and 0xFF).toFloat()
                val uvRow = sy / 2
                val uvCol = sx / 2
                val u = (uBuf.get(uvRow * uPlane.rowStride + uvCol * uPlane.pixelStride).toInt() and 0xFF) - 128f
                val v = (vBuf.get(uvRow * vPlane.rowStride + uvCol * vPlane.pixelStride).toInt() and 0xFF) - 128f
                val o = (y * w + x) * 3
                out[o] = clamp(yy + 1.402f * v)
                out[o + 1] = clamp(yy - 0.344136f * u - 0.714136f * v)
                out[o + 2] = clamp(yy + 1.772f * u)
            }
        }
        return out
    }

    private fun clamp(v: Float): Byte = v.toInt().coerceIn(0, 255).toByte()
}
