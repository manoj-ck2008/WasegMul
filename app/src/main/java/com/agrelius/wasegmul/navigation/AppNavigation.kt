package com.agrelius.wasegmul.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agrelius.wasegmul.ui.classify.ClassifyScreen
import com.agrelius.wasegmul.ui.home.HomeScreen
import com.agrelius.wasegmul.ui.result.ResultScreen
import com.agrelius.wasegmul.ui.splash.SplashScreen
import com.agrelius.wasegmul.viewmodel.ClassificationViewModel
import com.agrelius.wasegmul.viewmodel.HomeViewModel
import com.agrelius.wasegmul.viewmodel.ResultViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

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
            val homeViewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToClassify = {
                    navController.navigate(Screen.Classify.route)
                }
            )
        }

        composable(Screen.Classify.route) {
            val classifyViewModel: ClassificationViewModel = viewModel()
            ClassifyScreen(
                viewModel = classifyViewModel,
                onNavigateToResult = {
                    navController.navigate(Screen.Result.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Result.route) {
            val resultViewModel: ResultViewModel = viewModel()
            ResultScreen(
                viewModel = resultViewModel,
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
