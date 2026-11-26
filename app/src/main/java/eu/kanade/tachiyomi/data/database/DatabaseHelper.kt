package eu.kanade.tachiyomi.data.database

import android.content.ContentValues
import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.pushtorefresh.storio.sqlite.impl.DefaultStorIOSQLite
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import eu.kanade.tachiyomi.data.database.mappers.CategoryTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.ChapterTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.HistoryTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.MangaCategoryTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.MangaTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.SearchMetadataTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.TrackTypeMapping
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.SearchMetadata
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.queries.CategoryQueries
import eu.kanade.tachiyomi.data.database.queries.ChapterQueries
import eu.kanade.tachiyomi.data.database.queries.HistoryQueries
import eu.kanade.tachiyomi.data.database.queries.MangaCategoryQueries
import eu.kanade.tachiyomi.data.database.queries.MangaQueries
import eu.kanade.tachiyomi.data.database.queries.SearchMetadataQueries
import eu.kanade.tachiyomi.data.database.queries.TrackQueries
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.download.StorageSeriesType
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

/**
 * This class provides operations to manage the database through its interfaces.
 */
open class DatabaseHelper(
    context: Context,
) : MangaQueries,
    ChapterQueries,
    TrackQueries,
    CategoryQueries,
    MangaCategoryQueries,
    HistoryQueries,
    SearchMetadataQueries {
    private val configuration =
        SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(DbOpenCallback.DATABASE_NAME)
            .callback(DbOpenCallback())
            .build()

    override val db =
        DefaultStorIOSQLite
            .builder()
            .sqliteOpenHelper(RequerySQLiteOpenHelperFactory().create(configuration))
            .addTypeMapping(Manga::class.java, MangaTypeMapping())
            .addTypeMapping(Chapter::class.java, ChapterTypeMapping())
            .addTypeMapping(Track::class.java, TrackTypeMapping())
            .addTypeMapping(Category::class.java, CategoryTypeMapping())
            .addTypeMapping(MangaCategory::class.java, MangaCategoryTypeMapping())
            .addTypeMapping(SearchMetadata::class.java, SearchMetadataTypeMapping())
            .addTypeMapping(History::class.java, HistoryTypeMapping())
            .build()

    inline fun inTransaction(block: () -> Unit) = db.inTransaction(block)

    inline fun <T> inTransactionReturn(block: () -> T): T = db.inTransactionReturn(block)

    /** The general manga resolver deliberately never writes this column. */
    fun setStorageManualType(id: Long, type: StorageSeriesType?, onlyIfUnset: Boolean = false): Int =
        inTransactionReturn {
            val where = "${MangaTable.COL_ID} = ?" +
                if (onlyIfUnset) " AND ${MangaTable.COL_STORAGE_MANUAL_TYPE} IS NULL" else ""
            val query = UpdateQuery.builder().table(MangaTable.TABLE).where(where).whereArgs(id).build()
            val values = ContentValues(1).apply {
                if (type == null) putNull(MangaTable.COL_STORAGE_MANUAL_TYPE)
                else put(MangaTable.COL_STORAGE_MANUAL_TYPE, type.name)
            }
            lowLevel().update(query, values)
        }

    fun lowLevel() = db.lowLevel()
}
