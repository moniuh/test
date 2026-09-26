package com.moniuh.scan3d.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.moniuh.scan3d.core.ExportFormat
import com.moniuh.scan3d.core.Exporters
import com.moniuh.scan3d.core.Mesh
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves meshes into the public Downloads/Skaner3D folder (no storage permission needed). */
object ExportManager {

    const val FOLDER = "Skaner3D"

    data class SavedFile(val uri: Uri, val displayName: String, val format: ExportFormat)

    fun newBaseName(): String =
        "skan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun save(context: Context, baseName: String, format: ExportFormat, mesh: Mesh): SavedFile {
        val suffix = if (format == ExportFormat.PLY_POINTS) "_punkty" else ""
        val displayName = "$baseName$suffix.${format.extension}"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            // Generic type keeps MediaStore from rewriting the .ply/.obj/.stl extension.
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Cannot create $displayName in Downloads")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot open $displayName")
            out.use { Exporters.write(format, mesh, it) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return SavedFile(uri, displayName, format)
    }
}
