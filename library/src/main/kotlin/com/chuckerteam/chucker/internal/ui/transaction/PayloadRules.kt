package com.chuckerteam.chucker.internal.ui.transaction

import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import okio.Source
import okio.source
import java.io.IOException

/**
 * Pure rules for the payload tab, moved verbatim from the former `TransactionPayloadFragment`.
 */
internal object PayloadRules {
    private const val TRANSACTION_EXCEPTION = "Transaction not ready"
    const val DEFAULT_FILE_PREFIX = "chucker-export-"

    fun shouldShowSaveIcon(
        payloadType: PayloadType,
        transaction: HttpTransaction?,
    ) = when {
        (payloadType == PayloadType.REQUEST) -> (0L != (transaction?.requestPayloadSize))
        (payloadType == PayloadType.RESPONSE) -> (0L != (transaction?.responsePayloadSize))
        else -> true
    }

    fun shouldShowSearchIcon(
        payloadType: PayloadType,
        transaction: HttpTransaction?,
    ) = when (payloadType) {
        PayloadType.REQUEST -> {
            (false == transaction?.isRequestBodyEncoded) && (0L != (transaction.requestPayloadSize))
        }

        PayloadType.RESPONSE -> {
            (false == transaction?.isResponseBodyEncoded) && (0L != (transaction.responsePayloadSize))
        }
    }

    fun prepareDataToSave(
        type: PayloadType,
        transaction: HttpTransaction,
    ): Source =
        when (type) {
            PayloadType.REQUEST -> {
                transaction.requestBody?.byteInputStream()?.source()
                    ?: throw IOException(TRANSACTION_EXCEPTION)
            }

            PayloadType.RESPONSE -> {
                transaction.responseBody?.byteInputStream()?.source()
                    ?: throw IOException(TRANSACTION_EXCEPTION)
            }
        }

    fun isBodyEmpty(
        type: PayloadType,
        transaction: HttpTransaction,
    ): Boolean =
        when {
            type == PayloadType.REQUEST && transaction.requestBody == null -> true
            type == PayloadType.RESPONSE && transaction.responseBody == null -> true
            else -> false
        }
}
