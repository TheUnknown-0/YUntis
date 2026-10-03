package sh.lsx.untisx.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.DirectMessage
import sh.lsx.untisx.data.model.MessageAttachment
import sh.lsx.untisx.data.model.parseMessageDateTime
import sh.lsx.untisx.data.stripHtml
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.tapHaptic

@Composable
fun MessageDetailsScreen(
    vm: MessagesViewModel,
    modifier: Modifier = Modifier,
) {
    val message = vm.detail

    if (message == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MessageHeader(message)
        MessageBody(message, loading = vm.detailLoading)

        if (message.attachments.isNotEmpty()) {
            DetailCard(title = stringResource(R.string.messages_attachments)) {
                message.attachments.forEach { attachment ->
                    AttachmentRow(attachment)
                }
            }
        }

        message.requestConfirmationStatus
            ?.takeIf { it.totalRequestCount > 0 }
            ?.let { status ->
                DetailCard(title = stringResource(R.string.messages_read_confirmations)) {
                    Text(
                        text = stringResource(
                            R.string.messages_confirmation,
                            status.confirmedRequestCount,
                            status.totalRequestCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

        if (message.replyHistory.isNotEmpty()) {
            DetailCard(title = stringResource(R.string.messages_replies)) {
                message.replyHistory.forEach { reply ->
                    ReplyRow(reply)
                }
            }
        }
    }
}

@Composable
private fun MessageHeader(message: DirectMessage) {
    val dateFormat = rememberDateFormat("EEEE, d. MMMM yyyy, HH:mm")
    val time = remember(message.sentDateTime) { parseMessageDateTime(message.sentDateTime) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = message.subject.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.messages_no_subject),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            message.sender?.displayName?.takeIf { it.isNotBlank() }?.let { sender ->
                val className = message.sender.className.takeIf { it.isNotBlank() }

                Text(
                    text = if (className != null) "$sender ($className)" else sender,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
            }

            time?.let {
                Text(
                    text = dateFormat.format(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun MessageBody(message: DirectMessage, loading: Boolean) {
    val content = remember(message.content, message.contentPreview) {
        stripHtml(message.content ?: message.contentPreview.orEmpty())
    }

    DetailCard(title = null) {
        if (loading && content.isBlank()) {
            Text(
                text = stringResource(R.string.lesson_notes_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = content.ifBlank { stringResource(R.string.messages_no_content) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AttachmentRow(attachment: MessageAttachment) {
    val uriHandler = LocalUriHandler.current
    val url = attachment.downloadUrl?.takeIf { it.startsWith("https://") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (url != null) {
                    base.tapHaptic { runCatching { uriHandler.openUri(url) } }
                } else {
                    base
                }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = attachment.displayName.ifBlank { stringResource(R.string.messages_attachments) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        attachment.attachmentType
            .takeIf { it.isNotBlank() }
            ?.let { type ->
                Text(
                    text = type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun ReplyRow(reply: DirectMessage) {
    val dateFormat = rememberDateFormat("d. MMM, HH:mm")
    val time = remember(reply.sentDateTime) { parseMessageDateTime(reply.sentDateTime) }
    val content = remember(reply.content, reply.contentPreview) {
        stripHtml(reply.content ?: reply.contentPreview.orEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = reply.sender?.displayName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.messages_no_subject),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            time?.let {
                Text(
                    text = dateFormat.format(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailCard(
    title: String?,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            content()
        }
    }
}
