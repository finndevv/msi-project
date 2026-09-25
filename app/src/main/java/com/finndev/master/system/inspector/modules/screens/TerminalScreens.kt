package com.finndev.master.system.inspector.modules.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finndev.master.system.inspector.core.Exports
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.MonoTerminal
import com.finndev.master.system.inspector.core.PRootEngine
import com.finndev.master.system.inspector.core.TerminalCenter
import com.finndev.master.system.inspector.core.VtTerm
import com.finndev.master.system.inspector.ui.ChipRow
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.StatCard
import com.finndev.master.system.inspector.ui.LocalNavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import com.finndev.master.system.inspector.ui.DataRow

// ==================================================================================
// MODULE 16 — True Alpine Linux PRoot Terminal (with apk support)
// ==================================================================================

private fun renderLine(row: List<VtTerm.Cell>): AnnotatedString {
    return buildAnnotatedString {
        var lastFg = -99; var lastBold = false
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                val color = Color(VtTerm.colorOf(if (lastFg == -99) 99 else lastFg))
                pushStyle(SpanStyle(color = color, fontWeight = if (lastBold) FontWeight.Bold else null))
                append(sb.toString())
                pop()
                sb.clear()
            }
        }
        row.forEach { cell ->
            if (cell.fg != lastFg || cell.bold != lastBold) {
                flush(); lastFg = cell.fg; lastBold = cell.bold
            }
            sb.append(cell.ch)
        }
        flush()
    }
}

@Composable
fun TerminalScreen() {
    ModuleScaffold(moduleId = 16, scrollable = false) {
        ModuleIntroCard(16)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val state by TerminalCenter.state.collectAsState()
        val frame by TerminalCenter.term.frame.collectAsState()

        // auto-start on first open
        LaunchedEffect(Unit) {
            if (TerminalCenter.state.value == TerminalCenter.State.IDLE) TerminalCenter.start(ctx)
        }

        // session status row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                L("m16_state"),
                when (state) {
                    TerminalCenter.State.IDLE -> L("m16_idle")
                    TerminalCenter.State.BOOTSTRAPPING -> L("m16_bootstrapping")
                    TerminalCenter.State.RUNNING -> L("m16_running")
                    TerminalCenter.State.EXITED -> L("m16_exited")
                    TerminalCenter.State.ERROR -> L("error")
                },
                modifier = Modifier.weight(1f),
            )
            StatCard(
                "Alpine",
                PRootEngine.alpineVersion(ctx) ?: "-",
                PRootEngine.alpineArch() ?: "?",
                Modifier.weight(1f),
            )
        }

        // terminal viewport
        Surface(
            Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0A0A0A),
        ) {
            TerminalViewport(frame)
        }

        // input line
        var input by remember { mutableStateOf("") }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(L("m16_input_hint"), style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A6B55)) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = MonoTerminal, fontSize = 13.sp, color = Color(0xFFD6FFE2)),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                TerminalCenter.send(input); input = ""
            }),
        )

        // extra keys row
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val keys = listOf(
                "Esc" to "\u001B", "Tab" to "\t", "↑" to "\u001B[A", "↓" to "\u001B[B",
                "←" to "\u001B[D", "→" to "\u001B[C", "Ctrl+C" to "\u0003", "Ctrl+D" to "\u0004",
                "Ctrl+Z" to "\u001A", "Ctrl+L" to "\u000C",
            )
            keys.forEach { (label, code) ->
                Surface(
                    onClick = { TerminalCenter.sendRaw(code.toByteArray(Charsets.ISO_8859_1)) },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF12301C),
                ) {
                    Text(
                        label, color = Color(0xFF69FF94), fontSize = 11.sp, fontFamily = MonoTerminal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // action row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MsiButton(L("m16_restart"), {
                TerminalCenter.stop()
                TerminalCenter.start(ctx)
            }, Modifier.weight(1f), Icons.Filled.Refresh)
            MsiButton(L("m16_stop"), {
                TerminalCenter.stop()
            }, Modifier.weight(1f), Icons.Filled.Stop, danger = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MsiButton(L("m16_apk_update"), {
                TerminalCenter.send("apk update && apk upgrade")
            }, Modifier.weight(1f), filled = false)
            MsiButton(L("m16_share_log"), {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val text = synchronized(TerminalCenter.transcript) { TerminalCenter.transcript.joinToString("\n") }
                val f = Exports.saveLog(ctx, "msi-terminal-$stamp.log", text.lineSequence().toList())
                Exports.shareFile(ctx, f)
            }, Modifier.weight(1f), Icons.Filled.Share, filled = false)
        }
        InfoTextSmall(L("m16_hint"))
    }
}

@Composable
private fun TerminalViewport(frame: Int) {
    val listState = rememberLazyListState()
    val term = TerminalCenter.term
    // snapshot rows on every frame bump so LazyColumn items see fresh immutable data
    val snapshot = remember(frame) { term.lines.map { it.toList() }.takeLast(400) }
    val atBottom by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { it.index >= snapshot.size - 2 } ?: true } }
    LaunchedEffect(frame, snapshot.size) {
        if (atBottom && snapshot.isNotEmpty()) {
            listState.scrollToItem(snapshot.size - 1)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        itemsIndexed(snapshot, key = { idx, _ -> idx }) { _, row ->
            Text(
                renderLine(row),
                fontFamily = MonoTerminal,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                color = Color(0xFFD6FFE2),
            )
        }
    }
}

