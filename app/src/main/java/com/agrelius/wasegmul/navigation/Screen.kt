package com.agrelius.wasegmul.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Home : Screen("home")
    data object Classify : Screen("classify")
    data object Result : Screen("result") {
        const val routeWithArgs = "result?recordId={recordId}"
        fun createRoute(recordId: Long? = null): String =
            if (recordId != null && recordId > 0) "result?recordId=$recordId" else "result"
    }
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object Yolo : Screen("yolo")
    data object Guide : Screen("guide")
    data object BarcodeScan : Screen("barcode_scan")
}
