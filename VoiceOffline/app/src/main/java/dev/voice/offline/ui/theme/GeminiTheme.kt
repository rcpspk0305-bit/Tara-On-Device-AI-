package dev.voice.offline.ui.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

/**
 * TARA-aligned theme tokens for backwards compatibility.
 * All views inherit the new cyber-neon TARA color scheme.
 */
object GeminiTheme {

    // Background & Deep Obsidian Surfaces
    val Background = TaraTheme.Background
    val Surface = TaraTheme.Surface
    val CardSurface = TaraTheme.CardSurface
    val CardSurfaceElevated = TaraTheme.CardSurfaceElevated
    val BorderStroke = TaraTheme.BorderStroke
    val BorderSubtle = TaraTheme.BorderSubtle

    // TARA Signature Accents
    val GeminiBlue = TaraTheme.TaraCyan
    val GeminiPurple = TaraTheme.TaraPurple
    val GeminiCoral = TaraTheme.TaraOrange
    val GeminiCyan = TaraTheme.TaraCyan
    val GeminiMint = TaraTheme.TaraGreen
    val GeminiAmber = TaraTheme.TaraOrangeLight
    val GeminiRed = TaraTheme.TaraRed

    // Typography & Content
    val TextPrimary = TaraTheme.TextPrimary
    val TextSecondary = TaraTheme.TextSecondary
    val TextTertiary = TaraTheme.TextTertiary

    // Pill Badges
    val ChipBackground = TaraTheme.ChipBackground
    val ChipBorder = TaraTheme.ChipBorder

    fun dpToPx(context: Context, dp: Float): Int = TaraTheme.dpToPx(context, dp)

    fun adjustAlpha(color: Int, factor: Float): Int = TaraTheme.adjustAlpha(color, factor)

    fun createCardDrawable(
        context: Context,
        cornerRadiusDp: Float = 18f,
        fillColor: Int = CardSurface,
        strokeColor: Int = BorderStroke,
        strokeWidthDp: Float = 1f
    ): GradientDrawable = TaraTheme.createCardDrawable(context, cornerRadiusDp, fillColor, strokeColor, strokeWidthDp)

    fun createPillDrawable(
        context: Context,
        fillColor: Int = ChipBackground,
        strokeColor: Int = ChipBorder,
        strokeWidthDp: Float = 1f
    ): GradientDrawable = TaraTheme.createPillDrawable(context, fillColor, strokeColor, strokeWidthDp)

    fun createGeminiGradientDrawable(
        context: Context,
        cornerRadiusDp: Float = 24f,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT
    ): GradientDrawable = TaraTheme.createTaraGradientDrawable(context, cornerRadiusDp, orientation)

    fun createRippleDrawable(
        context: Context,
        contentDrawable: GradientDrawable,
        rippleColor: Int = Color.parseColor("#3300E5FF")
    ): RippleDrawable = TaraTheme.createRippleDrawable(context, contentDrawable, rippleColor)
}
