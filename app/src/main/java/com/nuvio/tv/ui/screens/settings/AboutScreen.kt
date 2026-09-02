@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.ui.aiotv.brand.AioBrandWordmark
import com.nuvio.tv.ui.aiotv.design.AioColors

@Composable
fun AboutScreen(
    onNavigateToSupportersContributors: () -> Unit = {},
    onNavigateToLicensesAttributions: () -> Unit = {},
    onBackPress: () -> Unit = {}
) {
    BackHandler { onBackPress() }

    SettingsStandaloneScaffold(
        title = "About AIOtv",
        subtitle = "Version, licences and project information"
    ) {
        AboutSettingsContent(
            onNavigateToSupportersContributors = onNavigateToSupportersContributors,
            onNavigateToLicensesAttributions = onNavigateToLicensesAttributions
        )
    }
}

@Composable
fun AboutSettingsContent(
    onNavigateToSupportersContributors: () -> Unit = {},
    onNavigateToLicensesAttributions: () -> Unit = {},
    initialFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsDetailHeader(
            title = "About AIOtv",
            subtitle = "AIOtv product information and open-source acknowledgements"
        )

        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            title = null
        ) {
            val aboutScrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(aboutScrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    AioBrandWordmark(
                        modifier = Modifier
                            .width(310.dp)
                            .height(92.dp),
                        contentDescription = "AIOtv"
                    )

                    Text(
                        text = "Your media. One interface.",
                        style = MaterialTheme.typography.titleMedium,
                        color = AioColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelMedium,
                        color = AioColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "AIOtv is built on the open-source NuvioTV project and retains its upstream licence and attribution requirements.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AioColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(620.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (AppFeaturePolicy.inAppUpdatesEnabled) {
                        UpdateChannelSettings(initialFocusRequester)
                    }

                    // Keep upstream legal information explicit without presenting
                    // Nuvio as AIOtv's product identity.
                    SettingsActionRow(
                        title = "Upstream NuvioTV project",
                        subtitle = "Open the project AIOtv is built upon",
                        modifier = if (!AppFeaturePolicy.inAppUpdatesEnabled && initialFocusRequester != null) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else Modifier,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NuvioMedia/NuvioTV"))
                            )
                        }
                    )

                    if (AppFeaturePolicy.supportNuvioEnabled) {
                        SettingsActionRow(
                            title = "NuvioTV supporters & contributors",
                            subtitle = stringResource(R.string.about_supporters_contributors_subtitle),
                            trailingIcon = Icons.Default.ChevronRight,
                            onClick = onNavigateToSupportersContributors
                        )
                    }

                    SettingsActionRow(
                        title = stringResource(R.string.about_licenses_attributions),
                        subtitle = stringResource(R.string.about_licenses_attributions_subtitle),
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = onNavigateToLicensesAttributions
                    )
                }
                SettingsVerticalScrollIndicators(state = aboutScrollState)
            }
        }
    }
}
