package com.chuckerteam.chucker.internal.ui.transaction

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.support.FileSaver
import com.chuckerteam.chucker.internal.support.Logger
import com.chuckerteam.chucker.internal.ui.compose.ChuckerTheme
import com.chuckerteam.chucker.internal.ui.compose.chuckerAppBarContainerColor
import com.chuckerteam.chucker.internal.ui.compose.chuckerTopAppBarColors
import kotlinx.coroutines.launch

private const val PAGE_OVERVIEW = 0
private const val PAGE_REQUEST = 1
private const val PAGE_RESPONSE = 2
private const val PAGE_MESSAGES = 3
private const val UNSELECTED_TAB_ALPHA = 0.7f

/** More tabs than this don't fit at fixed width on a phone, so the row scrolls instead. */
private const val MAX_FIXED_TABS = 3

private fun payloadTypeForPage(page: Int): PayloadType? =
    when (page) {
        PAGE_REQUEST -> PayloadType.REQUEST
        PAGE_RESPONSE -> PayloadType.RESPONSE
        else -> null
    }

@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionScreen(
    viewModel: TransactionViewModel,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onShareText: () -> Unit,
    onShareCurl: () -> Unit,
    onShareFile: () -> Unit,
    onShareHar: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transaction by viewModel.transaction.observeAsState()
    val isWebSocket = transaction?.isWebSocket == true
    val pagerState =
        rememberPagerState(initialPage = initialPage) { if (isWebSocket) PAGE_MESSAGES + 1 else PAGE_RESPONSE + 1 }
    val currentPage = pagerState.currentPage
    val currentPageState = rememberUpdatedState(currentPage)
    val onPageChangedState = rememberUpdatedState(onPageChanged)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChangedState.value(it) }
    }

    val title by viewModel.transactionTitle.observeAsState()
    val encodeUrl by viewModel.encodeUrl.observeAsState(false)
    val doesUrlRequireEncoding by viewModel.doesUrlRequireEncoding.observeAsState(false)
    val doesRequestBodyRequireEncoding by viewModel.doesRequestBodyRequireEncoding.observeAsState(false)

    // Messages tab: expand and pretty-print every JSON message.
    var formatJson by rememberSaveable { mutableStateOf(false) }

    // The search state belongs to the page it was opened on; switching pages closes it.
    var searchOpen by remember(currentPage) { mutableStateOf(false) }
    var searchQuery by remember(currentPage) { mutableStateOf("") }
    BackHandler(enabled = searchOpen) {
        searchOpen = false
        searchQuery = ""
    }

    val saveToFile =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val payloadType = payloadTypeForPage(currentPageState.value) ?: return@rememberLauncherForActivityResult
            val currentTransaction = viewModel.transaction.value
            val applicationContext = context.applicationContext
            if (uri != null && currentTransaction != null) {
                scope.launch {
                    val source =
                        runCatching {
                            PayloadRules.prepareDataToSave(payloadType, currentTransaction)
                        }.getOrElse {
                            Logger.error("Failed to save transaction to a file", it)
                            return@launch
                        }
                    val result = FileSaver.saveFile(source, uri, applicationContext.contentResolver)
                    val toastMessageId =
                        if (result) {
                            R.string.chucker_file_saved
                        } else {
                            R.string.chucker_file_not_saved
                        }
                    Toast.makeText(applicationContext, toastMessageId, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast
                    .makeText(
                        applicationContext,
                        R.string.chucker_save_failed_to_open_document,
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }

    val payloadType = payloadTypeForPage(currentPage)
    val showEncodeIcon =
        when (currentPage) {
            PAGE_OVERVIEW -> doesUrlRequireEncoding
            PAGE_REQUEST -> doesRequestBodyRequireEncoding
            else -> false
        }
    val showSearchIcon =
        currentPage == PAGE_MESSAGES ||
            (payloadType != null && PayloadRules.shouldShowSearchIcon(payloadType, transaction))
    val showSaveIcon = payloadType != null && PayloadRules.shouldShowSaveIcon(payloadType, transaction)

    val tabTitles =
        listOfNotNull(
            stringResource(R.string.chucker_overview),
            stringResource(R.string.chucker_request),
            stringResource(R.string.chucker_response),
            if (isWebSocket) stringResource(R.string.chucker_messages) else null,
        )

    Scaffold(
        topBar = {
            Column {
                TransactionTopBar(
                    title = title.orEmpty(),
                    searchOpen = searchOpen,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchClose = {
                        searchOpen = false
                        searchQuery = ""
                    },
                    onBack = onBack,
                    showSearchIcon = showSearchIcon,
                    onSearchOpen = { searchOpen = true },
                    showEncodeIcon = showEncodeIcon,
                    encodeUrl = encodeUrl,
                    onEncodeUrl = { viewModel.switchUrlEncoding() },
                    showFormatJsonIcon = currentPage == PAGE_MESSAGES,
                    formatJson = formatJson,
                    onFormatJsonChange = { formatJson = it },
                    showSaveIcon = showSaveIcon,
                    onSave = {
                        val currentTransaction = viewModel.transaction.value
                        if (payloadType != null &&
                            currentTransaction != null &&
                            PayloadRules.isBodyEmpty(payloadType, currentTransaction)
                        ) {
                            Toast
                                .makeText(
                                    context,
                                    R.string.chucker_file_not_saved_body_is_empty,
                                    Toast.LENGTH_SHORT,
                                ).show()
                        } else {
                            saveToFile.launch("${PayloadRules.DEFAULT_FILE_PREFIX}${System.currentTimeMillis()}")
                        }
                    },
                    onShareText = onShareText,
                    onShareCurl = onShareCurl,
                    onShareFile = onShareFile,
                    onShareHar = onShareHar,
                )
                TransactionTabs(
                    titles = tabTitles,
                    selectedIndex = currentPage,
                    onTabClick = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    PAGE_OVERVIEW -> TransactionOverviewScreen(viewModel)
                    PAGE_MESSAGES ->
                        WebSocketMessagesScreen(
                            viewModel = viewModel,
                            searchQuery = if (page == currentPage) searchQuery else "",
                            formatJson = formatJson,
                        )
                    else ->
                        TransactionPayloadScreen(
                            viewModel = viewModel,
                            type = requireNotNull(payloadTypeForPage(page)),
                            searchQuery = if (page == currentPage) searchQuery else "",
                        )
                }
            }
        }
    }
}

@Suppress("LongMethod", "LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionTopBar(
    title: String,
    searchOpen: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onBack: () -> Unit,
    showSearchIcon: Boolean,
    onSearchOpen: () -> Unit,
    showEncodeIcon: Boolean,
    encodeUrl: Boolean,
    onEncodeUrl: () -> Unit,
    showFormatJsonIcon: Boolean,
    formatJson: Boolean,
    onFormatJsonChange: (Boolean) -> Unit,
    showSaveIcon: Boolean,
    onSave: () -> Unit,
    onShareText: () -> Unit,
    onShareCurl: () -> Unit,
    onShareFile: () -> Unit,
    onShareHar: () -> Unit,
) {
    var shareMenuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            if (searchOpen) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.focusRequester(focusRequester),
                )
            } else {
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.chucker_ic_arrow_back), contentDescription = null)
            }
        },
        actions = {
            if (searchOpen) {
                IconButton(onClick = onSearchClose) {
                    Icon(painterResource(R.drawable.chucker_ic_close), contentDescription = null)
                }
                return@TopAppBar
            }
            if (showSearchIcon) {
                IconButton(onClick = onSearchOpen) {
                    Icon(
                        painterResource(R.drawable.chucker_ic_search_white),
                        contentDescription = stringResource(R.string.chucker_search),
                    )
                }
            }
            if (showFormatJsonIcon) {
                IconToggleButton(checked = formatJson, onCheckedChange = onFormatJsonChange) {
                    Icon(
                        painterResource(R.drawable.chucker_ic_format_json_white),
                        contentDescription = stringResource(R.string.chucker_format_json),
                        // Dimmed when off, like a light that's switched off.
                        tint = if (formatJson) Color.White else Color.White.copy(alpha = UNSELECTED_TAB_ALPHA),
                    )
                }
            }
            if (showEncodeIcon) {
                IconButton(onClick = onEncodeUrl) {
                    val icon =
                        if (encodeUrl) {
                            R.drawable.chucker_ic_encoded_url_white
                        } else {
                            R.drawable.chucker_ic_decoded_url_white
                        }
                    Icon(painterResource(icon), contentDescription = stringResource(R.string.chucker_encode_url))
                }
            }
            Box {
                IconButton(onClick = { shareMenuExpanded = true }) {
                    Icon(
                        painterResource(R.drawable.chucker_ic_share_white),
                        contentDescription = stringResource(R.string.chucker_share),
                    )
                }
                DropdownMenu(expanded = shareMenuExpanded, onDismissRequest = { shareMenuExpanded = false }) {
                    ShareMenuItem(R.string.chucker_share_as_text, onShareText) { shareMenuExpanded = false }
                    ShareMenuItem(R.string.chucker_share_as_curl, onShareCurl) { shareMenuExpanded = false }
                    ShareMenuItem(R.string.chucker_share_as_file, onShareFile) { shareMenuExpanded = false }
                    ShareMenuItem(R.string.chucker_share_as_har, onShareHar) { shareMenuExpanded = false }
                }
            }
            if (showSaveIcon) {
                IconButton(onClick = onSave) {
                    Icon(
                        painterResource(R.drawable.chucker_ic_save_white),
                        contentDescription = stringResource(R.string.chucker_save_body),
                    )
                }
            }
        },
        colors = chuckerTopAppBarColors(),
    )
}

