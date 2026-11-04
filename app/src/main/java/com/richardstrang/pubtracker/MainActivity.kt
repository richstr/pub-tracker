package com.richardstrang.pubtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.richardstrang.pubtracker.ui.AppNavHost
import com.richardstrang.pubtracker.ui.theme.PubTrackerTheme

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
