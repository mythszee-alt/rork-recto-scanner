package com.rork.recto.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rork.recto.ui.screens.AppGate
import com.rork.recto.ui.screens.AppViewModel
import com.rork.recto.ui.screens.AuthScreen
import com.rork.recto.ui.screens.CaptureScreen
import com.rork.recto.ui.screens.DocumentScreen
import com.rork.recto.ui.screens.HomeScreen
import com.rork.recto.ui.screens.OnboardingScreen
import com.rork.recto.ui.screens.PaywallScreen
import com.rork.recto.ui.screens.ReceiptScreen
import com.rork.recto.ui.screens.ReviewScreen
import com.rork.recto.ui.screens.RectoAction
import com.rork.recto.ui.screens.RectoViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppNavigation(authCallback: StateFlow<Uri?>) {
    val appViewModel: AppViewModel = viewModel()
    val appState by appViewModel.uiState.collectAsStateWithLifecycle()
    val callback by authCallback.collectAsStateWithLifecycle()
    LaunchedEffect(callback) { callback?.let(appViewModel::acceptOAuth) }

    AnimatedContent(targetState = appState.gate, label = "app-gate") { gate ->
        when (gate) {
            AppGate.ONBOARDING -> OnboardingScreen(onComplete = appViewModel::completeOnboarding)
            AppGate.AUTH -> AuthScreen(state = appState, viewModel = appViewModel)
            AppGate.PAYWALL -> PaywallScreen(state = appState, viewModel = appViewModel)
            AppGate.LIBRARY -> MainNavigation(appState, appViewModel)
        }
    }
}

@Composable
private fun MainNavigation(appState: com.rork.recto.ui.screens.AppUiState, appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val viewModel: RectoViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(navController = navController, uiState = uiState, onAction = viewModel::onAction)
        }
        composable("capture") {
            CaptureScreen(
                navController = navController,
                capturedCount = uiState.capturedPages.size,
                onPageCaptured = { path -> viewModel.onAction(RectoAction.AddPage(path)) }
            )
        }
        composable("review") {
            ReviewScreen(
                pages = uiState.capturedPages,
                onAction = viewModel::onAction,
                onBack = navController::popBackStack,
                onSaved = { navController.navigate("home") { popUpTo("home") { inclusive = true } } }
            )
        }
        composable("document/{id}") { entry ->
            val document = uiState.documents.firstOrNull { it.id == entry.arguments?.getString("id") }
            if (document != null) {
                DocumentScreen(document = document, onAction = viewModel::onAction, onBack = navController::popBackStack)
            }
        }
        composable("receipt") { ReceiptScreen(navController = navController) }
        composable("account") { com.rork.recto.ui.screens.AccountScreen(state = appState, appViewModel = appViewModel, onBack = navController::popBackStack) }
    }
}
