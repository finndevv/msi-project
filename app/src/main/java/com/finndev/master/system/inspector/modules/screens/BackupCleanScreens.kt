package com.finndev.master.system.inspector.modules.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Save
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
import com.finndev.master.system.inspector.core.Exports
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
import com.finndev.master.system.inspector.ui.RootBanner
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.StatCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 53 — APK Backup & Package Extractor
// ==================================================================================
@Composable
fun ApkBackupScreen() {
    ModuleScaffold(moduleId = 53) {
        ModuleIntroCard(53)
        val ctx = LocalContext.current
        var apps by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) } // pkg, label, path
        var filter by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var log by remember { mutableStateOf(listOf<String>()) }
        val scope = rememberCoroutineScope()

        fun load() {
            scope.launch {
                busy = true
                apps = ctx.packageManager.getInstalledPackages(0).mapNotNull { p ->
                    val ai = p.applicationInfo ?: return@mapNotNull null
                    val label = runCatching { ctx.packageManager.getApplicationLabel(ai).toString() }.getOrDefault(p.packageName)
                    Triple(p.packageName, label, ai.sourceDir)
                }.sortedBy { it.second.lowercase() }
                busy = false
            }
        }
        LaunchedEffect(Unit) { load() }

        SectionCard(L("m53_apps"), "${apps.size}") {
            LabeledField(L("filter"), filter, { filter = it })
            if (busy) LoadingRow(L("scanning"))
            LazyColumn(Modifier.height(320.dp)) {
                items(apps.filter { filter.isBlank() || it.first.contains(filter, true) || it.second.contains(filter, true) }.take(150)) { (pkg, label, path) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        MsiButton("APK", {
                            scope.launch(Dispatchers.IO) {
                                val src = File(path)
                                if (src.exists()) {
                                    val name = "${label.replace(Regex("[^A-Za-z0-9._-]"), "_")}-${pkg}-${System.currentTimeMillis() % 10000}.apk"
                                    val dst = File(Exports.msiPublicDir("APKBackups"), name)
                                    src.copyTo(dst, overwrite = true)
                                    log = (log + listOf("[OK] ${dst.name} (${SystemInfo.fmtBytes(dst.length())})")).takeLast(30)
                                } else log = (log + listOf("[ERR] missing $path")).takeLast(30)
                            }
                        }, filled = false)
                    }
                }
            }
        }
        SectionCard(L("m53_all")) {
            MsiButton(L("m53_backup_user_apps"), {
                scope.launch(Dispatchers.IO) {
                    busy = true
                    val pm = ctx.packageManager
                    val pkgs = pm.getInstalledPackages(0).filter {
                        ((it.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                    }
                    pkgs.forEach { p ->
                        runCatching {
                            val srcPath = p.applicationInfo?.sourceDir ?: return@runCatching
                            val src = File(srcPath)
                            if (src.exists()) {
                                val dst = File(Exports.msiPublicDir("APKBackups"), "${p.packageName}.apk")
                                src.copyTo(dst, overwrite = true)
                                log = (log + listOf("[OK] ${p.packageName}")).takeLast(200)
                            }
                        }
                    }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.Save, enabled = !busy)
            InfoTextSmall(L("m53_hint"))
        }
        if (log.isNotEmpty()) SectionCard(L("output")) { OutputConsole(log, maxLinesShown = 40) }
    }
}

// ==================================================================================
// MODULE 54 — Deep Storage & Cache Junk Cleaner
// ==================================================================================
@Composable
fun CacheCleanerScreen() {
    ModuleScaffold(moduleId = 54) {
        ModuleIntroCard(54)
        RootBanner()
        val ctx = LocalContext.current
        var ownCache by remember { mutableStateOf(0L) }
        var log by remember { mutableStateOf(listOf<String>()) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun ownCacheSize(): Long {
            var size = 0L
            listOf(ctx.cacheDir, ctx.externalCacheDir, File(ctx.filesDir, "alpine-dl")).forEach { d ->
                d?.walkTopDown()?.filter { it.isFile }?.forEach { size += it.length() }
            }
            return size
        }
        LaunchedEffect(Unit) { ownCache = ownCacheSize() }

        SectionCard(L("m54_own")) {
            DataRow(L("m54_own_cache"), SystemInfo.fmtBytes(ownCache))
            MsiButton(L("m54_clean_own"), {
                scope.launch(Dispatchers.IO) {
                    busy = true
                    var freed = 0L
                    listOf<File?>(ctx.cacheDir, ctx.externalCacheDir).forEach { d ->
                        d?.listFiles()?.forEach {
                            freed += it.length()
                            it.deleteRecursively()
                        }
                    }
                    ownCache = ownCacheSize()
                    log = (log + listOf("[OK] ${Ls("m54_freed")}: ${SystemInfo.fmtBytes(freed)}")).takeLast(40)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.CleaningServices)
        }
        SectionCard(L("m54_system")) {
            InfoTextSmall(L("m54_system_hint"))
            if (busy) LoadingRow(L("m54_cleaning"))
            MsiButton(L("m54_trim_caches"), {
                scope.launch {
                    busy = true
                    val r = Shell.su("pm trim-caches 999999999999 && echo TRIM_OK")
                    log = (log + r.output.lines().filter { it.isNotBlank() }).takeLast(40)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            MsiButton(L("m54_deep_clean"), {
                scope.launch {
                    busy = true
                    val r = Shell.su(
                        "du -s /data/data/*/cache 2>/dev/null | head -30; " +
                            "rm -rf /data/data/*/cache/* 2>/dev/null; " +
                            "rm -rf /data/user/0/*/cache/code_cache/* 2>/dev/null; " +
                            "echo DEEP_CLEAN_DONE"
                    )
                    log = (log + r.output.lines().filter { it.isNotBlank() }).takeLast(80)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), danger = true, enabled = !busy)
        }
        if (log.isNotEmpty()) SectionCard(L("output")) { OutputConsole(log, maxLinesShown = 40) }
    }
}
