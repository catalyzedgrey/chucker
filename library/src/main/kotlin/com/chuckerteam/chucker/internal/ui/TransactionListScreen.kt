package com.chuckerteam.chucker.internal.ui

import android.text.Spanned
import android.text.style.URLSpan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.ui.compose.chuckerTopAppBarColors
import com.chuckerteam.chucker.internal.ui.transaction.TransactionActivity
import com.chuckerteam.chucker.internal.ui.transaction.TransactionListItem

@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionListScreen(
    viewModel: MainViewModel,
    applicationName: CharSequence,
    snackbarHostState: SnackbarHostState,
    onClear: () -> Unit,
    onShareText: () -> Unit,
    onShareHar: () -> Unit,
    onSaveText: () -> Unit,
    onSaveHar: () -> Unit,
) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.observeAsState()
    val selectedIds by viewModel.selectedItemIds.observeAsState(emptyList())
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = chuckerTopAppBarColors(),
                title = {
                    if (isSearching) {
                        SearchField(onQueryChange = viewModel::updateItemsFilter)
                    } else {
                        Column {
                            Text(stringResource(R.string.chucker_name))
                            Text(text = applicationName.toString(), fontSize = 14.sp)
                        }
                    }
                },
                actions = {
                    if (isSearching) {
                        IconButton(
                            onClick = {
                                isSearching = false
                                viewModel.updateItemsFilter("")
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.chucker_ic_close),
                                contentDescription = null,
                            )
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                painter = painterResource(R.drawable.chucker_ic_search_white),
                                contentDescription = stringResource(R.string.chucker_search),
                            )
                        }
                        Box {
                            IconButton(onClick = { exportMenuExpanded = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.chucker_ic_share_white),
                                    contentDescription = stringResource(R.string.chucker_export),
                                )
                            }
                            DropdownMenu(
                                expanded = exportMenuExpanded,
                                onDismissRequest = { exportMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chucker_share_as_text)) },
                                    onClick = {
                                        exportMenuExpanded = false
                                        onShareText()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chucker_share_as_har)) },
                                    onClick = {
                                        exportMenuExpanded = false
                                        onShareHar()
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chucker_save_as_text)) },
                                    onClick = {
                                        exportMenuExpanded = false
                                        onSaveText()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chucker_save_as_har)) },
                                    onClick = {
                                        exportMenuExpanded = false
                                        onSaveHar()
                                    },
                                )
                            }
                        }
                        IconButton(onClick = onClear) {
                            Icon(
                                painter = painterResource(R.drawable.chucker_ic_delete_white),
                                contentDescription = stringResource(R.string.chucker_clear),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val items = transactions
        when {
            items == null -> Box(Modifier.padding(innerPadding))
            items.isEmpty() -> TutorialContent(Modifier.padding(innerPadding))
            else ->
                LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    items(items, key = { it.id }) { transaction ->
                        TransactionListItem(
                            transaction = transaction,
                            isSelected = selectedIds.contains(transaction.id),
                            onClick = {
                                if (viewModel.isItemSelected.value == true) {
                                    viewModel.startSelection(transaction.id)
                                } else {
                                    TransactionActivity.start(context, transaction.id)
                                }
                            },
                            onLongClick = { viewModel.startSelection(transaction.id) },
                        )
                        HorizontalDivider()
                    }
                }
        }
    }
}

@Composable
private fun SearchField(onQueryChange: (String) -> Unit) {
    val state = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect(onQueryChange)
    }
    TextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        placeholder = { Text(stringResource(R.string.chucker_search)) },
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color.White,
                focusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
            ),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

@Composable
private fun TutorialContent(modifier: Modifier = Modifier) {
    val link = linkedText(LocalResources.current.getText(R.string.chucker_check_readme))
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.weight(1f).padding(bottom = 8.dp), contentAlignment = Alignment.BottomStart) {
            Text(
                text = stringResource(R.string.chucker_setup),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        Text(stringResource(R.string.chucker_network_tutorial))
        Box(Modifier.weight(1f).padding(top = 8.dp), contentAlignment = Alignment.TopStart) {
            Text(link)
        }
    }
}

@Composable
private fun linkedText(text: CharSequence): AnnotatedString {
    val linkStyle =
        TextLinkStyles(
            SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
        )
    return buildAnnotatedString {
        append(text.toString())
        if (text is Spanned) {
            text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
                addLink(
                    LinkAnnotation.Url(span.url, linkStyle),
                    text.getSpanStart(span),
                    text.getSpanEnd(span),
                )
            }
        }
    }
}
