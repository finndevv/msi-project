package com.finndev.master.system.inspector.modules.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.AppState
import com.finndev.master.system.inspector.core.Elf
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
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
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 22 — ELF Binary Deep Structure Analyzer (32-bit vs 64-bit inspection)
// ==================================================================================
@Composable
fun ElfAnalyzerScreen() {
    ModuleScaffold(moduleId = 22) {
        ModuleIntroCard(22)
        var path by remember { mutableStateOf("") }
        var info by remember { mutableStateOf<Elf.ElfInfo?>(null) }
        var ls by remember { mutableStateOf(listOf<String>()) }
        val scope = rememberCoroutineScope()

        SectionCard(L("m22_pick")) {
            LabeledField(L("m20_path"), path, { path = it }, mono = true, placeholder = "/system/bin/sh")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("m22_analyze"), {
                    scope.launch {
                        val f = File(path)
                        info = if (f.isFile) Elf.parse(f) else null
                        ls = emptyList()
                    }
                }, Modifier.weight(1f))
                MsiButton(L("m22_list"), {
                    scope.launch {
                        val dir = File(path).parentFile ?: File("/system/bin")
                        ls = dir.listFiles()?.joinToString("\n") { it.name }?.lines()?.take(200) ?: listOf(Ls("m22_no_dir"))
                    }
                }, Modifier.weight(1f), filled = false)
            }
            listOf("/system/bin/sh", "/system/bin/linker64", "/system/lib64/libc.so", "/system/bin/toybox").forEach { preset ->
                MsiButton(preset, { path = preset }, Modifier.fillMaxWidth(), filled = false)
            }
        }
        info?.let { e ->
            SectionCard(if (e.isElf) "ELF ${e.bits}-bit" else L("m22_invalid"), e.file.name) {
                if (!e.isElf) {
                    InfoTextSmall(L("m20_not_elf"))
                    return@SectionCard
                }
                DataRow("Class", "${e.bits}-bit ${e.endian} endian")
                DataRow(L("m22_type"), e.elfType)
                DataRow(L("m22_machine"), e.machineName)
                DataRow("Entry point", "0x${java.lang.Long.toHexString(e.entryPoint)}")
                DataRow("Program headers", "${e.programHeaders}")
                DataRow("Section headers", "${e.sectionHeaders}")
                DataRow("PT_INTERP", e.interpreter ?: "-", mono = true)
                DataRow("Size", com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(e.file.length()))
                StatusBadge(if (e.matchesDevice) L("m20_compat_yes") else L("m20_compat_no"), e.matchesDevice)
            }
        }
        if (ls.isNotEmpty()) SectionCard(L("m22_listing")) { OutputConsole(ls, maxLinesShown = 200) }
    }
}

