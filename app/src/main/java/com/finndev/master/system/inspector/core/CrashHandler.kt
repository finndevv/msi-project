package com.finndev.master.system.inspector.core

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Uncaught-exception capture to filesDir/crash (modules 60 / 64). */
object CrashHandler {

    fun install(ctx: Context) {
        val appCtx = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = File(appCtx.filesDir, "crash").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                File(dir, "crash-$stamp.txt").writeText(buildString {
                    appendLine("MSI crash report — ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                    appendLine("Thread: ${thread.name} (id ${thread.id})")
                    appendLine("Device: ${Build.BRAND} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
                    appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(",")}")
                    appendLine("App: MSI v${com.finndev.master.system.inspector.BuildConfig.VERSION_NAME}")
                    appendLine()
                    appendLine(android.util.Log.getStackTraceString(throwable))
                })
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun crashDir(ctx: Context): File = File(ctx.filesDir, "crash").apply { mkdirs() }

    fun listCrashes(ctx: Context): List<File> =
        crashDir(ctx).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun clearCrashes(ctx: Context) { crashDir(ctx).listFiles()?.forEach { it.delete() } }
}
