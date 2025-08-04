package uk.akane.cupertino.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Path
import android.graphics.RectF
import uk.akane.cupertino.R

@Suppress("NOTHING_TO_INLINE")
inline fun Int.dpToPx(context: Context): Int =
    (this.toFloat() * context.resources.displayMetrics.density).toInt()

@Suppress("NOTHING_TO_INLINE")
inline fun Float.dpToPx(context: Context): Float =
    (this * context.resources.displayMetrics.density)

private val factors = arrayOf(0f, 0f, 0.0460f, 0.1336f, 0.2207f, 0.3486f, 0.5116f, 0.6745f, 0.8362f, 1.2819f)

fun Path.continuousRoundRect(bounds: RectF, cornerRadius: Float) {
    continuousRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, cornerRadius)
}

fun Path.continuousRoundRect(left: Float, top: Float, right: Float, bottom: Float, cornerRadius: Float) {
    val radiusMax = (right - left).coerceAtMost(bottom - top) / factors.last() / 2
    val radius = cornerRadius.coerceAtMost(radiusMax)
    val offsets = factors.map { radius * it }

    moveTo(left + offsets[0], top + offsets[9])
    cubicTo(
        left + offsets[1], top + offsets[8],
        left + offsets[2], top + offsets[7],
        left + offsets[3], top + offsets[6]
    )
    cubicTo(
        left + offsets[4], top + offsets[5],
        left + offsets[5], top + offsets[4],
        left + offsets[6], top + offsets[3]
    )
    cubicTo(
        left + offsets[7], top + offsets[2],
        left + offsets[8], top + offsets[1],
        left + offsets[9], top + offsets[0]
    )
    lineTo(right - offsets[9], top + offsets[0])
    cubicTo(
        right - offsets[8], top + offsets[1],
        right - offsets[7], top + offsets[2],
        right - offsets[6], top + offsets[3]
    )
    cubicTo(
        right - offsets[5], top + offsets[4],
        right - offsets[4], top + offsets[5],
        right - offsets[3], top + offsets[6]
    )
    cubicTo(
        right - offsets[2], top + offsets[7],
        right - offsets[1], top + offsets[8],
        right - offsets[0], top + offsets[9]
    )
    lineTo(right - offsets[0], bottom - offsets[9])
    cubicTo(
        right - offsets[1], bottom - offsets[8],
        right - offsets[2], bottom - offsets[7],
        right - offsets[3], bottom - offsets[6]
    )
    cubicTo(
        right - offsets[4], bottom - offsets[5],
        right - offsets[5], bottom - offsets[4],
        right - offsets[6], bottom - offsets[3]
    )
    cubicTo(
        right - offsets[7], bottom - offsets[2],
        right - offsets[8], bottom - offsets[1],
        right - offsets[9], bottom - offsets[0]
    )
    lineTo(left + offsets[9], bottom - offsets[0])
    cubicTo(
        left + offsets[8], bottom - offsets[1],
        left + offsets[7], bottom - offsets[2],
        left + offsets[6], bottom - offsets[3]
    )
    cubicTo(
        left + offsets[5], bottom - offsets[4],
        left + offsets[4], bottom - offsets[5],
        left + offsets[3], bottom - offsets[6]
    )
    cubicTo(
        left + offsets[2], bottom - offsets[7],
        left + offsets[1], bottom - offsets[8],
        left + offsets[0], bottom - offsets[9]
    )
    lineTo(left + offsets[0], top + offsets[9])
}

fun Resources.getOverlayLayerColor(textViewLayer: Int): Int {
    return getColor(
        when (textViewLayer) {
            0 -> R.color.primaryOverlayColor
            1 -> R.color.secondaryOverlayColor
            2 -> R.color.tertiaryOverlayColor
            3 -> R.color.standardOverlayColor
            else -> throw IllegalArgumentException("Invalid textViewLayer value")
        },
        null
    )
}

fun Resources.getShadeLayerColor(textViewLayer: Int): Int {
    return getColor(
        when (textViewLayer) {
            0 -> R.color.primaryOverlayShadeColor
            1 -> R.color.secondaryOverlayShadeColor
            2 -> R.color.tertiaryOverlayShadeColor
            3 -> R.color.standardOverlayShadeColor
            else -> throw IllegalArgumentException("Invalid textViewLayer value")
        },
        null
    )
}