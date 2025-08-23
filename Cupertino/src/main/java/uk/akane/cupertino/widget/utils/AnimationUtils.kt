package uk.akane.cupertino.widget.utils

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.view.Choreographer
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import androidx.core.animation.doOnEnd
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator

object AnimationUtils {

    val easingStandardInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    val decelerateInterpolator: Interpolator = DecelerateInterpolator(1.7f)
    val accelerateInterpolator: Interpolator = AccelerateInterpolator(1.7f)
    val linearOutSlowInInterpolator = LinearOutSlowInInterpolator()
    val fastOutSlowInInterpolator = FastOutSlowInInterpolator()
    val accelerateDecelerateInterpolator = AccelerateDecelerateInterpolator()
    val linearInterpolator = LinearInterpolator()

    const val FASTEST_DURATION = 150L
    const val FAST_DURATION = 256L
    const val MID_DURATION = 350L
    const val LONG_DURATION = 500L

    val addXfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    val overlayXfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)

    inline fun <reified T> createValAnimator(
        fromValue: T,
        toValue: T,
        duration: Long = FAST_DURATION,
        interpolator: TimeInterpolator = easingStandardInterpolator,
        isArgb: Boolean = false,
        startInstant: Boolean = true,
        crossinline doOnEnd: (() -> Unit) = {},
        crossinline changedListener: (animatedValue: T) -> Unit,
    ) : ValueAnimator {
        return when (T::class) {
            Int::class -> {
                if (!isArgb)
                    ValueAnimator.ofInt(fromValue as Int, toValue as Int)
                else
                    ValueAnimator.ofArgb(fromValue as Int, toValue as Int)
            }
            Float::class -> {
                ValueAnimator.ofFloat(fromValue as Float, toValue as Float)
            }
            else -> throw IllegalArgumentException("No valid animator type found!")
        }.apply {
            this.duration = duration
            this.interpolator = interpolator
            this.addUpdateListener {
                changedListener(this.animatedValue as T)
            }
            this.doOnEnd {
                doOnEnd()
            }
            if (startInstant) {
                start()
            }
        }
    }

    open class LinearAnimator<T>(
        initialValue: T,
        targetValue: T,
        override var startDelay: Long = 0L,
        var duration: Long = 300L,
        var interpolator: TimeInterpolator? = null,
        private val listener: Animator.ValueUpdateListener<T>,
        private val lerp: (from: T, to: T, fraction: Float) -> T
    ) : Animator<T> {
        final override var initialValue: T = initialValue
            private set
        final override var targetValue: T = targetValue
            private set
        final override var currentValue: T = initialValue
            private set
        final override val currentVelocity: Float
            get() = throw UnsupportedOperationException("LinearAnimator does not support velocity")

        final override var isRunning = false
            private set

        private var frameCallback: Choreographer.FrameCallback? = null
        private val choreographer = Choreographer.getInstance()
        private var currentCallbackIncrement = 0

        override fun start() {
            currentCallbackIncrement++
            val currentCallback = currentCallbackIncrement

            val durationScale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ValueAnimator.getDurationScale()
            } else {
                1f
            }

            cancel()
            isRunning = true
            var startTime = 0L

            val initialValue = initialValue
            val targetValue = targetValue
            val interpolator = interpolator
            val delay = (startDelay * 1_000_000L * durationScale).toLong()
            val duration = (duration * 1_000_000L * durationScale).toLong()

            frameCallback = Choreographer.FrameCallback { time ->
                if (currentCallback != currentCallbackIncrement) {
                    return@FrameCallback
                }

                if (startTime == 0L) {
                    startTime = time
                }

                val playTime = time - startTime
                if (playTime < delay) {
                    choreographer.postFrameCallback(frameCallback)
                    return@FrameCallback
                }

                val fraction = (playTime - delay).toFloat() / duration
                if (fraction >= 1f) {
                    end()
                } else {
                    val interpolatedFraction = interpolator?.getInterpolation(fraction) ?: fraction
                    currentValue = lerp(initialValue, targetValue, interpolatedFraction)
                    listener.onValueUpdate(this)
                    choreographer.postFrameCallback(frameCallback)
                }
            }

            choreographer.postFrameCallback(frameCallback)
        }

        override fun animateTo(targetValue: T) {
            initialValue = currentValue
            this.targetValue = targetValue
            start()
        }

        override fun snapTo(targetValue: T) {
            currentCallbackIncrement++
            cancel()
            currentValue = targetValue
            this.targetValue = targetValue
            listener.onValueUpdate(this)
        }

        override fun cancel() {
            isRunning = false
            if (frameCallback != null) {
                choreographer.removeFrameCallback(frameCallback)
            }
        }

        override fun end() {
            isRunning = false
            if (frameCallback != null) {
                choreographer.removeFrameCallback(frameCallback)
            }
            currentValue = targetValue
            listener.onValueUpdate(this)
        }
    }

    fun Animation.doOnEnd(action: () -> Unit): Animation {
        setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                action()
            }
            override fun onAnimationRepeat(animation: Animation) {}
        })
        return this
    }

    fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val clampedFraction = fraction.coerceIn(0f, 1f)

        val startA = (startColor shr 24) and 0xff
        val startR = (startColor shr 16) and 0xff
        val startG = (startColor shr 8) and 0xff
        val startB = startColor and 0xff

        val endA = (endColor shr 24) and 0xff
        val endR = (endColor shr 16) and 0xff
        val endG = (endColor shr 8) and 0xff
        val endB = endColor and 0xff

        val a = (startA + ((endA - startA) * clampedFraction)).toInt()
        val r = (startR + ((endR - startR) * clampedFraction)).toInt()
        val g = (startG + ((endG - startG) * clampedFraction)).toInt()
        val b = (startB + ((endB - startB) * clampedFraction)).toInt()

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    interface Animator<T> {
        val initialValue: T

        val targetValue: T

        val currentValue: T

        val currentVelocity: Float

        var startDelay: Long

        val isRunning: Boolean

        fun start()

        fun animateTo(targetValue: T)

        fun snapTo(targetValue: T)

        fun cancel()

        fun end()

        fun interface ValueUpdateListener<T> {
            fun onValueUpdate(animator: Animator<T>)
        }
    }
}