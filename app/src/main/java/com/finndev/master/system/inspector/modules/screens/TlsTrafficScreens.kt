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
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 43 — SSL Certificate & TLS Inspection Tool
// ==================================================================================
@Composable
fun TlsInspectorScreen() {
    ModuleScaffold(moduleId = 43) {
        ModuleIntroCard(43)
        var host by remember { mutableStateOf("www.google.com") }
        var busy by remember { mutableStateOf(false) }
        var rows by remember { mutableStateOf(listOf<Pair<String, String>>()) }
        var chain by remember { mutableStateOf(listOf<String>()) }
        val scope = rememberCoroutineScope()

        // trust-everything manager: we WANT to inspect any certificate
        val trustAll = remember {
            arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
        }

        fun inspect() {
            scope.launch {
                busy = true
                rows = emptyList(); chain = emptyList()
                withContext(Dispatchers.IO) {
                    runCatching {
                        val factory: SSLSocketFactory = SSLContext.getInstance("TLS").apply {
                            init(null, trustAll, java.security.SecureRandom())
                        }.socketFactory
                        val socket = factory.createSocket(InetAddress.getByName(host), 443) as SSLSocket
                        socket.soTimeout = 12000
                        socket.startHandshake()
                        val session = socket.session
                        val certs = session.peerCertificates.filterIsInstance<X509Certificate>()
                        val c = certs.firstOrNull()
                        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        rows = buildList {
                            add(Ls("m43_tls_version") to session.protocol)
                            add(Ls("m43_cipher") to session.cipherSuite)
                            if (c != null) {
                                add("Subject" to c.subjectX500Principal.name.take(90))
                                add("Issuer" to c.issuerX500Principal.name.take(90))
                                add("Serial" to c.serialNumber.toString(16))
                                add(Ls("m43_valid_from") to df.format(c.notBefore))
                                add(Ls("m43_valid_to") to df.format(c.notAfter))
                                add("SigAlg" to c.sigAlgName)
                                add("Public key" to "${c.publicKey.algorithm} ${c.publicKey.format} ${c.publicKey.toString().substringAfterLast('.').take(12)}")
                                val expired = Date().after(c.notAfter)
                                add(Ls("m43_status") to if (expired) Ls("m43_expired") else Ls("m43_valid"))
                            }
                        }
                        chain = certs.mapIndexed { i, cert ->
                            "[$i] ${cert.subjectX500Principal.name.take(70)} (${cert.sigAlgName})"
                        }
                        socket.close()
                    }.onFailure {
                        rows = listOf(Ls("error") to (it.message ?: "?").take(120))
                    }
                }
                busy = false
            }
        }
        LaunchedEffect(Unit) { inspect() }

        SectionCard(L("m43_target")) {
            LabeledField("host:443", host, { host = it }, mono = true)
            if (busy) LoadingRow(L("scanning"))
            MsiButton(L("m43_inspect"), { if (!busy) inspect() }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow)
        }
        SectionCard(L("m43_cert")) {
            rows.forEach { (k, v) -> DataRow(k, v, mono = true) }
        }
        if (chain.isNotEmpty()) SectionCard(L("m43_chain")) {
            chain.forEach { ConsoleLine(it) }
        }
    }
}

