package com.smsdetector.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.smsdetector.app.ui.navigation.AppNavigation
import com.smsdetector.app.ui.theme.SmsDetectorTheme

class MainActivity : ComponentActivity() {
    private val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results observed via settings screen */ }

    private val requestDefaultSms = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result observed by recomposition */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions.launch(missing.toTypedArray())

        setContent {
            SmsDetectorTheme {
                AppNavigation(
                    onRequestDefaultSms = {
                        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                        }
                        requestDefaultSms.launch(intent)
                    },
                    isDefaultSms = {
                        Telephony.Sms.getDefaultSmsPackage(this) == packageName
                    }
                )
            }
        }
    }
}
