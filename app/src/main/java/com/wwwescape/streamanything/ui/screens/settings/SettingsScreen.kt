package com.wwwescape.streamanything.ui.screens.settings

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.settings.AppSettings
import com.wwwescape.streamanything.data.settings.ColorTheme
import com.wwwescape.streamanything.data.settings.SeekIncrement
import com.wwwescape.streamanything.data.settings.ThemeContrast
import com.wwwescape.streamanything.data.settings.ThemeMode
import com.wwwescape.streamanything.data.settings.VideoScaling
import com.wwwescape.streamanything.util.openUrl

private val SettingsRowHeight = 72.dp

private const val PRIVACY_POLICY_URL = "https://www.ericppereira.co.in/apps/stream-anything/privacy-policy.html"

@Composable
fun SettingsScreen(
    onNavigateToExperimental: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScreenContent(
        settings = settings,
        onThemeModeSelected = viewModel::setThemeMode,
        onDynamicColorChanged = viewModel::setDynamicColor,
        onColorThemeSelected = viewModel::setColorTheme,
        onThemeContrastSelected = viewModel::setThemeContrast,
        onPureDarkChanged = viewModel::setPureDark,
        onAbsoluteDarkChanged = viewModel::setAbsoluteDark,
        onSeekIncrementSelected = viewModel::setSeekIncrement,
        onVideoScalingSelected = viewModel::setVideoScaling,
        onResumePlaybackChanged = viewModel::setResumePlayback,
        onClearWatchHistory = viewModel::clearWatchHistory,
        onNavigateToExperimental = onNavigateToExperimental,
        onNavigateToLicenses = onNavigateToLicenses,
        modifier = modifier,
    )
}

