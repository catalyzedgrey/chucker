package com.chuckerteam.chucker.internal.ui.transaction

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.internal.support.FormatUtils
import com.chuckerteam.chucker.internal.ui.compose.ChuckerTheme
import com.chuckerteam.chucker.internal.websocket.SocketIoDecoder
import java.util.Locale

private const val COLLAPSED_MAX_LINES = 3
private const val MILLIS_PER_SECOND = 1000.0

/**
 * Timeline of a WebSocket connection's messages, with direction filters and, for Socket.IO
 * connections, decoded packets. Follows new messages while scrolled to the bottom and stops
 * following once the user scrolls up.
 *
 * @param searchQuery Filters messages by payload or event name; blank shows everything.
 */
@Composable
internal fun WebSocketMessagesScreen(
    viewModel: TransactionViewModel,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.webSocketMessages.observeAsState(emptyList())
    val transaction by viewModel.transaction.observeAsState()
    WebSocketMessagesContent(
        messages = messages,
        openedAt = transaction?.requestDate ?: messages.firstOrNull()?.timestamp ?: 0L,
        connectionUrl = transaction?.url,
        searchQuery = searchQuery,
        modifier = modifier,
    )
}

/** Stateless part of [WebSocketMessagesScreen], so it can be previewed and tested without a ViewModel. */
@Composable
internal fun WebSocketMessagesContent(
    messages: List<WebSocketMessage>,
    openedAt: Long,
    connectionUrl: String?,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    val isEngineIo = remember(connectionUrl) { SocketIoDecoder.isEngineIoUrl(connectionUrl) }

    var showSent by rememberSaveable { mutableStateOf(true) }
    var showReceived by rememberSaveable { mutableStateOf(true) }
    var showPingPong by rememberSaveable { mutableStateOf(false) }
    val filter = WebSocketMessageFilter(showSent, showReceived, showPingPong)

    val items = remember(messages, isEngineIo) { messages.toMessageItems(isEngineIo) }
    val visible = remember(items, filter, searchQuery) { items.filterMessages(filter, searchQuery) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = showSent,
                onClick = { showSent = !showSent },
                label = { Text(stringResource(R.string.chucker_websocket_filter_sent)) },
            )
            FilterChip(
                selected = showReceived,
                onClick = { showReceived = !showReceived },
                label = { Text(stringResource(R.string.chucker_websocket_filter_received)) },
            )
            if (isEngineIo) {
                FilterChip(
                    selected = showPingPong,
                    onClick = { showPingPong = !showPingPong },
                    label = { Text(stringResource(R.string.chucker_websocket_filter_ping_pong)) },
                )
            }
        }
        HorizontalDivider()
        when {
            messages.isEmpty() -> EmptyMessage(R.string.chucker_websocket_no_messages)
            visible.isEmpty() -> EmptyMessage(R.string.chucker_websocket_no_matches)
            else -> MessageList(visible, openedAt)
        }
    }
}

@Composable
private fun EmptyMessage(textRes: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(textRes), color = colorResource(R.color.chucker_status_default))
    }
}

@Composable
private fun MessageList(
    items: List<WebSocketMessageItem>,
    openedAt: Long,
) {
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
    LaunchedEffect(items.size) {
        if (followTail && items.isNotEmpty()) listState.scrollToItem(items.lastIndex)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.message.id }) { item ->
            WebSocketMessageRow(item, openedAt)
            HorizontalDivider()
        }
    }
}

