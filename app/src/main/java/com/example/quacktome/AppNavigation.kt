package com.example.quacktome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.quacktome.ui.SplashScreen
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.delay

@Composable
fun AppNavigation(llm: LlmInference?, dataRepository: DataRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen()
            LaunchedEffect(Unit) {
                delay(2000) // 2-second delay
                navController.navigate("main") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
        composable("main") {
            val useDarkTheme = dataRepository.loadThemePreference()
            AppLayout(
                llm = llm,
                useDarkTheme = useDarkTheme,
                onThemeChange = { useDarkTheme -> dataRepository.saveThemePreference(useDarkTheme) },
                dataRepository = dataRepository
            )
        }
    }
}