package com.jupiterconnect

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jupiterconnect.ui.SplashScreen
import com.jupiterconnect.ui.theme.JupiterConnectTheme

/** Launcher activity: the branded splash is its own route before Home. */
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SplashActivity finishes after navigation, so any resume of the app
        // lands on MainActivity directly - this screen is always a cold start.
        setContent {
            JupiterConnectTheme {
                SplashScreen(onDone = {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                })
            }
        }
    }
}
