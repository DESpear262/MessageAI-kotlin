package com.messageai.tactical.data.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * ImageService handles: picking URIs, copying to app cache for retry, EXIF strip, optional
 * HEIC→JPEG decode, resize/compress, and upload to Firebase Storage. Returns a gs/http URL.
 */
@Singleton
class ImageService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: FirebaseStorage
) {
    data class ProcessOptions(
        val maxEdge: Int = 2048,
        val jpegQuality: Int = 85
    )

    /** Copies a content [uri] into app cache to ensure stability across restarts. */
    fun persistToCache(uri: Uri): File {
        val cacheDir = File(context.cacheDir, "images").apply { mkdirs() }
        val outFile = File(cacheDir, UUID.randomUUID().toString() + ".jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            FileOutputStream(outFile).use { output ->
                input?.copyTo(output)
            }
        }
        return outFile
    }

    /** Loads bitmap from a file or content URI, handles HEIC on API 28+. */
    private fun decodeBitmap(resolver: ContentResolver, source: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(resolver, source)
            ImageDecoder.decodeBitmap(src)
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(resolver, source)
        }
    }

    /** Resizes the bitmap maintaining aspect ratio so the longer edge ≤ maxEdge. */
    private fun resizeBitmap(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val scale = maxOf(w, h).toFloat() / maxEdge.toFloat()
        if (scale <= 1f) return src
        val newW = (w / scale).toInt()
        val newH = (h / scale).toInt()
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    /** Compresses to JPEG at quality; strips EXIF by re-encoding. */
    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }

    /** Returns image byte size; used for warnings. */
    fun uriSize(resolver: ContentResolver, uri: Uri): Long {
        resolver.openAssetFileDescriptor(uri, "r").use { afd ->
            return afd?.length ?: -1
        }
    }

    /** Processes and uploads; returns the public download URL. */
    suspend fun processAndUpload(chatId: String, messageId: String, senderId: String, sourceUri: Uri, options: ProcessOptions = ProcessOptions()): String {
        val resolver = context.contentResolver
        val bitmap = decodeBitmap(resolver, sourceUri)
        val resized = resizeBitmap(bitmap, options.maxEdge)
        val jpegBytes = compressJpeg(resized, options.jpegQuality)

        val path = "chat-media/${'$'}chatId/${'$'}messageId.jpg"
        val ref = storage.reference.child(path)
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("chatId", chatId)
            .setCustomMetadata("messageId", messageId)
            .setCustomMetadata("senderId", senderId)
            .build()
        ref.putBytes(jpegBytes, metadata).await()
        return ref.downloadUrl.await().toString()
    }
}

