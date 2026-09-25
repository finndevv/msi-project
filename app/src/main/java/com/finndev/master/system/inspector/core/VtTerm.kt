package com.finndev.master.system.inspector.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val ESC = 27.toChar()   // 0x1B
private const val BEL = 7.toChar()    // 0x07
private const val DEL = 127.toChar()  // 0x7F

/**
 * Compact VT100/ANSI terminal model (module 16).
 * Handles: printable chars, CR/LF/BS/TAB/BEL, ESC [ cursor movement,
 * erase (J/K), SGR colors (basic 16 + bold), insert/delete lines & chars,
 * OSC title sequences (consumed), charset switches (consumed).
 */
class VtTerm(val cols: Int = 88, val maxLines: Int = 600) {

    class Cell(val ch: Char, val fg: Int, val bold: Boolean)

    var lines = mutableListOf<MutableList<Cell>>()
    private var cx = 0
    private var cy = 0

    /** bumped after each feed batch so Compose can recompose */
    val frame = MutableStateFlow(0)

    private var state = 0 // 0 ground, 1 esc, 2 csi, 3 osc, 4 esc-intermediate
    private val csiParams = StringBuilder()
    private var savedCx = 0
    private var savedCy = 0
    private var fg = 99
    private var bold = false
    var title: String = ""
        private set

    init {
        lines.add(mutableListOf())
    }

    private fun ensureRow(y: Int) {
        while (lines.size <= y) lines.add(mutableListOf())
    }

    private fun curRow(): MutableList<Cell> {
        ensureRow(cy)
        return lines[cy]
    }

    private fun blank() = Cell(' ', 99, false)

    private fun put(ch: Char) {
        val row = curRow()
        while (row.size < cx) row.add(blank())
        if (cx < row.size) row[cx] = Cell(ch, fg, bold) else row.add(Cell(ch, fg, bold))
        cx++
        if (cx >= cols) cx = cols - 1
    }

    private fun newline() {
        cy++
        ensureRow(cy)
        if (lines.size > maxLines) {
            val drop = lines.size - maxLines
            repeat(drop) { lines.removeAt(0) }
            cy -= drop
        }
        cx = 0
    }

    fun clearScreen() {
        lines.clear(); lines.add(mutableListOf()); cx = 0; cy = 0
    }

    fun feed(bytes: ByteArray) {
        val text = String(bytes, Charsets.UTF_8)
        for (c in text) {
            when (state) {
                0 -> when {
                    c == ESC -> state = 1
                    c == '\n' -> newline()
                    c == '\r' -> cx = 0
                    c == '\b' -> if (cx > 0) cx--
                    c == '\t' -> cx = ((cx / 8) + 1) * 8
                    c == BEL || c == DEL -> { /* ignore */ }
                    c.code >= 32 -> put(c)
                    else -> { /* other C0 ignored */ }
                }
                1 -> when (c) {
                    '[' -> { state = 2; csiParams.setLength(0) }
                    ']' -> { state = 3; csiParams.setLength(0) }
                    '(', ')', '*', '+' -> state = 4 // charset: consume one more
                    '7' -> { savedCx = cx; savedCy = cy; state = 0 }
                    '8' -> { cx = savedCx; cy = savedCy; state = 0 }
                    'D' -> { newline(); state = 0 }
                    'M' -> { if (cy > 0) cy--; state = 0 }
                    'c' -> { clearScreen(); state = 0 }
                    else -> state = 0
                }
                2 -> { // CSI
                    if (c.code in 0x40..0x7E) {
                        handleCsi(csiParams.toString(), c)
                        state = 0
                    } else csiParams.append(c)
                }
                3 -> { // OSC ... terminated by BEL or ESC
                    if (c == BEL) { title = csiParams.toString(); state = 0 }
                    else if (c == ESC) { title = csiParams.toString(); state = 0 }
                    else csiParams.append(c)
                }
                4 -> state = 0 // after charset indicator
            }
        }
        frame.value++
    }

