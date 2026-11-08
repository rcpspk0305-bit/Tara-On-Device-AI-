package dev.voice.offline.ui.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue

/**
 * TARA design system tokens and programmatic UI builders.
 * Matches the futuristic cyber-neon on-device controller aesthetic:
 * Electric Cyan (#00E5FF) and Neon Orange (#FF6B00) on Obsidian Black (#080A0F).
 */
object TaraTheme {

    // Background & Deep Obsidian Surfaces
    val Background = Color.parseColor("#080A0F")
    val Surface = Color.parseColor("#0E121B")
    val CardSurface = Color.parseColor("#121622")
    val CardSurfaceElevated = Color.parseColor("#1A2030")
    val CardSurfaceGlass = Color.parseColor("#E6121622")
    val BorderStroke = Color.parseColor("#1E2638")
    val BorderSubtle = Color.parseColor("#161C2A")

    // Signature TARA Neon Accents
    val TaraCyan = Color.parseColor("#00E5FF")
    val TaraCyanDark = Color.parseColor("#0091EA")
    val TaraOrange = Color.parseColor("#FF6B00")
    val TaraOrangeLight = Color.parseColor("#FF8800")
    val TaraGreen = Color.parseColor("#00E676")
    val TaraPurple = Color.parseColor("#8B5CF6")
    val TaraRed = Color.parseColor("#FF3D71")

    // Extended Pre-parsed Shader & UI Constants (avoids GC allocations in onDraw)
    val CoreDiskDark = Color.parseColor("#182030")
    val CoreDiskMid = Color.parseColor("#0F1420")
    val CoreDiskBlack = Color.parseColor("#0A0D15")
    val AmbientGlowBase = Color.parseColor("#30121D2C")
    val SkyBlue = Color.parseColor("#38BDF8")
    val NeonAmber = Color.parseColor("#F59E0B")
    val NeonFlame = Color.parseColor("#FF5500")
    val BorderCore = Color.parseColor("#331E293B")
    val TextSilver = Color.parseColor("#E2E8F0")
    val IconSlate = Color.parseColor("#94A3B8")
    val DividerDark = Color.parseColor("#1C2333")
    val SwitchInactiveThumb = Color.parseColor("#475569")
    val SwitchInactiveTrack = Color.parseColor("#1E293B")
    val TabActivePill = Color.parseColor("#1C2538")

    // Typography & Content
    val TextPrimary = Color.parseColor("#FFFFFF")
    val TextSecondary = Color.parseColor("#94A3B8")
    val TextTertiary = Color.parseColor("#64748B")

    // Chips & Pills
    val ChipBackground = Color.parseColor("#141824")
    val ChipBorder = Color.parseColor("#222B3D")

    fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun createCardDrawable(
        context: Context,
        cornerRadiusDp: Float = 18f,
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

    fun createTaraGradientDrawable(
        context: Context,
        cornerRadiusDp: Float = 24f,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT
    ): GradientDrawable {
        return GradientDrawable(
            orientation,
            intArrayOf(TaraCyan, TaraOrange)
        ).apply {
            cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()
        }
    }

    fun createRippleDrawable(
        context: Context,
        contentDrawable: GradientDrawable,
        rippleColor: Int = Color.parseColor("#3300E5FF")
    ): RippleDrawable {
        val colorStateList = ColorStateList.valueOf(rippleColor)
        val maskDrawable = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = contentDrawable.cornerRadius
        }
        return RippleDrawable(colorStateList, contentDrawable, maskDrawable)
    }
}
