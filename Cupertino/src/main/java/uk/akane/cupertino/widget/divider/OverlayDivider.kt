package uk.akane.cupertino.widget.divider

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Px
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.dpToPx

class OverlayDivider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resources.getColor(R.color.secondaryOverlayColor, null)
        style = Paint.Style.FILL
        blendMode = BlendMode.OVERLAY
    }

    private val shadePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resources.getColor(R.color.secondaryOverlayShadeColor, null)
        style = Paint.Style.FILL
    }

    @Px
    private val rectWidth = 36.dpToPx(context)

    @Px
    private val rectHeight = 5.25F.dpToPx(context)

    @Px
    private val cornerRadius = 3.dpToPx(context)

    init {
        layoutParams =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8.dpToPx(context))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(8.dpToPx(context), MeasureSpec.EXACTLY)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f

        val left = centerX - rectWidth / 2
        val top = centerY - rectHeight / 2
        val right = centerX + rectWidth / 2
        val bottom = centerY + rectHeight / 2

        canvas.drawRoundRect(left, top, right, bottom, cornerRadius.toFloat(), cornerRadius.toFloat(), paint)
        canvas.drawRoundRect(left, top, right, bottom, cornerRadius.toFloat(), cornerRadius.toFloat(), shadePaint)
    }
}