// ==================================================================================
// MODULE 23 — File Permission Manager (Chmod / Chown via root)
// ==================================================================================
@Composable
fun FilePermScreen() {
    ModuleScaffold(moduleId = 23) {
        ModuleIntroCard(23)
        RootBanner()
        var path by remember { mutableStateOf("/sdcard/MSI") }
        var stat by remember { mutableStateOf("") }
        var mode by remember { mutableStateOf("755") }
        var owner by remember { mutableStateOf("") }
        var out by remember { mutableStateOf(listOf<String>()) }
        val scope = rememberCoroutineScope()

        fun statFile() {
            scope.launch {
                val r = Shell.su("ls -ld '$path' 2>/dev/null || stat '$path' 2>/dev/null")
                stat = r.output.trim()
            }
        }
        LaunchedEffect(Unit) { statFile() }

        SectionCard(L("m23_stat")) {
            LabeledField(L("m20_path"), path, { path = it }, mono = true)
            MsiButton(L("refresh"), { statFile() }, Modifier.fillMaxWidth(), Icons.Filled.Refresh)
            if (stat.isNotBlank()) OutputConsole(stat.lines(), maxLinesShown = 4)
        }
        SectionCard("chmod") {
            listOf("755", "644", "700", "777", "000").forEach { preset ->
                MsiButton(preset, { mode = preset }, Modifier.fillMaxWidth(), filled = (mode == preset))
            }
            var custom by remember { mutableStateOf(mode) }
            LabeledField(L("m23_mode"), custom, { custom = it; mode = it }, mono = true)
            MsiButton("chmod", {
                scope.launch {
                    val r = Shell.su("chmod $mode '$path' && ls -ld '$path'")
                    out = r.output.lines(); statFile()
                }
            })
        }
        SectionCard("chown") {
            LabeledField("user:group", owner, { owner = it }, placeholder = "root:root / 0:0 / u0_a123")
            MsiButton("chown", {
                scope.launch {
                    val r = Shell.su("chown ${owner.replace(";", "")} '$path' && ls -ld '$path'")
                    out = r.output.lines(); statFile()
                }
            })
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 24 — Environment PATH Variable Manager & Editor
// ==================================================================================
@Composable
fun PathManagerScreen() {
    ModuleScaffold(moduleId = 24) {
        ModuleIntroCard(24)
        var currentPath by remember { mutableStateOf(Shell.msiPath()) }
        var custom by remember { mutableStateOf(AppState.customPath.value) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            currentPath = Shell.msiPath()
        }

        SectionCard("PATH") {
            currentPath.split(':').filter { it.isNotBlank() }.forEachIndexed { i, entry ->
                DataRow("[$i]", entry, mono = true)
            }
        }
        SectionCard(L("m24_custom")) {
            LabeledField(L("m24_extra"), custom, { custom = it }, mono = true, placeholder = "/data/data/com.termux/files/usr/bin")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("save"), {
                    AppState.setCustomPath(custom.trim())
                    currentPath = Shell.msiPath()
                }, Modifier.weight(1f), Icons.Filled.Save)
                MsiButton(L("reset"), {
                    AppState.setCustomPath("")
                    custom = ""
                    currentPath = Shell.msiPath()
                }, Modifier.weight(1f), danger = true)
            }
            InfoTextSmall(L("m24_hint"))
        }
    }
}

// ==================================================================================
// MODULE 25 — Busybox Integration & Toolkit Validator
// ==================================================================================
@Composable
fun BusyboxScreen() {
    ModuleScaffold(moduleId = 25) {
        ModuleIntroCard(25)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var found by remember { mutableStateOf<String?>(null) }
        var applets by remember { mutableStateOf(listOf<String>()) }
        var out by remember { mutableStateOf(listOf<String>()) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun detect() {
            scope.launch {
                found = Shell.which("busybox")
                if (found == null) {
                    val r = Shell.su("which busybox 2>/dev/null || ls /system/xbin/busybox /su/bin/busybox /data/adb/magisk/busybox 2>/dev/null | head -1")
                    found = r.stdout.trim().ifBlank { null }
                }
                applets = if (found != null) {
                    val r = Shell.su("$found --list 2>/dev/null || $found --help 2>&1 | head -80")
                    r.stdout.lines().filter { it.matches(Regex("[a-z0-9_-]{2,20}")) }.take(300)
                } else emptyList()
            }
        }
        LaunchedEffect(Unit) { detect() }

        SectionCard(L("m25_detect")) {
            DataRow("busybox", found ?: L("m25_not_found"), mono = true)
            DataRow(L("m25_applets"), "${applets.size}")
            MsiButton(L("refresh"), { detect() }, Modifier.fillMaxWidth(), Icons.Filled.Refresh)
        }
        SectionCard(L("m25_install")) {
            InfoTextSmall(L("m25_install_hint"))
            if (busy) LoadingRow(L("downloading"))
            MsiButton(L("m25_install_btn"), {
                scope.launch {
                    busy = true
                    out = listOf("[MSI] apk add busybox-static (Alpine) ...")
                    val r = com.finndev.master.system.inspector.core.PRootEngine.runOnce(
                        ctx, listOf("/bin/sh", "-c",
                            "apk add -q busybox-static && cp \$(command -v busybox) /sdcard/MSI/busybox && echo INSTALLED /sdcard/MSI/busybox"),
                        timeoutMs = 600_000,
                    )
                    out = (out + r.output.lines()).takeLast(100)
                    busy = false
                    detect()
                }
            }, enabled = !busy)
        }
        if (applets.isNotEmpty()) SectionCard(L("m25_applets")) {
            OutputConsole(applets, maxLinesShown = 120)
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}
