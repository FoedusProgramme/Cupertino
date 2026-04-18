package uk.akane.cupertino.widget.text

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.getViewLayer
import uk.akane.cupertino.widget.getOverlayLayerColor
import uk.akane.cupertino.widget.getShadeLayerColor
import java.lang.reflect.Field

class OverlayTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var viewLayer: Int = 0
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
                viewLayer = getViewLayer(R.styleable.OverlayTextView_viewLayer, 0)
            } finally {
                recycle()
            }
        }

        textColorField = resolveTextColorField()
        textColorField.isAccessible = true

        isSingleLine = true
        updateFallbackLineSpacing()
    }

    private fun resolveTextColorField(): Field {
        val fields = runCatching {
            if (shouldUseHiddenApiBypass()) {
                val clazz = Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass")
                val method = clazz.getMethod("getInstanceFields", Class::class.java)
                @Suppress("UNCHECKED_CAST")
                method.invoke(null, TextView::class.java) as Array<Field>
            } else {
                TextView::class.java.declaredFields
            }
        }.getOrElse {
            TextView::class.java.declaredFields
        }
        return fields.find { it.name == "mCurTextColor" }
            ?: throw NoSuchFieldException("Field 'mCurTextColor' not found")
    }

    private fun shouldUseHiddenApiBypass(): Boolean {
        if (isInEditMode) return false
        val contextName = context.javaClass.name
        return !contextName.contains("BridgeContext", ignoreCase = true)
    }

    private fun containsCjk(text: CharSequence?): Boolean {
        if (text.isNullOrEmpty()) {
            return false
        }

        var index = 0
        while (index < text.length) {
            val current = text[index]
            if (current.code < 0x80) {
                index++
                continue
            }

            val codePoint = Character.codePointAt(text, index)
            if (isCjkScript(codePoint)) {
                return true
            }
            index += Character.charCount(codePoint)
        }
        return false
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        updateFallbackLineSpacing()
    }

    override fun setTypeface(tf: Typeface?) {
        super.setTypeface(tf)
        updateFallbackLineSpacing()
    }

    private fun updateFallbackLineSpacing() {
        setFallbackLineSpacing(!containsCjk(text))
    }

    private fun isCjkScript(codePoint: Int): Boolean {
        return when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL -> true
            else -> false
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (isInEditMode) {
            setTextColor(resources.getShadeLayerColor(viewLayer))
            super.onDraw(canvas)
            return
        }
        paint.blendMode = BlendMode.OVERLAY
        textColorField.set(this, resources.getOverlayLayerColor(viewLayer))
        super.onDraw(canvas)

        paint.blendMode = null
        textColorField.set(this, resources.getShadeLayerColor(viewLayer))
        super.onDraw(canvas)
    }

    companion object {
        private const val TAG = "OverlayTextView"
    }
}
