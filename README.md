# MSI — Ana Sistem Denetçisi / Master System Inspector v4.0.0

Enterprise-grade Android system toolbox with **78 fully implemented modules**, a **true Alpine
Linux PRoot terminal**, **5-language instant localization** (TR / ES / EN / FR / IT) and
**3 live UI themes** (Material 3 / Hacker-Cyberpunk / Classic Utility).

| | |
|---|---|
| Application ID | `com.finndev.master.system.inspector` |
| Version | 4.0.0 (versionCode 40000) |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 (Android 15+) |
| UI | Jetpack Compose + Material 3 (single-activity, modular) |
| Terminal engine | Alpine Linux 3.21 minirootfs + PRoot (bundled, SHA-256 verified) |

## ⚠️ About the package name

The requested id `com.finndev-master-system-inspector` contains hyphens. Android application
ids must be valid Java package names (letters, digits, underscores, dots only) — AAPT2 rejects
hyphens. The closest valid form is used instead: **`com.finndev.master.system.inspector`**.

## Build

### Android Studio
Open the project folder, let it sync, then **Build ▸ Build Bundle(s)/APK(s) ▸ Build APK(s)**.

### Command line
```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```
Requires JDK 17 and an Android SDK with `platforms;android-35` + `build-tools;35.0.0`
(create `local.properties` with `sdk.dir=/path/to/android-sdk` if needed).

The **release keystore is included** (`keystore/msi-release.keystore`) so the shipped APK
signature can be reproduced:
- store/key password: `msi2024master`, alias: `msi`

## Permissions requested on cold start (before any dashboard)
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (≤ Android 12)
- `MANAGE_EXTERNAL_STORAGE` (Android 11+, "All files access")
- `POST_NOTIFICATIONS` (Android 13+)
- later, per-module: location (Wi-Fi analyzer), camera (QR scanner)

## The 78 modules

| # | Module | # | Module | # | Module |
|---|---|---|---|---|---|
| 1 | Advanced Root Auditor | 27 | Quick Command Macro Builder | 53 | APK Backup & Extractor |
| 2 | SELinux Mode Switcher | 28 | Terminal Output Log Exporter | 54 | Deep Storage & Cache Cleaner |
| 3 | Build.prop Live Editor | 29 | Offline Markdown Notes | 55 | ISO & Binary Hash Verifier |
| 4 | Init.d Script Runner | 30 | QR Code & Barcode Scanner | 56 | Storage Hog Hunter |
| 5 | System Partition Remounter | 31 | Base64/Hex/Binary Encoder | 57 | Zip, Tar & Gz Manager |
| 6 | CPU Governor Profiler | 32 | JSON & XML Formatter | 58 | Download Queue & Tracker |
| 7 | CPU Hotplug & Freq Limiter | 33 | Encrypted Password Manager | 59 | Clipboard History & Snippets |
| 8 | Low Memory Killer Tweaker | 34 | LAN IP & MAC Scanner | 60 | Diagnostic Report Exporter |
| 9 | Battery Health & Charging | 35 | Port Scanner (TCP/UDP) | 61 | System Font Customizer |
| 10 | Screen Resolution & DPI | 36 | Advanced Ping Monitor | 62 | Bootanimation Customizer |
| 11 | Bloatware App Freezer | 37 | Custom DNS Enforcer | 63 | Quick Settings Tile |
| 12 | Live Sensors Streamer | 38 | Firewall Rule Manager | 64 | Crash Log Zip Packager |
| 13 | Kernel Log (dmesg) Reader | 39 | Socket Inspector | 65 | Hardware Profiler |
| 14 | Boot Stage Analyzer | 40 | Bandwidth Speed Test | 66 | RAM & Swap Monitor |
| 15 | CPU & RAM Benchmark | 41 | MAC Address Spoofer | 67 | Package Name Inspector |
| 16 | **True Alpine PRoot Terminal** | 42 | Wi-Fi Signal Analyzer | 68 | Process Manager |
| 17 | Termux & Git Cloner | 43 | SSL/TLS Inspection | 69 | Logcat Live Stream |
| 18 | QEMU Multi-Arch Emulator | 44 | Packet/Traffic Logger | 70 | Mounts & Disk Usage |
| 19 | Bash Script Runner | 45 | Hotspot Client Inspector | 71 | CPU Thermal Inspector |
| 20 | Native ELF Runner | 46 | Captcha Test Bench | 72 | Timer & Stopwatch |
| 21 | Python & Lua Interpreter | 47 | Password & Token Generator | 73 | Multi-Language Engine |
| 22 | ELF Deep Analyzer | 48 | Sensitive File Shredder | 74 | Theme Engine |
| 23 | File Permission Manager | 49 | Regex Workbench | 75 | Alpine Bootstrap Status |
| 24 | PATH Manager | 50 | Color Picker HEX/RGB | 76 | ELF Architecture Matcher |
| 25 | Busybox Validator | 51 | ValiantCore ISO Downloader | 77 | Update Checker |
| 26 | Emergency Recovery Console | 52 | HTTP File Sharing Server | 78 | Master Dashboard |

## Terminal engine internals (module 16)

- `libproot.so` (+ `libtalloc.so`, `libandroid-shmem.so`, static loaders) bundled for
  `arm64-v8a`, `armeabi-v7a`, `x86_64` — extracted to `nativeLibraryDir` automatically.
- Alpine 3.21 minirootfs bundled as asset (per arch) with official `.sha256` verification;
  runtime re-download/update also supported from `dl-cdn.alpinelinux.org`.
- Rootfs is extracted to app-private storage, `/etc/resolv.conf` and `apk` repositories
  (main + community) are pre-configured → **`apk add` works out of the box**.
- Interactive sessions stream through a VT100/ANSI parser (colors, cursor, erase)
  with non-blocking reader threads; extra keys row (Esc/Tab/arrows/Ctrl+C…).
- If PRoot/bootstrap fails, the console degrades gracefully to `/system/bin/sh`.
- Modules 17–21 (git, QEMU, bash/python/lua) execute inside the same rootfs.

## Localization

`core/LocData{En,Tr,Es,Fr,It}.kt` — **724 keys per language**, generated from
`scripts/loc/*.py` with parity validation (`python3 scripts/gen_loc.py`).
Switching language is instant (no activity recreation) — module 73 or onboarding.

## Launcher icon

The icon is generated (`scripts/gen_icons.py`, PIL). To use your own PNG, replace
`app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png` (and the other densities)
with your artwork, keeping the 108dp adaptive-icon safe zone, then rebuild.

## Source layout

```
app/src/main/java/com/finndev/master/system/inspector/
├── MainActivity.kt / MsiApplication.kt
├── core/            # Shell/su engine, PRoot+Alpine engine, VT100, crypto, ELF,
│                    # tar.gz, downloader, localization, theme engine, registry,
│                    # HTTP server service, QS tile service, crash handler, exports
├── ui/              # shared components, navigation, onboarding, dashboard
└── modules/screens/ # the 78 module screens (grouped files) + router
```

## Scripts (host-side helpers, not part of the APK)

`scripts/` contains the fetch/build pipeline used to produce this package:
fetch toolchain & PRoot/Alpine assets, generate icons, validate/generate localization
tables, build the APK, and package the source zip.
