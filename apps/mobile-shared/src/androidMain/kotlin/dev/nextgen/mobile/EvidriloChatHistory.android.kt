package dev.nextgen.mobile

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CHAT_HISTORY_PREFERENCES = "evidrilo_chat_history_v1"
private const val CHAT_HISTORY_KEY = "sessions"

public object AndroidChatHistoryStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun createStore(): ChatSessionStore = applicationContext?.let(::AndroidChatSessionStore)
        ?: UnavailableChatSessionStore()
}

internal actual fun createChatSessionStore(): ChatSessionStore = AndroidChatHistoryStorage.createStore()

private class AndroidChatSessionStore(private val context: Context) : ChatSessionStore {
    private val preferences = context.getSharedPreferences(CHAT_HISTORY_PREFERENCES, Context.MODE_PRIVATE)

    override fun load(): Result<List<ChatSession>> = runCatching {
        val encoded = preferences.getString(CHAT_HISTORY_KEY, null) ?: return@runCatching emptyList()
        ChatHistoryCodec.decode(encoded) ?: error("Chat history could not be read")
    }

    override fun save(sessions: List<ChatSession>): Boolean = runCatching {
        preferences.edit().putString(CHAT_HISTORY_KEY, ChatHistoryCodec.encode(sessions)).commit()
    }.getOrDefault(false)

    override fun deleteAttachment(id: String) {
        if (!validAttachmentId(id)) return
        File(File(context.filesDir, "evidrilo_chat_attachments"), id).delete()
    }
}

private class UnavailableChatSessionStore : ChatSessionStore {
    override fun load(): Result<List<ChatSession>> = Result.failure(IllegalStateException("Local chat storage is unavailable"))
    override fun save(sessions: List<ChatSession>): Boolean = false
    override fun deleteAttachment(id: String) = Unit
}

@Composable
internal actual fun rememberChatFilePicker(onResult: (ChatFilePickResult) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val currentResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { copyChatAttachment(context, uri) }
                currentResult.value(result)
            }
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

private fun copyChatAttachment(context: Context, uri: Uri): ChatFilePickResult {
    val resolver = context.contentResolver
    val metadata = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor: Cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                name to size
            }
    }.getOrNull()
    if ((metadata?.second ?: 0L) > CHAT_FILE_LIMIT_BYTES) return ChatFilePickResult.TooLarge

    val folder = File(context.filesDir, "evidrilo_chat_attachments")
    val id = UUID.randomUUID().toString()
    val destination = File(folder, id)
    return runCatching {
        if (!folder.exists() && !folder.mkdirs()) error("Cannot prepare local storage")
        var copied = 0L
        resolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > CHAT_FILE_LIMIT_BYTES) return ChatFilePickResult.TooLarge.also { destination.delete() }
                    output.write(buffer, 0, count)
                }
            }
        } ?: error("Cannot open selected file")
        ChatFilePickResult.Picked(
            ChatAttachment(
                id = id,
                name = metadata?.first?.take(100)?.ifBlank { null } ?: "Selected file",
                sizeBytes = copied,
                mimeType = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" },
            ),
        )
    }.getOrElse {
        destination.delete()
        ChatFilePickResult.Failed("The selected file could not be saved on this device.")
    }
}

private fun validAttachmentId(id: String): Boolean = runCatching {
    UUID.fromString(id).toString() == id
}.getOrDefault(false)
