package uk.akane.cupertino.widget

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import org.lsposed.hiddenapibypass.HiddenApiBypass
import uk.akane.cupertino.R
import java.lang.reflect.Field

class OverlayTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var textViewLayer: Int = 0
        set(value) {
            field = value
            invalidate()
            requestLayout()
        }

    private var textColorField: Field

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.OverlayTextView,
            0, 0
        ).apply {
            try {
                textViewLayer = getInt(R.styleable.OverlayTextView_textViewLayer, 0)
            } finally {
                recycle()
            }
        }

        textColorField = (HiddenApiBypass.getInstanceFields(TextView::class.java)
            .find { (it as Field).name == "mCurTextColor" } ?: throw NoSuchFieldException("Field 'mCurTextColor' not found")) as Field
        textColorField.isAccessible = true

        isSingleLine = true
    }

    override fun onDraw(canvas: Canvas) {
        Log.d(TAG, "onDraw")

        paint.blendMode = BlendMode.OVERLAY
        textColorField.set(this, getOverlayLayerColor())
        super.onDraw(canvas)

        paint.blendMode = null
        textColorField.set(this, getShadeLayerColor())
        super.onDraw(canvas)
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
        private const val TAG = "OverlayTextView"
    }
}