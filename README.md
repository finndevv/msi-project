# 🛡️ Master System Auditor (Ana Sistem Denetçisi) v3.0.0

**Master System Auditor** is a professional **Cyberpunk/Developer Command Center** application built for Android devices, providing high-level system, hardware, network, and root management capabilities. Designed away from the soft, pastel aesthetics of Material 3, it features a hardcore system utility look with high-density layouts, monospaced fonts, and high-contrast neon accents.

---

## 🚀 Project Overview & Architecture

* **App Name:** Master System Auditor (MSI)
* **Package Name:** `com.finndev-multi-software-installer`
* **Version:** v3.0.0
* **UI Theme Style:** Cyberpunk / Hacker / Developer Terminal (Dark background `#0A0A0A`, information-dense layout, neon green/cyan/amber accents).
* **Storage Access:** Full `MANAGE_EXTERNAL_STORAGE` permission integration (`ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`) to bypass Android 11+ restrictions for unhindered file operations and ISO downloads.

---

## ⚙️ 65 Operational Features & Module List

The application packs **65 fully functional modules** tailored for system administrators and power users in the field:

### 🔑 Core Highlights (1 - 2)
1. **Offline Password Manager:** A secure, local, encrypted database vault working completely offline without internet access to store sensitive credentials.
2. **Advanced Root Manager & Auditor:** Deep `su` binary audit (Magisk, KernelSU, APatch), SELinux mode switcher (`Enforcing`/`Permissive`), `build.prop` editor, init.d script runner, and system partition remounter (`/system` R/W).

### 💻 Terminal, PRoot & Container Tools (3 - 12)
3. **Real Alpine Linux PRoot Terminal:** An isolated `rootfs` terminal environment supporting the actual `apk` package manager.
4. **Termux Integration Git Cloner:** Detects `com.termux`, fires intent, and auto-copies `git clone https://github.com/finndev62/valiantcore.git` to clipboard.
5. **QEMU Architecture Emulator Launcher:** ISO image emulation starter for `x86_64`, `i386`, and `ARM64` architectures.
6. **Bash & Shell Script Runner (.sh):** Local script execution engine with live output log console.
7. **Python / Lua Command Executor:** Built-in lightweight script interpreter environment.
8. **ELF Binary Architecture Analyzer:** Inspects whether target files or binaries are 32-bit or 64-bit ELF.
9. **File Permission Manager (Chmod / Chown):** Modifies file and directory permissions via root access.
10. **Environment PATH Variable Viewer & Editor:** Lists and manages system `PATH` entries.
11. **Busybox Integration & Validator:** Checks and verifies the device's Busybox toolkit status and version.
12. **Emergency Recovery Command Console:** Secure terminal to run critical commands during system anomalies.

### 📦 Storage, ISO & File Management (13 - 22)
13. **ValiantCore ISO Downloader:** Download manager engine with explicit storage permission support, featuring `x86_64` and `i386` architecture selection.
14. **Local HTTP File Sharing Server:** Instant mini HTTP server for sharing assets across devices on the local network (LAN).
15. **APK Backup & Extractor:** Extracts installed application APK files for local backup.
16. **Deep Storage & Cache Cleaner:** Scans and purges leftover junk files and app caches.
17. **ISO / File MD5 & SHA256 Hash Verifier:** Cryptographic integrity check tool for large downloaded binaries and ISOs.
18. **Storage Hog Hunter (Large File Finder):** Locates and lists massive files consuming device space.
19. **Zip / Tar Archive Manager:** Extracts, inspects, and creates compressed archive packages.
20. **Download Queue & Progress Tracker:** Monitors active download speeds, percentages, and status.
21. **Clipboard History & Manager:** Saves copied text snippets for quick retrieval.
22. **System Log & Report Exporter:** Dumps system diagnostics and logs into `.txt` or `.log` files.

