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
import android.widget.Checkable
import androidx.core.graphics.createBitmap
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.base.ShrinkableView
import uk.akane.cupertino.widget.dpToPx

class OverlayBackgroundButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShrinkableView(context, attrs, defStyleAttr), Checkable {

    private var isChecked = false
    private var iconSize: Int = 28.dpToPx(context)
    private var drawableBitmap: Bitmap
    private var iconDrawable: Drawable? = null
    private var drawableCanvas: Canvas

    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val drawableColorFilter =
        PorterDuffColorFilter(getHollowStarColor(), PorterDuff.Mode.SRC_IN)
    private val drawablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = drawableColorFilter
    }

    init {
        isClickable = true
        setOnClickListener { toggle() }

        context.obtainStyledAttributes(attrs, R.styleable.OverlayBackgroundButton).apply {
            iconDrawable = getDrawable(R.styleable.OverlayButton_icon)
            iconSize = getDimensionPixelSize(R.styleable.OverlayButton_iconSize, 0)
            recycle()
        }

        drawableBitmap = createBitmap(iconSize, iconSize)
        drawableCanvas = Canvas(drawableBitmap)
    }

    fun updateBitmap(drawable: Drawable): Bitmap {
        drawableCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
        drawable.setBounds(0, 0, iconSize, iconSize)
        drawable.draw(drawableCanvas)
        return drawableBitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas, backgroundPaint)
        drawDrawable(canvas, drawablePaint)
    }

    private fun drawBackground(canvas: Canvas, paint: Paint) {
        paint.color = getBackgroundOverlayLayerColor()
        paint.blendMode = BlendMode.OVERLAY
        canvas.drawCircle(width / 2F, height / 2F, iconSize / 2F, paint)

        paint.color = getBackgroundShadeLayerColor()
        paint.blendMode = null
        canvas.drawCircle(width / 2F, height / 2F, iconSize / 2F, paint)
    }

    private fun drawDrawable(canvas: Canvas, paint: Paint) {
        val bitmap = drawableBitmap

        val centerX = width / 2f
        val centerY = height / 2f

        val drawWidth = bitmap.width
        val drawHeight = bitmap.height

        val left = centerX - drawWidth / 2f
        val top = centerY - drawHeight / 2f

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

    override fun setChecked(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
        }
    }

    override fun toggle() {
        isChecked = !isChecked
    }

    override fun isChecked(): Boolean = isChecked

    private fun getBackgroundOverlayLayerColor(): Int =
        resources.getColor(
            R.color.tertiaryOverlayColor,
            null
        )

    private fun getBackgroundShadeLayerColor(): Int =
        resources.getColor(
            R.color.tertiaryOverlayShadeColor,
            null
        )

    private fun getHollowStarColor(): Int =
        resources.getColor(
            R.color.white,
            null
        )
}