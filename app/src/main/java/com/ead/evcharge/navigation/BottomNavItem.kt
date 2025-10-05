package com.ead.evcharge.navigation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val roles: List<String>
) {
    // Station Operator navigation items
    object OperatorHome : BottomNavItem(
        route = Screen.OperatorHome.route,
        title = "Dashboard",
        icon = Icons.Default.Home,
        roles = listOf("operator", "OPERATOR", "station_operator", "STATION_OPERATOR")
    )

    object OperatorStations : BottomNavItem(
        route = Screen.OperatorStations.route,
        title = "Stations",
        icon = Icons.Default.LocationOn,
        roles = listOf("operator", "OPERATOR", "station_operator", "STATION_OPERATOR")
    )

    object OperatorBookings : BottomNavItem(
        route = Screen.OperatorBookings.route,
        title = "Bookings",
        icon = Icons.Default.List,
        roles = listOf("operator", "OPERATOR", "station_operator", "STATION_OPERATOR")
    )

    object OperatorProfile : BottomNavItem(
        route = Screen.OperatorProfile.route,
        title = "Profile",
        icon = Icons.Default.AccountCircle,
        roles = listOf("operator", "OPERATOR", "station_operator", "STATION_OPERATOR")
    )

    // EV Owner navigation items
    object OwnerHome : BottomNavItem(
        route = Screen.OwnerHome.route,
        title = "Home",
        icon = Icons.Default.Home,
        roles = listOf("owner", "OWNER", "ev_owner", "EV_OWNER", "user", "USER")
    )

    object OwnerMap : BottomNavItem(
        route = Screen.OwnerMap.route,
        title = "Find Stations",
        icon = Icons.Default.Search,
        roles = listOf("owner", "OWNER", "ev_owner", "EV_OWNER", "user", "USER")
    )

    object OwnerVehicles : BottomNavItem(
        route = Screen.OwnerVehicles.route,
        title = "My Vehicles",
        icon = Icons.Default.List,
        roles = listOf("owner", "OWNER", "ev_owner", "EV_OWNER", "user", "USER")
    )

    object OwnerProfile : BottomNavItem(
        route = Screen.OwnerProfile.route,
        title = "Profile",
        icon = Icons.Default.AccountCircle,
        roles = listOf("owner", "OWNER", "ev_owner", "EV_OWNER", "user", "USER")
    )
}

// Helper function to get navigation items based on role
fun getBottomNavItemsForRole(userRole: String): List<BottomNavItem> {
    val normalizedRole = userRole.lowercase().replace("_", "").replace(" ", "")

    return when {
        normalizedRole.contains("operator") || normalizedRole.contains("stationoperator") -> listOf(
            BottomNavItem.OperatorHome,
            BottomNavItem.OperatorStations,
            BottomNavItem.OperatorBookings,
            BottomNavItem.OperatorProfile
        )
        else -> listOf( // Default: EV Owner
            BottomNavItem.OwnerHome,
            BottomNavItem.OwnerMap,
            BottomNavItem.OwnerVehicles,
            BottomNavItem.OwnerProfile
        )
    }
}

fun getStartDestinationForRole(userRole: String): String {
    val normalizedRole = userRole.lowercase().replace("_", "").replace(" ", "")

    return when {
        normalizedRole.contains("operator") || normalizedRole.contains("Stationoperator") ->
            Screen.OperatorHome.route
        else -> Screen.OwnerHome.route
    }
}