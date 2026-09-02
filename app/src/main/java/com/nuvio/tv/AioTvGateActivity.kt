package com.nuvio.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
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
        colors = SurfaceDefaults.colors(containerColor = NuvioTheme.colors.Background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 48.dp),
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

@Composable
private fun LoadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        LoadingIndicator()
        Text(
            text = "Preparing your AIOtv account…",
            style = MaterialTheme.typography.titleMedium,
            color = NuvioTheme.colors.TextSecondary
        )
    }
}

@Composable
private fun PairingContent(
    state: AioTvGateState.Pairing,
    onNewCode: () -> Unit
) {
    Column(
        modifier = Modifier.width(760.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Text(
            text = "Connect AIOtv",
            style = MaterialTheme.typography.headlineLarge,
            color = NuvioTheme.colors.TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Send the code below to your AIOtv administrator. They will choose your managed user and approve this television.",
            style = MaterialTheme.typography.bodyLarge,
            color = NuvioTheme.colors.TextSecondary,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .background(
                    color = NuvioTheme.colors.BackgroundCard,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 52.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.userCode,
                style = MaterialTheme.typography.displayLarge,
                color = NuvioTheme.colors.Secondary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "Keep this screen open. AIOtv will continue automatically as soon as the administrator assigns this TV.",
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.colors.TextTertiary,
            textAlign = TextAlign.Center
        )

        Button(onClick = onNewCode) {
            Text("Get a new code")
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
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "AIOtv couldn't start",
            style = MaterialTheme.typography.headlineMedium,
            color = NuvioTheme.colors.TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = NuvioTheme.colors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
