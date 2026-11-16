package com.tvmate.shared.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tvmate.shared.R

/** Design tokens from design/b-tuner-dial-v2 ("Tuner Dial"). */
object TvMateColors {
    val Chassis = Color(0xFFD8D5CE)
    val Panel = Color(0xFFE8E5DF)
    val Face = Color(0xFFF4F2EE)
    val Ink = Color(0xFF1C1B19)
    val Ink2 = Color(0xFF46433D)
    val Mute = Color(0xFF6F6B63)
    val Line = Color(0xFFC6C2B9)
    val Orange = Color(0xFFD9500A)
    val OrangeDark = Color(0xFFA83C04)
    val Locked = Color(0xFF2F7D4B)
    val Fault = Color(0xFFB3321F)
    val Amber = Color(0xFFB7791F)
}

object TvMateFonts {
    /** Headings and channel numbers. */
    val Display = FontFamily(
        Font(R.font.barlow_condensed_medium, FontWeight.Medium),
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    )

    /** All interface text (variable font). */
    val Ui = FontFamily(
        listOf(400, 500, 600, 700).map { w ->
            Font(
                R.font.inter_variable,
                weight = FontWeight(w),
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(w)),
            )
        },
    )

    /** Data: IPs, bitrates, countries. */
    val Mono = FontFamily(
        Font(R.font.plex_mono_regular, FontWeight.Normal),
        Font(R.font.plex_mono_medium, FontWeight.Medium),
    )
}

private val TvMateTypography = Typography(
    displayLarge = TextStyle(fontFamily = TvMateFonts.Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 62.sp),
    headlineLarge = TextStyle(fontFamily = TvMateFonts.Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = TvMateFonts.Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = TvMateFonts.Ui, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = TvMateFonts.Ui, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = TvMateFonts.Ui, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = TvMateFonts.Ui, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = TvMateFonts.Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = TvMateFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

private val TvMateScheme = lightColorScheme(
    primary = TvMateColors.Orange,
    onPrimary = Color.White,
    secondary = TvMateColors.Ink,
    onSecondary = Color.White,
    background = TvMateColors.Chassis,
    onBackground = TvMateColors.Ink,
    surface = TvMateColors.Panel,
    onSurface = TvMateColors.Ink,
    surfaceVariant = TvMateColors.Face,
    onSurfaceVariant = TvMateColors.Mute,
    outline = TvMateColors.Line,
    error = TvMateColors.Fault,
)

@Composable
fun TvMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvMateScheme, typography = TvMateTypography, content = content)
}
