package com.agrelius.wasegmul.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Classify : Screen("classify")
    object Result : Screen("result")
    object History : Screen("history")
    object Settings : Screen("settings")
}
