package com.example.quacktome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.quacktome.ui.theme.QuackTomeTheme
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class MainActivity : ComponentActivity() {

    private var llm: LlmInference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataRepository = DataRepository(applicationContext)

        // Initialize LLM safely
        val modelPath = "/data/local/tmp/llm/gemma-2b-it-cpu-int8.bin"
        if (File(modelPath).exists()) {
            try {
                val taskOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTopK(64)
                    .build()
                llm = LlmInference.createFromOptions(this, taskOptions)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            val useDarkTheme = dataRepository.loadThemePreference()
            QuackTomeTheme(darkTheme = useDarkTheme) {
                AppNavigation(llm = llm, dataRepository = dataRepository)
            }
        }
    }
}