package io.embodit.moonphases.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Typography
import com.pico.spatial.ui.design.systemColorScheme
import io.embodit.moonphases.domain.AppLanguage
import java.util.Locale

/** Brand colours of the design package (3D content and the few accents the panels need). */
object MoonColors {
    val Interaction = Color(0xFF9CC8FF)
    val Passable = Color(0xFF8FE3B0)
    val Alert = Color(0xFFFFC56E)
    val Error = Color(0xFFFF8A7A)
    val SunCore = Color(0xFFFFE6B0)
    val SunGlow = Color(0xFFFFC56E)
    val Corona = Color(0xFFFFF4D6)
    val MoonLit = Color(0xFFEDE8DC)
    val EclipseCopper = Color(0xFF7A3A22)
    val FloorDisc = Color(0xFF0E1020)
    val NotchIdle = Color(0xFF3A4060)
    val NotchLearned = Color(0xFFF3E9C6)
    val NotchTarget = Color(0xFF9CC8FF)
    val Space = Color(0xFF05060B)

    // Buttons on the glass card (0.1.1): one tone per level, moon-night palette, text contrast >= 12:1
    // (danger 5.8:1). Primary = moonlight, secondary / icon keys / selected option = night blue,
    // danger (only "Start over") = muted brick, links = deep night ink (~3.6:1 on the grey glass).
    val ButtonPrimary = Color(0xFFEDE8DC)
    val OnButtonPrimary = Color(0xFF141B33)
    val ButtonSecondary = Color(0xFF1B2440)
    val OnButtonSecondary = Color(0xFFE4E7F0)
    val ButtonDanger = Color(0xFFA9463C)
    val OnButtonDanger = Color(0xFFFFF6F2)
    val LinkInk = Color(0xFF16224F)
}

/**
 * PicoTheme with the design's four accent roles over the system (Vibrant) scheme, and Noto Sans /
 * Noto Sans SC in place of the system family. The language is the one chosen in the app.
 */
@Composable
fun MoonTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val system = systemColorScheme(context)
    val colors =
        remember(system) {
            system.copy(
                interaction = MoonColors.Interaction,
                passable = MoonColors.Passable,
                alert = MoonColors.Alert,
                error = MoonColors.Error,
            )
        }
    PicoTheme(colorScheme = colors, content = content)
}

// ---------------------------------------------------------------------------------------------
// In-app language: resources and typeface resolved in the language picked in settings.
// ---------------------------------------------------------------------------------------------

val LocalAppResources = staticCompositionLocalOf<Resources> { error("AppLocale not provided") }
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ZH }

private fun AppLanguage.locale(): Locale =
    when (this) {
        AppLanguage.ZH -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.EN -> Locale.ENGLISH
    }

fun Context.localizedResources(language: AppLanguage): Resources {
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(language.locale())
    return createConfigurationContext(configuration).resources
}

/** Provides string resources and the matching Noto family in [language]. Call inside [MoonTheme]. */
@Composable
fun AppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val resources = remember(context, language) { context.localizedResources(language) }
    CompositionLocalProvider(LocalAppResources provides resources, LocalAppLanguage provides language) {
        val base = PicoTheme.typography
        val typography = rememberNotoTypography(base, language)
        PicoTheme(typography = typography, content = content)
    }
}

@Composable
@ReadOnlyComposable
fun str(@StringRes id: Int): String = LocalAppResources.current.getString(id)

@Composable
@ReadOnlyComposable
fun str(@StringRes id: Int, vararg args: Any): String = LocalAppResources.current.getString(id, *args)

@Composable
@ReadOnlyComposable
fun strArray(@ArrayRes id: Int): Array<String> = LocalAppResources.current.getStringArray(id)

private const val LATIN_FONT = "NotoSans-VF.ttf"
private const val CHINESE_FONT = "NotoSansSC-VF.ttf"
private val WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

private fun Context.notoFontFamily(language: AppLanguage): FontFamily? {
    val file = if (language == AppLanguage.ZH) CHINESE_FONT else LATIN_FONT
    val bundled = runCatching { assets.list("fonts")?.toSet() }.getOrNull().orEmpty()
    if (file !in bundled) return null
    return FontFamily(
        WEIGHTS.map { weight ->
            Font(
                path = "fonts/$file",
                assetManager = assets,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        }
    )
}

@Composable
private fun rememberNotoTypography(base: Typography, language: AppLanguage): Typography {
    val context = LocalContext.current
    return remember(context, base, language) {
        val family = context.notoFontFamily(language) ?: return@remember base
        fun TextStyle.inFamily(): TextStyle = copy(fontFamily = family)
        base.copy(
            displayLarge = base.displayLarge.inFamily(),
            displayMedium = base.displayMedium.inFamily(),
            displaySmall = base.displaySmall.inFamily(),
            headlineLarge = base.headlineLarge.inFamily(),
            headlineMedium = base.headlineMedium.inFamily(),
            headlineSmall = base.headlineSmall.inFamily(),
            titleLarge = base.titleLarge.inFamily(),
            titleMedium = base.titleMedium.inFamily(),
            titleSmall = base.titleSmall.inFamily(),
            bodyLarge = base.bodyLarge.inFamily(),
            bodyLargeMultiline = base.bodyLargeMultiline.inFamily(),
            bodyMedium = base.bodyMedium.inFamily(),
            bodyMediumMultiline = base.bodyMediumMultiline.inFamily(),
            bodySmall = base.bodySmall.inFamily(),
            bodyTiny = base.bodyTiny.inFamily(),
            labelLarge = base.labelLarge.inFamily(),
            labelMedium = base.labelMedium.inFamily(),
            labelSmall = base.labelSmall.inFamily(),
        )
    }
}
