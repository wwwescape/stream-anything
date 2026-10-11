package com.wwwescape.streamanything.data.settings

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** [seedHue] is a 0-359 HSV hue driving [com.wwwescape.streamanything.ui.theme.generateColorScheme].
 * DEFAULT (null) keeps the app's own hand-authored static palette (see Color.kt) instead of a
 * generated one. Ignored entirely when dynamic color is on, which takes precedence. */
enum class ColorTheme(val seedHue: Float?) {
    DEFAULT(null),
    OCEAN(205f),
    FOREST(140f),
    SUNSET(25f),
    GRAPE(280f),
    ROSE(340f),
    SLATE(220f),
    GOLD(45f),
    TEAL(175f),
    PLUM(300f),
    MOSS(95f),
    CORAL(12f),
    INDIGO(245f),
    MUSTARD(55f),
    CRIMSON(355f),
    MINT(160f),
    PERIWINKLE(230f),
}

/** Only affects (or only makes sense for) dark theme. */
enum class ThemeContrast { STANDARD, MEDIUM, HIGH }

/** How far the player's rewind/forward controls (and D-pad left/right) jump. Limited to the
 * steps Material has matching replay/forward icons for. */
enum class SeekIncrement(val seconds: Int) {
    FIVE_SECONDS(5),
    TEN_SECONDS(10),
    THIRTY_SECONDS(30),
}

/** How the video is scaled into the screen when its aspect ratio doesn't match. The player's
 * own scaling button cycles through these too; this is just the one each video starts with. */
enum class VideoScaling { FIT, ZOOM, STRETCH }

data class PlaybackSettings(
    val seekIncrement: SeekIncrement = SeekIncrement.TEN_SECONDS,
    val scaling: VideoScaling = VideoScaling.FIT,
    /** Remember where each video stopped (see [com.wwwescape.streamanything.data.history.WatchHistory]). */
    val resumePlayback: Boolean = true,
)

/** Features still being shaped, behind Settings → Advanced → Experimental. */
data class ExperimentalSettings(
    /** List recently played links on the home screen. Links are only stored while this is on
     * (and only alongside Resume Playback, which is what records the history). */
    val showHistory: Boolean = false,
    /** Send links from a phone to Stream Anything on a Google TV over Wi-Fi (see
     * [com.wwwescape.streamanything.data.tvlink.TvLinkProtocol]). On a TV it makes the app
     * listen while it's open; on a phone it looks for TVs while Home is open. */
    val playOnTv: Boolean = false,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT,
    val themeContrast: ThemeContrast = ThemeContrast.STANDARD,
    val pureDark: Boolean = false,
    val absoluteDark: Boolean = false,
    val playback: PlaybackSettings = PlaybackSettings(),
    val experimental: ExperimentalSettings = ExperimentalSettings(),
)
