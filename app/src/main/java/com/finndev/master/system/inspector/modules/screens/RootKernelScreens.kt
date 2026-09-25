package com.finndev.master.system.inspector.modules.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.AppState
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.RootDetector
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.RootBanner
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.StatusBadge
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 1 — Advanced Root Auditor (Magisk, KernelSU, APatch detection)
// ==================================================================================
@Composable
fun RootAuditorScreen() {
    ModuleScaffold(moduleId = 1) {
        ModuleIntroCard(1)
        RootBanner()
        var busy by remember { mutableStateOf(false) }
        var info by remember { mutableStateOf<com.finndev.master.system.inspector.core.RootInfo?>(null) }
        val scope = rememberCoroutineScope()
        val ctx = LocalContext.current

        LaunchedEffect(Unit) {
            busy = true
            info = RootDetector.detect(ctx)
            busy = false
        }

        if (busy) LoadingRow(L("scanning"))
        info?.let { r ->
            SectionCard(L("m01_name")) {
                DataRow("Root", if (r.rooted) "YES" else "NO")
                DataRow(L("m01_manager"), r.manager)
                DataRow(L("m01_su_path"), r.suPath ?: "-", mono = true)
                DataRow("su -v", r.managerDetail.ifBlank { "-" })
            }
            SectionCard(L("m01_evidence")) {
                if (r.evidence.isEmpty()) InfoTextSmall(L("m01_no_evidence"))
                r.evidence.take(24).forEach { ConsoleLine(it) }
            }
        }
        MsiButton(L("refresh"), {
            scope.launch {
                busy = true
                val r = RootDetector.detect(ctx)
                AppState.rootState.value = r
                info = r; busy = false
            }
        }, icon = Icons.Filled.Refresh)
    }
}

@Composable
fun ConsoleLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

@Composable
fun InfoTextSmall(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ==================================================================================
// MODULE 2 — SELinux Mode Switcher (Enforcing / Permissive)
// ==================================================================================
@Composable
fun SelinuxScreen() {
    ModuleScaffold(moduleId = 2) {
        ModuleIntroCard(2)
        RootBanner()
        var mode by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun refresh() {
            scope.launch {
                busy = true
                mode = Shell.su("getenforce", 8000).stdout.trim().ifBlank { "unknown" }
                busy = false
            }
        }
        LaunchedEffect(Unit) { refresh() }

        SectionCard(L("m02_name")) {
            if (busy) LoadingRow(L("scanning"))
            else {
                DataRow("getenforce", mode)
                StatusBadge(mode, mode.equals("Enforcing", true))
            }
        }
        SectionCard(L("actions")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("m02_set_enforcing"), {
                    scope.launch { Shell.su("setenforce 1", 8000); refresh() }
                }, Modifier.weight(1f), Icons.Filled.Security)
                MsiButton(L("m02_set_permissive"), {
                    scope.launch { Shell.su("setenforce 0", 8000); refresh() }
                }, Modifier.weight(1f), Icons.Filled.RestartAlt, danger = true)
            }
            InfoTextSmall(L("m02_warn"))
        }
    }
}

// ==================================================================================
// MODULE 3 — Build.prop Live Editor & Reader
// ==================================================================================
@Composable
fun BuildPropScreen() {
    ModuleScaffold(moduleId = 3) {
        ModuleIntroCard(3)
        RootBanner()
        var props by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var filter by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var editKey by remember { mutableStateOf("") }
        var editVal by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        val toastCtx = androidx.compose.ui.platform.LocalContext.current

        fun load() {
            scope.launch {
                busy = true
                val out = Shell.readFile("/system/build.prop")
                    ?: Shell.readFile("/vendor/build.prop") ?: ""
                props = out.lineSequence()
                    .filter { it.contains('=') && !it.trim().startsWith('#') }
                    .map { it.substringBefore('=') to it.substringAfter('=') }
                    .toList()
                busy = false
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m03_loaded"), "${props.size}") {
            if (busy) LoadingRow(L("scanning"))
            LabeledField(L("filter"), filter, { filter = it }, placeholder = "ro.build.id")
            LazyColumn(Modifier.height(240.dp)) {
                items(props.filter { filter.isBlank() || it.first.contains(filter, true) }) { (k, v) ->
                    ConsoleLine("$k = $v")
                }
            }
        }
        SectionCard(L("m03_edit")) {
            LabeledField("key", editKey, { editKey = it }, placeholder = "persist.sys.theme")
            LabeledField("value", editVal, { editVal = it }, placeholder = "1")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("apply"), {
                    scope.launch {
                        busy = true
                        val safeVal = editVal.replace("\"", "").replace("'", "").replace("$", "").replace(";", "")
                        val r = Shell.su(
                            "mount -o remount,rw /system 2>/dev/null; " +
                                "sed -i '/^$editKey=/d' /system/build.prop 2>/dev/null; " +
                                "echo '$editKey=$safeVal' >> /system/build.pid 2>/dev/null || " +
                                "echo '$editKey=$safeVal' >> /system/build.prop; " +
                                "mount -o remount,ro /system 2>/dev/null; echo DONE"
                        )
                        val outLines = r.output.lines()
                        load()
                        busy = false
                        android.widget.Toast.makeText(
                            toastCtx,
                            if (r.ok) "OK" else r.output.take(200), android.widget.Toast.LENGTH_SHORT
                        ).show()
                        _unusedLogs(outLines)
                    }
                }, Modifier.weight(1f), Icons.Filled.Save)
                MsiButton(L("refresh"), { load() }, Modifier.weight(1f), Icons.Filled.Refresh, filled = false)
            }
            InfoTextSmall(L("m03_hint"))
        }
    }
}

