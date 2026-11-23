package com.amp.antigem

import androidx.compose.runtime.Composable
import com.amp.antigem.ui.chat.ChatScreen
import com.amp.antigem.ui.chat.ChatViewModel

@Composable
fun MainNavigation(viewModel: ChatViewModel? = null) {
    if (viewModel != null) {
        ChatScreen(viewModel = viewModel)
    } else {
        ChatScreen()
    }
}
