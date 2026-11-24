package com.amp.antigem.data.agent.harness

import android.app.Application
import com.amp.antigem.data.agent.AgentChatBackend
import com.amp.antigem.data.agent.ChatSessionStore
import com.amp.antigem.domain.model.AgentKind
import com.amp.antigem.domain.model.ChatMessage
import com.amp.antigem.domain.model.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class HarnessChatBackend(
    private val application: Application,
    private val backendScope: CoroutineScope,
    private val store: ChatSessionStore,
    val prefs: HarnessPreferences,
    val client: HarnessBridgeClient = HarnessBridgeClient()
) : AgentChatBackend {

    override val kind: AgentKind = AgentKind.HARNESS

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()
    override val currentConversation: StateFlow<Conversation?> = store.currentConversation
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()
    private val _isLoadingConversation = MutableStateFlow(false)
    override val isLoadingConversation: StateFlow<Boolean> = _isLoadingConversation.asStateFlow()
    private val _conversationError = MutableStateFlow<String?>(null)
    override val conversationError: StateFlow<String?> = _conversationError.asStateFlow()

    private var activeStreamJob: Job? = null
    private val transcript = HarnessTranscript()

    fun start() {
        backendScope.launch(Dispatchers.IO) {
            try {
                client.start()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        stopStreaming()
    }

    override fun startNewChat() {
        stopStreaming()
        val id = UUID.randomUUID().toString()
        val conv = Conversation(
            id = id,
            title = "DeepSeek Harness",
            sessionId = id,
            agent = AgentKind.HARNESS,
            workspaceUri = "file:///data/user/0/com.amp.ai/files/home/projects"
        )
        transcript.clear()
        transcript.sessionId = id
        _messages.value = emptyList()
        store.currentConversation.value = conv
        _conversations.value = listOf(conv) + _conversations.value.filter { it.id != id }
    }

    override fun selectConversation(id: String) {
        val conv = _conversations.value.find { it.id == id } ?: return
        store.currentConversation.value = conv
        transcript.sessionId = id
        _messages.value = transcript.toChatMessages()
    }

    fun renameConversation(id: String, title: String) {
        val clean = title.trim()
        if (clean.isBlank()) return
        _conversations.value = _conversations.value.map {
            if (it.id == id) it.copy(title = clean) else it
        }
        store.currentConversation.value?.let {
            if (it.id == id) store.currentConversation.value = it.copy(title = clean)
        }
    }

    fun setArchived(id: String, archived: Boolean) {
        _conversations.value = _conversations.value.map {
            if (it.id == id) it.copy(archived = archived) else it
        }
        store.currentConversation.value?.let {
            if (it.id == id) store.currentConversation.value = it.copy(archived = archived)
        }
    }

    override fun sendMessage(content: String) {
        if (content.isBlank()) return
        val conv = store.currentConversation.value ?: run {
            startNewChat()
            store.currentConversation.value!!
        }

        transcript.appendUser(content)
        val assistantTurn = transcript.startAssistant()
        _messages.value = transcript.toChatMessages()
        _isStreaming.value = true
        _conversationError.value = null

        activeStreamJob?.cancel()
        activeStreamJob = backendScope.launch(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("session_id", conv.id)
                    put("prompt", content)
                    put("model", prefs.model.value)
                    put("effort", prefs.effort.value)
                }
                val res = client.rpc("session/prompt", params)
                assistantTurn.content = res.toString()
                assistantTurn.isStreaming = false
            } catch (e: Exception) {
                assistantTurn.content = "Error: ${e.message}"
                assistantTurn.isStreaming = false
                _conversationError.value = e.message
            } finally {
                _messages.value = transcript.toChatMessages()
                _isStreaming.value = false
            }
        }
    }

    override fun stopStreaming() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        transcript.currentAssistant()?.isStreaming = false
        _messages.value = transcript.toChatMessages()
        _isStreaming.value = false
    }

    override fun deleteConversation(id: String) {
        _conversations.value = _conversations.value.filter { it.id != id }
        if (store.currentConversation.value?.id == id) {
            startNewChat()
        }
    }

    override fun forkConversation(id: String) {
        startNewChat()
    }

    override fun retryConnections() {
        start()
    }

    override fun onAppForegrounded() {
        start()
    }
}