    private fun handleCsi(params: String, cmd: Char) {
        val ps = params.split(';').map { it.toIntOrNull() ?: 0 }
        fun p(i: Int, def: Int) = ps.getOrElse(i) { def }
        when (cmd) {
            'A' -> cy = (cy - p(0, 1)).coerceAtLeast(0)
            'B' -> cy += p(0, 1)
            'C' -> cx = (cx + p(0, 1)).coerceAtMost(cols - 1)
            'D' -> cx = (cx - p(0, 1)).coerceAtLeast(0)
            'G' -> cx = (p(0, 1) - 1).coerceIn(0, cols - 1)
            'H', 'f' -> { cy = (p(0, 1) - 1).coerceAtLeast(0); cx = (p(1, 1) - 1).coerceIn(0, cols - 1) }
            'J' -> when (p(0, 0)) {
                0 -> { val row = curRow(); while (row.size > cx) row.removeAt(row.size - 1); for (i in cy + 1 until lines.size) lines[i].clear() }
                1 -> { for (i in 0 until cy) lines[i].clear(); val row = curRow(); while (row.isNotEmpty()) row.removeAt(row.size - 1) }
                else -> clearScreen()
            }
            'K' -> when (p(0, 0)) {
                0 -> { val row = curRow(); while (row.size > cx) row.removeAt(row.size - 1) }
                1 -> { val row = curRow(); repeat(cx.coerceAtMost(row.size)) { row[it] = blank() } }
                else -> curRow().clear()
            }
            'L' -> { repeat(p(0, 1)) { lines.add(cy.coerceIn(0, lines.size), mutableListOf()) } }
            'M' -> { repeat(p(0, 1)) { if (lines.size > 1 && cy < lines.size) lines.removeAt(cy) } }
            'P', 'X' -> { val row = curRow(); repeat(p(0, 1)) { if (cx < row.size) row.removeAt(cx) } }
            '@' -> { val row = curRow(); repeat(p(0, 1)) { row.add(cx.coerceAtMost(row.size), blank()) } }
            's' -> { savedCx = cx; savedCy = cy }
            'u' -> { cx = savedCx; cy = savedCy }
            'm' -> { // SGR
                for (v in ps) {
                    when (v) {
                        0 -> { fg = 99; bold = false }
                        1 -> bold = true
                        2, 21, 22 -> bold = false
                        30 -> fg = 1; 31 -> fg = 2; 32 -> fg = 3; 33 -> fg = 4
                        34 -> fg = 5; 35 -> fg = 6; 36 -> fg = 7; 37 -> fg = 8
                        90 -> fg = 9; 91 -> fg = 10; 92 -> fg = 11; 93 -> fg = 12
                        94 -> fg = 13; 95 -> fg = 14; 96 -> fg = 15; 97 -> fg = 16
                        39 -> fg = 99
                    }
                }
            }
            'h', 'l', 'n', 'r', 'd' -> { /* modes / reports / scroll region: safely ignored */ }
            else -> { /* unknown CSI: ignore */ }
        }
    }

    companion object {
        /** Map cell color index -> ARGB. */
        fun colorOf(idx: Int): Long = when (idx) {
            1 -> 0xFFCC5555; 2 -> 0xFFDD4A4A; 3 -> 0xFF50FA7B; 4 -> 0xFFF1FA8C
            5 -> 0xFFBD93F9; 6 -> 0xFFFF79C6; 7 -> 0xFF8BE9FD; 8 -> 0xFFBBBBBB
            9 -> 0xFF665C54; 10 -> 0xFFFF6E6E; 11 -> 0xFF69FF94; 12 -> 0xFFFFFFA9
            13 -> 0xFFAFFFFF; 14 -> 0xFFFF92DF; 15 -> 0xFFFFFFA8; 16 -> 0xFFFFFFFF
            else -> 0xFFD6FFE2 // default
        }
    }
}

/**
 * Singleton interactive terminal shared by module 16 (and the QS tile / deep links).
 * Survives navigation & configuration changes.
 */
object TerminalCenter {
    val term = VtTerm()
    val state = MutableStateFlow(State.IDLE)
    val session = MutableStateFlow<TermSession?>(null)
    /** Plain text mirror of everything the session printed (module 28 export). */
    val transcript = mutableListOf<String>()
    var lastError: String? = null

