package uk.akane.cupertino.widget.button

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
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
import uk.akane.cupertino.widget.spToPx
import uk.akane.cupertino.widget.utils.AnimationUtils
import uk.akane.cupertino.widget.utils.AnimationUtils.MID_DURATION
import androidx.core.view.isVisible

class OverlayTextPillButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShrinkableView(context, attrs, defStyleAttr), Checkable {

    private var isChecked = false

    private var iconSize: Int = 20.dpToPx(context)
    private var cornerRadius: Float = 12.dpToPx(context).toFloat()
    private var text: String = ""
    private var iconPadding: Int = 8.dpToPx(context)

    private var drawableBitmap: Bitmap? = null
    private var iconDrawable: Drawable? = null
    private var drawableCanvas: Canvas? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f.dpToPx(context).toFloat()
        color = ColorUtils.setAlphaComponent(Color.WHITE, 40)
    }
    private val contentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 17.spToPx(context).toFloat()
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        color = Color.parseColor("#0B0B0F")
    }
    
    private val overlayCompositePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = BlendMode.OVERLAY
    }

    private var transformFactor: Float = 0F

    private val rect = RectF()
    private val textBounds = Rect()
    private val clipPath = android.graphics.Path()

    private val backgroundLocation = IntArray(2)
    private val selfLocation = IntArray(2)
    private val backgroundRenderNode = RenderNode("OverlayTextPillButtonBlur").apply {
        setRenderEffect(
            RenderEffect.createBlurEffect(
                70f, 70f,
                Shader.TileMode.MIRROR
            )
        )
    }

    var backgroundView: View? = null
        set(value) {
            field = value
            refreshWithDelay()
        }

    private fun refreshWithDelay() {
        invalidate()
        // High-frequency refresh during the first 150ms to ensure seamless sync with transitions
        val startTime = System.currentTimeMillis()
        val refreshRunnable = object : Runnable {
            override fun run() {
                invalidate()
                if (System.currentTimeMillis() - startTime < 150) {
                    postDelayed(this, 16) // ~60fps refresh during entrance
                }
            }
        }
        post(refreshRunnable)
    }

    private val onScrollChangedListener = android.view.ViewTreeObserver.OnScrollChangedListener {
        invalidate()
    }

    private val onPreDrawListener = android.view.ViewTreeObserver.OnPreDrawListener {
        if (renderAlpha > 0f && visibility == VISIBLE && backgroundView != null) {
            invalidate()
        }
        true
    }

    private val checkedOverlayColor = resources.getOverlayLayerColor(0)
    private val checkedShadeColor = resources.getShadeLayerColor(0)
    private var renderAlpha = 1F

    init {
        isClickable = true
        setOnClickListener { toggle() }
        setOnTouchListener(null)

        context.obtainStyledAttributes(attrs, R.styleable.OverlayTextPillButton).apply {
            iconDrawable = getDrawable(R.styleable.OverlayTextPillButton_icon)
            iconSize = getDimensionPixelSize(R.styleable.OverlayTextPillButton_iconSize, iconSize)
            cornerRadius = getDimension(R.styleable.OverlayTextPillButton_cornerRadius, cornerRadius)
            text = getString(R.styleable.OverlayTextPillButton_android_text) ?: ""
            recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        drawableBitmap = createBitmap(iconSize, iconSize)
        drawableCanvas = Canvas(drawableBitmap!!)
        iconDrawable?.let { updateBitmap(it) }
        viewTreeObserver.addOnScrollChangedListener(onScrollChangedListener)
        viewTreeObserver.addOnPreDrawListener(onPreDrawListener)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        drawableBitmap?.recycle()
        drawableBitmap = null
        drawableCanvas = null
        viewTreeObserver.removeOnScrollChangedListener(onScrollChangedListener)
        viewTreeObserver.removeOnPreDrawListener(onPreDrawListener)
    }

    private fun updateBitmap(drawable: Drawable): Bitmap? {
        val c = drawableCanvas ?: return null
        c.drawColor(0, PorterDuff.Mode.CLEAR)
        drawable.setTint(Color.parseColor("#0B0B0F")) // Use black/dark color
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

    fun setText(value: String) {
        text = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        
        // 1. Draw actual blurred content from background
        drawBlurredBackground(canvas)
        
        // 2. Draw frosted overlays
        drawBackground(canvas, bgPaint, transformFactor)
        
        // 3. Draw content (Icon/Text)
        drawContent(canvas, transformFactor)
    }

    private fun drawBlurredBackground(canvas: Canvas) {
        val bg = backgroundView ?: return
        if (width == 0 || height == 0 || bg.width == 0 || bg.height == 0) return
        
        backgroundRenderNode.setPosition(0, 0, width, height)
        val recordingCanvas = backgroundRenderNode.beginRecording(width, height)
        
        bg.getLocationInWindow(backgroundLocation)
        getLocationInWindow(selfLocation)
        val offsetX = backgroundLocation[0] - selfLocation[0]
        val offsetY = backgroundLocation[1] - selfLocation[1]
        
        recordingCanvas.save()
        recordingCanvas.translate(offsetX.toFloat(), offsetY.toFloat())
        bg.draw(recordingCanvas)
        recordingCanvas.restore()
        backgroundRenderNode.endRecording()
        
        clipPath.reset()
        clipPath.addRoundRect(rect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRenderNode(backgroundRenderNode)
        canvas.restore()
    }

    private fun drawBackground(canvas: Canvas, paint: Paint, factor: Float) {
        if (renderAlpha <= 0F) return
        
        // 1. Milky base layer - slightly toned down
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, (130 * renderAlpha).toInt())
        paint.blendMode = null
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // 2. Glass overlay layer - slightly toned down
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, (60 * renderAlpha).toInt())
        paint.blendMode = BlendMode.SCREEN
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // 3. Sharp edge stroke
        strokePaint.alpha = (30 * renderAlpha).toInt()
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

        // 4. Subtle shade layer (depth)
        val shadeAlpha = ((8 + (12 * (1F - factor))) * renderAlpha).toInt()
        paint.color = ColorUtils.setAlphaComponent(
            Color.BLACK,
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

        // Use accent color for selected state overlay
        val overlayAlpha = (200 * factor * renderAlpha).toInt().coerceIn(0, 255)
        val cutoutAlpha = (255 * factor * renderAlpha).toInt().coerceIn(0, 255)
        
        bgPaint.color = ColorUtils.setAlphaComponent(checkedOverlayColor, overlayAlpha)
        bgPaint.blendMode = null
        val overlayLayerId = canvas.saveLayer(rect, overlayCompositePaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        
        drawContentCutout(canvas, cutoutAlpha)
        canvas.restoreToCount(overlayLayerId)
    }

    private fun drawContent(canvas: Canvas, factor: Float) {
        val inactiveFactor = 1F - factor
        if (inactiveFactor <= 0F) return

        val totalContentWidth = if (iconDrawable != null && text.isNotEmpty()) {
            iconSize + iconPadding + textPaint.measureText(text)
        } else if (iconDrawable != null) {
            iconSize.toFloat()
        } else {
            textPaint.measureText(text)
        }

        val startX = (width - totalContentWidth) / 2f
        val cy = height / 2f

        // Draw Icon (Pure White)
        if (iconDrawable != null && drawableBitmap != null) {
            val iconLeft = startX
            val iconTop = cy - iconSize / 2f
            val dst = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)

            contentPaint.colorFilter = null
            contentPaint.blendMode = null
            contentPaint.alpha = (255 * renderAlpha * inactiveFactor).toInt()
            canvas.drawBitmap(drawableBitmap!!, null, dst, contentPaint)
        }

        // Draw Text (Pure White)
        if (text.isNotEmpty()) {
            val textX = if (iconDrawable != null) startX + iconSize + iconPadding else startX
            val fm = textPaint.fontMetrics
            val textY = cy - (fm.ascent + fm.descent) / 2f

            textPaint.alpha = (255 * renderAlpha * inactiveFactor).toInt()
            canvas.drawText(text, textX, textY, textPaint)
        }
    }

    private fun drawContentCutout(canvas: Canvas, alpha: Int) {
        val totalContentWidth = if (iconDrawable != null && text.isNotEmpty()) {
            iconSize + iconPadding + textPaint.measureText(text)
        } else if (iconDrawable != null) {
            iconSize.toFloat()
        } else {
            textPaint.measureText(text)
        }

        val startX = (width - totalContentWidth) / 2f
        val cy = height / 2f

        if (iconDrawable != null && drawableBitmap != null) {
            val iconLeft = startX
            val iconTop = cy - iconSize / 2f
            val dst = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            
            contentPaint.colorFilter = null
            contentPaint.blendMode = BlendMode.DST_OUT
            contentPaint.alpha = alpha
            canvas.drawBitmap(drawableBitmap!!, null, dst, contentPaint)
            contentPaint.blendMode = null
        }

        if (text.isNotEmpty()) {
            val textX = if (iconDrawable != null) startX + iconSize + iconPadding else startX
            val fm = textPaint.fontMetrics
            val textY = cy - (fm.ascent + fm.descent) / 2f

            textPaint.colorFilter = null
            textPaint.blendMode = BlendMode.DST_OUT
            textPaint.alpha = alpha
            canvas.drawText(text, textX, textY, textPaint)
            textPaint.blendMode = null
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
        fun onCheckedChanged(button: OverlayTextPillButton, isChecked: Boolean)
    }

    private var onCheckedChangeListener: OnCheckedChangeListener? = null

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener) {
        onCheckedChangeListener = listener
    }
}
