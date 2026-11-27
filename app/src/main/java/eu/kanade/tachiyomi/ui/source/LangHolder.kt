package eu.kanade.tachiyomi.ui.source

import android.view.View
import eu.kanade.tachiyomi.R
import android.content.res.ColorStateList
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.SourceHeaderItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.LocaleHelper

class LangHolder(
    view: View,
    adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
) : BaseFlexibleViewHolder(view, adapter) {
    fun bind(item: LangItem) {
        val binding = SourceHeaderItemBinding.bind(itemView)
        binding.title.text = LocaleHelper.getSourceDisplayName(item.code, itemView.context)
        if (itemView.resources.getBoolean(R.bool.v4_neon_mode)) {
            val icon = when (item.code) {
                SourcePresenter.PINNED_KEY -> R.drawable.ic_star_24dp
                SourcePresenter.LAST_USED_KEY -> R.drawable.ic_history_24dp
                else -> R.drawable.ic_language_24dp
            }
            binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(binding.title, ColorStateList.valueOf(android.graphics.Color.rgb(25, 230, 255)))
        }
    }
}
