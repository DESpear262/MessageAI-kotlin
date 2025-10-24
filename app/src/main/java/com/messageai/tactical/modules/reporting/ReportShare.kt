package com.messageai.tactical.modules.reporting

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ReportShare {
    fun shareMarkdown(context: Context, fileName: String, content: String) {
        val cacheDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(cacheDir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report"))
    }
}


