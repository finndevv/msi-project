package com.finndev.master.system.inspector.modules.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.Exports
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.core.ShellStream
import com.finndev.master.system.inspector.core.TerminalCenter
import com.finndev.master.system.inspector.ui.ConfirmDialog
import com.finndev.master.system.inspector.ui.EmptyHint
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.RootBanner
import com.finndev.master.system.inspector.ui.SectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
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
import com.finndev.master.system.inspector.ui.DataRow

// ==================================================================================
// MODULE 26 — Emergency Recovery Command Console
// ==================================================================================
@Composable
fun RecoveryConsoleScreen() {
    ModuleScaffold(moduleId = 26) {
        ModuleIntroCard(26)
        RootBanner()
        val scope = rememberCoroutineScope()
        var out by remember { mutableStateOf(listOf<String>()) }
        var busy by remember { mutableStateOf(false) }
        var confirmCmd by remember { mutableStateOf<Pair<String, String>?>(null) } // label to cmd
        var custom by remember { mutableStateOf("") }

        data class RecoveryAction(val labelKey: String, val cmd: String, val danger: Boolean = false)
        val actions = listOf(
            RecoveryAction("m26_fix_perms", "for d in /data/data/*; do chown -R \$(stat -c '%u:%g' \$d) \$d 2>/dev/null; done; echo PERMS_DONE"),
            RecoveryAction("m26_clear_caches", "pm trim-caches 999999999999 && echo CACHES_TRIMMED"),
            RecoveryAction("m26_drop_caches", "sync && echo 3 > /proc/sys/vm/drop_caches && echo DROPPED"),
            RecoveryAction("m26_logcat_clear", "logcat -c -b all && echo LOGCAT_CLEARED"),
            RecoveryAction("m26_kill_bg", "am kill-all && echo BG_KILLED"),
            RecoveryAction("m26_remount_ro", "mount -o remount,ro /system; mount -o remount,ro /vendor; echo REMOUNTED_RO"),
            RecoveryAction("m26_reboot", "reboot", true),
            RecoveryAction("m26_reboot_recovery", "reboot recovery", true),
            RecoveryAction("m26_reboot_bootloader", "reboot bootloader", true),
        )

        fun run(cmd: String) {
            scope.launch {
                busy = true
                out = (out + listOf("\$ $cmd")).takeLast(400)
                val r = Shell.su(cmd, 120_000)
                out = (out + r.output.lines()).takeLast(400)
                busy = false
            }
        }

        SectionCard(L("m26_actions")) {
            actions.forEach { a ->
                MsiButton(L(a.labelKey), {
                    confirmCmd = Ls(a.labelKey) to a.cmd
                }, Modifier.fillMaxWidth(), danger = a.danger)
            }
        }
        SectionCard(L("m26_custom")) {
            LabeledField("", custom, { custom = it }, mono = true, placeholder = "su -c ...")
            MsiButton(L("run"), { if (custom.isNotBlank()) run(custom) }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow, enabled = !busy)
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }

        confirmCmd?.let { (label, cmd) ->
            ConfirmDialog(
                title = label,
                text = L("m26_confirm") + "\n\n$ cmd",
                confirmLabel = L("confirm"),
                onConfirm = { run(cmd); confirmCmd = null },
                onDismiss = { confirmCmd = null },
            )
        }
    }
}

// ==================================================================================
// MODULE 27 — Quick Command Macro Builder & Runner
// ==================================================================================
@Composable
fun MacroBuilderScreen() {
    ModuleScaffold(moduleId = 27) {
        ModuleIntroCard(27)
        val ctx = LocalContext.current
        val macrosFile = remember { File(ctx.filesDir, "macros.json") }
        var macros by remember { mutableStateOf(listOf<Pair<String, String>>()) } // name to "cmd|delayMs;cmd|delayMs..."
        var name by remember { mutableStateOf("") }
        var steps by remember { mutableStateOf("echo step 1|500\necho step 2|500") }
        var out by remember { mutableStateOf(listOf<String>()) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun load() {
            macros = runCatching {
                val arr = JSONArray(File(ctx.filesDir, "macros.json").readText())
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    o.getString("name") to o.getString("steps")
                }
            }.getOrDefault(emptyList())
        }
        LaunchedEffect(Unit) { load() }

        fun saveAll(list: List<Pair<String, String>>) {
            macros = list
            val arr = JSONArray()
            list.forEach { (n, s) -> arr.put(JSONObject().put("name", n).put("steps", s)) }
            macrosFile.writeText(arr.toString())
        }

        fun runMacro(stepsStr: String) {
            scope.launch {
                busy = true
                val stepsList = stepsStr.lines().filter { it.contains('|') }
                for (line in stepsList) {
                    val cmd = line.substringBefore('|').trim()
                    val delayMs = line.substringAfter('|').trim().toLongOrNull() ?: 0L
                    out = (out + listOf("\$ $cmd")).takeLast(400)
                    val r = Shell.auto(cmd, preferRoot = cmd.startsWith("#root "))
                    out = (out + r.output.lines()).takeLast(400)
                    if (delayMs > 0) delay(delayMs)
                }
                out = (out + listOf("[MSI] macro finished")).takeLast(400)
                busy = false
            }
        }

        SectionCard(L("m27_saved"), "${macros.size}") {
            if (macros.isEmpty()) EmptyHint(L("m27_none"))
            macros.forEach { (n, s) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    MsiButton(Ls("run"), { runMacro(s) }, filled = false, enabled = !busy)
                    MsiButton(Ls("delete"), { saveAll(macros.filter { it.first != n }) }, filled = false, danger = true)
                }
            }
        }
        SectionCard(L("m27_builder")) {
            LabeledField(L("m27_name"), name, { name = it })
            LabeledField(L("m27_steps"), steps, { steps = it }, singleLine = false, mono = true, minLines = 4)
            InfoTextSmall(L("m27_format"))
            MsiButton(L("save"), {
                if (name.isNotBlank()) saveAll(macros + (name to steps))
            }, Modifier.fillMaxWidth(), Icons.Filled.Save)
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 28 — Terminal Output Log Exporter (.txt / .log)
// ==================================================================================
@Composable
fun LogExporterScreen() {
    ModuleScaffold(moduleId = 28) {
        ModuleIntroCard(28)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var logs by remember { mutableStateOf<List<File>>(emptyList()) }

        fun load() { logs = Exports.logsDir(ctx).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList() }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m28_current")) {
            val transcript = remember { synchronized(TerminalCenter.transcript) { TerminalCenter.transcript.toList() } }
            DataRow(L("m28_lines"), "${transcript.size}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(".txt", {
                    scope.launch {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        Exports.saveLog(ctx, "msi-terminal-$stamp.txt", transcript)
                        load()
                    }
                }, Modifier.weight(1f))
                MsiButton(".log", {
                    scope.launch {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        Exports.saveLog(ctx, "msi-terminal-$stamp.log", transcript)
                        load()
                    }
                }, Modifier.weight(1f))
            }
        }
        SectionCard(L("m28_saved"), "${logs.size}") {
            if (logs.isEmpty()) EmptyHint(L("m28_none"))
            logs.forEach { f ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(f.lastModified())) +
                                " • " + com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(f.length()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MsiButton(L("share"), { Exports.shareFile(ctx, f) }, icon = Icons.Filled.Share, filled = false)
                    MsiButton(L("delete"), { f.delete(); load() }, icon = Icons.Filled.DeleteForever, filled = false, danger = true)
                }
            }
            InfoTextSmall(L("m28_hint"))
        }
    }
}