@Composable
private fun SettingsScreenContent(
    settings: AppSettings,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onColorThemeSelected: (ColorTheme) -> Unit,
    onThemeContrastSelected: (ThemeContrast) -> Unit,
    onPureDarkChanged: (Boolean) -> Unit,
    onAbsoluteDarkChanged: (Boolean) -> Unit,
    onSeekIncrementSelected: (SeekIncrement) -> Unit,
    onVideoScalingSelected: (VideoScaling) -> Unit,
    onResumePlaybackChanged: (Boolean) -> Unit,
    onClearWatchHistory: () -> Unit,
    onNavigateToExperimental: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }
    val isDarkTheme = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val currentLanguageTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { null }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showColorThemeDialog by remember { mutableStateOf(false) }
    var showContrastDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSeekDialog by remember { mutableStateOf(false) }
    var showScalingDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel(stringResource(R.string.section_appearance))
            SettingsGroupCard {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ToggleRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.setting_dynamic_color),
                        subtitle = stringResource(R.string.setting_dynamic_color_subtitle),
                        checked = settings.useDynamicColor,
                        onCheckedChange = onDynamicColorChanged,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                NavigationRow(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.setting_color_theme),
                    subtitle = settings.colorTheme.label(),
                    onClick = { showColorThemeDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                NavigationRow(
                    icon = Icons.Rounded.DarkMode,
                    title = stringResource(R.string.setting_theme),
                    subtitle = settings.themeMode.label(),
                    onClick = { showThemeDialog = true },
                )
                // Contrast/pure dark/absolute dark all only affect (or only make sense for) dark
                // theme — hidden entirely rather than shown-but-inert when the effective theme
                // (accounting for "System default" actually resolving to light) is light.
                if (isDarkTheme) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    NavigationRow(
                        icon = Icons.Rounded.Contrast,
                        title = stringResource(R.string.setting_theme_contrast),
                        subtitle = settings.themeContrast.label(),
                        onClick = { showContrastDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ToggleRow(
                        icon = Icons.Rounded.Brightness6,
                        title = stringResource(R.string.setting_pure_dark),
                        subtitle = stringResource(R.string.setting_pure_dark_subtitle),
                        checked = settings.pureDark,
                        onCheckedChange = onPureDarkChanged,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ToggleRow(
                        icon = Icons.Rounded.NightsStay,
                        title = stringResource(R.string.setting_absolute_dark),
                        subtitle = stringResource(R.string.setting_absolute_dark_subtitle),
                        checked = settings.absoluteDark,
                        onCheckedChange = onAbsoluteDarkChanged,
                    )
                }
            }

            SectionLabel(stringResource(R.string.section_playback))
            SettingsGroupCard {
                NavigationRow(
                    icon = Icons.Rounded.FastForward,
                    title = stringResource(R.string.setting_seek_increment),
                    subtitle = settings.playback.seekIncrement.label(),
                    onClick = { showSeekDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                NavigationRow(
                    icon = Icons.Rounded.AspectRatio,
                    title = stringResource(R.string.setting_video_scaling),
                    subtitle = settings.playback.scaling.label(),
                    onClick = { showScalingDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ToggleRow(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.setting_resume_playback),
                    subtitle = stringResource(R.string.setting_resume_playback_subtitle),
                    checked = settings.playback.resumePlayback,
                    onCheckedChange = onResumePlaybackChanged,
                )
                if (settings.playback.resumePlayback) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    NavigationRow(
                        icon = Icons.Rounded.DeleteSweep,
                        title = stringResource(R.string.setting_clear_watch_history),
                        subtitle = stringResource(R.string.setting_clear_watch_history_subtitle),
                        onClick = { showClearHistoryDialog = true },
                    )
                }
            }

            SectionLabel(stringResource(R.string.section_advanced))
            SettingsGroupCard {
                NavigationRow(
                    icon = Icons.Rounded.Science,
                    title = stringResource(R.string.setting_experimental),
                    subtitle = stringResource(R.string.setting_experimental_subtitle),
                    onClick = onNavigateToExperimental,
                )
            }

            SectionLabel(stringResource(R.string.section_general))
            SettingsGroupCard {
                NavigationRow(
                    icon = Icons.Rounded.Language,
                    title = stringResource(R.string.setting_language),
                    subtitle = appLanguageLabel(currentLanguageTag),
                    onClick = { showLanguageDialog = true },
                )
            }

            SectionLabel(stringResource(R.string.section_about))
            SettingsGroupCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_app_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.about_version, versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    text = stringResource(R.string.privacy_statement_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = stringResource(R.string.content_disclaimer_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                NavigationRow(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.section_privacy_policy),
                    subtitle = null,
                    trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { openUrl(context, PRIVACY_POLICY_URL) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                NavigationRow(
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.section_open_source_licenses),
                    subtitle = stringResource(R.string.settings_row_licenses_subtitle),
                    onClick = onNavigateToLicenses,
                )
            }
        }
    }

    if (showThemeDialog) {
        SettingsPickerDialog(
            title = stringResource(R.string.setting_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { it.label() },
            onSelect = onThemeModeSelected,
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showColorThemeDialog) {
        ThemePickerDialog(
            selected = settings.colorTheme,
            onSelect = onColorThemeSelected,
            onDismiss = { showColorThemeDialog = false },
        )
    }
    if (showContrastDialog) {
        SettingsPickerDialog(
            title = stringResource(R.string.setting_theme_contrast),
            options = ThemeContrast.entries,
            selected = settings.themeContrast,
            label = { it.label() },
            onSelect = onThemeContrastSelected,
            onDismiss = { showContrastDialog = false },
        )
    }
    if (showLanguageDialog) {
        SettingsPickerDialog(
            title = stringResource(R.string.setting_language),
            options = SUPPORTED_APP_LANGUAGES,
            selected = currentLanguageTag,
            label = { appLanguageLabel(it) },
            onSelect = { tag ->
                AppCompatDelegate.setApplicationLocales(
                    if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                )
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
    if (showSeekDialog) {
        SettingsPickerDialog(
            title = stringResource(R.string.setting_seek_increment),
            options = SeekIncrement.entries,
            selected = settings.playback.seekIncrement,
            label = { it.label() },
            onSelect = onSeekIncrementSelected,
            onDismiss = { showSeekDialog = false },
        )
    }
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_history_title)) },
            text = { Text(stringResource(R.string.dialog_clear_history_body)) },
            confirmButton = {
                TextButton(onClick = { onClearWatchHistory(); showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showScalingDialog) {
        SettingsPickerDialog(
            title = stringResource(R.string.setting_video_scaling),
            options = VideoScaling.entries,
            selected = settings.playback.scaling,
            label = { it.label() },
            onSelect = onVideoScalingSelected,
            onDismiss = { showScalingDialog = false },
        )
    }
}

@Composable
internal fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
internal fun RowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsRowHeight)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            RowIcon(icon)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
internal fun NavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailingIcon: ImageVector = Icons.Rounded.ChevronRight,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(SettingsRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
