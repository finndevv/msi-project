package com.finndev.master.system.inspector.modules.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.core.content.ContextCompat
import com.finndev.master.system.inspector.core.Downloader
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.core.SystemInfo
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.EmptyHint
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.SimpleBarChart
import com.finndev.master.system.inspector.ui.StatCard
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 39 — Active Network Socket Inspector (Netstat endpoint tracker)
// ==================================================================================
@Composable
fun SocketInspectorScreen() {
    ModuleScaffold(moduleId = 39) {
        ModuleIntroCard(39)
        val ctx = LocalContext.current
        var sockets by remember { mutableStateOf<List<List<String>>>(emptyList()) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun load() {
            scope.launch {
                busy = true
                val rows = mutableListOf<List<String>>()
                for (file in listOf("/proc/net/tcp", "/proc/net/tcp6", "/proc/net/udp", "/proc/net/udp6")) {
                    val content = Shell.readFile(file) ?: continue
                    val proto = file.substringAfterLast('/').uppercase()
                    content.lineSequence().drop(1).forEach { line ->
                        val p = line.trim().split(Regex("\\s+"))
                        if (p.size < 4) return@forEach
                        val local = parseAddr(p[1])
                        val remote = parseAddr(p[2])
                        val state = if (proto.startsWith("TCP")) tcpState(p[3]) else "-"
                        val uid = p.getOrNull(7) ?: "?"
                        val appName = uidToApp(ctx, uid)
                        rows.add(listOf(proto, local, remote, state, appName))
                    }
                }
                sockets = rows
                busy = false
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m39_sockets"), "${sockets.size}") {
            if (busy) LoadingRow(L("scanning"))
            val tcpCount = sockets.count { it[0].startsWith("TCP") }
            val udpCount = sockets.count { it[0].startsWith("UDP") }
            val established = sockets.count { it[3] == "ESTABLISHED" }
            DataRow("TCP / UDP", "$tcpCount / $udpCount")
            DataRow("ESTABLISHED", "$established")
            MsiButton(L("refresh"), { load() }, Modifier.fillMaxWidth())
        }
        SectionCard(L("m39_table")) {
            if (sockets.isEmpty() && !busy) EmptyHint(L("m39_none"))
            LazyColumn(Modifier.height(320.dp)) {
                items(sockets.take(200)) { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${s[0].take(4)}", Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(s[1], Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Text(s[2], Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Text(s[3].take(4), Modifier.weight(0.4f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(s[4].take(12), Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            InfoTextSmall(L("m39_hint"))
        }
    }
}

private fun parseAddr(hex: String): String {
    return runCatching {
        val parts = hex.split(':')
        val addrHex = parts[0]
        val port = parts[1].toInt(16)
        val ip = if (addrHex.length == 8) {
            val v = addrHex.toLong(16)
            "${(v shr 0) and 0xFF}.${(v shr 8) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 24) and 0xFF}"
        } else {
            // IPv6 compact display: expand each 32-bit little-endian word, join groups
            val groups = (addrHex.length / 8).coerceAtLeast(1)
            (0 until groups).joinToString(":") { i ->
                val word = addrHex.substring(i * 8, minOf(i * 8 + 8, addrHex.length))
                // swap 16-bit halves (little endian), then group as hextets
                runCatching {
                    val w = word.toLong(16)
                    val a = ((w shr 16) and 0xFFFF).toInt()
                    val b = (w and 0xFFFF).toInt()
                    "%x:%x".format(a, b)
                }.getOrDefault(word.take(4))
            }
        }
        "$ip:$port"
    }.getOrDefault(hex)
}

private fun tcpState(code: String): String = when (code) {
    "01" -> "ESTABLISHED"; "02" -> "SYN_SENT"; "03" -> "SYN_RECV"; "04" -> "FIN_WAIT1"
    "05" -> "FIN_WAIT2"; "06" -> "TIME_WAIT"; "07" -> "CLOSE"; "08" -> "CLOSE_WAIT"
    "09" -> "LAST_ACK"; "0A" -> "LISTEN"; "0B" -> "CLOSING"; else -> code
}

private val uidCache = ConcurrentHashMap<String, String>()
private fun uidToApp(ctx: android.content.Context, uid: String): String {
    return uidCache.getOrPut(uid) {
        runCatching {
            val names = ctx.packageManager.getPackagesForUid(uid.toIntOrNull() ?: -1)
            names?.firstOrNull()?.take(14) ?: "uid:$uid"
        }.getOrDefault("uid:$uid")
    }
}

// ==================================================================================
// MODULE 40 — Internet Bandwidth Speed Test Module
// ==================================================================================
@Composable
fun SpeedTestScreen() {
    ModuleScaffold(moduleId = 40) {
        ModuleIntroCard(40)
        var downMbps by remember { mutableStateOf(0.0) }
        var upMbps by remember { mutableStateOf(0.0) }
        var latencyMs by remember { mutableStateOf(0.0) }
        var phase by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var history by remember { mutableStateOf(listOf<Float>()) }
        val scope = rememberCoroutineScope()

        fun run() {
            scope.launch {
                busy = true
                // latency probe
                phase = Ls("m40_latency")
                var latencies = mutableListOf<Long>()
                repeat(4) {
                    val start = System.currentTimeMillis()
                    Downloader.fetchText("https://speed.cloudflare.com/__down?bytes=10", 5000).getOrNull()
                    latencies.add(System.currentTimeMillis() - start)
                }
                latencyMs = latencies.sorted().take(3).average()
                // download 25 MB
                phase = Ls("m40_down")
                val dl = Downloader.measureDownload("https://speed.cloudflare.com/__down?bytes=25000000", 25_000_000)
                dl.getOrNull()?.let { downMbps = it * 8 / 1_000_000 }
                // upload 10 MB
                phase = Ls("m40_up")
                val ul = Downloader.measureUpload("https://speed.cloudflare.com/__up", 10_000_000)
                ul.getOrNull()?.let { upMbps = it * 8 / 1_000_000 }
                history = (history + downMbps.toFloat()).takeLast(12)
                phase = ""
                busy = false
            }
        }

        SectionCard(L("m40_results")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(L("m40_down"), if (downMbps > 0) "%.1f".format(downMbps) else "—", "Mbps", Modifier.weight(1f))
                StatCard(L("m40_up"), if (upMbps > 0) "%.1f".format(upMbps) else "—", "Mbps", Modifier.weight(1f))
                StatCard(L("m40_latency"), if (latencyMs > 0) "%.0f".format(latencyMs) else "—", "ms", Modifier.weight(1f))
            }
            if (history.size > 1) SimpleBarChart(history)
            if (busy) LoadingRow(phase.ifBlank { L("m40_running") })
        }
        MsiButton(if (busy) L("m40_running") else L("m40_start"), { if (!busy) run() }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow, enabled = !busy)
        InfoTextSmall(L("m40_hint"))
    }
}

// ==================================================================================
// MODULE 42 — Wi-Fi Signal Strength & Channel Analyzer
// ==================================================================================
@Composable
fun WifiAnalyzerScreen() {
    ModuleScaffold(moduleId = 42) {
        ModuleIntroCard(42)
        val ctx = LocalContext.current
        var haveLocation by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            )
        }
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            haveLocation = it
        }
        var info by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var scanResults by remember { mutableStateOf<List<Triple<String, Int, Int>>>(emptyList()) } // ssid, rssi, channel
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun load() {
            scope.launch {
                busy = true
                val wm = ctx.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                if (wm != null) {
                    val conn = wm.connectionInfo
                    val rssi = conn.rssi
                    val freq = conn.frequency
                    val channel = if (freq in 2400..2500) (freq - 2407) / 5 else if (freq in 5000..5900) (freq - 5000) / 5 else 0
                    info = listOf(
                        Ls("m42_rssi") to "$rssi dBm",
                        Ls("m42_level") to "${android.net.wifi.WifiManager.calculateSignalLevel(rssi, 5)}/4",
                        Ls("m42_freq") to "$freq MHz",
                        Ls("m42_channel") to "$channel",
                        "BSSID" to (conn.bssid ?: "-"),
                        "IP" to (android.text.format.Formatter.formatIpAddress(conn.ipAddress) ?: "-"),
                    )
                    if (haveLocation) {
                        @Suppress("DEPRECATION")
                        val results = wm.scanResults
                        scanResults = results.map { r ->
                            val ch = if (r.frequency in 2400..2500) (r.frequency - 2407) / 5 else (r.frequency - 5000) / 5
                            Triple(r.SSID.ifBlank { "<hidden>" }, r.level, ch)
                        }.sortedByDescending { it.second }.take(30)
                    }
                }
                busy = false
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m42_current")) {
            if (busy) LoadingRow(L("scanning"))
            info.forEach { (k, v) -> DataRow(k, v, mono = true) }
            MsiButton(L("refresh"), { load() }, Modifier.fillMaxWidth())
        }
        if (!haveLocation) {
            SectionCard(L("m42_location")) {
                InfoTextSmall(L("m42_location_hint"))
                MsiButton(L("m42_grant"), { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) })
            }
        }
        if (scanResults.isNotEmpty()) {
            SectionCard(L("m42_networks"), "${scanResults.size}") {
                // channel congestion: count networks per channel
                val congestion = scanResults.groupBy { it.third }.map { (ch, list) -> ch to list.size }.sortedBy { it.first }
                SimpleBarChart(congestion.map { it.second.toFloat() })
                DataRow(L("m42_congestion"), congestion.joinToString("  ") { "ch${it.first}:${it.second}" })
                LazyColumn(Modifier.height(240.dp)) {
                    items(scanResults) { (ssid, rssi, ch) ->
                        DataRow("${ssid.take(22)}", "$rssi dBm • ch$ch", mono = true)
                    }
                }
            }
        }
    }
}
