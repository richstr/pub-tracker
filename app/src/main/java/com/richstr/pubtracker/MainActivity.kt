package com.richstr.pubtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.richstr.pubtracker.ui.AppNavHost
import com.richstr.pubtracker.ui.theme.PubTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PubTrackerTheme {
                AppNavHost()
            }
        }
    }
}
