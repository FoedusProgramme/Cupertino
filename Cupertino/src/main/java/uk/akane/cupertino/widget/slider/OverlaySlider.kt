package uk.akane.cupertino.widget.slider

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
import uk.akane.cupertino.widget.dpToPx
import uk.akane.cupertino.widget.lerp
import uk.akane.cupertino.widget.utils.AnimationUtils
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
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

    var valueTo = 100F
    var valueFrom = 0F
    var value = 20F

    private val normalizedValue
        get() = ((value - valueFrom) / (valueTo - valueFrom)).coerceIn(0F, 1F)

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
    private var enableOverShoot = false

    init {
        @Suppress("UsePropertyAccessSyntax")
        gestureDetector.setIsLongpressEnabled(false)

        doOnLayout {
            currentHeight = actualHeight
            currentSidePadding = actualSidePadding
            updateTrackBound(actualHeight, currentSidePadding)

            progressCurrentColor = progressShadeInitialColor
            pivotY = height / 2F
        }

        context.obtainStyledAttributes(attrs, R.styleable.OverlaySlider).apply {
            enableMomentum = getBoolean(R.styleable.OverlaySlider_momentum, true)
            enableOverShoot = getBoolean(R.styleable.OverlaySlider_overshoot, false)
            heightResizeFactor = getFloat(R.styleable.OverlaySlider_resizeFactor, HEIGHT_RESIZE_FACTOR_DEFAULT)
            recycle()
        }
    }

    private val actualHeight: Float = 7.5F.dpToPx(context)
    private var currentHeight: Float = 0F

    private val actualSidePadding: Float = 16F.dpToPx(context)
    private var currentSidePadding: Float = 0F
    private val sideOverShootFingerBound: Float = 100F.dpToPx(context)
    private val sideOverShootTransition: Float = 8F.dpToPx(context)

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
            calculatedLeft + calculatedProgress * normalizedValue,
            calculatedBottom,
            paint
        )

        // Second layer
        paint.blendMode = null
        paint.color = progressCurrentColor
        canvas.drawRect(
            calculatedLeft,
            calculatedTop,
            calculatedLeft + calculatedProgress * normalizedValue,
            calculatedBottom,
            paint
        )
    }

    private fun drawOuterBound(canvas: Canvas, paint: Paint) {
        // First layer
        paint.blendMode = BlendMode.OVERLAY
        paint.color = trackOverlayColor
        canvas.drawRect(
            calculatedLeft + calculatedProgress * normalizedValue,
            calculatedTop,
            calculatedRight,
            calculatedBottom,
            paint
        )

        // Second layer
        paint.blendMode = null
        paint.color = trackShadeColor
        canvas.drawRect(
            calculatedLeft + calculatedProgress * normalizedValue,
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
        Log.d(TAG, "onUp: $event")
        triggeredOvershootXLeft = 0F
        triggeredOvershootXRight = 0F
        transformSize(true)
    }

    private fun cancelTransform() {
        transformValueAnimator?.cancel()
        transformValueAnimator = null
        shouldLockValue = false
    }

    private fun transformSize(isShrink: Boolean = false) {
        cancelTransform()

        ValueAnimator.ofFloat(
            transformFraction,
            if (isShrink) 0F else 1F
        ).apply {
            transformValueAnimator = this
            interpolator = AnimationUtils.easingInterpolator
            duration = TRANSFORM_DURATION
            val startScaleX = scaleX
            val startScaleY = scaleY
            val startTranslationX = translationX

            addUpdateListener {
                if (shouldLockValue) return@addUpdateListener
                transformFraction = animatedValue as Float
                currentHeight = actualHeight * (1F + (heightResizeFactor - 1F) * transformFraction)
                currentSidePadding = (width - (width - 2 * actualSidePadding) * (1F + (WIDTH_RESIZE_FACTOR - 1F) * transformFraction)) / 2
                progressCurrentColor = AnimationUtils.interpolateColor(
                    progressShadeInitialColor,
                    progressShadeFinalColor,
                    transformFraction
                )

                scaleX = lerp(
                    startScaleX,
                    1F,
                    if (isShrink) 1f - transformFraction else transformFraction
                )
                scaleY = lerp(
                    startScaleY,
                    1F,
                    if (isShrink) 1f - transformFraction else transformFraction
                )
                translationX = lerp(
                    startTranslationX,
                    0F,
                    if (isShrink) 1f - transformFraction else transformFraction
                )

                updateTrackBound(currentHeight, currentSidePadding)

                updateListeners(1)
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

    private var triggeredOvershootXLeft = 0F
    private var triggeredOvershootXRight = 0F
    private var triggerOvershootTransitionMark = 0
    private var shouldLockValue = false

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        resetAnimator()

        val progressMoved = (-distanceX / calculatedProgress) * (valueTo - valueFrom)

        penultimateMotionX = lastMotionX
        penultimateMotionTime = lastMotionTime
        lastMotionX = e2.x
        lastMotionTime = e2.eventTime

        if (enableOverShoot) {
            calculateOverShoot(progressMoved)
        } else {
            calculateNormalValue(progressMoved)
        }

        return true
    }

    private fun calculateOverShoot(progressMoved: Float) {
        if (value + progressMoved <= valueFrom || lastMotionX < triggeredOvershootXLeft) {
            triggerOvershootTransitionMark = 1

            if (value != valueFrom) {
                value = valueFrom
                invalidate()
            }

            triggeredOvershootXLeft = if (triggeredOvershootXLeft == 0F) {
                lastMotionX
            } else if (lastMotionX >= triggeredOvershootXLeft) {
                0F
            } else {
                max(triggeredOvershootXLeft, lastMotionX)
            }

            pivotX = width - actualSidePadding
            val squeezeFraction = ((triggeredOvershootXLeft - lastMotionX) / sideOverShootFingerBound).coerceIn(0F, 1F)
            scaleX = lerp(1F, WIDTH_SCALE_FACTOR, squeezeFraction)
            scaleY = lerp(1F, HEIGHT_SCALE_FACTOR, squeezeFraction)
            translationX = lerp(0F, -sideOverShootTransition, squeezeFraction)

            updateListeners(3)

        } else if (value + progressMoved >= valueTo || (lastMotionX > triggeredOvershootXRight && triggeredOvershootXRight != 0F)) {
            triggerOvershootTransitionMark = 2

            if (value != valueTo) {
                value = valueTo
                invalidate()
            }

            triggeredOvershootXRight = if (triggeredOvershootXRight == 0F) {
                lastMotionX
            } else if (lastMotionX <= triggeredOvershootXRight) {
                0F
            } else {
                min(triggeredOvershootXRight, lastMotionX)
            }

            pivotX = actualSidePadding
            val squeezeFraction = ((lastMotionX - triggeredOvershootXRight) / sideOverShootFingerBound).coerceIn(0F, 1F)
            scaleX = lerp(1F, WIDTH_SCALE_FACTOR, squeezeFraction)
            scaleY = lerp(1F, HEIGHT_SCALE_FACTOR, squeezeFraction)
            translationX = lerp(0F, sideOverShootTransition, squeezeFraction)

            updateListeners(2)

        } else if (triggeredOvershootXLeft == 0F && triggeredOvershootXRight == 0F) {
            calculateNormalValue(progressMoved)
        } else {
            triggeredOvershootXLeft = 0F
            triggeredOvershootXRight = 0F
        }
    }

    private fun calculateNormalValue(progressMoved: Float) {
        triggerOvershootTransitionMark = 0
        value = (value + progressMoved).coerceIn(valueFrom, valueTo)
        invalidate()
    }

    fun updateListeners(triggerPlace: Int) {
        emphasizeListenerList.forEach {

            val emphasizeTransition = (actualSidePadding - currentSidePadding)
            val scaleTransition = (scaleX - 1f) * (calculatedProgress + currentSidePadding)
            val horizontalTransition = translationX * scaleX

            Log.d(TAG, "horizontalTransition: ${translationX * scaleX}, triggerPlace: $triggerPlace")

            it.onEmphasizeProgressLeft(
                if (triggeredOvershootXLeft == 0F && triggeredOvershootXRight == 0F && triggerOvershootTransitionMark == 0) {
                    // When transitioning back to original position (without previous transition, triggered on return).
                    emphasizeTransition
                } else if (triggeredOvershootXLeft == 0F && triggeredOvershootXRight == 0F && triggerOvershootTransitionMark == 1) {
                    // When transitioning back to original position (with previous transition, triggered on return).
                    emphasizeTransition + scaleTransition - horizontalTransition
                } else if (triggeredOvershootXLeft == 0F && triggeredOvershootXRight == 0F && triggerOvershootTransitionMark == 2) {
                    // Other side transition (triggered on return).
                    emphasizeTransition - horizontalTransition
                } else if (triggeredOvershootXLeft != 0F) {
                    // Left icon transition (triggered during transition).
                    emphasizeTransition + scaleTransition - horizontalTransition
                } else {
                    // Opposite side transition (triggered during transition)
                    emphasizeTransition - horizontalTransition
                }
            )
            it.onEmphasizeProgressRight(
                if (triggeredOvershootXLeft == 0F && triggeredOvershootXRight == 0F && triggerOvershootTransitionMark == 0) {
                    // When transitioning back to original position (without previous transition, triggered on return).
                    emphasizeTransition
                } else if (triggeredOvershootXLeft == 0F && triggeredOvershootXRight == 0F && triggerOvershootTransitionMark == 2) {
                    // When transitioning back to original position (with previous transition, triggered on return).
                    emphasizeTransition + scaleTransition + horizontalTransition
                } else if (triggeredOvershootXLeft == 0F && triggeredOvershootXRight == 0F && triggerOvershootTransitionMark == 1) {
                    // Other side transition (triggered on return).
                    emphasizeTransition + horizontalTransition
                } else if (triggeredOvershootXRight != 0F) {
                    // Right icon transition (triggered during transition).
                    emphasizeTransition + scaleTransition + horizontalTransition
                } else {
                    // Opposite side transition (triggered during transition)
                    emphasizeTransition + horizontalTransition
                }
            )
            it.onEmphasizeAll(transformFraction)
        }
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
            val distance = (lastVelocity.pow(2) / 2 / FRICTION / calculatedProgress) * (valueTo - valueFrom)
            val flingStartValue = value
            resetAnimator()

            ValueAnimator.ofFloat(
                0F,
                1.0F
            ).apply {
                flingValueAnimator = this
                interpolator = AnimationUtils.decelerateInterpolator
                duration = (lastVelocity / FRICTION).toLong().absoluteValue.coerceIn(200, 600)

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

        if (e2.action == MotionEvent.ACTION_UP) {
            onUp(e2)
        }
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

    interface EmphasizeListener {
        fun onEmphasizeProgressLeft(translationX: Float)
        fun onEmphasizeProgressRight(translationX: Float)
        fun onEmphasizeAll(fraction: Float)
    }

    private val emphasizeListenerList: MutableList<EmphasizeListener> = mutableListOf()

    override fun onDetachedFromWindow() {
        emphasizeListenerList.clear()
        super.onDetachedFromWindow()
    }

    fun addEmphasizeListener(listener: EmphasizeListener) {
        emphasizeListenerList.add(listener)
    }

    private fun resetAnimator() {
        flingValueAnimator?.cancel()
        flingValueAnimator = null
        shouldLockValue = true
    }

    private var heightResizeFactor: Float = 2.25F

    companion object {
        const val TAG = "OverlaySlider"

        const val HEIGHT_RESIZE_FACTOR_DEFAULT = 2.25F
        const val HEIGHT_SCALE_FACTOR = 0.8F
        const val WIDTH_RESIZE_FACTOR = 1.05F
        const val WIDTH_SCALE_FACTOR = 1.025F
        const val TRANSFORM_DURATION = 250L
        const val FRICTION = 0.01F
    }

}