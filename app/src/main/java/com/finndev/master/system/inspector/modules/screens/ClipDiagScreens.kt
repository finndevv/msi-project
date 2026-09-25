package com.finndev.master.system.inspector.modules.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.CrashHandler
import com.finndev.master.system.inspector.core.Exports
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.core.SystemInfo
import com.finndev.master.system.inspector.core.TarGz
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.EmptyHint
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect

// ==================================================================================
// MODULE 59 — Clipboard History & Snippet Manager
// ==================================================================================
@Composable
fun ClipboardHistoryScreen() {
    ModuleScaffold(moduleId = 59) {
        ModuleIntroCard(59)
        val ctx = LocalContext.current
        val historyFile = remember { File(ctx.filesDir, "clip-history.json") }
        val snippetsFile = remember { File(ctx.filesDir, "snippets.json") }
        var history by remember { mutableStateOf(listOf<String>()) }
        var snippets by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var snippetName by remember { mutableStateOf("") }
        var snippetBody by remember { mutableStateOf("") }
        var watching by remember { mutableStateOf(true) }

        fun loadAll() {
            history = runCatching {
                JSONArray(historyFile.readText()).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            }.getOrDefault(emptyList())
            snippets = runCatching {
                val arr = JSONArray(snippetsFile.readText())
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    o.getString("name") to o.getString("body")
                }
            }.getOrDefault(emptyList())
        }
        LaunchedEffect(Unit) { loadAll() }

        fun persist() {
            historyFile.writeText(JSONArray(history).toString())
            val arr = JSONArray()
            snippets.forEach { (n, b) -> arr.put(JSONObject().put("name", n).put("body", b)) }
            snippetsFile.writeText(arr.toString())
        }

        // live clipboard watcher
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        DisposableEffect(watching) {
            val listener = ClipboardManager.OnPrimaryClipChangedListener {
                if (!watching) return@OnPrimaryClipChangedListener
                val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener
                if (text.isNotBlank() && (history.firstOrNull() != text)) {
                    history = (listOf(text) + history).take(100)
                    persist()
                }
            }
            cm.addPrimaryClipChangedListener(listener)
            onDispose { cm.removePrimaryClipChangedListener(listener) }
        }

        val clipboardMgr = LocalClipboardManager.current

        SectionCard(L("m59_history"), "${history.size}") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MsiButton(if (watching) L("m59_watching") else L("m59_paused"), { watching = !watching }, filled = false)
                MsiButton(L("m59_clear"), {
                    history = emptyList(); persist()
                }, filled = false, danger = true)
            }
            if (history.isEmpty()) EmptyHint(L("m59_none"))
            LazyColumn(Modifier.height(260.dp)) {
                items(history) { item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(item.take(140), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Row {
                            MsiButton(L("copy"), { clipboardMgr.setText(AnnotatedString(item)) }, filled = false)
                            MsiButton(L("m59_save_snippet"), {
                                snippets = snippets + (item.take(24) to item)
                                persist()
                            }, filled = false)
                        }
                    }
                }
            }
        }
        SectionCard(L("m59_snippets"), "${snippets.size}") {
            LabeledField(L("m04_name"), snippetName, { snippetName = it })
            LabeledField("", snippetBody, { snippetBody = it }, singleLine = false, mono = true, minLines = 3)
            MsiButton(L("save"), {
                if (snippetName.isNotBlank()) {
                    snippets = snippets + (snippetName to snippetBody)
                    snippetName = ""; snippetBody = ""
                    persist()
                }
            })
            snippets.forEach { (n, b) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(n, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(b.take(120), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    Row {
                        MsiButton(L("copy"), { clipboardMgr.setText(AnnotatedString(b)) }, filled = false)
                        MsiButton(L("delete"), { snippets = snippets.filterNot { it.first == n }; persist() }, filled = false, danger = true)
                    }
                }
            }
        }
    }
}

