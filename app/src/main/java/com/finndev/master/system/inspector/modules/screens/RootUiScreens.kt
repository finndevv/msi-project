package com.finndev.master.system.inspector.modules.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
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
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
import com.finndev.master.system.inspector.core.Shell
import com.finndev.master.system.inspector.core.TerminalCenter
import com.finndev.master.system.inspector.ui.ConfirmDialog
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.OutputConsole
import com.finndev.master.system.inspector.ui.RootBanner
import com.finndev.master.system.inspector.ui.SectionCard
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 61 — System Font Customizer (Root)
// ==================================================================================
@Composable
fun FontCustomizerScreen() {
    ModuleScaffold(moduleId = 61) {
        ModuleIntroCard(61)
        RootBanner()
        val scope = rememberCoroutineScope()
        var fontPath by remember { mutableStateOf("") }
        var out by remember { mutableStateOf(listOf<String>()) }
        var confirm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val r = Shell.su("ls /system/fonts/ | head -20; echo ---; mount | grep system | head -3", 10000)
            out = r.stdout.lines().filter { it.isNotBlank() }.take(30)
        }

        SectionCard(L("m61_current")) {
            OutputConsole(out, maxLinesShown = 30)
        }
        SectionCard(L("m61_install")) {
            LabeledField(L("m61_font_path"), fontPath, { fontPath = it }, mono = true, placeholder = "/sdcard/Download/JetBrainsMono-Regular.ttf")
            MsiButton(L("m61_install_btn"), { confirm = true }, Modifier.fillMaxWidth(), Icons.Filled.Save, enabled = fontPath.isNotBlank())
            InfoTextSmall(L("m61_hint"))
        }
        if (confirm) {
            ConfirmDialog(
                title = L("m61_install_btn"),
                text = L("m61_confirm"),
                confirmLabel = L("confirm"),
                onConfirm = {
                    confirm = false
                    scope.launch {
                        val f = File(fontPath)
                        if (!f.name.endsWith(".ttf")) {
                            out = listOf("[ERR] " + Ls("m61_not_ttf")); return@launch
                        }
                        val r = Shell.su(
                            "mount -o remount,rw /system 2>/dev/null; " +
                                "cp /system/fonts/Roboto-Regular.ttf /sdcard/MSI/backup-Roboto-Regular.ttf 2>/dev/null; " +
                                "cp '$fontPath' /system/fonts/MSICustom-Regular.ttf && chmod 644 /system/fonts/MSICustom-Regular.ttf && " +
                                "ls -la /system/fonts/MSICustom-Regular.ttf && mount -o remount,ro /system 2>/dev/null; echo FONT_INSTALLED"
                        )
                        out = r.output.lines().takeLast(30)
                    }
                },
                onDismiss = { confirm = false },
            )
        }
        SectionCard(L("m61_restore")) {
            MsiButton(L("m61_restore_btn"), {
                scope.launch {
                    val r = Shell.su(
                        "mount -o remount,rw /system 2>/dev/null; " +
                            "rm -f /system/fonts/MSICustom-Regular.ttf && mount -o remount,ro /system 2>/dev/null; echo FONT_REMOVED"
                    )
                    out = r.output.lines().takeLast(30)
                }
            }, Modifier.fillMaxWidth(), Icons.Filled.RestartAlt)
        }
    }
}