@Composable
private fun WebSocketMessageRow(
    item: WebSocketMessageItem,
    openedAt: Long,
    initiallyExpanded: Boolean = false,
) {
    val message = item.message
    var expanded by rememberSaveable(message.id) { mutableStateOf(initiallyExpanded) }
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
                text = item.frame?.label ?: message.label(),
                color = accent,
                fontSize = 12.sp,
                fontWeight = if (item.frame?.event != null) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        val payload = formatPayload(item.displayPayload, expanded && message.type == Type.TEXT)
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

private fun formatPayload(
    text: String,
    prettyPrint: Boolean,
): String {
    if (!prettyPrint) return text
    val trimmed = text.trimStart()
    return if (trimmed.startsWith("{") || trimmed.startsWith("[")) FormatUtils.formatJson(text) else text
}

internal fun formatOffset(millis: Long): String = String.format(Locale.US, "+%.3fs", millis.coerceAtLeast(0) / MILLIS_PER_SECOND)

private const val PREVIEW_OPENED_AT = 1_000L

private fun previewMessage(
    seq: Long,
    direction: Direction,
    payload: String?,
    type: Type = Type.TEXT,
    closeCode: Int? = null,
) = WebSocketMessage(
    id = seq,
    transactionId = 1,
    seq = seq,
    timestamp = PREVIEW_OPENED_AT + seq * 120,
    direction = direction,
    type = type,
    payload = payload,
    payloadSize = payload?.length?.toLong() ?: 0,
    closeCode = closeCode,
)

private val previewSocketIoMessages =
    listOf(
        previewMessage(1, Direction.RECEIVED, """0{"sid":"Lbo5JLzTotvW3g2LAAAA","pingInterval":25000}"""),
        previewMessage(2, Direction.SENT, "40"),
        previewMessage(3, Direction.RECEIVED, """40{"sid":"wZX3oN0bSVIhsaknAAAI"}"""),
        previewMessage(4, Direction.SENT, """42["chat",{"user":"chucker","text":"Hello!"}]"""),
        previewMessage(5, Direction.RECEIVED, "2"),
        previewMessage(6, Direction.SENT, "3"),
        previewMessage(7, Direction.RECEIVED, """42/admin,["stats",{"users":3}]"""),
        previewMessage(8, Direction.SENT, "Normal closure", Type.CLOSE, closeCode = 1000),
    )

@Preview(name = "Socket.IO", showBackground = true)
@Preview(name = "Socket.IO dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WebSocketMessagesContentSocketIoPreview() {
    ChuckerTheme {
        WebSocketMessagesContent(
            messages = previewSocketIoMessages,
            openedAt = PREVIEW_OPENED_AT,
            connectionUrl = "https://example.com/socket.io/?EIO=4&transport=websocket",
            searchQuery = "",
        )
    }
}

@Preview(name = "Plain WebSocket", showBackground = true)
@Composable
private fun WebSocketMessagesContentPlainPreview() {
    ChuckerTheme {
        WebSocketMessagesContent(
            messages =
                listOf(
                    previewMessage(1, Direction.SENT, "Hello from Chucker"),
                    previewMessage(2, Direction.RECEIVED, "Hello from Chucker"),
                    previewMessage(3, Direction.SENT, "62696e617279", Type.BINARY),
                    previewMessage(4, Direction.RECEIVED, "java.io.EOFException", Type.FAILURE),
                ),
            openedAt = PREVIEW_OPENED_AT,
            connectionUrl = "https://example.com/chat",
            searchQuery = "",
        )
    }
}

@Preview(name = "No messages", showBackground = true)
@Composable
private fun WebSocketMessagesContentEmptyPreview() {
    ChuckerTheme {
        WebSocketMessagesContent(
            messages = emptyList(),
            openedAt = PREVIEW_OPENED_AT,
            connectionUrl = "https://example.com/chat",
            searchQuery = "",
        )
    }
}

@Preview(name = "No matches", showBackground = true)
@Composable
private fun WebSocketMessagesContentNoMatchesPreview() {
    ChuckerTheme {
        WebSocketMessagesContent(
            messages = previewSocketIoMessages,
            openedAt = PREVIEW_OPENED_AT,
            connectionUrl = "https://example.com/socket.io/?EIO=4",
            searchQuery = "nothing matches this",
        )
    }
}

@Preview(name = "Message row, expanded JSON", showBackground = true)
@Composable
private fun WebSocketMessageRowPreview() {
    ChuckerTheme {
        val message = previewMessage(4, Direction.SENT, """42["chat",{"user":"chucker","text":"Hello!"}]""")
        WebSocketMessageRow(
            item = listOf(message).toMessageItems(isEngineIo = true).single(),
            openedAt = PREVIEW_OPENED_AT,
            initiallyExpanded = true,
        )
    }
}
