package com.finndev.master.system.inspector.modules.screens

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.Crypto
import com.finndev.master.system.inspector.core.Exports
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
import com.finndev.master.system.inspector.ui.ChipRow
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.EmptyHint
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.SectionCard
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 29 — Offline Markdown Note Editor (wired to internal storage)
// ==================================================================================
@Composable
fun MarkdownNotesScreen() {
    ModuleScaffold(moduleId = 29) {
        ModuleIntroCard(29)
        val ctx = LocalContext.current
        val notesDir = remember { File(ctx.filesDir, "notes").apply { mkdirs() } }
        var notes by remember { mutableStateOf<List<File>>(emptyList()) }
        var current by remember { mutableStateOf<File?>(null) }
        var text by remember { mutableStateOf("") }
        var preview by remember { mutableStateOf(false) }

        fun load() { notes = notesDir.listFiles()?.sortedBy { it.name } ?: emptyList() }
        LaunchedEffect(Unit) { load() }

        fun renderMarkdown(md: String): AnnotatedString = buildAnnotatedString {
            md.lines().forEach { line ->
                when {
                    line.startsWith("### ") -> { pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(line.drop(4)); pop() }
                    line.startsWith("## ") -> { pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(line.drop(3)); pop() }
                    line.startsWith("# ") -> { pushStyle(SpanStyle(fontWeight = FontWeight.Black)); append(line.drop(2)); pop() }
                    line.startsWith("- ") || line.startsWith("* ") -> append("• " + line.drop(2))
                    line.startsWith("```") -> pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                    else -> {
                        var rest = line
                        while (true) {
                            val boldIdx = rest.indexOf("**")
                            val codeIdx = rest.indexOf('`')
                            if (boldIdx >= 0 && (codeIdx < 0 || boldIdx < codeIdx)) {
                                append(rest.substring(0, boldIdx))
                                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                val close = rest.indexOf("**", boldIdx + 2)
                                if (close < 0) { append(rest.substring(boldIdx + 2)); pop(); break }
                                append(rest.substring(boldIdx + 2, close)); pop()
                                rest = rest.substring(close + 2)
                            } else if (codeIdx >= 0) {
                                append(rest.substring(0, codeIdx))
                                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                                val close = rest.indexOf('`', codeIdx + 1)
                                if (close < 0) { append(rest.substring(codeIdx + 1)); pop(); break }
                                append(rest.substring(codeIdx + 1, close)); pop()
                                rest = rest.substring(close + 1)
                            } else { append(rest); break }
                        }
                    }
                }
                append("\n")
            }
        }

        SectionCard(L("m29_notes"), "${notes.size}") {
            if (notes.isEmpty()) EmptyHint(L("m29_none"))
            LazyColumn(Modifier.height(120.dp)) {
                items(notes) { n ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(n.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        MsiButton(L("open"), { current = n; text = n.readText(); preview = false }, filled = false)
                        MsiButton(L("delete"), { n.delete(); load() }, filled = false, danger = true)
                    }
                }
            }
        }
        SectionCard(current?.name ?: L("m29_new")) {
            if (preview) Text(renderMarkdown(text), style = MaterialTheme.typography.bodySmall)
            else LabeledField("", text, { text = it }, singleLine = false, mono = false, minLines = 10)
            var name by remember { mutableStateOf(current?.name ?: "") }
            LabeledField(L("m04_name"), name, { name = it }, placeholder = "note.md")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MsiButton(if (preview) L("m29_edit") else L("m29_preview"), { preview = !preview }, Modifier.weight(1f))
                MsiButton(L("save"), {
                    val f = File(notesDir, name.ifBlank { "note-${System.currentTimeMillis() % 100000}.md" })
                    f.writeText(text)
                    current = f
                    load()
                }, Modifier.weight(1f), Icons.Filled.Save)
            }
            InfoTextSmall(L("m29_hint"))
        }
    }
}

