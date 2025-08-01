package uk.akane.cupertino.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.doOnLayout
import uk.akane.cupertino.R

class OverlaySlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr),
    GestureDetector.OnGestureListener {

    private var gestureDetector = GestureDetector(context, this)
    var progress = 0F

    val defaultPaint = Paint()
    val boundPath = Path()
    val boundRectF = RectF()

    private val trackOverlayColor = resources.getColor(R.color.primaryOverlayColor, null)
    private val trackShadeColor = resources.getColor(R.color.secondaryOverlayShadeColor, null)

    init {
        doOnLayout {
            updateTrackBound()
        }
    }

    private val actualHeight = 7F.dpToPx(context)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.clipPath(boundPath)

        defaultPaint.blendMode = BlendMode.OVERLAY
        defaultPaint.color = trackOverlayColor
        defaultPaint.strokeWidth = actualHeight
        defaultPaint.strokeCap
        canvas.drawLine(0F, height / 2F, width.toFloat(), height / 2F, defaultPaint)

        defaultPaint.blendMode = null
        defaultPaint.color = trackShadeColor
        canvas.drawLine(0F, height / 2F, width.toFloat(), height / 2F, defaultPaint)
    }

    private fun updateTrackBound() {
        boundRectF.set(
            0F,
            (height - actualHeight) / 2,
            measuredWidth.toFloat(),
            height - (height - actualHeight) / 2
        )
        boundPath.addRoundRect(boundRectF, 100 / 2f, 100 / 2f, Path.Direction.CW)
    }

    override fun onDown(e: MotionEvent): Boolean {
        Log.d("TAG", "onDown $e")
        this.scaleY = 1.9F
        this.scaleX = 1.07F
        return true
    }

    override fun onShowPress(e: MotionEvent) {
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        Log.d("TAG", "onSingleTapUp $e")
        restoreScaling()
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        Log.d("TAG", "onFling,\n$e1\n$e2\n$distanceX\n$distanceY")
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        Log.d("TAG", "onLongPress $e")
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        Log.d("TAG", "onFling,\n$e1\n$e2\n$velocityX\n$velocityY")
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (gestureDetector.onTouchEvent(event)) {
            true
        } else if (event.action == MotionEvent.ACTION_UP) {
            onUp(event)
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    private fun onUp(event: MotionEvent) {
        Log.d("TAG", "onUp: $event")
        restoreScaling()
    }

    private fun restoreScaling() {
        this.scaleY = 1F
        this.scaleX = 1F
    }

}