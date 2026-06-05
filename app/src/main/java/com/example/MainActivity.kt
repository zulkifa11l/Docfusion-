package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DocFusionViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val viewModel: DocFusionViewModel = viewModel()
            val isDarkThemeEnabled = viewModel.isDarkMode

            MyApplicationTheme(darkTheme = isDarkThemeEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Startup security vault routing check
                    val startDestination = if (viewModel.isAppLocked) "lock" else "dashboard"

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        // 1. PIN Lock / Setup Screen
                        composable("lock") {
                            AppLockScreen(
                                viewModel = viewModel,
                                onUnlocked = {
                                    navController.navigate("dashboard") {
                                        popUpTo("lock") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // 2. Main Dashboard screen
                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToScan = { navController.navigate("scanner") },
                                onNavigateToConvert = { navController.navigate("converter") },
                                onNavigateToPdfTools = { navController.navigate("pdftools") },
                                onNavigateToOcr = { navController.navigate("ocr") },
                                onNavigateToHistory = { category -> navController.navigate("history/$category") },
                                onNavigateToNotes = { navController.navigate("notes") },
                                onNavigateToPhotoStudio = { navController.navigate("photo_studio") }
                            )
                        }

                        // 3. Document CamScanner screen
                        composable("scanner") {
                            ScannerScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onOcrScanned = { navController.navigate("ocr") }
                            )
                        }

                        // 4. File and Document conversion panel
                        composable("converter") {
                            ConverterScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // 5. Advanced PDF Toolkit
                        composable("pdftools") {
                            PdfToolsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Photo Studio & Passport Maker
                        composable("photo_studio") {
                            PhotoStudioScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // 6. Notes Text and Voice logger
                        composable("notes") {
                            NotesScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // 7. OCR & deep text processing
                        composable("ocr") {
                            OcrScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // 8. File Hub categories screen (Receives dynamic categories argument!)
                        composable(
                            route = "history/{category}",
                            arguments = listOf(navArgument("category") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val cat = backStackEntry.arguments?.getString("category") ?: "All"
                            DocumentHistoryScreen(
                                viewModel = viewModel,
                                initialCategory = cat,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPdfReader = { path ->
                                    val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
                                    navController.navigate("pdf_reader?path=$encodedPath")
                                }
                            )
                        }

                        // 9. Interactive PDF Reader Doodle and Signature Creator Canvas
                        composable(
                            route = "pdf_reader?path={path}",
                            arguments = listOf(navArgument("path") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val path = backStackEntry.arguments?.getString("path") ?: ""
                            val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
                            PdfReaderScreen(
                                viewModel = viewModel,
                                pdfPath = decodedPath,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
