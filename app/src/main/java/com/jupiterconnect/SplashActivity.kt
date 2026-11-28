package com.jupiterconnect

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.jupiterconnect.sync.SupabaseClient
import com.jupiterconnect.ui.CloudLoginDialog
import com.jupiterconnect.ui.SplashScreen
import com.jupiterconnect.ui.theme.JupiterConnectTheme

/** Launcher activity: branded splash, then sign-in popup if needed, then Home. */
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JupiterConnectTheme {
                val showLogin = remember { mutableStateOf(false) }
                SplashScreen(
                    onDone = {
                        if (SupabaseClient.currentEmail() == null) {
                            showLogin.value = true
                        } else {
                            startHome()
                        }
                    },
                )
                if (showLogin.value) {
                    // Sign-in appears only after the loading animation, as a
                    // popup over the splash - never as a screen inside the app.
                    CloudLoginDialog(
                        onDismiss = { showLogin.value = false },
                        onSuccess = { startHome() },
                    )
                }
            }
        }
    }

    private fun startHome() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(0, 0)
        finish()
    }
}
