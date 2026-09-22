package com.finndev.master.system.inspector.ui

import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.AppState
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.MsiCategory
import com.finndev.master.system.inspector.core.MsiTheme
import com.finndev.master.system.inspector.core.ModuleRegistry
import com.finndev.master.system.inspector.core.RootDetector
import com.finndev.master.system.inspector.core.SystemInfo

/**
 * MODULE 78 — Master System Auditor (MSI) v4.0.0 Control Dashboard.
 * System overview + quick actions + gateway to ALL 78 modules.
 */
@Composable
fun DashboardScreen() {
    val ctx = LocalContext.current
    val nav = LocalNavController.current

    // one-time root probe for the whole app
    LaunchedEffect(Unit) {
        if (AppState.rootState.value == null) {
            AppState.rootState.value = RootDetector.detect(ctx)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MSI v" + ModuleRegistry.APP_VERSION, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    L("app_tagline"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // system overview
        val mem = remember { SystemInfo.memInfoMap() }
        val storage = remember { SystemInfo.storageRows() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(L("dash_android"), Build.VERSION.RELEASE ?: "?", "API " + Build.VERSION.SDK_INT, Modifier.weight(1f))
            val rootInfo by AppState.rootState.collectAsState()
            StatCard(
                L("dash_root"),
                when {
                    rootInfo == null -> "…"
                    rootInfo!!.rooted -> rootInfo!!.manager
                    else -> L("dash_root_no")
                },
                "ABI " + (Build.SUPPORTED_ABIS.firstOrNull() ?: "?"),
                Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                L("dash_ram"),
                SystemInfo.fmtBytes(mem["MemAvailable"] ?: 0),
                L("dash_ram_sub", SystemInfo.fmtBytes(mem["MemTotal"] ?: 0)),
                Modifier.weight(1f),
            )
            val mainStorage = storage.firstOrNull()
            StatCard(
                L("dash_storage"),
                mainStorage?.let { SystemInfo.fmtBytes(it.freeBytes) } ?: "?",
                mainStorage?.let { L("dash_storage_sub", SystemInfo.fmtBytes(it.totalBytes)) } ?: "",
                Modifier.weight(1f),
            )
        }

        // quick actions
        SectionCard(L("dash_quick")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Icons.Filled.Terminal, L("m16_name"), Modifier.weight(1f)) { nav.navigate("module/16") }
                QuickAction(Icons.Filled.Bolt, L("m15_name"), Modifier.weight(1f)) { nav.navigate("module/15") }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Icons.Filled.Translate, L("m73_name"), Modifier.weight(1f)) { nav.navigate("module/73") }
                QuickAction(Icons.Filled.Style, L("m74_name"), Modifier.weight(1f)) { nav.navigate("module/74") }
            }
        }

        // category overview
        SectionCard(L("dash_categories")) {
            MsiCategory.entries.forEach { cat ->
                val count = ModuleRegistry.byCategory(cat).size
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate("modules") }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(L(cat.key), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            L("dash_all_modules"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        // all modules (guaranteed visible per spec)
        MsiCategory.entries.forEach { cat ->
            Text(
                L(cat.key) + " (1–" + ModuleRegistry.all.last().id + ")",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            ModuleGrid(modules = ModuleRegistry.byCategory(cat))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ModuleGrid(modules: List<com.finndev.master.system.inspector.core.MsiModule>) {
    val nav = LocalNavController.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxWidth().height(((modules.size / 4 + 1) * 86).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(modules) { mod ->
            Card(
                onClick = { nav.navigate("module/${mod.id}") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.height(82.dp),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(mod.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${mod.id}. " + L(mod.nameKey),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Searchable browser of all 78 modules ("modules" tab). */
@Composable
fun ModuleBrowserBody() {
    var query by remember { mutableStateOf("") }
    var catFilter by remember { mutableStateOf(-1) }
    val nav = LocalNavController.current

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(L("search_modules"), style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        val catNames = listOf(L("cat_all")) + MsiCategory.entries.map { L(it.key) }
        ChipRow(catNames, catFilter) { catFilter = it }
        val list = ModuleRegistry.all.filter { mod ->
            (catFilter <= 0 || mod.category == MsiCategory.entries[catFilter - 1]) &&
                (query.isBlank() || L(mod.nameKey).contains(query, true) ||
                    L(mod.descKey).contains(query, true) || mod.id.toString() == query)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list) { mod ->
                Card(
                    onClick = { nav.navigate("module/${mod.id}") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(mod.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("${mod.id}. " + L(mod.nameKey), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(L(mod.descKey), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (list.isEmpty()) EmptyHint(L("empty_list"))
    }
}

/** Settings hub ("settings" tab). */
@Composable
fun SettingsHubBody() {
    val ctx = LocalContext.current
    val nav = LocalNavController.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(L("nav_settings"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard(L("m73_name")) {
            val lang by AppState.language.collectAsState()
            Text(L("current_language"), style = MaterialTheme.typography.bodySmall)
            Text(lang, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            MsiButton(L("open_module"), { nav.navigate("module/73") })
        }
        SectionCard(L("m74_name")) {
            val theme by AppState.theme.collectAsState()
            Text(
                when (theme) { MsiTheme.MATERIAL3 -> "Material 3"; MsiTheme.HACKER -> "Hacker / Cyberpunk"; MsiTheme.CLASSIC -> "Classic Utility" },
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            )
            MsiButton(L("open_module"), { nav.navigate("module/74") })
        }
        SectionCard(L("m77_name")) {
            MsiButton(L("check_updates"), { nav.navigate("module/77") }, icon = Icons.Filled.Update)
        }
        SectionCard(L("dash_storage")) {
            RootStatusRow()
            MsiButton(L("m60_name"), { nav.navigate("module/60") }, icon = Icons.Filled.FolderOpen)
        }
        SectionCard(L("about")) {
            Text("MSI — " + L("app_tagline"), style = MaterialTheme.typography.bodySmall)
            Text("v" + ModuleRegistry.APP_VERSION + " (40000)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                L("about_modules_count", 78),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
