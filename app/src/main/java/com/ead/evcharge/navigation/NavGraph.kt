package com.ead.evcharge.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.ui.operator.*
import com.ead.evcharge.ui.owner.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavGraph(
    tokenManager: TokenManager,
    startDestination: String,
    userRole: String,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val bottomNavItems = getBottomNavItemsForRole(userRole)

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            val shouldShowBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

            if (shouldShowBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Station Operator screens
            composable(Screen.OperatorHome.route) {
                OperatorHomeScreen(
                    tokenManager = tokenManager,
                    onLogout = onLogout
                )
            }
            composable(Screen.OperatorStations.route) {
                OperatorStationsScreen()
            }
            composable(Screen.OperatorBookings.route) {
                OperatorBookingsScreen()
            }
            composable(Screen.OperatorProfile.route) {
                OperatorProfileScreen(
                    tokenManager = tokenManager,
                    onLogout = onLogout
                )
            }

            // EV Owner screens
            composable(Screen.OwnerHome.route) {
                OwnerHomeScreen(
                    tokenManager = tokenManager,
                    navController = navController,
                    onLogout = onLogout
                )
            }
            composable(Screen.OwnerMap.route) {
                OwnerMapScreen()
            }
            composable(Screen.OwnerBooking.route) {
                OwnerBookingScreen(
                    tokenManager = tokenManager
                )
            }
            composable(Screen.OwnerProfile.route) {
                OwnerProfileScreen(
                    tokenManager = tokenManager,
                    onLogout = onLogout
                )
            }
        }
    }
}