### 🛡️ Network, Security & Diagnostics (23 - 35)
23. **Local Area Network (LAN) Device Scanner:** Lists IP and MAC addresses of active devices connected to the same Wi-Fi.
24. **Port Scanner (TCP/UDP Port Checker):** Tests open ports on a target IP address.
25. **Advanced Ping & Latency (ms) Tester:** Measures real-time packet response times.
26. **Wi-Fi Signal & Channel Analyzer:** Inspects wireless signal strength and channel quality.
27. **Custom DNS Changer over Root:** Overrides system-wide DNS servers (Cloudflare, Google, AdGuard) via root.
28. **Firewall Rule Manager (`iptables` / `nftables`):** Manages packet filtering rules at the root level.
29. **Active Network Socket Inspector (Netstat):** Tracks active socket connections and remote endpoints.
30. **Internet Bandwidth Speed Test Module:** Measures active download and upload throughput.
31. **Custom Captcha & Bot Filtering Test Bench:** Testing environment for custom bot validation scripts.
32. **MAC Address Spoofing (Root):** Temporarily alters the device's hardware MAC address for privacy.
33. **Hotspot / Tethering Client Inspector:** Lists active clients connected to your mobile hotspot.
34. **SSL Certificate Viewer:** Inspects SSL/TLS certificates of remote servers.
35. **Packet Sniffer / Logger:** Captures basic network traffic log streams.

### ⚙️ Hardware Profiling & System Auditing (36 - 50)
36. **Comprehensive Hardware Profiler:** CPU core frequencies (MHz), chipset details, and ABI architecture.
37. **Real-time RAM & Memory Monitor:** Live usage statistics of system memory.
38. **Battery Health & Temperature Reporter:** Monitors thermal status, voltage, and milliamps.
39. **Screen Resolution & DPI Changer (Root):** Adjusts pixel density and display metrics.
40. **CPU Governor Performance Mode Manager:** Switches scaling governors (`performance`, `powersave`, `schedutil`).
41. **Live Hardware Sensors Inspector:** Reads live data from the gyroscope, accelerometer, and magnetometer.
42. **Installed Package Name Inspector:** Copies target app package names (e.g., `com.termux`).
43. **Background Services & Processes Manager:** Lists active system processes and memory consumption.
44. **System Logcat Flow Viewer & Filter:** Real-time Android logcat stream reader.
45. **Bloatware App Freezer (Root):** Disables unused pre-installed system applications.
46. **Kernel Log (`dmesg`) Reader:** Inspects low-level kernel ring buffer messages.
47. **Device Boot Time & Log Analyzer:** Evaluates system startup timing and logs.
48. **Mount Points & Disk Usage Table:** Displays partition mapping (`/system`, `/data`, `/vendor`) and usage.
49. **Hardware Stress Test / Benchmark Launcher:** Puts load on CPU and memory for stability testing.
50. **CPU Thermal Threshold Inspector:** Monitors thermal limits and throttling triggers.

### 🛠️ Developer & Utility Tools (51 - 65)
51. **Offline Markdown Note Editor:** Quick snippet and note-taking scratchpad requiring no internet.
52. **QR Code & Barcode Scanner/Generator:** Camera-based scanner and text-to-QR generator.
53. **Base64 & Hex Converter:** Text encoding and decoding utility.
54. **JSON & XML Formatter/Validator:** Beautifies and validates structured data payloads.
55. **Color Palette & HEX/RGB Picker:** Handy color reference tool for UI development.
56. **Timer, Stopwatch & Chronometer:** Precision timekeeping utilities.
57. **Regex Test Panel:** Regular expression validator workspace.
58. **Secure Random Password & Token Generator:** Generates high-entropy cryptographic strings.
59. **Sensitive File Secure Shredder:** Permanently wipes files beyond recovery.
60. **System Font Changer (Root):** Overrides system typeface settings.
61. **Bootanimation Changer (Root):** Replaces system boot startup animations.
62. **Night/Day Dark Theme Manager:** Controls interface contrast profiles.
63. **Debug Support & Report Package Zip Creator:** Bundles logs and error data into a single `.zip` archive.
64. **Quick Settings Tile Integration:** Adds custom tile shortcuts to the notification shade.
65. **Version Info & Module Update Checker:** Displays application build metadata and internal version state.

---
*Elite System Administrators & Developers Utility.*