// ==================================================================================
// MODULE 62 — Bootanimation Customizer (Root)
// ==================================================================================
@Composable
fun BootAnimScreen() {
    ModuleScaffold(moduleId = 62) {
        ModuleIntroCard(62)
        RootBanner()
        val scope = rememberCoroutineScope()
        var zipPath by remember { mutableStateOf("") }
        var out by remember { mutableStateOf(listOf<String>()) }
        var confirm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val r = Shell.su("ls -la /system/media/ 2>/dev/null; ls /sdcard/MSI/*.zip 2>/dev/null | head -5", 10000)
            out = r.stdout.lines().filter { it.isNotBlank() }.take(20)
        }

        SectionCard(L("m62_current")) {
            OutputConsole(out, maxLinesShown = 20)
        }
        SectionCard(L("m62_install")) {
            LabeledField(L("m62_zip_path"), zipPath, { zipPath = it }, mono = true, placeholder = "/sdcard/MSI/bootanimation.zip")
            MsiButton(L("m62_validate"), {
                scope.launch {
                    val f = File(zipPath)
                    if (!f.isFile) { out = listOf("[ERR] " + Ls("m62_no_file")); return@launch }
                    runCatching {
                        val zip = java.util.zip.ZipFile(f)
                        val hasDesc = zip.getEntry("desc.txt") != null
                        val count = zip.size()
                        zip.close()
                        out = listOf(
                            "desc.txt: " + (if (hasDesc) "✔" else "✘"),
                            "entries: $count",
                            "size: ${com.finndev.master.system.inspector.core.SystemInfo.fmtBytes(f.length())}",
                            if (hasDesc) Ls("m62_valid_zip") else Ls("m62_invalid_zip"),
                        )
                    }.onFailure { out = listOf("[ERR] ${it.message}") }
                }
            })
            MsiButton(L("m62_install_btn"), { confirm = true }, Modifier.fillMaxWidth(), enabled = zipPath.isNotBlank())
            InfoTextSmall(L("m62_hint"))
        }
        if (confirm) {
            ConfirmDialog(
                title = L("m62_install_btn"),
                text = L("m62_confirm"),
                confirmLabel = L("confirm"),
                onConfirm = {
                    confirm = false
                    scope.launch {
                        val r = Shell.su(
                            "mount -o remount,rw /system 2>/dev/null; " +
                                "mkdir -p /system/media; " +
                                "cp /system/media/bootanimation.zip /sdcard/MSI/backup-bootanimation.zip 2>/dev/null; " +
                                "cp '$zipPath' /system/media/bootanimation.zip && chmod 644 /system/media/bootanimation.zip && " +
                                "ls -la /system/media/bootanimation.zip && mount -o remount,ro /system 2>/dev/null; echo BOOTANIM_INSTALLED"
                        )
                        out = r.output.lines().takeLast(20)
                    }
                },
                onDismiss = { confirm = false },
            )
        }
        SectionCard(L("m62_restore")) {
            MsiButton(L("m62_restore_btn"), {
                scope.launch {
                    val r = Shell.su(
                        "mount -o remount,rw /system 2>/dev/null; " +
                            "cp /sdcard/MSI/backup-bootanimation.zip /system/media/bootanimation.zip 2>/dev/null && echo RESTORED || echo NO_BACKUP; " +
                            "mount -o remount,ro /system 2>/dev/null"
                    )
                    out = r.output.lines().takeLast(20)
                }
            })
        }
    }
}

// ==================================================================================
// MODULE 63 — Quick Settings Notification Tile Integration
// ==================================================================================
@Composable
fun QsTileScreen() {
    ModuleScaffold(moduleId = 63) {
        ModuleIntroCard(63)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var tileAdded by remember { mutableStateOf<Boolean?>(null) }

        fun checkTile() {
            scope.launch {
                val r = Shell.su("settings get secure sysui_qs_tiles 2>/dev/null | grep -o custom(.*) | grep -o MsiTileService; echo RC=\$?", 8000)
                tileAdded = r.stdout.contains("MsiTileService")
            }
        }
        LaunchedEffect(Unit) { checkTile() }

        SectionCard(L("m63_tile")) {
            DataRow("MsiTileService", "com.finndev.master.system.inspector/.core.tile.MsiTileService", mono = true)
            DataRow(L("m63_state"), when (tileAdded) { null -> "…"; true -> L("m63_added"); false -> L("m63_not_added") })
            DataRow(L("m63_action"), L("m63_action_desc"))
            InfoTextSmall(L("m63_howto"))
        }
        SectionCard(L("actions")) {
            MsiButton(L("m63_open_terminal"), {
                // The tile opens this same target; here we simulate the tile tap.
                val intent = android.content.Intent(ctx, com.finndev.master.system.inspector.MainActivity::class.java).apply {
                    putExtra("open", "terminal")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                ctx.startActivity(intent)
            }, Modifier.fillMaxWidth())
            MsiButton(L("refresh"), { checkTile() }, Modifier.fillMaxWidth(), filled = false)
            DataRow("Terminal", when (TerminalCenter.state.value) {
                TerminalCenter.State.RUNNING -> L("m16_running")
                else -> L("m16_idle")
            })
        }
        SectionCard(L("m63_notification")) {
            MsiButton(L("m63_test_notification"), {
                val nm = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    val ch = android.app.NotificationChannel("msi_general", "MSI General", android.app.NotificationManager.IMPORTANCE_LOW)
                    nm.createNotificationChannel(ch)
                }
                val notif = androidx.core.app.NotificationCompat.Builder(ctx, "msi_general")
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("MSI")
                    .setContentText(Ls("m63_test_notification"))
                    .build()
                nm.notify(9901, notif)
            }, Modifier.fillMaxWidth())
        }
    }
}
