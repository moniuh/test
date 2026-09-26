package com.moniuh.scan3d.scan

import android.os.SystemClock
import android.util.Log
import com.moniuh.scan3d.core.DepthFrame
import com.moniuh.scan3d.core.Mesh
import com.moniuh.scan3d.core.MeshCleanup
import com.moniuh.scan3d.core.PointCloud
import com.moniuh.scan3d.core.TsdfVolume
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the TSDF volume and runs all work on it (fusion, preview, mesh extraction) on a single
 * background thread, so the volume itself needs no locking.
 */
class ScanEngine(initialPreset: ScanPreset) {

    data class Stats(
        val frames: Int,
        val blocks: Int,
        val megabytes: Int,
        val previewPoints: Int,
        val memoryFull: Boolean,
    )

    class Preview(val cloud: PointCloud, val version: Int)

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "scan-fusion").apply { priority = Thread.NORM_PRIORITY + 1 }
    }
    private val busy = AtomicBoolean(false)

    @Volatile
    var preset: ScanPreset = initialPreset
        private set

    @Volatile
    var stats = Stats(0, 0, 0, 0, false)
        private set

    @Volatile
    var preview: Preview? = null
        private set

    private var volume = TsdfVolume(initialPreset.voxelSize)
    private var frames = 0
    private var previewVersion = 0
    private var lastPreviewMs = 0L
    private val memoryBudget = (Runtime.getRuntime().maxMemory() * 0.55).toLong()

    /** True while a frame is being fused; callers should skip capturing new frames meanwhile. */
    val isBusy: Boolean get() = busy.get()

    /** Queues a frame for fusion. Returns false (and drops the frame) if the engine is busy. */
    fun submit(frame: DepthFrame): Boolean {
        if (stats.memoryFull) return false
        if (!busy.compareAndSet(false, true)) return false
        executor.execute {
            try {
                val p = preset
                if (volume.integrate(frame, p.minDepth, p.maxDepth, p.minConfidence) > 0) frames++
                val now = SystemClock.uptimeMillis()
                if (now - lastPreviewMs > PREVIEW_INTERVAL_MS) {
                    lastPreviewMs = now
                    refreshPreview()
                }
                publishStats()
            } catch (t: Throwable) {
                Log.e(TAG, "Fusion failed", t)
            } finally {
                busy.set(false)
            }
        }
        return true
    }

    /** Clears the scan, optionally switching to another preset. */
    fun reset(newPreset: ScanPreset = preset) {
        executor.execute {
            preset = newPreset
            volume = TsdfVolume(newPreset.voxelSize)
            frames = 0
            refreshPreview()
            publishStats()
        }
    }

    /** Extracts and cleans the mesh on the fusion thread, then runs [onMesh] there too. */
    fun withMesh(onMesh: (Mesh, ScanPreset) -> Unit) {
        executor.execute {
            val p = preset
            val raw = volume.extractMesh(p.meshMinWeight)
            val mesh = MeshCleanup.removeSmallComponents(raw, p.minComponentTriangles)
            onMesh(mesh, p)
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun refreshPreview() {
        val cloud = volume.extractSurfaceVoxels(PREVIEW_MIN_WEIGHT, MAX_PREVIEW_POINTS)
        preview = Preview(cloud, ++previewVersion)
    }

    private fun publishStats() {
        val bytes = volume.approximateBytes
        stats = Stats(
            frames = frames,
            blocks = volume.blockCount,
            megabytes = (bytes / (1024 * 1024)).toInt(),
            previewPoints = preview?.cloud?.size ?: 0,
            memoryFull = bytes > memoryBudget,
        )
    }

    companion object {
        private const val TAG = "ScanEngine"
        private const val PREVIEW_INTERVAL_MS = 400L
        private const val PREVIEW_MIN_WEIGHT = 1f
        private const val MAX_PREVIEW_POINTS = 200_000
    }
}
