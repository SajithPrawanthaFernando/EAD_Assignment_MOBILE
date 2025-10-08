package com.ead.evcharge.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Splash : Screen("splash")

    // Station Operator screens
    object OperatorHome : Screen("operator_home")
    object OperatorStations : Screen("operator_stations")
    object OperatorBookings : Screen("operator_bookings")
    object OperatorProfile : Screen("operator_profile")

    // EV Owner screens
    object OwnerHome : Screen("owner_home")
    object OwnerMap : Screen("owner_map")
    object OwnerBooking : Screen("owner_booking")
    object OwnerProfile : Screen("owner_profile")
}