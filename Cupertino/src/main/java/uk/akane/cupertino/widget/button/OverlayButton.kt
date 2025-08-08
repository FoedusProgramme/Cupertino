package uk.akane.cupertino.widget.button

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Checkable
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.graphics.createBitmap
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.getOverlayLayerColor
import uk.akane.cupertino.widget.getShadeLayerColor
import uk.akane.cupertino.widget.utils.AnimationUtils

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

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null

    private var transformBitmap: Bitmap? = null
    private var transformCanvas: Canvas? = null

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    val transformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

        iconDrawable?.callback = this
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bitmap = createBitmap(iconSize, iconSize)
        bitmapCanvas = Canvas(bitmap!!)
        transformBitmap = createBitmap(iconSize, iconSize)
        transformCanvas = Canvas(transformBitmap!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bitmap?.recycle()
        bitmap = null
        transformBitmap?.recycle()
        transformBitmap = null
    }

    fun updateBitmap(drawable: Drawable) {
        transformCanvas!!.drawColor(0, PorterDuff.Mode.CLEAR)
        transformCanvas!!.drawBitmap(bitmap!!, 0F, 0F, null)
        bitmapCanvas!!.drawColor(0, PorterDuff.Mode.CLEAR)
        drawable.setBounds(0, 0, iconSize, iconSize)
        drawable.draw(bitmapCanvas!!)
    }

    private val overlayColorFilter = PorterDuffColorFilter(
        resources.getOverlayLayerColor(textViewLayer), PorterDuff.Mode.SRC_IN
    )
    private val shadeColorFilter = PorterDuffColorFilter(
        resources.getShadeLayerColor(textViewLayer), PorterDuff.Mode.SRC_IN
    )
    private val holdingColorFilter = PorterDuffColorFilter(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

    private var isTransform = false
    private var isHolding = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = if (iconSize > 0) iconSize else minOf(width, height)
        val left = (width - size) / 2
        val top = (height - size) / 2

        if (isTransform) {
            drawIcon(transformPaint, canvas, transformBitmap!!, left, top)
        }

        drawIcon(paint, canvas, bitmap!!, left, top)
    }

    private fun drawIcon(
        paint: Paint,
        canvas: Canvas,
        bitmap: Bitmap,
        left: Int,
        top: Int
    ) {
        paint.colorFilter = if (isHolding && !isChecked) holdingColorFilter else overlayColorFilter
        paint.blendMode = BlendMode.OVERLAY
        canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), paint)

        paint.colorFilter = shadeColorFilter
        paint.blendMode = if (isHolding && !isChecked) BlendMode.SOFT_LIGHT else null
        canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), paint)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        iconDrawable?.apply {
            state = drawableState
            isHolding = false
            updateBitmap(this)
        }
    }

    override fun setChecked(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
            refreshDrawableState()
            animateChecked()
        }
    }

    private var transformValueAnimator: ValueAnimator? = null

    private fun animateChecked() {
        transformValueAnimator?.cancel()
        transformValueAnimator = null
        isTransform = false

        ValueAnimator.ofFloat(
            0F,
            1F
        ).apply {
            transformValueAnimator = this
            interpolator = AnimationUtils.easingInterpolator
            duration = 200L

            doOnStart {
                isTransform = true
            }

            doOnEnd {
                isTransform = false
                transformValueAnimator = null
            }

            addUpdateListener {
                val animatedAlpha = animatedValue as Float
                transformPaint.alpha = (255 * (1F - animatedAlpha)).toInt()
                paint.alpha = (255 * animatedAlpha).toInt()
                Log.d("TAG", "tp: ${transformPaint.alpha}, p: ${paint.alpha}")
                invalidate()
            }

            start()
        }
    }

    override fun isChecked(): Boolean = isChecked

    override fun toggle() {
        Log.d(TAG, "toggle!")
        isChecked = !isChecked
        animateChecked()
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val state = super.onCreateDrawableState(extraSpace + 1)
        if (isChecked) mergeDrawableStates(state, CHECKED_STATE_SET)
        return state
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isHolding = true
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                isHolding = false
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val TAG = "OverlayButton"
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }
}