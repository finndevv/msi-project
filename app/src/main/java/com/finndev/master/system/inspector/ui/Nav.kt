package com.finndev.master.system.inspector.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.modules.screens.ModuleScreenHost

val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("NavController not provided")
}

private data class Tab(val route: String, val icon: ImageVector, val labelKey: String)

private val tabs = listOf(
    Tab("home", Icons.Filled.Dashboard, "nav_home"),
    Tab("modules", Icons.Filled.ViewModule, "nav_modules"),
    Tab("module/16", Icons.Filled.Terminal, "nav_terminal"),
    Tab("settings", Icons.Filled.Settings, "nav_settings"),
)

/** Root scaffold: bottom navigation + module nav graph. */
@Composable
fun MsiNavRoot(openTarget: String? = null) {
    val nav = rememberNavController()
    CompositionLocalProvider(LocalNavController provides nav) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                val backStack by nav.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                if (currentRoute == "home" || currentRoute == "modules" || currentRoute == "settings") {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route ||
                                (tab.route == "module/16" && currentRoute == "module/16")
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != tab.route) {
                                        nav.navigate(tab.route) {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(L(tab.labelKey), style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.padding(pad),
            ) {
                composable("home") { DashboardScreen() }
                composable("modules") { AllModulesBrowserScreen() }
                composable("settings") { SettingsHubScreen() }
                composable("module/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toIntOrNull() ?: 78
                    ModuleScreenHost(id)
                }
            }
        }
    }
    if (openTarget == "terminal") {
        androidx.compose.runtime.LaunchedEffect(Unit) { nav.navigate("module/16") }
    }
}

/** "All modules" browser with category filter + search. */
@Composable
fun AllModulesBrowserScreen() {
    ModuleBrowserBody()
}

/** Settings hub linking modules 73 / 74 / 77 + storage permission re-request. */
@Composable
fun SettingsHubScreen() {
    SettingsHubBody()
}
