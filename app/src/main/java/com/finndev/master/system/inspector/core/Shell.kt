package com.finndev.master.system.inspector.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.concurrent.thread

/** Result of a one-shot shell command. */
data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exitCode == 0
    val output: String
        get() {
            val o = stdout.trim(); val e = stderr.trim()
            return when { o.isEmpty() -> e; e.isEmpty() -> o; else -> "$o\n$e" }
        }
    override fun toString(): String = output
}

/** Handle for a long-running streaming process (logcat, dmesg -w, ping ...). */
class ShellStream internal constructor(val process: Process, private val threads: List<Thread>) {
    val alive: Boolean
        get() = try { process.exitValue(); false } catch (e: IllegalThreadStateException) { true }

    fun kill() {
        runCatching { process.destroy() }
        runCatching { process.destroyForcibly() }
        threads.forEach { runCatching { it.interrupt() } }
    }
}

/**
 * Shell execution engine used by every module:
 *  - [sh]  : plain unprivileged shell via /system/bin/sh
 *  - [su]  : privileged shell (Magisk / KernelSU / APatch `su`), invocation form auto-probed
 *  - [stream] : long-running process with non-blocking asynchronous line callbacks
 */
object Shell {
    @Volatile private var suForm: Int = -1 // index of the working su invocation form
    @Volatile var lastError: String? = null

    /** MSI shell PATH (module 24 appends custom entries). */
    fun msiPath(): String {
        val custom = AppState.customPath.value.trim()
        val base = listOf(
            "/system/bin", "/system/xbin", "/vendor/bin", "/su/bin", "/sbin",
            System.getenv("PATH") ?: ""
        ).filter { it.isNotBlank() }.joinToString(":")
        return if (custom.isNotBlank()) "$base:$custom" else base
    }

    private val suForms: List<(String) -> List<String>> = listOf(
        { cmd -> listOf("su", "-c", cmd) },
        { cmd -> listOf("su", "0", "sh", "-c", cmd) },
        { cmd -> listOf("/system/bin/su", "-c", cmd) },
        { cmd -> listOf("/system/xbin/su", "-c", cmd) },
        { cmd -> listOf("/sbin/su", "-c", cmd) },
    )

    private fun suArgv(cmd: String): List<String> = suForms[maxOf(suForm, 0)](cmd)

    /** Blocking probe (called from IO dispatcher only). */
    private fun probeSu(): Boolean {
        if (suForm >= 0) return true
        for (i in suForms.indices) {
            val r = capture(suForms[i]("id"), emptyMap(), 10_000, quiet = true)
            if (r.ok && r.stdout.contains("uid=0")) { suForm = i; return true }
        }
        return false
    }

    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) { probeSu() }

    suspend fun sh(cmd: String, timeoutMs: Long = 20_000, env: Map<String, String> = emptyMap()): ShellResult =
        withContext(Dispatchers.IO) { capture(listOf("sh", "-c", cmd), env, timeoutMs) }

    suspend fun su(cmd: String, timeoutMs: Long = 20_000, env: Map<String, String> = emptyMap()): ShellResult =
        withContext(Dispatchers.IO) {
            if (!probeSu()) {
                lastError = "su: root access unavailable"
                ShellResult(126, "", "su: root access unavailable (is the device rooted & authorized?)")
            } else capture(suArgv(cmd), env, timeoutMs)
        }

    /** Prefer root; transparently degrade to unprivileged shell. */
    suspend fun auto(cmd: String, preferRoot: Boolean, timeoutMs: Long = 20_000): ShellResult {
        if (!preferRoot) return sh(cmd, timeoutMs)
        val r = su(cmd, timeoutMs)
        return if (r.exitCode != 126) r else sh(cmd, timeoutMs)
    }

    /** One-shot execution of a raw argv (used by the PRoot engine & ELF runner). */
    fun captureRaw(argv: List<String>, env: Map<String, String> = emptyMap(), timeoutMs: Long = 30_000): ShellResult =
        capture(argv, env, timeoutMs)

    private fun capture(argv: List<String>, env: Map<String, String>, timeoutMs: Long, quiet: Boolean = false): ShellResult {
        return try {
            val pb = ProcessBuilder(argv).redirectErrorStream(false)
            pb.environment().apply {
                if (!env.containsKey("PATH")) put("PATH", msiPath())
                env.forEach { (k, v) -> put(k, v) }
                if (!env.containsKey("TERM")) put("TERM", "xterm-256color")
            }
            val p = pb.start()
            val out = StringBuilder(); val err = StringBuilder()
            val t1 = thread(name = "msi-out") {
                runCatching {
                    BufferedReader(InputStreamReader(p.inputStream)).useLines { lines ->
                        lines.forEach { synchronized(out) { out.append(it).append('\n') } }
                    }
                }
            }
            val t2 = thread(name = "msi-err") {
                runCatching {
                    BufferedReader(InputStreamReader(p.errorStream)).useLines { lines ->
                        lines.forEach { synchronized(err) { err.append(it).append('\n') } }
                    }
                }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            var exited = false
            while (System.currentTimeMillis() < deadline) {
                try { p.exitValue(); exited = true; break } catch (e: IllegalThreadStateException) { Thread.sleep(25) }
            }
            if (!exited) {
                runCatching { p.destroyForcibly() }
                t1.join(500); t2.join(500)
                return ShellResult(-2, out.toString(), "timeout after ${timeoutMs}ms")
            }
            t1.join(1500); t2.join(1500)
            val code = runCatching { p.exitValue() }.getOrDefault(-1)
            ShellResult(code, out.toString(), err.toString())
        } catch (e: Exception) {
            if (!quiet) lastError = "${e.javaClass.simpleName}: ${e.message}"
            ShellResult(-1, "", "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Long-running stream: lines of stdout AND stderr are pushed asynchronously
     * from dedicated reader threads (non-blocking).
     */
    fun stream(cmd: String, root: Boolean, env: Map<String, String> = emptyMap(), onLine: (String) -> Unit): ShellStream {
        val argv: List<String> = if (root) {
            if (suForm >= 0) suArgv(cmd) else suForms[0](cmd)
        } else listOf("sh", "-c", cmd)
        val pb = ProcessBuilder(argv).redirectErrorStream(true)
        pb.environment().apply {
            if (!env.containsKey("PATH")) put("PATH", msiPath())
            env.forEach { (k, v) -> put(k, v) }
        }
        val p = pb.start()
        val t = thread(name = "msi-stream") {
            runCatching {
                BufferedReader(InputStreamReader(p.inputStream)).useLines { lines -> lines.forEach(onLine) }
            }
        }
        return ShellStream(p, listOf(t))
    }

    /** Locate an executable in PATH (plus common root directories). */
    fun which(bin: String): String? {
        val dirs = msiPath().split(':').filter { it.isNotBlank() }
        for (d in dirs) {
            val f = File(d, bin)
            if (f.isFile && f.canExecute()) return f.absolutePath
        }
        return null
    }

    /** Read a file directly when possible, falling back to `su cat` for protected paths. */
    suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val f = File(path)
        if (f.isFile && f.canRead()) runCatching { f.readText() }.getOrNull()
        else {
            val r = su("cat '$path'", 10_000)
            if (r.ok) r.stdout else null
        }
    }
}
