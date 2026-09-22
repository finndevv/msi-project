package com.finndev.master.system.inspector.core

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * A live interactive process: stdout/stderr are read asynchronously on
 * dedicated threads (non-blocking) and pushed to registered listeners as raw
 * byte chunks so the VT100 parser can consume escape sequences directly.
 */
class TermSession(val process: Process) {

    private val listeners = mutableListOf<(ByteArray) -> Unit>()

    @Volatile var onExit: ((Int) -> Unit)? = null
    @Volatile var startedAt: Long = System.currentTimeMillis()

    private val outThread = thread(name = "msi-term-out", isDaemon = true) {
        runCatching {
            val input = process.inputStream
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) dispatch(buf.copyOf(n))
            }
        }
    }

    private val errThread = thread(name = "msi-term-err", isDaemon = true) {
        runCatching {
            val input = process.errorStream
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) dispatch(buf.copyOf(n))
            }
        }
    }

    private val waitThread = thread(name = "msi-term-wait", isDaemon = true) {
        runCatching { val code = process.waitFor(); onExit?.invoke(code) }
    }

    val alive: Boolean
        get() = try { process.exitValue(); false } catch (e: IllegalThreadStateException) { true }

    private fun dispatch(bytes: ByteArray) {
        synchronized(listeners) { listeners.toList() }.forEach { l ->
            runCatching { l(bytes) }
        }
    }

    fun onOutput(listener: (ByteArray) -> Unit) {
        synchronized(listeners) { listeners += listener }
    }

    val stdin: OutputStream get() = process.outputStream

    fun write(bytes: ByteArray) {
        runCatching { process.outputStream.write(bytes); process.outputStream.flush() }
    }

    fun writeLine(line: String) = write((line + "\n").toByteArray())

    fun sendKey(code: String) = write(code.toByteArray(Charsets.ISO_8859_1))

    fun kill() {
        runCatching { process.destroy() }
        runCatching { process.destroyForcibly() }
    }
}
