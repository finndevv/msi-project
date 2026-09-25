package com.finndev.master.system.inspector.modules.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.core.ShellStream
import com.finndev.master.system.inspector.core.SystemInfo
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import com.finndev.master.system.inspector.ui.DataRow

// ==================================================================================
// MODULE 34 — LAN IP & MAC Device Scanner
// ==================================================================================
@Composable
fun LanScannerScreen() {
    ModuleScaffold(moduleId = 34) {
        ModuleIntroCard(34)
        var subnet by remember { mutableStateOf("192.168.1.") }
        var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var scanning by remember { mutableStateOf(false) }
        var progress by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress }
                    ?.hostAddress?.let { subnet = it.substringBeforeLast('.') + "." }
            }
        }

        SectionCard(L("m34_scan")) {
            LabeledField(L("m34_subnet"), subnet, { subnet = it }, mono = true, placeholder = "192.168.1.")
            if (scanning) LoadingRow("$progress / 254")
            MsiButton(if (scanning) L("m34_scanning") else L("scan"), {
                if (scanning) return@MsiButton
                scope.launch {
                    scanning = true; results = emptyList(); progress = 0
                    val found = mutableListOf<Pair<String, String>>()
                    withContext(Dispatchers.IO) {
                        kotlinx.coroutines.coroutineScope {
                        (1..254).map { host ->
                            async {
                                val addr = InetAddress.getByName(subnet + host)
                                val ok = runCatching { addr.isReachable(120) }.getOrDefault(false)
                                synchronized(found) { if (ok) found.add(subnet + host to "?") }
                                synchronized(progress) { progress++ }
                            }
                        }.awaitAll()
                        }
                        // resolve MAC from /proc/net/arp
                        val arp = runCatching { java.io.File("/proc/net/arp").readLines() }.getOrDefault(emptyList())
                        val macMap = arp.drop(1).mapNotNull { line ->
                            val p = line.split(Regex("\\s+"))
                            if (p.size >= 4) p[0] to p[3] else null
                        }.toMap()
                        results = found.map { (ip, _) ->
                            ip to (macMap[ip] ?: runCatching {
                                val r = Shell.su("ip neigh show $ip", 5000)
                                Regex("([0-9a-fA-F:]{17})").find(r.stdout)?.value ?: "-"
                            }.getOrDefault("-"))
                        }.sortedBy { it.first.substringAfterLast('.').toIntOrNull() ?: 0 }
                    }
                    scanning = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow, enabled = !scanning)
        }
        SectionCard(L("m34_found"), "${results.size}") {
            if (results.isEmpty() && !scanning) EmptyHint(L("m34_none"))
            results.forEach { (ip, mac) ->
                com.finndev.master.system.inspector.ui.DataRow(ip, mac, mono = true)
            }
            InfoTextSmall(L("m34_hint"))
        }
    }
}

