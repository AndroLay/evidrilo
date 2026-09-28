package dev.nextgen.mobile

import androidx.compose.runtime.Composable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal actual fun createChatSessionStore(): ChatSessionStore = JvmChatSessionStore()

private class JvmChatSessionStore : ChatSessionStore {
    private val historyPath = Path.of(System.getProperty("user.home"), ".evidrilo", "chat-history-v1.json")

    override fun load(): Result<List<ChatSession>> = runCatching {
        if (!Files.exists(historyPath)) return@runCatching emptyList()
        val encoded = Files.readString(historyPath)
        ChatHistoryCodec.decode(encoded) ?: error("Chat history could not be read")
    }

    override fun save(sessions: List<ChatSession>): Boolean = runCatching {
        Files.createDirectories(historyPath.parent)
        val staging = historyPath.resolveSibling("chat-history-v1.tmp")
        Files.writeString(staging, ChatHistoryCodec.encode(sessions))
        try {
            Files.move(staging, historyPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(staging, historyPath, StandardCopyOption.REPLACE_EXISTING)
        }
        true
    }.getOrDefault(false)

    override fun deleteAttachment(id: String) = Unit
}

@Composable
internal actual fun rememberChatFilePicker(onResult: (ChatFilePickResult) -> Unit): () -> Unit = {
    onResult(ChatFilePickResult.Failed("File selection is currently available on Android only."))
}
