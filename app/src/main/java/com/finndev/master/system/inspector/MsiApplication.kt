package com.finndev.master.system.inspector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.finndev.master.system.inspector.core.AppState
import com.finndev.master.system.inspector.core.CrashHandler
import java.io.File

class MsiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppState.init(this)
        CrashHandler.install(this)
        createChannels()
        copyProotLoader()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("msi_general", "MSI General", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel("msi_http_server", "MSI HTTP Server", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel("msi_downloads", "MSI Transfers", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    /** Extract the PRoot loader binary (proot >= 5.3) from assets if bundled. */
    private fun copyProotLoader() {
        runCatching {
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return
            val name = "proot/loader-$abi"
            val out = File(filesDir, "proot-loader")
            if (out.isFile) return
            assets.open(name).use { input ->
                out.outputStream().use { input.copyTo(it, 32 * 1024) }
            }
            out.setExecutable(true, false)
        }
    }
}
