package eu.kanade.tachiyomi.ui.source.plus

import android.os.Bundle
import androidx.core.os.bundleOf
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchPresenter

/** An opt-in search restricted to other enabled sources. Existing parsers and migration are unchanged. */
class PlusAlternativesController(bundle: Bundle) : GlobalSearchController(bundle.getString(KEY_QUERY), bundle = bundle) {
    constructor(query: String, excludedSourceId: Long) : this(bundleOf(KEY_QUERY to query, KEY_EXCLUDED to excludedSourceId))

    private val excludedSourceId: Long? = if (bundle.containsKey(KEY_EXCLUDED)) bundle.getLong(KEY_EXCLUDED) else null

    override val presenter: GlobalSearchPresenter = object : GlobalSearchPresenter(initialQuery) {
        override fun getEnabledSources(): List<CatalogueSource> =
            super.getEnabledSources().filterNot { it.id == excludedSourceId }
    }

    companion object {
        private const val KEY_QUERY = "mv_plus_query"
        private const val KEY_EXCLUDED = "mv_plus_excluded_source"
    }
}
