/**
 * MessageAI – Camera helper utilities.
 *
 * Provides helper functions for camera operations including file creation
 * with proper FileProvider authorities.
 */
package com.messageai.tactical.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object CameraHelper {
    /**
     * Creates a temporary image file in app cache and returns its FileProvider URI.
     * The file is created in the cache/images/ subdirectory to match file_paths.xml.
     *
     * @param context Application context
     * @return Pair of (File, Uri) for the created image file
     */
    fun createImageFile(context: Context): Pair<File, Uri> {
        val cacheImagesDir = File(context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File(cacheImagesDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
        return Pair(imageFile, uri)
    }
}

