package com.rork.recto.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.revenuecat.purchases.Package
import com.rork.recto.ui.theme.AppTheme
import com.rork.recto.ui.theme.CalibrationCyan
import com.rork.recto.ui.theme.RegistrationMagenta

// Google sign-in is now enabled for testing.
private const val GOOGLE_SIGN_IN_ENABLED = true

private data class OnboardingPage(val eyebrow: String, val title: String, val body: String, val icon: ImageVector, val accent: Color)

@Composable
fun OnboardingScreen(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val pages = remember {
        listOf(
            OnboardingPage("PROOFCHECK", "Know before you capture.", "Recto checks for blur and glare while the paper is still in front of you.", Icons.Outlined.AutoAwesome, CalibrationCyan),
            OnboardingPage("PRIVATE BY DESIGN", "Your desk stays yours.", "Scans are saved locally first. Optional cloud copies are encrypted before upload and isolated to your account.", Icons.Outlined.Lock, RegistrationMagenta),
            OnboardingPage("TEXT EXTRACTION", "Content at your fingertips.", "Extract text from any scan using on-device ML Kit OCR. Export as searchable PDF or plain text.", Icons.Outlined.AutoAwesome, CalibrationCyan)
        )
    }
    var page by remember { mutableIntStateOf(0) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("RECTO / 01", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                TextButton(onClick = onComplete) { Text("SKIP") }
            }
            AnimatedContent(targetState = pages[page], label = "onboarding") { item ->
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(
                        Modifier.size(112.dp).background(item.accent.copy(alpha = .12f), RoundedCornerShape(28.dp))
                            .border(1.dp, item.accent.copy(alpha = .45f), RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(item.icon, null, Modifier.size(52.dp), tint = item.accent) }
                    Text(item.eyebrow, color = item.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text(item.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    Text(item.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.forEachIndexed { index, item ->
                        Box(Modifier.height(3.dp).weight(1f).background(if (index <= page) item.accent else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Button(
                    onClick = { if (page == pages.lastIndex) onComplete() else page += 1 },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = pages[page].accent, contentColor = Color.Black)
                ) { Text(if (page == pages.lastIndex) "CREATE MY ACCOUNT" else "CONTINUE", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
fun AuthScreen(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val title = when (state.authMode) { AuthMode.SIGN_IN -> "Welcome back"; AuthMode.CREATE -> "Create your account"; AuthMode.RESET -> "Reset your password" }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("RECTO", color = CalibrationCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("Private document capture with measurable quality.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, leadingIcon = { Icon(Icons.Outlined.Mail, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
            if (state.authMode != AuthMode.RESET) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            }
            if (!state.isAccountServiceConfigured) {
                Text(
                    "Accounts aren't connected in this build, so sign-in won't work yet. You can still scan, edit and export — everything stays on this device.",
                    Modifier.padding(top = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.message?.let { Text(it, Modifier.padding(top = 12.dp), color = if (it.contains("sent") || it.contains("inbox")) CalibrationCyan else MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { viewModel.authenticate(email, password) }, enabled = !state.isLoading && state.isAccountServiceConfigured, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(when (state.authMode) { AuthMode.SIGN_IN -> "SIGN IN"; AuthMode.CREATE -> "CREATE ACCOUNT"; AuthMode.RESET -> "SEND RESET LINK" }, fontWeight = FontWeight.Black)
            }
            if (state.authMode != AuthMode.RESET && GOOGLE_SIGN_IN_ENABLED) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    viewModel.googleUrl().fold(
                        onSuccess = { url ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                .onFailure { viewModel.reportError("No browser is available to complete Google sign-in") }
                        },
                        onFailure = { error -> viewModel.reportError(error.message ?: "Google sign-in is unavailable") }
                    )
                }, Modifier.fillMaxWidth().height(54.dp)) { Text("CONTINUE WITH GOOGLE", fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { viewModel.setAuthMode(if (state.authMode == AuthMode.CREATE) AuthMode.SIGN_IN else AuthMode.CREATE) }) {
                    Text(if (state.authMode == AuthMode.CREATE) "Already have an account? Sign in" else "New to Recto? Create account")
                }
            }
            if (state.authMode == AuthMode.SIGN_IN) TextButton(onClick = { viewModel.setAuthMode(AuthMode.RESET) }, Modifier.align(Alignment.CenterHorizontally)) { Text("Forgot password?") }
            if (state.authMode == AuthMode.RESET) TextButton(onClick = { viewModel.setAuthMode(AuthMode.SIGN_IN) }, Modifier.align(Alignment.CenterHorizontally)) { Text("Back to sign in") }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::continueAsGuest,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("SCAN WITHOUT AN ACCOUNT", fontWeight = FontWeight.Bold) }
            Text(
                "Documents stay on this device. Sign in later to enable encrypted cloud backup.",
                Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PaywallScreen(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as Activity
    val orderedPackages = state.packages.sortedByDescending { it.packageType.name.contains("ANNUAL") }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("RECTO / PRO", color = RegistrationMagenta, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                        Text("MEASURED ACCESS", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(
                        onClick = viewModel::continueWithoutPro,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) { Icon(Icons.Outlined.Close, "Continue with free access") }
                }
                Surface(
                    color = RegistrationMagenta.copy(alpha = .12f),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, RegistrationMagenta.copy(alpha = .35f), RoundedCornerShape(26.dp))
                ) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Scan with proof, not hope.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                        Text("Unlock every format, encrypted backup and searchable PDF export.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    FeatureLine(Icons.Outlined.AutoAwesome, "Live ProofCheck quality gate")
                    FeatureLine(Icons.Outlined.AutoAwesome, "Searchable PDF & Text Export")
                    FeatureLine(Icons.Outlined.CloudDone, "Encrypted account backup")
                    FeatureLine(Icons.Outlined.Lock, "PDF, JPEG, PNG, WebP and ZIP formats")
                }
                Text("CHOOSE YOUR PLAN", style = MaterialTheme.typography.labelLarge, color = CalibrationCyan, fontWeight = FontWeight.Black)
                when {
                    state.isLoading && orderedPackages.isEmpty() -> Box(Modifier.fillMaxWidth().height(116.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CalibrationCyan)
                    }
                    orderedPackages.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        orderedPackages.forEach { pkg ->
                            PackageChoice(pkg, state.selectedPackageId == pkg.identifier) { viewModel.selectPackage(pkg.identifier) }
                        }
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StaticPlan("YEARLY", "$69.99 / year", "Best value")
                        StaticPlan("MONTHLY", "$9.99 / month", null)
                        Text(
                            state.message ?: "Google Play plans could not be loaded. Check your connection and try again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = viewModel::loadPackages, modifier = Modifier.fillMaxWidth()) { Text("RETRY PLAN LOADING") }
                    }
                }
                if (orderedPackages.isNotEmpty()) state.message?.let {
                    Text(it, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = { viewModel.purchase(activity) },
                    enabled = !state.isLoading && state.isBillingConfigured && state.selectedPackageId != null,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CalibrationCyan, contentColor = Color.Black)
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                    else Text("UNLOCK RECTO PRO", fontWeight = FontWeight.Black)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = viewModel::restorePurchases, enabled = !state.isLoading) { Text("RESTORE") }
                    TextButton(onClick = viewModel::continueWithoutPro) { Text("CONTINUE FREE") }
                }
                Text(
                    "Subscriptions renew automatically unless canceled in Google Play before the current period ends. Prices shown by Google Play are localized and are the final prices charged.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeatureLine(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = CalibrationCyan)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PackageChoice(pkg: Package, selected: Boolean, onClick: () -> Unit) {
    val isAnnual = pkg.packageType.name.contains("ANNUAL")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (selected) CalibrationCyan.copy(alpha = .09f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) CalibrationCyan else MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(17.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.CheckCircle, null, tint = if (selected) CalibrationCyan else MaterialTheme.colorScheme.outline)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isAnnual) "YEARLY" else "MONTHLY", fontWeight = FontWeight.Black)
                    if (isAnnual) Surface(color = RegistrationMagenta, shape = CircleShape) {
                        Text("BEST VALUE", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
                }
                Text(if (isAnnual) "One payment every 12 months" else "Flexible monthly access", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(pkg.product.price.formatted, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StaticPlan(title: String, price: String, badge: String?) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)).padding(17.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Black)
            badge?.let { Text(it, color = RegistrationMagenta, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) }
        }
        Text(price, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingPreview() {
    AppTheme {
        OnboardingScreen(onComplete = {})
    }
}

@Preview(showBackground = true)
@Composable
fun AuthPreview() {
    AppTheme {
        AuthScreen(state = AppUiState(isAccountServiceConfigured = true), viewModel = androidx.lifecycle.viewmodel.compose.viewModel())
    }
}

@Preview(showBackground = true)
@Composable
fun PaywallPreview() {
    AppTheme {
        PaywallScreen(state = AppUiState(isBillingConfigured = true), viewModel = androidx.lifecycle.viewmodel.compose.viewModel())
    }
}
