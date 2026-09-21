# 🛡️ Master System Auditor (Ana Sistem Denetçisi) v2.0.0

**Master System Auditor** (MSI) is a professional Android system management and developer utility designed for low-level diagnostics, terminal execution, and field operations. Version v2.0.0 introduces critical bug fixes for Android's modern scoped storage, a true interactive Linux execution engine, and powerful new diagnostic modules.

---

## 🚀 Project Overview & Architecture

* **App Name:** Master System Auditor (MSI)
* **Package Name:** `com.finndev-multi-software-installer`
* **Version:** v2.0.0
* **UI Theme Style:** Clean, high-performance Jetpack Compose dashboard tailored for developers and system engineers.

---

## 🛠️ Critical Bug Fixes in v2.0.0

1. **Storage Permission & ISO Download Fix:**
   - Bypasses legacy storage restrictions by utilizing modern Android `DownloadManager` requests targeting `Environment.DIRECTORY_DOWNLOADS` with proper metered/roaming network allowances.
   - Implements robust fallback mechanisms (app-specific external files directory / MediaStore API) ensuring ValiantCore ISO downloads (`x86_64` and `i386` links) execute without failure, complete with user-facing progress feedback and notifications.

2. **Real Linux Terminal Environment Fix:**
   - Replaces basic pseudo-terminals restricted to Android Toybox/mksh with a genuine interactive shell process (`ProcessBuilder("sh")`).
   - Configures persistent environment variables, proper `PATH` routing (`/system/bin:/vendor/bin:/data/local/tmp`), and robust stream handling (`getInputStream`, `getErrorStream`) to run standard Linux utilities, package checks, and scripts seamlessly.

---

## ⚡ New v2.0.0 Field Features (All-in-One Multi-Software Manager)

In addition to core system tools, version v2.0.0 integrates these powerful modules into the dashboard:

* **Script Runner:** A dedicated execution environment to run custom local shell scripts or pre-configured security and system diagnostic commands with live output logs.
* **Quick Package Manager / App Audit:** Inspects installed packages, evaluates binary architectures via basic ELF/stat checks (32-bit vs. 64-bit analysis), and manages system packages.
* **Advanced Network Diagnostics:** Built-in IP configuration reader, active interface checker, and real-time latency/ping utility.

---

## 📋 Permissions & Configuration (`AndroidManifest.xml`)

* Explicit network states, internet access, and package visibility queries configured for external tools (e.g., `com.termux`).
* Modern scoped storage and download manager configurations optimized for Android 10+ up to Android 15.

---
*Built for elite system administrators and Android developers.*
