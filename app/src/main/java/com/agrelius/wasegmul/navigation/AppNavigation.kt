package com.agrelius.wasegmul.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agrelius.wasegmul.ui.classify.ClassificationViewModel
import com.agrelius.wasegmul.ui.classify.ClassifyScreen
import com.agrelius.wasegmul.ui.history.HistoryScreen
import com.agrelius.wasegmul.ui.home.HomeScreen
import com.agrelius.wasegmul.ui.result.ResultScreen
import com.agrelius.wasegmul.ui.settings.SettingsScreen
import com.agrelius.wasegmul.ui.splash.SplashScreen
import com.agrelius.wasegmul.ui.yolo.YoloScreen
import com.agrelius.wasegmul.viewmodel.HomeViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as? com.agrelius.wasegmul.WasegMulApp
        ?: return
    val repository = app.repository
    val settingsManager = app.settingsManager

    val classificationViewModel: ClassificationViewModel = viewModel(
        factory = ClassificationViewModel.Factory(repository)
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
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToYolo = {
                    navController.navigate(Screen.Yolo.route)
                }
            )
        }

        composable(Screen.Settings.route) {
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

        composable(
            route = Screen.Result.route + "?recordId={recordId}",
            arguments = listOf(navArgument("recordId") { 
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: -1L
            LaunchedEffect(recordId) {
                if (recordId != -1L) {
                    classificationViewModel.loadRecord(recordId)
                }
            }
            
            ResultScreen(
                viewModel = classificationViewModel,
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToResult = { id ->
                    navController.navigate(Screen.Result.route + "?recordId=$id")
                }
            )
        }

        composable(Screen.Yolo.route) {
            YoloScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
