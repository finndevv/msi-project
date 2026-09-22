package com.finndev.master.system.inspector.modules.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.finndev.master.system.inspector.core.Crypto
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.Ls
import com.finndev.master.system.inspector.ui.ConfirmDialog
import com.finndev.master.system.inspector.ui.DataRow
import com.finndev.master.system.inspector.ui.EmptyHint
import com.finndev.master.system.inspector.ui.LabeledField
import com.finndev.master.system.inspector.ui.LoadingRow
import com.finndev.master.system.inspector.ui.ModuleIntroCard
import com.finndev.master.system.inspector.ui.ModuleScaffold
import com.finndev.master.system.inspector.ui.MsiButton
import com.finndev.master.system.inspector.ui.SectionCard
import com.finndev.master.system.inspector.ui.StatusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.crypto.SecretKey
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// ==================================================================================
// MODULE 33 — Offline Encrypted Password Manager (Secure local DB)
// ==================================================================================
@Composable
fun PasswordManagerScreen() {
    ModuleScaffold(moduleId = 33) {
        ModuleIntroCard(33)
        val ctx = LocalContext.current
        val vaultFile = remember { File(ctx.filesDir, "vault.msi") }
        var unlocked by remember { mutableStateOf(false) }
        var master by remember { mutableStateOf("") }
        var key by remember { mutableStateOf<SecretKey?>(null) }
        var entries by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) } // title, user, pass
        var error by remember { mutableStateOf("") }
        var newTitle by remember { mutableStateOf("") }
        var newUser by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var visible by remember { mutableStateOf(-1) }
        val scope = rememberCoroutineScope()

        suspend fun unlock(pwd: String): Boolean = withContext(Dispatchers.IO) {
            runCatching {
                val blob = vaultFile.readBytes()
                val salt = blob.copyOfRange(0, 16)
                val k = Crypto.deriveKey(pwd.toCharArray(), salt)
                val json = String(Crypto.decrypt(k, blob.copyOfRange(16, blob.size)))
                entries = json.lineSequence().filter { it.contains('\u0001') }.map {
                    val p = it.split('\u0001')
                    Triple(p[0], p.getOrElse(1) { "" }, p.getOrElse(2) { "" })
                }.toList()
                key = k
                true
            }.getOrDefault(false)
        }

        fun saveVault() {
            val k = key ?: return
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val data = entries.joinToString("\n") { "${it.first}\u0001${it.second}\u0001${it.third}" }
                    val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                    val ek = Crypto.deriveKey(master.toCharArray(), salt)
                    vaultFile.writeBytes(salt + Crypto.encrypt(ek, data.toByteArray()))
                }
            }
        }

        if (!unlocked) {
            SectionCard(L("m33_unlock")) {
                InfoTextSmall(L("m33_hint"))
                LabeledField(L("m33_master"), master, { master = it }, mono = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                MsiButton(L("m33_unlock_btn"), {
                    scope.launch {
                        error = ""
                        if (!vaultFile.exists()) {
                            if (master.length < 4) { error = Ls("m33_short_pwd"); return@launch }
                            val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                            key = Crypto.deriveKey(master.toCharArray(), salt)
                            vaultFile.writeBytes(salt + Crypto.encrypt(key!!, "{}".toByteArray()))
                            unlocked = true
                        } else {
                            val ok = unlock(master)
                            if (ok) unlocked = true else error = Ls("m33_wrong_pwd")
                        }
                    }
                }, Modifier.fillMaxWidth(), Icons.Filled.Lock)
            }
        } else {
            SectionCard(L("m33_entries"), "${entries.size}") {
                if (entries.isEmpty()) EmptyHint(L("m33_empty"))
                LazyColumn(Modifier.height(220.dp)) {
                    items(entries.indices.toList()) { i ->
                        val (title, user, pass) = entries[i]
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.bodyMedium)
                                Text(user, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (visible == i) Text(
                                    pass,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            MsiButton(if (visible == i) "🙈" else "👁", { visible = if (visible == i) -1 else i }, filled = false)
                            MsiButton(L("delete"), {
                                entries = entries.filterIndexed { idx, _ -> idx != i }
                                saveVault()
                            }, filled = false, danger = true)
                        }
                    }
                }
            }
            SectionCard(L("m33_add")) {
                LabeledField(L("m33_title"), newTitle, { newTitle = it })
                LabeledField(L("m33_user"), newUser, { newUser = it })
                LabeledField(L("m33_pass"), newPass, { newPass = it }, mono = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MsiButton(L("save"), {
                        if (newTitle.isNotBlank()) {
                            entries = entries + Triple(newTitle, newUser, newPass)
                            saveVault()
                            newTitle = ""; newUser = ""; newPass = ""
                        }
                    }, Modifier.weight(1f), Icons.Filled.Shield)
                    MsiButton(L("m33_gen"), {
                        newPass = Crypto.randomToken(20, "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#\$%^&*")
                    }, Modifier.weight(1f))
                }
            }
            MsiButton(L("m33_lock"), {
                unlocked = false; key = null; entries = emptyList(); master = ""
            }, Modifier.fillMaxWidth())
        }
    }
}

