package uk.akane.cupertino.widget.popup

import android.graphics.RectF
import android.view.View

interface PopupMenuHost {
    val popupHostView: View

    fun showPopupMenu(
        entries: PopupHelper.PopupEntries,
        locationX: Int,
        locationY: Int,
        anchorFromTop: Boolean = false,
        backgroundView: View? = null,
        onDismiss: (() -> Unit)? = null,
        onEntryClick: ((PopupHelper.PopupEntry) -> Unit)? = null
    )
}

fun PopupMenuHost.showPopupMenuAtScreen(
    entries: PopupHelper.PopupEntries,
    screenX: Int,
    screenY: Int,
    anchorFromTop: Boolean = false,
    backgroundView: View? = null,
    onDismiss: (() -> Unit)? = null,
    onEntryClick: ((PopupHelper.PopupEntry) -> Unit)? = null
) {
    val hostLocation = IntArray(2)
    popupHostView.getLocationOnScreen(hostLocation)

    val localX = screenX - hostLocation[0]
    val localY = screenY - hostLocation[1]

    showPopupMenu(entries, localX, localY, anchorFromTop, backgroundView, onDismiss, onEntryClick)
}

fun PopupMenuHost.showPopupMenuFromAnchor(
    entries: PopupHelper.PopupEntries,
    anchorView: View,
    showBelow: Boolean = false,
    alignToRight: Boolean = true,
    anchorOffsetX: Int = 0,
    anchorOffsetY: Int = 0,
    belowGapPx: Int = 0,
    backgroundView: View? = null,
    onDismiss: (() -> Unit)? = null,
    onEntryClick: ((PopupHelper.PopupEntry) -> Unit)? = null
) {
    val anchorLocation = IntArray(2)
    val hostLocation = IntArray(2)
    anchorView.getLocationOnScreen(anchorLocation)
    popupHostView.getLocationOnScreen(hostLocation)

    val localLeft = anchorLocation[0] - hostLocation[0]
    val localTop = anchorLocation[1] - hostLocation[1]
    val locationX = localLeft + anchorOffsetX + if (alignToRight) anchorView.width else 0
    val locationY = localTop + anchorOffsetY + if (showBelow) anchorView.height + belowGapPx else 0

    showPopupMenu(entries, locationX, locationY, showBelow, backgroundView, onDismiss, onEntryClick)
}

fun PopupMenuHost.showPopupMenuFromAnchorRect(
    entries: PopupHelper.PopupEntries,
    anchorView: View,
    anchorRect: RectF,
    showBelow: Boolean = false,
    alignToRight: Boolean = true,
    anchorOffsetX: Int = 0,
    anchorOffsetY: Int = 0,
    belowGapPx: Int = 0,
    backgroundView: View? = null,
    onDismiss: (() -> Unit)? = null,
    onEntryClick: ((PopupHelper.PopupEntry) -> Unit)? = null
) {
    val anchorLocation = IntArray(2)
    val hostLocation = IntArray(2)
    anchorView.getLocationOnScreen(anchorLocation)
    popupHostView.getLocationOnScreen(hostLocation)

    val localLeft = anchorLocation[0] - hostLocation[0]
    val localTop = anchorLocation[1] - hostLocation[1]
    val anchorX = if (alignToRight) anchorRect.right else anchorRect.left
    val locationX = (localLeft + anchorX).toInt() + anchorOffsetX
    val baseY = localTop + anchorRect.top
    val locationY = (baseY + if (showBelow) anchorRect.height() + belowGapPx else 0f).toInt() +
        anchorOffsetY

    showPopupMenu(entries, locationX, locationY, showBelow, backgroundView, onDismiss, onEntryClick)
}
