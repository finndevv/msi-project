package com.finndev.master.system.inspector.modules.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.core.ShellStream
import com.finndev.master.system.inspector.core.SystemInfo
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.EmptyHint
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.RootBanner
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.SimpleBarChart
import com.finndev.master.system.inspector.ui.Sparkline
import com.finndev.master.system.inspector.ui.StatCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.security.MessageDigest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 11 — Bloatware System App Freezer / Uninstaller (pm disable/uninstall)
// ==================================================================================
@Composable
fun BloatwareScreen() {
    ModuleScaffold(moduleId = 11) {
        ModuleIntroCard(11)
        RootBanner()
        val ctx = LocalContext.current
        var apps by remember { mutableStateOf<List<Triple<String, String, Boolean>>>(emptyList()) } // pkg, label, isSystem
        var filter by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var out by remember { mutableStateOf<List<String>>(emptyList()) }
        val scope = rememberCoroutineScope()

        fun load() {
            scope.launch {
                busy = true
                val pm = ctx.packageManager
                val pkgs = pm.getInstalledPackages(android.content.pm.PackageManager.GET_META_DATA)
                apps = pkgs.mapNotNull { p ->
                    val ai = p.applicationInfo ?: return@mapNotNull null
                    val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(p.packageName)
                    Triple(p.packageName, label, (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                }.sortedBy { it.second.lowercase() }
                busy = false
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m11_apps"), "${apps.size}") {
            LabeledField(L("filter"), filter, { filter = it }, placeholder = "com.android.")
            if (busy) { LoadingRow(L("scanning")); return@SectionCard }
            val shown = apps.filter { (pkg, label, sys) ->
                (filter.isBlank() || pkg.contains(filter, true) || label.contains(filter, true))
            }.take(120)
            LazyColumn(Modifier.height(320.dp)) {
                items(shown) { (pkg, label, sys) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        MsiButton(L("m11_freeze"), {
                            scope.launch {
                                val r = Shell.su("pm disable-user --user 0 $pkg")
                                out = listOf("freeze $pkg -> ${if (r.ok) "OK" else r.output.take(120)}")
                            }
                        }, filled = false)
                        MsiButton(L("m11_uninstall"), {
                            scope.launch {
                                val r = Shell.su("pm uninstall --user 0 $pkg")
                                out = listOf("uninstall $pkg -> ${if (r.ok) "OK" else r.output.take(120)}")
                            }
                        }, filled = false, danger = true)
                    }
                }
            }
        }
        SectionCard(L("m11_restore")) {
            var restorePkg by remember { mutableStateOf("") }
            LabeledField(L("m11_restore_pkg"), restorePkg, { restorePkg = it }, placeholder = "com.android.app")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("m11_enable"), {
                    scope.launch { val r = Shell.su("pm enable $restorePkg"); out = r.output.lines() }
                }, Modifier.weight(1f))
                MsiButton(L("m11_unhide"), {
                    scope.launch { val r = Shell.su("pm unhide $restorePkg"); out = r.output.lines() }
                }, Modifier.weight(1f))
            }
            InfoTextSmall(L("m11_hint"))
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 12 — Live Hardware Sensors Streamer (Gyro, Accel, Magnetometer)
// ==================================================================================
@Composable
fun SensorStreamerScreen() {
    ModuleScaffold(moduleId = 12) {
        ModuleIntroCard(12)
        val ctx = LocalContext.current
        val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
        val sensors = remember { sm.getSensorList(Sensor.TYPE_ALL) }

        var selected by remember { mutableStateOf<Sensor?>(null) }
        var values by remember { mutableStateOf(FloatArray(3)) }
        var history by remember { mutableStateOf(listOf<Float>()) }

        DisposableEffect(selected) {
            val s = selected
            var listener: SensorEventListener? = null
            if (s != null) {
                listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        values = event.values.copyOf()
                        history = (history + (event.values.getOrNull(0) ?: 0f)).takeLast(120)
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose { listener?.let { sm.unregisterListener(it) } }
        }

        SectionCard(L("m12_count"), "${sensors.size}") {
            val interesting = sensors.filter {
                it.type in listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
                    Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY, Sensor.TYPE_PRESSURE, Sensor.TYPE_AMBIENT_TEMPERATURE)
            }
            interesting.forEach { s ->
                MsiButton(s.name.take(42), {
                    selected = if (selected == s) null else s
                }, Modifier.fillMaxWidth(), filled = (selected == s))
            }
        }
        selected?.let { s ->
            SectionCard(s.name, s.vendor) {
                val labels = when (s.type) {
                    Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE ->
                        listOf("X", "Y", "Z")
                    else -> listOf("V0", "V1", "V2")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEachIndexed { i, lbl ->
                        StatCard(lbl, "%.3f".format(values.getOrNull(i) ?: 0f), modifier = Modifier.weight(1f))
                    }
                }
                if (history.size > 2) Sparkline(history)
                DataRow(L("m12_power"), "%.3f mA".format(s.power))
                DataRow(L("m12_range"), "${s.maximumRange} ${s.stringType.substringAfterLast('.').take(12)}")
                DataRow(L("m12_resolution"), "%.4f".format(s.resolution))
            }
        }
    }
}

// ==================================================================================
// MODULE 13 — Kernel Log (dmesg) Live Buffer Reader
// ==================================================================================
@Composable
fun DmesgScreen() {
    ModuleScaffold(moduleId = 13) {
        ModuleIntroCard(13)
        RootBanner()
        var lines by remember { mutableStateOf(listOf<String>()) }
        var filter by remember { mutableStateOf("") }
        var live by remember { mutableStateOf(false) }
        var stream by remember { mutableStateOf<ShellStream?>(null) }
        val scope = rememberCoroutineScope()

        fun startLive() {
            stream?.kill()
            lines = listOf("[MSI] dmesg -w following ... (root)")
            stream = Shell.stream("dmesg -w", root = true) { line ->
                lines = (lines + line).takeLast(1500)
            }
            live = true
        }

        DisposableEffect(Unit) { onDispose { stream?.kill() } }

        SectionCard(L("m13_buffer")) {
            MsiButton(L("m13_read"), {
                scope.launch {
                    lines = listOf("[MSI] su -c dmesg ...")
                    val r = Shell.su("dmesg", 30000)
                    lines = r.stdout.lines().takeLast(1500)
                }
            }, Modifier.fillMaxWidth())
        }
        SectionCard(L("m13_live")) {
            if (live) MsiButton(L("stop"), { stream?.kill(); live = false; lines = lines + "[MSI] stopped" }, Modifier.fillMaxWidth(), Icons.Filled.Stop, danger = true)
            else MsiButton(L("m13_follow"), { startLive() }, Modifier.fillMaxWidth())
            InfoTextSmall(L("root_required"))
        }
        LabeledField(L("filter"), filter, { filter = it })
        val shown = if (filter.isBlank()) lines else lines.filter { it.contains(filter, true) }
        if (shown.isEmpty()) EmptyHint(L("m13_empty"))
        else OutputConsole(shown, maxLinesShown = 500)
    }
}

// ==================================================================================
// MODULE 14 — Boot Stage & Startup Timing Analyzer
// ==================================================================================
@Composable
fun BootTimingScreen() {
    ModuleScaffold(moduleId = 14) {
        ModuleIntroCard(14)
        var stats by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var bootLog by remember { mutableStateOf(listOf<String>()) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            scope.launch {
                val (real, up, deep) = SystemInfo.uptimeInfo()
                stats = listOf(
                    Ls("m14_uptime") to "${real / 1000} s",
                    Ls("m14_awake") to "${up / 1000} s",
                    Ls("m14_deep") to "${deep / 1000} s (${if (real > 0) deep * 100 / real else 0}%)",
                    Ls("m14_boot_id") to (Shell.readFile("/proc/sys/kernel/random/boot_id")?.take(8) ?: "-"),
                )
                val lc = Shell.su(
                    "logcat -d -b events -v brief 2>/dev/null | grep -iE 'boot|sf_stop|am_proc_start' | tail -60",
                    20000
                )
                bootLog = lc.stdout.lines().filter { it.isNotBlank() }.takeLast(60)
                if (bootLog.isEmpty()) {
                    bootLog = listOf(Ls("m14_no_log"))
                }
            }
        }

        SectionCard(L("m14_stats")) {
            stats.forEach { (k, v) -> DataRow(k, v) }
        }
        SectionCard(L("m14_stages")) {
            if (bootLog.isEmpty()) LoadingRow(L("scanning"))
            else OutputConsole(bootLog, maxLinesShown = 60)
        }
    }
}

// ==================================================================================
// MODULE 15 — CPU & RAM Hardware Benchmark Stress Tester
// ==================================================================================
@Composable
fun BenchmarkScreen() {
    ModuleScaffold(moduleId = 15) {
        ModuleIntroCard(15)
        var cpuSingle by remember { mutableStateOf(0.0) }
        var cpuMulti by remember { mutableStateOf(0.0) }
        var memScore by remember { mutableStateOf(0.0) }
        var running by remember { mutableStateOf(false) }
        var history by remember { mutableStateOf(listOf<Float>()) }
        val cores = remember { Runtime.getRuntime().availableProcessors() }
        val scope = rememberCoroutineScope()

        fun cpuWorkload(millis: Long): Int {
            val md = MessageDigest.getInstance("SHA-256")
            val data = ByteArray(64) { (it * 31).toByte() }
            var ops = 0
            val end = System.currentTimeMillis() + millis
            while (System.currentTimeMillis() < end) {
                md.update(data)
                md.digest()
                ops++
            }
            return ops
        }

        fun run() {
            scope.launch {
                running = true
                cpuSingle = 0.0; cpuMulti = 0.0; memScore = 0.0
                // single core
                val sOps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { cpuWorkload(2000) }
                cpuSingle = sOps / 2.0 // ops per second
                delay(100)
                // multi core
                val mOps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    kotlinx.coroutines.coroutineScope {
                        val jobs = (0 until cores).map {
                            async { cpuWorkload(2000) }
                        }
                        jobs.sumOf { it.await() }
                    }
                }
                cpuMulti = mOps / 2.0 / cores
                delay(100)
                // memory bandwidth: write + read 96 MB in 512 KB chunks
                val mem = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val chunks = 192
                    val chunk = ByteArray(512 * 1024)
                    val start = System.currentTimeMillis()
                    repeat(chunks) { i -> chunk[i] = (i % 251).toByte() }
                    val writeMs = System.currentTimeMillis() - start
                    val readStart = System.currentTimeMillis()
                    var acc = 0L
                    repeat(chunks) { i -> acc += chunk[(i * 977) % chunk.size] }
                    val readMs = System.currentTimeMillis() - readStart
                    (chunks * 512.0 * 1024.0 / 1024 / 1024) / ((writeMs + readMs) / 1000.0) // MB/s
                }
                memScore = mem
                history = (history + listOf(cpuSingle.toFloat(), cpuMulti.toFloat())).takeLast(20)
                running = false
            }
        }

        SectionCard(L("m15_scores")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(L("m15_single"), if (cpuSingle > 0) "%.0f".format(cpuSingle) else "—", "SHA-256 ops/s", Modifier.weight(1f))
                StatCard(L("m15_multi"), if (cpuMulti > 0) "%.0f".format(cpuMulti) else "—", "×$cores threads", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(L("m15_mem"), if (memScore > 0) "%.0f".format(memScore) else "—", "MB/s R/W", Modifier.weight(1f))
                StatCard(L("m15_cores"), "$cores", "threads", Modifier.weight(1f))
            }
            if (history.size > 1) SimpleBarChart(history)
        }
        if (running) LoadingRow(L("m15_running"))
        MsiButton(if (running) L("m15_running") else L("run"), { if (!running) run() }, Modifier.fillMaxWidth(), enabled = !running)
        InfoTextSmall(L("m15_hint"))
    }
}