// ==================================================================================
// MODULE 60 — System Log & Diagnostic Report Exporter
// ==================================================================================
@Composable
fun DiagReportScreen() {
    ModuleScaffold(moduleId = 60) {
        ModuleIntroCard(60)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var report by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf("") }

        SectionCard(L("m60_report")) {
            if (busy) LoadingRow(L("scanning"))
            MsiButton(L("m60_generate"), {
                scope.launch(Dispatchers.IO) {
                    busy = true
                    val sb = StringBuilder()
                    sb.append(SystemInfo.diagnosticText(ctx))
                    sb.appendLine()
                    sb.appendLine("== LOGCAT (last) ==")
                    val lc = Shell.su("logcat -d -t 200 *:W 2>/dev/null || logcat -d -t 200", 30000)
                    sb.appendLine(lc.stdout.take(8000))
                    sb.appendLine()
                    sb.appendLine("== DMESG (last) ==")
                    val dm = Shell.su("dmesg | tail -60", 20000)
                    sb.appendLine(dm.stdout.take(4000))
                    report = sb.toString()
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = !busy)
        }
        if (report.isNotBlank()) {
            SectionCard(L("m60_preview")) {
                OutputConsole(report.lines().filter { it.isNotBlank() }.take(150), maxLinesShown = 150)
            }
            SectionCard(L("export")) {
                MsiButton(L("m60_save"), {
                    scope.launch(Dispatchers.IO) {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        val r = Exports.saveTextToDownloads(ctx, "msi-diag-$stamp.txt", report)
                        saved = r.fold({ "[OK] " + it.path }, { "[ERR] " + it.message })
                    }
                }, Modifier.fillMaxWidth())
                MsiButton(L("share"), {
                    scope.launch(Dispatchers.IO) {
                        val f = File(ctx.cacheDir, "msi-diag.txt")
                        f.writeText(report)
                        Exports.shareFile(ctx, f)
                    }
                }, Modifier.fillMaxWidth(), Icons.Filled.Share)
                if (saved.isNotBlank()) ConsoleLine(saved)
            }
        }
    }
}

// ==================================================================================
// MODULE 64 — Debug Support & Crash Log Zip Packager
// ==================================================================================
@Composable
fun CrashPackagerScreen() {
    ModuleScaffold(moduleId = 64) {
        ModuleIntroCard(64)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var crashes by remember { mutableStateOf<List<File>>(emptyList()) }
        var busy by remember { mutableStateOf(false) }
        var out by remember { mutableStateOf(listOf<String>()) }

        fun load() { crashes = CrashHandler.listCrashes(ctx) }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m64_crashes"), "${crashes.size}") {
            if (crashes.isEmpty()) EmptyHint(L("m64_none"))
            LazyColumn(Modifier.height(200.dp)) {
                items(crashes) { f ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(f.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(f.lastModified())) +
                                " • " + SystemInfo.fmtBytes(f.length()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            MsiButton(L("m64_view"), {
                crashes.firstOrNull()?.let { f ->
                    out = f.readText().lines().take(60)
                }
            }, Modifier.fillMaxWidth(), filled = false)
            MsiButton(L("m64_clear"), {
                CrashHandler.clearCrashes(ctx); load()
            }, Modifier.fillMaxWidth(), Icons.Filled.Delete, danger = true)
        }
        SectionCard(L("m64_package")) {
            if (busy) LoadingRow(L("scanning"))
            MsiButton(L("m64_zip"), {
                scope.launch(Dispatchers.IO) {
                    busy = true
                    val entries = mutableMapOf<String, File>()
                    CrashHandler.listCrashes(ctx).forEachIndexed { i, f -> entries["crash/$i-${f.name}"] = f }
                    // include latest logcat + dmesg snapshot
                    val cache = ctx.cacheDir
                    val lc = File(cache, "logcat.txt")
                    lc.writeText(Shell.su("logcat -d -t 500", 30000).stdout)
                    entries["logs/logcat.txt"] = lc
                    val dm = File(cache, "dmesg.txt")
                    dm.writeText(Shell.su("dmesg | tail -200", 20000).stdout)
                    entries["logs/dmesg.txt"] = dm
                    val diag = File(cache, "diagnostic.txt")
                    diag.writeText(SystemInfo.diagnosticText(ctx))
                    entries["diagnostic.txt"] = diag
                    val zip = File(ctx.cacheDir, "msi-debug.zip")
                    TarGz.zip(entries, zip)
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    val result = Exports.saveToDownloads(ctx, "msi-debug-$stamp.zip", zip.readBytes())
                    out = result.fold(
                        { listOf("[OK] /sdcard/Download/MSI/msi-debug-$stamp.zip (${SystemInfo.fmtBytes(it.length())})") },
                        { listOf("[ERR] ${it.message}") },
                    )
                    busy = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.Archive, enabled = !busy)
            MsiButton(L("share"), {
                scope.launch(Dispatchers.IO) {
                    val zips = ctx.cacheDir.listFiles()?.filter { it.name.endsWith(".zip") }
                    zips?.lastOrNull()?.let { Exports.shareFile(ctx, it) }
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.Share, filled = false)
            InfoTextSmall(L("m64_hint"))
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out, maxLinesShown = 30) }
    }
}
