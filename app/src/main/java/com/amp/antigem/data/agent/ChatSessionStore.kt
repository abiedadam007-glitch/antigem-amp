package com.amp.antigem.data.agent

import com.amp.antigem.domain.model.ChatAttachment
import com.amp.antigem.domain.model.ChatMessage
import com.amp.antigem.domain.model.Conversation
import com.amp.antigem.domain.model.ToolCall
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * State of the chat on screen, shared between ChatViewModel and the agent backend that owns the active conversation.
 */
class ChatSessionStore {
    val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val currentConversation = MutableStateFlow<Conversation?>(null)
    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val conversationError = MutableStateFlow<String?>(null)
    val userEmail = MutableStateFlow<String?>(null)
    val attachments = MutableStateFlow<List<ChatAttachment>>(emptyList())
    val terminatedToolDialog = MutableStateFlow<Pair<ToolCall, String>?>(null)
}
