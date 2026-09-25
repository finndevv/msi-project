package com.finndev.master.system.inspector.modules.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.Crypto
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.DataRowMono
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
import java.io.RandomAccessFile
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect

// ==================================================================================
// MODULE 46 — Custom Captcha & Bot Filtering Test Bench
// ==================================================================================
@Composable
fun CaptchaScreen() {
    ModuleScaffold(moduleId = 46) {
        ModuleIntroCard(46)
        var challenge by remember { mutableStateOf("") }
        var answer by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<String?>(null) }
        var solved by remember { mutableStateOf(0) }
        var failed by remember { mutableStateOf(0) }
        var log by remember { mutableStateOf(listOf<String>()) }

        fun newChallenge() {
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            challenge = Crypto.randomToken(5, alphabet)
                .chunked(1).joinToString(" ") // spacing against simple OCR
            answer = ""
            result = null
        }
        LaunchedEffect(Unit) { newChallenge() }

        SectionCard(L("m46_bench")) {
            Text(
                L("m46_challenge"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // distorted captcha bitmap
            Canvas(Modifier.fillMaxWidth().height(84.dp)) {
                val seed = challenge.hashCode()
                val rnd = java.util.Random(seed.toLong())
                drawRect(Color(0xFF0E1A12))
                // noise lines
                repeat(7) {
                    drawLine(
                        color = Color(0xFF1E4D2E),
                        start = androidx.compose.ui.geometry.Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                        end = androidx.compose.ui.geometry.Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                        strokeWidth = 2f,
                    )
                }
                // character blocks
                challenge.filter { it != ' ' }.forEachIndexed { i, ch ->
                    val x = size.width * (0.18f + i * 0.14f)
                    val y = size.height * (0.35f + rnd.nextFloat() * 0.3f)
                    drawContext.canvas.nativeCanvas.drawText(
                        ch.toString(),
                        x, y,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.rgb(0, 230, 118)
                            textSize = 44f
                            isFakeBoldText = true
                            val skew = (rnd.nextFloat() - 0.5f) * 0.4f
                            textSkewX = skew
                        },
                    )
                }
            }
            LabeledField(L("m46_answer"), answer, { answer = it.uppercase() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(L("m46_verify"), {
                    val ok = answer.replace(" ", "") == challenge.replace(" ", "")
                    result = if (ok) Ls("m46_pass") else Ls("m46_fail")
                    if (ok) solved++ else failed++
                    log = (log + listOf("${if (ok) "PASS" else "FAIL"}: '${answer}' vs '${challenge}'")).takeLast(20)
                }, Modifier.weight(1f))
                MsiButton(L("m46_new"), { newChallenge() }, Modifier.weight(1f))
            }
            result?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = if (it == L("m46_pass")) Color(0xFF00E676) else MaterialTheme.colorScheme.error)
            }
            DataRow(L("m46_stats"), "$solved ✓ / $failed ✗")
        }
        if (log.isNotEmpty()) SectionCard(L("m46_log")) { OutputConsole(log, maxLinesShown = 20) }
    }
}

