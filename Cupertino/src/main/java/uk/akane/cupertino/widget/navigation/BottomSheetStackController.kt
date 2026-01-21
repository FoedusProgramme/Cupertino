package uk.akane.cupertino.widget.navigation

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import com.google.android.material.card.MaterialCardView
import uk.akane.cupertino.widget.utils.AnimationUtils

class BottomSheetStackController(
    private val activity: AppCompatActivity,
    private val rootView: ViewGroup,
    private val fragmentManager: FragmentManager,
    private val config: Config,
    private val callbacks: Callbacks
) {
    data class Config(
        val stackedSheetScale: Float,
        val stackedSheetOffsetFactor: Float,
        val rootCardViewId: Int,
        val animationDuration: Long = AnimationUtils.LONG_DURATION
    )

    interface Callbacks {
        fun onContainerReady(cardView: MaterialCardView, screenHeight: Float)
        fun onBeforeShow()
        fun onBeforeHide()
        fun onBackgroundProgress(progress: Float)
        fun onAfterHide()
        fun onHideUnderlay()
        fun onShowUnderlayAnimated()
    }

    private var containerId: Int = View.NO_ID
    private val containerStack = ArrayDeque<Int>()
    private var isRemoving = false
    private var pendingRemove = false

    val hasContainers: Boolean
        get() = containerStack.isNotEmpty()

    fun showContainer(fragment: Fragment) {
        if (containerStack.isNotEmpty()) return
        insertContainer(fragment, affectBackground = true)
    }

    fun pushContainer(fragment: Fragment) {
        if (containerStack.size == 1) {
            callbacks.onHideUnderlay()
        }
        containerStack.lastOrNull()?.let { previousId ->
            animateContainerScale(previousId, config.stackedSheetScale)
        }
        insertContainer(fragment, affectBackground = false)
    }

    fun popContainerOrRemove() {
        if (containerStack.isEmpty()) return
        if (containerStack.size > 1) {
            removeContainerInternal(affectBackground = false)
            return
        }
        removeContainerInternal(affectBackground = true)
    }

    fun replaceContainer(fragment: Fragment) {
        val container = rootView.findViewById<FragmentContainerView>(containerId)
        if (container == null) {
            insertContainer(fragment, affectBackground = true)
            return
        }
        fragmentManager.beginTransaction()
            .replace(container.id, fragment)
            .runOnCommit {
                val containerCardView: MaterialCardView =
                    container.findViewById(config.rootCardViewId)
                val screenHeight = Resources.getSystem().displayMetrics.heightPixels.toFloat()
                callbacks.onContainerReady(containerCardView, screenHeight)
                containerCardView.translationY = 0f
                containerCardView.visibility = View.VISIBLE
            }
            .commit()
    }

    fun removeContainer() {
        if (containerStack.isEmpty()) return
        removeContainerInternal(affectBackground = containerStack.size <= 1)
    }

    private fun insertContainer(fragment: Fragment, affectBackground: Boolean) {
        val container = FragmentContainerView(activity).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
        }

        containerId = container.id
        containerStack.addLast(container.id)
        rootView.addView(container)

        container.post {
            fragmentManager.beginTransaction()
                .replace(container.id, fragment)
                .runOnCommit {
                    val containerCardView: MaterialCardView =
                        container.findViewById(config.rootCardViewId)
                    val screenHeight = Resources.getSystem().displayMetrics.heightPixels.toFloat()
                    callbacks.onContainerReady(containerCardView, screenHeight)
                    containerCardView.translationY = screenHeight
                    if (affectBackground) {
                        callbacks.onBeforeShow()
                    }

                    containerCardView.post {
                        containerCardView.visibility = View.VISIBLE
                        AnimationUtils.createValAnimator<Float>(
                            containerCardView.translationY,
                            0F,
                            duration = config.animationDuration
                        ) { animatedValue ->
                            containerCardView.translationY = animatedValue
                            if (affectBackground) {
                                callbacks.onBackgroundProgress(
                                    1f - animatedValue / screenHeight
                                )
                            }
                        }
                    }
                }
                .commit()
        }
    }

    private fun removeContainerInternal(affectBackground: Boolean) {
        if (isRemoving) {
            pendingRemove = true
            return
        }
        val container = rootView.findViewById<FragmentContainerView>(containerId)
            ?: return
        val isStacked = !affectBackground && containerStack.size > 1
        val previousContainerId = if (isStacked) {
            containerStack.elementAt(containerStack.size - 2)
        } else {
            null
        }

        val containerCardView: MaterialCardView = container.findViewById(config.rootCardViewId)
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels.toFloat()
        val startAlpha = containerCardView.alpha
        val endAlpha = if (isStacked) 0f else startAlpha
        isRemoving = true
        if (affectBackground) {
            callbacks.onBeforeHide()
        }
        if (isStacked && containerStack.size == 2) {
            callbacks.onShowUnderlayAnimated()
        }
        previousContainerId?.let { animateContainerScale(it, 1f) }

        AnimationUtils.createValAnimator<Float>(
            0F,
            screenHeight,
            duration = config.animationDuration,
            doOnEnd = {
                fragmentManager.findFragmentById(container.id)?.let {
                    fragmentManager.beginTransaction().remove(it).commit()
                }
                rootView.removeView(container)
                if (containerStack.isNotEmpty()) {
                    containerStack.removeLast()
                }
                containerId = containerStack.lastOrNull() ?: View.NO_ID
                if (affectBackground) {
                    callbacks.onAfterHide()
                }
                isRemoving = false
                if (pendingRemove) {
                    pendingRemove = false
                    if (containerStack.isNotEmpty()) {
                        removeContainerInternal(affectBackground = containerStack.size <= 1)
                    }
                }
            },
            doOnCancel = {
                isRemoving = false
                pendingRemove = false
            }
        ) { animatedValue ->
            containerCardView.translationY = animatedValue
            if (isStacked) {
                val fraction = (animatedValue / screenHeight).coerceIn(0f, 1f)
                containerCardView.alpha = lerp(startAlpha, endAlpha, fraction)
            }
            if (affectBackground) {
                callbacks.onBackgroundProgress(1f - animatedValue / screenHeight)
            }
        }
    }

    private fun animateContainerScale(containerId: Int, targetScale: Float) {
        val container = rootView.findViewById<FragmentContainerView>(containerId) ?: return
        val cardView = container.findViewById<MaterialCardView>(config.rootCardViewId) ?: return
        cardView.pivotX = cardView.width / 2f
        cardView.pivotY = cardView.height / 2f
        val startScale = cardView.scaleX
        val startTranslation = cardView.translationY
        val topMargin = (cardView.layoutParams as? MarginLayoutParams)?.topMargin ?: 0
        val targetTranslation = if (targetScale < 1f) {
            -topMargin.toFloat() * config.stackedSheetOffsetFactor
        } else {
            0f
        }
        if (startScale == targetScale && startTranslation == targetTranslation) return
        val range = targetScale - startScale
        AnimationUtils.createValAnimator<Float>(
            startScale,
            targetScale,
            duration = config.animationDuration
        ) { value ->
            val fraction = if (range == 0f) 1f else ((value - startScale) / range).coerceIn(0f, 1f)
            cardView.scaleX = value
            cardView.scaleY = value
            cardView.translationY = lerp(startTranslation, targetTranslation, fraction)
            val t = ((1f - value) / (1f - config.stackedSheetScale)).coerceIn(0f, 1f)
            cardView.alpha = lerp(1f, 0.5f, t)
        }
    }

    private fun lerp(from: Float, to: Float, fraction: Float): Float {
        return from + (to - from) * fraction
    }
}
