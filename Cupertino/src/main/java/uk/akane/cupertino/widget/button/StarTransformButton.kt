package uk.akane.cupertino.widget.button

import android.animation.Animator
import android.animation.AnimatorSet
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
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.utils.AnimationUtils
import uk.akane.cupertino.widget.base.ShrinkableView
import uk.akane.cupertino.widget.dpToPx

class StarTransformButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShrinkableView(context, attrs, defStyleAttr), Checkable {

    private var isChecked = false
    private var iconSize: Int = 28.dpToPx(context)
    private var hollowStarBitmap: Bitmap? = null
    private var hollowStarDrawable: Drawable? = null
    private var hollowStarBitmapCanvas: Canvas? = null

    private var filledStarBitmap: Bitmap? = null
    private var filledStarDrawable: Drawable? = null
    private var filledStarBitmapCanvas: Canvas? = null

    private var checkedBackgroundBitmap: Bitmap? = null
    private var checkedBackgroundDrawable: Drawable? = null
    private var checkedBackgroundBitmapCanvas: Canvas? = null

    val backgroundPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
    val backgroundPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)

    private val hollowStarPaintColorFilter = PorterDuffColorFilter(
        getHollowStarColor(), PorterDuff.Mode.SRC_IN
    )
    private val hollowStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = hollowStarPaintColorFilter
    }
    private val filledStarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayColorFilter = PorterDuffColorFilter(
        getOverlayLayerColor(), PorterDuff.Mode.SRC_IN
    )
    private val shadeColorFilter = PorterDuffColorFilter(
        getShadeLayerColor(), PorterDuff.Mode.SRC_IN
    )

    init {
        isClickable = true
        setOnClickListener { toggle() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        hollowStarDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_star_hollow, null)
        hollowStarBitmap = createBitmap(iconSize, iconSize)
        hollowStarBitmapCanvas = Canvas(hollowStarBitmap!!)

        hollowStarDrawable?.setBounds(0, 0, iconSize, iconSize)
        hollowStarDrawable?.draw(hollowStarBitmapCanvas!!)

        filledStarDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_star_filled, null)
        filledStarBitmap = createBitmap(iconSize, iconSize)
        filledStarBitmapCanvas = Canvas(filledStarBitmap!!)

        filledStarDrawable?.setBounds(0, 0, iconSize, iconSize)
        filledStarDrawable?.draw(filledStarBitmapCanvas!!)

        checkedBackgroundDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_star_outline, null)
        checkedBackgroundBitmap = createBitmap(iconSize, iconSize)
        checkedBackgroundBitmapCanvas = Canvas(checkedBackgroundBitmap!!)

        checkedBackgroundDrawable?.setBounds(0, 0, iconSize, iconSize)
        checkedBackgroundDrawable?.draw(checkedBackgroundBitmapCanvas!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hollowStarBitmap?.recycle()
        hollowStarBitmap = null
        filledStarBitmap?.recycle()
        filledStarBitmap = null
        checkedBackgroundBitmap?.recycle()
        checkedBackgroundBitmap = null
    }

    private var shouldDrawFilledStar = false
    private var shouldDrawHollowStar = true
    private var transformFraction = HOLLOW_STAR_MINIMUM_SHRINK_FRACTION
    private var hollowStarTransformFraction = 1F
    private var backgroundTransformFraction = 1F

    private var shouldDrawNormalBackground = true
    private var shouldDrawCheckedBackground = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (shouldDrawNormalBackground) {
            drawBackground(canvas, backgroundPaint1, backgroundTransformFraction)
        }

        if (shouldDrawCheckedBackground) {
            drawCheckedBackground(canvas, backgroundPaint2, backgroundTransformFraction)
        }

        if (shouldDrawHollowStar) {
            drawHollowStar(canvas, hollowStarPaint, hollowStarTransformFraction)
        } else if (shouldDrawFilledStar) {
            drawFilledStar(canvas, filledStarPaint, transformFraction)
        }
    }

    private fun drawBackground(canvas: Canvas, paint: Paint, fraction: Float) {
        paint.alpha = (255 * fraction).toInt()

        paint.color = getBackgroundOverlayLayerColor()
        paint.blendMode = BlendMode.OVERLAY
        canvas.drawCircle(width / 2F, height / 2F, iconSize / 2F, paint)

        paint.color = getBackgroundShadeLayerColor()
        paint.blendMode = null
        canvas.drawCircle(width / 2F, height / 2F, iconSize / 2F, paint)
    }

    private fun drawCheckedBackground(canvas: Canvas, paint: Paint, factor: Float) {
        val bitmap = checkedBackgroundBitmap ?: return

        val centerX = width / 2f
        val centerY = height / 2f

        val scale = 0.95F + (1F - factor) * 0.05F

        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale

        val left = centerX - drawWidth / 2f
        val top = centerY - drawHeight / 2f

        paint.alpha = (255 * (1F - factor)).toInt()

        paint.colorFilter = overlayColorFilter
        paint.blendMode = BlendMode.OVERLAY
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            paint
        )

        paint.colorFilter = shadeColorFilter
        paint.blendMode = null
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            paint
        )
    }

    private fun drawHollowStar(canvas: Canvas, paint: Paint, factor: Float) {
        val bitmap = hollowStarBitmap ?: return

        val centerX = width / 2f
        val centerY = height / 2f

        val scale = factor

        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale

        val left = centerX - drawWidth / 2f
        val top = centerY - drawHeight / 2f

        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            paint
        )
    }

    private fun drawFilledStar(canvas: Canvas, paint: Paint, factor: Float) {
        val bitmap = filledStarBitmap ?: return

        val centerX = width / 2f
        val centerY = height / 2f

        val scale = factor

        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale

        val left = centerX - drawWidth / 2f
        val top = centerY - drawHeight / 2f

        paint.colorFilter = overlayColorFilter
        paint.blendMode = BlendMode.OVERLAY
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            paint
        )

        paint.colorFilter = shadeColorFilter
        paint.blendMode = null
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            paint
        )
    }

    override fun setChecked(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
        }
    }

    override fun toggle() {
        if (transformAnimatorSet == null || transformAnimatorSet?.isRunning == false) {
            isChecked = !isChecked
            if (isChecked) {
                animateChecked()
            } else {
                transformToUnchecked()
            }
        }
    }

    private var transformAnimatorSet: AnimatorSet? = null

    private fun animateChecked() {
        transformAnimatorSet?.cancel()
        transformAnimatorSet = null

        transformAnimatorSet = AnimatorSet().apply {
            playSequentially(
                hollowStarShrinkAnimator,
                filledStarGrowAnimator,
                filledStarShrinkAnimator,
                transformBackground()
            )
            start()
        }
    }

    private fun transformToUnchecked() {
        transformAnimatorSet?.cancel()
        transformAnimatorSet = null

        transformAnimatorSet = AnimatorSet().apply {
            playSequentially(
                transformBackground(false),
                redrawHollowStarAnimator
            )
            start()
        }
    }

    private val hollowStarShrinkAnimator: Animator =
        ValueAnimator.ofFloat(
            1.0F,
            HOLLOW_STAR_MINIMUM_SHRINK_FRACTION
        ).apply {
            duration = 300L
            interpolator = AnimationUtils.accelerateInterpolator

            addUpdateListener {
                hollowStarTransformFraction = animatedValue as Float
                invalidate()
            }
            doOnEnd {
                shouldDrawHollowStar = false
            }
        }

    private val filledStarGrowAnimator: Animator =
        ValueAnimator.ofFloat(
            HOLLOW_STAR_MINIMUM_SHRINK_FRACTION,
            FILLED_STAR_MAXIMUM_GROW_FRACTION
        ).apply {
            duration = 400L
            interpolator = AnimationUtils.decelerateInterpolator

            doOnStart {
                shouldDrawFilledStar = true
            }
            addUpdateListener {
                transformFraction = animatedValue as Float
                invalidate()
            }
        }

    private val filledStarShrinkAnimator: Animator =
        ValueAnimator.ofFloat(
            FILLED_STAR_MAXIMUM_GROW_FRACTION,
            HOLLOW_STAR_MINIMUM_SHRINK_FRACTION
        ).apply {
            duration = 300L
            interpolator = AnimationUtils.accelerateInterpolator

            addUpdateListener {
                transformFraction = animatedValue as Float
                invalidate()
            }
            doOnEnd {
                shouldDrawFilledStar = false
            }
        }

    private fun transformBackground(isChecked: Boolean = true): Animator =
        ValueAnimator.ofFloat(
            if (isChecked) 1.0F else 0F,
            if (isChecked) 0F else 1.0F
        ).apply {
            duration = 100L
            interpolator = AnimationUtils.easingInterpolator

            doOnStart {
                shouldDrawCheckedBackground = true
                shouldDrawNormalBackground = !isChecked
            }
            addUpdateListener {
                backgroundTransformFraction = animatedValue as Float
                invalidate()
            }
            doOnEnd {
                shouldDrawCheckedBackground = isChecked
                shouldDrawNormalBackground = !isChecked
                invalidate()
            }
        }

    private val redrawHollowStarAnimator: Animator =
        ValueAnimator.ofFloat(
            HOLLOW_STAR_MINIMUM_SHRINK_FRACTION,
            1.0F
        ).apply {
            duration = 100L
            interpolator = AnimationUtils.easingInterpolator

            doOnStart {
                shouldDrawHollowStar = true
            }
            addUpdateListener {
                hollowStarTransformFraction = animatedValue as Float
                invalidate()
            }
        }

    override fun isChecked(): Boolean = isChecked

    private fun getOverlayLayerColor(): Int {
        return resources.getColor(
            R.color.primaryOverlayColor,
            null
        )
    }

    private fun getShadeLayerColor(): Int {
        return resources.getColor(
            R.color.primaryOverlayShadeColor,
            null
        )
    }

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

    companion object {
        const val HOLLOW_STAR_MINIMUM_SHRINK_FRACTION = 0.8F
        const val FILLED_STAR_MAXIMUM_GROW_FRACTION = 1.5F
    }
}