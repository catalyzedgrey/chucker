package com.chuckerteam.chucker.internal.ui.transaction

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.support.ChessboardDrawable
import com.chuckerteam.chucker.internal.ui.compose.toAnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DELAY_FOR_SEARCH_SCROLL = 600L
private const val LUMINANCE_THRESHOLD = 0.25
private val PayloadTextSize = 14.sp

/** Content of the Request / Response tab. [searchQuery] is the text typed into the toolbar search. */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun TransactionPayloadScreen(
    viewModel: TransactionViewModel,
    type: PayloadType,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val transaction by viewModel.transaction.observeAsState()
    val formatRequestBody by viewModel.formatRequestBody.observeAsState()

    var items by remember { mutableStateOf<List<TransactionPayloadItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isEmpty by remember { mutableStateOf<Boolean?>(null) }
    val rowVersions = remember { mutableStateMapOf<Int, Int>() }
    val controller =
        remember {
            PayloadSearchController { index -> rowVersions[index] = (rowVersions[index] ?: 0) + 1 }
        }
    val listState = rememberLazyListState()
    var summary by remember { mutableStateOf<PayloadSearchController.SearchPosition?>(null) }
    var summaryVisible by remember { mutableStateOf(false) }

    val backgroundSpanColor = ContextCompat.getColor(context, R.color.chucker_background_span_color)
    val foregroundSpanColor = ContextCompat.getColor(context, R.color.chucker_foreground_span_color)
    val backgroundSpanColorSearchItem = android.graphics.Color.GREEN

    val currentTransaction = transaction
    val currentFormat = formatRequestBody
    LaunchedEffect(currentTransaction, currentFormat) {
        if (currentTransaction == null || currentFormat == null) return@LaunchedEffect
        isLoading = true
        val result = PayloadItemsBuilder.build(context, type, currentTransaction, currentFormat)
        if (result.isEmpty()) {
            isEmpty = true
        } else {
            controller.items = result
            items = result
            isEmpty = false
        }
        isLoading = false
    }

    suspend fun showPosition(position: PayloadSearchController.SearchPosition) {
        summary = position
        summaryVisible = true
        listState.animateScrollToItem(position.rowIndex)
    }

    LaunchedEffect(searchQuery) {
        val hasResults = controller.onQueryChanged(searchQuery, backgroundSpanColor, foregroundSpanColor)
        if (!hasResults) summaryVisible = false
        delay(DELAY_FOR_SEARCH_SCROLL)
        val position =
            lifecycle.withResumed {
                controller.onQuerySettled(backgroundSpanColor, foregroundSpanColor, backgroundSpanColorSearchItem)
            }
        if (position != null) showPosition(position)
    }

    val onNavigate: (Boolean) -> Unit = { goNext ->
        // hide the keyboard if visible
        keyboardController?.hide()
        focusManager.clearFocus()
        controller
            .navigate(goNext, backgroundSpanColor, foregroundSpanColor, backgroundSpanColorSearchItem)
            ?.let { scope.launch { showPosition(it) } }
    }

    Column(modifier = modifier.fillMaxSize()) {
        val currentSummary = summary
        if (summaryVisible && currentSummary != null) {
            SearchSummaryBar(
                summary = currentSummary,
                onUp = { onNavigate(false) },
                onDown = { onNavigate(true) },
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (items.isNotEmpty() && isEmpty == false) {
                PayloadList(
                    items = items,
                    rowVersions = rowVersions,
                    listState = listState,
                    onCopyRaw = { transaction?.let { copyRawPayload(context, type, it) } },
                    onCopyFormatted = {
                        transaction?.let { scope.launch { copyFormattedPayload(context, type, it) } }
                    },
                )
            }
            if (isEmpty == true) {
                EmptyPayloadState(type)
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).padding(16.dp))
            }
        }
    }
}

@Composable
private fun SearchSummaryBar(
    summary: PayloadSearchController.SearchPosition,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.chucker_color_primary)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("${summary.current} / ${summary.total}")
                    }
                },
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
            color = colorResource(R.color.chucker_color_on_primary),
            fontSize = 16.sp,
        )
        SearchNavButton(R.drawable.chucker_ic_arrow_up, onUp)
        SearchNavButton(R.drawable.chucker_ic_arrow_down, onDown)
    }
}

