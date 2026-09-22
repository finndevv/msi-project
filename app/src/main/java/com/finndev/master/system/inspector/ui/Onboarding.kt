package com.finndev.master.system.inspector.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.finndev.master.system.inspector.core.AppState
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Loc

/**
 * MANDATORY onboarding (spec):
 *  Step 1 — storage permissions are requested IMMEDIATELY on cold start,
 *           before any dashboard is shown.
 *  Step 2 — "Please select your language" with Turkish / Spanish / English /
 *           French / Italian buttons; the entire UI switches instantly.
 */
@Composable
fun OnboardingFlow() {
    var step by remember { mutableStateOf(0) }
    if (step == 0) PermissionsStep(onNext = { step = 1 })
    else LanguageStep(onDone = { AppState.setOnboardingDone() })
}

@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val ctx = LocalContext.current
    fun readGranted() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    fun writeGranted() = Build.VERSION.SDK_INT >= 33 || Build.VERSION.SDK_INT > 29 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    fun allFilesGranted() = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    var readOk by remember { mutableStateOf(readGranted()) }
    var writeOk by remember { mutableStateOf(writeGranted()) }
    var allFilesOk by remember { mutableStateOf(allFilesGranted()) }
    var notifOk by remember { mutableStateOf(Build.VERSION.SDK_INT < 33) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        readOk = map[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readOk
        writeOk = map[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writeOk
        notifOk = map[Manifest.permission.POST_NOTIFICATIONS] ?: notifOk
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        allFilesOk = allFilesGranted()
        readOk = readGranted()
    }

    // Fire the storage permission request immediately on cold start (spec rule #2)
    LaunchedEffect(Unit) {
        val wants = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= 32) {
            wants += Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT <= 28) wants += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= 33) wants += Manifest.permission.POST_NOTIFICATIONS
        if (wants.isNotEmpty()) permLauncher.launch(wants.toTypedArray())
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(30.dp))
        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text(L("ob_welcome"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            L("ob_perm_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionCard(L("ob_perm_read"), "READ_EXTERNAL_STORAGE", readOk)
        PermissionCard(L("ob_perm_write"), "WRITE_EXTERNAL_STORAGE", writeOk)
        if (Build.VERSION.SDK_INT >= 30) {
            PermissionCard(L("ob_perm_allfiles"), "MANAGE_EXTERNAL_STORAGE", allFilesOk)
        }

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 30 && !allFilesOk) {
                    allFilesLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + ctx.packageName))
                    )
                } else {
                    val wants = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT <= 32) wants += Manifest.permission.READ_EXTERNAL_STORAGE
                    if (Build.VERSION.SDK_INT <= 28) wants += Manifest.permission.WRITE_EXTERNAL_STORAGE
                    if (Build.VERSION.SDK_INT >= 33 && !notifOk) wants += Manifest.permission.POST_NOTIFICATIONS
                    if (wants.isNotEmpty()) permLauncher.launch(wants.toTypedArray())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text(L("ob_perm_grant")) }

        OutlinedButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(if (readOk || allFilesOk) L("ob_continue") else L("ob_skip"))
        }
        if (!readOk && !allFilesOk) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text(L("ob_perm_skip_warn"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PermissionCard(title: String, permName: String, granted: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(permName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(if (granted) L("ob_perm_granted") else L("ob_perm_missing"), granted)
        }
    }
}

@Composable
private fun LanguageStep(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(30.dp))
        Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
        // "Please select your language" — translated instantly once a language is tapped
        Text(L("ob_lang_title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(L("ob_lang_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val current by AppState.language.collectAsState()
        Loc.languages.forEach { meta ->
            Surface(
                onClick = { AppState.setLanguage(meta.code) },
                shape = RoundedCornerShape(14.dp),
                color = if (current == meta.code) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(meta.code.uppercase(), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(meta.nativeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(meta.englishName, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ArrowForward, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text(L("ob_start")) }
        Text(
            L("ob_lang_note"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
    }
}
