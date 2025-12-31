package uk.akane.cupertino.widget.navigation

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import uk.akane.cupertino.R
import uk.akane.cupertino.widget.dpToPx
import uk.akane.cupertino.widget.lerp
import uk.akane.cupertino.widget.runOnContentLoaded
import uk.akane.cupertino.widget.utils.AnimationUtils
import uk.akane.cupertino.widget.utils.AnimationUtils.doOnEnd
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import android.view.animation.AnimationUtils as AndroidAnimationUtils

class FragmentSwitcherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    GestureDetector.OnGestureListener {

    private var gestureDetector = GestureDetector(context, this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isEdgeSwipeActive = false
    private var edgeStartX = 0f
    private var edgeStartY = 0f
    private var edgeDownEvent: MotionEvent? = null

    // DEFAULT_CONTAINER
    private val containerDefault: FrameLayout
    // APPEND_CONTAINER
    private val containerAppend: FrameLayout
    private val scrimView: View

    private var activeContainer: ContainerType = ContainerType.DEFAULT_CONTAINER

    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxScrimAlpha = 0.4f
    private val maxOverscrollFraction = 0.6f
    private val overscrollResistance = 1f
    private val backCommitThreshold = 0.5f
    private var predictiveBackActive = false

    private var fragmentManager: FragmentManager? = null
    private val baseFragments: MutableList<Fragment> = mutableListOf()
    private val subFragmentStack: MutableList<MutableList<Fragment>> = mutableListOf()
    private var currentBaseFragment: Int = 0

    private val fadeInAnimation = AndroidAnimationUtils.loadAnimation(context, R.anim.fade_in)
    private val fadeOutAnimation = AndroidAnimationUtils.loadAnimation(context, R.anim.fade_out)

    private var surfaceColor: Int = 0

    init {
        @Suppress("UsePropertyAccessSyntax")
        gestureDetector.setIsLongpressEnabled(false)

        inflate(context, R.layout.layout_fragment_switcher, this)

        containerDefault = findViewById(R.id.fragment_0)
        containerAppend = findViewById(R.id.fragment_1)
        scrimView = findViewById(R.id.fragment_scrim)
        scrimView.isClickable = false
        scrimView.elevation = 5F.dpToPx(context)

        surfaceColor = resources.getColor(R.color.surfaceColor, null)
    }

    private var firstRun: Boolean = true

    fun setup(
        activity: FragmentActivity,
        fragmentList: List<Fragment>,
        tags: List<String>,
        defaultIndex: Int = 0
    ) = doOnLayout {
        if (!firstRun) return@doOnLayout

        require(fragmentList.size == tags.size) { "Fragment count should match tag count" }
        require(tags.distinct().size == tags.size) { "Tags should be unique" }

        fragmentManager = activity.supportFragmentManager

        baseFragments.clear()
        baseFragments.addAll(fragmentList)

        repeat(baseFragments.size) { subFragmentStack.add(mutableListOf()) }

        fragmentManager!!.beginTransaction().apply {
            baseFragments.forEachIndexed { index, fragment ->
                add(getContainer(ContainerType.DEFAULT_CONTAINER).id, fragment, tags[index])
                hide(fragment)
            }
            show(baseFragments[defaultIndex])
        }.commit()

        currentBaseFragment = defaultIndex

        getContainer(ContainerType.APPEND_CONTAINER).translationX = width.toFloat()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)

        ss.currentBaseFragment = currentBaseFragment
        ss.activeContainerOrdinal = activeContainer.ordinal
        ss.baseFragmentTags = baseFragments.map { it.tag ?: "" }
        ss.subFragmentStackTags = subFragmentStack.map { list ->
            list.map { it.tag ?: "" }
        }

        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)

        firstRun = false
        currentBaseFragment = state.currentBaseFragment
        activeContainer = ContainerType.entries.toTypedArray()[state.activeContainerOrdinal]