// ==================================================================================
// MODULE 47 — Secure Random Password & Token Generator
// ==================================================================================
@Composable
fun TokenGenScreen() {
    ModuleScaffold(moduleId = 47) {
        ModuleIntroCard(47)
        var length by remember { mutableStateOf(24f) }
        var upper by remember { mutableStateOf(true) }
        var lower by remember { mutableStateOf(true) }
        var digits by remember { mutableStateOf(true) }
        var symbols by remember { mutableStateOf(true) }
        var ambiguous by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(0) } // 0 password, 1 hex token, 2 uuid, 3 passphrase
        var results by remember { mutableStateOf(listOf<String>()) }

        val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val UPPER_AMB = "IO"
        val LOWER = "abcdefghijkmnpqrstuvwxyz"
        val LOWER_AMB = "lo"
        val DIGITS = "23456789"
        val DIGITS_AMB = "01"
        val SYM = "!@#$%^&*()-_=+[]{};:,.?/"
        val WORDS = listOf("alpine", "kernel", "neon", "cipher", "root", "delta", "vector", "matrix", "pixel",
            "quark", "photon", "radar", "sonic", "titan", "volt", "warp", "zenith", "onyx", "quartz", "cipher")

        fun charset(): String = buildString {
            if (upper) append(UPPER)
            if (lower) append(LOWER)
            if (digits) append(DIGITS)
            if (symbols) append(SYM)
            if (ambiguous) { if (upper) append(UPPER_AMB); if (lower) append(LOWER_AMB); if (digits) append(DIGITS_AMB) }
        }

        fun generate() {
            results = (1..5).map {
                when (mode) {
                    1 -> Crypto.randomToken(length.toInt() / 2, "0123456789abcdef")
                    2 -> java.util.UUID.randomUUID().toString()
                    3 -> {
                        val w = (1..4).map { WORDS[Crypto.randomBytes(1)[0].toInt() and 0xFF % WORDS.size] }
                        w.joinToString("-") + "-" + Crypto.randomToken(3, "0123456789")
                    }
                    else -> Crypto.randomToken(length.toInt(), charset())
                }
            }
        }
        LaunchedEffect(Unit) { generate() }

        SectionCard(L("m47_mode")) {
            val modes = listOf(L("m47_password"), "hex token", "uuid", L("m47_passphrase"))
            com.finndev.master.system.inspector.ui.ChipRow(modes, mode) { mode = it; generate() }
            Text(L("m47_length") + ": ${length.toInt()}", style = MaterialTheme.typography.labelSmall)
            androidx.compose.material3.Slider(value = length, onValueChange = { length = it.coerceIn(6f, 64f); if (mode == 0 || mode == 1) generate() }, valueRange = 6f..64f)
            if (mode == 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Toggle("A-Z", upper, { upper = it; generate() })
                    Toggle("a-z", lower, { lower = it; generate() })
                    Toggle("0-9", digits, { digits = it; generate() })
                    Toggle("!@#", symbols, { symbols = it; generate() })
                }
            }
        }
        SectionCard(L("m47_results")) {
            results.forEach { r ->
                com.finndev.master.system.inspector.ui.DataRowMono(r)
            }
            MsiButton(L("m47_regenerate"), { generate() }, Modifier.fillMaxWidth())
            InfoTextSmall(L("m47_hint"))
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = value,
        onClick = { onChange(!value) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}
