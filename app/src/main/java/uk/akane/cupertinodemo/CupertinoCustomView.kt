package uk.akane.cupertinodemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference

class CupertinoCustomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val contentNode: RenderNode = RenderNode("Content")
    val blurNode: RenderNode = RenderNode("Blur")

    private var weakRef: WeakReference<View>? = null

    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    fun setWeakRef(view: View) {
        weakRef?.get()?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)

        weakRef = WeakReference(view)

        preDrawListener = ViewTreeObserver.OnPreDrawListener {
            invalidate()
            true
        }
        view.viewTreeObserver.addOnPreDrawListener(preDrawListener)

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {

        contentNode.setPosition(0, 0, width, height)
        val rnCanvas = contentNode.beginRecording()
        weakRef?.get()?.draw(rnCanvas)
        contentNode.endRecording()

        blurNode.setRenderEffect(
            RenderEffect.createBlurEffect(30f, 30f,
            Shader.TileMode.MIRROR))
        blurNode.setPosition(0, 0, width, height)

        val blurCanvas = blurNode.beginRecording()
        blurCanvas.drawRenderNode(contentNode)
        blurNode.endRecording()

        canvas.drawRenderNode(blurNode)

        super.onDraw(canvas)

    }

}