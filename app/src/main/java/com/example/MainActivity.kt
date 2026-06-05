package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PremiumToolsScreen
import com.example.ui.theme.DocfusionTheme
import com.example.ui.viewmodel.DocfusionViewModel
import com.example.util.AdMobManager

enum class AppScreen {
    Home,
    Dashboard,
    Premium
}

class MainActivity : ComponentActivity() {

    private val viewModel: DocfusionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Support nice premium full bleeds
        enableEdgeToEdge()

        // Initialize AdMob managers immediately
        AdMobManager.initialize(this)

        setContent {
            DocfusionTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.Home) }

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "screen_routing"
                    ) { screen ->
                        when (screen) {
                            AppScreen.Home -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToDashboard = { currentScreen = AppScreen.Dashboard },
                                    onNavigateToPremium = { currentScreen = AppScreen.Premium }
                                )
                            }
                            AppScreen.Dashboard -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onBack = { currentScreen = AppScreen.Home }
                                )
                            }
                            AppScreen.Premium -> {
                                PremiumToolsScreen(
                                    viewModel = viewModel,
                                    onBack = { currentScreen = AppScreen.Home }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Prefetch ads when returning to the application
        AdMobManager.loadInterstitial(this)
        AdMobManager.loadRewarded(this)
    }
}
