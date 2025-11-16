package com.example.quacktome

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.quacktome.ui.theme.QuackTomeTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.BlockQuoteGutter
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.TableStyle
import com.halilibo.richtext.ui.material3.RichText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// --- Data Classes ---
data class Message(val text: String, val isFromUser: Boolean)
data class Conversation(val id: Int, var name: String, val messages: List<Message>)

class DataRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("QuackTomePrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val DEFAULT_MODEL_FILENAME = "gemma3-1b-it-int4.task"
    }

    // --- Theme Settings ---
    fun saveThemePreference(isDarkTheme: Boolean) {
        prefs.edit { putBoolean("dark_theme", isDarkTheme) }
    }

    fun loadThemePreference(): Boolean {
        return prefs.getBoolean("dark_theme", false)
    }

    fun deleteDefaultModel(): Boolean {
        val modelFile = getDefaultModelFile()
        return if (modelFile.exists()) {
            modelFile.delete()
        } else {
            false
        }
    }

    // --- Model Path ---
    fun saveModelPath(path: String) {
        prefs.edit { putString("model_path", path) }
    }

    fun loadModelPath(): String {
        return prefs.getString("model_path", "") ?: ""
    }

    fun getDefaultModelFile(): File {
        return File(context.getExternalFilesDir(null), DEFAULT_MODEL_FILENAME)
    }

    // --- Conversation History ---
    fun saveConversations(conversations: List<Conversation>) {
        val json = gson.toJson(conversations)
        prefs.edit { putString("conversations_json", json) }
    }

    fun loadConversations(): List<Conversation> {
        val json = prefs.getString("conversations_json", null)
        return if (json != null) {
            val type = object : TypeToken<List<Conversation>>() {}.type
            gson.fromJson(json, type)
        } else {
            listOf(Conversation(id = 1, name = "Chat 1", messages = emptyList()))
        }
    }
}

