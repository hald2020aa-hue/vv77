package eu.kanade.tachiyomi.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.MaterialShapeDrawable

/**
 * A MaterialButton that shows a neon gradient without ever replacing its background.
 *
 * Calling `setBackground()` on a MaterialButton marks its background as "overwritten", after which
 * `getShapeAppearanceModel()` throws `IllegalStateException`. Material's shared-element callback calls
 * that getter while opening the reader, which crashed the app on Android 16 for the "start reading"
 * button. This class keeps Material's own background (so the shape model stays valid), paints the
 * gradient and rim itself in [onDraw], and draws the press ripple in the view foreground.
 */
class NeonGradientButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
) : MaterialButton(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val density = resources.displayMetrics.density
    private val rimWidth = 1.5f * density

    private var gradientEnabled = false
    private var fillStart = Color.rgb(25, 200, 255)
    private var fillEnd = Color.rgb(61, 124, 255)
    private var rimStart = Color.argb(102, 25, 230, 255)
    private var rimEnd = Color.argb(102, 61, 124, 255)

    /**
     * Turns the gradient on. Colours default to the V22 cyan -> blue read button.
     * Safe to call repeatedly (the details header re-binds on every colour change).
     */
    fun applyNeonGradient(
        fillStart: Int = this.fillStart,
        fillEnd: Int = this.fillEnd,
        rimStart: Int = this.rimStart,
        rimEnd: Int = this.rimEnd,
    ) {
        this.fillStart = fillStart
        this.fillEnd = fillEnd
        this.rimStart = rimStart
        this.rimEnd = rimEnd
        gradientEnabled = true
        // Material keeps drawing its own (now invisible) background and ripple.
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        strokeWidth = 0
        rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
        foreground = RippleDrawable(
            ColorStateList.valueOf(Color.argb(102, 255, 255, 255)),
            null,
            MaterialShapeDrawable(shapeAppearanceModel),
        )
        updateShaders()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShaders()
    }

    override fun onDraw(canvas: Canvas) {
        if (gradientEnabled && width > 0 && height > 0) {
            val radius = (cornerRadius.takeIf { it > 0 } ?: (height / 2)).toFloat().coerceAtMost(height / 2f)
            val alpha = if (isEnabled) 255 else 97
            rimPaint.alpha = alpha
            fillPaint.alpha = alpha
            bounds.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(bounds, radius, radius, rimPaint)
            bounds.inset(rimWidth, rimWidth)
            canvas.drawRoundRect(bounds, radius - rimWidth, radius - rimWidth, fillPaint)
        }
        super.onDraw(canvas)
    }

    private fun updateShaders() {
        if (width <= 0) return
        val w = width.toFloat()
        fillPaint.shader = LinearGradient(0f, 0f, w, 0f, fillStart, fillEnd, Shader.TileMode.CLAMP)
        rimPaint.shader = LinearGradient(0f, 0f, w, 0f, rimStart, rimEnd, Shader.TileMode.CLAMP)
    }
}
