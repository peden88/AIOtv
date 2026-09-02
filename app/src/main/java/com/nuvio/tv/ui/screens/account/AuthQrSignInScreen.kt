@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.ui.components.BrandWordmark

private val PairingTextPrimary = Color(0xFFF5F7F8)
private val PairingTextSecondary = Color(0xFF969CA3)
private val PairingPaneBackground = Color.White.copy(alpha = 0.025f)
private val PairingPaneBorder = Color.White.copy(alpha = 0.08f)

/**
 * AIOtv managed-device sign in.
 *
 * The original Nuvio QR-account flow is intentionally replaced for AIOtv. A new installation
 * requests a short code from AIOtv Control, displays it on the television, and waits for an
 * administrator to assign that device to a managed user. Once approved, the assigned addon set
 * is installed locally and the normal Nuvio content engine takes over.
 */
@Composable
fun AuthQrSignInScreen(
    onBackPress: () -> Unit = {},
    onContinue: (() -> Unit)? = null,
    viewModel: AccountViewModel = hiltViewModel(),
    pairingViewModel: AiotvManagedPairingViewModel = hiltViewModel()
) {
    // Keep the AccountViewModel parameter for source compatibility with existing callers while
    // deliberately avoiding the legacy Nuvio/Supabase authentication flow.
    @Suppress("UNUSED_VARIABLE") val compatibilityViewModel = viewModel
    val state by pairingViewModel.state.collectAsState()

    BackHandler { onBackPress() }

    LaunchedEffect(state) {
        if (state is AiotvPairingState.Paired) {
            onContinue?.invoke()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 64.dp),
                verticalArrangement = Arrangement.Center
            ) {
                BrandWordmark()
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "AIOtv",
                    color = PairingTextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Your TV is managed securely through AIOtv Control. Addons and service configuration are assigned automatically after this device is approved.",
                    color = PairingTextSecondary,
                    fontSize = 19.sp,
                    lineHeight = 28.sp,
                    modifier = Modifier.fillMaxWidth(0.82f)
                )
            }

            Column(
                modifier = Modifier
                    .width(470.dp)
                    .fillMaxHeight()
                    .background(PairingPaneBackground)
                    .border(width = 1.dp, color = PairingPaneBorder)
                    .padding(horizontal = 52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when (state) {
                        AiotvPairingState.Starting -> "Connecting to AIOtv Control"
                        is AiotvPairingState.Waiting -> "Enter this code in AIOtv Control"
                        is AiotvPairingState.Applying -> "Applying your configuration"
                        is AiotvPairingState.Paired -> "Device approved"
                        is AiotvPairingState.Error -> "Unable to pair"
                    },
                    color = PairingTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(28.dp))

                when (val current = state) {
                    AiotvPairingState.Starting -> {
                        Text("Requesting a login code…", color = PairingTextSecondary, fontSize = 18.sp)
                    }
                    is AiotvPairingState.Waiting -> {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 34.dp, vertical = 24.dp)
                        ) {
                            Text(
                                text = current.code,
                                color = PairingTextPrimary,
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 7.sp
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "Give this code to the administrator. This screen will continue automatically after the TV is assigned to your user.",
                            color = PairingTextSecondary,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    is AiotvPairingState.Applying -> {
                        Text(
                            text = "Approved for ${current.userName}. Installing the managed addon set…",
                            color = PairingTextSecondary,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    is AiotvPairingState.Paired -> {
                        Text(
                            text = "Signed in as ${current.userName}",
                            color = PairingTextSecondary,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    is AiotvPairingState.Error -> {
                        Text(
                            text = current.message,
                            color = PairingTextSecondary,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = pairingViewModel::retry) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