// ==================================================================================
// MODULE 17 — Termux Integration & Git Cloner (auto-detects com.termux)
// ==================================================================================
@Composable
fun TermuxScreen() {
    ModuleScaffold(moduleId = 17) {
        ModuleIntroCard(17)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var installed by remember { mutableStateOf<Boolean?>(null) }
        var version by remember { mutableStateOf("") }
        var repoUrl by remember { mutableStateOf("https://github.com/torvalds/linux.git") }
        var out by remember { mutableStateOf(listOf<String>()) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            runCatching {
                val pm = ctx.packageManager
                val info = pm.getPackageInfo("com.termux", 0)
                installed = true
                version = info.versionName ?: ""
            }.onFailure { installed = false }
        }

        SectionCard("Termux") {
            DataRow("com.termux", when (installed) {
                null -> "…"
                true -> L("m17_installed") + (if (version.isNotBlank()) " v$version" else "")
                else -> L("m17_not_installed")
            })
            MsiButton(L("m17_launch"), {
                runCatching {
                    ctx.startActivity(
                        android.content.Intent("com.termux.OPEN_APP").apply {
                            setClassName("com.termux", "com.termux.app.TermuxActivity")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }.onFailure {
                    runCatching {
                        ctx.packageManager.getLaunchIntentForPackage("com.termux")?.let { ctx.startActivity(it) }
                    }
                }
            }, enabled = installed == true)
        }
        SectionCard(L("m17_git")) {
            LabeledField("URL", repoUrl, { repoUrl = it }, mono = true)
            MsiButton(L("m17_clone"), {
                if (busy) return@MsiButton
                scope.launch {
                    busy = true
                    out = listOf("[MSI] ensuring git in Alpine rootfs ...")
                    val install = PRootEngine.runOnce(
                        ctx, listOf("/bin/sh", "-lc", "apk add -q git || apk add git"), timeoutMs = 300_000
                    )
                    out = out + install.output.lines()
                    val name = repoUrl.trimEnd('/').substringAfterLast('/')
                        .removeSuffix(".git").ifBlank { "repo" }
                    val target = "/sdcard/MSI/GitRepos/$name"
                    out = out + listOf("[MSI] git clone --depth 1 -> $target")
                    val clone = PRootEngine.runOnce(
                        ctx,
                        listOf("/bin/sh", "-lc", "rm -rf '$target' && git clone --depth 1 '${repoUrl.replace("'", "")}' '$target' && echo CLONE_OK"),
                        timeoutMs = 600_000,
                    )
                    out = (out + clone.output.lines()).takeLast(300)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow, enabled = !busy)
            InfoTextSmall(L("m17_git_hint"))
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 18 — QEMU Multi-Architecture Emulator Launcher (x86_64, i386, ARM64)
// ==================================================================================
@Composable
fun QemuScreen() {
    ModuleScaffold(moduleId = 18) {
        ModuleIntroCard(18)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val nav = LocalNavController.current
        var archIdx by remember { mutableStateOf(0) }
        var ramIdx by remember { mutableStateOf(1) }
        var diskGb by remember { mutableStateOf("2") }
        var isoPath by remember { mutableStateOf("") }
        var out by remember { mutableStateOf(listOf<String>()) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val arches = listOf("x86_64", "i386", "aarch64")
        val rams = listOf("128", "256", "512", "1024")

        val qemuPkg = when (archIdx) {
            0 -> "qemu-system-x86_64"
            1 -> "qemu-system-i386"
            else -> "qemu-system-aarch64"
        }

        SectionCard(L("m18_config")) {
            Text(L("m18_arch"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipRow(arches, archIdx) { archIdx = it }
            Text(L("m18_ram"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipRow(rams.map { "$it MB" }, ramIdx) { ramIdx = it }
            LabeledField(L("m18_disk"), diskGb, { diskGb = it }, placeholder = "2")
            LabeledField("ISO (/sdcard/...)", isoPath, { isoPath = it }, mono = true, placeholder = "/sdcard/Download/systemrescue.iso")
        }

        SectionCard(L("actions")) {
            MsiButton(L("m18_install"), {
                scope.launch {
                    busy = true
                    out = listOf("[MSI] apk add $qemuPkg qemu-img (inside Alpine, may take a while)")
                    val r = PRootEngine.runOnce(
                        ctx, listOf("/bin/sh", "-lc", "apk update -q && apk add -q $qemuPkg qemu-img && echo QEMU_READY"),
                        timeoutMs = 900_000,
                    )
                    out = (out + r.output.lines()).takeLast(200)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            MsiButton(L("m18_create_disk"), {
                scope.launch {
                    busy = true
                    val r = PRootEngine.runOnce(
                        ctx, listOf("/bin/sh", "-lc",
                            "mkdir -p /sdcard/MSI/qemu && qemu-img create -f qcow2 /sdcard/MSI/qemu/disk-${arches[archIdx]}.qcow2 ${diskGb}G && echo DISK_OK"),
                        timeoutMs = 120_000,
                    )
                    out = (out + r.output.lines()).takeLast(200)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            val cdrom = if (isoPath.isNotBlank()) " -cdrom '$isoPath'" else ""
            val cmd = "$qemuPkg -m ${rams[ramIdx]} -drive file=/sdcard/MSI/qemu/disk-${arches[archIdx]}.qcow2,if=virtio$cdrom -nographic -serial mon:stdio"
            MsiButton(L("m18_launch"), {
                TerminalCenter.startCustom(
                    ctx,
                    listOf("/bin/sh", "-lc", cmd),
                    "QEMU ${arches[archIdx]} — ${rams[ramIdx]} MB"
                )
                nav.navigate("module/16")
            }, Modifier.fillMaxWidth(), Icons.Filled.Terminal)
            InfoTextSmall(L("m18_hint"))
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}