    enum class State { IDLE, BOOTSTRAPPING, RUNNING, EXITED, ERROR }

    fun start(ctx: android.content.Context, onDone: () -> Unit = {}) {
        if (session.value?.alive == true) return
        state.value = State.BOOTSTRAPPING
        term.clearScreen()
        term.feed("[MSI] bootstrapping Alpine Linux rootfs ...\r\n".toByteArray())
        CoroutineScope(Dispatchers.Main).launch {
            PRootEngine.bootstrap(ctx).fold(
                onSuccess = {
                    term.feed("[MSI] starting PRoot session ...\r\n".toByteArray())
                    realStart(ctx)
                    onDone()
                },
                onFailure = { e ->
                    lastError = e.message
                    state.value = State.ERROR
                    term.feed("[MSI] bootstrap failed: ${e.message}\r\n".toByteArray())
                    term.feed("[MSI] falling back to Android system shell (no Alpine).\r\n".toByteArray())
                    fallbackShell(ctx)
                    onDone()
                }
            )
        }
    }

    private fun realStart(ctx: android.content.Context) {
        runCatching {
            val s = PRootEngine.startSession(ctx) { code ->
                state.value = State.EXITED
                term.feed("\r\n[MSI] session exited (code $code)\r\n".toByteArray())
            }
            attach(s)
            state.value = State.RUNNING
        }.onFailure { e ->
            lastError = e.message
            state.value = State.ERROR
            term.feed("[MSI] PRoot failed: ${e.message} — falling back to system shell.\r\n".toByteArray())
            fallbackShell(ctx)
        }
    }

    /** Fallback: plain /system/bin/sh so the console stays usable without rootfs. */
    private fun fallbackShell(ctx: android.content.Context) {
        runCatching {
            val pb = ProcessBuilder("sh").redirectErrorStream(true)
            pb.environment()["TERM"] = "xterm-256color"
            pb.environment()["PATH"] = Shell.msiPath()
            val s = TermSession(pb.start())
            attach(s)
            s.onExit = { code ->
                state.value = State.EXITED
                term.feed("\r\n[MSI] shell exited (code $code)\r\n".toByteArray())
            }
            state.value = State.RUNNING
        }.onFailure {
            term.feed("[MSI] unable to start any shell: ${it.message}\r\n".toByteArray())
        }
    }

    private fun attach(s: TermSession) {
        session.value = s
        s.onOutput { bytes ->
            term.feed(bytes)
            synchronized(transcript) {
                String(bytes, Charsets.UTF_8).split('\n').dropLast(1).forEach { transcript.add(it.trimEnd('\r')) }
                while (transcript.size > 4000) transcript.removeAt(0)
            }
        }
    }

    fun send(line: String) {
        val s = session.value ?: return
        s.writeLine(line)
        synchronized(transcript) { transcript.add(line) }
    }

    fun sendRaw(bytes: ByteArray) { session.value?.write(bytes) }

    /** Launch a custom argv through PRoot inside the shared terminal (modules 18 / 21). */
    fun startCustom(ctx: android.content.Context, argv: List<String>, label: String) {
        stop()
        state.value = State.BOOTSTRAPPING
        term.clearScreen()
        term.feed("[MSI] $label\r\n".toByteArray())
        CoroutineScope(Dispatchers.Main).launch {
            PRootEngine.bootstrap(ctx).fold(
                onSuccess = {
                    runCatching {
                        val s = PRootEngine.startSession(ctx, argv) { code ->
                            state.value = State.EXITED
                            term.feed("\r\n[MSI] process exited (code $code)\r\n".toByteArray())
                        }
                        attach(s)
                        state.value = State.RUNNING
                    }.onFailure { e ->
                        state.value = State.ERROR
                        term.feed("[MSI] failed: ${e.message}\r\n".toByteArray())
                    }
                },
                onFailure = { e ->
                    state.value = State.ERROR
                    term.feed("[MSI] bootstrap failed: ${e.message}\r\n".toByteArray())
                }
            )
        }
    }

    fun stop() {
        session.value?.kill()
        session.value = null
        state.value = State.EXITED
    }
}
