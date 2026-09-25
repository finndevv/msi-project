package com.finndev.master.system.inspector.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import java.util.Locale

/** Centralized hardware/system profiling shared by modules 14, 60, 65, 66, 71, 78. */
object SystemInfo {

    data class CpuCore(
        val index: Int, val online: Boolean, val curFreqKHz: Long,
        val minFreqKHz: Long, val maxFreqKHz: Long, val governor: String,
    )

    data class StorageRow(val path: String, val totalBytes: Long, val freeBytes: Long) {
        val usedBytes: Long get() = totalBytes - freeBytes
    }

    fun cpuCount(): Int = Runtime.getRuntime().availableProcessors()

    fun readSys(path: String): String? = runCatching { File(path).readText().trim() }.getOrNull()

    suspend fun readSysDeep(path: String): String? {
        readSys(path)?.let { return it }
        return Shell.readFile(path)?.trim()
    }

    fun cpuCoresNow(): List<CpuCore> {
        val n = cpuCount()
        return (0 until n).map { i ->
            val base = "/sys/devices/system/cpu/cpu$i"
            val online = if (i == 0) true else (readSys("$base/online")?.toIntOrNull() ?: 1) == 1
            CpuCore(
                index = i,
                online = online,
                curFreqKHz = readSys("$base/cpufreq/scaling_cur_freq")?.toLongOrNull() ?: 0,
                minFreqKHz = readSys("$base/cpufreq/scaling_min_freq")?.toLongOrNull() ?: 0,
                maxFreqKHz = readSys("$base/cpufreq/cpuinfo_max_freq")?.toLongOrNull() ?: 0,
                governor = readSys("$base/cpufreq/scaling_governor") ?: "-",
            )
        }
    }

    fun cpuModel(): String {
        val cpuinfo = readSys("/proc/cpuinfo") ?: return "unknown"
        for (key in listOf("model name", "Hardware", "Processor", "cpu model")) {
            val line = cpuinfo.lineSequence().firstOrNull { it.startsWith(key, true) } ?: continue
            return line.substringAfter(':').trim().take(60)
        }
        return "unknown"
    }

    /** SoC identification via system properties. */
    suspend fun socInfo(): Pair<String, String> {
        val manufacturer = getprop("ro.soc.manufacturer").ifBlank { Build.MANUFACTURER }
        val model = getprop("ro.soc.model").ifBlank { Build.HARDWARE }
        return manufacturer to model
    }

    suspend fun getprop(key: String): String =
        Shell.sh("getprop $key", 6000).stdout.trim()

    fun gpuInfo(ctx: Context): String {
        val gl = runCatching {
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .deviceConfigurationInfo.glEsVersion
        }.getOrDefault("?")
        val egl = readSysPropDirect("ro.hardware.egl")
        return "OpenGL ES $gl${if (egl.isNotBlank()) " / $egl" else ""}"
    }

