package uk.akane.cupertino.widget.button

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.widget.Checkable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.base.ShrinkableView
import uk.akane.cupertino.widget.dpToPx
import uk.akane.cupertino.widget.getOverlayLayerColor
import uk.akane.cupertino.widget.getShadeLayerColor
import uk.akane.cupertino.widget.utils.AnimationUtils

class OverlayBackgroundButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShrinkableView(context, attrs, defStyleAttr), Checkable {

    private var isChecked = false
    private var iconSize: Int = 28.dpToPx(context)
    private var drawableBitmap: Bitmap? = null
    private var iconDrawable: Drawable? = null
    private var drawableCanvas: Canvas? = null

    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val drawablePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val overlayColorFilter =
        PorterDuffColorFilter(resources.getOverlayLayerColor(0), PorterDuff.Mode.SRC_IN)
    private val shadeColorFilter =
        PorterDuffColorFilter(resources.getOverlayLayerColor(3), PorterDuff.Mode.SRC_IN)

    init {
        isClickable = true
        setOnClickListener { toggle() }

        context.obtainStyledAttributes(attrs, R.styleable.OverlayBackgroundButton).apply {
            iconDrawable = getDrawable(R.styleable.OverlayButton_icon)
            iconSize = getDimensionPixelSize(R.styleable.OverlayButton_iconSize, 0)
            recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        drawableBitmap = createBitmap(iconSize, iconSize)
        drawableCanvas = Canvas(drawableBitmap!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        drawableBitmap?.recycle()
        drawableBitmap = null
    }

    fun updateBitmap(drawable: Drawable): Bitmap? {
        drawableCanvas!!.drawColor(0, PorterDuff.Mode.CLEAR)
        drawable.setBounds(0, 0, iconSize, iconSize)
        drawable.draw(drawableCanvas!!)
        return drawableBitmap
    }

    private var transformFactor: Float = 0F

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas, backgroundPaint, transformFactor)
        drawDrawable(canvas, drawablePaint, transformFactor)
    }

    private fun drawBackground(canvas: Canvas, paint: Paint, factor: Float) {
        paint.color = resources.getOverlayLayerColor(2)
        paint.blendMode = BlendMode.OVERLAY
        canvas.drawCircle(width / 2F, height / 2F, iconSize / 2F, paint)

        paint.color = ColorUtils.setAlphaComponent(
            resources.getShadeLayerColor(2),
            (4 + 20 * (1F - factor)).toInt()
        )
        paint.blendMode = null
        canvas.drawCircle(width / 2F, height / 2F, iconSize / 2F, paint)
    }

    private fun drawDrawable(canvas: Canvas, paint: Paint, factor: Float) {
        val bitmap = drawableBitmap ?: return

        val centerX = width / 2f
        val centerY = height / 2f

        val drawWidth = bitmap.width
        val drawHeight = bitmap.height

        val left = centerX - drawWidth / 2f
        val top = centerY - drawHeight / 2f

        paint.colorFilter = overlayColorFilter
        paint.blendMode = BlendMode.OVERLAY
        paint.alpha = (255 * ((1F - factor) * 0.25F + 0.75F)).toInt()
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            paint
        )

        paint.colorFilter = shadeColorFilter
        paint.blendMode = null
        paint.alpha = (255 * ((1F - factor) * 0.55F + 0.45F)).toInt()
        Log.d(TAG, "alpha: ${paint.alpha}")
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            paint
        )
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        iconDrawable?.apply {
            state = drawableState
            updateBitmap(this)
        }
    }

    private var valueAnimator: ValueAnimator? = null

    private fun animateChecked(checked: Boolean) {
        valueAnimator?.cancel()
        valueAnimator = null

        ValueAnimator.ofFloat(
            if (checked) 0F else 1F,
            if (checked) 1F else 0F
        ).apply {
            valueAnimator = this
            duration = 300L
            interpolator = AnimationUtils.easingStandardInterpolator

            addUpdateListener {
                transformFactor = animatedValue as Float
                invalidate()
            }

            start()
        }
    }

    override fun setChecked(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
        }
    }

    override fun toggle() {
        isChecked = !isChecked
        animateChecked(isChecked)
    }

    override fun isChecked(): Boolean = isChecked

    private fun getDrawableColor(): Int =
        resources.getColor(
            R.color.white,
            null
        )

    companion object {
        private const val TAG = "OverlayBackgroundButton"
    }
}