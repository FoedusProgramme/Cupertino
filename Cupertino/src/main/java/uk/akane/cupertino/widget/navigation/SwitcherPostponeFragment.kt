package uk.akane.cupertino.widget.navigation

import androidx.fragment.app.Fragment

abstract class SwitcherPostponeFragment : Fragment() {
    var isPostponed = false
        private set

    private val contentLoadedListeners = mutableListOf<() -> Unit>()

    fun postponeSwitcherAnimation() {
        isPostponed = true
    }

    fun addOnContentLoadedListener(listener: () -> Unit) {
        contentLoadedListeners += listener
    }

    fun notifyContentLoaded() {
        isPostponed = false
        contentLoadedListeners.forEach { it.invoke() }
        contentLoadedListeners.clear()
    }
}
