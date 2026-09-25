package com.finndev.master.system.inspector.modules.screens

import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
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
import com.finndev.master.system.inspector.core.Crypto
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.RootBanner
import com.finndev.master.system.inspector.ui.SectionCard
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 37 — Custom DNS Enforcer over Root (Cloudflare, Google, AdGuard)
// ==================================================================================
@Composable
fun DnsEnforcerScreen() {
    ModuleScaffold(moduleId = 37) {
        ModuleIntroCard(37)
        RootBanner()
        val scope = rememberCoroutineScope()
        var current by remember { mutableStateOf("") }
        var out by remember { mutableStateOf(listOf<String>()) }

        val presets = listOf(
            L("m37_auto") to "",
            L("m37_cloudflare") to "1dot1dot1dot1.cloudflare-dns.com",
            L("m37_google") to "dns.google",
            L("m37_adguard") to "dns.adguard.com",
            L("m37_quad9") to "dns.quad9.net",
            L("m37_off") to "off",
        )

        fun refresh() {
            scope.launch {
                val mode = Shell.su("settings get global private_dns_mode", 8000).stdout.trim()
                val spec = Shell.su("settings get global private_dns_specifier", 8000).stdout.trim()
                current = "mode=$mode  spec=${spec.ifBlank { "-" }}"
            }
        }
        LaunchedEffect(Unit) { refresh() }

        SectionCard(L("m37_current")) {
            DataRow("Private DNS", current, mono = true)
            MsiButton(L("refresh"), { refresh() }, Modifier.fillMaxWidth(), Icons.Filled.Refresh)
        }
        SectionCard(L("m37_presets")) {
            presets.forEach { (label, host) ->
                MsiButton(label, {
                    scope.launch {
                        val r = when (host) {
                            "" -> Shell.su("settings put global private_dns_mode opportunistic")
                            "off" -> Shell.su("settings put global private_dns_mode off")
                            else -> Shell.su(
                                "settings put global private_dns_specifier $host && settings put global private_dns_mode hostname"
                            )
                        }
                        out = r.output.lines()
                        refresh()
                    }
                }, Modifier.fillMaxWidth())
            }
            InfoTextSmall(L("m37_hint"))
        }
        SectionCard(L("m37_custom")) {
            var custom by remember { mutableStateOf("") }
            LabeledField("hostname", custom, { custom = it }, mono = true, placeholder = "dns.example.com")
            MsiButton(L("apply"), {
                if (custom.isNotBlank()) scope.launch {
                    val r = Shell.su("settings put global private_dns_specifier ${custom.replace(" ", "")} && settings put global private_dns_mode hostname")
                    out = r.output.lines(); refresh()
                }
            })
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 38 — Firewall Rule Manager (iptables / nftables)
// ==================================================================================
@Composable
fun FirewallScreen() {
    ModuleScaffold(moduleId = 38) {
        ModuleIntroCard(38)
        RootBanner()
        val scope = rememberCoroutineScope()
        var rules by remember { mutableStateOf(listOf<String>()) }
        var nft by remember { mutableStateOf<Boolean?>(null) }
        var out by remember { mutableStateOf(listOf<String>()) }
        var port by remember { mutableStateOf("") }
        var blockInput by remember { mutableStateOf(true) }

        fun load() {
            scope.launch {
                val ipt = Shell.su("iptables -S 2>/dev/null; iptables -L INPUT -n --line-numbers 2>/dev/null | head -40", 20000)
                rules = if (ipt.ok) ipt.stdout.lines().filter { it.isNotBlank() }.take(80) else listOf(Ls("m38_ipt_unavailable"))
                val n = Shell.su("command -v nft >/dev/null 2>&1 && nft list ruleset 2>/dev/null | head -20", 15000)
                nft = n.stdout.isNotBlank()
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard("iptables") {
            if (rules.isEmpty()) com.finndev.master.system.inspector.ui.LoadingRow(L("scanning"))
            else OutputConsole(rules, maxLinesShown = 60)
            MsiButton(L("refresh"), { load() }, Modifier.fillMaxWidth(), Icons.Filled.Refresh)
        }
        SectionCard(L("m38_nft")) {
            DataRow("nftables", when (nft) { null -> "…"; true -> L("m38_nft_available"); false -> L("m38_nft_missing") })
        }
        SectionCard(L("m38_add_rule")) {
            LabeledField(L("m35_ports"), port, { port = it }, placeholder = "8080")
            com.finndev.master.system.inspector.ui.ChipRow(listOf("INPUT", "OUTPUT"), if (blockInput) 0 else 1) { blockInput = it == 0 }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("m38_block"), {
                    val chain = if (blockInput) "INPUT" else "OUTPUT"
                    val p = port.toIntOrNull() ?: return@MsiButton
                    scope.launch {
                        val r = Shell.su("iptables -A $chain -p tcp --dport $p -j REJECT && iptables -S $chain | tail -5")
                        out = r.output.lines(); load()
                    }
                }, Modifier.weight(1f))
                MsiButton(L("m38_allow"), {
                    val chain = if (blockInput) "INPUT" else "OUTPUT"
                    val p = port.toIntOrNull() ?: return@MsiButton
                    scope.launch {
                        val r = Shell.su("iptables -D $chain -p tcp --dport $p -j REJECT 2>/dev/null; iptables -S $chain | tail -5")
                        out = r.output.lines(); load()
                    }
                }, Modifier.weight(1f))
            }
            InfoTextSmall(L("m38_hint"))
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}

// ==================================================================================
// MODULE 41 — MAC Address Spoofing & Manipulator (Root)
// ==================================================================================
@Composable
fun MacSpoofScreen() {
    ModuleScaffold(moduleId = 41) {
        ModuleIntroCard(41)
        RootBanner()
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var ifaces by remember { mutableStateOf<List<Triple<String, String, Boolean>>>(emptyList()) } // name, hw or ip, up
        var selected by remember { mutableStateOf("wlan0") }
        var newMac by remember { mutableStateOf("") }
        var out by remember { mutableStateOf(listOf<String>()) }

        fun load() {
            scope.launch {
                ifaces = runCatching {
                    NetworkInterface.getNetworkInterfaces().toList().filter { !it.isLoopback }.map { n ->
                        Triple(n.name, n.hardwareAddress?.joinToString(":") { "%02X".format(it) } ?: "-", n.isUp)
                    }
                }.getOrDefault(emptyList())
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m41_interfaces")) {
            ifaces.forEach { (name, mac, up) ->
                DataRow("$name ${if (up) "" else "(down)"}", mac, mono = true)
            }
            MsiButton(L("refresh"), { load() }, Modifier.fillMaxWidth(), Icons.Filled.Refresh)
        }
        SectionCard(L("m41_spoof")) {
            LabeledField(L("m41_iface"), selected, { selected = it }, mono = true, placeholder = "wlan0")
            LabeledField(L("m41_new_mac"), newMac, { newMac = it }, mono = true, placeholder = "02:1A:2B:3C:4D:5E")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("m41_random"), {
                    newMac = Crypto.randomMac()
                }, Modifier.weight(1f), Icons.Filled.Shuffle)
                MsiButton(L("apply"), {
                    val mac = newMac.trim().replace("-", ":")
                    if (!Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}").matches(mac)) {
                        out = listOf(Ls("m41_invalid_mac")); return@MsiButton
                    }
                    scope.launch {
                        val r = Shell.su(
                            "ip link set $selected down && ip link set $selected address ${mac.lowercase()} && " +
                                "ip link set $selected up && ip link show $selected"
                        )
                        out = r.output.lines(); load()
                    }
                }, Modifier.weight(1f))
            }
            InfoTextSmall(L("m41_hint"))
        }
        if (out.isNotEmpty()) SectionCard(L("output")) { OutputConsole(out) }
    }
}
