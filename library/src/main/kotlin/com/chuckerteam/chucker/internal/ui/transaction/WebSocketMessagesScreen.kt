package com.chuckerteam.chucker.internal.ui.transaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.internal.support.FormatUtils
import java.util.Locale

private const val COLLAPSED_MAX_LINES = 3
private const val MILLIS_PER_SECOND = 1000.0

/**
 * Timeline of a WebSocket connection's messages. Follows new messages while scrolled to the
 * bottom and stops following once the user scrolls up.
 */
@Composable
internal fun WebSocketMessagesScreen(
    viewModel: TransactionViewModel,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.webSocketMessages.observeAsState(emptyList())
    val transaction by viewModel.transaction.observeAsState()
    val openedAt = transaction?.requestDate ?: messages.firstOrNull()?.timestamp ?: 0L

    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.chucker_websocket_no_messages),
                color = colorResource(R.color.chucker_status_default),
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }
    var followTail by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to isAtBottom }
            .collect { (scrolling, atBottom) -> if (scrolling) followTail = atBottom }
    }
    LaunchedEffect(messages.size) {
        if (followTail) listState.scrollToItem(messages.lastIndex)
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(messages, key = { it.id }) { message ->
            WebSocketMessageRow(message, openedAt)
            HorizontalDivider()
        }
    }
}

@Composable
private fun WebSocketMessageRow(
    message: WebSocketMessage,
    openedAt: Long,
) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val sent = message.direction == Direction.SENT
    val accent =
        when {
            message.type == Type.FAILURE || message.type == Type.DROPPED -> colorResource(R.color.chucker_status_error)
            sent -> colorResource(R.color.chucker_color_primary)
            else -> colorResource(R.color.chucker_status_300)
        }
    val textColor = colorResource(R.color.chucker_status_default)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (sent) "↑" else "↓",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = formatOffset(message.timestamp - openedAt),
                color = textColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = message.label(),
                color = accent,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (message.isDropped) {
                Text(
                    text = stringResource(R.string.chucker_websocket_not_sent),
                    color = colorResource(R.color.chucker_status_error),
                    fontSize = 12.sp,
                )
            }
        }
        val payload = message.displayPayload(expanded)
        if (payload.isNotEmpty()) {
            Text(
                text = payload,
                color = textColor,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (message.isTruncated && expanded) {
            Text(
                text = stringResource(R.string.chucker_body_content_truncated).trim(),
                color = Color.Gray,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WebSocketMessage.label(): String =
    when (type) {
        Type.TEXT -> ""
        Type.BINARY ->
            stringResource(R.string.chucker_websocket_binary, FormatUtils.formatByteCount(payloadSize, true))
        Type.CLOSE -> stringResource(R.string.chucker_websocket_close, closeCode ?: 0)
        Type.FAILURE -> stringResource(R.string.chucker_websocket_failure)
        Type.DROPPED -> stringResource(R.string.chucker_websocket_messages_dropped, payloadSize)
    }

private fun WebSocketMessage.displayPayload(expanded: Boolean): String {
    val text = payload.orEmpty()
    if (!expanded || type != Type.TEXT) return text
    val trimmed = text.trimStart()
    return if (trimmed.startsWith("{") || trimmed.startsWith("[")) FormatUtils.formatJson(text) else text
}

internal fun formatOffset(millis: Long): String = String.format(Locale.US, "+%.3fs", millis.coerceAtLeast(0) / MILLIS_PER_SECOND)
