package com.finndev.master.system.inspector.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Save-to-Downloads / share helpers used by exporting modules (28, 55, 60, 64 ...). */
object Exports {

    fun saveToDownloads(context: Context, fileName: String, content: ByteArray): Result<File> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(fileName))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MSI")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert failed")
                resolver.openOutputStream(uri)?.use { it.write(content) } ?: error("stream failed")
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MSI/$fileName")
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MSI")
                dir.mkdirs()
                val f = File(dir, fileName)
                f.writeBytes(content)
                f
            }
        }
    }

    fun saveTextToDownloads(context: Context, fileName: String, text: String): Result<File> =
        saveToDownloads(context, fileName, text.toByteArray(Charsets.UTF_8))

    fun shareFile(context: Context, file: File, mime: String = mimeOf(file.name)) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, file.name))
    }

    fun shareText(context: Context, text: String, subject: String = "MSI") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    }

    fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "txt", "log" -> "text/plain"
        "md" -> "text/markdown"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "zip" -> "application/zip"
        "gz" -> "application/gzip"
        "apk" -> "application/vnd.android.package-archive"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "iso" -> "application/octet-stream"
        "sh" -> "application/x-sh"
        else -> "application/octet-stream"
    }

    /** Public base folder for MSI user files (needs MANAGE_EXTERNAL_STORAGE on 11+). */
    fun msiPublicDir(name: String = ""): File {
        val base = File(Environment.getExternalStorageDirectory(), "MSI" +
                if (name.isBlank()) "" else "/$name")
        base.mkdirs()
        return base
    }

    /** app-private logs dir (module 28). */
    fun logsDir(context: Context): File = File(context.filesDir, "logs").apply { mkdirs() }

    fun saveLog(context: Context, name: String, lines: List<String>): File {
        val f = File(logsDir(context), name)
        f.writeText(lines.joinToString("\n"))
        return f
    }
}
