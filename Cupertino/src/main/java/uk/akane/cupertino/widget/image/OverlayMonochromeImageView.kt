package uk.akane.cupertino.widget.image

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.withStyledAttributes
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.getOverlayLayerColor
import uk.akane.cupertino.widget.getShadeLayerColor
import uk.akane.cupertino.widget.utils.AnimationUtils

class OverlayMonochromeImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = BlendMode.OVERLAY
        isFilterBitmap = true
    }

    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    var viewLayer: Int = 0
        set(value) {
            field = value
            overlayPaint.color = resources.getOverlayLayerColor(value)
            shadePaint.color = resources.getShadeLayerColor(value)
            invalidate()
        }

    init {
        context.withStyledAttributes(attrs, R.styleable.OverlayMonochromeImageView, defStyleAttr, 0) {
            viewLayer = getInt(R.styleable.OverlayMonochromeImageView_imageViewLayer, 0)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val overlayLayer = canvas.saveLayer(null, overlayPaint)
        super.onDraw(canvas)
        canvas.restoreToCount(overlayLayer)

        val shadeLayer = canvas.saveLayer(null, shadePaint)
        super.onDraw(canvas)
        canvas.restoreToCount(shadeLayer)
    }
}
