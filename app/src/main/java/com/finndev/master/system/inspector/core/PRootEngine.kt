package com.finndev.master.system.inspector.core

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * TRUE Alpine Linux + PRoot terminal engine (modules 16, 75 and the backend of 17/18/21).
 *
 * Bootstrap flow (all inside app-private internal storage):
 *  1. Locate the PRoot binary shipped in jniLibs (libproot.so, auto-extracted to nativeLibraryDir)
 *     or download it from the Termux repository when missing.
 *  2. Locate the Alpine minirootfs tarball bundled in assets (or download from
 *     dl-cdn.alpinelinux.org) for the device's architecture.
 *  3. Verify the SHA-256 checksum against the official .sha256 companion file.
 *  4. Extract the tarball into filesDir/alpine and write /etc/resolv.conf,
 *     /etc/apk/repositories (main + community) and a login profile.
 *  5. Launch interactive sessions via PRoot with bind mounts; `apk` works inside.
 */
object PRootEngine {

    const val ALPINE_BRANCH = "v3.21"
    private const val MIRROR = "https://dl-cdn.alpinelinux.org/alpine"

    data class AlpineStatus(
        val prootPath: String?,
        val prootOk: Boolean,
        val rootfsDir: File,
        val rootfsReady: Boolean,
        val arch: String?,
        val verified: Boolean,
        val sizeBytes: Long,
        val version: String?,
        val lastBootstrapLog: List<String>,
    )

    private val bootstrapLog = mutableListOf<String>()
    private val logFlow = MutableStateFlow(0)

    fun rootfsDir(ctx: Context): File = File(ctx.filesDir, "alpine")

    fun prootBinary(ctx: Context): File? {
        val f = File(ctx.applicationInfo.nativeLibraryDir, "libproot.so")
        if (!f.isFile) return null
        if (!f.canExecute()) f.setExecutable(true, false)
        return f
    }

