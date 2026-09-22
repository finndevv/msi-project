package com.finndev.master.system.inspector.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Minimal pure-Kotlin tar + gzip implementation (modules 57, 64, 75).
 * Supports ustar headers (incl. prefix field) and GNU long-name entries,
 * which is sufficient for the Alpine minirootfs tarballs and MSI archives.
 */
object TarGz {

    private const val BLOCK = 512

    private fun octal(field: ByteArray): Long {
        var v = 0L
        for (b in field) {
            val c = b.toInt() and 0xFF
            if (c == 0) break
            if (c == ' '.code || c == 0) continue
            if (c in '0'.code..'7'.code) v = v * 8 + (c - '0'.code)
        }
        return v
    }

    private fun writeOctal(field: ByteArray, value: Long) {
        java.util.Arrays.fill(field, 0.toByte())
        val s = java.lang.Long.toOctalString(value)
        val bytes = s.toByteArray()
        val start = field.size - bytes.size
        System.arraycopy(bytes, 0, field, start, bytes.size)
    }

    /** Extract a .tar.gz into destDir. onEntry(name, count) for progress. */
    fun extract(tarGz: File, destDir: File, onEntry: (String, Int) -> Unit = { _, _ -> }) {
        destDir.mkdirs()
        GZIPInputStream(FileInputStream(tarGz), 64 * 1024).use { gz ->
            extractFromStream(gz, destDir, onEntry)
        }
    }

    /** Extract a plain (uncompressed) .tar into destDir. */
    fun extractTar(tar: File, destDir: File, onEntry: (String, Int) -> Unit = { _, _ -> }) {
        destDir.mkdirs()
        java.io.BufferedInputStream(FileInputStream(tar), 256 * 1024).use { input ->
            extractFromStream(input, destDir, onEntry)
        }
    }

