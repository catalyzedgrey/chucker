package com.chuckerteam.chucker.internal.ui.transaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.support.HarUtils
import com.chuckerteam.chucker.internal.support.Sharable
import com.chuckerteam.chucker.internal.support.TransactionCurlCommandSharable
import com.chuckerteam.chucker.internal.support.TransactionDetailsHarSharable
import com.chuckerteam.chucker.internal.support.TransactionDetailsSharable
import com.chuckerteam.chucker.internal.support.shareAsFile
import com.chuckerteam.chucker.internal.support.shareAsUtf8Text
import com.chuckerteam.chucker.internal.ui.BaseChuckerActivity
import com.chuckerteam.chucker.internal.ui.compose.ChuckerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TransactionActivity : BaseChuckerActivity() {
    private val viewModel: TransactionViewModel by viewModels {
        TransactionViewModelFactory(intent.getLongExtra(EXTRA_TRANSACTION_ID, 0))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChuckerTheme {
                TransactionScreen(
                    viewModel = viewModel,
                    initialPage = selectedTabPosition,
                    onPageChanged = { selectedTabPosition = it },
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onShareText = {
                        shareTransactionAsText { transaction ->
                            val encodeUrls = viewModel.encodeUrl.value!!
                            TransactionDetailsSharable(transaction, encodeUrls)
                        }
                    },
                    onShareCurl = {
                        shareTransactionAsText { transaction ->
                            TransactionCurlCommandSharable(transaction)
                        }
                    },
                    onShareFile = {
                        shareTransactionAsFile(EXPORT_TXT_FILE_NAME) { transaction ->
                            val encodeUrls = viewModel.encodeUrl.value!!
                            TransactionDetailsSharable(transaction, encodeUrls)
                        }
                    },
                    onShareHar = {
                        shareTransactionAsFile(EXPORT_HAR_FILE_NAME) { transaction ->
                            TransactionDetailsHarSharable(
                                HarUtils.harStringFromTransactions(
                                    listOf(transaction),
                                    getString(R.string.chucker_name),
                                    getString(R.string.chucker_version),
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    private fun shareTransactionAsText(block: (HttpTransaction) -> Sharable): Boolean {
        val transaction = viewModel.transaction.value
        if (transaction == null) {
            showToast(getString(R.string.chucker_request_not_ready))
            return true
        }
        val sharable = block(transaction)
        lifecycleScope.launch {
            val shareIntent =
                sharable.shareAsUtf8Text(
                    activity = this@TransactionActivity,
                    intentTitle = getString(R.string.chucker_share_transaction_title),
                    intentSubject = getString(R.string.chucker_share_transaction_subject),
                )
            startActivity(shareIntent)
        }
        return true
    }

    private fun shareTransactionAsFile(
        fileName: String,
        block: suspend (HttpTransaction) -> Sharable,
    ): Boolean {
        lifecycleScope.launch {
            val transaction = viewModel.transaction.value
            if (transaction == null) {
                showToast(getString(R.string.chucker_request_not_ready))
                return@launch
            }

            val sharable = block(transaction)
            val shareIntent =
                withContext(Dispatchers.IO) {
                    sharable.shareAsFile(
                        activity = this@TransactionActivity,
                        fileName = fileName,
                        intentTitle = getString(R.string.chucker_share_transaction_title),
                        intentSubject = getString(R.string.chucker_share_transaction_subject),
                        clipDataLabel = "transaction",
                    )
                }
            if (shareIntent != null) {
                startActivity(shareIntent)
            } else {
                Toast.makeText(applicationContext, R.string.chucker_export_no_file, Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    companion object {
        private const val EXPORT_TXT_FILE_NAME = "transaction.txt"
        private const val EXPORT_HAR_FILE_NAME = "transaction.har"
        private const val EXTRA_TRANSACTION_ID = "transaction_id"
        private var selectedTabPosition = 0

        fun start(
            context: Context,
            transactionId: Long,
        ) {
            val intent = Intent(context, TransactionActivity::class.java)
            intent.putExtra(EXTRA_TRANSACTION_ID, transactionId)
            context.startActivity(intent)
        }
    }
}
