package com.agrelius.wasegmul.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agrelius.wasegmul.WasegMulApp
import com.agrelius.wasegmul.ui.classify.ClassificationViewModel
import com.agrelius.wasegmul.ui.classify.ClassifyScreen
import com.agrelius.wasegmul.ui.history.HistoryScreen
import com.agrelius.wasegmul.ui.home.HomeScreen
import com.agrelius.wasegmul.ui.result.ResultScreen
import com.agrelius.wasegmul.ui.settings.SettingsScreen
import com.agrelius.wasegmul.ui.splash.SplashScreen
import com.agrelius.wasegmul.viewmodel.HomeViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as WasegMulApp
    val repository = app.repository
    val soundManager = app.soundManager

    val classificationViewModel: ClassificationViewModel = viewModel(
        factory = ClassificationViewModel.Factory(repository, soundManager)
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(repository)
    )

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onImageSelected = { bitmap ->
                    classificationViewModel.setBitmap(bitmap)
                    navController.navigate(Screen.Classify.route)
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                homeViewModel = homeViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Classify.route) {
            ClassifyScreen(
                viewModel = classificationViewModel,
                onNavigateToResult = {
                    navController.navigate(Screen.Result.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Result.route) {
            ResultScreen(
                viewModel = classificationViewModel,
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable("history") {
            HistoryScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