// ==================================================================================
// MODULE 48 — Sensitive File & Data Shredder (Permanent wipe)
// ==================================================================================
@Composable
fun ShredderScreen() {
    ModuleScaffold(moduleId = 48) {
        ModuleIntroCard(48)
        var path by remember { mutableStateOf("") }
        var passes by remember { mutableStateOf(3) }
        var busy by remember { mutableStateOf(false) }
        var log by remember { mutableStateOf(listOf<String>()) }
        var confirm by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        SectionCard(L("m48_target")) {
            LabeledField(L("m20_path"), path, { path = it }, mono = true, placeholder = "/sdcard/DCIM/private.jpg")
            Text(L("m48_passes") + ": $passes", style = MaterialTheme.typography.labelSmall)
            androidx.compose.material3.Slider(
                value = passes.toFloat(), onValueChange = { passes = it.toInt().coerceIn(1, 7) },
                valueRange = 1f..7f, steps = 5,
            )
            val target = File(path)
            DataRow(L("m48_exists"), if (target.exists()) L("yes") else L("no"))
            if (target.exists()) DataRow(L("size"), com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(target.length()))
            MsiButton(L("m48_shred"), { confirm = true }, Modifier.fillMaxWidth(), Icons.Filled.DeleteForever, danger = true, enabled = path.isNotBlank())
            InfoTextSmall(L("m48_hint"))
        }
        if (confirm) {
            com.finndev.master.system.inspector.ui.ConfirmDialog(
                title = L("m48_shred"),
                text = L("m48_confirm") + "\n$path",
                confirmLabel = L("m48_shred"),
                onConfirm = {
                    confirm = false
                    scope.launch {
                        busy = true
                        log = listOf("[MSI] shredding $path ($passes passes) ...")
                        withContext(Dispatchers.IO) {
                            val files = if (File(path).isDirectory) File(path).walkTopDown().filter { it.isFile }.toList()
                            else listOf(File(path))
                            var done = 0
                            files.forEach { f ->
                                runCatching {
                                    val length = f.length()
                                    RandomAccessFile(f, "rw").use { raf ->
                                        repeat(passes) { pass ->
                                            raf.seek(0)
                                            var left = length
                                            val buf = ByteArray(256 * 1024)
                                            while (left > 0) {
                                                Crypto.randomBytes(buf.size).copyInto(buf)
                                                val w = minOf(buf.size.toLong(), left).toInt()
                                                raf.write(buf, 0, w)
                                                left -= w
                                            }
                                            raf.fd.sync()
                                            if (pass == passes - 1) { // final pass: zeros
                                                raf.seek(0)
                                                left = length
                                                val zeros = ByteArray(256 * 1024)
                                                while (left > 0) {
                                                    val w = minOf(zeros.size.toLong(), left).toInt()
                                                    raf.write(zeros, 0, w)
                                                    left -= w
                                                }
                                                raf.fd.sync()
                                            }
                                        }
                                    }
                                    f.delete()
                                    done++
                                    if (done % 10 == 0 || done == files.size) {
                                        log = (log + listOf("shredded [$done/${files.size}] ${f.name.take(40)}")).takeLast(100)
                                    }
                                }.onFailure {
                                    log = (log + listOf("ERR ${f.name}: ${it.message}")).takeLast(100)
                                }
                            }
                            if (File(path).isDirectory) File(path).deleteRecursively()
                        }
                        log = log + listOf("[MSI] done.")
                        busy = false
                    }
                },
                onDismiss = { confirm = false },
            )
        }
        if (busy) LoadingRow(L("m48_running"))
        if (log.size > 1) SectionCard(L("output")) { OutputConsole(log, maxLinesShown = 60) }
    }
}

