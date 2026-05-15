@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.firesin.xuipanel.feature.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.core.xui.share.ShareError
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val QR_SIZE_DP = 240.dp
private val QrCardShape = RoundedCornerShape(22.dp)
private val LinkCardShape = RoundedCornerShape(14.dp)

@Composable
fun ClientShareScreen(
    onBack: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.share_copied)

    ClientShareContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onCopied = { scope.launch { snackbarHostState.showSnackbar(copiedMessage) } },
    )
}

@Composable
private fun ClientShareContent(
    uiState: ShareUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCopied: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.share_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.share_cd_back),
                        )
                    }
                },
                actions = {
                    // Share icon in top bar — only visible for Content state
                    if (uiState is ShareUiState.Content) {
                        val context = LocalContext.current
                        val uri = uiState.uri
                        IconButton(onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, uri)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.share_cd_share),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState) {
                is ShareUiState.Loading -> CircularProgressIndicator()
                is ShareUiState.Content -> ContentBody(state = uiState, onCopied = onCopied)
                is ShareUiState.Error -> ErrorBody(state = uiState, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun ContentBody(
    state: ShareUiState.Content,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val qrSizePx = with(density) { QR_SIZE_DP.toPx() }.toInt()

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

    // QR always shows the direct protocol URL — that's what client apps (v2rayNG,
    // Karing, Streisand, …) actually consume by scanning. Subscription / Clash
    // URLs are shown below as separate cards with copy/send buttons.
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(state.uri, onSurfaceColor, surfaceColor) {
        qrBitmap = withContext(Dispatchers.Default) {
            generateQrBitmap(state.uri, qrSizePx, onSurfaceColor, surfaceColor)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header: caption + email
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.share_caption_for),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.client.email,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFontFamily,
                ),
                textAlign = TextAlign.Center,
            )
        }

        // QR card
        Surface(
            shape = QrCardShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = QrCardShape,
                ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val bitmap = qrBitmap
                if (bitmap != null) {
                    Image(
                        painter = remember(bitmap) { BitmapPainter(bitmap) },
                        contentDescription = stringResource(R.string.share_cd_qr),
                        modifier = Modifier.size(QR_SIZE_DP),
                    )
                } else {
                    Box(
                        modifier = Modifier.size(QR_SIZE_DP),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.share_qr_hint),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // All available link formats — stacked. Direct is always shown; Subscription
        // and Clash only when the panel exposes them. Long URLs are clipped to two
        // lines + ellipsis so the cards don't dominate the screen.
        LinkCard(
            title = stringResource(R.string.share_link_section),
            uri = state.uri,
            onCopied = onCopied,
        )
        state.subUri?.takeIf { it.isNotBlank() }?.let { sub ->
            LinkCard(
                title = stringResource(R.string.share_section_sub),
                uri = sub,
                onCopied = onCopied,
            )
        }
        state.clashUri?.takeIf { it.isNotBlank() }?.let { clash ->
            LinkCard(
                title = stringResource(R.string.share_section_clash),
                uri = clash,
                onCopied = onCopied,
            )
        }
    }
}

@Composable
private fun LinkCard(
    title: String,
    uri: String,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = LinkCardShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = LinkCardShape,
            ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = uri,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("share_uri", uri))
                        onCopied()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.share_copy))
                }
                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, uri)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.share_send))
                }
            }
        }
    }
}

@Composable
private fun ErrorBody(
    state: ShareUiState.Error,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.toUserMessage(context),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.share_retry))
        }
    }
}

@Composable
private fun ShareUiState.Error.toUserMessage(context: Context): String {
    return when {
        shareError != null -> shareError.toUserMessage()
        domainError != null -> context.getString(R.string.share_error_domain)
        else -> context.getString(R.string.share_error_domain)
    }
}

@Composable
private fun ShareError.toUserMessage(): String = when (this) {
    is ShareError.UnsupportedTransport ->
        stringResource(R.string.share_error_unsupported_transport, network)
    is ShareError.InvalidStreamSettings ->
        stringResource(R.string.share_error_invalid_stream_settings, reason)
    is ShareError.MissingInboundPassword ->
        stringResource(R.string.share_error_missing_inbound_password)
    is ShareError.UnsupportedProtocol ->
        stringResource(R.string.share_error_unsupported_protocol, protocol)
}

/**
 * Generates a QR bitmap using ZXing core.
 *
 * [foreground] and [background] should be the current theme colors (onSurface / surface)
 * so the QR respects dark/light mode. Called on [Dispatchers.Default].
 *
 * Design: docs/architecture/share-config.md §6
 */
fun generateQrBitmap(
    text: String,
    sizePx: Int,
    foreground: Color,
    background: Color,
): ImageBitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 1,
    )
    val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val fgArgb = foreground.toArgb()
    val bgArgb = background.toArgb()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) fgArgb else bgArgb)
        }
    }
    return bitmap.asImageBitmap()
}

@Preview(showBackground = true)
@Composable
private fun ClientShareLoadingPreview() {
    XuiPanelTheme {
        ClientShareContent(
            uiState = ShareUiState.Loading,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onRetry = {},
            onCopied = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientShareErrorPreview() {
    XuiPanelTheme {
        ClientShareContent(
            uiState = ShareUiState.Error(shareError = ShareError.UnsupportedTransport("kcp")),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onRetry = {},
            onCopied = {},
        )
    }
}
