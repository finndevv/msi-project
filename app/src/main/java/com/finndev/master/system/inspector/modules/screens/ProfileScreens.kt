package com.finndev.master.system.inspector.modules.screens

import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.core.SystemInfo
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.SimpleBarChart
import com.finndev.master.system.inspector.ui.Sparkline
import com.finndev.master.system.inspector.ui.StatCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 65 — CPU-Z Killer Advanced Hardware Profiler
// ==================================================================================
@Composable
fun HardwareProfilerScreen() {
    ModuleScaffold(moduleId = 65) {
        ModuleIntroCard(65)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var soc by remember { mutableStateOf("" to "") }
        var gpu by remember { mutableStateOf("") }
        var rows by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var cores by remember { mutableStateOf<List<SystemInfo.CpuCore>>(emptyList()) }
        var caches by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }

        LaunchedEffect(Unit) {
            soc = SystemInfo.socInfo()
            gpu = SystemInfo.gpuInfo(ctx)
            cores = SystemInfo.cpuCoresNow()
            caches = SystemInfo.cacheInfo()
            val props = listOf(
                "ro.board.platform" to "Platform",
                "ro.hardware" to "Hardware",
                "ro.product.board" to "Board",
                "ro.chipname" to "Chip",
                "ro.boot.hardware.sku" to "SKU",
            )
            rows = props.mapNotNull { (k, label) ->
                val v = SystemInfo.getprop(k)
                if (v.isNotBlank()) label to v else null
            }
        }

        SectionCard("SoC") {
            DataRow(L("m65_soc"), "${soc.first} ${soc.second}")
            DataRow("CPU", SystemInfo.cpuModel())
            DataRow(L("m65_cores"), "${SystemInfo.cpuCount()}")
            DataRow("GPU", gpu)
            rows.forEach { (k, v) -> DataRow(k, v, mono = true) }
        }
        SectionCard(L("m65_freqs")) {
            cores.forEach { c ->
                DataRow(
                    "cpu${c.index}" + if (!c.online) " (off)" else "",
                    SystemInfo.fmtHz(c.curFreqKHz), mono = true,
                )
            }
            SimpleBarChart(cores.map { (it.curFreqKHz / 1000f).coerceAtLeast(1f) })
        }
        SectionCard(L("m65_caches")) {
            if (caches.isEmpty()) Text(L("m65_no_caches"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            caches.take(12).forEach { (cpu, level, size) -> DataRow("$cpu $level", size, mono = true) }
        }
        SectionCard(L("m65_device")) {
            DataRow("Android", android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")")
            DataRow("Security patch", SystemInfo.androidSecurityPatch())
            DataRow("ABI", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
            DataRow("RAM", SystemInfo.fmtBytes(SystemInfo.memInfoMap()["MemTotal"] ?: 0))
            SystemInfo.storageRows().forEach { s ->
                DataRow(s.path, SystemInfo.fmtBytes(s.freeBytes) + " " + L("dash_storage_free"), mono = true)
            }
        }
    }
}

// ==================================================================================
// MODULE 66 — Real-time RAM & Swap Memory Monitor
// ==================================================================================
@Composable
fun RamMonitorScreen() {
    ModuleScaffold(moduleId = 66) {
        ModuleIntroCard(66)
        var mem by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
        var history by remember { mutableStateOf(listOf<Float>()) }
        var zram by remember { mutableStateOf(0L to 0L) } // disksize, memUsed

        LaunchedEffect(Unit) {
            while (isActive) {
                mem = SystemInfo.memInfoMap()
                val total = (mem["MemTotal"] ?: 1).toFloat()
                val avail = (mem["MemAvailable"] ?: 0).toFloat()
                history = (history + ((total - avail) / total * 100f)).takeLast(90)
                val disk = SystemInfo.readSys("/sys/block/zram0/disksize")?.toLongOrNull() ?: 0L
                val used = SystemInfo.readSys("/sys/block/zram0/mem_used_total")?.toLongOrNull() ?: 0L
                zram = disk to used
                delay(1000)
            }
        }

        val total = mem["MemTotal"] ?: 0L
        val avail = mem["MemAvailable"] ?: 0L
        val used = total - avail
        SectionCard(L("m66_ram")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(L("m66_used"), SystemInfo.fmtBytes(used), "${(used * 100 / total.coerceAtLeast(1))}%", Modifier.weight(1f))
                StatCard(L("m66_available"), SystemInfo.fmtBytes(avail), "", Modifier.weight(1f))
                StatCard(L("m66_total"), SystemInfo.fmtBytes(total), "", Modifier.weight(1f))
            }
            if (history.size > 2) Sparkline(history)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { (used.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionCard(L("m66_details")) {
            DataRow("Cached", SystemInfo.fmtBytes(mem["Cached"] ?: 0))
            DataRow("Buffers", SystemInfo.fmtBytes(mem["Buffers"] ?: 0))
            DataRow("Active", SystemInfo.fmtBytes(mem["Active"] ?: 0))
            DataRow("Inactive", SystemInfo.fmtBytes(mem["Inactive"] ?: 0))
            DataRow("Shmem", SystemInfo.fmtBytes(mem["Shmem"] ?: 0))
            DataRow("SwapTotal", SystemInfo.fmtBytes(mem["SwapTotal"] ?: 0))
            DataRow("SwapFree", SystemInfo.fmtBytes(mem["SwapFree"] ?: 0))
            DataRow("zram", SystemInfo.fmtBytes(zram.second) + " / " + SystemInfo.fmtBytes(zram.first))
        }
    }
}

// ==================================================================================
// MODULE 71 — CPU Thermal Threshold & Throttling Inspector
// ==================================================================================
@Composable
fun ThermalScreen() {
    ModuleScaffold(moduleId = 71) {
        ModuleIntroCard(71)
        val ctx = LocalContext.current
        var zones by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
        var batteryTemp by remember { mutableStateOf(0f) }
        var history by remember { mutableStateOf(listOf<Float>()) }

        fun readZones(): List<Pair<String, Float>> {
            val list = mutableListOf<Pair<String, Float>>()
            for (i in 0 until 16) {
                val type = SystemInfo.readSys("/sys/class/thermal/thermal_zone$i/type") ?: continue
                val temp = SystemInfo.readSys("/sys/class/thermal/thermal_zone$i/temp")?.toFloatOrNull() ?: continue
                // normalize: some report millidegrees
                val c = if (temp > 1000) temp / 1000f else temp
                list.add(type to c)
            }
            return list.sortedByDescending { it.second }
        }

        LaunchedEffect(Unit) {
            while (isActive) {
                zones = readZones()
                val tempIntent = ctx.registerReceiver(
                    null,
                    android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                )
                val tempRaw = tempIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                if (tempRaw > 0) batteryTemp = tempRaw / 10f
                val max = zones.maxByOrNull { it.second }?.second
                if (max != null) history = (history + max).takeLast(60)
                delay(2000)
            }
        }

        SectionCard(L("m71_zones")) {
            if (zones.isEmpty()) LoadingRow(L("scanning"))
            zones.forEach { (type, temp) ->
                val hot = temp >= 60f
                val warm = temp in 45f..60f
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(type.take(28), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "%.1f °C".format(temp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            hot -> MaterialTheme.colorScheme.error
                            warm -> androidx.compose.ui.graphics.Color(0xFFFFB300)
                            else -> androidx.compose.ui.graphics.Color(0xFF00E676)
                        },
                    )
                }
            }
        }
        SectionCard(L("m71_battery")) {
            DataRow(L("m09_temp"), "%.1f °C".format(batteryTemp))
            if (history.size > 2) Sparkline(history)
            InfoTextSmall(L("m71_hint"))
        }
    }
}
