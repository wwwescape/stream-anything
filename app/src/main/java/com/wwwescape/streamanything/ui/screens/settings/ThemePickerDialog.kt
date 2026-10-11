package com.wwwescape.streamanything.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.settings.ColorTheme
import com.wwwescape.streamanything.ui.theme.Primary
import com.wwwescape.streamanything.ui.theme.generateColorScheme

/** A visual grid picker rather than [SettingsPickerDialog]'s text radio list — color choices
 * benefit from showing the actual color, not just a name. */
@Composable
fun ThemePickerDialog(selected: ColorTheme, onSelect: (ColorTheme) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_color_theme)) },
        text = {
            LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(320.dp)) {
                items(ColorTheme.entries) { theme ->
                    ThemeSwatch(
                        name = theme.label(),
                        color = theme.seedHue?.let { generateColorScheme(it, dark = false).primary } ?: Primary,
                        isSelected = selected == theme,
                        onClick = { onSelect(theme); onDismiss() },
                    )
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ThemeSwatch(name: String, color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.padding(6.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null, tint = Color.White)
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
