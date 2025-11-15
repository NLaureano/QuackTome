package com.example.quacktome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.quacktome.ui.theme.QuackTomeTheme
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private var llm: LlmInference? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelPath = "/data/local/tmp/llm/gemma3-1b-it-int4.task"
        val modelFile = File(modelPath)

        if (modelFile.exists()) {
            // Set the configuration options for the LLM Inference task
            val taskOptions = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTopK(64)
                .build()

            // Create an instance of the LLM Inference task
            llm = LlmInference.createFromOptions(this, taskOptions)
        }

        setContent {
            QuackTomeTheme {
                ChatScreen(llm)
            }
        }
    }
}

@Composable
fun ChatScreen(llm: LlmInference?) {
    val messages = remember { mutableStateOf(listOf<String>()) }
    val inputText = remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            items(messages.value) { message ->
                Text(text = message, modifier = Modifier.padding(4.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            TextField(
                value = inputText.value,
                onValueChange = { inputText.value = it },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val userText = inputText.value.trim()
                    if (userText.isNotEmpty()) {
                        messages.value += "You: $userText"
                        inputText.value = ""
                        coroutineScope.launch {
                            llm?.let {
                                val result = it.generateResponse(userText)
                                messages.value += "AI: $result"
                            }
                        }
                    }
                },
                enabled = llm != null,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Send")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    QuackTomeTheme {
        ChatScreen(llm = null)
    }
}
