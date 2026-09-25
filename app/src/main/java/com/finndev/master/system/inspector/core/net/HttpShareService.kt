package com.finndev.master.system.inspector.core.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.finndev.master.system.inspector.MainActivity
import com.finndev.master.system.inspector.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/** Local HTTP file sharing server engine (module 52). */
object HttpServer {
    @Volatile var running = false
        private set
    @Volatile var rootDir: File = File("/sdcard")
    @Volatile var port: Int = 8080
    val clients = AtomicInteger(0)
    val served = AtomicInteger(0)
    val log = ConcurrentLinkedDeque<String>()

    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null

    fun baseUrl(): String {
        val ip = localIp() ?: "0.0.0.0"
        return "http://$ip:$port"
    }

    fun localIp(): String? = runCatching {
        val all = mutableListOf<java.net.InetAddress>()
        NetworkInterface.getNetworkInterfaces().toList().forEach { all.addAll(it.inetAddresses.toList()) }
        all.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress
    }.getOrNull()

    fun start(context: Context, dir: File, port: Int, onLog: (String) -> Unit = {}) {
        if (running) return
        rootDir = dir
        HttpServer.port = port
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serverSocket = ServerSocket()
        serverSocket!!.reuseAddress = true
        serverSocket!!.bind(InetSocketAddress(port))
        running = true
        log.clear(); served.set(0)
        onLog("listening on $port, root=$dir")
        scope!!.launch {
            while (running) {
                val client = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                clients.incrementAndGet()
                launch { runCatching { handle(client) } }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope?.cancel()
        scope = null
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 30_000
            val input = s.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val rawPath = parts[1].substringBefore('?')
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
            val decoded = URLDecoder.decode(rawPath, "UTF-8")
            val rel = decoded.removePrefix("/").ifBlank { "" }
            val safeRel = rel.split('/').filter { it.isNotBlank() && it != ".." }.joinToString("/")
            val target = if (safeRel.isBlank()) rootDir else File(rootDir, safeRel)

            val status: Int
            val headersOut = mutableMapOf<String, String>()
            lateinit var body: (OutputStream) -> Unit
            var partial = false

            when {
                method != "GET" && method != "HEAD" -> {
                    status = 405; body = { }
                }
                !target.exists() -> {
                    status = 404; body = { it.write("404 Not Found".toByteArray()) }
                }
                target.isDirectory -> {
                    status = 200
                    headersOut["Content-Type"] = "text/html; charset=utf-8"
                    val html = dirListingHtml(target, decoded)
                    body = { out -> out.write(html.toByteArray()) }
                }
                else -> {
                    status = 200
                    headersOut["Content-Type"] = mimeOf(target.name)
                    headersOut["Accept-Ranges"] = "bytes"
                    val range = parseRange(headers["range"], target.length())
                    if (range != null) {
                        partial = true
                        headersOut["Content-Range"] = "bytes ${range.first}-${range.second}/${target.length()}"
                        body = { out -> sendRange(out, target, range.first, range.second) }
                    } else {
                        body = { out ->
                            BufferedInputStream(FileInputStream(target), 64 * 1024).use { it.copyTo(out, 64 * 1024) }
                        }
                    }
                }
            }
            val statusLine = when {
                status == 200 && partial -> "206 Partial Content"
                status == 200 -> "200 OK"
                status == 404 -> "404 Not Found"
                status == 405 -> "405 Method Not Allowed"
                else -> "500 Internal Error"
            }
            val head = buildString {
                append("HTTP/1.1 ").append(statusLine).append("\r\n")
                headersOut.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
                if (!headersOut.containsKey("Content-Range") && !headersOut.containsKey("Content-Type")) {
                    append("Content-Type: application/octet-stream\r\n")
                }
                if (target.isFile && !partial) append("Content-Length: ").append(target.length()).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            val out = s.getOutputStream()
            out.write(head.toByteArray())
            if (method != "HEAD") body(out)
            out.flush()
            served.incrementAndGet()
            log.add("$method ${decoded.take(60)} -> $statusLine (${s.inetAddress.hostAddress})")
            if (log.size > 120) log.removeFirst()
        }
    }

    private fun parseRange(header: String?, length: Long): Pair<Long, Long>? {
        if (header == null) return null
        val m = Regex("bytes=(\\d*)-(\\d*)").find(header) ?: return null
        val start = m.groupValues[1].toLongOrNull() ?: 0L
        val end = m.groupValues[2].toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)
        if (start > end || start >= length) return null
        return start to end
    }

    private fun sendRange(out: OutputStream, file: File, start: Long, end: Long) {
        FileInputStream(file).use { input ->
            var skipped = 0L
            while (skipped < start) {
                val n = input.skip(start - skipped)
                if (n <= 0) break
                skipped += n
            }
            var left = end - start + 1
            val buf = ByteArray(64 * 1024)
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buf, 0, n); left -= n
            }
        }
    }

    private fun dirListingHtml(dir: File, path: String): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>MSI Share — ")
        sb.append(dir.name).append("</title>")
        sb.append("<style>body{font-family:monospace;background:#0A0A0A;color:#00E676;padding:24px}")
        sb.append("a{color:#00E5FF;text-decoration:none;display:block;padding:4px 0}a:hover{text-decoration:underline}")
        sb.append("h1{color:#00E676;border-bottom:1px solid #1B3A26;padding-bottom:8px}</style></head><body>")
        sb.append("<h1>MSI Local Share — /").append(dir.name).append("</h1>")
        val parent = path.trimEnd('/').substringBeforeLast('/')
        if (path.trim('/').isNotEmpty()) sb.append("<a href=\"").append(parent).append("\">[ ../ ]</a>")
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        files.forEach { f ->
            val href = (path.trimEnd('/') + "/" + java.net.URLEncoder.encode(f.name, "UTF-8")).removePrefix("/")
            if (f.isDirectory) sb.append("<a href=\"/").append(href).append("\">[DIR] ").append(f.name).append("/</a>")
            else sb.append("<a href=\"/").append(href).append("\">").append(f.name).append(" — ")
                .append(com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(f.length())).append("</a>")
        }
        if (files.isEmpty()) sb.append("<p>(empty)</p>")
        sb.append("<hr><p>MSI v").append(com.finndev.master.system.inspector.BuildConfig.VERSION_NAME)
        sb.append(" — Master System Inspector</p></body></html>")
        return sb.toString()
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "txt", "log", "md" -> "text/plain"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        "iso" -> "application/octet-stream"
        else -> "application/octet-stream"
    }
}

/** Foreground service wrapper keeping the HTTP server alive (module 52). */
class HttpShareService : Service() {

    companion object {
        const val CHANNEL_ID = "msi_http_server"
        const val EXTRA_DIR = "dir"
        const val EXTRA_PORT = "port"
        const val NOTIF_ID = 4210
        var isActive = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        val dir = intent?.getStringExtra(EXTRA_DIR) ?: "/sdcard/MSI-Shared"
        val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
        startAsForeground(port)
        isActive = true
        File(dir).mkdirs()
        HttpServer.start(this, File(dir), port)
        return START_STICKY
    }

    private fun startAsForeground(port: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "MSI HTTP Server", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.app_name) + " — HTTP Server")
            .setContentText(HttpServer.baseUrl())
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        isActive = false
        HttpServer.stop()
        super.onDestroy()
    }
}
