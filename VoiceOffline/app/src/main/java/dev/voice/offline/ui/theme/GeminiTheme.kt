package dev.voice.offline.ui.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue

/**
 * Google Gemini design system tokens and programmatic UI builders.
 */
object GeminiTheme {

    // Background & Surfaces
    val Background = Color.parseColor("#0B0C10")
    val Surface = Color.parseColor("#13151D")
    val CardSurface = Color.parseColor("#181A23")
    val CardSurfaceElevated = Color.parseColor("#212430")
    val BorderStroke = Color.parseColor("#2B2E3D")
    val BorderSubtle = Color.parseColor("#1F222E")

    // Gemini Signature Accents
    val GeminiBlue = Color.parseColor("#4285F4")
    val GeminiPurple = Color.parseColor("#9B72CF")
    val GeminiCoral = Color.parseColor("#D96570")
    val GeminiCyan = Color.parseColor("#29B6F6")
    val GeminiMint = Color.parseColor("#1EA362")
    val GeminiAmber = Color.parseColor("#F9AB00")
    val GeminiRed = Color.parseColor("#EA4335")

    // Typography & Content
    val TextPrimary = Color.parseColor("#F8F9FA")
    val TextSecondary = Color.parseColor("#9AA0A6")
    val TextTertiary = Color.parseColor("#5F6368")

    // Pill Badges
    val ChipBackground = Color.parseColor("#1E202B")
    val ChipBorder = Color.parseColor("#2F3344")

    fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    fun createCardDrawable(
        context: Context,
        cornerRadiusDp: Float = 20f,
        fillColor: Int = CardSurface,
        strokeColor: Int = BorderStroke,
        strokeWidthDp: Float = 1f
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()
            if (strokeWidthDp > 0f) {
                setStroke(dpToPx(context, strokeWidthDp), strokeColor)
            }
        }
    }

    fun createPillDrawable(
        context: Context,
        fillColor: Int = ChipBackground,
        strokeColor: Int = ChipBorder,
        strokeWidthDp: Float = 1f
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dpToPx(context, 100f).toFloat()
            if (strokeWidthDp > 0f) {
                setStroke(dpToPx(context, strokeWidthDp), strokeColor)
            }
        }
    }

    fun createGeminiGradientDrawable(
        context: Context,
        cornerRadiusDp: Float = 24f,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT
    ): GradientDrawable {
        return GradientDrawable(
            orientation,
            intArrayOf(GeminiBlue, GeminiPurple, GeminiCoral)
        ).apply {
            cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()
        }
    }

    fun createRippleDrawable(
        context: Context,
        contentDrawable: GradientDrawable,
        rippleColor: Int = Color.parseColor("#33FFFFFF")
    ): RippleDrawable {
        val colorStateList = ColorStateList.valueOf(rippleColor)
        val maskDrawable = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = contentDrawable.cornerRadius
        }
        return RippleDrawable(colorStateList, contentDrawable, maskDrawable)
    }
}