    private fun readSysPropDirect(key: String): String = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
            .invoke(null, key) as String ?: ""
    }.getOrDefault("")

    /** L1/L2/L3 cache descriptor rows read from sysfs. */
    fun cacheInfo(): List<Triple<String, String, String>> {
        val rows = mutableListOf<Triple<String, String, String>>()
        for (i in 0 until cpuCount()) {
            for (idx in 0 until 8) {
                val base = "/sys/devices/system/cpu/cpu$i/cache/index$idx"
                val level = readSys("$base/level") ?: break
                val type = readSys("$base/type") ?: "unknown"
                val size = readSys("$base/size") ?: "?"
                rows += Triple("cpu$i", "L$level $type", size)
            }
            if (i > 1 && rows.isEmpty()) break // sysfs caches unavailable
        }
        return rows.distinctBy { it.second + it.third }
    }

    fun memInfoMap(): Map<String, Long> {
        val map = linkedMapOf<String, Long>()
        readSys("/proc/meminfo")?.lineSequence()?.forEach { line ->
            val k = line.substringBefore(':')
            val v = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L
            map[k] = v * 1024 // meminfo is in kB
        }
        return map
    }

    fun storageRows(): List<StorageRow> {
        val paths = linkedSetOf(
            Environment.getDataDirectory().absolutePath,
            Environment.getExternalStorageDirectory().absolutePath,
            Environment.getDownloadCacheDirectory().absolutePath,
        )
        return paths.mapNotNull { p ->
            runCatching {
                val s = StatFs(p)
                StorageRow(p, s.totalBytes, s.availableBytes)
            }.getOrNull()
        }
    }

    fun uptimeInfo(): Triple<Long, Long, Long> {
        // (realtime since boot incl. sleep, uptime, deep sleep)
        val real = SystemClock.elapsedRealtime()
        val up = SystemClock.uptimeMillis()
        return Triple(real, up, real - up)
    }

    fun androidSecurityPatch(): String =
        if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "N/A"

    fun fmtBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        if (gb < 1024) return String.format(Locale.US, "%.2f GB", gb)
        return String.format(Locale.US, "%.2f TB", gb / 1024.0)
    }

    fun fmtHz(khz: Long): String = when {
        khz <= 0 -> "-"
        khz >= 1_000_000 -> String.format(Locale.US, "%.2f GHz", khz / 1_000_000.0)
        khz >= 1000 -> String.format(Locale.US, "%d MHz", khz / 1000)
        else -> "$khz kHz"
    }

    /** Full diagnostic snapshot as text (module 60 / 64). */
    suspend fun diagnosticText(ctx: Context): String = buildString {
        appendLine("MSI (Master System Inspector) v${com.finndev.master.system.inspector.BuildConfig.VERSION_NAME} — Diagnostic Report")
        appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date())}")
        appendLine()
        appendLine("== DEVICE ==")
        appendLine("Brand: ${Build.BRAND}  Model: ${Build.MODEL}  Device: ${Build.DEVICE}")
        appendLine("Board: ${Build.BOARD}  Hardware: ${Build.HARDWARE}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}, patch ${androidSecurityPatch()})")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        val soc = socInfo(); appendLine("SoC: ${soc.first} ${soc.second}")
        appendLine("CPU: ${cpuModel()} x ${cpuCount()} cores")
        appendLine("GPU: ${gpuInfo(ctx)}")
        appendLine("Root: " + (AppState.rootState.value?.let { if (it.rooted) it.manager else "no" } ?: "unknown"))
        appendLine()
        appendLine("== MEMORY ==")
        val mi = memInfoMap()
        val total = mi["MemTotal"] ?: 0; val avail = mi["MemAvailable"] ?: 0
        appendLine("RAM total: ${fmtBytes(total)}  available: ${fmtBytes(avail)}")
        appendLine("Swap: ${fmtBytes(mi["SwapTotal"] ?: 0)} free ${fmtBytes(mi["SwapFree"] ?: 0)}")
        appendLine("Cached: ${fmtBytes(mi["Cached"] ?: 0)}  Buffers: ${fmtBytes(mi["Buffers"] ?: 0)}")
        appendLine()
        appendLine("== STORAGE ==")
        storageRows().forEach { appendLine("${it.path}: ${fmtBytes(it.usedBytes)} / ${fmtBytes(it.totalBytes)}") }
        appendLine()
        appendLine("== CPU ==")
        cpuCoresNow().forEach {
            appendLine("cpu${it.index}: ${if (it.online) "online" else "offline"} ${fmtHz(it.curFreqKHz)} [${fmtHz(it.minFreqKHz)}-${fmtHz(it.maxFreqKHz)}] ${it.governor}")
        }
        appendLine()
        appendLine("== UPTIME ==")
        val (real, up, deep) = uptimeInfo()
        appendLine("elapsed: ${real / 1000}s  uptime: ${up / 1000}s  deep-sleep: ${deep / 1000}s")
        appendLine()
        appendLine("== ALPINE / PROOT ==")
        appendLine(PRootEngine.statusLine(ctx))
    }
}
