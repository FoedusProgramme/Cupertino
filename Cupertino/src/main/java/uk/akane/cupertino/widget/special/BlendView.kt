package uk.akane.cupertino.widget.special

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.net.Uri
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.doOnLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.sin
import androidx.core.graphics.withTranslation
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.dpToPx
import kotlin.math.max

class BlendView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet), Choreographer.FrameCallback {

    companion object {
        const val SATURATION_FACTOR: Float = 1.7F
        const val BRIGHTNESS_FACTOR: Float = 0F
    }

    var running = false
    private var lastFrameTimeNanos = 0L
    private val frameIntervalNanos = 1_000_000_000L / 24 // 24fps

    private var currentBitmap: Bitmap? = null
    private var currentBitmapTL: Bitmap? = null
    private var currentBitmapBR: Bitmap? = null

    private var tlRotation = 0f
    private var brRotation = 0f
    private val tlMatrix = Matrix()
    private val brMatrix = Matrix()
    private val tlRotationSpeed = 0.3f
    private val brRotationSpeed = 0.5f
    private var tlOrbitAngle = 0f
    private var brOrbitAngle = 180f
    private val tlOrbitSpeed = 0.15f
    private val brOrbitSpeed = 0.13f
    private var orbitRadius = 0f

    private val renderBox: Int = 200.dpToPx(context)
    private val overlayColor = ContextCompat.getColor(context, R.color.frontShadeColor)

    private val renderNode = RenderNode("RenderBox").apply {
        setRenderEffect(
            RenderEffect.createBlurEffect(
                50f.dpToPx(context),
                50f.dpToPx(context),
                Shader.TileMode.MIRROR
            )
        )
        setPosition(0, 0, renderBox, renderBox)
    }


    init {
        doOnLayout {
            orbitRadius = renderBox / 2F
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaleX = width.toFloat() / renderBox
        val scaleY = height.toFloat() / renderBox
        val scale = max(scaleX, scaleY)

        val dx = (width - renderBox * scale) / 2f
        val dy = (height - renderBox * scale) / 2f

        canvas.withTranslation(dx, dy) {
            scale(scale, scale)
            canvas.drawRenderNode(renderNode)
        }

        canvas.drawColor(overlayColor)
    }

    private val fullMatrix = Matrix()

    private fun calculateAnimation() {
        if (currentBitmap == null || currentBitmapBR == null || currentBitmapTL == null) return
        tlRotation = (tlRotation - tlRotationSpeed) % 360f
        tlOrbitAngle = (tlOrbitAngle + tlOrbitSpeed) % 360f
        brRotation = (brRotation + brRotationSpeed) % 360f
        brOrbitAngle = (brOrbitAngle + brOrbitSpeed) % 360f

        val viewCenterX = renderBox / 2f
        val viewCenterY = renderBox / 2f

        val tlRadians = Math.toRadians(tlOrbitAngle.toDouble())
        val brRadians = Math.toRadians(brOrbitAngle.toDouble())

        val tlOrbitX = (viewCenterX + orbitRadius * cos(tlRadians)).toFloat()
        val tlOrbitY = (viewCenterY + orbitRadius * sin(tlRadians)).toFloat()
        val brOrbitX = (viewCenterX + orbitRadius * cos(brRadians)).toFloat()
        val brOrbitY = (viewCenterY + orbitRadius * sin(brRadians)).toFloat()

        val tlOrbitDrawX = tlOrbitX - currentBitmapTL!!.width / 2f
        val tlOrbitDrawY = tlOrbitY - currentBitmapTL!!.height / 2f
        val brOrbitDrawX = brOrbitX - currentBitmapBR!!.width / 2f
        val brOrbitDrawY = brOrbitY - currentBitmapBR!!.height / 2f
        val scaleX = renderBox.toFloat() / currentBitmap!!.width
        val scaleY = renderBox.toFloat() / currentBitmap!!.height

        tlMatrix.reset()
        tlMatrix.postRotate(tlRotation, currentBitmapTL!!.width / 2F, currentBitmapTL!!.height / 2F)
        tlMatrix.postTranslate(tlOrbitDrawX, tlOrbitDrawY)

        brMatrix.reset()
        brMatrix.postRotate(brRotation, currentBitmapBR!!.width / 2F, currentBitmapBR!!.height / 2F)
        brMatrix.postTranslate(brOrbitDrawX, brOrbitDrawY)

        fullMatrix.reset()
        fullMatrix.setScale(scaleX, scaleY)
    }

    fun startAnimation() {
        if (!running) {
            running = true
            lastFrameTimeNanos = System.nanoTime()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stopAnimation() {
        if (running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    private fun drawOnRenderNode() {
        val recordingCanvas = renderNode.beginRecording(renderBox, renderBox)
        currentBitmap?.let { recordingCanvas.drawBitmap(it, fullMatrix, null) }
        currentBitmapTL?.let { recordingCanvas.drawBitmap(it, tlMatrix, null) }
        currentBitmapBR?.let { recordingCanvas.drawBitmap(it, brMatrix, null) }
        renderNode.endRecording()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return

        if (frameTimeNanos - lastFrameTimeNanos >= frameIntervalNanos) {
            lastFrameTimeNanos = frameTimeNanos
            calculateAnimation()
            drawOnRenderNode()
            invalidate()
        }

        Choreographer.getInstance().postFrameCallback(this)
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    fun setImageUri(uri: Uri) {
        doOnLayout {
            CoroutineScope(Dispatchers.IO).launch {
                currentBitmap = enhanceBitmap(
                    getBitmapFromUri(context.contentResolver, uri)!!
                )
                currentBitmapTL = cropTopLeftQuarter(currentBitmap!!)
                currentBitmapBR = cropBottomRightQuarter(currentBitmap!!)
            }
        }
    }

    private fun getBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            options.inSampleSize = calculateInSampleSize(options, renderBox.toInt(), renderBox.toInt())
            options.inJustDecodeBounds = false
            inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            null
        } finally {
            inputStream?.close()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val heightRatio = height.toFloat() / reqHeight.toFloat()
            val widthRatio = width.toFloat() / reqWidth.toFloat()
            val ratio = maxOf(heightRatio, widthRatio)

            var pow = 1
            while (pow * 2 <= ratio) {
                pow *= 2
            }
            inSampleSize = pow
        }

        return inSampleSize
    }


    private fun enhanceBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val enhancedBitmap = createBitmap(width, height)
        enhancedBitmap.density = bitmap.density

        val enhancePaint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(SATURATION_FACTOR) }

        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, BRIGHTNESS_FACTOR,
                0f, 1f, 0f, 0f, BRIGHTNESS_FACTOR,
                0f, 0f, 1f, 0f, BRIGHTNESS_FACTOR,
                0f, 0f, 0f, 1f, 0f
            )
        )

        colorMatrix.postConcat(brightnessMatrix)

        enhancePaint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        val canvas = Canvas(enhancedBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, enhancePaint)

        return enhancedBitmap
    }


    private fun cropTopLeftQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 3 * 2
        val quarterHeight = bitmap.height / 3 * 2
        return Bitmap.createBitmap(bitmap, 0, 0, quarterWidth, quarterHeight)
    }

    private fun cropBottomRightQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 3 * 2
        val quarterHeight = bitmap.height / 3 * 2
        val quarterX = bitmap.width / 3
        val quarterY = bitmap.height / 3
        return Bitmap.createBitmap(bitmap, quarterX, quarterY, quarterWidth, quarterHeight)
    }
}
