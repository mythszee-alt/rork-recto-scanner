package com.rork.recto.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rork.recto.data.ImportService
import com.rork.recto.ui.screens.AppGate
import com.rork.recto.ui.screens.AppViewModel
import com.rork.recto.ui.screens.AuthScreen
import com.rork.recto.ui.screens.BarcodeScannerScreen
import com.rork.recto.ui.screens.CaptureScreen
import com.rork.recto.ui.screens.DocumentScreen
import com.rork.recto.ui.screens.HomeScreen
import com.rork.recto.ui.screens.OnboardingScreen
import com.rork.recto.ui.screens.PaywallScreen
import com.rork.recto.ui.screens.ReviewScreen
import com.rork.recto.ui.screens.RectoAction
import com.rork.recto.ui.screens.RectoViewModel
import com.rork.recto.ui.screens.TextExtractScreen
import com.rork.recto.ui.screens.TrashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

/** Upper bound for a single gallery import, matching a sane multi-page scan. */
private const val MAX_IMPORT_PAGES = 20

@Composable
fun AppNavigation(authCallback: StateFlow<Uri?>) {
    val appViewModel: AppViewModel = koinViewModel()
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
    val viewModel: RectoViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val importer = remember(context) { ImportService(context) }

    // System photo picker: scoped, one-off access with no storage permission.
    // Copying happens off the main thread — importing 20 pages otherwise
    // blocks the UI while the files are written.
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMPORT_PAGES)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val paths = withContext(Dispatchers.IO) { importer.importImages(uris) }
            if (paths.isNotEmpty()) {
                paths.forEach { viewModel.onAction(RectoAction.AddPage(it)) }
                navController.navigate("review")
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                navController = navController,
                uiState = uiState,
                onAction = viewModel::onAction,
                onImportImages = {
                    importLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
        }
        composable("capture") {
            CaptureScreen(
                navController = navController,
                capturedCount = uiState.capturedPages.size,
                onPageCaptured = { path, sharpness, glare -> viewModel.onAction(RectoAction.AddPage(path, sharpness, glare)) }
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
                DocumentScreen(
                    document = document,
                    onAction = viewModel::onAction,
                    onBack = navController::popBackStack,
                    onExtractText = { navController.navigate("text/${document.id}") },
                )
            }
        }
        composable("text/{id}") { entry ->
            val document = uiState.documents.firstOrNull { it.id == entry.arguments?.getString("id") }
            if (document != null) {
                TextExtractScreen(document = document, onBack = navController::popBackStack)
            }
        }
        composable("barcode") { BarcodeScannerScreen(onBack = navController::popBackStack) }
        composable("trash") {
            TrashScreen(
                documents = uiState.documents.filter { it.isDeleted },
                onAction = viewModel::onAction,
                onBack = navController::popBackStack
            )
        }
        composable("account") { com.rork.recto.ui.screens.AccountScreen(state = appState, appViewModel = appViewModel, onBack = navController::popBackStack) }
    }
}