@Composable
private fun SearchNavButton(
    drawableRes: Int,
    onClick: () -> Unit,
) {
    Icon(
        painter = painterResource(drawableRes),
        contentDescription = stringResource(R.string.chucker_scroll_buttons_for_search),
        tint = Color.Unspecified,
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun PayloadList(
    items: List<TransactionPayloadItem>,
    rowVersions: Map<Int, Int>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCopyRaw: () -> Unit,
    onCopyFormatted: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // One container for the whole list: a SelectionContainer per row made composing a
        // payload tab's first screen (~23 rows) cost a whole frame on every tab swipe.
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                itemsIndexed(items) { index, item ->
                    when (item) {
                        is TransactionPayloadItem.HeaderItem ->
                            Text(
                                text = remember(item) { item.headers.toAnnotatedString() },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                style = TextStyle(fontSize = PayloadTextSize),
                            )
                        is TransactionPayloadItem.CopyItem ->
                            DisableSelection {
                                PayloadCopyRow(onCopyRaw = onCopyRaw, onCopyFormatted = onCopyFormatted)
                            }
                        is TransactionPayloadItem.BodyLineItem -> {
                            val version = rowVersions[index]
                            Text(
                                text = remember(item, version) { item.line.toAnnotatedString() },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                style =
                                    TextStyle(
                                        fontSize = PayloadTextSize,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                minLines = 1,
                            )
                        }
                        is TransactionPayloadItem.ImageItem -> PayloadImage(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun PayloadImage(item: TransactionPayloadItem.ImageItem) {
    val context = LocalContext.current
    val background =
        remember(item) {
            val luminance = item.luminance ?: return@remember null
            if (luminance < LUMINANCE_THRESHOLD) {
                ChessboardDrawable.createPattern(
                    context,
                    R.color.chucker_chessboard_even_square_light,
                    R.color.chucker_chessboard_odd_square_light,
                    R.dimen.chucker_half_grid,
                )
            } else {
                ChessboardDrawable.createPattern(
                    context,
                    R.color.chucker_chessboard_even_square_dark,
                    R.color.chucker_chessboard_odd_square_dark,
                    R.dimen.chucker_half_grid,
                )
            }
        }
    val bitmap: Bitmap = item.image
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    background?.let { drawable ->
                        drawIntoCanvas {
                            drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                            drawable.draw(it.nativeCanvas)
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = stringResource(R.string.chucker_binary_data),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun EmptyPayloadState(type: PayloadType) {
    val text =
        if (type == PayloadType.RESPONSE) {
            stringResource(R.string.chucker_response_is_empty)
        } else {
            stringResource(R.string.chucker_request_is_empty)
        }
    val imageSize = 64.dp
    val gap = 32.dp
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Icon(
                painter = painterResource(R.drawable.chucker_empty_payload),
                contentDescription = stringResource(R.string.chucker_body_empty),
                tint = colorResource(R.color.chucker_color_primary),
                modifier = Modifier.size(imageSize),
            )
            Text(text = text, fontSize = 18.sp)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val image = measurables[0].measure(loose)
        val label = measurables[1].measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val labelY = (constraints.maxHeight - label.height) / 2
            label.place((constraints.maxWidth - label.width) / 2, labelY)
            image.place((constraints.maxWidth - image.width) / 2, labelY - gap.roundToPx() - image.height)
        }
    }
}

internal fun copyRawPayload(
    context: Context,
    payloadType: PayloadType,
    transaction: HttpTransaction,
) {
    when (payloadType) {
        PayloadType.REQUEST ->
            transaction.requestBody?.let { request ->
                copyToClipboard(
                    context,
                    request,
                    context.getString(R.string.chucker_request),
                    context.getString(R.string.chucker_request_copied),
                )
            }
        PayloadType.RESPONSE ->
            transaction.responseBody?.let { response ->
                copyToClipboard(
                    context,
                    response,
                    context.getString(R.string.chucker_response),
                    context.getString(R.string.chucker_response_copied),
                )
            }
    }
}

internal suspend fun copyFormattedPayload(
    context: Context,
    payloadType: PayloadType,
    transaction: HttpTransaction,
) {
    val formatted =
        withContext(Dispatchers.Default) {
            when (payloadType) {
                PayloadType.REQUEST -> transaction.getFormattedRequestBody()
                PayloadType.RESPONSE -> transaction.getFormattedResponseBody()
            }
        }

    if (formatted.isEmpty()) return

    val (label, toast) =
        when (payloadType) {
            PayloadType.REQUEST -> {
                context.getString(R.string.chucker_request) to
                    context.getString(R.string.chucker_request_copied_formatted)
            }
            PayloadType.RESPONSE -> {
                context.getString(R.string.chucker_response) to
                    context.getString(R.string.chucker_response_copied_formatted)
            }
        }
    copyToClipboard(context, formatted, label, toast)
}

private fun copyToClipboard(
    context: Context,
    payload: String,
    payloadType: String,
    toastSuccessMessage: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(payloadType, payload)
    clipboard.setPrimaryClip(clip)

    Toast
        .makeText(context, toastSuccessMessage, Toast.LENGTH_LONG)
        .show()
}
