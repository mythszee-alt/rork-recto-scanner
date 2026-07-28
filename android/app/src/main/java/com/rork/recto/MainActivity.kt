package com.rork.recto

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rork.recto.ui.navigation.AppNavigation
import com.rork.recto.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val authCallback = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authCallback.value = intent?.data
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNavigation(authCallback = authCallback)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        authCallback.value = intent.data
    }
}
