package uk.akane.cupertino.widget.base

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.animation.doOnEnd
import uk.akane.cupertino.widget.utils.AnimationUtils

@SuppressLint("ClickableViewAccessibility")
open class ShrinkableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val shrinkValue = 0.9F
    val shrinkDuration = 600L
    val interpolator = AnimationUtils.decelerateInterpolator

    var currentScale = 1F
    var isDown = false

    private var valueAnimator: ValueAnimator? = null

    init {
        setOnTouchListener { _, event ->
            isDown = when (event.action) {
                MotionEvent.ACTION_DOWN -> true
                MotionEvent.ACTION_UP -> false
                else -> return@setOnTouchListener false
            }

            if (!isDown && valueAnimator?.isRunning == true) {
                return@setOnTouchListener false
            } else if (!isDown) {
                callGrowAnimation()
                return@setOnTouchListener false
            }

            callShrinkAnimation()
            return@setOnTouchListener false
        }
    }

    private fun callShrinkAnimation() {
        callAnimator(true) {
            callGrowAnimation()
        }
    }

    private fun callGrowAnimation() {
        callAnimator(false)
    }

    private fun callAnimator(isShrink: Boolean, onEnd: (() -> Unit)? = null) {
        valueAnimator?.cancel()
        valueAnimator = null

        ValueAnimator.ofFloat(
            currentScale,
            if (isShrink) shrinkValue else 1F
        ).apply {
            valueAnimator = this

            duration = shrinkDuration / 2
            interpolator = this@ShrinkableView.interpolator

            addUpdateListener {
                val scale = animatedValue as Float
                scaleX = scale
                scaleY = scale
                currentScale = scale
            }

            doOnEnd {
                if (!isDown) {
                    onEnd?.invoke()
                }
            }

            start()
        }
    }
}