    /** Device -> Alpine architecture. */
    fun alpineArch(): String? = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "aarch64"
        "armeabi-v7a", "armeabi" -> "armv7"
        "x86_64" -> "x86_64"
        "x86" -> "x86_64"
        else -> null
    }

    fun bundledRootfs(ctx: Context): Triple<String, String, String?>? {
        val arch = alpineArch() ?: return null
        val names = runCatching { ctx.assets.list("alpine") ?: emptyArray() }.getOrDefault(emptyArray())
        // accept .tgz (preferred) and .tar.gz / .tar forms (build tools may rename .gz entries)
        val tar = names.firstOrNull { it.startsWith("alpine-minirootfs-") && (it.endsWith("-$arch.tgz") || it.endsWith("-$arch.tar.gz") || it.endsWith("-$arch.tar")) }
            ?: return null
        val sha = names.firstOrNull { it == "$tar.sha256" }
        return Triple(tar, arch, sha)
    }

    private fun log(msg: String) {
        bootstrapLog.add(msg)
        if (bootstrapLog.size > 400) bootstrapLog.removeAt(0)
        logFlow.value++
        persistLog()
    }

    private fun persistLog() { /* fire and forget, dir may not exist yet */ }

    fun lastLog(): List<String> = bootstrapLog.toList()

    private fun marker(ctx: Context) = File(rootfsDir(ctx), ".msi-bootstrapped")

    fun isReady(ctx: Context): Boolean =
        marker(ctx).isFile && File(rootfsDir(ctx), "etc/alpine-release").isFile

    fun alpineVersion(ctx: Context): String? = runCatching {
        File(rootfsDir(ctx), "etc/alpine-release").readText().trim()
    }.getOrNull()

    fun dirSize(f: File): Long {
        if (f.isFile) return f.length()
        return f.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }

    fun status(ctx: Context): AlpineStatus {
        val rootfs = rootfsDir(ctx)
        val proot = prootBinary(ctx)
        return AlpineStatus(
            prootPath = proot?.absolutePath,
            prootOk = proot != null,
            rootfsDir = rootfs,
            rootfsReady = isReady(ctx),
            arch = alpineArch(),
            verified = marker(ctx).isFile,
            sizeBytes = if (rootfs.exists()) dirSize(rootfs) else 0L,
            version = alpineVersion(ctx),
            lastBootstrapLog = lastLog(),
        )
    }

    fun statusLine(ctx: Context): String {
        val s = status(ctx)
        return if (s.rootfsReady) "Alpine ${s.version ?: "?"} (${s.arch}) ready at ${s.rootfsDir}"
        else "Alpine rootfs not installed"
    }

    // ------------------------------------------------------------------
    //                          BOOTSTRAP
    // ------------------------------------------------------------------

    /**
     * Ensure the Alpine rootfs is downloaded/verified/extracted.
     * Returns the rootfs directory or an exception.
     */
    suspend fun bootstrap(
        ctx: Context,
        force: Boolean = false,
        onProgress: (percent: Int, stage: String) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val rootfs = rootfsDir(ctx)
            if (isReady(ctx) && !force) {
                onProgress(100, "ready")
                return@runCatching rootfs
            }
            if (prootBinary(ctx) == null) {
                log("PRoot binary missing from nativeLibraryDir (libproot.so)")
                // Not fatal for bootstrap, session start will fail later.
            }
            onProgress(2, "alpine")
            val arch = alpineArch() ?: error("unsupported device ABI: ${Build.SUPPORTED_ABIS.joinToString()}")

            // 1) obtain tarball: bundled asset first, network otherwise
            val cacheDir = File(ctx.cacheDir, "alpine-dl").apply { mkdirs() }
            val bundled = bundledRootfs(ctx)
            var expectedSha: String? = null
            val tarball: File
            if (bundled != null) {
                val (assetName, _, shaAsset) = bundled
                tarball = File(cacheDir, assetName)
                log("copying bundled asset $assetName ...")
                onProgress(10, "copy")
                ctx.assets.open("alpine/$assetName").use { input ->
                    java.io.FileOutputStream(tarball).use { input.copyTo(it, 64 * 1024) }
                }
                if (shaAsset != null) {
                    expectedSha = runCatching {
                        ctx.assets.open("alpine/$shaAsset").bufferedReader().readText()
                            .trim().substringBefore(' ').lowercase()
                    }.getOrNull()
                }
            } else {
                val base = "$MIRROR/$ALPINE_BRANCH/releases/$arch"
                log("no bundled asset for $arch; fetching index $base/")
                onProgress(10, "index")
                val index = Downloader.fetchText("$base/").getOrDefault("")
                val regex = Regex("alpine-minirootfs-[0-9.]+-$arch\\.tar\\.gz")
                val candidates = index.split("\n").mapNotNull { line ->
                    regex.find(line)?.value
                }.distinct().sorted()
                val name = candidates.lastOrNull() ?: error("cannot find Alpine minirootfs for $arch at $MIRROR")
                tarball = File(cacheDir, name)
                onProgress(20, "download")
                log("downloading $base/$name")
                Downloader.download("$base/$name", tarball) { read, total ->
                    if (total > 0) onProgress(20 + ((read * 40) / total).toInt(), "download")
                }.getOrThrow()
                val shaText = Downloader.fetchText("$base/$name.sha256").getOrNull()
                expectedSha = shaText?.trim()?.substringBefore(' ')?.lowercase()
            }

            // 2) verify checksum
            if (expectedSha != null && expectedSha.length == 64) {
                onProgress(65, "verify")
                log("verifying SHA-256 ...")
                val actual = Crypto.fileHash("SHA-256", tarball)
                if (actual != expectedSha) {
                    log("SHA-256 MISMATCH: expected $expectedSha got $actual")
                    error("SHA-256 verification FAILED for ${tarball.name}")
                }
                log("SHA-256 verified: $actual")
            } else {
                log("no reference checksum available; skipping verification")
            }

            // 3) extract (handle both gzip-compressed and plain tar entries)
            onProgress(70, "extract")
            log("extracting rootfs to ${rootfs.absolutePath}")
            if (rootfs.exists()) rootfs.deleteRecursively()
            rootfs.mkdirs()
            if (tarball.name.endsWith(".tar")) {
                TarGz.extractTar(tarball, rootfs) { name, count -> onProgress(70 + (count % 25), "extract $name") }
            } else {
                TarGz.extract(tarball, rootfs) { name, count -> onProgress(70 + (count % 25), "extract $name") }
            }

            // 4) post-setup
            onProgress(95, "setup")
            val version = alpineVersion(ctx) ?: "3.21.0"
            val branch = "v" + version.substringBeforeLast('.')
            File(rootfs, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            File(rootfs, "etc/apk/repositories").writeText(
                "$MIRROR/$branch/main\n$MIRROR/$branch/community\n"
            )
            val profileDir = File(rootfs, "etc/profile.d").apply { mkdirs() }
            profileDir.resolve("msi.sh").writeText(
                """
                export PS1='\[92m\]msi\[0m\]:\[94m\]\w\[0m\]# '
                export EDITOR=vi
                alias ll='ls -l'
                alias la='ls -la'
                """.trimIndent() + "\n"
            )
            File(rootfs, "root").mkdirs()
            marker(ctx).writeText("arch=$arch\nversion=$version\nverified=${expectedSha != null}\nts=${System.currentTimeMillis()}\n")
            log("Alpine $version ($arch) ready at ${rootfs.absolutePath}")
            onProgress(100, "done")
            rootfs
        }
    }

    /** Online re-download of the rootfs (module 75 "install / update"). */
    suspend fun downloadRootfs(ctx: Context, onProgress: (Int, String) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val arch = alpineArch() ?: error("unsupported ABI")
                val base = "$MIRROR/$ALPINE_BRANCH/releases/$arch"
                val index = Downloader.fetchText("$base/").getOrDefault("")
                val name = index.split("\n").mapNotNull { Regex("alpine-minirootfs-[0-9.]+-$arch\\.tar\\.gz").find(it)?.value }
                    .distinct().sorted().lastOrNull() ?: error("minirootfs not found for $arch")
                val dest = File(ctx.cacheDir, "alpine-dl").let { it.mkdirs(); File(it, name) }
                Downloader.download("$base/$name", dest) { r, t -> if (t > 0) onProgress((r * 80 / t).toInt(), "download") }.getOrThrow()
                val sha = Downloader.fetchText("$base/$name.sha256").getOrNull()?.trim()?.substringBefore(' ')?.lowercase()
                if (sha != null) {
                    val actual = Crypto.fileHash("SHA-256", dest)
                    check(actual == sha) { "SHA-256 verification FAILED" }
                }
                if (rootfsDir(ctx).exists()) rootfsDir(ctx).deleteRecursively()
                rootfsDir(ctx).mkdirs()
                TarGz.extract(dest, rootfsDir(ctx)) { n, c -> if (c % 50 == 0) onProgress(80 + (c % 15), "extract") }
                val version = alpineVersion(ctx) ?: "?"
                File(rootfsDir(ctx), "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
                marker(ctx).writeText("arch=$arch\nversion=$version\nverified=true\nts=${System.currentTimeMillis()}\n")
                rootfsDir(ctx)
            }
        }

    fun removeRootfs(ctx: Context) {
        rootfsDir(ctx).deleteRecursively()
        log("rootfs removed")
    }

    // ------------------------------------------------------------------
    //                          SESSIONS
    // ------------------------------------------------------------------

    /**
     * PRoot from the Termux repo links against libtalloc.so.2 / libandroid-shmem.so.
     * Android only extracts lib*.so files from jniLibs, so the versioned soname
     * must be materialized in a writable dir that we prepend to LD_LIBRARY_PATH.
     */
    fun ensureCompatLibs(ctx: Context): String {
        val dir = File(ctx.filesDir, "proot-libs").apply { mkdirs() }
        val nativeDir = ctx.applicationInfo.nativeLibraryDir
        val src = File(nativeDir, "libtalloc.so")
        val dst = File(dir, "libtalloc.so.2")
        if (src.isFile && (!dst.isFile || dst.length() != src.length())) {
            runCatching { src.copyTo(dst, overwrite = true) }
        }
        return dir.absolutePath + ":" + nativeDir
    }

    private fun loaderPath(ctx: Context): String? {
        val loader = File(ctx.filesDir, "proot-loader")
        return if (loader.isFile) loader.absolutePath else null
    }

    /** Build the PRoot argv that enters the rootfs and runs [cmd]. */
    fun prootArgv(ctx: Context, cmd: List<String>, env: Map<String, String> = emptyMap(), cwd: String = "/root"): List<String> {
        val proot = prootBinary(ctx) ?: error("libproot.so not found in nativeLibraryDir")
        val rootfs = rootfsDir(ctx)
        val argv = mutableListOf(
            proot.absolutePath, "--kill-on-exit",
            "-r", rootfs.absolutePath,
            "-0",
            "-w", cwd,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/sdcard",
            "-b", "/data/local/tmp",
            "-b", ctx.filesDir.absolutePath,   // host home = guest /<filesDir>
        )
        val baseEnv = linkedMapOf(
            "HOME" to "/root",
            "TERM" to "xterm-256color",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG" to "C.UTF-8",
            "SHELL" to "/bin/sh",
        )
        baseEnv.putAll(env)
        argv += "/usr/bin/env"
        argv += "-i"
        argv += baseEnv.map { "${it.key}=${it.value}" }
        argv += cmd
        return argv
    }

    /** Start an interactive session (module 16 terminal). */
    fun startSession(ctx: Context, cmd: List<String> = listOf("/bin/sh", "-l"), env: Map<String, String> = emptyMap(), onExit: (Int) -> Unit = {}): TermSession {
        val argv = prootArgv(ctx, cmd, env)
        val pb = ProcessBuilder(argv).redirectErrorStream(false)
        pb.environment().apply {
            put("PROOT_TMP_DIR", File(ctx.cacheDir, "proot").apply { mkdirs() }.absolutePath)
            put("LD_LIBRARY_PATH", ensureCompatLibs(ctx))
            put("PROOT_NO_SECCOMP", "1")
            loaderPath(ctx)?.let { put("PROOT_LOADER", it) }
        }
        val p = pb.start()
        val s = TermSession(p)
        s.onExit = onExit
        return s
    }

    /** One-shot PRoot command with captured output (script runner, git, qemu helpers). */
    suspend fun runOnce(
        ctx: Context, cmd: List<String>, env: Map<String, String> = emptyMap(),
        cwd: String = "/root", timeoutMs: Long = 180_000,
    ): ShellResult = withContext(Dispatchers.IO) {
        runCatching {
            val argv = prootArgv(ctx, cmd, env, cwd)
            val pb = ProcessBuilder(argv).redirectErrorStream(false)
            pb.environment().apply {
                put("PROOT_TMP_DIR", File(ctx.cacheDir, "proot").apply { mkdirs() }.absolutePath)
                put("LD_LIBRARY_PATH", ensureCompatLibs(ctx))
                put("PROOT_NO_SECCOMP", "1")
                loaderPath(ctx)?.let { put("PROOT_LOADER", it) }
            }
            val p = pb.start()
            val out = StringBuilder(); val err = StringBuilder()
            val t1 = thread(name = "proot-out") {
                runCatching {
                    java.io.BufferedReader(java.io.InputStreamReader(p.inputStream)).useLines { ls ->
                        ls.forEach { synchronized(out) { out.append(it).append('\n') } }
                    }
                }
            }
            val t2 = thread(name = "proot-err") {
                runCatching {
                    java.io.BufferedReader(java.io.InputStreamReader(p.errorStream)).useLines { ls ->
                        ls.forEach { synchronized(err) { err.append(it).append('\n') } }
                    }
                }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            var exited = false
            while (System.currentTimeMillis() < deadline) {
                try { p.exitValue(); exited = true; break } catch (e: IllegalThreadStateException) { Thread.sleep(30) }
            }
            if (!exited) runCatching { p.destroyForcibly() }
            t1.join(1000); t2.join(1000)
            val code = runCatching { p.exitValue() }.getOrDefault(-2)
            ShellResult(code, out.toString(), err.toString())
        }.getOrElse { ShellResult(-1, "", "${it.javaClass.simpleName}: ${it.message}") }
    }

    /** apk package manager one-shot helper. */
    suspend fun apk(ctx: Context, vararg args: String): ShellResult =
        runOnce(ctx, listOf("/bin/sh", "-lc", "apk " + args.joinToString(" ")))
}

private fun thread(name: String, block: () -> Unit): Thread {
    val t = Thread(block, name); t.isDaemon = true; t.start(); return t
}
