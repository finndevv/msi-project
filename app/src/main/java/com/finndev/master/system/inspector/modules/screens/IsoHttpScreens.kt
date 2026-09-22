package com.finndev.master.system.inspector.modules.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.Downloader
import com.finndev.master.system.inspector.core.Exports
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.net.HttpServer
import com.finndev.master.system.inspector.core.net.HttpShareService
import com.finndev.master.system.inspector.ui.ChipRow
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.StatusBadge
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect

// ==================================================================================
// MODULE 51 — ValiantCore ISO Downloader (MANAGE_EXTERNAL_STORAGE & arch selector)
// ==================================================================================
@Composable
fun IsoDownloaderScreen() {
    ModuleScaffold(moduleId = 51) {
        ModuleIntroCard(51)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var archIdx by remember { mutableStateOf(0) }
        var url by remember { mutableStateOf("https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/x86_64/alpine-standard-3.21.3-x86_64.iso") }
        var busy by remember { mutableStateOf(false) }
        var progress by remember { mutableLongStateOf(0) }
        var total by remember { mutableLongStateOf(-1) }
        var log by remember { mutableStateOf(listOf<String>()) }
        var job by remember { mutableStateOf<Job?>(null) }

        val arches = listOf("x86_64", "i386", "aarch64")

        SectionCard(L("m51_config")) {
            Text(L("m51_arch"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipRow(arches, archIdx) {
                archIdx = it
                url = url.replace(Regex("(x86_64|i386|aarch64)"), arches[it])
            }
            LabeledField("URL", url, { url = it }, mono = true, minLines = 2)
            DataRow(L("m51_dest"), "/sdcard/Download/MSI/")
            if (busy) {
                val pct = if (total > 0) (progress * 100 / total).toInt() else -1
                Text(
                    if (pct >= 0) "$pct% • ${com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(progress)} / ${com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(total)}"
                    else "${com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(progress)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (total > 0) (progress.toFloat() / total).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                MsiButton(L("abort"), {
                    job?.cancel()
                    busy = false
                    log = log + "[MSI] aborted"
                }, Modifier.fillMaxWidth(), Icons.Filled.Stop, danger = true)
            } else {
                MsiButton(L("download"), {
                    val fileName = url.substringAfterLast('/').ifBlank { "image.iso" }
                    val dest = File(Exports.msiPublicDir("ISO"), fileName)
                    busy = true; progress = 0; total = -1
                    log = listOf("[MSI] GET $url")
                    job = scope.launch {
                        Downloader.download(url, dest) { read, t ->
                            progress = read; total = t
                        }.fold(
                            onSuccess = {
                                log = (log + listOf("[MSI] saved ${it.path} (${com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(it.length())})")).takeLast(50)
                            },
                            onFailure = { log = (log + listOf("[ERR] ${it.message}")).takeLast(50) },
                        )
                        busy = false
                    }
                }, Modifier.fillMaxWidth(), Icons.Filled.Download)
            }
        }
        SectionCard(L("m51_verify")) {
            InfoTextSmall(L("m51_verify_hint"))
            var saved by remember { mutableStateOf("") }
            LabeledField(L("m20_path"), saved, { saved = it }, mono = true)
            MsiButton(L("m51_sha256"), {
                val f = File(saved.ifBlank { Exports.msiPublicDir("ISO").listFiles()?.firstOrNull()?.absolutePath ?: "" })
                if (f.isFile) {
                    log = (log + listOf("[MSI] sha256 ${f.name} ...")).takeLast(50)
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val h = com.finndev.master.system.inspector.core.Crypto.fileHash("SHA-256", f)
                        log = (log + listOf(h)).takeLast(50)
                    }
                }
            })
        }
        if (log.isNotEmpty()) SectionCard(L("output")) { OutputConsole(log, maxLinesShown = 40) }
    }
}

// ==================================================================================
// MODULE 52 — Local HTTP File Sharing Server (LAN asset sharing)
// ==================================================================================
@Composable
fun HttpServerScreen() {
    ModuleScaffold(moduleId = 52) {
        ModuleIntroCard(52)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var dir by remember { mutableStateOf("/sdcard/MSI-Shared") }
        var port by remember { mutableStateOf("8080") }
        var running by remember { mutableStateOf(HttpServer.running) }
        var served by remember { mutableStateOf(0) }
        var url by remember { mutableStateOf("") }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                running = HttpServer.running
                served = HttpServer.served.get()
                if (HttpServer.running) url = HttpServer.baseUrl()
                kotlinx.coroutines.delay(1000)
            }
        }

        SectionCard(L("m52_config")) {
            LabeledField(L("m52_dir"), dir, { dir = it }, mono = true)
            LabeledField(L("m52_port"), port, { port = it })
            DataRow(L("m52_status"), if (running) L("m52_running") else L("m52_stopped"))
            if (running) {
                DataRow("URL", url, mono = true, copyable = true)
                DataRow(L("m52_served"), "$served")
            }
            if (running) {
                MsiButton(L("stop"), {
                    ctx.stopService(Intent(ctx, HttpShareService::class.java))
                    running = false
                }, Modifier.fillMaxWidth(), Icons.Filled.Stop, danger = true)
            } else {
                MsiButton(L("m52_start"), {
                    File(dir).mkdirs()
                    val intent = Intent(ctx, HttpShareService::class.java).apply {
                        putExtra(HttpShareService.EXTRA_DIR, dir)
                        putExtra(HttpShareService.EXTRA_PORT, port.toIntOrNull() ?: 8080)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        ctx.startForegroundService(intent)
                    } else {
                        ctx.startService(intent)
                    }
                    running = true
                }, Modifier.fillMaxWidth())
            }
            StatusBadge(if (running) L("m52_running") else L("m52_stopped"), running)
        }
        SectionCard(L("m52_log")) {
            val log = remember { HttpServer.log }
            val snapshot = HttpServer.log.toList()
            if (snapshot.isEmpty()) InfoTextSmall(L("m52_log_empty"))
            else OutputConsole(snapshot.takeLast(30), maxLinesShown = 30)
            InfoTextSmall(L("m52_hint"))
        }
        if (url.isNotBlank()) {
            MsiButton(L("share"), {
                Exports.shareText(ctx, "MSI file server: $url")
            }, Modifier.fillMaxWidth())
        }
    }
}
