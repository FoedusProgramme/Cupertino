package uk.akane.cupertino.widget.button

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.Checkable
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.base.ShrinkableView
import uk.akane.cupertino.widget.dpToPx
import uk.akane.cupertino.widget.getOverlayLayerColor
import uk.akane.cupertino.widget.getShadeLayerColor
import uk.akane.cupertino.widget.utils.AnimationUtils
import uk.akane.cupertino.widget.utils.AnimationUtils.MID_DURATION

class OverlayPillButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShrinkableView(context, attrs, defStyleAttr), Checkable {

    private var isChecked = false

    private var iconSize: Int = 26.dpToPx(context)
    private var cornerRadius: Float = 18.dpToPx(context).toFloat()

    private var drawableBitmap: Bitmap? = null
    private var iconDrawable: Drawable? = null
    private var drawableCanvas: Canvas? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayCompositePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = BlendMode.OVERLAY
    }

    private val overlayColorFilter =
        PorterDuffColorFilter(resources.getOverlayLayerColor(0), PorterDuff.Mode.SRC_IN)
    private val shadeColorFilter =
        PorterDuffColorFilter(resources.getOverlayLayerColor(3), PorterDuff.Mode.SRC_IN)

    // Used to animate "selected" state (like your reference)
    private var transformFactor: Float = 0F

    private val rect = RectF()

    private val checkedOverlayColor = resources.getOverlayLayerColor(0)
    private val checkedShadeColor = resources.getShadeLayerColor(0)
    private var renderAlpha = 1F

    init {
        isClickable = true
        setOnClickListener { toggle() }
        setOnTouchListener(null)

        context.obtainStyledAttributes(attrs, R.styleable.OverlayPillButton).apply {
            iconDrawable = getDrawable(R.styleable.OverlayPillButton_icon)
            iconSize = getDimensionPixelSize(R.styleable.OverlayPillButton_iconSize, iconSize)
            cornerRadius = getDimension(R.styleable.OverlayPillButton_cornerRadius, cornerRadius)
            recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        drawableBitmap = createBitmap(iconSize, iconSize)
        drawableCanvas = Canvas(drawableBitmap!!)
        iconDrawable?.let { updateBitmap(it) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        drawableBitmap?.recycle()
        drawableBitmap = null
        drawableCanvas = null
    }

    private fun updateBitmap(drawable: Drawable): Bitmap? {
        val c = drawableCanvas ?: return null
        c.drawColor(0, PorterDuff.Mode.CLEAR)
        drawable.setBounds(0, 0, iconSize, iconSize)
        drawable.draw(c)
        return drawableBitmap
    }

    fun setIcon(drawable: Drawable?) {
        iconDrawable = drawable
        drawable?.let { updateBitmap(it) }
        invalidate()
    }

    fun setIconResource(@DrawableRes resId: Int) {
        setIcon(ResourcesCompat.getDrawable(resources, resId, null))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        drawBackground(canvas, bgPaint, transformFactor)
        drawIcon(canvas, iconPaint, transformFactor)
    }

    private fun drawBackground(canvas: Canvas, paint: Paint, factor: Float) {
        if (renderAlpha <= 0F) return
        // Base overlay layer (glass)
        val baseOverlayColor = resources.getOverlayLayerColor(2)
        val baseOverlayAlpha = (Color.alpha(baseOverlayColor) * renderAlpha).toInt()
        paint.color = ColorUtils.setAlphaComponent(baseOverlayColor, baseOverlayAlpha)
        paint.blendMode = BlendMode.OVERLAY
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // Shade layer (depth).
        val shadeAlpha = ((6 + (18 * (1F - factor))) * renderAlpha).toInt()
        paint.color = ColorUtils.setAlphaComponent(
            resources.getShadeLayerColor(2),
            shadeAlpha
        )
        paint.blendMode = null
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        if (factor > 0F) {
            drawSelectedLayer(canvas, factor)
        }
    }

    private fun drawSelectedLayer(canvas: Canvas, factor: Float) {
        if (renderAlpha <= 0F) return

        val bitmap = drawableBitmap
        val dst = if (bitmap != null) {
            val cx = width / 2f
            val cy = height / 2f
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val left = cx - w / 2f
            val top = cy - h / 2f
            RectF(left, top, left + w, top + h)
        } else {
            null
        }

        // Overlay layer composited with OVERLAY blend, like the star background.
        val overlayAlpha =
            (Color.alpha(checkedOverlayColor) * factor * renderAlpha).toInt().coerceIn(0, 255)
        val cutoutAlpha = (255 * factor * renderAlpha).toInt().coerceIn(0, 255)
        if (overlayAlpha <= 0) return
        bgPaint.color = ColorUtils.setAlphaComponent(checkedOverlayColor, overlayAlpha)
        bgPaint.blendMode = null
        val overlayLayerId = canvas.saveLayer(rect, overlayCompositePaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        if (bitmap != null && dst != null) {
            iconPaint.colorFilter = null
            iconPaint.blendMode = BlendMode.DST_OUT
            iconPaint.alpha = cutoutAlpha
            canvas.drawBitmap(bitmap, null, dst, iconPaint)
            iconPaint.blendMode = null
        }
        canvas.restoreToCount(overlayLayerId)

        // Shade layer composited normally.
        val shadeAlpha =
            (Color.alpha(checkedShadeColor) * factor * renderAlpha).toInt().coerceIn(0, 255)
        bgPaint.color = ColorUtils.setAlphaComponent(checkedShadeColor, shadeAlpha)
        bgPaint.blendMode = null
        val shadeLayerId = canvas.saveLayer(rect, null)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        if (bitmap != null && dst != null) {
            iconPaint.colorFilter = null
            iconPaint.blendMode = BlendMode.DST_OUT
            iconPaint.alpha = cutoutAlpha
            canvas.drawBitmap(bitmap, null, dst, iconPaint)
            iconPaint.blendMode = null
        }
        canvas.restoreToCount(shadeLayerId)

        iconPaint.alpha = 255
    }

    private fun drawIcon(canvas: Canvas, paint: Paint, factor: Float) {
        val bitmap = drawableBitmap ?: return

        val cx = width / 2f
        val cy = height / 2f

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        val left = cx - w / 2f
        val top = cy - h / 2f
        val dst = RectF(left, top, left + w, top + h)

        val inactiveFactor = 1F - factor

        if (inactiveFactor > 0F) {
            paint.colorFilter = overlayColorFilter
            paint.blendMode = BlendMode.OVERLAY
            paint.alpha =
                (255 * ((inactiveFactor * 0.25F + 0.75F) * inactiveFactor) * renderAlpha).toInt()
            canvas.drawBitmap(bitmap, null, dst, paint)

            paint.colorFilter = shadeColorFilter
            paint.blendMode = null
            paint.alpha =
                (255 * ((inactiveFactor * 0.55F + 0.45F) * inactiveFactor) * renderAlpha).toInt()
            canvas.drawBitmap(bitmap, null, dst, paint)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        iconDrawable?.apply {
            state = drawableState
            updateBitmap(this)
        }
    }

    override fun setAlpha(alpha: Float) {
        renderAlpha = alpha.coerceIn(0F, 1F)
        super.setAlpha(1F)
        invalidate()
    }

    override fun getAlpha(): Float = renderAlpha

    private var valueAnimator: ValueAnimator? = null

    private fun animateChecked(checked: Boolean) {
        valueAnimator?.cancel()
        valueAnimator = ValueAnimator.ofFloat(
            if (checked) 0F else 1F,
            if (checked) 1F else 0F
        ).apply {
            duration = MID_DURATION
            interpolator = AnimationUtils.linearInterpolator
            addUpdateListener {
                transformFactor = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun setChecked(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
            animateChecked(isChecked)
        }
    }

    override fun toggle() {
        isChecked = !isChecked
        onCheckedChangeListener?.onCheckedChanged(this, isChecked)
        animateChecked(isChecked)
    }

    override fun isChecked(): Boolean = isChecked

    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(button: OverlayPillButton, isChecked: Boolean)
    }

    private var onCheckedChangeListener: OnCheckedChangeListener? = null

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener) {
        onCheckedChangeListener = listener
    }
}
