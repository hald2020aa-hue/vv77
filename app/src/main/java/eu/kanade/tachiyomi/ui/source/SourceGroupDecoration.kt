package eu.kanade.tachiyomi.ui.source

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.RectF
import androidx.recyclerview.widget.RecyclerView

/** Draw only behind the visible, real adapter groups; no synthetic sources or rows. */
internal class SourceGroupDecoration(private val currentAdapter: () -> SourceAdapter?) : RecyclerView.ItemDecoration() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18, 16, 42) }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(104, 70, 170)
        style = Paint.Style.STROKE
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = currentAdapter() ?: return
        val density = parent.resources.displayMetrics.density
        val groups = linkedMapOf<String, RectF>()
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || position >= adapter.itemCount) continue
            val code = when (val item = adapter.getItem(position)) {
                is LangItem -> item.code
                is SourceItem -> item.header?.code
                else -> null
            } ?: continue
            val top = child.top + child.translationY
            val bottom = child.bottom + child.translationY
            val existing = groups[code]
            if (existing == null) {
                groups[code] = RectF(parent.paddingLeft + density, top, (parent.width - parent.paddingRight).toFloat() - density, bottom)
            } else {
                existing.top = minOf(existing.top, top)
                existing.bottom = maxOf(existing.bottom, bottom)
            }
        }
        rim.strokeWidth = density
        for (rect in groups.values) {
            if (rect.width() <= 0f || rect.height() <= 4f * density) continue
            rect.inset(0f, 2f * density)
            rim.shader = LinearGradient(rect.left, rect.top, rect.right, rect.top,
                intArrayOf(Color.rgb(25, 230, 255), Color.rgb(161, 75, 255), Color.rgb(255, 47, 208)), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, 14f * density, 14f * density, fill)
            c.drawRoundRect(rect, 14f * density, 14f * density, rim)
        }
    }
}