    private fun extractFromStream(inBuf: java.io.InputStream, destDir: File, onEntry: (String, Int) -> Unit) {
        var count = 0
            var pendingLongName: String? = null
            while (true) {
                val header = ByteArray(BLOCK)
                if (!readFully(inBuf, header)) break
                if (header.all { it == 0.toByte() }) continue // zero block
                val name = buildString {
                    append(String(header, 0, 100, Charsets.UTF_8).trimEnd('\u0000'))
                    val prefix = String(header, 345, 155, Charsets.UTF_8).trimEnd('\u0000')
                    if (prefix.isNotEmpty()) insert(0, "$prefix/")
                }.trim('\u0000', ' ', '/')
                val size = octal(header.copyOfRange(124, 136))
                val typeFlag = header[156]
                val mode = octal(header.copyOfRange(100, 108))

                when (typeFlag) {
                    'L'.code.toByte() -> { // GNU long name -> content is the next entry's name
                        val nb = ByteArray(size.toInt())
                        readFully(inBuf, nb)
                        pendingLongName = String(nb, Charsets.UTF_8).trimEnd('\u0000')
                        skipPadding(inBuf, size)
                        continue
                    }
                    'K'.code.toByte() -> { // GNU long link name -> skip
                        skipBytes(inBuf, size); skipPadding(inBuf, size); continue
                    }
                }

                val target = File(destDir, (pendingLongName ?: name).let { n ->
                    n.removePrefix("./").removePrefix("/")
                })
                pendingLongName = null
                if (name.isBlank()) { skipBytes(inBuf, size); skipPadding(inBuf, size); continue }

                when (typeFlag) {
                    '0'.code.toByte(), 0.toByte() -> {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            var left = size
                            val buf = ByteArray(64 * 1024)
                            while (left > 0) {
                                val n = inBuf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                if (n < 0) throw IOException("unexpected EOF in tar entry $name")
                                out.write(buf, 0, n); left -= n
                            }
                        }
                        if (mode > 0) runCatching { target.setExecutable(mode and 0x111 != 0L) }
                    }
                    '5'.code.toByte() -> target.mkdirs()
                    '2'.code.toByte() -> { // symlink: store link target; android can't create symlinks w/o root -> keep a record file
                        val lb = ByteArray(size.toInt())
                        readFully(inBuf, lb)
                        val link = String(lb, Charsets.UTF_8).trimEnd('\u0000')
                        target.parentFile?.mkdirs()
                        target.writeText("SYMLINK -> $link")
                    }
                    else -> skipBytes(inBuf, size)
                }
                skipPadding(inBuf, size)
                count++
                if (count % 250 == 0 || count < 10) onEntry(name, count)
            }
            onEntry("done", count)
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return off == 0
            off += n
        }
        return true
    }

    private fun skipBytes(input: java.io.InputStream, n: Long) {
        var left = n
        val buf = ByteArray(64 * 1024)
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) break
            left -= r
        }
    }

    private fun skipPadding(input: java.io.InputStream, size: Long) {
        val rem = size % BLOCK
        if (rem != 0L) skipBytes(input, BLOCK - rem)
    }

    // ---------- creation ----------

    private fun entryHeader(name: String, size: Long, typeFlag: Byte): ByteArray {
        val h = ByteArray(BLOCK)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        require(nameBytes.size <= 100) { "tar name too long: $name" }
        System.arraycopy(nameBytes, 0, h, 0, nameBytes.size)
        writeOctal(h.copyOfRange(100, 108), 0b111101101) // 0755
        writeOctal(h.copyOfRange(108, 116), 0)
        writeOctal(h.copyOfRange(116, 124), 0)
        writeOctal(h.copyOfRange(124, 136), size)
        writeOctal(h.copyOfRange(136, 148), System.currentTimeMillis() / 1000)
        java.util.Arrays.fill(h, 148, 156, ' '.code.toByte())
        h[156] = typeFlag
        val magic = "ustar\u000000".toByteArray()
        System.arraycopy(magic, 0, h, 257, magic.size)
        // checksum
        var sum = 0L
        for (b in h) sum += (b.toInt() and 0xFF)
        writeOctal(h.copyOfRange(148, 156), sum)
        val chk = java.lang.Long.toOctalString(sum).toByteArray()
        System.arraycopy(chk, 0, h, 148 + (6 - chk.size.coerceAtMost(6)), chk.size.coerceAtMost(6))
        h[155] = ' '.code.toByte()
        return h
    }

    private fun writeEntry(out: java.io.OutputStream, file: File, arcName: String) {
        if (file.isDirectory) {
            out.write(entryHeader(arcName.trimEnd('/') + "/", 0, '5'.code.toByte()))
            file.listFiles()?.sortedBy { it.name }?.forEach { writeEntry(out, it, "$arcName/${it.name}") }
        } else {
            out.write(entryHeader(arcName, file.length(), '0'.code.toByte()))
            FileInputStream(file).use { input ->
                val buf = ByteArray(64 * 1024)
                var left = file.length()
                while (left > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n <= 0) break
                    out.write(buf, 0, n); left -= n
                }
            }
            val rem = file.length() % BLOCK
            if (rem != 0L) out.write(ByteArray((BLOCK - rem).toInt()))
        }
    }

    /** Create a .tar.gz from a directory or single file. */
    fun createTarGz(source: File, outFile: File, onProgress: (files: Int) -> Unit = { }) {
        val count = intArrayOf(0)
        GZIPOutputStream(FileOutputStream(outFile), 64 * 1024).use { gz ->
            val name = if (source.isDirectory) source.name else source.name
            writeEntry(gz, source, name)
            gz.write(ByteArray(BLOCK * 2)) // end-of-archive
            onProgress(count[0])
        }
    }

    // ---------- zip helpers (modules 53, 57, 64) ----------

    fun zip(files: Map<String, File>, outFile: File) {
        java.util.zip.ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            files.forEach { (entryName, f) ->
                zos.putNextEntry(java.util.zip.ZipEntry(entryName.replace("//", "/")))
                FileInputStream(f).use { input -> input.copyTo(zos, 64 * 1024) }
                zos.closeEntry()
            }
        }
    }

    fun unzip(zipFile: File, destDir: File, onEntry: (String, Int) -> Unit = { _, _ -> }) {
        java.util.zip.ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry; var i = 0
            while (entry != null) {
                val target = File(destDir, entry.name)
                if (entry.isDirectory) target.mkdirs()
                else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { zis.copyTo(it) }
                }
                zis.closeEntry(); i++
                onEntry(entry.name, i)
                entry = zis.nextEntry
            }
        }
    }
}
