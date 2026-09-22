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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
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
import com.finndev.master.system.inspector.core.AppState
import com.finndev.master.system.inspector.core.Elf
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.PRootEngine
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.core.TerminalCenter
import com.finndev.master.system.inspector.ui.ChipRow
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.StatCard
import com.finndev.master.system.inspector.ui.LocalNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 19 — Alpine-Bound Bash & Shell Script Runner (.sh live output)
// ==================================================================================
@Composable
fun ScriptRunnerScreen() {
    ModuleScaffold(moduleId = 19) {
        ModuleIntroCard(19)
        val ctx = LocalContext.current
        var script by remember {
            mutableStateOf("#!/bin/sh\necho \"MSI script runner — Alpine / PRoot\"\nuname -a\napk --version\n")
        }
        var out by remember { mutableStateOf(listOf<String>()) }
        var busy by remember { mutableStateOf(false) }
        var engineIdx by remember { mutableStateOf(0) } // 0=Alpine sh, 1=Alpine bash, 2=Android sh
        val scope = rememberCoroutineScope()

        SectionCard(L("m19_engine")) {
            ChipRow(listOf("Alpine /bin/sh", "Alpine bash", "Android /system/bin/sh"), engineIdx) { engineIdx = it }
            if (engineIdx == 1) InfoTextSmall(L("m19_bash_note"))
        }
        SectionCard(L("m19_script")) {
            LabeledField("", script, { script = it }, singleLine = false, mono = true, minLines = 8)
            if (busy) LoadingRow(L("m19_running"))
            MsiButton(L("run"), {
                scope.launch {
                    busy = true
                    out = listOf("[MSI] running ...")
                    val b64 = android.util.Base64.encodeToString(script.toByteArray(), android.util.Base64.DEFAULT).replace("\n", "")
                    when (engineIdx) {
                        0, 1 -> {
                            if (engineIdx == 1) {
                                PRootEngine.runOnce(ctx, listOf("/bin/sh", "-c", "apk add -q bash 2>/dev/null"), timeoutMs = 300_000)
                            }
                            val sh = if (engineIdx == 1) "/bin/bash" else "/bin/sh"
                            val r = PRootEngine.runOnce(
                                ctx, listOf("/bin/sh", "-c", "echo '$b64' | base64 -d > /tmp/msi.sh && chmod +x /tmp/msi.sh && $sh /tmp/msi.sh; echo \"EXIT=\$?\""),
                                timeoutMs = 600_000,
                            )
                            out = (out + r.output.lines()).takeLast(500)
                        }
                        2 -> {
                            val r = withContext(Dispatchers.IO) {
                                val f = File(ctx.cacheDir, "msi-script.sh")
                                f.writeText(script)
                                Shell.captureRaw(listOf("sh", f.absolutePath), timeoutMs = 300_000)
                            }
                            out = (out + r.output.lines()).takeLast(500)
                        }
                    }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow, enabled = !busy)
        }
        if (out.size > 1) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 20 — Native ELF Binary Architecture Runner (ARM/x86 execution)
// ==================================================================================
@Composable
fun ElfRunnerScreen() {
    ModuleScaffold(moduleId = 20) {
        ModuleIntroCard(20)
        val ctx = LocalContext.current
        var path by remember { mutableStateOf("") }
        var args by remember { mutableStateOf("") }
        var info by remember { mutableStateOf<Elf.ElfInfo?>(null) }
        var out by remember { mutableStateOf(listOf<String>()) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        SectionCard(L("m20_pick")) {
            LabeledField(L("m20_path"), path, { path = it }, mono = true, placeholder = "/sdcard/Download/busybox")
            LabeledField(L("m20_args"), args, { args = it }, placeholder = "--help")
            MsiButton(L("m20_inspect"), {
                scope.launch {
                    val f = File(path)
                    info = if (f.isFile) Elf.parse(f) else null
                }
            })
            info?.let { e ->
                DataRow("ELF", if (e.isElf) "${e.bits}-bit ${e.endian}" else L("m20_not_elf"))
                DataRow(L("m22_machine"), e.machineName)
                DataRow("ABI", Elf.abiOf(e.machine))
                DataRow(L("m20_compat"), if (e.matchesDevice) L("m20_compat_yes") else L("m20_compat_no"))
            }
        }
        SectionCard(L("run")) {
            MsiButton(L("run"), {
                scope.launch {
                    busy = true
                    out = listOf("[MSI] chmod +x & exec ...")
                    val argv = mutableListOf(
                        "su", "-c",
                        "chmod 755 '${path.replace("'", "")}' && '${path.replace("'", "")}' ${args.replace(";", "")}"
                    )
                    val r = withContext(Dispatchers.IO) { Shell.captureRaw(argv, timeoutMs = 120_000) }
                    out = (out + r.output.lines()).takeLast(400)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow, enabled = path.isNotBlank() && !busy)
            InfoTextSmall(L("m20_hint"))
        }
        if (out.size > 1) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 21 — Python & Lua Lightweight Script Interpreter
// ==================================================================================
@Composable
fun PythonLuaScreen() {
    ModuleScaffold(moduleId = 21) {
        ModuleIntroCard(21)
        val ctx = LocalContext.current
        val nav = LocalNavController.current
        var langIdx by remember { mutableStateOf(0) } // 0 python, 1 lua
        var code by remember {
            mutableStateOf(
                "import platform, sys\nprint('MSI Python on', platform.machine())\nprint(sys.version)\nfor i in range(5):\n    print('square', i, i*i)\n"
            )
        }
        var out by remember { mutableStateOf(listOf<String>()) }
        var busy by remember { mutableStateOf(false) }
        var installed by remember { mutableStateOf<Boolean?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(langIdx) {
            val pkg = if (langIdx == 0) "python3" else "lua5.4"
            val check = PRootEngine.runOnce(
                ctx, listOf("/bin/sh", "-c", "command -v $pkg >/dev/null && echo YES || echo NO"),
                timeoutMs = 60_000,
            )
            installed = check.stdout.contains("YES")
        }

        SectionCard(L("m21_lang")) {
            ChipRow(listOf("Python 3", "Lua 5.4"), langIdx) { langIdx = it }
            DataRow(
                L("m21_runtime"),
                when (installed) {
                    null -> "…"
                    true -> L("m21_installed")
                    else -> L("m21_not_installed")
                }
            )
            if (installed == false) {
                MsiButton(L("m21_install"), {
                    scope.launch {
                        busy = true
                        val pkg = if (langIdx == 0) "python3" else "lua5.4 lua5.4-libs"
                        out = listOf("[MSI] apk add $pkg (Alpine) ...")
                        val r = PRootEngine.runOnce(
                            ctx, listOf("/bin/sh", "-c", "apk add -q $pkg 2>&1 | tail -5; command -v ${if (langIdx == 0) "python3" else "lua5.4"} && echo INSTALL_OK"),
                            timeoutMs = 900_000,
                        )
                        out = (out + r.output.lines()).takeLast(300)
                        installed = r.stdout.contains("INSTALL_OK") || r.stdout.contains("python") || r.stdout.contains("lua")
                        busy = false
                    }
                }, enabled = !busy)
            }
        }
        SectionCard(L("m21_code")) {
            LabeledField("", code, { code = it }, singleLine = false, mono = true, minLines = 8)
            if (busy) LoadingRow(L("m21_running"))
            MsiButton(L("run"), {
                scope.launch {
                    busy = true
                    val b64 = android.util.Base64.encodeToString(code.toByteArray(), android.util.Base64.DEFAULT).replace("\n", "")
                    val cmd = if (langIdx == 0)
                        "echo '$b64' | base64 -d > /tmp/msi.py && python3 /tmp/msi.py; echo \"EXIT=\$?\""
                    else
                        "echo '$b64' | base64 -d > /tmp/msi.lua && lua5.4 /tmp/msi.lua; echo \"EXIT=\$?\""
                    out = listOf("[MSI] running ${if (langIdx == 0) "python3" else "lua5.4"} in Alpine ...")
                    val r = PRootEngine.runOnce(ctx, listOf("/bin/sh", "-c", cmd), timeoutMs = 600_000)
                    out = (out + r.output.lines()).takeLast(500)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow, enabled = !busy)
            MsiButton(L("m21_in_terminal"), {
                val b64 = android.util.Base64.encodeToString(code.toByteArray(), android.util.Base64.DEFAULT).replace("\n", "")
                val cmd = if (langIdx == 0)
                    "echo '$b64' | base64 -d > /tmp/msi.py && python3 -i /tmp/msi.py"
                else
                    "echo '$b64' | base64 -d > /tmp/msi.lua && lua5.4 -i /tmp/msi.lua"
                TerminalCenter.startCustom(ctx, listOf("/bin/sh", "-lc", cmd), "interpreter")
                nav.navigate("module/16")
            }, Modifier.fillMaxWidth(), Icons.Filled.Terminal, filled = false)
        }
        if (out.size > 1) SectionCard(L("output")) { OutputConsole(out) }
    }
}