class MainActivity : ComponentActivity() {
    private var llm: LlmInference? = null
    private lateinit var dataRepository: DataRepository
    private var downloadId: Long = -1L
    private val modelPathChannel = Channel<String>(Channel.BUFFERED)

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != -1L && id == downloadId) {
                val query = DownloadManager.Query().setFilterById(id)
                val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex != -1 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                        val downloadedModelPath = dataRepository.getDefaultModelFile().absolutePath
                        modelPathChannel.trySend(downloadedModelPath)
                        Toast.makeText(context, "Model download complete!", Toast.LENGTH_SHORT).show()
                    }
                }
                cursor.close()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataRepository = DataRepository(applicationContext)

        ContextCompat.registerReceiver(
            this,
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        reloadLlm(dataRepository.loadModelPath())

        setContent {
            var useDarkTheme by remember { mutableStateOf(dataRepository.loadThemePreference()) }
            var modelPathState by remember { mutableStateOf(dataRepository.loadModelPath()) }
            var isDefaultModelDownloaded by remember { mutableStateOf(dataRepository.getDefaultModelFile().exists()) }
            var isDownloading by remember { mutableStateOf(false) }

            val onModelPathChange = { newPath: String ->
                modelPathState = newPath
                dataRepository.saveModelPath(newPath)
                reloadLlm(newPath)
            }

            val onDeleteDefaultModel = {
                if (dataRepository.deleteDefaultModel()) {
                    isDefaultModelDownloaded = false
                    if (modelPathState == dataRepository.getDefaultModelFile().absolutePath) {
                        onModelPathChange("") // Clear active model if it was the default one
                    }
                    Toast.makeText(this, "Default model deleted.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to delete default model.", Toast.LENGTH_SHORT).show()
                }
            }

            val onRefreshDefaultModelStatus = {
                isDefaultModelDownloaded = dataRepository.getDefaultModelFile().exists()
                if (isDefaultModelDownloaded) {
                    isDownloading = false
                }
                Toast.makeText(this, "Model status refreshed.", Toast.LENGTH_SHORT).show()
            }

            LaunchedEffect(Unit) {
                modelPathChannel.receiveAsFlow().collect { newPath ->
                    onModelPathChange(newPath)
                    isDefaultModelDownloaded = true
                    isDownloading = false
                }
            }

            QuackTomeTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppLayout(
                        llm = llm,
                        useDarkTheme = useDarkTheme,
                        onThemeChange = { newThemeValue ->
                            useDarkTheme = newThemeValue
                            dataRepository.saveThemePreference(newThemeValue)
                        },
                        dataRepository = dataRepository,
                        modelPath = modelPathState,
                        onModelPathChange = onModelPathChange,
                        onDownloadModelClick = {
                            startModelDownload()
                            isDownloading = true
                        },
                        onDeleteDefaultModelClick = onDeleteDefaultModel,
                        isDefaultModelDownloaded = isDefaultModelDownloaded,
                        onRefreshDefaultModelStatus = onRefreshDefaultModelStatus,
                        isDownloading = isDownloading
                    )
                }
            }
        }
    }

    private fun reloadLlm(modelPath: String) {
        llm = if (modelPath.isNotEmpty()) {
            val modelFile = File(modelPath)
            if (modelFile.exists()) {
                val taskOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTopK(64)
                    .build()
                LlmInference.createFromOptions(this, taskOptions)
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun startModelDownload() {
        val modelUrl = "https://github.com/NLaureano/model-dispenser/releases/download/v1.0-models/gemma3-1b-it-int4.task"
        val destinationFile = dataRepository.getDefaultModelFile()

        val request = DownloadManager.Request(modelUrl.toUri())
            .setTitle(DataRepository.DEFAULT_MODEL_FILENAME)
            .setDescription("Downloading LLM Model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(destinationFile.toUri())

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)
        Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
    }
}

@Composable
fun AppLayout(
    llm: LlmInference?,
    useDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    dataRepository: DataRepository,
    modelPath: String,
    isDefaultModelDownloaded: Boolean,
    onModelPathChange: (String) -> Unit,
    onDownloadModelClick: () -> Unit,
    onDeleteDefaultModelClick: () -> Unit,
    onRefreshDefaultModelStatus: () -> Unit,
    isDownloading: Boolean
) {
    var sidebarExpanded by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }

    var conversations by remember { mutableStateOf(dataRepository.loadConversations()) }
    var activeConversationId by remember { mutableStateOf(conversations.firstOrNull()?.id) }

    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val newPath = getPathFromUri(context, uri)
                onModelPathChange(newPath)
            }
        }
    }

    val activeConversation = conversations.find { it.id == activeConversationId }
    val coroutineScope = rememberCoroutineScope()

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
        isGenerating = true

        conversations = conversations.map {
            if (it.id == conversationId) {
                it.copy(messages = it.messages + Message(text, isFromUser = true))
            } else it
        }

        coroutineScope.launch {
            val result = if (llm != null) {
                withContext(Dispatchers.IO) {
                    llm.generateResponse(text)
                }
            } else {
                "No model loaded. Please select or download a model in Settings."
            }

            conversations = conversations.map {
                if (it.id == conversationId) {
                    it.copy(messages = it.messages + Message(result, isFromUser = false))
                } else it
            }
            isGenerating = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChatScreen(
            conversation = activeConversation,
            onSendMessage = { onSendMessage(it) },
            isGenerating = isGenerating,
            modifier = Modifier.fillMaxSize()
        )

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

        Button(
            onClick = { sidebarExpanded = !sidebarExpanded },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) {
            Text(if (sidebarExpanded) "<<" else ">>")
        }
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            modelPath = modelPath,
            defaultModelPath = dataRepository.getDefaultModelFile().absolutePath,
            isDefaultModelDownloaded = isDefaultModelDownloaded,
            onRefresh = onRefreshDefaultModelStatus
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            isDarkTheme = useDarkTheme,
            onThemeChange = onThemeChange,
            modelPath = modelPath,
            onModelPathChange = onModelPathChange,
            onSelectModelClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                filePicker.launch(intent)
            },
            onDownloadModelClick = onDownloadModelClick,
            onDeleteDefaultModelClick = onDeleteDefaultModelClick,
            defaultModelPath = dataRepository.getDefaultModelFile().absolutePath,
            isDefaultModelDownloaded = isDefaultModelDownloaded,
            onRefreshDefaultModelStatus = onRefreshDefaultModelStatus,
            isDownloading = isDownloading
        )
    }
}

fun getPathFromUri(context: Context, uri: Uri): String {
    val contentResolver = context.contentResolver
    val fileName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
    } ?: "temp_model.task"

    val file = File(context.cacheDir, fileName)
    try {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    } catch (_: IOException) {
        return ""
    }
    return file.absolutePath
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
    Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(8.dp)) {
            Spacer(Modifier.height(56.dp))
            Button(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) { Text("New Chat") }
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(conversations, key = { it.id }) { chat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectConversation(chat) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = chat.name, modifier = Modifier.weight(1f))
                        Button(onClick = { onDeleteConversation(chat) }) { Text("X") }
                    }
                }
            }
            Button(onClick = onShowSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onShowAbout, modifier = Modifier.fillMaxWidth()) { Text("About") }
        }
    }
}

@Composable
fun getCustomRichTextStyle(): RichTextStyle {
    val colors = MaterialTheme.colorScheme
    return RichTextStyle(
        codeBlockStyle = CodeBlockStyle(
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant).padding(16.dp)
        ),
        blockQuoteGutter = BlockQuoteGutter.BarGutter(barWidth = 4.sp, color = { colors.primary }),
        tableStyle = TableStyle(borderColor = colors.onSurface.copy(alpha = 0.5f), headerTextStyle = TextStyle(fontWeight = FontWeight.Bold), cellPadding = 8.sp)
    )
}

