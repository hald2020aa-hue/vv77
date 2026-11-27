package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CommonViewEmptyBinding
import eu.kanade.tachiyomi.util.view.setVectorCompat

class EmptyView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : RelativeLayout(context, attrs) {
        private val binding: CommonViewEmptyBinding =
            CommonViewEmptyBinding.inflate(LayoutInflater.from(context), this, true)
        private val normalContentBackground = binding.root.background
        private val normalScaleType = binding.imageView.scaleType
        private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()
        private var illustrationTitle: TextView? = null

        /**
         * Hide the information view
         */
        fun hide() {
            this.isVisible = false
        }

        /**
         * Show the information view
         * @param textResource text of information view
         */
        fun show(
            @DrawableRes drawable: Int,
            @StringRes textResource: Int,
            actions: List<Action>? = null,
        ) {
            show(drawable, context.getString(textResource), actions)
        }

        /**
         * Show the information view
         * @param drawable icon of information view
         * @param textResource text of information view
         */
        fun show(
            @DrawableRes drawable: Int,
            message: String,
            actions: List<Action>? = null,
        ) {
            val illustration = context.resources.getBoolean(R.bool.v4_neon_mode) &&
                (drawable == R.drawable.mv_neon_library || drawable == R.drawable.mv_neon_updates)
            if (illustration) {
                binding.root.setBackgroundResource(if (drawable == R.drawable.mv_neon_library) R.drawable.mv_neon_empty_card else android.R.color.transparent)
                binding.imageView.imageTintList = null
                binding.imageView.setImageResource(drawable)
                binding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            } else {
                binding.root.background = normalContentBackground
                binding.imageView.scaleType = normalScaleType
                binding.imageView.setVectorCompat(drawable, android.R.attr.textColorHint)
            }
            illustrationTitle?.let { (it.parent as? ViewGroup)?.removeView(it) }
            illustrationTitle = null
            val body = binding.textLabel.parent as? LinearLayout
            if (illustration && body != null && (drawable == R.drawable.mv_neon_library || actions?.size == 3)) {
                val title = TextView(context).apply {
                    text = context.getString(if (drawable == R.drawable.mv_neon_library) R.string.mv_neon_empty_library_title else R.string.mv_neon_empty_updates_title)
                    textSize = 21f
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, dp(4), 0, dp(4))
                }
                body.addView(title, body.indexOfChild(binding.textLabel), LinearLayout.LayoutParams(-1, -2))
                illustrationTitle = title
            }
            binding.textLabel.text = message

            binding.actionsContainer.removeAllViews()
            binding.actionsContainer.isVisible = !actions.isNullOrEmpty()
            if (!actions.isNullOrEmpty()) {
                val useRow = illustration && drawable == R.drawable.mv_neon_updates && actions.size == 3 && resources.configuration.screenWidthDp >= 360 && resources.configuration.fontScale <= 1.3f
                val secondaryRow = if (useRow) LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                } else null
                actions.forEachIndexed { index, action ->
                    val primary = illustration && drawable == R.drawable.mv_neon_updates && index == 0
                    val button =
                        (if (primary) NeonGradientButton(context) else inflate(context, R.layout.material_text_button, null) as MaterialButton)
                            .apply {
                                setText(action.resId)
                                setOnClickListener(action.listener)
                                if (illustration) {
                                    setTextColor(Color.WHITE)
                                    minHeight = dp(48)
                                    if (drawable == R.drawable.mv_neon_updates && index == 0) {
                                        (this as NeonGradientButton).applyNeonGradient(fillStart = Color.rgb(0, 174, 208), fillEnd = Color.rgb(151, 36, 229))
                                        setIconResource(R.drawable.ic_refresh_24dp)
                                    } else {
                                        setIconResource(if (drawable == R.drawable.mv_neon_library || action.resId == R.string.library) R.drawable.ic_book_24dp else R.drawable.ic_browse_outline_24dp)
                                    }
                                }
                            }
                    if (secondaryRow != null && index > 0) {
                        secondaryRow.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
                    } else binding.actionsContainer.addView(button)
                    if (context.resources.configuration.screenHeightDp < 600 && !(useRow && index > 0)) {
                        button.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                        button.updateLayoutParams<MarginLayoutParams> {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }
                }
                if (secondaryRow != null) binding.actionsContainer.addView(secondaryRow, LinearLayout.LayoutParams(-1, -2))
            }

            this.isVisible = true
        }

        data class Action(
            @StringRes val resId: Int,
            val listener: OnClickListener,
        )
    }
