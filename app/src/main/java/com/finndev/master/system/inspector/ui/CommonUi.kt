package com.finndev.master.system.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finndev.master.system.inspector.core.AppState
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.ModuleRegistry
import com.finndev.master.system.inspector.core.MonoTerminal
import com.finndev.master.system.inspector.core.RootInfo
import kotlinx.coroutines.flow.MutableStateFlow

/** Standard module screen scaffold: top bar (title + back) + scrollable content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScaffold(
    moduleId: Int,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val mod = ModuleRegistry.byId(moduleId)
    val nav = LocalNavController.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            L(mod?.nameKey ?: "m${moduleId}_name"),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "MSI #$moduleId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { pad ->
        if (scrollable) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** Module description banner shown on top of every module. */
@Composable
fun ModuleIntroCard(moduleId: Int) {
    val mod = ModuleRegistry.byId(moduleId)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                mod?.icon ?: Icons.Filled.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                L(mod?.descKey ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                )
                if (subtitle != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun DataRow(label: String, value: String, mono: Boolean = false, copyable: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f), maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ) else MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.62f),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 6, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        if (copyable) CopyButton(value)
    }
}

/** Monospace full-width value row with copy support (used by generators). */
@Composable
fun DataRowMono(value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        CopyButton(value)
    }
}

@Composable
fun CopyButton(value: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    IconButton(onClick = {
        clipboard.setText(AnnotatedString(value))
        copied = true
    }, modifier = Modifier.size(28.dp)) {
        Icon(
            Icons.Filled.ContentCopy, contentDescription = "copy",
            modifier = Modifier.size(14.dp),
            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun StatCard(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                value, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) Text(
                sub, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Monospace console output with autoscroll. */
@Composable
fun OutputConsole(
    lines: List<String>,
    modifier: Modifier = Modifier,
    maxLinesShown: Int = 800,
    fontSize: TextUnitSafe = 11.sp,
) {
    val shown = remember(lines.size) { if (lines.size > maxLinesShown) lines.takeLast(maxLinesShown) else lines }
    val listState = rememberLazyListState()
    val atBottom by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { it.index >= shown.size - 3 } ?: true } }
    LaunchedEffect(shown.size, atBottom) {
        if (atBottom && shown.isNotEmpty()) listState.animateScrollToItem(shown.size - 1)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(((shown.size.coerceAtMost(26)) * (fontSize.value * 1.7f)).dp.coerceAtLeast(40.dp)),
        ) {
            items(shown) { line ->
                Text(
                    line.ifBlank { " " },
                    fontFamily = MonoTerminal,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.35f,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
                )
            }
            item { Spacer(Modifier.height(6.dp)) }
        }
    }
}

private typealias TextUnitSafe = androidx.compose.ui.unit.TextUnit

@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    mono: Boolean = false,
    minLines: Int = 1,
) {
    Column(modifier) {
        if (label.isNotBlank()) Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
            singleLine = singleLine,
            minLines = minLines,
            textStyle = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(10.dp),
        )
    }
}

@Composable
fun MsiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    filled: Boolean = true,
) {
    val colors = if (danger) ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    ) else ButtonDefaults.buttonColors()
    if (filled) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors, shape = RoundedCornerShape(10.dp)) {
            if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = RoundedCornerShape(10.dp)) {
            if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = { Text(text, style = MaterialTheme.typography.bodySmall) },
        confirmButton = { Button(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(L("cancel")) } },
    )
}

@Composable
fun RootBanner(required: Boolean = true) {
    if (!required) return
    val root by AppState.rootState.collectAsState()
    val r = root
    if (r == null || r.rooted) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                L("root_required") + " — " + L("root_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun RootStatusRow() {
    val root by AppState.rootState.collectAsState()
    val r: RootInfo? = root
    DataRow(L("dash_root"), when {
        r == null -> L("dash_root_unknown")
        r.rooted -> L("dash_root_yes") + " (" + r.manager + ")"
        else -> L("dash_root_no")
    })
}

/** Chip row used for selectable options. */
@Composable
fun ChipRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { i, opt ->
            Surface(
                shape = RoundedCornerShape(50),
                color = if (i == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onSelect(i) },
            ) {
                Text(
                    opt,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == selectedIndex) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, ok: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (ok) Color(0x3300E676) else MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text, style = MaterialTheme.typography.labelSmall,
            color = if (ok) Color(0xFF00E676) else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
fun LoadingRow(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Simple canvas bar chart. */
@Composable
fun SimpleBarChart(values: List<Float>, modifier: Modifier = Modifier, barColor: Color = Color.Unspecified) {
    val color = if (barColor == Color.Unspecified) MaterialTheme.colorScheme.primary else barColor
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxWidth().height(72.dp)) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)
        val n = values.size
        val gap = size.width / (n * 4f + 1f)
        val barW = size.width / n - gap
        values.forEachIndexed { i, v ->
            val h = (v / max) * (size.height - 4f)
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(gap + i * (barW + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(barW, h),
            )
        }
    }
}

/** Canvas sparkline. */
@Composable
fun Sparkline(points: List<Float>, modifier: Modifier = Modifier, lineColor: Color = Color.Unspecified) {
    val color = if (lineColor == Color.Unspecified) MaterialTheme.colorScheme.secondary else lineColor
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxWidth().height(56.dp)) {
        if (points.size < 2) return@Canvas
        val max = points.max(); val min = points.min(); val range = (max - min).coerceAtLeast(0.0001f)
        val step = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - ((v - min) / range) * (size.height - 6f) - 3f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    }
}

@Composable
fun InfoText(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ErrorText(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
fun OkText(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF00E676),
    )
}

/** Shared "busy" output state for command-runner style modules. */
class RunnerState {
    val running = MutableStateFlow(false)
    val lines = MutableStateFlow<List<String>>(emptyList())
    fun clear() { lines.value = emptyList() }
    fun append(line: String) { lines.value = lines.value + line }
    fun appendAll(new: List<String>) { lines.value = (lines.value + new).takeLast(2000) }
}