@Composable
private fun ShareMenuItem(
    titleRes: Int,
    action: () -> Unit,
    dismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(titleRes)) },
        onClick = {
            dismiss()
            action()
        },
    )
}

/**
 * The transaction's tabs. Three tabs share the width; WebSocket connections add a fourth, which
 * doesn't fit on phones (labels wrapped onto two lines), so the row scrolls instead.
 */
@Composable
private fun TransactionTabs(
    titles: List<String>,
    selectedIndex: Int,
    onTabClick: (Int) -> Unit,
) {
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabClick(index) },
                text = { Text(title.uppercase(), maxLines = 1, softWrap = false) },
                selectedContentColor = Color.White,
                unselectedContentColor = Color.White.copy(alpha = UNSELECTED_TAB_ALPHA),
            )
        }
    }
    if (titles.size > MAX_FIXED_TABS) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = chuckerAppBarContainerColor(),
            contentColor = Color.White,
            edgePadding = 0.dp,
            tabs = tabs,
        )
    } else {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = chuckerAppBarContainerColor(),
            contentColor = Color.White,
            tabs = tabs,
        )
    }
}

@Preview(name = "HTTP tabs", widthDp = 360)
@Composable
private fun TransactionTabsHttpPreview() {
    ChuckerTheme {
        TransactionTabs(titles = listOf("Overview", "Request", "Response"), selectedIndex = 0, onTabClick = {})
    }
}

@Preview(name = "WebSocket tabs", widthDp = 360)
@Composable
private fun TransactionTabsWebSocketPreview() {
    ChuckerTheme {
        TransactionTabs(
            titles = listOf("Overview", "Request", "Response", "Messages"),
            selectedIndex = 3,
            onTabClick = {},
        )
    }
}
