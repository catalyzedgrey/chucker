package com.chuckerteam.chucker.internal.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.api.Chucker
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.model.DialogData
import com.chuckerteam.chucker.internal.support.FileSaver
import com.chuckerteam.chucker.internal.support.HarUtils
import com.chuckerteam.chucker.internal.support.Logger
import com.chuckerteam.chucker.internal.support.Sharable
import com.chuckerteam.chucker.internal.support.TransactionDetailsHarSharable
import com.chuckerteam.chucker.internal.support.TransactionListDetailsSharable
import com.chuckerteam.chucker.internal.support.shareAsFile
import com.chuckerteam.chucker.internal.ui.MainActivity.ExportType.HAR
import com.chuckerteam.chucker.internal.ui.MainActivity.ExportType.TEXT
import com.chuckerteam.chucker.internal.ui.compose.ChuckerConfirmDialog
import com.chuckerteam.chucker.internal.ui.compose.ChuckerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Source
import okio.buffer
import okio.source

internal class MainActivity : BaseChuckerActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val snackbarHostState = SnackbarHostState()
    private var pendingDialog by mutableStateOf<PendingDialog?>(null)

    private val applicationName: CharSequence
        get() = applicationInfo.loadLabel(packageManager)

    private val permissionRequest =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isPermissionGranted: Boolean ->
            if (!isPermissionGranted) {
                showToast(
                    applicationContext.getString(R.string.chucker_notifications_permission_not_granted),
                    Toast.LENGTH_LONG,
                )
                Logger.error("Notification permission denied. Can't show transactions info")
            }
        }

    private val saveTextToFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument(TEXT.mimeType)) { uri ->
            onSaveToFileActivityResult(uri, TEXT)
        }

    private val saveHarToFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument(HAR.mimeType)) { uri ->
            onSaveToFileActivityResult(uri, HAR)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ChuckerTheme {
                TransactionListScreen(
                    viewModel = viewModel,
                    applicationName = applicationName,
                    snackbarHostState = snackbarHostState,
                    onClear = ::onClearClicked,
                    onShareText = ::onShareTextClicked,
                    onShareHar = ::onShareHarClicked,
                    onSaveText = { showSaveDialog(TEXT) },
                    onSaveHar = { showSaveDialog(HAR) },
                )
                pendingDialog?.let { dialog ->
                    ChuckerConfirmDialog(
                        dialogData = dialog.dialogData,
                        onPositiveClick = dialog.onPositiveClick,
                        onDismiss = { pendingDialog = null },
                    )
                }
            }
        }

        if (Chucker.showNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            handleNotificationsPermission()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun handleNotificationsPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED -> {
                // We have permission, all good
            }

            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                lifecycleScope.launch {
                    val message = applicationContext.getString(R.string.chucker_notifications_permission_not_granted)
                    val result =
                        snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = applicationContext.getString(R.string.chucker_change),
                            duration = SnackbarDuration.Long,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                data = Uri.fromParts("package", packageName, null)
                            }.also { intent ->
                                startActivity(intent)
                            }
                    }
                }
            }

            else -> {
                permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun onClearClicked() {
        showDialog(
            getClearDialogData(),
            onPositiveClick = { viewModel.clearTransactions() },
        )
    }

    private fun onShareTextClicked() {
        val stringId =
            if (viewModel.isItemSelected.value == true) {
                R.string.chucker_export_text_selected_http_confirmation
            } else {
                R.string.chucker_export_text_http_confirmation
            }
        showDialog(
            getExportDialogData(stringId),
            onPositiveClick = {
                exportTransactions(EXPORT_TXT_FILE_NAME) { transactions ->
                    TransactionListDetailsSharable(transactions, encodeUrls = false)
                }
            },
        )
    }

    private fun onShareHarClicked() {
        val stringId =
            if (viewModel.isItemSelected.value == true) {
                R.string.chucker_export_har_selected_http_confirmation
            } else {
                R.string.chucker_export_har_http_confirmation
            }
        showDialog(
            getExportDialogData(stringId),
            onPositiveClick = {
                exportTransactions(EXPORT_HAR_FILE_NAME) { transactions ->
                    TransactionDetailsHarSharable(
                        HarUtils.harStringFromTransactions(
                            transactions,
                            getString(R.string.chucker_name),
                            getString(R.string.chucker_version),
                        ),
                    )
                }
            },
        )
    }

    private fun showDialog(
        dialogData: DialogData,
        onPositiveClick: () -> Unit,
    ) {
        pendingDialog = PendingDialog(dialogData, onPositiveClick)
    }

    private class PendingDialog(
        val dialogData: DialogData,
        val onPositiveClick: () -> Unit,
    )

    override fun onSaveInstanceState(outState: Bundle) {
        val selectedIds = viewModel.getSelectedIds()
        outState.putLongArray(KEY_SELECTED_TRANSACTION_IDS, selectedIds.toLongArray())
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val selectedIds = savedInstanceState.getLongArray(KEY_SELECTED_TRANSACTION_IDS)?.toList().orEmpty()
        viewModel.restoreSelection(selectedIds)
    }

    private fun exportTransactions(
        fileName: String,
        block: suspend (List<HttpTransaction>) -> Sharable,
    ) {
        val applicationContext = this.applicationContext
        lifecycleScope.launch {
            val transactions = viewModel.getTransactions()
            if (transactions.isEmpty()) {
                showToast(applicationContext.getString(R.string.chucker_export_empty_text))
                return@launch
            }

            val sharableTransactions = block(transactions)
            val shareIntent =
                withContext(Dispatchers.IO) {
                    sharableTransactions.shareAsFile(
                        activity = this@MainActivity,
                        fileName = fileName,
                        intentTitle = getString(R.string.chucker_share_all_transactions_title),
                        intentSubject = getString(R.string.chucker_share_all_transactions_subject),
                        clipDataLabel = "transactions",
                    )
                }
            if (shareIntent != null) {
                startActivity(shareIntent)
            } else {
                showToast(applicationContext.getString(R.string.chucker_export_no_file))
            }
        }
    }

    private fun getClearDialogData(): DialogData =
        DialogData(
            title = getString(R.string.chucker_clear),
            message =
                getString(
                    if (viewModel.isItemSelected.value == true) {
                        R.string.chucker_clear_selected_http_confirmation
                    } else {
                        R.string.chucker_clear_http_confirmation
                    },
                ),
            positiveButtonText = getString(R.string.chucker_clear),
            negativeButtonText = getString(R.string.chucker_cancel),
        )

    private fun getExportDialogData(
        @StringRes dialogMessage: Int,
    ): DialogData =
        DialogData(
            title = getString(R.string.chucker_export),
            message = getString(dialogMessage),
            positiveButtonText = getString(R.string.chucker_export),
            negativeButtonText = getString(R.string.chucker_cancel),
        )

    private fun getSaveDialogData(
        @StringRes dialogMessage: Int,
    ): DialogData =
        DialogData(
            title = getString(R.string.chucker_save),
            message = getString(dialogMessage),
            positiveButtonText = getString(R.string.chucker_save),
            negativeButtonText = getString(R.string.chucker_cancel),
        )

    private fun showSaveDialog(exportType: ExportType) {
        showDialog(
            getSaveDialogData(
                when (exportType) {
                    TEXT -> R.string.chucker_save_text_http_confirmation
                    HAR -> R.string.chucker_save_har_http_confirmation
                },
            ),
            onPositiveClick = {
                when (exportType) {
                    TEXT -> saveTextToFile.launch(EXPORT_TXT_FILE_NAME)
                    HAR -> saveHarToFile.launch(EXPORT_HAR_FILE_NAME)
                }
            },
        )
    }

    private fun onSaveToFileActivityResult(
        uri: Uri?,
        exportType: ExportType,
    ) {
        if (uri == null) {
            Toast
                .makeText(
                    applicationContext,
                    R.string.chucker_save_failed_to_open_document,
                    Toast.LENGTH_SHORT,
                ).show()
            return
        }
        lifecycleScope.launch {
            val source =
                runCatching {
                    prepareDataToSave(exportType)
                }.getOrNull() ?: return@launch
            val result = FileSaver.saveFile(source, uri, contentResolver)
            val toastMessageId =
                if (result) {
                    R.string.chucker_file_saved
                } else {
                    R.string.chucker_file_not_saved
                }
            Toast.makeText(applicationContext, toastMessageId, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun prepareDataToSave(exportType: ExportType): Source? {
        val transactions = viewModel.getTransactions()
        if (transactions.isEmpty()) {
            showToast(applicationContext.getString(R.string.chucker_save_empty_text))
            return null
        }
        return withContext(Dispatchers.IO) {
            when (exportType) {
                TEXT -> {
                    TransactionListDetailsSharable(
                        transactions,
                        encodeUrls = false,
                    ).toSharableContent(this@MainActivity)
                }

                HAR -> {
                    HarUtils
                        .harStringFromTransactions(
                            transactions,
                            getString(R.string.chucker_name),
                            getString(R.string.chucker_version),
                        ).byteInputStream()
                        .source()
                        .buffer()
                }
            }
        }
    }

    private enum class ExportType(
        val mimeType: String,
    ) {
        TEXT("text/plain"),
        HAR("application/har+json"),
    }

    companion object {
        private const val EXPORT_TXT_FILE_NAME = "transactions.txt"
        private const val EXPORT_HAR_FILE_NAME = "transactions.har"
        private const val KEY_SELECTED_TRANSACTION_IDS = "selectedTransactionIds"
    }
}
