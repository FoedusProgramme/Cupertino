package uk.akane.cupertino.widget.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.alpha
import androidx.core.graphics.createBitmap
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.getOverlayLayerColor
import uk.akane.cupertino.widget.getShadeLayerColor

class OverlayHintView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private val overlayColorFilter = PorterDuffColorFilter(
        resources.getOverlayLayerColor(0), PorterDuff.Mode.SRC_IN
    )
    private val shadeColorFilter = PorterDuffColorFilter(
        resources.getColor(R.color.white, null), PorterDuff.Mode.SRC_IN
    )

    private var iconDrawable: Drawable?
    private val iconSize: Int

    private var iconBitmap: Bitmap? = null
    private var iconCanvas: Canvas? = null

    private var lowestLuminous = 0

    init {
        context.obtainStyledAttributes(attrs, R.styleable.OverlayHintView).apply {
            iconDrawable = getDrawable(R.styleable.OverlayHintView_icon)
            iconSize = getDimensionPixelSize(R.styleable.OverlayHintView_iconSize, 0)
            recycle()
        }

        lowestLuminous = resources.getShadeLayerColor(0).alpha
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val bitmapWidth = if (iconSize == 0) iconDrawable?.intrinsicWidth ?: 0 else iconSize
        val bitmapHeight = if (iconSize == 0) iconDrawable?.intrinsicHeight ?: 0 else iconSize
        iconBitmap = createBitmap(bitmapWidth, bitmapHeight)
        iconCanvas = Canvas(iconBitmap!!)

        iconDrawable?.let { updateBitmap(it) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        iconBitmap?.recycle()
        iconBitmap = null
    }

    fun updateBitmap(drawable: Drawable) {
        iconCanvas!!.drawColor(0, PorterDuff.Mode.CLEAR)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(iconCanvas!!)
    }

    var transformValue: Float = 0F
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = iconBitmap ?: return

        val left = (width - bitmap.width) / 2f
        val top = (height - bitmap.height) / 2f

        paint.colorFilter = overlayColorFilter
        paint.blendMode = BlendMode.OVERLAY
        paint.alpha = 255
        canvas.drawBitmap(bitmap, left, top, paint)

        paint.colorFilter = shadeColorFilter
        paint.blendMode = null
        paint.alpha = (lowestLuminous + transformValue * (255 - lowestLuminous)).toInt()
        canvas.drawBitmap(bitmap, left, top, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        val drawableWidth = iconDrawable?.intrinsicWidth ?: 0
        val drawableHeight = iconDrawable?.intrinsicHeight ?: 0

        val desiredWidth = if (iconSize != 0) iconSize else drawableWidth
        val desiredHeight = if (iconSize != 0) iconSize else drawableHeight

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

}