// ==================================================================================
// MODULE 44 — Basic Packet Sniffer & Traffic Logger
// ==================================================================================
@Composable
fun TrafficLoggerScreen() {
    ModuleScaffold(moduleId = 44) {
        ModuleIntroCard(44)
        RootBannerOpt()
        var running by remember { mutableStateOf(false) }
        var log by remember { mutableStateOf(listOf<String>()) }
        var tcpCount by remember { mutableStateOf(0) }
        var rxBytes by remember { mutableStateOf(0L) }
        var txBytes by remember { mutableStateOf(0L) }
        val scope = rememberCoroutineScope()
        val startRx = remember { android.net.TrafficStats.getMobileRxBytes() + android.net.TrafficStats.getTotalRxBytes() }
        val startTx = remember { android.net.TrafficStats.getMobileTxBytes() + android.net.TrafficStats.getTotalTxBytes() }

        suspend fun snapshot(): Map<String, Set<String>> {
            val map = mutableMapOf<String, MutableSet<String>>()
            for (file in listOf("/proc/net/tcp", "/proc/net/tcp6")) {
                val content = runCatching { java.io.File(file).readText() }.getOrNull()
                    ?: Shell.su("cat $file", 5000).stdout
                content.lineSequence().drop(1).forEach { line ->
                    val p = line.trim().split(Regex("\\s+"))
                    if (p.size < 4) return@forEach
                    val remote = p[2]
                    val uid = p.getOrNull(7) ?: return@forEach
                    map.getOrPut(uid) { mutableSetOf() }.add(remote)
                }
            }
            return map
        }

        fun start() {
            scope.launch {
                running = true
                var last = snapshot()
                while (running && isActive) {
                    delay(2000)
                    val now = snapshot()
                    var newConns = 0
                    now.forEach { (uid, remotes) ->
                        val added = remotes - (last[uid] ?: emptySet())
                        if (added.isNotEmpty()) {
                            added.take(3).forEach { r ->
                                newConns++
                                val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                                log = (log + listOf("$ts uid=$uid -> ${r.take(26)} (open)")).takeLast(300)
                            }
                        }
                    }
                    last.forEach { (uid, remotes) ->
                        val gone = remotes - (now[uid] ?: emptySet())
                        if (gone.isNotEmpty()) {
                            gone.take(2).forEach { r ->
                                val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                                log = (log + listOf("$ts uid=$uid -> ${r.take(26)} (closed)")).takeLast(300)
                            }
                        }
                    }
                    tcpCount = now.values.sumOf { it.size }
                    rxBytes = (android.net.TrafficStats.getTotalRxBytes()) - startRx
                    txBytes = (android.net.TrafficStats.getTotalTxBytes()) - startTx
                    last = now
                }
            }
        }

        SectionCard(L("m44_status")) {
            DataRow(L("m44_connections"), "$tcpCount")
            DataRow("ΔRX", com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(rxBytes.coerceAtLeast(0)))
            DataRow("ΔTX", com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(txBytes.coerceAtLeast(0)))
            if (running) MsiButton(L("stop"), { running = false }, Modifier.fillMaxWidth(), Icons.Filled.Stop, danger = true)
            else MsiButton(L("m44_start"), { log = emptyList(); start() }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow)
            InfoTextSmall(L("m44_hint"))
        }
        if (log.isNotEmpty()) SectionCard(L("m44_log"), "${log.size}") {
            OutputConsole(log, maxLinesShown = 120)
        }
    }
}

@Composable
private fun RootBannerOpt() { RootBannerLocal() }

@Composable
private fun RootBannerLocal() {
    com.finndev.master.system.inspector.ui.RootBanner()
}

// ==================================================================================
// MODULE 45 — Hotspot & Tethering Client Inspector
// ==================================================================================
@Composable
fun HotspotScreen() {
    ModuleScaffold(moduleId = 45) {
        ModuleIntroCard(45)
        RootBannerOpt()
        var clients by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var apInfo by remember { mutableStateOf(listOf<Pair<String, String>>()) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun load() {
            scope.launch {
                busy = true
                // tethering state
                val tether = Shell.su(
                    "dumpsys tethering 2>/dev/null | grep -iE 'active|aplist|clients' | head -12; " +
                        "ip addr show | grep -A1 'wlan' | head -12", 15000
                )
                apInfo = tether.stdout.lines().filter { it.isNotBlank() }.take(12).map { "tether" to it.trim().take(90) }
                // ARP table shows hotspot clients
                val arp = Shell.readFile("/proc/net/arp") ?: Shell.su("cat /proc/net/arp", 8000).stdout
                clients = arp.lineSequence().drop(1).mapNotNull { line ->
                    val p = line.trim().split(Regex("\\s+"))
                    if (p.size >= 4 && p[0].contains('.')) p[0] to p[3] else null
                }.toList()
                busy = false
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m45_ap")) {
            if (busy) LoadingRow(L("scanning"))
            if (apInfo.isEmpty()) InfoTextSmall(L("m45_ap_off"))
            apInfo.forEach { (_, v) -> ConsoleLine(v) }
        }
        SectionCard(L("m45_clients"), "${clients.size}") {
            if (clients.isEmpty()) InfoTextSmall(L("m45_no_clients"))
            clients.forEach { (ip, mac) -> DataRow(ip, mac, mono = true) }
            MsiButton(L("refresh"), { load() }, Modifier.fillMaxWidth())
            InfoTextSmall(L("m45_hint"))
        }
    }
}
