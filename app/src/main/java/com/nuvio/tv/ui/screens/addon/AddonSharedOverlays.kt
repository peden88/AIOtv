package com.nuvio.tv.ui.screens.addon

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Shared QR overlay used by several upstream configuration screens.
 *
 * AIOtv replaced the original addon manager screen, but Debrid, Layout and
 * legacy setup screens still depend on these presentation helpers. Keeping
 * them in a separate file preserves those screens without restoring user
 * addon-management controls.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun QrCodeOverlay(
    qrBitmap: Bitmap?,
    serverUrl: String?,
    instruction: String,
    onClose: () -> Unit,
    hasPendingChange: Boolean = false,
    qrSize: Dp = 240.dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Overlay),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            colors = SurfaceDefaults.colors(containerColor = NuvioTheme.colors.BackgroundCard),
            shape = RoundedCornerShape(NuvioTheme.radii.lg)
        ) {
            Row(
                modifier = Modifier.padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = androidx.compose.ui.graphics.Color.White,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR code",
                            modifier = Modifier.size(qrSize)
                        )
                    } else {
                        Box(modifier = Modifier.size(qrSize), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Generating QR…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.ui.graphics.Color.Black
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.width(430.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = instruction,
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioTheme.colors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    serverUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioTheme.colors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (hasPendingChange) {
                        Text(
                            text = "A change is waiting for confirmation on this TV.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioTheme.colors.Secondary
                        )
                    }

                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundElevated,
                            contentColor = NuvioTheme.colors.TextPrimary,
                            focusedContainerColor = NuvioTheme.colors.FocusBackground,
                            focusedContentColor = NuvioTheme.colors.TextPrimary
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ConfirmAddonChangesDialog(
    pendingChange: PendingChangeInfo,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val changeSummary = buildList {
        if (pendingChange.addedUrls.isNotEmpty()) add("${pendingChange.addedUrls.size} addon(s) added")
        if (pendingChange.removedUrls.isNotEmpty()) add("${pendingChange.removedUrls.size} addon(s) removed")
        if (pendingChange.catalogsReordered) add("catalog order changed")
        if (pendingChange.disabledCatalogNames.isNotEmpty()) add("catalog visibility changed")
        if (pendingChange.enabledCatalogNames.isNotEmpty()) add("catalog visibility changed")
        if (pendingChange.collectionsChanged) add("collections changed")
        if (pendingChange.proposedFollowAddonsOrder != null) add("catalog ordering mode changed")
    }.distinct().joinToString(", ").ifBlank { "configuration changes" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Overlay),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            colors = SurfaceDefaults.colors(containerColor = NuvioTheme.colors.BackgroundCard),
            shape = RoundedCornerShape(NuvioTheme.radii.lg)
        ) {
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Confirm changes",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioTheme.colors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "A connected configuration page requested $changeSummary. Approve only if you initiated this request.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Button(
                        onClick = onReject,
                        enabled = !pendingChange.isApplying,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundElevated,
                            contentColor = NuvioTheme.colors.TextPrimary,
                            focusedContainerColor = NuvioTheme.colors.FocusBackground,
                            focusedContentColor = NuvioTheme.colors.TextPrimary
                        )
                    ) {
                        Text("Reject")
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !pendingChange.isApplying,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.Secondary,
                            contentColor = NuvioTheme.colors.OnSecondary,
                            focusedContainerColor = NuvioTheme.colors.SecondaryVariant,
                            focusedContentColor = NuvioTheme.colors.OnSecondary
                        )
                    ) {
                        Text(if (pendingChange.isApplying) "Applying…" else "Confirm")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AddonMessageOverlay(
    message: String?,
    isError: Boolean
) {
    if (message.isNullOrBlank()) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            colors = SurfaceDefaults.colors(containerColor = NuvioTheme.colors.BackgroundCard),
            shape = RoundedCornerShape(NuvioTheme.radii.md)
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) NuvioTheme.colors.Error else NuvioTheme.colors.Success,
                textAlign = TextAlign.Center
            )
        }
    }
}