// ==================================================================================
// MODULE 30 — QR Code & Barcode Scanner / Generator
// ==================================================================================
@Composable
fun QrCodeScreen() {
    ModuleScaffold(moduleId = 30) {
        ModuleIntroCard(30)
        val ctx = LocalContext.current
        var content by remember { mutableStateOf("MSI v4.0.0 — Master System Inspector") }
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var scanResult by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
            scanResult = result.contents ?: Ls("m30_scan_cancel")
            if (result.contents != null) content = result.contents
        }

        fun generate() {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
                    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 640, 640, hints)
                    val bmp = Bitmap.createBitmap(640, 640, Bitmap.Config.RGB_565)
                    for (x in 0 until 640) for (y in 0 until 640) {
                        bmp.setPixel(x, y, if (matrix.get(x, y)) AColor.BLACK else AColor.WHITE)
                    }
                    bitmap = bmp
                }.onFailure { bitmap = null }
            }
        }
        LaunchedEffect(Unit) { generate() }

        SectionCard(L("m30_generate")) {
            LabeledField("", content, { content = it }, singleLine = false, minLines = 2)
            MsiButton(L("m30_generate_btn"), { generate() }, Modifier.fillMaxWidth())
            bitmap?.let { bmp ->
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = "qr",
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
                MsiButton(L("m30_save"), {
                    scope.launch {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        val f = File(ctx.cacheDir, "qr-$stamp.png")
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        Exports.saveToDownloads(ctx, "msi-qr-$stamp.png", f.readBytes())
                    }
                }, Modifier.fillMaxWidth(), Icons.Filled.Save)
            }
        }
        SectionCard(L("m30_scan")) {
            MsiButton(L("m30_scan_btn"), {
                val opts = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                    setPrompt("MSI " + Ls("m30_scan_btn"))
                    setBeepEnabled(false)
                    setOrientationLocked(true)
                }
                scanner.launch(opts)
            }, Modifier.fillMaxWidth())
            if (scanResult.isNotBlank()) {
                DataRow(L("m30_result"), scanResult)
                MsiButton(L("share"), { Exports.shareText(ctx, scanResult) }, icon = Icons.Filled.Share)
            }
        }
    }
}

// ==================================================================================
// MODULE 31 — Base64, Hex & Binary Encoder/Decoder
// ==================================================================================
@Composable
fun EncoderScreen() {
    ModuleScaffold(moduleId = 31) {
        ModuleIntroCard(31)
        var input by remember { mutableStateOf("MSI Master System Inspector") }
        var output by remember { mutableStateOf("") }
        var modeIdx by remember { mutableStateOf(0) }

        fun convert() {
            output = runCatching {
                when (modeIdx) {
                    0 -> android.util.Base64.encodeToString(input.toByteArray(), android.util.Base64.NO_WRAP)
                    1 -> runCatching { String(android.util.Base64.decode(input, android.util.Base64.DEFAULT)) }
                        .getOrElse { Ls("m31_invalid_b64") }
                    2 -> input.toByteArray().joinToString("") { "%02x".format(it) }
                    3 -> runCatching { String(Crypto.unhex(input.replace(" ", ""))) }
                        .getOrElse { Ls("m31_invalid_hex") }
                    4 -> input.toByteArray().joinToString(" ") { Integer.toBinaryString(it.toInt() and 0xFF).padStart(8, '0') }
                    else -> runCatching {
                        val bytes = input.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
                            .map { it.toInt(2).toByte() }.toByteArray()
                        String(bytes)
                    }.getOrElse { Ls("m31_invalid_bin") }
                }
            }.getOrDefault(Ls("error"))
        }
        LaunchedEffect(input, modeIdx) { convert() }

        SectionCard(L("m31_mode")) {
            ChipRow(listOf("→Base64", "Base64→", "→Hex", "Hex→", "→Binary", "Binary→"), modeIdx) { modeIdx = it; convert() }
            LabeledField(L("input"), input, { input = it }, singleLine = false, mono = true, minLines = 3)
            LabeledField(L("output"), output, { }, singleLine = false, mono = true, minLines = 3)
            DataRow(L("m31_bytes"), "${input.toByteArray().size} → ${output.toByteArray().size}")
        }
        SectionCard(L("m31_file")) {
            var filePath by remember { mutableStateOf("") }
            var fileHash by remember { mutableStateOf("") }
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            LabeledField(L("m20_path"), filePath, { filePath = it }, mono = true, placeholder = "/sdcard/file.bin")
            MsiButton(L("m31_file_b64"), {
                scope.launch(Dispatchers.IO) {
                    val f = File(filePath)
                    if (f.exists() && f.length() < 8 * 1024 * 1024) {
                        val b64 = android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.DEFAULT)
                        fileHash = "sha256=" + Crypto.sha256(f.readBytes()) + "\nbase64:\n" + b64.take(4000)
                        Exports.saveTextToDownloads(ctx, f.nameWithoutExtension + ".b64.txt", b64)
                    } else fileHash = Ls("m31_file_limit")
                }
            })
            if (fileHash.isNotBlank()) OutputConsole(fileHash.lines(), maxLinesShown = 10)
        }
    }
}

