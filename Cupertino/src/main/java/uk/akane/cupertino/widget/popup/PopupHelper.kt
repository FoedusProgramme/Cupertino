package uk.akane.cupertino.widget.popup

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withSave
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.utils.AnimationUtils

private val Int.dp: Float
    get() = this * Resources.getSystem().displayMetrics.density

private val Float.dp: Float
    get() = this * Resources.getSystem().displayMetrics.density

private val Int.sp: Float
    get() = this * Resources.getSystem().displayMetrics.scaledDensity

private val Float.sp: Float
    get() = this * Resources.getSystem().displayMetrics.scaledDensity

private inline fun <T> Iterable<T>.sumOf(selector: (T) -> Float): Float {
    var sum = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

private inline fun lerp(
    start: Float,
    stop: Float,
    amount: Float,
    interpolator: (Float) -> Float = { it }
): Float {
    val t = interpolator(amount)
    return start + (stop - start) * t
}

private fun RenderNode.setOutline(
    left: Int = 0,
    top: Int = 0,
    right: Int = width,
    bottom: Int = height,
    radius: Float = 0F
) {
    val outline = Outline().apply {
        setRoundRect(left, top, right, bottom, radius)
    }
    setOutline(outline)
}

private fun Context.isDarkMode(): Boolean =
    resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

class PopupHelper(
    private val context: Context,
    private val contentRenderNode: RenderNode,
    textTypeface: Typeface? = null
) {

    private var currentPopupEntries: PopupEntries? = null

    // Popup properties
    private val popupWidth: Float = 270.dp
    private val popupHeight: Float
        get() = currentPopupEntries?.entries?.sumOf { it.heightInPx } ?: 0F
    private val popupRadius: Float = 14.dp
    private val popupItemHorizontalMargin = 18.dp
    private val popupIconCenterToRightMargin = 27.dp

    // Popup colors
    private val popupColorDodge = context.getColor(R.color.popupMenuColorDodge)
    private val popupColorPlain = context.getColor(R.color.popupMenuPlain)
    private val contentColor = context.getColor(R.color.onSurfaceColorSolid)
    private val destructiveColor = context.getColor(R.color.red)
    private val separatorColor = context.getColor(R.color.popupMenuSeparator)
    private val largeSeparatorColor = context.getColor(R.color.popupMenuLargeSeparator)

    // Popup locations

    // Initial
    private var popupInitialLocationX = 0
    private var popupInitialLocationY = 0

    // Motion
    private var popupLeft = 0F
    private var popupTop = 0F
    private var popupRight = 0F
    private var popupBottom = 0F

    // Last popup
    private var lastPopupWidth = 0
    private var lastPopupHeight = 0

    // Progress
    private var popupTransformFraction = 0F
    private var popupRenderNodeDirty = true
    val transformFraction: Float
        get() = popupTransformFraction
    private var popupAnchorFromTop = false
    private var popupBackgroundRenderNode: RenderNode? = null

    // Called upon dismiss
    private var popupDismissAction: (() -> Unit)? = null

    // Animator
    private var popupAnimator: ValueAnimator? = null

    // Popup paint
    private val popupForegroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = popupColorDodge }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = separatorColor }
    private val largeSeparatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = largeSeparatorColor }
    private val popupForegroundShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = popupColorPlain }

    // Content paint
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = contentColor
        textSize = 18.sp
        typeface = textTypeface ?: Typeface.DEFAULT
    }
    val textPaintFontMetrics: Paint.FontMetrics by lazy { textPaint.fontMetrics }

    // RenderNode
    private val popupRenderNode = RenderNode("popupBlur").apply {
        clipToOutline = true
        setRenderEffect(
            RenderEffect.createBlurEffect(
                50.dp,
                50.dp,
                Shader.TileMode.MIRROR
            )
        )
    }

    // Exposed methods
    fun isInsidePopupMenu(x: Float, y: Float): Boolean {
        return x in popupLeft..popupRight && y in popupTop..popupBottom
    }

    fun findEntryAt(x: Float, y: Float): PopupEntry? {
        if (popupTransformFraction != 1f) return null
        if (!isInsidePopupMenu(x, y)) return null
        val entries = currentPopupEntries?.entries ?: return null
        val startTop = if (popupAnchorFromTop) {
            popupInitialLocationY.toFloat()
        } else {
            popupInitialLocationY - popupHeight
        }
        var heightAccumulated = 0f
        entries.forEach { entry ->
            val entryTop = startTop + heightAccumulated
            val entryBottom = entryTop + entry.heightInPx
            if (y in entryTop..entryBottom) {
                return entry
            }
            heightAccumulated += entry.heightInPx
        }
        return null
    }

    fun drawPopup(canvas: Canvas) {
        if (popupTransformFraction != 0f) {
            calculatePopupBounds()
            if (!popupRenderNode.hasDisplayList()) {
                drawBlurredBackground()
            }
            drawPopupContent(canvas)
        }
    }


    fun callUpPopup(
        isRetract: Boolean,
        entryList: PopupEntries?,
        locationX: Int = 0,
        locationY: Int = 0,
        anchorFromTop: Boolean = false,
        backgroundRenderNode: RenderNode? = null,
        dismissAction: (() -> Unit)? = null,
        invalidate: (() -> Unit),
        doOnStart: (() -> Unit),
        doOnEnd: (() -> Unit)
    ) {
        if (popupTransformFraction != 0F && popupTransformFraction != 1F) {
            dismissAction?.invoke()
            return
        }

        if (!isRetract) {
            popupInitialLocationX = locationX + 16.dp.toInt()
            popupInitialLocationY = locationY - 12.dp.toInt()
            popupAnchorFromTop = anchorFromTop
            popupBackgroundRenderNode = backgroundRenderNode
        } else {
            popupBackgroundRenderNode = null
        }

        if (entryList != null) {
            currentPopupEntries = entryList
        }

        if (dismissAction != null) {
            popupDismissAction = dismissAction
        }

        if (isRetract) {
            popupDismissAction?.invoke()
            popupDismissAction = null
        }

        popupAnimator?.cancel()
        popupAnimator = null

        if (!isRetract) {
            popupRenderNode.discardDisplayList()
        }

        val renderTop = if (popupAnchorFromTop) {
            popupInitialLocationY.toFloat()
        } else {
            popupInitialLocationY - popupHeight
        }
        val renderBottom = if (popupAnchorFromTop) {
            popupInitialLocationY + popupHeight
        } else {
            popupInitialLocationY.toFloat()
        }

        popupRenderNode.setPosition(
            (popupInitialLocationX - popupWidth).toInt(),
            renderTop.toInt(),
            popupInitialLocationX,
            renderBottom.toInt()
        )

        doOnStart.invoke()

        popupAnimator = AnimationUtils.createValAnimator<Float>(
            if (isRetract) 1F else 0F,
            if (isRetract) 0F else 1F,
            duration = 300L,
            interpolator = AnimationUtils.linearInterpolator,
            doOnEnd = {
                doOnEnd.invoke()
            },
            doOnCancel = {
                doOnEnd.invoke()
            }
        ) {
            popupTransformFraction = it
            invalidate()
        }
    }

    // Internal method for call up drawing
    private fun drawPopupContent(canvas: Canvas) {
        val alphaInt = lerp(
            0F,
            255F,
            popupTransformFraction
        ) { t -> AnimationUtils.accelerateDecelerateInterpolator.getInterpolation(t) }.toInt()

        val layer = canvas.saveLayerAlpha(
            popupLeft,
            popupTop,
            popupRight,
            popupBottom,
            alphaInt
        )

        canvas.drawRenderNode(popupRenderNode)
        drawForegroundShade(canvas)
        drawItemContent(canvas)

        canvas.restoreToCount(layer)
    }

    // Internal calculations
    private fun calculatePopupBounds() {
        popupLeft = lerp(
            popupInitialLocationX - popupWidth * 0.1F,
            (popupInitialLocationX - popupWidth),
            popupTransformFraction
        ) { f -> AnimationUtils.linearOutSlowInInterpolator.getInterpolation(f) }
        popupRight = popupInitialLocationX.toFloat()

        if (popupAnchorFromTop) {
            popupTop = popupInitialLocationY.toFloat()
            popupBottom = lerp(
                popupInitialLocationY + popupHeight * 0.1F,
                popupInitialLocationY + popupHeight,
                popupTransformFraction
            ) { f -> AnimationUtils.fastOutSlowInInterpolator.getInterpolation(f) }
        } else {
            popupTop = lerp(
                popupInitialLocationY - popupHeight * 0.1F,
                (popupInitialLocationY - popupHeight),
                popupTransformFraction
            ) { f -> AnimationUtils.fastOutSlowInInterpolator.getInterpolation(f) }
            popupBottom = popupInitialLocationY.toFloat()
        }

        val newWidth = (popupRight - popupLeft).toInt()
        val newHeight = (popupBottom - popupTop).toInt()

        if (newWidth != lastPopupWidth || newHeight != lastPopupHeight) {
            popupRenderNodeDirty = true
            lastPopupWidth = newWidth
            lastPopupHeight = newHeight

            popupRenderNode.setOutline(
                (popupLeft - popupInitialLocationX + popupWidth).toInt(),
                if (popupAnchorFromTop) {
                    (popupTop - popupInitialLocationY).toInt()
                } else {
                    (popupTop - popupInitialLocationY + popupHeight).toInt()
                },
                popupWidth.toInt(),
                popupHeight.toInt(),
                popupRadius)
        }
    }

    private fun drawBlurredBackground() {
        if (!popupRenderNodeDirty) return

        val recordingCanvas = popupRenderNode.beginRecording(popupWidth.toInt(), popupHeight.toInt())
        val translateY = if (popupAnchorFromTop) {
            -popupInitialLocationY.toFloat()
        } else {
            -popupInitialLocationY + popupHeight
        }
        recordingCanvas.translate(-popupInitialLocationX + popupWidth, translateY)
        recordingCanvas.drawRenderNode(popupBackgroundRenderNode ?: contentRenderNode)

        popupRenderNode.endRecording()
        popupRenderNodeDirty = false
    }


    private fun drawItemContent(canvas: Canvas) {
        canvas.withSave {
            var heightAccumulated = 0F

            val scale = (popupInitialLocationX - popupLeft) / popupWidth

            canvas.scale(scale, scale, popupLeft, popupTop)

            val distanceX = popupLeft - (popupInitialLocationX - popupWidth)
            val distanceY = popupTop - if (popupAnchorFromTop) {
                popupInitialLocationY.toFloat()
            } else {
                (popupInitialLocationY - popupHeight)
            }

            canvas.translate(
                distanceX,
                distanceY,
            )

            currentPopupEntries!!.entries.forEachIndexed { index, entry ->
                // Calculate bounds
                val entryLeft = popupInitialLocationX - popupWidth
                val entryTop = (if (popupAnchorFromTop) {
                    popupInitialLocationY.toFloat()
                } else {
                    popupInitialLocationY - popupHeight
                }) + heightAccumulated
                val entryRight = popupRight
                val entryBottom = entryTop + entry.heightInPx

                heightAccumulated += entry.heightInPx

                when (entry) {
                    is Spacer -> {
                        canvas.drawRect(
                            entryLeft,
                            entryTop,
                            entryRight,
                            entryBottom,
                            largeSeparatorPaint
                        )
                    }

                    is MenuEntry -> {
                        textPaint.color =
                            if (entry.isDestructive) destructiveColor else contentColor

                        entry.icon.let { drawable ->
                            val iconWidth = entry.icon.intrinsicWidth
                            val iconLeft = entryRight - iconWidth / 2f - popupIconCenterToRightMargin
                            val iconTop = (entryTop + entryBottom - iconWidth) / 2f
                            val iconRight = iconLeft + iconWidth
                            val iconBottom = iconTop + iconWidth

                            drawable.setTint(
                                if (entry.isDestructive) destructiveColor else contentColor
                            )
                            drawable.setBounds(
                                iconLeft.toInt(),
                                iconTop.toInt(),
                                iconRight.toInt(),
                                iconBottom.toInt()
                            )
                            drawable.draw(canvas)
                        }

                        entry.string.let { str ->
                            val fm = textPaintFontMetrics
                            val textHeight = fm.descent - fm.ascent
                            val offsetY =
                                entryTop + (entryBottom - entryTop - textHeight) / 2f - fm.ascent

                            val textLeft = entryLeft + popupItemHorizontalMargin
                            canvas.drawText(str, textLeft, offsetY, textPaint)
                        }


                        if (index != currentPopupEntries!!.entries.size - 1 &&
                            currentPopupEntries!!.entries[index + 1] !is Spacer
                        ) {
                            canvas.drawRect(
                                entryLeft,
                                entryBottom + 0.5f.dp,
                                entryRight,
                                entryBottom,
                                separatorPaint
                            )
                        }
                    }
                }
            }
        }
    }

    private fun drawForegroundShade(canvas: Canvas) {
        if (context.isDarkMode()) {
            drawShade(popupForegroundPaint, BlendMode.COLOR_DODGE, canvas)
            drawShade(popupForegroundShadePaint, null, canvas)
        } else {
            drawShade(popupForegroundShadePaint, null, canvas)
            drawShade(popupForegroundPaint, BlendMode.COLOR_DODGE, canvas)
        }
    }

    fun drawShade(paint: Paint, blendMode: BlendMode? = null, canvas: Canvas) {
        paint.blendMode = blendMode
        canvas.drawRoundRect(
            popupLeft,
            popupTop,
            popupRight,
            popupBottom,
            popupRadius,
            popupRadius,
            paint
        )
    }

    // Helper classes
    @JvmInline
    value class PopupEntries(val entries: List<PopupEntry>)

    class PopupMenuBuilder {
        private val menuEntryList: MutableList<PopupEntry> = mutableListOf()

        fun addMenuEntry(
            resources: Resources,
            iconResId: Int,
            iconDescriptionResId: Int,
            payload: Any? = null
        ): PopupMenuBuilder {
            menuEntryList.add(
                MenuEntry(
                    ResourcesCompat.getDrawable(resources, iconResId, null)!!,
                    resources.getString(iconDescriptionResId),
                    payload = payload
                )
            )
            return this
        }

        fun addDestructiveMenuEntry(
            resources: Resources,
            iconResId: Int,
            iconDescriptionResId: Int,
            payload: Any? = null
        ): PopupMenuBuilder {
            menuEntryList.add(
                MenuEntry(
                    ResourcesCompat.getDrawable(resources, iconResId, null)!!,
                    resources.getString(iconDescriptionResId),
                    true,
                    payload
                )
            )
            return this
        }

        fun addSpacer(): PopupMenuBuilder {
            menuEntryList.add(Spacer())
            return this
        }

        fun build() = PopupEntries(menuEntryList)
    }

    data class MenuEntry(
        val icon: Drawable,
        val string: String,
        val isDestructive: Boolean = false,
        val payload: Any? = null
    ) : PopupEntry(48)

    class Spacer : PopupEntry(10)

    abstract class PopupEntry(val heightInDp: Int) {
        val heightInPx by lazy { heightInDp.dp }
    }
}
