package uk.akane.cupertino.widget.special

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.Choreographer
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageSwitcher
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.areBitmapsVaguelySame
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.math.ceil
import kotlin.math.cos

class BlendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes),
    Choreographer.FrameCallback {

    private val imageViewTS: ImageSwitcher
    private val imageViewBE: ImageSwitcher
    private val imageViewBG: ImageSwitcher
    private val rotateFrame: ConstraintLayout
    private val blurredFrame: ConstraintLayout
    private val overlayColor = ContextCompat.getColor(context, R.color.frontShadeColor)
    private var previousBitmap: Bitmap? = null
    private var curveBitmap: Bitmap? = null

    private val overlayPaint = Paint().apply {
        blendMode = BlendMode.SOFT_LIGHT
        alpha = 30
        isDither = true
    }

    companion object {
        const val VIEW_TRANSIT_DURATION: Long = 400
        const val FULL_BLUR_RADIUS: Float = 120F
        const val SHALLOW_BLUR_RADIUS: Float = 60F
        const val CYCLE: Int = 360
        const val SATURATION_FACTOR: Float = 2F
        const val BRIGHTNESS_FACTOR: Float = 0F
        const val PICTURE_SIZE: Int = 60
    }

    init {
        inflate(context, R.layout.view_blend, this)
        imageViewTS = findViewById(R.id.type1)
        imageViewBE = findViewById(R.id.type3)
        imageViewBG = findViewById(R.id.bg)
        rotateFrame = findViewById(R.id.rotate_frame)
        blurredFrame = findViewById(R.id.blurredViews)

        CoroutineScope(Dispatchers.IO).launch {
            curveBitmap =
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.fg_blend_curving,
                    BitmapFactory.Options().apply { inSampleSize = 16 })
            postInvalidate()
        }

        initializeImageSwitchers()

        blurredFrame.setRenderEffect(
            RenderEffect.createBlurEffect(FULL_BLUR_RADIUS, FULL_BLUR_RADIUS, Shader.TileMode.MIRROR)
        )
    }

    private fun initializeImageSwitchers() {
        val animationIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in).apply {
            duration = VIEW_TRANSIT_DURATION
        }
        val animationOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out).apply {
            duration = VIEW_TRANSIT_DURATION
        }
        val factoryList = listOf(imageViewTS, imageViewBE, imageViewBG)

        factoryList.forEach { switcher ->
            switcher.setFactory {
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setLayerType(LAYER_TYPE_SOFTWARE, null)
                }
            }
            switcher.inAnimation = animationIn
            switcher.outAnimation = animationOut
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        canvas.drawColor(overlayColor)
        curveBitmap?.let { bmp ->
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()

            val bmpWidth = bmp.width.toFloat()
            val bmpHeight = bmp.height.toFloat()

            val scale = minOf(viewWidth / bmpWidth, viewHeight / bmpHeight)

            val scaledWidth = bmpWidth * scale
            val scaledHeight = bmpHeight * scale

            val left = (viewWidth - scaledWidth) / 2f
            val top = (viewHeight - scaledHeight) / 2f

            val dstRect = RectF(left, top, left + scaledWidth, top + scaledHeight)

            // TODO
            // canvas.drawBitmap(bmp, null, dstRect, overlayPaint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        adjustViewScale()
    }

    private fun adjustViewScale() {
        doOnLayout {
            val windowMetrics = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
            val screenHeight = windowMetrics.bounds.height()
            val screenWidth = windowMetrics.bounds.width()

            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()

            val finalScale =
                ceil((screenWidth / viewWidth).coerceAtLeast(screenHeight / viewHeight))

            this.scaleX = finalScale
            this.scaleY = finalScale
        }
    }

    fun setImageUri(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val originalBitmap = getBitmapFromUri(context.contentResolver, uri)
            if (originalBitmap != null && !originalBitmap.areBitmapsVaguelySame(previousBitmap)) {
                enhanceBitmap(originalBitmap).let { enhancedBitmap ->
                    withContext(Dispatchers.Main) {
                        updateImageViews(enhancedBitmap)
                    }
                }
                previousBitmap = originalBitmap
            } else if (originalBitmap == null) {
                withContext(Dispatchers.Main) {
                    clearImageViews()
                }
                previousBitmap = null
            }
        }
    }

    private fun updateImageViews(bitmap: Bitmap) {
        imageViewTS.setImageDrawable(cropTopLeftQuarter(bitmap).toDrawable(resources))
        imageViewBE.setImageDrawable(cropBottomRightQuarter(bitmap).toDrawable(resources))
        imageViewBG.setImageDrawable(bitmap.toDrawable(resources))
    }

    private fun clearImageViews() {
        imageViewTS.setImageDrawable(null)
        imageViewBE.setImageDrawable(null)
        imageViewBG.setImageDrawable(null)
    }

    fun animateBlurRadius(enlarge: Boolean, duration: Long) {
        val fromVal = if (enlarge) SHALLOW_BLUR_RADIUS else FULL_BLUR_RADIUS
        val toVal = if (enlarge) FULL_BLUR_RADIUS else SHALLOW_BLUR_RADIUS
        ValueAnimator.ofFloat(fromVal, toVal).apply {
            this.duration = duration
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                val renderEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR)
                post { blurredFrame.setRenderEffect(renderEffect) }
            }
            start()
        }
    }

    fun startRotationAnimation() {
        if (!running && alpha > 0) {
            running = true
            lastFrameTimeNanos = System.nanoTime()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stopRotationAnimation() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Choreographer.getInstance().removeFrameCallback(this)
    }

    private var lastFrameTimeNanos = 0L
    private val frameIntervalNanos = 1_000_000_000L / 30  // 30fps
    private var running = false

    val isRunning: Boolean
        get() = running

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (frameTimeNanos - lastFrameTimeNanos >= frameIntervalNanos) {
            lastFrameTimeNanos = frameTimeNanos
            imageViewTS.rotation = (imageViewTS.rotation + .3f) % CYCLE
            imageViewBE.rotation = (imageViewBE.rotation + .2f) % CYCLE
            rotateFrame.rotation = (rotateFrame.rotation - .1f) % CYCLE
            overlayPaint.alpha = 30 + (30 * sineAlphaFloat()).toInt()
            invalidate()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private var frame = 0

    fun sineAlphaFloat(): Float {
        frame++
        val radians = frame * 0.05f
        return (1f + cos(radians.toDouble())).toFloat() / 2f // 0.0 ~ 1.0
    }


    private fun getBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            options.inSampleSize = calculateInSampleSize(options)
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

    private fun calculateInSampleSize(options: BitmapFactory.Options): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > PICTURE_SIZE || width > PICTURE_SIZE) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= PICTURE_SIZE && (halfWidth / inSampleSize) >= PICTURE_SIZE) {
                inSampleSize *= 2
            }
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
        val quarterWidth = bitmap.width / 2
        val quarterHeight = bitmap.height / 2
        return Bitmap.createBitmap(bitmap, 0, 0, quarterWidth, quarterHeight)
    }

    private fun cropBottomRightQuarter(bitmap: Bitmap): Bitmap {
        val quarterWidth = bitmap.width / 2
        val quarterHeight = bitmap.height / 2
        return Bitmap.createBitmap(bitmap, quarterWidth, quarterHeight, quarterWidth, quarterHeight)
    }
}