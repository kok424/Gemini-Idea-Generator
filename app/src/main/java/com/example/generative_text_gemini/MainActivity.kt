package com.example.generative_text_gemini

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generative_text_gemini.ui.theme.Generative_text_GeminiTheme
import com.example.generative_text_gemini.ui.theme.IdeaBlue
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(applicationContext)

        setContent {
            Generative_text_GeminiTheme {
                val viewModel: ChatViewModel = viewModel(
                    factory = ChatViewModelFactory(database.chatDao())
                )
                // アプリ全体の画面構成
                AppScreen(viewModel)
            }
        }
    }
}

// -------------------------------------------
// UI: ハンバーガーメニューを含む全体の構成
// -------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(viewModel: ChatViewModel) {
    // ドロワー（メニュー）の状態管理
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text("履歴一覧", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()

                // 新規チャットボタン
                NavigationDrawerItem(
                    label = { Text("＋ 新しいチャット") },
                    selected = false,
                    onClick = {
                        viewModel.createNewSession()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 過去のチャットリスト
                LazyColumn {
                    items(sessions) { session ->
                        NavigationDrawerItem(
                            label = { Text(session.title) },
                            selected = session.sessionId == currentSessionId,
                            onClick = {
                                viewModel.selectSession(session.sessionId)
                                scope.launch { drawerState.close() }
                            },
                            badge = {
                                // 削除ボタン
                                IconButton(onClick = { viewModel.deleteSession(session.sessionId) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "削除", modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        }
    ) {
        // メインコンテンツ（チャット画面）
        Scaffold(
            topBar = {
                // 上部のバー（ハンバーガーアイコンを表示）
                CenterAlignedTopAppBar(
                    title = { Text("Gemini Ideas") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "メニュー")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent // 背景透過
                    )
                )
            }
        ) { innerPadding ->
            // パディングを渡してチャット画面を表示
            Box(modifier = Modifier.padding(innerPadding)) {
                if (currentSessionId != null) {
                    IdeaChatScreen(viewModel)
                } else {
                    // セッションがない場合の表示
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("左上のメニューから\n新しいチャットを作成してください")
                    }
                }
            }
        }
    }
}

// -------------------------------------------
// UI: チャット画面 (前回と同じだが微調整)
// -------------------------------------------
@Composable
fun IdeaChatScreen(viewModel: ChatViewModel) {
    var inputText by remember { mutableStateOf("") }
    val messages by viewModel.currentMessages.collectAsState(initial = emptyList())

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.tertiary.copy(alpha=0.1f), Color(0xFFE3F2FD))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(messages) { message ->
                MessageBubble(message)
            }
        }

        if (viewModel.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f), // backgroundは削除
                placeholder = { Text("アイデアを入力...") },
                // ★ここを追加：どんな時でも「白背景・黒文字」にする魔法の設定
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,    // 入力中の背景：白
                    unfocusedContainerColor = Color.White,  // 普段の背景：白
                    focusedTextColor = Color.Black,         // 入力中の文字：黒
                    unfocusedTextColor = Color.Black,       // 普段の文字：黒
                    cursorColor = IdeaBlue                // カーソル（点滅する棒）は青にするとカッコいい
                )
            )
            IconButton(
                onClick = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                enabled = !viewModel.isLoading
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "送信", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (isUser) MaterialTheme.colorScheme.primary else Color.White
    val textColor = if (isUser) Color.White else Color.Black

    val timeString = remember(message.timestamp) {
        val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    val bubbleShape = if (isUser) {
        androidx.compose.foundation.shape.RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Surface(modifier = Modifier.size(32.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.tertiary) {
                Icon(Icons.Default.Face, contentDescription = "AI", tint = Color.White, modifier = Modifier.padding(4.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Surface(color = backgroundColor, shape = bubbleShape, shadowElevation = 2.dp, modifier = Modifier.widthIn(max = 280.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = message.text, color = textColor, style = MaterialTheme.typography.bodyLarge)
                    Text(text = timeString, color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
}

// -------------------------------------------
// Logic: ViewModel
// -------------------------------------------
data class ChatMessage(val text: String, val isUser: Boolean, val timestamp: Long)

class ChatViewModelFactory(private val dao: ChatDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) return ChatViewModel(dao) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatViewModel(private val chatDao: ChatDao) : ViewModel() {
    private val apiKey = "" // あなたのキー
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        safetySettings = listOf(SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.LOW_AND_ABOVE)),
        systemInstruction = content { text("あなたは優秀なアイデアマンです。") }
    )

    // セッション一覧
    val sessions = chatDao.getAllSessions().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 現在選択中のセッションID
    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId = _currentSessionId.asStateFlow()

    // 選択中のセッションのメッセージだけを取得（IDが変わると自動で切り替わる）
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMessages = _currentSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else chatDao.getMessagesFromSession(id).map { list ->
            list.map { ChatMessage(it.text, it.isUser, it.timestamp) }
        }
    }

    var isLoading by mutableStateOf(false)
        private set

    init {
        // 起動時に最新のセッションがあればそれを開く
        viewModelScope.launch {
            sessions.collect { list ->
                if (_currentSessionId.value == null && list.isNotEmpty()) {
                    _currentSessionId.value = list.first().sessionId
                } else if (list.isEmpty() && _currentSessionId.value == null) {
                    // 初回起動時などは自動で1個作る
                    createNewSession()
                }
            }
        }
    }

    fun selectSession(id: Long) {
        _currentSessionId.value = id
    }

    fun createNewSession() {
        viewModelScope.launch {
            val newSession = ChatSession(title = "新規チャット ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date())}")
            val newId = chatDao.insertSession(newSession)
            _currentSessionId.value = newId
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            chatDao.deleteMessagesBySession(id)
            chatDao.deleteSession(id)
            if (_currentSessionId.value == id) _currentSessionId.value = null
        }
    }

    fun sendMessage(userInput: String) {
        val sessionId = _currentSessionId.value ?: return
        if (userInput.isBlank()) return
        isLoading = true

        viewModelScope.launch {
            chatDao.insertMessage(ChatEntity(sessionId = sessionId, text = userInput, isUser = true))
            try {

                val response = generativeModel.generateContent(userInput)
                val aiText = response.text ?: "..."
                chatDao.insertMessage(ChatEntity(sessionId = sessionId, text = aiText, isUser = false))
            } catch (e: Exception) {
                chatDao.insertMessage(ChatEntity(sessionId = sessionId, text = "エラー: ${e.message}", isUser = false))
            } finally {
                isLoading = false
            }
        }
    }
}
