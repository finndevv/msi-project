package com.finndev.master.system.inspector.core

import android.os.Build
import java.io.File
import java.io.RandomAccessFile

/**
 * Native ELF binary parser (modules 20, 22, 76).
 * Parses ELF headers directly — no external dependency.
 */
object Elf {

    data class ElfInfo(
        val isElf: Boolean,
        val bits: Int,               // 32 / 64 / 0
        val endian: String,          // little / big
        val elfType: String,         // EXEC / DYN(PIE) / REL / CORE
        val machine: Int,
        val machineName: String,     // ARM / AArch64 / x86 / x86-64 / MIPS ...
        val entryPoint: Long,
        val programHeaders: Int,
        val sectionHeaders: Int,
        val interpreter: String?,    // PT_INTERP (dynamic linker)
        val file: File,
    ) {
        val matchesDevice: Boolean get() = Elf.matchesDevice(machine)
    }

    private fun machineName(m: Int): String = when (m) {
        0 -> "none"; 2 -> "SPARC"; 3 -> "x86"; 4 -> "68k"; 8 -> "MIPS"
        20 -> "PowerPC"; 21 -> "PPC64"; 40 -> "ARM"; 42 -> "SuperH"
        50 -> "IA-64"; 62 -> "x86-64"; 183 -> "AArch64 (ARM64)"
        243 -> "RISC-V"; else -> "unknown ($m)"
    }

    /** Device ABI -> expected ELF machine codes (module 76 arch matcher). */
    fun deviceMachineCodes(): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        Build.SUPPORTED_ABIS.forEach { abi ->
            when (abi) {
                "arm64-v8a" -> out += 183 to "AArch64 (arm64-v8a)"
                "armeabi-v7a", "armeabi" -> out += 40 to "ARM (armeabi-v7a)"
                "x86_64" -> out += 62 to "x86-64"
                "x86" -> out += 3 to "x86"
                "mips", "mips64" -> out += 8 to "MIPS"
            }
        }
        return out.distinctBy { it.first }
    }

    fun matchesDevice(machine: Int): Boolean = deviceMachineCodes().any { it.first == machine }

    fun abiOf(machine: Int): String = when (machine) {
        183 -> "arm64-v8a"; 40 -> "armeabi-v7a"; 62 -> "x86_64"; 3 -> "x86"; 8 -> "mips"; else -> "n/a"
    }

    fun parse(file: File): ElfInfo {
        val info = ElfInfo(false, 0, "", "", 0, "", 0, 0, 0, null, file)
        if (!file.isFile || file.length() < 64) return info
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(64)
            raf.readFully(header)
            if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() ||
                header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
            ) return info

            val bits = if (header[4] == 2.toByte()) 64 else 32
            val endian = if (header[5] == 2.toByte()) "big" else "little"
            val little = header[5] == 1.toByte()

            fun u16(off: Int): Int {
                val b0 = header[off].toInt() and 0xFF; val b1 = header[off + 1].toInt() and 0xFF
                return if (little) b0 or (b1 shl 8) else (b0 shl 8) or b1
            }
            fun u32(off: Int): Long {
                var v = 0L
                for (i in 0 until 4) {
                    val b = (header[off + i].toInt() and 0xFF).toLong()
                    v = if (little) v or (b shl (8 * i)) else (v shl 8) or b
                }
                return v
            }
            fun u64(off: Int): Long {
                var v = 0L
                for (i in 0 until 8) {
                    val b = header[off + i].toLong() and 0xFF
                    v = if (little) v or (b shl (8 * i)) else (v shl 8) or b
                }
                return v
            }

            val eType = u16(16)
            val machine = u16(18)
            val entry = if (bits == 64) u64(24) else u32(24)
            val phOff = if (bits == 64) u64(32) else u32(28)
            val shOff = if (bits == 64) u64(40) else u32(32)
            val phNum = u16(if (bits == 64) 56 else 44)
            val shNum = u16(if (bits == 64) 60 else 48)

            // Parse program headers to find PT_INTERP
            var interp: String? = null
            if (phOff in 1 until file.length() && phNum in 1..256) {
                val phSize = if (bits == 64) 56 else 32
                raf.seek(phOff)
                val ph = ByteArray(phSize)
                for (i in 0 until phNum) {
                    raf.readFully(ph)
                    val pType = (ph[0].toInt() and 0xFF) or ((ph[1].toInt() and 0xFF) shl 8) or
                            ((ph[2].toInt() and 0xFF) shl 16) or ((ph[3].toInt() and 0xFF) shl 24)
                    if (pType == 3) { // PT_INTERP
                        val pOffset: Long = if (bits == 64) {
                            var v = 0L; for (k in 0 until 8) v = v or ((ph[8 + k].toLong() and 0xFF) shl (8 * k)); v
                        } else {
                            var v = 0L; for (k in 0 until 4) v = v or ((ph[4 + k].toLong() and 0xFF) shl (8 * k)); v
                        }
                        if (pOffset in 1 until file.length()) {
                            raf.seek(pOffset)
                            val buf = ByteArray(256)
                            val n = raf.read(buf)
                            val end = (0 until n).firstOrNull { buf[it] == 0.toByte() } ?: n
                            interp = String(buf, 0, end, Charsets.UTF_8)
                            raf.seek(phOff + (i + 1L) * phSize)
                        }
                    }
                }
            }

            val typeStr = when (eType) {
                1 -> "REL (relocatable)"; 2 -> "EXEC (executable)"; 3 -> "DYN (shared/PIE)"; 4 -> "CORE"; else -> "type $eType"
            }
            return ElfInfo(true, bits, endian, typeStr, machine, machineName(machine), entry, phNum, shNum, interp, file)
        }
    }
}
