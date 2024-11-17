package uk.akane.cupertinodemo

import android.content.Context

@Suppress("NOTHING_TO_INLINE")
inline fun Int.dpToPx(context: Context): Int =
    (this.toFloat() * context.resources.displayMetrics.density).toInt()

@Suppress("NOTHING_TO_INLINE")
inline fun Float.dpToPx(context: Context): Float =
    (this * context.resources.displayMetrics.density)