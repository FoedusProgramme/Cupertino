package uk.akane.cupertino.widget

import android.animation.TimeInterpolator
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.navigation.SwitcherPostponeFragment

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

@Suppress("NOTHING_TO_INLINE")
inline fun lerp(start: Float, stop: Float, amount: Float): Float {
    return start + (stop - start) * amount
}

fun Bitmap?.areBitmapsVaguelySame(b1: Bitmap?): Boolean {
    return b1 != null && this != null && b1.width == this.width && b1.height == this.height && b1.getColor(0, 0) == this.getColor(0, 0)
}


fun FragmentTransaction.runOnContentLoaded(
    fragment: Fragment,
    action: () -> Unit
): FragmentTransaction {
    return if (fragment is SwitcherPostponeFragment && fragment.isPostponed) {
        fragment.addOnContentLoadedListener(action)
        this
    } else {
        this.runOnCommit {
            action()
        }
    }
}

/** Returns the alpha component of a color in ARGB format.  */
fun Int.alphaFromArgb(): Int = (this shr 24) and 255

fun View.fadOutAnimation(
    duration: Long = 300,
    visibility: Int = View.INVISIBLE,
    interpolator: TimeInterpolator,
    completion: (() -> Unit)? = null
) {
    animate()
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(interpolator)
        .withEndAction {
            this.visibility = visibility
            completion?.let {
                it()
            }
        }
}

fun View.fadInAnimation(
    duration: Long = 300,
    completion: (() -> Unit)? = null,
    interpolator: TimeInterpolator
) {
    alpha = 0f
    visibility = View.VISIBLE
    animate()
        .alpha(1f)
        .setDuration(duration)
        .setInterpolator(interpolator)
        .withEndAction {
            completion?.let {
                it()
            }
        }
}