package uk.akane.cupertino.widget.image

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.getOverlayLayerColor
import uk.akane.cupertino.widget.lerp
import uk.akane.cupertino.widget.utils.AnimationUtils

class OverlayHintView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = AnimationUtils.overlayXfermode
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var iconDrawable: Drawable?
    private val iconSize: Int

    init {
        context.obtainStyledAttributes(attrs, R.styleable.OverlayHintView).apply {
            iconDrawable = getDrawable(R.styleable.OverlayHintView_icon)
            iconSize = getDimensionPixelSize(R.styleable.OverlayHintView_iconSize, 0)
            recycle()
        }
    }

    var transformValue: Float = 0F
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val overlayLayer = canvas.saveLayer(null, overlayPaint)
        iconDrawable?.apply {
            setTint(resources.getOverlayLayerColor(0))
            draw(canvas)
        }
        canvas.restoreToCount(overlayLayer)

        paint.alpha = lerp(114.75F, 216.75F, transformValue).toInt()
        val normalLayer = canvas.saveLayer(null, paint)
        iconDrawable?.apply {
            setTint(resources.getOverlayLayerColor(3))
            draw(canvas)
        }
        canvas.restoreToCount(normalLayer)

    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        iconDrawable?.let { d ->
            val (width, height) = calculateScaledSize()

            d.setBounds(
                (w - width) / 2,
                (h - height) / 2,
                (w + width) / 2,
                (h + height) / 2
            )
        }
    }

    fun playAnim() {
        (iconDrawable as AnimatedVectorDrawable).start()
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        val (desiredWidth, desiredHeight) = calculateScaledSize()

        val measuredWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desiredWidth, MeasureSpec.getSize(widthMeasureSpec))
            MeasureSpec.UNSPECIFIED -> desiredWidth
            else -> desiredWidth
        }

        val measuredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
            MeasureSpec.UNSPECIFIED -> desiredHeight
            else -> desiredHeight
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    private fun calculateScaledSize(): Pair<Int, Int> {
        val drawableWidth = iconDrawable?.intrinsicWidth ?: 0
        val drawableHeight = iconDrawable?.intrinsicHeight ?: 0

        if (drawableWidth <= 0 || drawableHeight <= 0) {
            return 0 to 0
        }

        if (iconSize > 0) {
            val scale: Float
            val width: Int
            val height: Int

            if (drawableWidth >= drawableHeight) {
                scale = iconSize.toFloat() / drawableHeight.toFloat()
                height = iconSize
                width = (drawableWidth * scale).toInt()
            } else {
                scale = iconSize.toFloat() / drawableWidth.toFloat()
                width = iconSize
                height = (drawableHeight * scale).toInt()
            }
            return width to height
        }

        return drawableWidth to drawableHeight
    }

}