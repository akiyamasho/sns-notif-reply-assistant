package com.example.snsassistant.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.snsassistant.ui.screens.FeedScreen
import com.example.snsassistant.ui.screens.SettingsScreen
import com.example.snsassistant.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "feed") {
                        composable("feed") { FeedScreen(onOpenSettings = { navController.navigate("settings") }) }
                        composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
                    }
                }
            }
        }
    }
}

