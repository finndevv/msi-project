package com.finndev.master.system.inspector.modules.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
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
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.SystemInfo
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.StatCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect

// ==================================================================================
// MODULE 56 — Storage Hog Hunter (Large file locator)
// ==================================================================================
@Composable
fun HogHunterScreen() {
    ModuleScaffold(moduleId = 56) {
        ModuleIntroCard(56)
        val scope = rememberCoroutineScope()
        var root by remember { mutableStateOf("/sdcard") }
        var minMb by remember { mutableStateOf("50") }
        var results by remember { mutableStateOf<List<Pair<File, Long>>>(emptyList()) }
        var busy by remember { mutableStateOf(false) }
        var scanned by remember { mutableStateOf(0) }
        var totalSize by remember { mutableStateOf(0L) }

        SectionCard(L("m56_scan")) {
            LabeledField(L("m56_root"), root, { root = it }, mono = true, placeholder = "/sdcard")
            LabeledField(L("m56_min_size"), minMb, { minMb = it }, placeholder = "50")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("/sdcard", "/sdcard/Download", "/sdcard/DCIM", "/sdcard/Android/data").forEach { p ->
                    MsiButton(p.takeLast(16), { root = p }, Modifier.weight(1f), filled = false)
                }
            }
            if (busy) LoadingRow("$scanned " + L("m56_files"))
            MsiButton(if (busy) L("scanning") else L("scan"), {
                if (busy) return@MsiButton
                scope.launch(Dispatchers.IO) {
                    busy = true; scanned = 0; totalSize = 0
                    val minBytes = (minMb.toLongOrNull() ?: 50L) * 1024 * 1024
                    val found = mutableListOf<Pair<File, Long>>()
                    File(root).walkTopDown().onProgress { }
                    File(root).takeIf { it.exists() }?.walkTopDown()?.forEach { f ->
                        if (f.isFile) {
                            scanned++
                            if (f.length() >= minBytes) {
                                synchronized(found) { found.add(f to f.length()) }
                                totalSize += f.length()
                            }
                        }
                    }
                    results = found.sortedByDescending { it.second }.take(300)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.Search, enabled = !busy)
        }
        SectionCard(L("m56_results"), "${results.size}") {
            DataRow(L("m56_total"), SystemInfo.fmtBytes(results.sumOf { it.second }))
            if (results.isEmpty() && !busy) Text(L("m56_none"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.height(340.dp)) {
                items(results) { (f, size) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(f.parent?.takeLast(46) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text(SystemInfo.fmtBytes(size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun <T> Sequence<T>.onProgress(block: (T) -> Unit): Sequence<T> = map { block(it); it }

// ==================================================================================
// MODULE 58 — Download Queue & Active Transfer Tracker
// ==================================================================================
private data class Transfer(
    val id: Int, val url: String, val fileName: String,
    var received: Long, var total: Long, var state: String, var error: String? = null,
)

@Composable
fun DownloadQueueScreen() {
    ModuleScaffold(moduleId = 58) {
        ModuleIntroCard(58)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var url by remember { mutableStateOf("") }
        var transfers by remember { mutableStateOf(listOf<Transfer>()) }
        val live = remember { ConcurrentLinkedQueue<Transfer>() }
        val semaphore = remember { Semaphore(2) }
        var nextId by remember { mutableStateOf(1) }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                transfers = live.toList()
                kotlinx.coroutines.delay(700)
            }
        }

        SectionCard(L("m58_add")) {
            LabeledField("URL", url, { url = it }, mono = true, placeholder = "https://example.com/file.zip")
            MsiButton(L("m58_enqueue"), {
                if (url.isBlank()) return@MsiButton
                val fileName = url.substringAfterLast('/').substringBefore('?').ifBlank { "file-${System.currentTimeMillis() % 10000}" }
                val t = Transfer(nextId++, url.trim(), fileName, 0, -1, "queued")
                live.add(t)
                url = ""
                scope.launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        t.state = "downloading"
                        Downloader.download(t.url, File(com.finndev.master.system.inspector.core.Exports.msiPublicDir("Downloads"), t.fileName)) { r, tot ->
                            t.received = r; t.total = tot
                        }.fold(
                            onSuccess = { t.state = "done"; t.received = it.length(); t.total = it.length() },
                            onFailure = { t.state = "failed"; t.error = it.message },
                        )
                    }
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.Download)
            InfoTextSmall(L("m58_hint"))
        }
        SectionCard(L("m58_active"), "${transfers.count { it.state == "downloading" }}") {
            if (transfers.isEmpty()) Text(L("m58_none"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            transfers.reversed().take(20).forEach { t ->
                val pct = if (t.total > 0) t.received * 100 / t.total else -1
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t.fileName.take(28), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text(
                            when (t.state) {
                                "done" -> "✔ ${SystemInfo.fmtBytes(t.total)}"
                                "failed" -> "✘"
                                else -> if (pct >= 0) "$pct%" else SystemInfo.fmtBytes(t.received)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (t.state) {
                                "done" -> androidx.compose.ui.graphics.Color(0xFF00E676)
                                "failed" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    if (t.state == "downloading" && t.total > 0) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (t.received.toFloat() / t.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (t.error != null) Text(t.error ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
            }
        }
    }
}
