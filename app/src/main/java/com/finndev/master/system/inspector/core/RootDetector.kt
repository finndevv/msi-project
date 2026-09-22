package com.finndev.master.system.inspector.core

import android.content.Context
import android.content.pm.PackageManager

/** Root status details for module 1 (Advanced Root Auditor). */
data class RootInfo(
    val rooted: Boolean,
    val manager: String,        // Magisk / KernelSU / APatch / su (generic) / none
    val managerDetail: String,  // version or detail string
    val suPath: String?,
    val evidence: List<String>, // every detection hit
)

object RootDetector {
    private val suCandidates = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/magisk/.core/bin/su", "/data/adb/magisk/su", "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su", "/vendor/bin/su", "/system/sbin/su",
    )
    private val managerPackages = mapOf(
        "com.topjohnwu.magisk" to "Magisk",
        "io.github.huskydg.magisk" to "Magisk Delta",
        "me.weishu.kernelsu" to "KernelSU",
        "me.weishu.kernelsu.next" to "KernelSU Next",
        "me.bmax.apatch" to "APatch",
    )

    suspend fun detect(ctx: Context): RootInfo {
        val evidence = mutableListOf<String>()

        // 1) su binary presence (direct + root listing)
        var suPath: String? = null
        for (p in suCandidates) {
            if (java.io.File(p).exists()) { suPath = p; evidence += "su binary: $p" }
        }
        val lsAdb = Shell.su("ls -la /data/adb", 8000)
        if (lsAdb.ok) {
            evidence += "/data/adb accessible: " + lsAdb.stdout.lineSequence().joinToString(" | ").take(200)
        }

        // 2) version fingerprint from su itself
        var manager = "none"; var detail = ""
        if (suPath != null || lsAdb.ok) {
            val v = Shell.su("su -v", 6000)
            val vv = v.stdout.trim() + v.stderr.trim()
            manager = when {
                vv.contains("Magisk", true) -> "Magisk"
                vv.contains("KernelSU", true) || vv.contains("ksu", true) -> "KernelSU"
                vv.contains("APatch", true) || vv.contains("apd", true) -> "APatch"
                else -> "su (generic)"
            }
            if (vv.isNotBlank()) { detail = vv.take(80); evidence += "su -v: ${vv.take(80)}" }
            val idr = Shell.su("id", 6000)
            if (idr.ok) evidence += "id: ${idr.stdout.trim().take(80)}"
            val magiskDir = Shell.su("[ -d /data/adb/magisk ] && echo MAGISK_DIR", 5000)
            if (magiskDir.stdout.contains("MAGISK_DIR")) { evidence += "/data/adb/magisk exists"; if (manager == "none") manager = "Magisk" }
            val ksuDir = Shell.su("[ -d /data/adb/ksu ] && echo KSU_DIR", 5000)
            if (ksuDir.stdout.contains("KSU_DIR")) { evidence += "/data/adb/ksu exists"; if (manager == "none") manager = "KernelSU" }
            val apDir = Shell.su("[ -d /data/adb/ap ] && echo AP_DIR", 5000)
            if (apDir.stdout.contains("AP_DIR")) { evidence += "/data/adb/ap exists"; if (manager == "none") manager = "APatch" }
        }

        // 3) manager app presence
        val pm = ctx.packageManager
        for ((pkg, name) in managerPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                evidence += "manager app: $pkg"
                if (manager == "none" || manager == "su (generic)") manager = name
            } catch (e: Exception) { /* not installed */ }
        }

        val rooted = (suPath != null || lsAdb.ok || manager != "none")
        return RootInfo(rooted = rooted, manager = manager, managerDetail = detail, suPath = suPath, evidence = evidence)
    }
}