// ==================================================================================
// MODULE 32 — JSON & XML Syntax Formatter / Validator
// ==================================================================================
@Composable
fun FormatterScreen() {
    ModuleScaffold(moduleId = 32) {
        ModuleIntroCard(32)
        var input by remember { mutableStateOf("""{"app":"MSI","version":4.0,"modules":[1,2,3],"root":true}""") }
        var output by remember { mutableStateOf("") }
        var fmtIdx by remember { mutableStateOf(0) }
        var valid by remember { mutableStateOf<Boolean?>(null) }

        fun format() {
            valid = null
            output = when (fmtIdx) {
                0 -> runCatching {
                    val json = JSONObject(input) // throws if invalid
                    valid = true
                    json.toString(2)
                }.getOrElse {
                    runCatching {
                        val arr = org.json.JSONArray(input)
                        valid = true
                        arr.toString(2)
                    }.getOrElse { valid = false; Ls("m32_invalid_json") + ": ${it.message?.take(120)}" }
                }
                1 -> runCatching {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(ByteArrayInputStream(input.toByteArray()), null)
                    var event = parser.eventType
                    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) { event = parser.next() }
                    valid = true
                    var indent = 0
                    input.lineSequence().filter { it.isNotBlank() }.joinToString("\n") { line ->
                        val t = line.trim()
                        when {
                            t.startsWith("</") -> { indent = (indent - 1).coerceAtLeast(0); "  ".repeat(indent) + t }
                            t.startsWith("<") && !t.endsWith("/>") && !t.startsWith("<?") && !t.contains("</") -> { val s = "  ".repeat(indent) + t; indent += 1; s }
                            else -> "  ".repeat(indent) + t
                        }
                    }
                }.getOrElse { valid = false; Ls("m32_invalid_xml") + ": ${it.message?.take(160)}" }
                else -> input
            }
        }
        LaunchedEffect(Unit) { format() }

        SectionCard(L("m32_mode")) {
            ChipRow(listOf("JSON", "XML"), fmtIdx) { fmtIdx = it; format() }
            LabeledField(L("input"), input, { input = it }, singleLine = false, mono = true, minLines = 4)
            MsiButton(L("m32_format"), { format() }, Modifier.fillMaxWidth())
            valid?.let { v ->
                Text(
                    if (v) "✔ " + L("m32_valid") else "✘ " + L("m32_invalid"),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (v) androidx.compose.ui.graphics.Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                )
            }
            LabeledField(L("output"), output, { }, singleLine = false, mono = true, minLines = 4)
        }
    }
}