@Composable
fun MessageInput(
    onSendMessage: (String) -> Unit,
    isGenerating: Boolean,
    isConversationSelected: Boolean
) {
    var inputText by remember { mutableStateOf("") }

    Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.weight(1f),
            enabled = !isGenerating
        )
        Button(
            onClick = {
                val userText = inputText.trim()
                if (userText.isNotEmpty()) {
                    onSendMessage(userText)
                    inputText = ""
                }
            },
            enabled = isConversationSelected && !isGenerating,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text("Send")
        }
    }
}

@Composable
fun ChatScreen(
    conversation: Conversation?,
    onSendMessage: (String) -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Text(text = "Runic Tome", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Box(modifier = Modifier.weight(1f)) {
            if (conversation == null || conversation.messages.isEmpty()) {
                val context = LocalContext.current
                val imageBitmap = remember(context) {
                    try {
                        context.assets.open("RUNICLogoGlowTransparent.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                    } catch (_: IOException) {
                        null
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (imageBitmap != null) {
                            Image(bitmap = imageBitmap, contentDescription = "Logo", modifier = Modifier.fillMaxSize(0.5f), alpha = 0.5f)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select or create a conversation to get started.")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(conversation.messages) { message ->
                        Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(text = if (message.isFromUser) "You: " else "AI: ")
                            if (message.isFromUser) {
                                Text(text = message.text)
                            } else {
                                RichText(style = getCustomRichTextStyle(), modifier = Modifier.weight(1f)) {
                                    Markdown(content = message.text)
                                }
                            }
                        }
                    }
                }
            }
        }
        MessageInput(
            onSendMessage = onSendMessage,
            isGenerating = isGenerating,
            isConversationSelected = conversation != null
        )
    }
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    modelPath: String,
    defaultModelPath: String,
    isDefaultModelDownloaded: Boolean,
    onRefresh: () -> Unit
) {
    val modelInUse = when {
        modelPath.isEmpty() -> "None"
        modelPath == defaultModelPath -> "Default Model"
        else -> "Custom Model"
    }
    val defaultModelStatus = if (isDefaultModelDownloaded) "Downloaded" else "Not Downloaded"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About QuackTome") },
        text = {
            Column {
                Text("This is a simple chat application that uses a local AI model to generate responses.")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Currently using: $modelInUse")
                Text(
                    text = "Model path: ${modelPath.ifEmpty { "N/A" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Default Model Status: $defaultModelStatus")
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh status")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    modelPath: String,
    onModelPathChange: (String) -> Unit,
    onSelectModelClick: () -> Unit,
    onDownloadModelClick: () -> Unit,
    onDeleteDefaultModelClick: () -> Unit,
    defaultModelPath: String,
    isDefaultModelDownloaded: Boolean,
    onRefreshDefaultModelStatus: () -> Unit,
isDownloading: Boolean
) {
    val customModelPath = if (modelPath.isNotEmpty() && modelPath != defaultModelPath) modelPath else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark Theme", modifier = Modifier.weight(1f))
                    Switch(checked = isDarkTheme, onCheckedChange = onThemeChange)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("LLM Model", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                // Default Model Option
                Column {
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = (modelPath == defaultModelPath), onClick = { if (isDefaultModelDownloaded) onModelPathChange(defaultModelPath) }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (modelPath == defaultModelPath), onClick = { if (isDefaultModelDownloaded) onModelPathChange(defaultModelPath) }, enabled = isDefaultModelDownloaded)
                        Text("Default Model", modifier = Modifier.weight(1f).padding(start = 16.dp))
                        if (isDefaultModelDownloaded) {
                            IconButton(onClick = onDeleteDefaultModelClick) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete downloaded model")
                            }
                        }
                    }
                    if (!isDefaultModelDownloaded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onDownloadModelClick,
                                enabled = !isDownloading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isDownloading) "Downloading..." else "Download")
                            }
                            if (isDownloading) {
                                IconButton(onClick = onRefreshDefaultModelStatus) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh download status")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Model Option
                Column {
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = customModelPath.isNotEmpty(), onClick = { if (customModelPath.isNotEmpty()) onModelPathChange(customModelPath) }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = customModelPath.isNotEmpty(), onClick = { if(customModelPath.isNotEmpty()) onModelPathChange(customModelPath) } )
                        Text("Custom Model", modifier = Modifier.padding(start = 16.dp))
                    }
                    Button(onClick = onSelectModelClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Select File")
                    }
                    Text(
                        text = customModelPath.ifEmpty { "No custom model selected." },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    QuackTomeTheme {
        AppLayout(
            llm = null,
            useDarkTheme = false,
            onThemeChange = { },
            dataRepository = DataRepository(LocalContext.current),
            modelPath = "",
            onModelPathChange = { },
            onDownloadModelClick = { },
            onDeleteDefaultModelClick = { },
            isDefaultModelDownloaded = true,
            onRefreshDefaultModelStatus = {},
            isDownloading = false
        )
    }
}
