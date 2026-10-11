package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.enableEdgeToEdge
import com.example.ui.NativeFeedScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember {
                getSharedPreferences("jhd_settings", Context.MODE_PRIVATE)
            }
            var darkTheme by remember {
                mutableStateOf(preferences.getBoolean("dark_theme", false))
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                NativeFeedScreen(
                    darkTheme = darkTheme,
                    onThemeChange = { enabled ->
                        darkTheme = enabled
                        preferences.edit().putBoolean("dark_theme", enabled).apply()
                    }
                )
            }
        }
    }
}
