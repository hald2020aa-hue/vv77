package eu.kanade.tachiyomi.util.view

import android.content.Context
import android.os.Parcelable
import android.view.View
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback

/**
 * Shared-element callback that never lets a bad shared view crash the activity transition.
 *
 * Material's callback reads the shape of a shared view through `Shapeable.getShapeAppearanceModel()`.
 * A `MaterialButton` whose background was replaced (XML `android:background` or `setBackground`) throws
 * `IllegalStateException: Attempted to get ShapeAppearanceModel from a MaterialButton which has an
 * overwritten background`, which killed the app when opening the reader from a cover/chapter button.
 * Falling back to "no snapshot" only skips the morph animation for that one view.
 */
open class SafeContainerTransformCallback : MaterialContainerTransformSharedElementCallback() {
    override fun onCreateSnapshotView(context: Context?, snapshot: Parcelable?): View? =
        try {
            super.onCreateSnapshotView(context, snapshot)
        } catch (_: IllegalStateException) {
            null
        }
}
