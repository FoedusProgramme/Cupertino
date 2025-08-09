package uk.akane.cupertino.widget.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import uk.akane.cupertino.R
import androidx.core.content.withStyledAttributes
import uk.akane.cupertino.widget.areBitmapsVaguelySame
import uk.akane.cupertino.widget.continuousRoundRect

class SimpleImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private var imageBitmap: Bitmap? = null
    private var cornerRadius: Int = 0

    private val bezierPath = Path()
    private val dstRect = RectF()

    init {
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.SimpleImageView, defStyleAttr, 0) {
                val drawableResId = getResourceId(R.styleable.SimpleImageView_drawable, 0)
                cornerRadius = getDimensionPixelSize(R.styleable.SimpleImageView_cornerRadius, 0)
                if (drawableResId != 0) {
                    val drawable = AppCompatResources.getDrawable(context, drawableResId)
                    drawable?.let { d ->
                        imageBitmap = drawableToBitmap(d)
                    }
                    calculateDstRect()
                }
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it }
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDstRect()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        imageBitmap?.recycle()
        imageBitmap = null
    }

    fun setImageBitmap(bitmap: Bitmap?) {
        if (imageBitmap.areBitmapsVaguelySame(bitmap)) {
            imageBitmap?.recycle()
            imageBitmap = bitmap
            calculateDstRect()
            invalidate()
        }
    }

    private fun calculateDstRect() {
        val bmp = imageBitmap ?: return

        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).toFloat()

        if (availableWidth <= 0 || availableHeight <= 0) {
            dstRect.setEmpty()
            return
        }

        val bitmapWidth = bmp.width.toFloat()
        val bitmapHeight = bmp.height.toFloat()

        if (bitmapWidth > bitmapHeight) {
            val scaledHeight = availableWidth * (bitmapHeight / bitmapWidth)
            val top = (availableHeight - scaledHeight) / 2f + paddingTop
            dstRect.set(paddingLeft.toFloat(), top, paddingLeft + availableWidth, top + scaledHeight)
        } else {
            val scaledWidth = availableHeight * (bitmapWidth / bitmapHeight)
            val left = (availableWidth - scaledWidth) / 2f + paddingLeft
            dstRect.set(left, paddingTop.toFloat(), left + scaledWidth, paddingTop + availableHeight)
        }

        bezierPath.reset()
        bezierPath.continuousRoundRect(dstRect, cornerRadius.toFloat())
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(bezierPath)
        imageBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, dstRect, imagePaint)
        }
    }

}