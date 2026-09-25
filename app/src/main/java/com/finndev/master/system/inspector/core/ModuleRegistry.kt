package com.finndev.master.system.inspector.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector

/** The five module categories. */
enum class MsiCategory(val key: String) {
    ROOT("cat_root"),
    TERMINAL("cat_terminal"),
    NETWORK("cat_network"),
    STORAGE("cat_storage"),
    SYSTEM("cat_system"),
}

data class MsiModule(
    val id: Int,
    val nameKey: String,
    val descKey: String,
    val category: MsiCategory,
    val icon: ImageVector,
)

/** Registry of ALL 78 MSI modules (1..78, exact order per specification). */
object ModuleRegistry {

    val all: List<MsiModule> = listOf(
        // ---------- ROOT & KERNEL TWEAKS (1-15) ----------
        MsiModule(1, "m01_name", "m01_desc", MsiCategory.ROOT, Icons.Filled.AdminPanelSettings),
        MsiModule(2, "m02_name", "m02_desc", MsiCategory.ROOT, Icons.Filled.Security),
        MsiModule(3, "m03_name", "m03_desc", MsiCategory.ROOT, Icons.Filled.EditNote),
        MsiModule(4, "m04_name", "m04_desc", MsiCategory.ROOT, Icons.Filled.RocketLaunch),
        MsiModule(5, "m05_name", "m05_desc", MsiCategory.ROOT, Icons.Filled.Storage),
        MsiModule(6, "m06_name", "m06_desc", MsiCategory.ROOT, Icons.Filled.Speed),
        MsiModule(7, "m07_name", "m07_desc", MsiCategory.ROOT, Icons.Filled.Tune),
        MsiModule(8, "m08_name", "m08_desc", MsiCategory.ROOT, Icons.Filled.Memory),
        MsiModule(9, "m09_name", "m09_desc", MsiCategory.ROOT, Icons.Filled.BatteryChargingFull),
        MsiModule(10, "m10_name", "m10_desc", MsiCategory.ROOT, Icons.Filled.AspectRatio),
        MsiModule(11, "m11_name", "m11_desc", MsiCategory.ROOT, Icons.Filled.Apps),
        MsiModule(12, "m12_name", "m12_desc", MsiCategory.ROOT, Icons.Filled.Sensors),
        MsiModule(13, "m13_name", "m13_desc", MsiCategory.ROOT, Icons.Filled.BugReport),
        MsiModule(14, "m14_name", "m14_desc", MsiCategory.ROOT, Icons.Filled.Timelapse),
        MsiModule(15, "m15_name", "m15_desc", MsiCategory.ROOT, Icons.Filled.Bolt),

        // ---------- TERMINAL, ROOTFS & SCRIPT ENGINE (16-32) ----------
        MsiModule(16, "m16_name", "m16_desc", MsiCategory.TERMINAL, Icons.Filled.Terminal),
        MsiModule(17, "m17_name", "m17_desc", MsiCategory.TERMINAL, Icons.Filled.Extension),
        MsiModule(18, "m18_name", "m18_desc", MsiCategory.TERMINAL, Icons.Filled.Computer),
        MsiModule(19, "m19_name", "m19_desc", MsiCategory.TERMINAL, Icons.Filled.Code),
        MsiModule(20, "m20_name", "m20_desc", MsiCategory.TERMINAL, Icons.Filled.PlayArrow),
        MsiModule(21, "m21_name", "m21_desc", MsiCategory.TERMINAL, Icons.Filled.DataObject),
        MsiModule(22, "m22_name", "m22_desc", MsiCategory.TERMINAL, Icons.Filled.Architecture),
        MsiModule(23, "m23_name", "m23_desc", MsiCategory.TERMINAL, Icons.Filled.Key),
        MsiModule(24, "m24_name", "m24_desc", MsiCategory.TERMINAL, Icons.Filled.AltRoute),
        MsiModule(25, "m25_name", "m25_desc", MsiCategory.TERMINAL, Icons.Filled.Handyman),
        MsiModule(26, "m26_name", "m26_desc", MsiCategory.TERMINAL, Icons.Filled.MedicalServices),
        MsiModule(27, "m27_name", "m27_desc", MsiCategory.TERMINAL, Icons.Filled.Schema),
        MsiModule(28, "m28_name", "m28_desc", MsiCategory.TERMINAL, Icons.Filled.SaveAlt),
        MsiModule(29, "m29_name", "m29_desc", MsiCategory.TERMINAL, Icons.Filled.Notes),
        MsiModule(30, "m30_name", "m30_desc", MsiCategory.TERMINAL, Icons.Filled.QrCode2),
        MsiModule(31, "m31_name", "m31_desc", MsiCategory.TERMINAL, Icons.Filled.Transform),
        MsiModule(32, "m32_name", "m32_desc", MsiCategory.TERMINAL, Icons.Filled.FormatAlignLeft),

        // ---------- NETWORK, SECURITY & DIAGNOSTICS (33-50) ----------
        MsiModule(33, "m33_name", "m33_desc", MsiCategory.NETWORK, Icons.Filled.Password),
        MsiModule(34, "m34_name", "m34_desc", MsiCategory.NETWORK, Icons.Filled.DeviceHub),
        MsiModule(35, "m35_name", "m35_desc", MsiCategory.NETWORK, Icons.Filled.SettingsEthernet),
        MsiModule(36, "m36_name", "m36_desc", MsiCategory.NETWORK, Icons.Filled.NetworkCheck),
        MsiModule(37, "m37_name", "m37_desc", MsiCategory.NETWORK, Icons.Filled.Dns),
        MsiModule(38, "m38_name", "m38_desc", MsiCategory.NETWORK, Icons.Filled.LocalFireDepartment),
        MsiModule(39, "m39_name", "m39_desc", MsiCategory.NETWORK, Icons.Filled.FindInPage),
        MsiModule(40, "m40_name", "m40_desc", MsiCategory.NETWORK, Icons.Filled.SignalCellularAlt),
        MsiModule(41, "m41_name", "m41_desc", MsiCategory.NETWORK, Icons.Filled.Fingerprint),
        MsiModule(42, "m42_name", "m42_desc", MsiCategory.NETWORK, Icons.Filled.Wifi),
        MsiModule(43, "m43_name", "m43_desc", MsiCategory.NETWORK, Icons.Filled.Visibility),
        MsiModule(44, "m44_name", "m44_desc", MsiCategory.NETWORK, Icons.Filled.GraphicEq),
        MsiModule(45, "m45_name", "m45_desc", MsiCategory.NETWORK, Icons.Filled.WifiTethering),
        MsiModule(46, "m46_name", "m46_desc", MsiCategory.NETWORK, Icons.Filled.Gesture),
        MsiModule(47, "m47_name", "m47_desc", MsiCategory.NETWORK, Icons.Filled.Casino),
        MsiModule(48, "m48_name", "m48_desc", MsiCategory.NETWORK, Icons.Filled.DeleteForever),
        MsiModule(49, "m49_name", "m49_desc", MsiCategory.NETWORK, Icons.Filled.ManageSearch),
        MsiModule(50, "m50_name", "m50_desc", MsiCategory.NETWORK, Icons.Filled.Style),

        // ---------- STORAGE, ISO & FILE MANAGEMENT (51-64) ----------
        MsiModule(51, "m51_name", "m51_desc", MsiCategory.STORAGE, Icons.Filled.Download),
        MsiModule(52, "m52_name", "m52_desc", MsiCategory.STORAGE, Icons.Filled.Share),
        MsiModule(53, "m53_name", "m53_desc", MsiCategory.STORAGE, Icons.Filled.Inventory2),
        MsiModule(54, "m54_name", "m54_desc", MsiCategory.STORAGE, Icons.Filled.CleaningServices),
        MsiModule(55, "m55_name", "m55_desc", MsiCategory.STORAGE, Icons.Filled.FactCheck),
        MsiModule(56, "m56_name", "m56_desc", MsiCategory.STORAGE, Icons.Filled.Search),
        MsiModule(57, "m57_name", "m57_desc", MsiCategory.STORAGE, Icons.Filled.FolderZip),
        MsiModule(58, "m58_name", "m58_desc", MsiCategory.STORAGE, Icons.Filled.SyncAlt),
        MsiModule(59, "m59_name", "m59_desc", MsiCategory.STORAGE, Icons.Filled.ContentPaste),
        MsiModule(60, "m60_name", "m60_desc", MsiCategory.STORAGE, Icons.Filled.Summarize),
        MsiModule(61, "m61_name", "m61_desc", MsiCategory.STORAGE, Icons.Filled.TextFields),
        MsiModule(62, "m62_name", "m62_desc", MsiCategory.STORAGE, Icons.Filled.Movie),
        MsiModule(63, "m63_name", "m63_desc", MsiCategory.STORAGE, Icons.Filled.ToggleOn),
        MsiModule(64, "m64_name", "m64_desc", MsiCategory.STORAGE, Icons.Filled.Archive),

        // ---------- SYSTEM PROFILING & UTILITIES (65-78) ----------
        MsiModule(65, "m65_name", "m65_desc", MsiCategory.SYSTEM, Icons.Filled.DeveloperBoard),
        MsiModule(66, "m66_name", "m66_desc", MsiCategory.SYSTEM, Icons.Filled.ShowChart),
        MsiModule(67, "m67_name", "m67_desc", MsiCategory.SYSTEM, Icons.Filled.Label),
        MsiModule(68, "m68_name", "m68_desc", MsiCategory.SYSTEM, Icons.Filled.ViewList),
        MsiModule(69, "m69_name", "m69_desc", MsiCategory.SYSTEM, Icons.Filled.Article),
        MsiModule(70, "m70_name", "m70_desc", MsiCategory.SYSTEM, Icons.Filled.TableChart),
        MsiModule(71, "m71_name", "m71_desc", MsiCategory.SYSTEM, Icons.Filled.Whatshot),
        MsiModule(72, "m72_name", "m72_desc", MsiCategory.SYSTEM, Icons.Filled.Timer),
        MsiModule(73, "m73_name", "m73_desc", MsiCategory.SYSTEM, Icons.Filled.Translate),
        MsiModule(74, "m74_name", "m74_desc", MsiCategory.SYSTEM, Icons.Filled.Style),
        MsiModule(75, "m75_name", "m75_desc", MsiCategory.SYSTEM, Icons.Filled.SystemUpdate),
        MsiModule(76, "m76_name", "m76_desc", MsiCategory.SYSTEM, Icons.Filled.CompareArrows),
        MsiModule(77, "m77_name", "m77_desc", MsiCategory.SYSTEM, Icons.Filled.Update),
        MsiModule(78, "m78_name", "m78_desc", MsiCategory.SYSTEM, Icons.Filled.Dashboard),
    )

    fun byId(id: Int): MsiModule? = all.firstOrNull { it.id == id }

    fun byCategory(c: MsiCategory): List<MsiModule> = all.filter { it.category == c }

    /** module manifest for module 77 (app & module version checker) */
    const val APP_VERSION = "4.0.0"
}
