package com.moniuh.scan3d.scan

import androidx.annotation.StringRes
import com.moniuh.scan3d.R

/** Scan quality presets: voxel size trades detail for memory and scan volume. */
enum class ScanPreset(
    @StringRes val label: Int,
    val voxelSize: Float,
    val minDepth: Float,
    val maxDepth: Float,
    /** Raw-depth confidence threshold, 0..255. */
    val minConfidence: Int,
    /** Accumulated confidence required before a voxel counts as observed in the exported mesh. */
    val meshMinWeight: Float,
    /** Connected mesh pieces smaller than this (triangles) are treated as noise. */
    val minComponentTriangles: Int,
) {
    OBJECT(R.string.preset_object, 0.005f, 0.15f, 1.2f, 140, 2.0f, 400),
    STANDARD(R.string.preset_standard, 0.01f, 0.15f, 2.5f, 128, 1.5f, 200),
    ROOM(R.string.preset_room, 0.02f, 0.2f, 4.5f, 110, 1.5f, 100),
}
