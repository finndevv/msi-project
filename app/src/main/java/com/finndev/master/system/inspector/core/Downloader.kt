package com.finndev.master.system.inspector.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** HTTP download/upload helper with progress (modules 40, 51, 58, 75, 16). */
object Downloader {

    class DownloadException(msg: String) : Exception(msg)

    private fun open(url: String, method: String = "GET"): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.requestMethod = method
        conn.setRequestProperty("User-Agent", "MSI/${com.finndev.master.system.inspector.BuildConfig.VERSION_NAME} (Android)")
        return conn
    }

    /** Download a URL to a file, streaming progress: onProgress(bytesRead, totalOrMinus1). */
    suspend fun download(
        url: String, dest: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".msitmp")
            val conn = open(url)
            if (conn.responseCode !in 200..299) throw DownloadException("HTTP ${conn.responseCode} for $url")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                    out.flush()
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw DownloadException("rename failed -> ${dest.path}")
            dest
        }
    }

    suspend fun fetchText(url: String, timeoutMs: Int = 15_000): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(url)
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            if (conn.responseCode !in 200..299) throw DownloadException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().readText()
        }
    }

    /** Content length probe (HEAD, falls back to ranged GET). */
    suspend fun contentLength(url: String): Long = withContext(Dispatchers.IO) {
        runCatching {
            val c = open(url, "HEAD")
            if (c.responseCode in 200..299 && c.contentLengthLong > 0) c.contentLengthLong
            else open(url).let { g -> g.contentLengthLong.also { g.disconnect() } }
        }.getOrDefault(-1L)
    }

    /** Measure download throughput (module 40). Returns bytes per second. */
    suspend fun measureDownload(url: String, maxBytes: Long, onTick: (bytes: Long, ms: Long) -> Unit = { _, _ -> }): Result<Double> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = open(url)
                var read = 0L; val start = System.currentTimeMillis()
                val buf = ByteArray(64 * 1024)
                conn.inputStream.use { input ->
                    while (read < maxBytes) {
                        val n = input.read(buf)
                        if (n < 0) break
                        read += n
                        onTick(read, System.currentTimeMillis() - start)
                    }
                }
                val seconds = (System.currentTimeMillis() - start) / 1000.0
                if (seconds <= 0.0) 0.0 else read / seconds
            }
        }

    /** Measure upload throughput via POST (module 40). */
    suspend fun measureUpload(url: String, payloadSize: Int, onTick: (bytes: Long, ms: Long) -> Unit = { _, _ -> }): Result<Double> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = open(url, "POST")
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(payloadSize)
                val chunk = ByteArray(64 * 1024)
                java.security.SecureRandom().nextBytes(chunk)
                var written = 0L; val start = System.currentTimeMillis()
                conn.outputStream.use { out ->
                    while (written < payloadSize) {
                        val len = minOf(chunk.size.toLong(), payloadSize - written).toInt()
                        out.write(chunk, 0, len)
                        written += len
                        onTick(written, System.currentTimeMillis() - start)
                    }
                    out.flush()
                }
                val code = conn.responseCode // consume response
                val seconds = (System.currentTimeMillis() - start) / 1000.0
                if (seconds <= 0.0) 0.0 else written / seconds
            }
        }
}
