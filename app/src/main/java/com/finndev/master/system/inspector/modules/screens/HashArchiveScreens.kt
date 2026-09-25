package com.finndev.master.system.inspector.modules.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.Crypto
import com.finndev.master.system.inspector.core.Exports
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.SystemInfo
import com.finndev.master.system.inspector.core.TarGz
import com.finndev.master.system.inspector.ui.ChipRow
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect

// ==================================================================================
// MODULE 55 — ISO & Binary MD5/SHA256 Hash Verifier
// ==================================================================================
@Composable
fun HashVerifierScreen() {
    ModuleScaffold(moduleId = 55) {
        ModuleIntroCard(55)
        val scope = rememberCoroutineScope()
        var path by remember { mutableStateOf("") }
        var algoIdx by remember { mutableStateOf(0) }
        var expected by remember { mutableStateOf("") }
        var actual by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf(0L) }
        var match by remember { mutableStateOf<Boolean?>(null) }

        val algos = listOf("MD5", "SHA-1", "SHA-256")

        SectionCard(L("m55_file")) {
            LabeledField(L("m20_path"), path, { path = it }, mono = true, placeholder = "/sdcard/Download/MSI/ISO/image.iso")
            ChipRow(algos, algoIdx) { algoIdx = it }
            if (busy) {
                Text(
                    SystemInfo.fmtBytes(progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            MsiButton(L("m55_hash"), {
                val f = File(path)
                if (!f.isFile || busy) return@MsiButton
                scope.launch(Dispatchers.IO) {
                    busy = true; match = null; actual = ""
                    actual = Crypto.fileHash(algos[algoIdx], f) { read, _ -> progress = read }
                    if (expected.isNotBlank()) match = actual.equals(expected.trim(), ignoreCase = true)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && path.isNotBlank())
        }
        SectionCard(L("m55_verify")) {
            LabeledField(L("m55_expected"), expected, { expected = it }, mono = true, placeholder = algos[algoIdx] + " ...")
            DataRow(algos[algoIdx], actual.ifBlank { "-" }, mono = true)
            match?.let { ok ->
                Text(
                    if (ok) "✔ " + L("m55_match") else "✘ " + L("m55_mismatch"),
                    color = if (ok) androidx.compose.ui.graphics.Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        SectionCard(L("m55_quick")) {
            listOf("/sdcard/Download", "/sdcard/MSI").forEach { dirPath ->
                MsiButton(dirPath, {
                    val f = File(dirPath)
                    val pick = f.listFiles()?.filter { it.isFile }?.sortedByDescending { it.length() }?.firstOrNull()
                    if (pick != null) path = pick.absolutePath
                }, Modifier.fillMaxWidth(), filled = false)
            }
        }
    }
}

// ==================================================================================
// MODULE 57 — Zip, Tar & Gz Archive Manager
// ==================================================================================
@Composable
fun ArchiveManagerScreen() {
    ModuleScaffold(moduleId = 57) {
        ModuleIntroCard(57)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var source by remember { mutableStateOf("") }
        var archiveName by remember { mutableStateOf("msi-archive") }
        var mode by remember { mutableStateOf(0) } // 0 zip, 1 tar.gz
        var log by remember { mutableStateOf(listOf<String>()) }
        var busy by remember { mutableStateOf(false) }
        var extractArchive by remember { mutableStateOf("") }

        SectionCard(L("m57_create")) {
            LabeledField(L("m57_source_dir"), source, { source = it }, mono = true, placeholder = "/sdcard/DCIM/Camera")
            LabeledField(L("m57_name"), archiveName, { archiveName = it })
            ChipRow(listOf(".zip", ".tar.gz"), mode) { mode = it }
            if (busy) LoadingRow(L("m57_working"))
            MsiButton(L("m57_pack"), {
                val src = File(source)
                if (!src.exists() || busy) return@MsiButton
                scope.launch(Dispatchers.IO) {
                    busy = true
                    runCatching {
                        if (mode == 0) {
                            val out = File(Exports.msiPublicDir("Archives"), "$archiveName.zip")
                            val entries = mutableMapOf<String, File>()
                            src.walkTopDown().filter { it.isFile }.forEach { f ->
                                entries[f.relativeTo(src).path] = f
                            }
                            TarGz.zip(entries, out)
                            log = (log + listOf("[OK] ${out.path} (${SystemInfo.fmtBytes(out.length())})")).takeLast(40)
                        } else {
                            val out = File(Exports.msiPublicDir("Archives"), "$archiveName.tar.gz")
                            TarGz.createTarGz(src, out)
                            log = (log + listOf("[OK] ${out.path} (${SystemInfo.fmtBytes(out.length())})")).takeLast(40)
                        }
                    }.onFailure { log = (log + listOf("[ERR] ${it.message}")).takeLast(40) }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.Archive, enabled = !busy)
        }
        SectionCard(L("m57_extract")) {
            LabeledField(L("m57_archive_path"), extractArchive, { extractArchive = it }, mono = true, placeholder = "/sdcard/MSI/Archives/x.zip")
            MsiButton(L("m57_unpack"), {
                val f = File(extractArchive)
                if (!f.isFile || busy) return@MsiButton
                scope.launch(Dispatchers.IO) {
                    busy = true
                    runCatching {
                        val dest = File(Exports.msiPublicDir("Extracted"), f.nameWithoutExtension)
                        dest.mkdirs()
                        if (f.name.endsWith(".zip")) {
                            TarGz.unzip(f, dest) { name, i -> if (i % 100 == 0) log = (log + listOf(".. $name")).takeLast(40) }
                        } else {
                            TarGz.extract(f, dest) { name, i -> if (i % 100 == 0) log = (log + listOf(".. $name")).takeLast(40) }
                        }
                        log = (log + listOf("[OK] -> ${dest.path}")).takeLast(40)
                    }.onFailure { log = (log + listOf("[ERR] ${it.message}")).takeLast(40) }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.FolderOpen, enabled = !busy)
        }
        if (log.isNotEmpty()) SectionCard(L("output")) { OutputConsole(log, maxLinesShown = 40) }
    }
}
