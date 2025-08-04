package uk.akane.cupertino.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.doOnLayout
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.AnimationUtils.interpolateColor
import kotlin.math.absoluteValue
import kotlin.math.pow

class OverlaySlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr),
    GestureDetector.OnGestureListener {

    private var gestureDetector = GestureDetector(context, this)

    private var transformValueAnimator: ValueAnimator? = null
    private var flingValueAnimator: ValueAnimator? = null
    private var transformFraction: Float = 0F

    var valueTo = 1F
    var valueFrom = 0F
    var value = 0.2F

    val defaultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    val outerPath = Path()
    val outerBoundRectF = RectF()

    private val trackOverlayColor = resources.getColor(R.color.secondaryOverlayColor, null)
    private val trackShadeColor = resources.getColor(R.color.secondaryOverlayShadeColor, null)

    private val progressOverlayColor = resources.getColor(R.color.primaryOverlayColor, null)
    private val progressShadeInitialColor = resources.getColor(R.color.primaryOverlayShadeColor, null)
    private val progressShadeFinalColor = resources.getColor(R.color.standardOverlayColor, null)
    private var progressCurrentColor = 0

    private var enableMomentum = true

    init {
        gestureDetector.setIsLongpressEnabled(false)

        doOnLayout {
            currentHeight = actualHeight
            currentSidePadding = actualSidePadding
            updateTrackBound(actualHeight, currentSidePadding)

            progressCurrentColor = progressShadeInitialColor
        }

        context.obtainStyledAttributes(attrs, R.styleable.OverlaySlider).apply {
            enableMomentum = getBoolean(R.styleable.OverlaySlider_momentum, true)
            recycle()
        }
    }

    private val actualHeight: Float = 7F.dpToPx(context)
    private var currentHeight: Float = 0F

    private val actualSidePadding: Float = 16F.dpToPx(context)
    private var currentSidePadding: Float = 0F

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(outerPath)
        drawProgress(canvas, defaultPaint)
        drawOuterBound(canvas, defaultPaint)
    }

    private var calculatedLeft = 0F
    private var calculatedTop = 0F
    private var calculatedRight = 0F
    private var calculatedBottom = 0F
    private var calculatedProgress = 0F

    private fun drawProgress(canvas: Canvas, paint: Paint) {
        calculatedProgress = calculatedRight - calculatedLeft

        // First layer
        paint.blendMode = BlendMode.OVERLAY
        paint.color = progressOverlayColor
        canvas.drawRect(
            calculatedLeft,
            calculatedTop,
            calculatedLeft + calculatedProgress * value,
            calculatedBottom,
            paint
        )

        // Second layer
        paint.blendMode = null
        paint.color = progressCurrentColor
        canvas.drawRect(
            calculatedLeft,
            calculatedTop,
            calculatedLeft + calculatedProgress * value,
            calculatedBottom,
            paint
        )
    }

    private fun drawOuterBound(canvas: Canvas, paint: Paint) {
        // First layer
        paint.blendMode = BlendMode.OVERLAY
        paint.color = trackOverlayColor
        canvas.drawRect(
            calculatedLeft + calculatedProgress * value,
            calculatedTop,
            calculatedRight,
            calculatedBottom,
            paint
        )

        // Second layer
        paint.blendMode = null
        paint.color = trackShadeColor
        canvas.drawRect(
            calculatedLeft + calculatedProgress * value,
            calculatedTop,
            calculatedRight,
            calculatedBottom,
            paint
        )
    }

    private fun updateTrackBound(currHeight: Float, currSidePadding: Float) {
        calculatedLeft = currSidePadding
        calculatedTop = (height - currHeight) / 2
        calculatedRight = width - currSidePadding
        calculatedBottom = height - (height - currHeight) / 2

        outerBoundRectF.setEmpty()
        outerBoundRectF.set(
            calculatedLeft,
            calculatedTop,
            calculatedRight,
            calculatedBottom
        )
        outerPath.reset()
        outerPath.addRoundRect(outerBoundRectF, currHeight / 2, currHeight / 2, Path.Direction.CW)
    }

    override fun onDown(e: MotionEvent): Boolean {
        transformSize(false)
        return true
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        transformSize(true)
        return true
    }

    private fun onUp(event: MotionEvent) {
        Log.d("TAG", "onUp: $event")

        transformSize(true)
    }

    private fun cancelTransform() {
        transformValueAnimator?.cancel()
        transformValueAnimator = null
    }

    private fun transformSize(isShrink: Boolean = false) {
        cancelTransform()

        ValueAnimator.ofFloat(
            transformFraction,
            if (isShrink) 0F else 1F
        ).apply {

            transformValueAnimator = this
            this.interpolator = AnimationUtils.easingInterpolator
            duration = TRANSFORM_DURATION

            addUpdateListener {
                transformFraction = animatedValue as Float
                currentHeight = actualHeight * (1F + (HEIGHT_RESIZE_FACTOR - 1F) * transformFraction)
                currentSidePadding = (width - (width - 2 * actualSidePadding) * (1F + (WIDTH_RESIZE_FACTOR - 1F) * transformFraction)) / 2
                progressCurrentColor = interpolateColor(progressShadeInitialColor, progressShadeFinalColor, transformFraction)
                updateTrackBound(currentHeight, currentSidePadding)
                invalidate()
            }

            start()
        }
    }

    override fun onShowPress(e: MotionEvent) {
    }

    private var lastMotionX = 0F
    private var lastMotionTime = 0L
    private var penultimateMotionX = 0F
    private var penultimateMotionTime = 0L

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        flingValueAnimator?.cancel()
        flingValueAnimator = null
        val progressMoved = -distanceX / calculatedProgress
        value += progressMoved
        invalidate()
        penultimateMotionX = lastMotionX
        penultimateMotionTime = lastMotionTime
        lastMotionX = e2.x
        lastMotionTime = e2.eventTime
        return true
    }

    override fun onLongPress(e: MotionEvent) {}

    private var flingFraction = 0F

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {

        if (enableMomentum) {
            val lastVelocity =
                ((lastMotionX - penultimateMotionX) / (lastMotionTime - penultimateMotionTime)).coerceIn(
                    -10F,
                    10F
                )
            val distance = lastVelocity.pow(2) / 2 / FRICTION / calculatedProgress
            val flingStartValue = value
            flingValueAnimator?.cancel()
            flingValueAnimator = null

            ValueAnimator.ofFloat(
                0F,
                1.0F
            ).apply {
                flingValueAnimator = this
                this.interpolator = AnimationUtils.decelerateInterpolator
                duration = (lastVelocity / FRICTION).toLong().absoluteValue.coerceIn(100, 600)

                addUpdateListener {
                    flingFraction = animatedValue as Float
                    value =
                        (flingStartValue + flingFraction * distance * (if (lastVelocity < 0) -1 else 1)).coerceIn(
                            valueFrom,
                            valueTo
                        )
                    invalidate()
                }

                start()
            }
        }

        if (e2.action == MotionEvent.ACTION_UP) { onUp(e2) }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (gestureDetector.onTouchEvent(event)) {
            true
        } else if (event.action == MotionEvent.ACTION_UP) {
            onUp(event)
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    companion object {
        const val HEIGHT_RESIZE_FACTOR = 2.5F
        const val WIDTH_RESIZE_FACTOR = 1.05F
        const val TRANSFORM_DURATION = 250L
        const val FRICTION = 0.01F
    }

}