// ==================================================================================
// MODULE 35 — Port Scanner (TCP/UDP Port Checker)
// ==================================================================================
@Composable
fun PortScannerScreen() {
    ModuleScaffold(moduleId = 35) {
        ModuleIntroCard(35)
        var host by remember { mutableStateOf("127.0.0.1") }
        var ports by remember { mutableStateOf("22,80,443,8080,5555") }
        var tcpResults by remember { mutableStateOf<List<Pair<Int, Boolean>>>(emptyList()) }
        var udpResults by remember { mutableStateOf<List<Pair<Int, Boolean>>>(emptyList()) }
        var scanning by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun scan() {
            scope.launch {
                scanning = true
                val portList = ports.split(Regex("[,\\s]+")).mapNotNull { it.toIntOrNull() }.filter { it in 1..65535 }.distinct()
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.coroutineScope {
                        tcpResults = portList.map { p ->
                            async {
                                val open = runCatching {
                                    java.net.Socket().use { s ->
                                        s.connect(java.net.InetSocketAddress(host, p), 400)
                                        true
                                    }
                                }.getOrDefault(false)
                                p to open
                            }
                        }.awaitAll().sortedBy { it.first }
                    }
                    kotlinx.coroutines.coroutineScope {
                        udpResults = portList.map { p ->
                            async {
                                val open = runCatching {
                                    val ds = java.net.DatagramSocket()
                                    ds.soTimeout = 600
                                    ds.connect(java.net.InetSocketAddress(host, p))
                                    ds.send(java.net.DatagramPacket(ByteArray(1), 1))
                                    val buf = ByteArray(64)
                                    val pkt = java.net.DatagramPacket(buf, buf.size)
                                    ds.receive(pkt) // open+responding if we get data
                                    true
                                }
                                p to open.getOrDefault(false) // false on timeout/ICMP unreachable
                            }
                        }.awaitAll().sortedBy { it.first }
                    }
                }
                scanning = false
            }
        }

        SectionCard(L("m35_target")) {
            LabeledField(L("m35_host"), host, { host = it }, mono = true)
            LabeledField(L("m35_ports"), ports, { ports = it }, mono = true, placeholder = "22,80,443,1-1000")
            if (scanning) LoadingRow(L("scanning"))
            MsiButton(L("scan"), { if (!scanning) scan() }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow, enabled = !scanning)
        }
        SectionCard("TCP") {
            if (tcpResults.isEmpty()) EmptyHint(L("m35_none"))
            tcpResults.forEach { (p, open) ->
                com.finndev.master.system.inspector.ui.DataRow(
                    ":$p", if (open) L("m35_open") else L("m35_closed"),
                    mono = false
                )
            }
        }
        SectionCard("UDP") {
            if (udpResults.isEmpty()) EmptyHint(L("m35_none"))
            udpResults.forEach { (p, open) ->
                com.finndev.master.system.inspector.ui.DataRow(":$p", if (open) L("m35_open") else L("m35_filtered"), mono = false)
            }
            InfoTextSmall(L("m35_udp_hint"))
        }
    }
}

// ==================================================================================
// MODULE 36 — Advanced Ping & Latency (ms) Monitor
// ==================================================================================
@Composable
fun PingScreen() {
    ModuleScaffold(moduleId = 36) {
        ModuleIntroCard(36)
        var host by remember { mutableStateOf("1.1.1.1") }
        var count by remember { mutableStateOf("10") }
        var running by remember { mutableStateOf(false) }
        var lines by remember { mutableStateOf(listOf<String>()) }
        var times by remember { mutableStateOf(listOf<Float>()) }
        var stats by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) } // min avg max
        var stream by remember { mutableStateOf<ShellStream?>(null) }
        val scope = rememberCoroutineScope()

        fun start() {
            stream?.kill()
            running = true
            lines = listOf("[MSI] ping -c $count $host")
            times = emptyList(); stats = null
            stream = Shell.stream("ping -c ${count.toIntOrNull() ?: 10} -i 0.3 $host", root = false) { line ->
                lines = (lines + line).takeLast(60)
                Regex("time=([0-9.]+) ms").find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let { ms ->
                    times = (times + ms).takeLast(60)
                    val t = times
                    if (t.isNotEmpty()) stats = Triple(t.minOrNull() ?: 0f, t.sum() / t.size, t.maxOrNull() ?: 0f)
                }
                if (line.contains("packet loss") || line.startsWith("---")) {
                    running = false
                }
            }
        }

        SectionCard(L("m36_target")) {
            LabeledField(L("m35_host"), host, { host = it }, mono = true)
            LabeledField(L("m36_count"), count, { count = it })
            if (running) MsiButton(L("stop"), { stream?.kill(); running = false }, Modifier.fillMaxWidth(), Icons.Filled.Stop, danger = true)
            else MsiButton(L("m36_start"), { start() }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow)
        }
        stats?.let { (min, avg, max) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("min", "%.1f ms".format(min), modifier = Modifier.weight(1f))
                StatCard("avg", "%.1f ms".format(avg), modifier = Modifier.weight(1f))
                StatCard("max", "%.1f ms".format(max), modifier = Modifier.weight(1f))
            }
            if (times.size > 1) SimpleBarChart(times.map { it })
        }
        if (lines.size > 1) SectionCard(L("output")) { OutputConsole(lines, maxLinesShown = 40) }
    }
}
