package com.jupiterconnect

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiterconnect.ui.theme.JupiterColors

/** Branded opening screen using the supplied scooter mark. */
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "Jupiter Connect",
                        modifier = Modifier.size(180.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "JUPITER CONNECT",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 4.sp,
                        color = Color(0xFFF2F2F4),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "JUPITER 125 · SMARTXONNECT",
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        color = JupiterColors.OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1500L)
    }
}
