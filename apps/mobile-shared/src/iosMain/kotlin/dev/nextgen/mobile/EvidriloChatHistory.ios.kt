package dev.nextgen.mobile

import androidx.compose.runtime.Composable
import platform.Foundation.NSUserDefaults

private const val CHAT_HISTORY_KEY = "evidrilo.chat.history.v1"

internal actual fun createChatSessionStore(): ChatSessionStore = IosChatSessionStore()

private class IosChatSessionStore : ChatSessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): Result<List<ChatSession>> = runCatching {
        val encoded = defaults.stringForKey(CHAT_HISTORY_KEY) ?: return@runCatching emptyList()
        ChatHistoryCodec.decode(encoded) ?: error("Chat history could not be read")
    }

    override fun save(sessions: List<ChatSession>): Boolean = runCatching {
        val encoded = ChatHistoryCodec.encode(sessions)
        defaults.setObject(encoded, forKey = CHAT_HISTORY_KEY)
        defaults.stringForKey(CHAT_HISTORY_KEY) == encoded
    }.getOrDefault(false)

    override fun deleteAttachment(id: String) = Unit
}

@Composable
internal actual fun rememberChatFilePicker(onResult: (ChatFilePickResult) -> Unit): () -> Unit = {
    onResult(ChatFilePickResult.Failed("File selection is currently available on Android only."))
}
