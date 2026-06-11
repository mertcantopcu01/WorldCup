package com.mert.dbrstudios.worldcup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mert.dbrstudios.worldcup.ui.screen.FixtureScreen
import com.mert.dbrstudios.worldcup.ui.theme.WorldCupTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorldCupTheme {
                FixtureScreen()
            }
        }
    }
}