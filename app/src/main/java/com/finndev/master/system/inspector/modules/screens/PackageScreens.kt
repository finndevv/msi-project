package com.finndev.master.system.inspector.modules.screens

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Shell
import androidx.compose.foundation.clickable
import com.finndev.master.system.inspector.ui.ConfirmDialog
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.EmptyHint
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.RootBanner
import com.finndev.master.system.inspector.ui.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 67 — Installed Package Name Inspector (One-tap copy)
// ==================================================================================
private data class PackageRow(
    val pkg: String, val label: String, val version: String, val targetSdk: Int,
    val isSystem: Boolean, val installedAt: Long, val debuggable: Boolean, val uid: Int,
)

@Composable
fun PackageInspectorScreen() {
    ModuleScaffold(moduleId = 67) {
        ModuleIntroCard(67)
        val ctx = LocalContext.current
        var apps by remember { mutableStateOf<List<PackageRow>?>(null) }
        var filter by remember { mutableStateOf("") }
        var onlySystem by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<PackageRow?>(null) }

        LaunchedEffect(Unit) {
            apps = ctx.packageManager.getInstalledPackages(0).mapNotNull { p ->
                val ai = p.applicationInfo ?: return@mapNotNull null
                PackageRow(
                    pkg = p.packageName,
                    label = runCatching { ctx.packageManager.getApplicationLabel(ai).toString() }.getOrDefault(p.packageName),
                    version = p.versionName ?: "-",
                    targetSdk = ai.targetSdkVersion,
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    installedAt = p.firstInstallTime,
                    debuggable = (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                    uid = ai.uid,
                )
            }.sortedBy { it.label.lowercase() }
        }

        SectionCard(L("m67_search")) {
            LabeledField("", filter, { filter = it }, placeholder = L("search_modules"))
            com.finndev.master.system.inspector.ui.ChipRow(listOf(L("m67_all"), L("m67_system")), if (onlySystem) 1 else 0) { onlySystem = it == 1 }
        }
        if (apps == null) { LoadingRow(L("scanning")); return@ModuleScaffold }
        SectionCard(L("m67_apps"), "${apps!!.size}") {
            val shown = apps!!.filter { r ->
                (!onlySystem || r.isSystem) &&
                    (filter.isBlank() || r.pkg.contains(filter, true) || r.label.contains(filter, true))
            }
            LazyColumn(Modifier.height(360.dp)) {
                items(shown.take(300)) { r ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { selected = r }
                            .padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(r.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                        com.finndev.master.system.inspector.ui.CopyButton(r.pkg)
                    }
                }
            }
        }
        selected?.let { r ->
            SectionCard(r.label, r.pkg) {
                DataRow("package", r.pkg, mono = true, copyable = true)
                DataRow(L("version"), r.version)
                DataRow("targetSdk", "${r.targetSdk}")
                DataRow("uid", "${r.uid}")
                DataRow("system", if (r.isSystem) L("yes") else L("no"))
                DataRow("debuggable", if (r.debuggable) L("yes") else L("no"))
                DataRow(L("m67_installed_at"), SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(r.installedAt)))
            }
        }
    }
}

// ==================================================================================
// MODULE 68 — Background Services & Active Processes Manager
// ==================================================================================
private data class ProcRow(val pid: Int, val name: String, val rssKb: Long, val state: String, val ppid: Int)

@Composable
fun ProcessManagerScreen() {
    ModuleScaffold(moduleId = 68) {
        ModuleIntroCard(68)
        RootBanner()
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var processes by remember { mutableStateOf<List<ProcRow>>(emptyList()) }
        var busy by remember { mutableStateOf(false) }
        var filter by remember { mutableStateOf("") }
        var killTarget by remember { mutableStateOf<ProcRow?>(null) }

        fun load() {
            scope.launch {
                busy = true
                processes = withContext(Dispatchers.IO) {
                    val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val appProcs = runCatching { am.runningAppProcesses.orEmpty() }.getOrDefault(emptyList())
                    if (appProcs.isNotEmpty() && Shell.hasRoot()) {
                        // root: full /proc scan
                        val out = mutableListOf<ProcRow>()
                        java.io.File("/proc").listFiles()?.filter { it.name.toIntOrNull() != null }?.forEach { d ->
                            val pid = d.name.toInt()
                            val stat = runCatching { java.io.File(d, "stat").readText() }.getOrNull() ?: return@forEach
                            val name = stat.substringAfterLast('(').substringBefore(')').take(24)
                            val after = stat.substringAfter(") ")
                            val parts = after.split(' ')
                            val state = parts.getOrNull(0) ?: "?"
                            val ppid = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            val rss = parts.getOrNull(21)?.toLongOrNull() ?: runCatching {
                                java.io.File(d, "statm").readText().split(' ').getOrNull(1)?.toLongOrNull()?.times(4096)
                            }.getOrNull() ?: 0L
                            if (rss > 0) out.add(ProcRow(pid, name, rss, state, ppid))
                        }
                        out.sortedByDescending { it.rssKb }
                    } else {
                        appProcs.map { p ->
                            val rss = runCatching {
                                java.io.File("/proc/${p.pid}/statm").readText().split(' ').getOrNull(1)?.toLongOrNull()?.times(4096L)
                            }.getOrNull() ?: 0L
                            ProcRow(p.pid, p.processName.take(28), rss, "?", 0)
                        }.sortedByDescending { it.rssKb }
                    }
                }
                busy = false
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m68_processes"), "${processes.size}") {
            LabeledField(L("filter"), filter, { filter = it })
            if (busy) LoadingRow(L("scanning"))
            MsiButton(L("refresh"), { load() }, Modifier.fillMaxWidth(), Icons.Filled.Refresh)
            LazyColumn(Modifier.height(360.dp)) {
                items(processes.filter { filter.isBlank() || it.name.contains(filter, true) }.take(200)) { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(
                                "pid ${p.pid} • ${p.state} • ${com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(p.rssKb)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MsiButton(L("m68_kill"), { killTarget = p }, filled = false, danger = true)
                    }
                }
            }
        }
        killTarget?.let { p ->
            ConfirmDialog(
                title = L("m68_kill"),
                text = "kill ${p.pid} (${p.name})?",
                confirmLabel = L("confirm"),
                onConfirm = {
                    scope.launch {
                        Shell.su("kill ${p.pid} 2>/dev/null; am force-stop ${p.name} 2>/dev/null; echo KILLED")
                        killTarget = null
                        load()
                    }
                },
                onDismiss = { killTarget = null },
            )
        }
    }
}