        val activity = context as? FragmentActivity
        if (activity != null) {
            fragmentManager = activity.supportFragmentManager
        }

        state.baseFragmentTags?.let { savedTags ->
            baseFragments.clear()
            savedTags.forEach { tag ->
                val fragment = fragmentManager?.findFragmentByTag(tag) ?: return@forEach
                baseFragments.add(fragment)
            }
        }

        state.subFragmentStackTags?.let { savedStacks ->
            subFragmentStack.clear()
            savedStacks.forEach { tagList ->
                val fragmentList = tagList.mapNotNull { tag ->
                    fragmentManager?.findFragmentByTag(tag)
                }.toMutableList()
                subFragmentStack.add(fragmentList)
            }
        }

        Log.d(
            TAG,
            "onRestoreInstanceState: ${state.subFragmentStackTags}, ${state.baseFragmentTags}, ${state.currentBaseFragment}"
        )

    }

    private class SavedState : BaseSavedState {
        var currentBaseFragment: Int = 0
        var activeContainerOrdinal: Int = 0
        var baseFragmentTags: List<String>? = null
        var subFragmentStackTags: List<List<String>>? = null

        constructor(superState: Parcelable?) : super(superState)

        private constructor(parcel: Parcel) : super(parcel) {
            currentBaseFragment = parcel.readInt()
            activeContainerOrdinal = parcel.readInt()
            baseFragmentTags = mutableListOf<String>().apply {
                val size = parcel.readInt()
                repeat(size) {
                    add(parcel.readString() ?: "")
                }
            }
            subFragmentStackTags = mutableListOf<List<String>>().apply {
                val size = parcel.readInt()
                repeat(size) {
                    val innerSize = parcel.readInt()
                    val innerList = mutableListOf<String>()
                    repeat(innerSize) {
                        innerList.add(parcel.readString() ?: "")
                    }
                    add(innerList)
                }
            }
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(currentBaseFragment)
            out.writeInt(activeContainerOrdinal)
            out.writeInt(baseFragmentTags?.size ?: 0)
            baseFragmentTags?.forEach {
                out.writeString(it)
            }
            out.writeInt(subFragmentStackTags?.size ?: 0)
            subFragmentStackTags?.forEach { list ->
                out.writeInt(list.size)
                list.forEach { out.writeString(it) }
            }
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel) = SavedState(parcel)
            override fun newArray(size: Int) = arrayOfNulls<SavedState>(size)
        }
    }

    private var addValueAnimator: ValueAnimator? = null

    /**
     * Should be called within base fragments in order
     * to create nested sub fragments.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun addFragmentToCurrentStack(
        fragment: Fragment
    ) {
        if (addValueAnimator?.isRunning == true) return
        // State 1: This sub-fragment is being added to
        // DEFAULT_CONTAINER.
        //
        // Start:  [SubFragment(E)], [BaseFragment]
        // StartC: [DEFAULT_CONTAINER]
        //
        // End:    [SubFragment(O)]
        // EndC:   [APPEND_CONTAINER]
        //
        // State 2: This sub-fragment is being added to
        // APPEND_CONTAINER.
        //
        // Start:  [SubFragment(O)]
        // StartC: [APPEND_CONTAINER]
        //
        // End:    [SubFragment(E)]
        // EndC:   [DEFAULT_CONTAINER]
        val fm = fragmentManager ?: return

        val targetStackList = subFragmentStack[currentBaseFragment]
        val currentFragment = if (targetStackList.isEmpty())
            baseFragments[currentBaseFragment]
        else
            targetStackList.last()

        val (startContainer, endContainer, nextContainerType) =
            if (activeContainer == ContainerType.DEFAULT_CONTAINER) {
                Triple(containerDefault, containerAppend, ContainerType.APPEND_CONTAINER)
            } else {
                Triple(containerAppend, containerDefault, ContainerType.DEFAULT_CONTAINER)
            }

        // Make end container ready to show up.
        endContainer.translationX = width.toFloat()
        endContainer.elevation = 10F.dpToPx(context)
        endContainer.setBackgroundColor(surfaceColor)

        // Make sure to record target fragment.
        targetStackList.add(fragment)

        fm.beginTransaction()
            .add(endContainer.id, fragment, Uuid.random().toString())
            .runOnContentLoaded(fragment) {
                addAnimation(
                    startContainer,
                    endContainer,
                    currentFragment,
                    fm,
                    nextContainerType
                )
            }
            .commit()
    }

    private fun addAnimation(
        startContainer: View,
        endContainer: View,
        currentFragment: Fragment,
        fm: FragmentManager,
        nextContainerType: ContainerType
    ) {
        addValueAnimator?.cancel()
        addValueAnimator = null

        addValueAnimator = AnimationUtils.createValAnimator(
            width.toFloat(),
            0F,
            doOnEnd = {
                // Reset end container.
                endContainer.elevation = 0F
                endContainer.setBackgroundColor(0)

                // Hide container under.
                fm.beginTransaction()
                    .hide(currentFragment)
                    .commit()

                startContainer.translationX = width.toFloat()
                activeContainer = nextContainerType

                addValueAnimator = null
            }
        ) {
            startContainer.translationX = -(width.toFloat() - it) / 3F
            endContainer.translationX = it
        }
    }

    /**
     * Should be called by navigation view or similar
     * views that handles base pages.
     *
     * This method should only use fade in / out animations.
     * For adding sub-fragments, check [addFragmentToCurrentStack].
     */
    fun switchBaseFragment(
        newFragmentIndex: Int
    ) {
        hideScrim()
        if (currentAnimator?.isRunning == true) {
            currentAnimator?.doOnEnd {
                switchBaseFragment(newFragmentIndex)
            }
            return
        }
        // State 1: Need to pop stacks till its empty.
        //
        // Start:  [Any]
        // StartC: [Any]
        //
        // End:    [BaseFragment]
        // EndC:   [DEFAULT_CONTAINER]
        if (newFragmentIndex == currentBaseFragment) {
            popBackTargetStack(newFragmentIndex)
            return
        }

        // Get start / end fragment, its type and the current
        // container status so we can decide states easier later.
        val startFragmentStack = subFragmentStack[currentBaseFragment]
        val startFragment =
            if (startFragmentStack.isEmpty())
                baseFragments[currentBaseFragment]
            else
                startFragmentStack.last()
        val startFragmentType =
            if (startFragmentStack.isEmpty())
                FragmentType.BASE_FRAGMENT
            else if (startFragmentStack.size % 2 == 0)
                FragmentType.SUB_FRAGMENT_E
            else
                FragmentType.SUB_FRAGMENT_O
        val startContainer = activeContainer
        val startContainerEntity = getContainer(startContainer)

        val endFragmentStack = subFragmentStack[newFragmentIndex]
        val endFragment =
            if (endFragmentStack.isEmpty())
                baseFragments[newFragmentIndex]
            else
                endFragmentStack.last()
        val endFragmentType =
            if (endFragmentStack.isEmpty())
                FragmentType.BASE_FRAGMENT
            else if (endFragmentStack.size % 2 == 0)
                FragmentType.SUB_FRAGMENT_E
            else
                FragmentType.SUB_FRAGMENT_O

        // Now we will check what the end state will be.
        val state =
            if (
            // State 2
                (startFragmentType == FragmentType.BASE_FRAGMENT || startFragmentType == FragmentType.SUB_FRAGMENT_E) &&
                (startContainer == ContainerType.DEFAULT_CONTAINER) &&
                (endFragmentType == FragmentType.BASE_FRAGMENT || endFragmentType == FragmentType.SUB_FRAGMENT_E)
            ) {
                2
            } else if (
            // State 3
                (startFragmentType == FragmentType.SUB_FRAGMENT_O) &&
                (startContainer == ContainerType.APPEND_CONTAINER) &&
                (endFragmentType == FragmentType.BASE_FRAGMENT || endFragmentType == FragmentType.SUB_FRAGMENT_E)
            ) {
                3
            } else if (
            // State 4
                (startFragmentType == FragmentType.BASE_FRAGMENT || startFragmentType == FragmentType.SUB_FRAGMENT_E) &&
                (startContainer == ContainerType.DEFAULT_CONTAINER)
            ) {
                4
            } else if (
            // State 5
                (startFragmentType == FragmentType.SUB_FRAGMENT_O) &&
                (startContainer == ContainerType.APPEND_CONTAINER)
            ) {
                5
            } else {
                throw IllegalArgumentException("Invalid end container type! StartFragment: $startFragmentType, StartContainer: $startContainer")
            }

        // Now with our state, we can calculate end containers.
        val endContainer =
            if (state == 2 || state == 3)
                ContainerType.DEFAULT_CONTAINER
            else
                ContainerType.APPEND_CONTAINER
        val endContainerEntity =
            getContainer(endContainer)

        // Set up pointers.
        val fm = (fragmentManager ?: return)

        when (state) {
            // State 2: Need to transport to another base page without
            // the need to alter the state, that is to say active container
            // is DEFAULT_CONTAINER, and target sub-fragment, is an even
            // index in target fragment stack, or the target is another
            // base Fragment.
            //
            // Start:  [BaseFragment], [SubFragment(E)]
            // StartC: [DEFAULT_CONTAINER]
            //
            // End:    [BaseFragment], [SubFragment(E)]
            // EndC:   [DEFAULT_CONTAINER]
            //
            // State 5: Need to transport to another page without
            // the need to alter the state. That is to say active
            // container is APPEND_CONTAINER, and the target sub-
            // fragment is an odd index in target fragment stack.
            //
            // Start:  [SubFragment(O)]
            // StartC: [APPEND_CONTAINER]
            //
            // End:    [SubFragment(O)]
            // EndC:   [APPEND_CONTAINER]
            2, 5 -> {
                transactionShrink()
                fm.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .show(endFragment)
                    .hide(startFragment)
                    .runOnCommit {
                        // Let's update our status.
                        activeContainer = endContainer
                        currentBaseFragment = newFragmentIndex
                    }
                    .commit()
            }


            // State 3: Need to transport to another base page with
            // the need to alter the state to DEFAULT_CONTAINER. That
            // is to say active container is APPEND_CONTAINER, and target
            // sub-fragment is an even index in target fragment stack, or
            // target is another base Fragment.
            //
            // Start:  [SubFragment(O)]
            // StartC: [APPEND_CONTAINER]
            //
            // End:    [BaseFragment], [SubFragment(E)]
            // EndC:   [DEFAULT_CONTAINER]
            //
            // State 4: Need to transport to another page with the
            // need to alter the state to APPEND_CONTAINER. That is to
            // say active container is DEFAULT_CONTAINER, and the target
            // sub-fragment is an odd index in target fragment stack.
            //
            // Start:  [BaseFragment], [SubFragment(E)]
            // StartC: [DEFAULT_CONTAINER]
            //
            // End:    [SubFragment(O)]
            // EndC:   [APPEND_CONTAINER]
            3, 4 -> {
                // Let's show our end container.
                endContainerEntity.translationX = 0F
                endContainerEntity.bringToFront()

                transactionShrink()
                fm.beginTransaction()
                    .show(endFragment)
                    .runOnCommit {
                        // Let's hide our start container after animation ended.
                        startContainerEntity.startAnimation(fadeOutAnimation.doOnEnd {
                            // Now let's hide start fragment.
                            fm.beginTransaction()
                                .hide(startFragment)
                                .commit()
                        })
                        endContainerEntity.startAnimation(fadeInAnimation)

                        // Let's update our status.
                        activeContainer = endContainer
                        currentBaseFragment = newFragmentIndex
                    }
                    .commit()
            }
        }

    }

    fun popBackTargetStack(index: Int) {

    }

    private val linearInterpolator = LinearInterpolator()

    private fun transactionShrink() {
        AnimationUtils.createValAnimator<Float>(
            1F,
            0.99F,
            interpolator = linearInterpolator,
            duration = 100,
            doOnEnd = { transactionGrow() }
        ) {
            this.scaleX = it
            this.scaleY = it
        }
    }

    private fun transactionGrow() {
        AnimationUtils.createValAnimator<Float>(
            0.99F,
            1F,
            interpolator = linearInterpolator,
            duration = 100
        ) {
            this.scaleX = it
            this.scaleY = it
        }
    }

    val containerStatus: ContainerType get() = activeContainer

    val currentStackSize: Int get() = subFragmentStack[currentBaseFragment].size

    private fun getContainer(containerType: ContainerType): FrameLayout =
        when (containerType) {
            ContainerType.DEFAULT_CONTAINER -> containerDefault
            ContainerType.APPEND_CONTAINER -> containerAppend
        }

    fun canPopBack(): Boolean = subFragmentStack[currentBaseFragment].isNotEmpty()

    private fun showScrim(progress: Float) {
        if (scrimView.visibility != View.VISIBLE) {
            scrimView.visibility = View.VISIBLE
        }
        scrimView.alpha = lerp(maxScrimAlpha, 0f, progress)
    }

    private fun hideScrim() {
        scrimView.alpha = 0f
        scrimView.visibility = View.GONE
    }

    private fun updateScrimFromTranslation(translationX: Float) {
        val progress = if (width > 0) (translationX / width).coerceIn(0f, 1f) else 0f
        showScrim(progress)
    }

    private fun applyTranslation(translationX: Float) {
        currentContainer?.translationX = translationX
        targetContainer?.translationX = -(width.toFloat() - translationX) / 3F
        updateScrimFromTranslation(translationX)
    }

    private fun prepareBackGesture(): Boolean {
        if (animationLoadState == LoadState.ALREADY_LOADED) return true
        if (subFragmentStack[currentBaseFragment].isEmpty()) return false
        if (addValueAnimator?.isRunning == true || removeValueAnimator?.isRunning == true) return false

        currentAnimator?.cancel()
        currentAnimator = null

        val list = subFragmentStack[currentBaseFragment]
        targetFragment = if (list.size - 2 >= 0) list[list.size - 2] else baseFragments[currentBaseFragment]
        targetFragmentIndex = if (list.size - 2 >= 0) list.size - 2 else -1
        currentFragment = list[list.size - 1]
        currentFragmentIndex = list.size - 1

        targetContainer = if (activeContainer == ContainerType.DEFAULT_CONTAINER) containerAppend else containerDefault
        currentContainer = getContainer(activeContainer)

        currentContainer?.bringToFront()
        targetContainer?.translationX = 0F

        val fm = fragmentManager ?: return false
        fm.beginTransaction()
            .show(targetFragment!!)
            .commit()

        currentContainer?.translationX = 0F
        currentContainer?.elevation = 10F.dpToPx(context)
        currentContainer?.setBackgroundColor(surfaceColor)

        animationLoadState = LoadState.ALREADY_LOADED
        showScrim(0f)
        return true
    }

    private fun animateBackTo(
        targetTranslation: Float,
        finish: Boolean,
        duration: Long = AnimationUtils.FAST_DURATION,
        interpolator: TimeInterpolator = AnimationUtils.fastOutSlowInInterpolator
    ) {
        currentAnimator?.cancel()
        val startTranslation = currentContainer?.translationX ?: return
        currentAnimator = AnimationUtils.createValAnimator<Float>(
            startTranslation,
            targetTranslation,
            duration = duration,
            interpolator = interpolator,
            doOnEnd = {
                isAnimationProperlyFinished = finish
                onAnimationFinished()
                currentAnimator = null
            }
        ) { value ->
            applyTranslation(value)
        }
    }

    fun startPredictiveBack(): Boolean {
        predictiveBackActive = prepareBackGesture()
        return predictiveBackActive
    }

    fun updatePredictiveBack(progress: Float) {
        if (!predictiveBackActive && !prepareBackGesture()) return
        val clamped = progress.coerceIn(0f, 1f)
        applyTranslation(width.toFloat() * clamped)
    }

    fun cancelPredictiveBack() {
        if (animationLoadState != LoadState.ALREADY_LOADED) {
            predictiveBackActive = false
            return
        }
        animateBackTo(0f, finish = false)
    }

    fun commitPredictiveBack(): Boolean {
        if (!predictiveBackActive && !prepareBackGesture()) return false
        animateBackTo(width.toFloat(), finish = true)
        return true
    }

    private var penultimateMotionX = 0F
    private var lastMotionX = 0F

    private var animationLoadState: LoadState = LoadState.DO_NOT_LOAD
    private var targetContainer: FrameLayout? = null
    private var targetFragment: Fragment? = null
    private var targetFragmentIndex = 0
    private var currentContainer: FrameLayout? = null
    private var currentFragment: Fragment? = null
    private var currentFragmentIndex = 0
    private var isAnimationProperlyFinished: Boolean = false

    override fun onDown(e: MotionEvent): Boolean {
        penultimateMotionX = e.x
        lastMotionX = e.x
        if (subFragmentStack[currentBaseFragment].isNotEmpty() &&
            animationLoadState == LoadState.DO_NOT_LOAD &&
            addValueAnimator?.isRunning != true &&
            removeValueAnimator?.isRunning != true &&
            currentAnimator?.isRunning != true
        ) {
            animationLoadState = LoadState.SHOULD_LOAD
        }
        return true
    }

    private var currentAnimator: ValueAnimator? = null

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (animationLoadState == LoadState.ALREADY_LOADED) {
            val minVelocity = minFlingVelocity.toFloat()
            val absVelocity = kotlin.math.abs(velocityX).coerceAtLeast(minVelocity)
            val supposedDuration =
                ((width / absVelocity) * 1000)
                    .toLong()
                    .coerceIn(
                        MINIMUM_ANIMATION_TIME,
                        MAXIMUM_ANIMATION_TIME
                    )

            val shouldFinish = velocityX > minVelocity
            val target = if (shouldFinish) width.toFloat() else 0F
            animateBackTo(target, finish = shouldFinish, duration = supposedDuration)
        }
        return true
    }

    override fun onLongPress(e: MotionEvent) {}

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        penultimateMotionX = lastMotionX
        lastMotionX = e2.x

        if (animationLoadState == LoadState.SHOULD_LOAD) {
            if (!prepareBackGesture()) return true
        }

        if (animationLoadState == LoadState.ALREADY_LOADED) {
            val distance = penultimateMotionX - lastMotionX
            val proposed = (currentContainer!!.translationX - distance)
            val maxOverscroll = width.toFloat() * maxOverscrollFraction
            val translation = when {
                proposed < 0F -> (proposed * overscrollResistance).coerceAtLeast(-maxOverscroll)
                else -> proposed.coerceAtMost(width.toFloat())
            }
            applyTranslation(translation)
            return true
        }
        return true
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean = true

    private fun onAnimationFinished() {
        if (animationLoadState == LoadState.ALREADY_LOADED &&
            currentFragment != null && targetFragment != null
        ) {
            targetContainer?.translationX = 0F

            val fm = fragmentManager ?: return

            fm.beginTransaction().apply {
                if (isAnimationProperlyFinished)
                    remove(currentFragment!!)
                else if (subFragmentStack[currentBaseFragment].size > 1)
                    hide(targetFragment!!)
                else
                    return@apply
            }
                .commit()

            // Also remove the fragment from our stack, log everything we needed
            if (isAnimationProperlyFinished) {
                subFragmentStack[currentBaseFragment].removeAt(subFragmentStack[currentBaseFragment].lastIndex)
                activeContainer =
                    if (activeContainer == ContainerType.DEFAULT_CONTAINER) ContainerType.APPEND_CONTAINER else ContainerType.DEFAULT_CONTAINER
                // Reset current container.
                currentContainer?.translationX = width.toFloat()
                currentContainer?.elevation = 0F
                currentContainer?.setBackgroundColor(0)

                targetContainer?.translationX = 0F
            }

            targetContainer = null
            targetFragment = null
            currentContainer = null
            currentFragment = null
            isAnimationProperlyFinished = false
            animationLoadState = LoadState.DO_NOT_LOAD
            predictiveBackActive = false
            hideScrim()
        }
    }

    private fun onUp(e: MotionEvent) {
        if (animationLoadState == LoadState.ALREADY_LOADED) {
            val progress = if (width > 0) (currentContainer!!.translationX / width).coerceIn(0f, 1f) else 0f
            val shouldFinish = progress > backCommitThreshold
            val target = if (shouldFinish) width.toFloat() else 0F
            animateBackTo(target, finish = shouldFinish)
        }
    }

    private var removeValueAnimator: ValueAnimator? = null

    fun popBackTopFragmentIfExists(): Boolean {
        if (removeValueAnimator?.isRunning == true) return false

        if (!prepareBackGesture()) return false

        removeValueAnimator?.cancel()
        removeValueAnimator = AnimationUtils.createValAnimator<Float>(
            currentContainer!!.translationX,
            width.toFloat(),
            doOnEnd = {
                isAnimationProperlyFinished = true
                onAnimationFinished()
                removeValueAnimator = null
            }
        ) {
            applyTranslation(it)
        }

        return true
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (predictiveBackActive) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                isEdgeSwipeActive = false
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            isEdgeSwipeActive = false
        }
        return if (gestureDetector.onTouchEvent(event)) {
            true
        } else if (event.action == MotionEvent.ACTION_UP) {
            onUp(event)
            true
        } else if (event.action == MotionEvent.ACTION_CANCEL) {
            if (animationLoadState == LoadState.ALREADY_LOADED) {
                animateBackTo(0f, finish = false)
                true
            } else {
                super.onTouchEvent(event)
            }
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (predictiveBackActive) {
            return false
        }
        if (subFragmentStack[currentBaseFragment].isEmpty()) {
            return super.onInterceptTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeStartX = ev.x
                edgeStartY = ev.y
                isEdgeSwipeActive = false
                edgeDownEvent?.recycle()
                edgeDownEvent = MotionEvent.obtain(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - edgeStartX
                val dy = ev.y - edgeStartY
                if (isEdgeSwipeActive) return true
                if (!isEdgeSwipeActive) {
                    if (kotlin.math.abs(dy) > touchSlop && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                        edgeDownEvent?.recycle()
                        edgeDownEvent = null
                        return false
                    }
                    if (kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        isEdgeSwipeActive = true
                        edgeDownEvent?.let {
                            gestureDetector.onTouchEvent(it)
                            it.recycle()
                            edgeDownEvent = null
                        }
                        return true
                    }
                    return false
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isEdgeSwipeActive = false
                edgeDownEvent?.recycle()
                edgeDownEvent = null
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    enum class ContainerType {
        DEFAULT_CONTAINER, APPEND_CONTAINER
    }

    enum class FragmentType {
        BASE_FRAGMENT, SUB_FRAGMENT_O, SUB_FRAGMENT_E
    }

    enum class LoadState {
        DO_NOT_LOAD, SHOULD_LOAD, ALREADY_LOADED
    }

    companion object {
        private const val TAG = "FragmentSwitcherView"

        const val MINIMUM_ANIMATION_TIME = 220L
        const val MAXIMUM_ANIMATION_TIME = 320L
    }

}
