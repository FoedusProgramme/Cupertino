package uk.akane.cupertino.widget.loading

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import androidx.core.graphics.ColorUtils
import uk.akane.cupertino.R
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.graphics.withRotation
import androidx.core.view.isVisible

class CupertinoActivityIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.progressBarStyle
) : ProgressBar(context, attrs, defStyleAttr) {

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val segmentRect = RectF()

    private val defaultSizePx = 30f * resources.displayMetrics.density
    private val fallbackColor = resources.getColor(R.color.onSurfaceColorSolid, context.theme)

    private var animatedStep = 0f
    private var animator: ValueAnimator? = null

    init {
        isIndeterminate = true
        indeterminateDrawable = null
        progressDrawable = null
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimatorState()
    }

    override fun onDetachedFromWindow() {
        stopAnimator()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimatorState()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimatorState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = defaultSizePx.roundToInt()
        val measuredWidth = resolveSize(desiredSize, widthMeasureSpec)
        val measuredHeight = resolveSize(desiredSize, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        val side = min(contentWidth, contentHeight).toFloat()
        val left = paddingLeft + (contentWidth - side) / 2f
        val top = paddingTop + (contentHeight - side) / 2f
        val centerX = left + side / 2f
        val centerY = top + side / 2f

        val segmentWidth = side * (1f - INNER_RADIUS) / 2f
        val segmentHeight = side / PATH_COUNT.toFloat()
        val cornerRadius = min(segmentWidth, segmentHeight) / 2f
        val angleStep = 360f / PATH_COUNT
        val trailCount = (PATH_COUNT / 2).coerceAtLeast(1)
        val indicatorColor = indeterminateTintList?.defaultColor ?: fallbackColor

        segmentRect.set(
            centerX + side / 2f - segmentWidth,
            centerY - segmentHeight / 2f,
            centerX + side / 2f,
            centerY + segmentHeight / 2f
        )

        val baseAlpha = (MIN_ALPHA * 255).roundToInt().coerceIn(0, 255)
        segmentPaint.color = ColorUtils.setAlphaComponent(indicatorColor, baseAlpha)
        for (index in 0 until PATH_COUNT) {
            canvas.withRotation(index * angleStep, centerX, centerY) {
                drawRoundRect(segmentRect, cornerRadius, cornerRadius, segmentPaint)
            }
        }

        val headIndex = animatedStep.toInt() % PATH_COUNT
        for (offset in 0..trailCount) {
            val alpha = (offset / trailCount.toFloat()).coerceIn(0f, 1f)
            segmentPaint.color = ColorUtils.setAlphaComponent(
                indicatorColor,
                (alpha * 255).roundToInt().coerceIn(0, 255)
            )
            canvas.withRotation((headIndex + offset) * angleStep, centerX, centerY) {
                drawRoundRect(segmentRect, cornerRadius, cornerRadius, segmentPaint)
            }
        }
    }

    private fun updateAnimatorState() {
        if (isInEditMode) return
        if (isAttachedToWindow && windowVisibility == VISIBLE && isVisible && alpha > 0f) {
            startAnimator()
        } else {
            stopAnimator()
        }
    }

    private fun startAnimator() {
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, PATH_COUNT.toFloat()).apply {
                duration = DURATION_MILLIS
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener {
                    animatedStep = it.animatedValue as Float
                    invalidate()
                }
            }
        }
        if (animator?.isStarted != true) {
            animator?.start()
        }
    }

    private fun stopAnimator() {
        animator?.cancel()
        animatedStep = 0f
        invalidate()
    }

    companion object {
        private const val PATH_COUNT = 8
        private const val DURATION_MILLIS = 1000L
        private const val MIN_ALPHA = 0.1f
        private const val INNER_RADIUS = 1f / 3f
    }
}
