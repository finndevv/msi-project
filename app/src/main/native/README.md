# MSI native components — build from source

This directory replaces the prebuilt binaries (`jniLibs/**/*.so` and
`assets/proot/loader-*`) that shipped with earlier MSI packages. Everything
is compiled by the normal Gradle build through CMake (`CMakeLists.txt`,
wired in `app/build.gradle.kts` via `externalNativeBuild`), so no binary
blobs are distributed in the source tree — required for F-Droid inclusion.

## Components

| Artifact (packaged) | Upstream project | Version | License | Source dir |
|---|---|---|---|---|
| `libproot.so` (PIE executable) | PRoot — Termux fork https://github.com/termux/proot | `v5.1.107.92` | GPL-2.0 | `proot/` |
| `libproot-loader.so` (static executable) | same as above (`src/loader/`) | `v5.1.107.92` | GPL-2.0 | `proot/` |
| `libtalloc.so` (soname `libtalloc.so.2`) | talloc https://www.samba.org/ftp/talloc/ | `2.4.3` | LGPL-3.0 | `talloc/` |
| `libandroid-shmem.so` | libandroid-shmem https://github.com/termux/libandroid-shmem | `v0.7` | BSD-3-Clause | `android-shmem/` |

## Provenance and integrity

Archives downloaded from the official upstream locations and verified
against the SHA-256 digests published in the Termux package recipes
(`termux-packages`):

| Archive | SHA-256 |
|---|---|
| `proot-v5.1.107.92.zip` (github.com/termux/proot) | `29385d1ddb619a9c4449ab512bfd55032034b22f724ddf98fc95ff300ea32135` |
| `talloc-2.4.3.tar.gz` (samba.org) | `dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd` |
| `libandroid-shmem-v0.7.tar.gz` (github.com/termux/libandroid-shmem) | `1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867` |

These are exactly the versions the previously shipped prebuilt binaries were
built from (the PRoot binary identifies itself as `5.1.107.92`; dependency
sonames `libtalloc.so.2` / `libandroid-shmem.so` match `libproot.so`'s
DT_NEEDED entries). The vendored sources are unmodified with the exceptions
listed under "Deviations from upstream" below. The only added files are:

- `CMakeLists.txt` — build orchestration,
- `talloc-config/config.h` — bionic feature set (upstream generates it with waf),
- `proot-config/build.h` — feature probes (`HAVE_PROCESS_VM`,
  `HAVE_SECCOMP_FILTER`), statically replicated because both features exist
  in bionic since API 23/21 respectively,
- `shmem-include/sys/shm.h` — forwarding header that exposes the vendored
  android-shmem `shm.h` as `<sys/shm.h>` (same effect as the Termux
  `libandroid-shmem` package installing it into the toolchain include tree;
  bionic has no SysV shm support of its own),
- `loader-info-portable.awk` — portable mirror of PRoot's
  `src/loader/loader-info.awk` (the upstream script uses gawk's `strtonum()`
  extension, which is not available in mawk/busybox awk on build machines).

## Deviations from upstream (build-level, kept minimal)

1. `android-shmem/shmem.c`: added `#include <fcntl.h>` (one line, marked with
   a comment). `open()` is used but never declared by the v0.7 include set;
   clang >= 16 treats implicit function declarations as hard errors, so the
   reference binaries could only have been built with an equivalent local
   fix or an older compiler.
2. `-D_PATH_TMP="/data/data/com.termux/files/usr/tmp"` is passed when
   compiling android-shmem: `_PATH_TMP` does not exist in the NDK bionic
   sysroot, and the reference binary embeds exactly this string (verified
   against `libandroid-shmem.so` from the previous release package).
3. `-Wno-error=implicit-function-declaration` for proot: the v5.1.107.92 tag
   ships `extension/ashmem_memfd/ashmem_memfd.c` with two implicit
   declarations (strcmp/memset); older toolchains warned, clang >= 16 errors.
4. `-Wl,--as-needed` on all link lines: reproduces the DT_NEEDED sets of the
   reference binaries (the NDK r29 clang driver injects `-ldl`, which the
   r28-based reference builds did not carry).

Build output was verified against the previous prebuilt binaries with NDK
r29 for arm64-v8a / armeabi-v7a / x86_64: identical ELF types, interpreters,
SONAMEs, DT_NEEDED dependency sets, loader load addresses and symbol
exports.

## Build mapping (Termux recipe -> CMake)

| Termux (`termux-packages`) | This build |
|---|---|
| `make -C src PROOT_WITH_LIBANDROID_SHMEM=true` | `WITH_LIBANDROID_SHMEM` define + link `talloc`/`android-shmem` targets |
| `CPPFLAGS += -DARG_MAX=131072 -DVERSION="5.1.107.92"` | same compile definitions |
| `PROOT_UNBUNDLE_LOADER=$PREFIX/libexec/proot` | same define (termux default path, inert here — the app always passes `PROOT_LOADER` env) |
| loader flags `-fPIC -ffreestanding` + `-static -nostdlib -Wl,-Ttext=<arch addr>,--build-id=none,-z,noexecstack` (+ `--rosegment` when supported) | identical, in the `proot-loader` custom command; `LOADER_ADDRESS` per ABI taken from `proot/src/arch.h` |
| arm64 `loader-info.c` generation (`readelf -s loader \| awk -f loader-info.awk`) | identical generation from the freshly built loader |
| talloc waf cross-compile | `talloc.c` compiled directly with `talloc-config/config.h` |
| libandroid-shmem `Makefile` (`-std=c11`, version script, `-llog -landroid`) | identical |

## Packaging notes

- PRoot and its loader are **executables**, renamed `lib*.so` so that Android
  packages/extracts them into `nativeLibraryDir`, the only location from
  which an app may `exec()` on modern Android. The loader used to be shipped
  in `assets/proot/` and extracted to `filesDir` at startup; since targetSdk
  29+ SELinux blocks executing files from app-writable storage, the loader is
  now packaged in `jniLibs` (`libproot-loader.so`) and `PRootEngine` points
  `PROOT_LOADER` directly at `nativeLibraryDir`. The legacy asset path is
  still honoured as a fallback.
- `libtalloc.so` keeps the upstream soname `libtalloc.so.2`; at runtime
  `PRootEngine.ensureCompatLibs()` materializes it under that name in a
  writable dir prepended to `LD_LIBRARY_PATH` (Android only extracts
  `lib*.so` names from jniLibs).
- 32-bit guest support on 64-bit hosts (`loader-m32`) is intentionally not
  built: a 64-bit NDK toolchain cannot produce it, the original prebuilt
  binaries did not contain it, and the app only enters same-ABI rootfs images.

## ABI coverage

`arm64-v8a`, `armeabi-v7a`, `x86_64` — identical to the prebuilt set.
