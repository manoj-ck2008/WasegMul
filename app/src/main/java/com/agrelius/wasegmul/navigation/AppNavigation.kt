package com.agrelius.wasegmul.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import kotlinx.coroutines.flow.StateFlow
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.ui.barcode.BarcodeScanScreen
import com.agrelius.wasegmul.ui.barcode.BarcodeScanViewModel
import com.agrelius.wasegmul.ui.classify.ClassificationViewModel
import com.agrelius.wasegmul.ui.classify.ClassifyScreen
import com.agrelius.wasegmul.ui.history.HistoryScreen
import com.agrelius.wasegmul.ui.home.HomeScreen
import com.agrelius.wasegmul.ui.result.ResultScreen
import com.agrelius.wasegmul.ui.settings.SettingsScreen
import com.agrelius.wasegmul.ui.splash.SplashScreen
import com.agrelius.wasegmul.ui.yolo.YoloScreen
import com.agrelius.wasegmul.ui.guide.GuideScreen
import com.agrelius.wasegmul.viewmodel.HomeViewModel

@Composable
fun AppNavigation(deepLinkEvents: StateFlow<Intent?>? = null) {
    val navController = rememberNavController()
    // Warm deep links: singleTask delivers them to onNewIntent while the graph
    // is alive; the NavHost only consumes the cold-start intent, so forward
    // subsequent ones explicitly (no-op when null).
    LaunchedEffect(deepLinkEvents) {
        deepLinkEvents?.collect { intent ->
            if (intent != null) navController.handleDeepLink(intent)
        }
    }
    val context = LocalContext.current
    val app = context.applicationContext as? com.agrelius.wasegmul.WasegMulApp
    if (app == null) {
        android.util.Log.e("AppNavigation", "WasegMulApp missing in manifest: check android:name")
        androidx.compose.material3.Text(stringResource(R.string.home_app_error))
        return
    }
    val repository = app.repository
    val settingsManager = app.settingsManager

    // Shared app-scoped ModelManager: one TFLite residency for camera,
    // barcode Tier-4 and YOLO-adjacent flows (no duplicate instances).
    val classificationViewModel: ClassificationViewModel = viewModel(
        factory = ClassificationViewModel.Factory(
            repository = repository,
            settingsManager = settingsManager,
            modelManager = app.modelManager,
            context = app
        )
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
                    navController.navigate(Screen.Classify.route) { launchSingleTop = true }
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route) { launchSingleTop = true }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                },
                onNavigateToYolo = {
                    navController.navigate(Screen.Yolo.route) { launchSingleTop = true }
                },
                onNavigateToGuide = {
                    navController.navigate(Screen.Guide.route) { launchSingleTop = true }
                },
                onNavigateToBarcode = {
                    navController.navigate(Screen.BarcodeScan.route) { launchSingleTop = true }
                },
                // Home recent-taps were dead (default {}). Wire to Result.
                onNavigateToResult = { recordId ->
                    navController.navigate(Screen.Result.createRoute(recordId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                homeViewModel = homeViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // Navigation policy (single rule): top-level destinations use
        // launchSingleTop; Result ALWAYS pushes a fresh entry (no
        // launchSingleTop) so a new recordId can never be swallowed and show
        // a stale record.
        composable(Screen.Classify.route) {
            ClassifyScreen(
                viewModel = classificationViewModel,
                onNavigateToResult = { recordId ->
                    navController.navigate(Screen.Result.createRoute(recordId))
                },
                onBack = {
                    classificationViewModel.releaseBitmap()
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Result.routeWithArgs,
            arguments = listOf(navArgument("recordId") { 
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: -1L
            LaunchedEffect(recordId) {
                if (recordId != -1L && classificationViewModel.currentRecord.value?.id != recordId) {
                    classificationViewModel.loadRecord(recordId, keepCelebration = classificationViewModel.isFreshScan.value)
                }
            }
            
            ResultScreen(
                viewModel = classificationViewModel,
                // recordId == -1 (no args): render the timeout/error state
                // instead of an indeterminate spinner.
                invalidRecordId = recordId == -1L,
                onNavigateToHome = {
                    classificationViewModel.releaseBitmap()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBack = {
                    // Do NOT release the bitmap here: back-to-Classify must
                    // restore the captured image (fixes the empty-Classify
                    // dead end). Classify's own onBack releases it.
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToResult = { id ->
                    navController.navigate(Screen.Result.createRoute(id))
                }
            )
        }

        composable(Screen.Yolo.route) {
            YoloScreen(
                onBack = { navController.popBackStack() },
                onCaptureAndClassify = { bitmap ->
                    classificationViewModel.setBitmap(bitmap)
                    navController.navigate(Screen.Classify.route) { launchSingleTop = true }
                }
            )
        }

        composable(Screen.Guide.route) {
            GuideScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.BarcodeScan.route,
            deepLinks = listOf(navDeepLink { uriPattern = "wasegmul://barcode" })
        ) {
            val barcodeViewModel: BarcodeScanViewModel = viewModel(
                factory = BarcodeScanViewModel.Factory(
                    barcodeRepository = app.barcodeRepository,
                    wasteRepository = app.repository,
                    modelManager = app.modelManager,
                    settingsManager = app.settingsManager,
                    context = app
                )
            )
            BarcodeScanScreen(
                viewModel = barcodeViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToResult = { recordId ->
                    classificationViewModel.setScanCelebration(
                        xpGain = barcodeViewModel.lastXpGain.value,
                        isFresh = true
                    )
                    navController.navigate(Screen.Result.createRoute(recordId))
                },
                onFallbackToCamera = {
                    navController.navigate(Screen.Yolo.route) { launchSingleTop = true }
                }
            )
        }
    }
}
