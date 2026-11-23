package com.amp.antigem.data.agent.harness

import com.amp.antigem.domain.model.ChatMessage
import com.amp.antigem.domain.model.MessageRole
import com.amp.antigem.domain.model.ToolCall
import com.amp.antigem.domain.model.ToolType
import java.util.UUID

class HarnessTranscript(var sessionId: String = "") {

    class Turn(
        val id: String = UUID.randomUUID().toString(),
        val role: MessageRole,
        var content: String = "",
        val toolCalls: MutableList<ToolCall> = mutableListOf(),
        var reasoning: String? = null,
        var isStreaming: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val turns = mutableListOf<Turn>()

    fun appendUser(text: String): Turn {
        val t = Turn(role = MessageRole.USER, content = text)
        turns.add(t)
        return t
    }

    fun startAssistant(): Turn {
        val t = Turn(role = MessageRole.ASSISTANT, isStreaming = true)
        turns.add(t)
        return t
    }

    fun currentAssistant(): Turn? = turns.lastOrNull { it.role == MessageRole.ASSISTANT }

    fun toChatMessages(): List<ChatMessage> {
        return turns.map { turn ->
            ChatMessage(
                id = turn.id,
                conversationId = sessionId,
                role = turn.role,
                content = turn.content,
                thoughtText = turn.reasoning,
                toolCalls = turn.toolCalls.toList(),
                createdAt = turn.createdAt,
                isStreaming = turn.isStreaming
            )
        }
    }

    fun clear() {
        turns.clear()
    }
}
