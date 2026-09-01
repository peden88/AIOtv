@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.LocaleCache
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * AIOtv uses a single administrator-defined visual identity. Theme, AMOLED,
 * settings-style, launcher-artwork and font controls from upstream Nuvio are
 * intentionally not exposed. Language remains user-configurable.
 */
@Composable
fun ThemeSettingsScreen(
    viewModel: ThemeSettingsViewModel = hiltViewModel(),
    onBackPress: () -> Unit
) {
    BackHandler { onBackPress() }

    SettingsStandaloneScaffold(
        title = stringResource(R.string.appearance_title),
        subtitle = stringResource(R.string.appearance_subtitle)
    ) {
        ThemeSettingsContent(viewModel = viewModel)
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun ThemeSettingsContent(
    viewModel: ThemeSettingsViewModel = hiltViewModel(),
    initialFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var pendingLanguageRestart by remember { mutableStateOf(false) }

    val systemLanguage = stringResource(R.string.appearance_language_system)
    val supportedLocales = remember(systemLanguage) {
        val tags = listOf(
            "en", "ru", "ar", "bg", "bs", "da", "de", "el", "es", "es-419", "hu", "fr", "in", "it",
            "no", "pl", "pt-PT", "pt-BR", "tr", "uk", "cs", "sk", "sl", "sq", "sr-Latn", "sv", "ta", "ro", "ja",
            "nl", "vi", "hi", "lt", "he", "zh-CN", "zh-TW"
        )
        listOf(null to systemLanguage) + tags.map { tag ->
            val locale = Locale.forLanguageTag(tag)
            tag to locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
        }.sortedBy { it.second }
    }

    var selectedTag by remember {
        mutableStateOf(
            context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
                .getString("locale_tag", null)
                ?.takeIf { it.isNotEmpty() }
        )
    }

    val currentLocaleName = supportedLocales
        .firstOrNull { it.first == selectedTag }
        ?.second
        ?: systemLanguage
    val restartHint = stringResource(R.string.appearance_language_restart_hint)

    LaunchedEffect(pendingLanguageRestart, showLanguageDialog) {
        if (pendingLanguageRestart && !showLanguageDialog) {
            delay(150)
            context.findActivity()?.recreate()
                ?: Toast.makeText(context, restartHint, Toast.LENGTH_LONG).show()
            pendingLanguageRestart = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.appearance_title),
            subtitle = stringResource(R.string.appearance_language_subtitle)
        )

        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.appearance_language),
            subtitle = stringResource(R.string.appearance_language_subtitle)
        ) {
            SettingsActionRow(
                title = stringResource(R.string.appearance_language),
                subtitle = stringResource(R.string.appearance_language_subtitle),
                value = currentLocaleName,
                onClick = { showLanguageDialog = true },
                modifier = initialFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
            )
        }
    }

    if (showLanguageDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.appearance_language_dialog_title),
            options = supportedLocales.map { (tag, name) ->
                SettingsPickerOption(tag, name)
            },
            selectedValue = selectedTag,
            onOptionSelected = { tag ->
                val previousTag = selectedTag
                val newTag = tag ?: ""
                context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
                    .edit()
                    .putString("locale_tag", newTag)
                    .apply()
                LocaleCache.localeTag = newTag
                selectedTag = tag
                showLanguageDialog = false
                if (previousTag != tag) {
                    pendingLanguageRestart = true
                }
            },
            onDismiss = { showLanguageDialog = false },
            width = 400.dp,
            maxHeight = 280.dp
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
