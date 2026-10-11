package com.wwwescape.streamanything.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.util.openUrl
import java.util.Calendar

private enum class LicenseType(val url: String) {
    APACHE("https://www.apache.org/licenses/LICENSE-2.0"),
}

private data class LibraryEntry(
    val name: String,
    val version: String,
    val license: LicenseType,
    val descriptionRes: Int? = null,
)

/** A real, hand-maintained list of this project's actual dependencies (from
 * gradle/libs.versions.toml) — not a stub. */
private val featuredLibraries = listOf(
    LibraryEntry("Jetpack Compose", "BOM 2026.06.01", LicenseType.APACHE, R.string.lib_desc_compose),
    LibraryEntry("AndroidX Media3", "1.11.1", LicenseType.APACHE, R.string.lib_desc_media3),
    LibraryEntry("OkHttp", "4.12.0", LicenseType.APACHE, R.string.lib_desc_okhttp),
)

private val supportingLibraries = listOf(
    LibraryEntry("Coil", "2.7.0", LicenseType.APACHE),
    LibraryEntry("AndroidX Core KTX", "1.10.1", LicenseType.APACHE),
    LibraryEntry("AndroidX AppCompat", "1.7.1", LicenseType.APACHE),
    LibraryEntry("AndroidX Activity Compose", "1.9.2", LicenseType.APACHE),
    LibraryEntry("AndroidX Navigation Compose", "2.8.0", LicenseType.APACHE),
    LibraryEntry("AndroidX Lifecycle", "2.8.6", LicenseType.APACHE),
    LibraryEntry("AndroidX DataStore Preferences", "1.1.1", LicenseType.APACHE),
    LibraryEntry("AndroidX Core SplashScreen", "1.0.1", LicenseType.APACHE),
)

@Composable
fun LicensesScreen(modifier: Modifier = Modifier) {
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IntroCard()

        featuredLibraries.forEach { library -> FeaturedLibraryCard(library) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            supportingLibraries.forEach { library -> CompactLibraryCard(library) }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Text(
            text = stringResource(R.string.licenses_footer_format, stringResource(R.string.app_name), year),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IntroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.section_licenses_intro_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.section_licenses_intro_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FeaturedLibraryCard(library: LibraryEntry) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, library.license.url) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                LicenseBadge(library.license)
            }
            Text(
                text = library.version,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (library.descriptionRes != null) {
                Text(
                    text = stringResource(library.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactLibraryCard(library: LibraryEntry) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, library.license.url) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(text = library.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.library_version_license_format, library.version, stringResource(library.license.labelRes())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LicenseBadge(license: LicenseType) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = stringResource(license.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun LicenseType.labelRes(): Int = when (this) {
    LicenseType.APACHE -> R.string.license_badge_apache
}
