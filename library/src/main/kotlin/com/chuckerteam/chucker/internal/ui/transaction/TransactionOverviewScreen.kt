package com.chuckerteam.chucker.internal.ui.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuckerteam.chucker.R

private const val LABEL_WEIGHT = 0.28f
private val OverviewTextSize = 14.sp

@Composable
internal fun TransactionOverviewScreen(
    viewModel: TransactionViewModel,
    modifier: Modifier = Modifier,
) {
    val transaction by viewModel.transaction.observeAsState()
    val encodeUrl by viewModel.encodeUrl.observeAsState(false)

    SelectionContainer {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            val t = transaction
            OverviewRow(R.string.chucker_url, t?.getFormattedUrl(encodeUrl))
            OverviewRow(R.string.chucker_host_ip, t?.hostIp)
            OverviewRow(R.string.chucker_method, t?.method)
            OverviewRow(R.string.chucker_protocol, t?.protocol)
            OverviewRow(R.string.chucker_status, t?.status.toString())
            OverviewRow(R.string.chucker_response, t?.responseSummaryText)
            if (t != null) {
                val sslValue = if (t.isSsl == true) R.string.chucker_yes else R.string.chucker_no
                OverviewRow(R.string.chucker_ssl, stringResource(sslValue), topPadding = 8)
            }
            if (t?.responseTlsVersion != null) {
                OverviewRow(R.string.chucker_tls_version, t.responseTlsVersion)
            }
            if (t?.responseCipherSuite != null) {
                OverviewRow(R.string.chucker_tls_cipher_suite, t.responseCipherSuite)
            }
            OverviewRow(R.string.chucker_request_time, t?.requestDateString, topPadding = 8)
            OverviewRow(R.string.chucker_response_time, t?.responseDateString)
            OverviewRow(R.string.chucker_duration, t?.durationString)
            OverviewRow(R.string.chucker_request_size, t?.requestSizeString, topPadding = 8)
            OverviewRow(R.string.chucker_response_size, t?.responseSizeString)
            OverviewRow(R.string.chucker_total_size, t?.totalSizeString)
        }
    }
}

@Composable
private fun OverviewRow(
    labelRes: Int,
    value: String?,
    topPadding: Int = 0,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.weight(LABEL_WEIGHT),
            style = TextStyle(fontSize = OverviewTextSize, fontWeight = FontWeight.Bold),
        )
        Text(
            text = value.orEmpty(),
            modifier = Modifier.weight(1f - LABEL_WEIGHT).padding(start = 16.dp),
            style = TextStyle(fontSize = OverviewTextSize),
        )
    }
}
