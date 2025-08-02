package uk.akane.cupertino.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.Checkable
import androidx.core.graphics.createBitmap
import uk.akane.cupertino.R

class OverlayButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Checkable {

    var textViewLayer: Int = 0
        set(value) {
            field = value
            invalidate()
            requestLayout()
        }

    private var isChecked = false
    private var iconDrawable: Drawable? = null
    private var iconSize: Int = 0
    private val bitmap: Bitmap
    private val bitmapCanvas: Canvas

    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    init {
        isClickable = true
        setOnClickListener { toggle() }

        context.obtainStyledAttributes(attrs, R.styleable.OverlayButton).apply {
            iconDrawable = getDrawable(R.styleable.OverlayButton_icon)
            iconSize = getDimensionPixelSize(R.styleable.OverlayButton_iconSize, 0)
            textViewLayer = getInt(R.styleable.OverlayButton_viewLayer, 0)
            recycle()
        }

        bitmap = createBitmap(iconSize, iconSize)
        bitmapCanvas = Canvas(bitmap)

        iconDrawable?.callback = this
    }

    fun Drawable.toBitmap(): Bitmap {
        bitmapCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
        this.setBounds(0, 0, iconSize, iconSize)
        this.draw(bitmapCanvas)
        return bitmap
    }

    private val overlayColorFilter = PorterDuffColorFilter(getOverlayLayerColor(), PorterDuff.Mode.SRC_IN)
    private val shadeColorFilter = PorterDuffColorFilter(getShadeLayerColor(), PorterDuff.Mode.SRC_IN)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = if (iconSize > 0) iconSize else minOf(width, height)
        val left = (width - size) / 2
        val top = (height - size) / 2

        paint.colorFilter = overlayColorFilter
        paint.blendMode = BlendMode.OVERLAY
        canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), paint)

        paint.colorFilter = shadeColorFilter
        paint.blendMode = null
        canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), paint)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        iconDrawable?.state = drawableState
        iconDrawable!!.toBitmap()
        invalidate()
    }

    override fun setChecked(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
            refreshDrawableState()
        }
    }

    override fun isChecked(): Boolean = isChecked

    override fun toggle() {
        isChecked = !isChecked
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val state = super.onCreateDrawableState(extraSpace + 1)
        if (isChecked) mergeDrawableStates(state, CHECKED_STATE_SET)
        return state
    }

    private fun getOverlayLayerColor(): Int {
        return resources.getColor(
            when (textViewLayer) {
                0 -> R.color.primaryOverlayColor
                1 -> R.color.secondaryOverlayColor
                2 -> R.color.tertiaryOverlayColor
                3 -> R.color.standardOverlayColor
                else -> throw IllegalArgumentException("Invalid textViewLayer value")
            },
            null
        )
    }

    private fun getShadeLayerColor(): Int {
        return resources.getColor(
            when (textViewLayer) {
                0 -> R.color.primaryOverlayShadeColor
                1 -> R.color.secondaryOverlayShadeColor
                2 -> R.color.tertiaryOverlayShadeColor
                3 -> R.color.standardOverlayShadeColor
                else -> throw IllegalArgumentException("Invalid textViewLayer value")
            },
            null
        )
    }

    companion object {
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }
}