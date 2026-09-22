package com.finndev.master.system.inspector.modules.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finndev.master.system.inspector.BuildConfig
import com.finndev.master.system.inspector.core.AppState
import com.finndev.master.system.inspector.core.Downloader
import com.finndev.master.system.inspector.core.Elf
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
import com.finndev.master.system.inspector.core.Loc
import com.finndev.master.system.inspector.core.ModuleRegistry
import com.finndev.master.system.inspector.core.MsiTheme
import com.finndev.master.system.inspector.core.PRootEngine
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
import com.finndev.master.system.inspector.ui.StatusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size

// ==================================================================================
// MODULE 72 — Timer, Precision Stopwatch & Chronometer
// ==================================================================================
@Composable
fun TimerScreen() {
    ModuleScaffold(moduleId = 72) {
        ModuleIntroCard(72)
        var stopwatchRunning by remember { mutableStateOf(false) }
        var stopwatchMs by remember { mutableLongStateOf(0L) }
        var laps by remember { mutableStateOf(listOf<Long>()) }
        var timerRunning by remember { mutableStateOf(false) }
        var timerLeftMs by remember { mutableLongStateOf(60_000L) }
        var timerSetMin by remember { mutableStateOf("1") }
        var timerSetSec by remember { mutableStateOf("0") }
        val ctx = LocalContext.current

        LaunchedEffect(stopwatchRunning) {
            var last = System.currentTimeMillis()
            while (stopwatchRunning && isActive) {
                delay(33)
                val now = System.currentTimeMillis()
                stopwatchMs += now - last
                last = now
            }
        }
        LaunchedEffect(timerRunning) {
            var last = System.currentTimeMillis()
            while (timerRunning && isActive) {
                delay(100)
                val now = System.currentTimeMillis()
                timerLeftMs -= (now - last)
                last = now
                if (timerLeftMs <= 0) {
                    timerLeftMs = 0
                    timerRunning = false
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 700)
                }
            }
        }

        SectionCard(L("m72_stopwatch")) {
            Text(
                formatMs(stopwatchMs),
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (stopwatchRunning) {
                    MsiButton(L("m72_lap"), { laps = (laps + stopwatchMs).takeLast(20) }, Modifier.weight(1f))
                    MsiButton(L("stop"), { stopwatchRunning = false }, Modifier.weight(1f), Icons.Filled.Stop, danger = true)
                } else {
                    MsiButton(L("start"), { stopwatchRunning = true }, Modifier.weight(1f), Icons.Filled.PlayArrow)
                    MsiButton(L("reset"), { stopwatchMs = 0; laps = emptyList() }, Modifier.weight(1f), Icons.Filled.Refresh)
                }
            }
            if (laps.isNotEmpty()) {
                laps.forEachIndexed { i, l ->
                    val prev = if (i > 0) laps[i - 1] else 0L
                    DataRow("LAP ${i + 1}", "${formatMs(l - prev)}  (${formatMs(l)})", mono = true)
                }
            }
        }
        SectionCard(L("m72_timer")) {
            Text(
                formatMs(timerLeftMs),
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = if (timerLeftMs == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField("min", timerSetMin, { timerSetMin = it }, Modifier.weight(1f))
                LabeledField("sec", timerSetSec, { timerSetSec = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (timerRunning) {
                    MsiButton(L("stop"), { timerRunning = false }, Modifier.weight(1f), Icons.Filled.Stop, danger = true)
                } else {
                    MsiButton(L("start"), {
                        val total = ((timerSetMin.toLongOrNull() ?: 0) * 60 + (timerSetSec.toLongOrNull() ?: 0)) * 1000
                        if (total > 0) {
                            timerLeftMs = total
                            timerRunning = true
                        }
                    }, Modifier.weight(1f), Icons.Filled.PlayArrow)
                }
                MsiButton(L("reset"), { timerLeftMs = 0; timerRunning = false }, Modifier.weight(1f), Icons.Filled.Refresh)
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = ms / 10
    val cs = total % 100
    val s = (total / 100) % 60
    val m = (total / 6000) % 60
    val h = total / 360000
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
}

// ==================================================================================
// MODULE 73 — Multi-Language Engine & Dynamic Switcher (TR, EN, ES, FR, IT)
// ==================================================================================
@Composable
fun LanguageSwitcherScreen() {
    ModuleScaffold(moduleId = 73) {
        ModuleIntroCard(73)
        val current by AppState.language.collectAsState()
        SectionCard(L("ob_lang_title")) {
            Text(L("ob_lang_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Loc.languages.forEach { meta ->
                Surface(
                    onClick = { AppState.setLanguage(meta.code) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (current == meta.code) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(meta.code.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(meta.nativeName, style = MaterialTheme.typography.bodyLarge)
                            Text(meta.englishName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            InfoTextSmall(L("m73_hint"))
        }
        SectionCard(L("m73_status")) {
            DataRow("active", current)
            DataRow("supported", Loc.languages.joinToString(", ") { it.code })
            DataRow("modules", L("about_modules_count", 78))
        }
    }
}

// ==================================================================================
// MODULE 74 — Dynamic UI Theme Engine (Material 3, Hacker, Normal)
// ==================================================================================
@Composable
fun ThemeEngineScreen() {
    ModuleScaffold(moduleId = 74) {
        ModuleIntroCard(74)
        val current by AppState.theme.collectAsState()
        val themes = listOf(
            Triple(MsiTheme.MATERIAL3, "Material 3", "m74_m3_desc"),
            Triple(MsiTheme.HACKER, "Hacker / Cyberpunk", "m74_hacker_desc"),
            Triple(MsiTheme.CLASSIC, L("m74_classic"), "m74_classic_desc"),
        )
        SectionCard(L("m74_select")) {
            themes.forEach { (theme, name, descKey) ->
                Surface(
                    onClick = { AppState.setTheme(theme) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (current == theme) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(L(descKey), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (current == theme) StatusBadge(L("m74_active"), true)
                    }
                }
            }
        }
        SectionCard(L("m74_preview")) {
            // live preview of current theme colors
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(40.dp)) {
                    Text("primary", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                }
                Surface(color = MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(40.dp)) {
                    Text("secondary", color = MaterialTheme.colorScheme.onSecondary, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                }
                Surface(color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(40.dp)) {
                    Text("error", color = MaterialTheme.colorScheme.onError, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                }
            }
            Text(
                "JetBrains Mono 0123456789 >_",
                fontFamily = com.finndev.master.system.inspector.core.JetBrainsMono,
                style = MaterialTheme.typography.bodyMedium,
            )
            DataRow("background", "#${Integer.toHexString(
                (MaterialTheme.colorScheme.background.hashCode() ushr 8) and 0xFFFFFF)}")
        }
    }
}

// ==================================================================================
// MODULE 75 — Alpine Rootfs Bootstrap Status & Installer Monitor
// ==================================================================================
@Composable
fun AlpineStatusScreen() {
    ModuleScaffold(moduleId = 75) {
        ModuleIntroCard(75)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf(PRootEngine.status(ctx)) }
        var busy by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf(0 to "") }
        var log by remember { mutableStateOf(PRootEngine.lastLog()) }

        LaunchedEffect(Unit) {
            while (isActive) {
                status = PRootEngine.status(ctx)
                delay(1500)
            }
        }

        SectionCard(L("m75_status")) {
            DataRow("PRoot", if (status.prootOk) "✔ " + (status.prootPath ?: "") else "✘", mono = true)
            DataRow("Alpine", status.version ?: "-")
            DataRow("arch", status.arch ?: "-")
            DataRow(L("m75_verified"), if (status.verified) L("yes") else L("no"))
            DataRow(L("size"), SystemInfo.fmtBytes(status.sizeBytes))
            DataRow(L("m75_path"), status.rootfsDir.absolutePath, mono = true)
            StatusBadge(if (status.rootfsReady) L("m75_ready") else L("m75_not_ready"), status.rootfsReady)
        }
        SectionCard(L("actions")) {
            if (busy) {
                Text("${progress.first}% • ${progress.second}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress.first / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MsiButton(L("m75_install"), {
                scope.launch {
                    busy = true
                    val r = PRootEngine.bootstrap(ctx, force = true) { pct, stage -> progress = pct to stage }
                    log = PRootEngine.lastLog()
                    busy = false
                    status = PRootEngine.status(ctx)
                    if (r.isFailure) {
                        android.widget.Toast.makeText(ctx, "[MSI] ${r.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            MsiButton(L("m75_update"), {
                scope.launch {
                    busy = true
                    val r = PRootEngine.downloadRootfs(ctx) { pct, stage -> progress = pct to stage }
                    log = PRootEngine.lastLog()
                    busy = false
                    status = PRootEngine.status(ctx)
                }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            MsiButton(L("m75_remove"), {
                PRootEngine.removeRootfs(ctx)
                status = PRootEngine.status(ctx)
                log = PRootEngine.lastLog()
            }, Modifier.fillMaxWidth(), Icons.Filled.DeleteForever, danger = true)
            InfoTextSmall(L("m75_hint"))
        }
        if (log.isNotEmpty()) SectionCard(L("m75_log")) {
            OutputConsole(log.takeLast(40), maxLinesShown = 40)
        }
    }
}

// ==================================================================================
// MODULE 76 — Native ELF Binary Architecture Matcher
// ==================================================================================
@Composable
fun ElfMatcherScreen() {
    ModuleScaffold(moduleId = 76) {
        ModuleIntroCard(76)
        var path by remember { mutableStateOf("") }
        var info by remember { mutableStateOf<Elf.ElfInfo?>(null) }
        val scope = rememberCoroutineScope()

        SectionCard(L("m76_device")) {
            Elf.deviceMachineCodes().forEach { (code, name) ->
                DataRow(name, "$code (${Elf.abiOf(code)})", mono = true)
            }
        }
        SectionCard(L("m76_check")) {
            LabeledField(L("m20_path"), path, { path = it }, mono = true, placeholder = "/sdcard/Download/binary")
            MsiButton(L("m76_match"), {
                scope.launch(Dispatchers.IO) {
                    info = if (File(path).isFile) Elf.parse(File(path)) else null
                }
            }, Modifier.fillMaxWidth())
            info?.let { e ->
                if (!e.isElf) {
                    Text(L("m20_not_elf"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                } else {
                    DataRow("ELF", "${e.bits}-bit ${e.machineName}")
                    DataRow("soname/ABI", Elf.abiOf(e.machine))
                    val deviceCodes = Elf.deviceMachineCodes().map { it.first }
                    val matches = e.machine in deviceCodes
                    Text(
                        if (matches) "✔ " + L("m76_will_run") else "✘ " + L("m76_wont_run"),
                        color = if (matches) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            InfoTextSmall(L("m76_hint"))
        }
    }
}

// ==================================================================================
// MODULE 77 — App Update & Module Version Checker
// ==================================================================================
@Composable
fun UpdateCheckerScreen() {
    ModuleScaffold(moduleId = 77) {
        ModuleIntroCard(77)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var checking by remember { mutableStateOf(false) }
        var remoteVersion by remember { mutableStateOf<String?>(null) }
        var remoteError by remember { mutableStateOf<String?>(null) }

        val appVersion = BuildConfig.VERSION_NAME

        SectionCard(L("m77_app")) {
            DataRow("MSI", "v$appVersion (40000)")
            DataRow(L("m77_modules"), "78")
            DataRow("Alpine", PRootEngine.alpineVersion(ctx) ?: "-")
            DataRow("PRoot", if (PRootEngine.prootBinary(ctx) != null) "✔" else "✘")
            DataRow("ABI", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
        }
        SectionCard(L("m77_check")) {
            if (checking) LoadingRow(L("m77_checking"))
            MsiButton(L("check_updates"), {
                scope.launch {
                    checking = true; remoteError = null; remoteVersion = null
                    // MSI remote manifest (GitHub raw; configurable)
                    val r = Downloader.fetchText(
                        "https://raw.githubusercontent.com/finndev/msi-manifest/main/version.json", 12000
                    )
                    r.fold(
                        onSuccess = { text ->
                            runCatching { org.json.JSONObject(text).optString("version") }
                                .getOrNull()?.takeIf { it.isNotBlank() }?.let { remoteVersion = it }
                                ?: run { remoteError = Ls("m77_parse_error") }
                        },
                        onFailure = { remoteError = Ls("m77_offline") },
                    )
                    checking = false
                }
            }, Modifier.fillMaxWidth(), enabled = !checking)
            remoteVersion?.let { rv ->
                val newer = compareVersions(rv, appVersion) > 0
                Text(
                    if (newer) "⬆ $rv " + L("m77_available") else "✔ " + L("m77_up_to_date") + " ($rv)",
                    color = if (newer) MaterialTheme.colorScheme.secondary else Color(0xFF00E676),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            remoteError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
            InfoTextSmall(L("m77_hint"))
        }
        SectionCard(L("m77_module_versions")) {
            listOf(
                16 to L("cat_terminal"), 75 to "Alpine", 74 to L("m74_select"),
            ).forEach { (id, label) ->
                DataRow("$id — $label", "v4.0.0")
            }
        }
    }
}

private fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
    val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until 3) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}
