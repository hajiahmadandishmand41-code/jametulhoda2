package com.jametulhoda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jametulhoda.app.ui.AppRoot
import com.jametulhoda.app.ui.theme.JametulHodaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JametulHodaTheme {
                AppRoot()
            }
        }
    }
}
