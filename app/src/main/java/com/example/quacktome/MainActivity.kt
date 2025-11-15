package com.example.quacktome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.quacktome.ui.theme.QuackTomeTheme
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.launch
import java.io.File

// --- Data Classes ---
data class Message(val text: String, val isFromUser: Boolean)
data class Conversation(val id: Int, var name: String, val messages: List<Message>)

class MainActivity : ComponentActivity() {
    private var llm: LlmInference? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelPath = "/data/local/tmp/llm/gemma3-1b-it-int4.task"
        val modelFile = File(modelPath)

        if (modelFile.exists()) {
            val taskOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTopK(64)
                .build()
            llm = LlmInference.createFromOptions(this, taskOptions)
        }

        setContent {
            QuackTomeTheme {
                AppLayout(llm)
            }
        }
    }
}

@Composable
fun AppLayout(llm: LlmInference?) {
    var sidebarExpanded by remember { mutableStateOf(true) }

    var conversations by remember {
        mutableStateOf(
            listOf(Conversation(id = 1, name = "Chat 1", messages = emptyList()))
        )
    }
    var activeConversationId by remember { mutableStateOf<Int?>(1) }

    val activeConversation = conversations.find { it.id == activeConversationId }
    val coroutineScope = rememberCoroutineScope()

    fun createNewConversation() {
        val newId = (conversations.maxOfOrNull { it.id } ?: 0) + 1
        val newConversation = Conversation(id = newId, name = "Chat $newId", messages = emptyList())
        conversations = conversations + newConversation
        activeConversationId = newId
    }

    fun onDeleteConversation(conversation: Conversation) {
        conversations = conversations.filterNot { it.id == conversation.id }
        if (activeConversationId == conversation.id) {
            activeConversationId = conversations.firstOrNull()?.id
        }
    }

    fun onSendMessage(text: String) {
        val conversationId = activeConversationId ?: return

        // Add user message by creating a new list of conversations
        conversations = conversations.map {
            if (it.id == conversationId) {
                it.copy(messages = it.messages + Message("You: $text", isFromUser = true))
            } else {
                it
            }
        }

        coroutineScope.launch {
            llm?.let { llmInference ->
                val result = llmInference.generateResponse(text)
                // Add AI message by creating a new list of conversations
                conversations = conversations.map {
                    if (it.id == conversationId) {
                        it.copy(messages = it.messages + Message("AI: $result", isFromUser = false))
                    } else {
                        it
                    }
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(
            conversations = conversations,
            onSelectConversation = { activeConversationId = it.id },
            onCreateNew = { createNewConversation() },
            onDeleteConversation = { onDeleteConversation(it) },
            expanded = sidebarExpanded,
            onToggle = { sidebarExpanded = !sidebarExpanded }
        )
        ChatScreen(
            conversation = activeConversation,
            onSendMessage = { onSendMessage(it) },
            llm = llm,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun Sidebar(
    conversations: List<Conversation>,
    onSelectConversation: (Conversation) -> Unit,
    onCreateNew: () -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val width by animateDpAsState(targetValue = if (expanded) 250.dp else 60.dp, label = "")

    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(8.dp)
    ) {
        // --- Toggle Button ---
        Button(onClick = onToggle) {
            Text(if (expanded) "<<" else ">>")
        }

        Spacer(Modifier.height(8.dp))

        // --- New Chat Button ---
        Button(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "New Chat" else "+")
        }

        Spacer(Modifier.height(8.dp))

        // --- List of chats ---
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(conversations, key = { it.id }) { chat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectConversation(chat) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) chat.name else "•",
                        modifier = Modifier.weight(1f)
                    )
                    if (expanded) {
                        Button(onClick = { onDeleteConversation(chat) }) {
                            Text("X")
                        }
                    }
                }
            }
        }

        // --- About button pinned at bottom ---
        Button(
            onClick = { /* Show About page */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (expanded) "About" else "A")
        }
    }
}

@Composable
fun ChatScreen(
    conversation: Conversation?,
    onSendMessage: (String) -> Unit,
    llm: LlmInference?,
    modifier: Modifier = Modifier
) {
    val inputText = remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        if (conversation == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select or create a conversation.")
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            items(conversation.messages) { message ->
                Text(text = message.text, modifier = Modifier.padding(4.dp))
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
                        onSendMessage(userText)
                        inputText.value = ""
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
        AppLayout(llm = null)
    }
}
