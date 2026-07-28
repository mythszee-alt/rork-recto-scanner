package com.rork.recto.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.Package
import com.rork.recto.BuildConfig
import com.rork.recto.data.AuthRepository
import com.rork.recto.data.BillingService
import com.rork.recto.data.RectoSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppGate { ONBOARDING, AUTH, PAYWALL, LIBRARY }
enum class AuthMode { SIGN_IN, CREATE, RESET }

data class AppUiState(
    val gate: AppGate = AppGate.ONBOARDING,
    val authMode: AuthMode = AuthMode.SIGN_IN,
    val session: RectoSession? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val packages: List<Package> = emptyList(),
    val selectedPackageId: String? = null,
    val isBillingConfigured: Boolean = BuildConfig.REVENUECAT_API_KEY.isNotBlank(),
    val hasProAccess: Boolean = false,
    val isFreeMode: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("recto_preferences", 0)
    private val auth = AuthRepository(application)
    private val billing = BillingService()
    private val restored = auth.restoredSession()
    private val _uiState = MutableStateFlow(
        AppUiState(
            gate = when {
                !preferences.getBoolean("onboarding_complete", false) -> AppGate.ONBOARDING
                restored == null -> AppGate.AUTH
                else -> AppGate.PAYWALL
            },
            session = restored
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        restored?.let { session ->
            billing.identify(session.user.id)
            refreshSubscription()
        }
    }

    fun completeOnboarding() {
        preferences.edit().putBoolean("onboarding_complete", true).apply()
        _uiState.update { it.copy(gate = if (it.session == null) AppGate.AUTH else AppGate.PAYWALL) }
    }

    fun setAuthMode(mode: AuthMode) = _uiState.update { it.copy(authMode = mode, message = null) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }
    fun reportError(message: String) = _uiState.update { it.copy(message = message) }

    fun authenticate(email: String, password: String) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _uiState.update { it.copy(message = "Enter a valid email address") }
            return
        }
        if (_uiState.value.authMode != AuthMode.RESET && password.length < 8) {
            _uiState.update { it.copy(message = "Password must contain at least 8 characters") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            if (_uiState.value.authMode == AuthMode.RESET) {
                val result = auth.sendPasswordReset(email.trim())
                _uiState.update { it.copy(isLoading = false, message = result.fold({ "Password reset email sent" }, { error -> error.message })) }
                return@launch
            }
            val result = if (_uiState.value.authMode == AuthMode.CREATE) auth.signUp(email.trim(), password) else auth.signIn(email.trim(), password)
            result.fold(
                onSuccess = { session ->
                    if (session == null) {
                        _uiState.update { it.copy(isLoading = false, message = "Check your inbox to confirm your account, then sign in", authMode = AuthMode.SIGN_IN) }
                    } else {
                        billing.identify(session.user.id)
                        _uiState.update { it.copy(isLoading = false, session = session, gate = AppGate.PAYWALL) }
                        loadPackages()
                    }
                },
                onFailure = { error -> _uiState.update { it.copy(isLoading = false, message = error.message ?: "Sign-in failed") } }
            )
        }
    }

    fun googleUrl(): Result<String> = runCatching { auth.googleAuthorizeUrl() }

    fun acceptOAuth(uri: Uri) {
        val encoded = uri.fragment ?: uri.query ?: return
        auth.acceptOAuthCallback(encoded).fold(
            onSuccess = { session ->
                billing.identify(session.user.id)
                _uiState.update { it.copy(session = session, gate = AppGate.PAYWALL, message = null) }
                loadPackages()
            },
            onFailure = { error -> _uiState.update { it.copy(message = error.message) } }
        )
    }

    fun continueWithoutPro() {
        _uiState.update { it.copy(gate = AppGate.LIBRARY, isFreeMode = true, message = null) }
    }

    fun showPaywall() {
        _uiState.update { it.copy(gate = AppGate.PAYWALL, isFreeMode = false, message = null) }
        loadPackages()
    }

    fun loadPackages() {
        _uiState.update { it.copy(isLoading = true, message = null) }
        billing.loadPackages { packages, error ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    packages = packages,
                    selectedPackageId = packages.firstOrNull { pkg -> pkg.packageType.name.contains("ANNUAL") }?.identifier
                        ?: packages.firstOrNull()?.identifier,
                    message = error
                )
            }
        }
    }

    fun selectPackage(identifier: String) = _uiState.update { it.copy(selectedPackageId = identifier) }

    fun purchase(activity: android.app.Activity) {
        val selected = _uiState.value.packages.firstOrNull { it.identifier == _uiState.value.selectedPackageId }
        if (selected == null) {
            _uiState.update { it.copy(message = "Subscription products are not available yet") }
            return
        }
        _uiState.update { it.copy(isLoading = true, message = null) }
        billing.purchase(activity, selected) { active, error ->
            _uiState.update { it.copy(isLoading = false, gate = if (active) AppGate.LIBRARY else AppGate.PAYWALL, hasProAccess = active, isFreeMode = false, message = error) }
        }
    }

    fun restorePurchases() {
        _uiState.update { it.copy(isLoading = true, message = null) }
        billing.restore { active, error ->
            _uiState.update { it.copy(isLoading = false, gate = if (active) AppGate.LIBRARY else AppGate.PAYWALL, hasProAccess = active, isFreeMode = false, message = error) }
        }
    }

    fun refreshSubscription() {
        billing.refresh { active, error ->
            _uiState.update { it.copy(gate = if (active) AppGate.LIBRARY else AppGate.PAYWALL, hasProAccess = active, isFreeMode = false, message = error) }
            if (!active) loadPackages()
        }
    }

    fun requestAccountDeletion() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            auth.requestAccountDeletion(session).fold(
                onSuccess = {
                    auth.signOut(session)
                    billing.logOut()
                    _uiState.value = AppUiState(gate = AppGate.AUTH, message = "Deletion scheduled. Your cloud account and backups will be erased after 30 days.")
                },
                onFailure = { error -> _uiState.update { it.copy(isLoading = false, message = error.message) } }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut(_uiState.value.session)
            billing.logOut()
            _uiState.value = AppUiState(gate = AppGate.AUTH)
        }
    }
}
