package com.finndev.master.system.inspector.modules.screens

import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.core.ShellStream
import com.finndev.master.system.inspector.ui.EmptyHint
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.ChipRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch

// ==================================================================================
// MODULE 69 — System Logcat Live Flow Stream & Filter
// ==================================================================================
@Composable
fun LogcatScreen() {
    ModuleScaffold(moduleId = 69, scrollable = false) {
        ModuleIntroCard(69)
        var lines by remember { mutableStateOf(listOf<String>()) }
        var filter by remember { mutableStateOf("") }
        var levelIdx by remember { mutableStateOf(0) }
        var rootMode by remember { mutableStateOf(true) }
        var running by remember { mutableStateOf(false) }
        var stream by remember { mutableStateOf<ShellStream?>(null) }

        val levels = listOf("V", "D", "I", "W", "E")

        fun start() {
            stream?.kill()
            running = true
            val level = levels[levelIdx]
            lines = listOf("[MSI] logcat *:$level ${if (rootMode) "(root)" else ""}")
            stream = Shell.stream("logcat -v time *:$level", root = rootMode) { line ->
                lines = (lines + line).takeLast(2000)
            }
        }
        fun stop() {
            stream?.kill(); running = false
            lines = lines + "[MSI] stopped"
        }

        DisposableEffect(Unit) { onDispose { stream?.kill() } }

        SectionCard(L("m69_filter")) {
            LabeledField("", filter, { filter = it }, placeholder = "tag | text ...")
            ChipRow(levels, levelIdx) { levelIdx = it }
            com.finndev.master.system.inspector.ui.ChipRow(listOf("root", "user"), if (rootMode) 0 else 1) { rootMode = it == 0 }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) MsiButton(L("stop"), { stop() }, Modifier.weight(1f), Icons.Filled.Stop, danger = true)
                else MsiButton(L("m69_start"), { start() }, Modifier.weight(1f))
                MsiButton(L("clear"), { lines = emptyList() }, Modifier.weight(1f), Icons.Filled.Delete)
            }
            InfoTextSmall(L("m69_hint"))
        }
        val shown = if (filter.isBlank()) lines else lines.filter { it.contains(filter, true) }
        if (shown.isEmpty()) EmptyHint(L("m69_empty"))
        else OutputConsole(shown.takeLast(400), Modifier.weight(1f), maxLinesShown = 400)
    }
}

// ==================================================================================
// MODULE 70 — Mount Points & Disk Partition Usage Table
// ==================================================================================
private data class MountRow(val dev: String, val point: String, val fstype: String, val ro: Boolean, val total: Long, val free: Long)

@Composable
fun MountsScreen() {
    ModuleScaffold(moduleId = 70) {
        ModuleIntroCard(70)
        var rows by remember { mutableStateOf<List<MountRow>>(emptyList()) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        fun load() {
            scope.launch {
                val mounts = com.finndev.master.system.inspector.core.Shell.readFile("/proc/mounts") ?: ""
                rows = mounts.lineSequence().mapNotNull { line ->
                    val p = line.split(' ')
                    if (p.size < 4) return@mapNotNull null
                    val point = p[1]
                    val ro = p[3].split(',').any { it == "ro" }
                    val stat = runCatching { StatFs(point) }.getOrNull()
                    MountRow(
                        dev = p[0], point = point, fstype = p[2], ro = ro,
                        total = stat?.totalBytes ?: 0, free = stat?.availableBytes ?: 0,
                    )
                }.filter { it.total > 0 || it.point == "/" || it.point.startsWith("/sdcard") || it.point.startsWith("/data") }
                    .distinctBy { it.point }
                    .toList()
            }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { load() }

        SectionCard(L("m70_mounts"), "${rows.size}") {
            MsiButton(L("refresh"), { load() }, Modifier.fillMaxWidth())
            LazyColumn(Modifier.height(400.dp)) {
                items(rows) { m ->
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.point, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            Text(
                                m.fstype + if (m.ro) " • RO" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (m.ro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(m.dev.takeLast(38), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        if (m.total > 0) {
                            Text(
                                com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(m.total - m.free) + " / " +
                                    com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(m.total),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        SectionCard("df -h (root)") {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var df by remember { mutableStateOf(listOf<String>()) }
            LaunchedEffect(Unit) {
                scope.launch { df = Shell.su("df -h 2>/dev/null | head -25", 15000).stdout.lines().filter { it.isNotBlank() } }
            }
            if (df.isEmpty()) EmptyHint(L("m70_none"))
            else OutputConsole(df, maxLinesShown = 25)
        }
    }
}
