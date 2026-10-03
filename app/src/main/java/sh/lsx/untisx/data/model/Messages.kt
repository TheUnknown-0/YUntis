package sh.lsx.untisx.data.model

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class MessageSender(
    val userId: Long = 0,
    val displayName: String = "",
    val imageUrl: String? = null,
    val className: String = "",
)

@Serializable
data class MessageAttachment(
    val id: String = "",
    val attachmentType: String = "",
    val name: String = "",
    val fileName: String = "",
    val downloadable: Boolean = false,
    val downloadUrl: String? = null,
) {
    val displayName: String get() = name.ifBlank { fileName }
}

@Serializable
data class MessageConfirmationStatus(
    val totalRequestCount: Int = 0,
    val confirmedRequestCount: Int = 0,
)

@Serializable
data class DirectMessage(
    val id: Long = 0,
    val subject: String = "",
    val content: String? = null,
    val contentPreview: String? = null,
    val sender: MessageSender? = null,
    val sentDateTime: String = "",
    val isMessageRead: Boolean = true,
    val hasAttachments: Boolean = false,
    val attachments: List<MessageAttachment> = emptyList(),
    val isReply: Boolean = false,
    val isReplyAllowed: Boolean = false,
    val replyHistory: List<DirectMessage> = emptyList(),
    val requestConfirmationStatus: MessageConfirmationStatus? = null,
) {
    val preview: String get() = (content ?: contentPreview).orEmpty()
}

@Serializable
data class MessagesData(
    val incoming: List<DirectMessage> = emptyList(),
    val readConfirmations: List<DirectMessage> = emptyList(),
)

fun parseMessageDateTime(raw: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(raw.take(19)) }.getOrNull()
