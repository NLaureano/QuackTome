package com.example.quacktome

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quacktome.ui.theme.QuackTomeTheme
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun AppLayout(
    llm: LlmInference?,
    useDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    dataRepository: DataRepository // <-- Receive the repository
) {
    var sidebarExpanded by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Load conversations from the repository
    var conversations by remember { mutableStateOf(dataRepository.loadConversations()) }

    var activeConversationId by remember {
        // Default to the first conversation's ID if available
        mutableStateOf(conversations.firstOrNull()?.id)
    }

    val activeConversation = conversations.find { it.id == activeConversationId }
    val coroutineScope = rememberCoroutineScope()

    // --- Save conversations whenever they change ---
    LaunchedEffect(conversations) {
        dataRepository.saveConversations(conversations)
    }

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
                it.copy(messages = it.messages + Message(text, isFromUser = true))
            } else {
                it
            }
        }

        coroutineScope.launch {
            val result = if (llm != null) {
                withContext(Dispatchers.IO) {
                    llm.generateResponse(text)
                }
            } else {
                // Add filler AI message for emulator
                "This is a filler response for testing."
            }

            conversations = conversations.map {
                if (it.id == conversationId) {
                    it.copy(messages = it.messages + Message(result, isFromUser = false))
                } else {
                    it
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Chat content fills the screen
        ChatScreen(
            conversation = activeConversation,
            onSendMessage = { onSendMessage(it) },
            llm = llm,
            modifier = Modifier.fillMaxSize()
        )

        // Sidebar slides over the chat screen
        val sidebarWidth = 250.dp
        val animatedOffsetX by animateDpAsState(targetValue = if (sidebarExpanded) 0.dp else -sidebarWidth, label = "")
        Sidebar(
            modifier = Modifier.offset(x = animatedOffsetX).width(sidebarWidth),
            conversations = conversations,
            onSelectConversation = { activeConversationId = it.id },
            onCreateNew = { createNewConversation() },
            onDeleteConversation = { onDeleteConversation(it) },
            onShowAbout = { showAboutDialog = true },
            onShowSettings = { showSettingsDialog = true }
        )

        // Toggle button always in top-left, on top of everything
        Button(
            onClick = { sidebarExpanded = !sidebarExpanded },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) {
            Text(if (sidebarExpanded) "<<" else ">>")
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            isDarkTheme = useDarkTheme,
            onThemeChange = onThemeChange
        )
    }
}

@Composable
fun Sidebar(
    conversations: List<Conversation>,
    onSelectConversation: (Conversation) -> Unit,
    onCreateNew: () -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onShowAbout: () -> Unit,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Spacer to avoid being covered by the toggle button
            Spacer(Modifier.height(56.dp))

            // --- New Chat Button ---
            Button(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
                Text("New Chat")
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
                            text = chat.name,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onDeleteConversation(chat) }) {
                            Text("X")
                        }
                    }
                }
            }

            // --- Settings button ---
            Button(
                onClick = onShowSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Settings")
            }

            Spacer(Modifier.height(8.dp))

            // --- About button pinned at bottom ---
            Button(
                onClick = onShowAbout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("About")
            }
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
        // --- Top Bar ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Runic Tome",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (conversation == null || conversation.messages.isEmpty()) {
                val context = LocalContext.current
                val imageBitmap = remember(context) { // Remember the loaded bitmap
                    try {
                        val inputStream = context.assets.open("RUNICLogoGlowTransparent.png")
                        BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                    } catch (e: IOException) {
                        null // Return null if loading fails
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Logo",
                                modifier = Modifier.fillMaxSize(0.5f),
                                alpha = 0.5f
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select or create a conversation to get started.")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(conversation.messages) { message ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(text = if (message.isFromUser) "You: " else "AI: ")
                            Text(text = message.text, modifier = Modifier.weight(1f))
                        }
                    }
                }
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
                enabled = conversation != null,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About QuackTome") },
        text = { Text("This is a simple chat application that uses a local AI model to generate responses. More information will be added here soon.") },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit, isDarkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Theme", modifier = Modifier.weight(1f))
                    Switch(checked = isDarkTheme, onCheckedChange = onThemeChange)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    QuackTomeTheme {
        AppLayout(llm = null, useDarkTheme = false, onThemeChange = {}, dataRepository = DataRepository(LocalContext.current))
    }
}
