package com.nuvio.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.ui.aiotv.brand.AioBrandGlyph
import com.nuvio.tv.ui.aiotv.brand.AioBrandWordmark
import com.nuvio.tv.ui.aiotv.design.AioColors
import com.nuvio.tv.ui.aiotv.design.AioRadii
import com.nuvio.tv.ui.auth.AioTvGateState
import com.nuvio.tv.ui.auth.AioTvGateViewModel
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AioTvGateActivity : ComponentActivity() {
    private val viewModel: AioTvGateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NuvioTheme {
                val state by viewModel.state.collectAsState()
                AioTvGateScreen(
                    state = state,
                    onRetry = viewModel::retry,
                    onNewCode = viewModel::requestNewCode,
                    onReady = ::openMainActivity
                )
            }
        }
    }

    private fun openMainActivity() {
        val incoming = intent
        val target = Intent(this, MainActivity::class.java).apply {
            action = incoming.action
            data = incoming.data
            clipData = incoming.clipData
            incoming.extras?.let(::putExtras)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(target)
        finish()
    }
}

@Composable
private fun AioTvGateScreen(
    state: AioTvGateState,
    onRetry: () -> Unit,
    onNewCode: () -> Unit,
    onReady: () -> Unit
) {
    if (state is AioTvGateState.Ready) {
        LaunchedEffect(Unit) { onReady() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = AioColors.Canvas)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Quiet brand atmosphere rather than a decorative image-heavy splash.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(AioColors.Surface.copy(alpha = 0.34f))
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 72.dp, vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    AioTvGateState.Loading,
                    AioTvGateState.Ready -> LoadingContent()

                    is AioTvGateState.Pairing -> PairingContent(
                        state = state,
                        onNewCode = onNewCode
                    )

                    is AioTvGateState.Error -> ErrorContent(
                        message = state.message,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.width(520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AioBrandWordmark(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            contentDescription = "AIOtv"
        )
        LoadingIndicator()
        Text(
            text = "Preparing your AIOtv account…",
            style = MaterialTheme.typography.titleMedium,
            color = AioColors.TextSecondary
        )
    }
}

@Composable
private fun PairingContent(
    state: AioTvGateState.Pairing,
    onNewCode: () -> Unit
) {
    val qr = remember(state.verificationUri) {
        QrCodeGenerator.generate(state.verificationUri, size = 460, margin = 3).asImageBitmap()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = AioColors.TextPrimary,
                        shape = RoundedCornerShape(AioRadii.Large)
                    )
                    .border(
                        width = 1.dp,
                        color = AioColors.Divider,
                        shape = RoundedCornerShape(AioRadii.Large)
                    )
                    .padding(16.dp)
            ) {
                Image(
                    bitmap = qr,
                    contentDescription = "AIOtv pairing QR code",
                    modifier = Modifier.size(306.dp)
                )
            }
            Text(
                text = "Scan to connect this TV",
                style = MaterialTheme.typography.labelLarge,
                color = AioColors.TextMuted
            )
        }

        Spacer(modifier = Modifier.width(64.dp))

        Column(
            modifier = Modifier.width(540.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AioBrandWordmark(
                modifier = Modifier
                    .fillMaxWidth(0.74f)
                    .height(92.dp),
                contentDescription = "AIOtv"
            )

            Text(
                text = "Connect your TV",
                style = MaterialTheme.typography.headlineLarge,
                color = AioColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Scan the QR code with your phone, sign in with Pocket ID, then approve this TV. Your managed AIOtv profile and assigned addons will load automatically.",
                style = MaterialTheme.typography.bodyLarge,
                color = AioColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.userCode,
                style = MaterialTheme.typography.displaySmall,
                color = AioColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Confirm that this code matches the one on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = AioColors.TextMuted
            )

            AioGateButton(text = "Get a new code", onClick = onNewCode)
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.width(620.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AioBrandGlyph(
            modifier = Modifier.size(width = 132.dp, height = 88.dp),
            contentDescription = null
        )
        Text(
            text = "AIOtv couldn't start",
            style = MaterialTheme.typography.headlineMedium,
            color = AioColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = AioColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        AioGateButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
private fun AioGateButton(
    text: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(AioRadii.Small)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = AioColors.SurfaceRaised,
            focusedContainerColor = AioColors.SurfaceFocused,
            contentColor = AioColors.TextPrimary,
            focusedContentColor = AioColors.TextPrimary
        ),
        shape = ButtonDefaults.shape(shape = shape),
        border = ButtonDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AioColors.FocusBorder),
                shape = shape
            )
        )
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}
