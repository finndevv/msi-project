package com.finndev.master.system.inspector.core

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Crypto toolbox: hashing, secure randomness, AES-GCM vault crypto (modules 33, 47, 55). */
object Crypto {

    private val HEX = "0123456789abcdef"

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            sb.append(HEX[i ushr 4]).append(HEX[i and 0x0F])
        }
        return sb.toString()
    }

    fun unhex(s: String): ByteArray {
        val clean = s.replace(" ", "").replace(":", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) out[i] = ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        return out
    }

    fun sha256(bytes: ByteArray): String = hash("SHA-256", bytes)
    fun md5(bytes: ByteArray): String = hash("MD5", bytes)
    fun sha1(bytes: ByteArray): String = hash("SHA-1", bytes)

    private fun hash(algo: String, bytes: ByteArray): String =
        hex(MessageDigest.getInstance(algo).digest(bytes))

    /** Streamed file hashing with progress (module 55). */
    fun fileHash(algo: String, file: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): String {
        val md = MessageDigest.getInstance(algo)
        FileInputStream(file).use { input ->
            val buf = ByteArray(256 * 1024)
            var read = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                read += n
                onProgress(read, file.length())
            }
        }
        return hex(md.digest())
    }

    private val rng = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    /** Random token from a charset (module 47). */
    fun randomToken(length: Int, charset: String): String {
        if (charset.isEmpty()) return ""
        val sb = StringBuilder(length)
        for (i in 0 until length) sb.append(charset[rng.nextInt(charset.length)])
        return sb.toString()
    }

    fun randomMac(): String {
        val b = randomBytes(6)
        b[0] = ((b[0].toInt() and 0xFE) or 0x02).toByte() // locally administered, unicast
        return (0 until 6).joinToString(":") { String.format("%02X", b[it]) }
    }

    // ---------- AES-GCM vault (module 33) ----------

    private const val PBKDF2_ITERS = 120_000
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12

    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = randomBytes(IV_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val enc = cipher.doFinal(plaintext)
        return iv + enc
    }

    fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        require(blob.size > IV_LEN) { "blob too short" }
        val iv = blob.copyOfRange(0, IV_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(blob.copyOfRange(IV_LEN, blob.size))
    }
}