// ==================================================================================
// MODULE 49 — Regex Pattern Testing Workbench
// ==================================================================================
@Composable
fun RegexScreen() {
    ModuleScaffold(moduleId = 49) {
        ModuleIntroCard(49)
        var pattern by remember { mutableStateOf("\\b(\\w+)@(\\w+\\.\\w+)\\b") }
        var testText by remember { mutableStateOf("Contact: root@msi.dev, admin@alpine.org — or visit forum.xda.dev") }
        var flags by remember { mutableStateOf(0) }
        var replacement by remember { mutableStateOf("[REDACTED]") }
        val scope = rememberCoroutineScope()
        var matches by remember { mutableStateOf(listOf<String>()) }
        var replaced by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }

        fun run() {
            matches = emptyList(); replaced = ""; error = ""
            runCatching {
                val regex = if (flags == 1) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
                matches = regex.findAll(testText).map { it.value }.toList()
                replaced = regex.replace(testText, replacement)
            }.onFailure { error = it.message ?: "?" }
        }
        LaunchedEffect(pattern, testText, flags, replacement) { run() }

        SectionCard(L("m49_pattern")) {
            LabeledField("regex", pattern, { pattern = it }, mono = true)
            com.finndev.master.system.inspector.ui.ChipRow(listOf("case sensitive", "ignore case"), flags) { flags = it }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
        SectionCard(L("m49_input")) {
            LabeledField("", testText, { testText = it }, singleLine = false, minLines = 4)
        }
        SectionCard(L("m49_matches"), "${matches.size}") {
            matches.take(20).forEach { m -> ConsoleLine("• $m") }
        }
        SectionCard(L("m49_replace")) {
            LabeledField(L("m49_with"), replacement, { replacement = it }, mono = true)
            Text(replaced, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ==================================================================================
// MODULE 50 — Color Palette & HEX/RGB Code Picker
// ==================================================================================
@Composable
fun ColorPickerScreen() {
    ModuleScaffold(moduleId = 50) {
        ModuleIntroCard(50)
        var hue by remember { mutableStateOf(140f) }
        var sat by remember { mutableStateOf(0.9f) }
        var lum by remember { mutableStateOf(0.55f) }
        var palette by remember { mutableStateOf(listOf<Int>()) }

        val hsv = floatArrayOf(hue, sat, lum)
        val colorInt = android.graphics.Color.HSVToColor(hsv)
        val hex = "#%06X".format(colorInt and 0xFFFFFF)
        val r = (colorInt shr 16) and 0xFF; val g = (colorInt shr 8) and 0xFF; val b = colorInt and 0xFF

        SectionCard(L("m50_current")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(
                    Modifier
                        .size(72.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                ) {
                    drawRect(Color(colorInt))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    DataRowMono(hex)
                    DataRowMono("rgb($r, $g, $b)")
                    DataRowMono("hsl(${hue.toInt()}°, ${(sat * 100).toInt()}%, ${(lum * 100).toInt()}%)")
                }
            }
        }
        SectionCard(L("m50_dials")) {
            ColorSlider(L("m50_hue"), hue, 360f, { hue = it })
            ColorSlider(L("m50_sat"), sat * 100, 100f, { sat = it / 100f })
            ColorSlider(L("m50_lum"), lum * 100, 100f, { lum = it / 100f })
            MsiButton(L("m50_add_palette"), { palette = (palette + colorInt).takeLast(24) })
        }
        if (palette.isNotEmpty()) {
            SectionCard(L("m50_palette"), "${palette.size}") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.take(12).forEach { c ->
                        Canvas(
                            Modifier
                                .size(28.dp)
                                .clickable { }
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        ) { drawRect(Color(c)) }
                    }
                }
                palette.take(12).forEach { c -> DataRowMono("#%06X".format(c and 0xFFFFFF)) }
                MsiButton(L("m50_clear"), { palette = emptyList() }, danger = true)
            }
        }
        SectionCard(L("m50_hex_input")) {
            var hexIn by remember { mutableStateOf("") }
            LabeledField("HEX", hexIn, { hexIn = it }, mono = true, placeholder = "#00E676")
            MsiButton(L("apply"), {
                val clean = hexIn.removePrefix("#").trim()
                if (Regex("[0-9a-fA-F]{6}").matches(clean)) {
                    val hsvArr = FloatArray(3)
                    android.graphics.Color.RGBToHSV(
                        clean.substring(0, 2).toInt(16),
                        clean.substring(2, 4).toInt(16),
                        clean.substring(4, 6).toInt(16),
                        hsvArr,
                    )
                    hue = hsvArr[0]; sat = hsvArr[1]; lum = hsvArr[2]
                }
            })
        }
    }
}

@Composable
private fun ColorSlider(label: String, value: Float, max: Float, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.Slider(
            value = value.coerceIn(0f, max),
            onValueChange = onChange,
            valueRange = 0f..max,
        )
    }
}
