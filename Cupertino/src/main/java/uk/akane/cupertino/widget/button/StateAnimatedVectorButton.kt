package uk.akane.cupertino.widget.button

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.content.res.AppCompatResources
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.base.ShrinkableView
import uk.akane.cupertino.widget.getOverlayLayerColor
import uk.akane.cupertino.widget.getShadeLayerColor
import uk.akane.cupertino.widget.utils.AnimationUtils

class StateAnimatedVectorButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShrinkableView(context, attrs, defStyleAttr) {

    private var normalDrawable: Drawable? = null
    private var stateDrawable: Drawable? = null
    private var activeDrawable: Drawable? = null

    private var iconSize = 0

    private val overlayPaint = Paint().apply {
        xfermode = AnimationUtils.overlayXfermode
    }

    init {
        isClickable = true
        isFocusable = true

        context.obtainStyledAttributes(attrs, R.styleable.StateAnimatedVectorButton).apply {
            getDrawable(R.styleable.StateAnimatedVectorButton_icon)?.let { normalDrawable = it }
            getDrawable(R.styleable.StateAnimatedVectorButton_iconState)?.let { stateDrawable = it }
            iconSize = getDimensionPixelSize(R.styleable.StateAnimatedVectorButton_iconSize, 0)
            activeDrawable = normalDrawable
            invalidate()
            recycle()
        }
    }

    fun setDrawable(resId: Int) =
        AppCompatResources.getDrawable(context, resId)?.let {
            normalDrawable = it
            if (activeDrawable == null) activeDrawable = it
            requestLayout()
            invalidate()
        }

    fun setStateDrawable(resId: Int) =
        AppCompatResources.getDrawable(context, resId)?.let {
            stateDrawable = it
            requestLayout()
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawable = activeDrawable ?: return

        val overlayCount = canvas.saveLayer(null, overlayPaint)
        drawable.setTint(resources.getOverlayLayerColor(3))
        drawable.draw(canvas)
        canvas.restoreToCount(overlayCount)

        val shadeCount = canvas.saveLayer(null, null)
        drawable.setTint(resources.getShadeLayerColor(3))
        drawable.draw(canvas)
        canvas.restoreToCount(shadeCount)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        listOfNotNull(normalDrawable, stateDrawable).forEach { d ->
            val intrinsicWidth = d.intrinsicWidth
            val intrinsicHeight = d.intrinsicHeight

            if (iconSize <= 0) {
                d.setBounds(
                    (w / 2 - intrinsicWidth / 2),
                    (h / 2 - intrinsicHeight / 2),
                    (w / 2 + intrinsicWidth / 2),
                    (h / 2 + intrinsicHeight / 2)
                )
            } else {
                val scale: Float
                val width: Int
                val height: Int

                if (intrinsicWidth >= intrinsicHeight) {
                    scale = iconSize.toFloat() / intrinsicWidth.toFloat()
                    width = iconSize
                    height = (intrinsicHeight * scale).toInt()
                } else {
                    scale = iconSize.toFloat() / intrinsicHeight.toFloat()
                    height = iconSize
                    width = (intrinsicWidth * scale).toInt()
                }

                d.setBounds(
                    (w / 2 - width / 2),
                    (h / 2 - height / 2),
                    (w / 2 + width / 2),
                    (h / 2 + height / 2)
                )
            }
        }
    }

    private var isFirstTime = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            if (!isFirstTime) {
                activeDrawable = if (activeDrawable == normalDrawable) {
                    stateDrawable ?: normalDrawable
                } else {
                    normalDrawable
                }
            }
            isFirstTime = false
            invalidate()
            (activeDrawable as AnimatedVectorDrawable).start()
        }
        return super.onTouchEvent(event)
    }
}
