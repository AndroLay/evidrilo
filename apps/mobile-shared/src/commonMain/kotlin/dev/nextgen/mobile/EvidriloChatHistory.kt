package dev.nextgen.mobile

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal const val CHAT_FILE_LIMIT_BYTES = 10L * 1024L * 1024L

internal data class ChatAttachment(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
)

internal data class ChatMessage(
    val fromLearner: Boolean,
    val text: String,
    val attachments: List<ChatAttachment> = emptyList(),
)

internal data class ChatSession(
    val id: String,
    val contextKey: String,
    val caseTitle: String,
    val title: String,
    val updatedAtMillis: Long,
    val messages: List<ChatMessage>,
)

internal sealed interface ChatFilePickResult {
    data class Picked(val attachment: ChatAttachment) : ChatFilePickResult
    data object TooLarge : ChatFilePickResult
    data class Failed(val reason: String) : ChatFilePickResult
}

internal interface ChatSessionStore {
    fun load(): Result<List<ChatSession>>
    fun save(sessions: List<ChatSession>): Boolean
    fun deleteAttachment(id: String)
}

internal expect fun createChatSessionStore(): ChatSessionStore

@Composable
internal expect fun rememberChatFilePicker(onResult: (ChatFilePickResult) -> Unit): () -> Unit

internal object ChatHistoryCodec {
    fun encode(sessions: List<ChatSession>): String = JsonArray(sessions.map { session ->
        buildJsonObject {
            put("id", session.id)
            put("contextKey", session.contextKey)
            put("caseTitle", session.caseTitle)
            put("title", session.title)
            put("updatedAtMillis", session.updatedAtMillis)
            put("messages", JsonArray(session.messages.map { message ->
                buildJsonObject {
                    put("fromLearner", message.fromLearner)
                    put("text", message.text)
                    put("attachments", JsonArray(message.attachments.map { attachment ->
                        buildJsonObject {
                            put("id", attachment.id)
                            put("name", attachment.name)
                            put("sizeBytes", attachment.sizeBytes)
                            put("mimeType", attachment.mimeType)
                        }
                    }))
                }
            }))
        }
    }).toString()

    fun decode(encoded: String): List<ChatSession>? = runCatching {
        Json.parseToJsonElement(encoded).jsonArray.map { element ->
            val source = element.jsonObject
            ChatSession(
                id = source.requiredText("id"),
                contextKey = source.requiredText("contextKey"),
                caseTitle = source.requiredText("caseTitle"),
                title = source.requiredText("title"),
                updatedAtMillis = source["updatedAtMillis"]?.jsonPrimitive?.longOrNull ?: error("Invalid timestamp"),
                messages = source["messages"]?.jsonArray?.map { messageElement ->
                    val message = messageElement.jsonObject
                    ChatMessage(
                        fromLearner = message["fromLearner"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                            ?: error("Invalid message author"),
                        text = message.requiredText("text"),
                        attachments = message["attachments"]?.jsonArray?.map { attachmentElement ->
                            val attachment = attachmentElement.jsonObject
                            ChatAttachment(
                                id = attachment.requiredText("id"),
                                name = attachment.requiredText("name"),
                                sizeBytes = attachment["sizeBytes"]?.jsonPrimitive?.longOrNull
                                    ?.takeIf { it in 0..CHAT_FILE_LIMIT_BYTES }
                                    ?: error("Invalid attachment size"),
                                mimeType = attachment.requiredText("mimeType"),
                            )
                        } ?: emptyList(),
                    )
                } ?: error("Missing messages"),
            )
        }
    }.getOrNull()

    private fun JsonObject.requiredText(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: error("Missing $key")
}