private fun _unusedLogs(l: List<String>) { /* keep for debugging */ }

// ==================================================================================
// MODULE 4 — Init.d Script Startup Runner
// ==================================================================================
@Composable
fun InitDRunnerScreen() {
    ModuleScaffold(moduleId = 4) {
        ModuleIntroCard(4)
        RootBanner()
        var scripts by remember { mutableStateOf<List<String>>(emptyList()) }
        var out by remember { mutableStateOf<List<String>>(emptyList()) }
        var newScript by remember { mutableStateOf("#!/system/bin/sh\n# MSI init.d script\necho hello from msi\n") }
        var name by remember { mutableStateOf("99msi") }
        val scope = rememberCoroutineScope()

        fun load() {
            scope.launch {
                val r = Shell.su("ls /system/etc/init.d/ 2>/dev/null || ls /su/etc/init.d/ 2>/dev/null", 8000)
                scripts = r.stdout.lineSequence().filter { it.isNotBlank() }.toList()
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m04_found"), "${scripts.size}") {
            if (scripts.isEmpty()) InfoTextSmall(L("m04_none"))
            scripts.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s, style = MaterialTheme.typography.bodySmall)
                    MsiButton(L("run"), {
                        scope.launch {
                            val r = Shell.su("sh '/system/etc/init.d/$s'", 30000)
                            out = r.output.lines()
                        }
                    }, filled = false)
                }
            }
        }
        SectionCard(L("m04_new")) {
            LabeledField(L("m04_name"), name, { name = it })
            LabeledField(L("m04_script"), newScript, { newScript = it }, singleLine = false, mono = true, minLines = 5)
            MsiButton(L("m04_install"), {
                scope.launch {
                    val b64 = android.util.Base64.encodeToString(newScript.toByteArray(), android.util.Base64.DEFAULT)
                    val r = Shell.su(
                        "mkdir -p /system/etc/init.d && " +
                            "echo '$b64' | base64 -d > /system/etc/init.d/$name && " +
                            "chmod 755 /system/etc/init.d/$name && echo INSTALLED"
                    )
                    out = r.output.lines(); load()
                }
            }, icon = Icons.Filled.Save)
            InfoTextSmall(L("m04_hint"))
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 5 — System Partition Remounter (/system, /vendor)
// ==================================================================================
@Composable
fun RemounterScreen() {
    ModuleScaffold(moduleId = 5) {
        ModuleIntroCard(5)
        RootBanner()
        var mounts by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
        var log by remember { mutableStateOf<List<String>>(emptyList()) }
        val scope = rememberCoroutineScope()

        fun load() {
            scope.launch {
                val out = Shell.readFile("/proc/mounts") ?: ""
                mounts = out.lineSequence().mapNotNull { line ->
                    val p = line.split(' ')
                    if (p.size < 4) null
                    else Triple(p[0], p[1], p[3])
                }.filter { it.second in listOf("/system", "/vendor", "/product", "/system_root", "/odm") }.toList()
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m05_current")) {
            if (mounts.isEmpty()) InfoTextSmall(L("m05_none"))
            mounts.forEach { (dev, point, opts) ->
                DataRow(point, opts.substringBefore(',') + " • " + dev.takeLast(20), mono = true)
            }
        }
        SectionCard(L("actions")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("m05_remount_rw"), {
                    scope.launch {
                        val r = Shell.su("mount -o remount,rw /system 2>/dev/null; mount -o remount,rw /vendor 2>/dev/null; mount -o remount,rw / 2>/dev/null; echo RC=\$?")
                        log = r.output.lines(); load()
                    }
                }, Modifier.weight(1f), Icons.Filled.PlayArrow)
                MsiButton(L("m05_remount_ro"), {
                    scope.launch {
                        val r = Shell.su("mount -o remount,ro /system 2>/dev/null; mount -o remount,ro /vendor 2>/dev/null; echo RC=\$?")
                        log = r.output.lines(); load()
                    }
                }, Modifier.weight(1f), danger = true)
            }
            InfoTextSmall(L("m05_hint"))
        }
        if (log.isNotEmpty()) SectionCard(L("output")) { OutputConsole(log) }
    }
}
