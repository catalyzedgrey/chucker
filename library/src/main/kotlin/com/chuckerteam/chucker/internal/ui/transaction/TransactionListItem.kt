package com.chuckerteam.chucker.internal.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.entity.HttpTransactionTuple
import java.text.DateFormat
import javax.net.ssl.HttpsURLConnection

private const val PATH_MAX_LINES = 4
private const val SECONDARY_MAX_LINES = 2
private const val WEBSOCKET_CODE = "WS"

/**
 * A single row of the transactions list.
 *
 * @param transaction The transaction to display.
 * @param isSelected Whether the row is highlighted as part of the multi-selection.
 * @param onClick Invoked on a single click.
 * @param onLongClick Invoked on a long click.
 */
@Composable
internal fun TransactionListItem(
    transaction: HttpTransactionTuple,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = transaction.statusColor()
    val protocol = if (transaction.isSsl) ProtocolResources.Https() else ProtocolResources.Http()
    val isComplete = transaction.status == HttpTransaction.Status.Complete
    val code =
        when {
            transaction.status == HttpTransaction.Status.Failed -> "!!!"
            transaction.isWebSocket -> WEBSOCKET_CODE
            isComplete -> transaction.responseCode.toString()
            else -> ""
        }
    val title =
        if (transaction.isWebSocket) {
            transaction.getFormattedPath(encode = false)
        } else {
            "${transaction.method} ${transaction.getFormattedPath(encode = false)}"
        }
    val duration =
        when {
            transaction.isWebSocketOpen -> stringResource(R.string.chucker_websocket_open)
            isComplete -> transaction.durationString.orEmpty()
            else -> ""
        }
    val size =
        when {
            transaction.isWebSocket -> "↑${transaction.sentCount} ↓${transaction.receivedCount}"
            isComplete -> transaction.totalSizeString
            else -> ""
        }
    val textColor = colorResource(R.color.chucker_status_default)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.background(colorResource(R.color.chucker_status_multiple_selection))
                    } else {
                        Modifier
                    },
                ).combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(8.dp),
    ) {
        Row {
            Text(
                text = code,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp).padding(end = 16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = PATH_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                if (transaction.graphQlDetected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.chucker_ic_graphql),
                            contentDescription = stringResource(R.string.chucker_ssl),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text =
                                transaction.graphQlOperationName
                                    ?: stringResource(R.string.chucker_graphql_operation_is_empty),
                            color = textColor,
                            fontSize = 14.sp,
                            maxLines = SECONDARY_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(protocol.icon),
                        contentDescription = stringResource(R.string.chucker_ssl),
                        tint = colorResource(protocol.color),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = transaction.host.orEmpty(),
                        color = textColor,
                        fontSize = 14.sp,
                        maxLines = SECONDARY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                text = DateFormat.getTimeInstance().format(transaction.requestDate),
                color = textColor,
                fontSize = 14.sp,
                modifier = Modifier.weight(2f).padding(start = 56.dp),
            )
            Text(
                text = duration,
                color = textColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = size,
                color = textColor,
                fontSize = 14.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HttpTransactionTuple.statusColor(): Color {
    val colorDefault = colorResource(R.color.chucker_status_default)
    val colorRequested = colorResource(R.color.chucker_status_requested)
    val colorError = colorResource(R.color.chucker_status_error)
    val color500 = colorResource(R.color.chucker_status_500)
    val color400 = colorResource(R.color.chucker_status_400)
    val color300 = colorResource(R.color.chucker_status_300)
    return when {
        (status === HttpTransaction.Status.Failed) -> colorError
        (status === HttpTransaction.Status.Requested) -> colorRequested
        isWebSocket -> colorDefault
        (responseCode == null) -> colorDefault
        (responseCode!! >= HttpsURLConnection.HTTP_INTERNAL_ERROR) -> color500
        (responseCode!! >= HttpsURLConnection.HTTP_BAD_REQUEST) -> color400
        (responseCode!! >= HttpsURLConnection.HTTP_MULT_CHOICE) -> color300
        else -> colorDefault
    }
}
