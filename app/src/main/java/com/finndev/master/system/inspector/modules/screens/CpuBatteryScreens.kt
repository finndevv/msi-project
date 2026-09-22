package com.finndev.master.system.inspector.modules.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.core.SystemInfo
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.RootBanner
import com.finndev.master.system.inspector.ui.SectionCard
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 6 — CPU Governor Performance Profiler
// ==================================================================================
@Composable
fun CpuGovernorScreen() {
    ModuleScaffold(moduleId = 6) {
        ModuleIntroCard(6)
        RootBanner()
        var cores by remember { mutableStateOf<List<SystemInfo.CpuCore>>(emptyList()) }
        var governors by remember { mutableStateOf<List<String>>(emptyList()) }
        var out by remember { mutableStateOf<List<String>>(emptyList()) }
        val scope = rememberCoroutineScope()

        fun load() {
            scope.launch {
                cores = SystemInfo.cpuCoresNow()
                val g = Shell.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governor")
                    ?: Shell.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governor")
                governors = g?.split(Regex("[ \\t]+"))?.filter { it.isNotBlank() } ?: emptyList()
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m06_current")) {
            cores.forEach { c ->
                DataRow(
                    "cpu${c.index}" + if (!c.online) " (off)" else "",
                    "${SystemInfo.fmtHz(c.curFreqKHz)} • ${c.governor}"
                )
            }
        }
        SectionCard(L("m06_available")) {
            if (governors.isEmpty()) InfoTextSmall(L("m06_no_governors"))
            governors.forEach { g ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(g, style = MaterialTheme.typography.bodySmall)
                    MsiButton(L("apply"), {
                        scope.launch {
                            val r = Shell.su(
                                "for c in /sys/devices/system/cpu/cpu*/cpufreq; do echo $g > \$c/scaling_governor 2>/dev/null; done; " +
                                    "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
                            )
                            out = r.output.lines(); load()
                        }
                    }, filled = false)
                }
            }
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 7 — CPU Core Hotplug & Frequency Limiter
// ==================================================================================
@Composable
fun CpuHotplugScreen() {
    ModuleScaffold(moduleId = 7) {
        ModuleIntroCard(7)
        RootBanner()
        var cores by remember { mutableStateOf<List<SystemInfo.CpuCore>>(emptyList()) }
        var minPct by remember { mutableStateOf(0f) }
        var maxPct by remember { mutableStateOf(100f) }
        var out by remember { mutableStateOf<List<String>>(emptyList()) }
        val scope = rememberCoroutineScope()

        fun load() { scope.launch { cores = SystemInfo.cpuCoresNow() } }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m07_cores")) {
            cores.forEach { c ->
                val idx = c.index
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "cpu$idx • ${SystemInfo.fmtHz(c.curFreqKHz)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    MsiButton(
                        if (c.online) L("m07_offline") else L("m07_online"),
                        {
                            scope.launch {
                                val v = if (c.online) 0 else 1
                                Shell.su("echo $v > /sys/devices/system/cpu/cpu$idx/online")
                                load()
                            }
                        }, filled = false, danger = c.online,
                    )
                }
            }
            InfoTextSmall(L("m07_cpu0_note"))
        }
        SectionCard(L("m07_freq_limit")) {
            Text(L("m07_min") + ": ${(minPct * 0.01).toInt()}%", style = MaterialTheme.typography.labelSmall)
            Slider(value = minPct, onValueChange = { minPct = it })
            Text(L("m07_max") + ": ${(maxPct * 0.01).toInt()}%", style = MaterialTheme.typography.labelSmall)
            Slider(value = maxPct, onValueChange = { maxPct = it })
            MsiButton(L("apply"), {
                scope.launch {
                    val policy0 = SystemInfo.readSys("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")?.toLongOrNull() ?: 0
                    val hwMax = policy0
                    val minK = (hwMax * minPct / 100).toLong()
                    val maxK = (hwMax * maxPct / 100).toLong()
                    val r = Shell.su(
                        "for p in /sys/devices/system/cpu/cpufreq/policy*; do " +
                            "echo $minK > \$p/scaling_min_freq 2>/dev/null; " +
                            "echo $maxK > \$p/scaling_max_freq 2>/dev/null; done; " +
                            "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
                    )
                    out = r.output.lines(); load()
                }
            }, icon = Icons.Filled.Save)
            InfoTextSmall(L("m07_limit_hint"))
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 8 — Low Memory Killer (LMK) Tweaker
// ==================================================================================
@Composable
fun LmkScreen() {
    ModuleScaffold(moduleId = 8) {
        ModuleIntroCard(8)
        RootBanner()
        var minfree by remember { mutableStateOf("") }
        var out by remember { mutableStateOf<List<String>>(emptyList()) }
        var newVals by remember { mutableStateOf("2048,3072,4096,6144,7680,10240") }
        val scope = rememberCoroutineScope()

        fun load() {
            scope.launch {
                minfree = Shell.readFile("/sys/module/lowmemorykiller/parameters/minfree")
                    ?: Shell.su("cat /sys/module/lowmemorykiller/parameters/minfree", 8000).stdout.trim()
                if (minfree.isBlank()) {
                    val zone = Shell.readFile("/proc/zoneinfo") ?: ""
                    val norm = Regex("pages free\\s+(\\d+)").find(zone)?.groupValues?.get(1)
                    minfree = if (norm != null) Ls("m08_lmk_missing") else Ls("m08_lmk_missing")
                }
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m08_current")) {
            DataRow("minfree", minfree.ifBlank { "-" }, mono = true)
            val mem = remember { SystemInfo.memInfoMap() }
            DataRow(L("dash_ram"), SystemInfo.fmtBytes(mem["MemAvailable"] ?: 0) + " / " + SystemInfo.fmtBytes(mem["MemTotal"] ?: 0))
            InfoTextSmall(L("m08_hint"))
        }
        SectionCard(L("m08_edit")) {
            LabeledField("minfree (kB)", newVals, { newVals = it }, mono = true)
            MsiButton(L("apply"), {
                scope.launch {
                    val r = Shell.su("echo '$newVals' > /sys/module/lowmemorykiller/parameters/minfree && cat /sys/module/lowmemorykiller/parameters/minfree")
                    out = r.output.lines(); load()
                }
            }, icon = Icons.Filled.Save)
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 9 — Battery Health, Cycle Count & Charging Threshold Manager
// ==================================================================================
@Composable
fun BatteryHealthScreen() {
    ModuleScaffold(moduleId = 9) {
        ModuleIntroCard(9)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()

        data class BatteryData(
            val level: Int, val health: String, val temp: Float, val voltage: Int,
            val tech: String, val status: String, val cycles: Int, val threshold: String,
        )

        fun read(): BatteryData {
            val bm = ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val intent = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            val healthInt = intent?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val tempRaw = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val volt = intent?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val tech = intent?.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY) ?: "-"
            val statusInt = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val cycles = if (android.os.Build.VERSION.SDK_INT >= 34)
                intent?.getIntExtra(android.os.BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1 else -1
            val healthStr = when (healthInt) {
                android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
                android.os.BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
                else -> "UNKNOWN ($healthInt)"
            }
            val statusStr = when (statusInt) {
                android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
                android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
                android.os.BatteryManager.BATTERY_STATUS_FULL -> "FULL"
                android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
                else -> "UNKNOWN"
            }
            return BatteryData(
                if (level >= 0) level * 100 / scale else -1, healthStr, tempRaw / 10f, volt, tech,
                statusStr, cycles, "",
            )
        }

        var data by remember { mutableStateOf<BatteryData?>(null) }
        var threshold by remember { mutableStateOf("") }
        var out by remember { mutableStateOf<List<String>>(emptyList()) }

        suspend fun loadThreshold() {
            val paths = listOf(
                "/sys/class/power_supply/battery/charge_control_end_threshold",
                "/sys/class/power_supply/battery/battery_charging_enabled",
            )
            var t = ""
            for (p in paths) {
                val v = Shell.readFile(p) ?: Shell.su("cat $p", 6000).stdout.trim()
                if (v.isNotBlank()) { t = v; break }
            }
            threshold = t
        }

        LaunchedEffect(Unit) {
            data = read()
            loadThreshold()
        }

        SectionCard(L("m09_now")) {
            data?.let { b ->
                DataRow(L("m09_level"), "${b.level}% ${b.status}")
                DataRow(L("m09_health"), b.health)
                DataRow(L("m09_temp"), "${b.temp} °C")
                DataRow(L("m09_voltage"), "${b.voltage} mV")
                DataRow(L("m09_tech"), b.tech)
                DataRow(L("m09_cycles"), if (b.cycles >= 0) "${b.cycles}" else L("m09_cycles_na"))
            } ?: LoadingRow(L("scanning"))
        }
        RootBanner()
        SectionCard(L("m09_threshold")) {
            DataRow(L("m09_threshold_current"), threshold.ifBlank { L("m09_threshold_na") }, mono = true)
            var newT by remember { mutableStateOf("80") }
            LabeledField(L("m09_threshold_new"), newT, { newT = it })
            MsiButton(L("apply"), {
                scope.launch {
                    val r = Shell.su(
                        "for p in /sys/class/power_supply/battery/charge_control_end_threshold " +
                            "/sys/class/power_supply/battery/charge_limit; do " +
                            "[ -f \$p ] && echo $newT > \$p && cat \$p; done"
                    )
                    out = r.output.lines(); loadThreshold()
                }
            })
            InfoTextSmall(L("m09_threshold_hint"))
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 10 — Screen Resolution & DPI Manipulator (wm size / density)
// ==================================================================================
@Composable
fun DisplayTweakScreen() {
    ModuleScaffold(moduleId = 10) {
        ModuleIntroCard(10)
        RootBanner()
        var sizeInfo by remember { mutableStateOf("") }
        var densityInfo by remember { mutableStateOf("") }
        var out by remember { mutableStateOf<List<String>>(emptyList()) }
        val scope = rememberCoroutineScope()

        fun load() {
            scope.launch {
                sizeInfo = Shell.su("wm size", 8000).stdout.trim()
                densityInfo = Shell.su("wm density", 8000).stdout.trim()
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m10_current")) {
            sizeInfo.lineSequence().forEach { if (it.isNotBlank()) ConsoleLine(it) }
            densityInfo.lineSequence().forEach { if (it.isNotBlank()) ConsoleLine(it) }
        }
        SectionCard(L("m10_size")) {
            var w by remember { mutableStateOf("1080") }
            var h by remember { mutableStateOf("2400") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField("W", w, { w = it }, Modifier.weight(1f))
                LabeledField("H", h, { h = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("apply"), {
                    scope.launch { val r = Shell.su("wm size ${w}x${h}"); out = r.output.lines(); load() }
                }, Modifier.weight(1f))
                MsiButton(L("reset"), {
                    scope.launch { val r = Shell.su("wm size reset"); out = r.output.lines(); load() }
                }, Modifier.weight(1f), danger = true)
            }
        }
        SectionCard(L("m10_density")) {
            var dpi by remember { mutableStateOf("420") }
            LabeledField("DPI", dpi, { dpi = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("apply"), {
                    scope.launch { val r = Shell.su("wm density $dpi"); out = r.output.lines(); load() }
                }, Modifier.weight(1f))
                MsiButton(L("reset"), {
                    scope.launch { val r = Shell.su("wm density reset"); out = r.output.lines(); load() }
                }, Modifier.weight(1f), danger = true)
            }
            InfoTextSmall(L("m10_warn"))
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}
