package sh.lsx.untisx.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.DirectMessage
import sh.lsx.untisx.data.model.parseMessageDateTime
import sh.lsx.untisx.data.stripHtml
import sh.lsx.untisx.ui.util.animatedItem
import sh.lsx.untisx.ui.util.entranceEffect
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.sharedElementBounds
import sh.lsx.untisx.ui.util.tapHaptic
import sh.lsx.untisx.ui.util.uiTextString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    vm: MessagesViewModel,
    onMessageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages = vm.incoming
    val confirmations = vm.readConfirmations

    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appeared = true
    }

    PullToRefreshBox(
        isRefreshing = vm.loading && (messages.isNotEmpty() || confirmations.isNotEmpty()),
        onRefresh = vm::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        val isEmpty = messages.isEmpty() && confirmations.isEmpty()

        when {
            isEmpty && vm.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            isEmpty -> MessagesEmptyState(
                error = vm.error?.let { uiTextString(it) },
                onRetry = {
                    vm.clearError()
                    vm.refresh()
                },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.messages_inbox))
                    }

                    itemsIndexed(messages, key = { index, message -> "in-${message.id}:$index" }) { index, message ->
                        MessageCard(
                            message = message,
                            modifier = animatedItem().entranceEffect(appeared, index),
                            onClick = {
                                vm.open(message)
                                onMessageClick()
                            },
                        )
                    }
                }

                if (confirmations.isNotEmpty()) {
                    item {
                        SectionHeader(
                            text = stringResource(R.string.messages_read_confirmations),
                            modifier = Modifier.padding(top = if (messages.isNotEmpty()) 16.dp else 0.dp),
                        )
                    }

                    itemsIndexed(confirmations, key = { index, message -> "rc-${message.id}:$index" }) { index, message ->
                        MessageCard(
                            message = message,
                            modifier = animatedItem()
                                .entranceEffect(appeared, messages.size + index),
                            onClick = {
                                vm.open(message)
                                onMessageClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun MessageCard(
    message: DirectMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = rememberDateFormat("dMMMjm")
    val time = remember(message.sentDateTime) { parseMessageDateTime(message.sentDateTime) }
    val preview = remember(message.preview) { stripHtml(message.preview) }

    Card(
        modifier = modifier
            .sharedElementBounds("message-${message.id}")
            .fillMaxWidth()
            .tapHaptic(onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!message.isMessageRead) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )

                Spacer(Modifier.size(10.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                message.sender?.displayName?.takeIf { it.isNotBlank() }?.let { sender ->
                    Text(
                        text = sender,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = message.subject.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.messages_no_subject),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (message.isMessageRead) FontWeight.Normal else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (preview.isNotBlank()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                time?.let {
                    Text(
                        text = dateFormat.format(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (message.attachments.isNotEmpty() || message.hasAttachments) {
                    Text(
                        text = stringResource(R.string.messages_attachments),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagesEmptyState(
    error: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            } else {
                Text(
                    text = stringResource(R.string.messages_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stringResource(R.string.messages_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
