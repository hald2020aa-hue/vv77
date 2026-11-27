package eu.kanade.tachiyomi.data.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notificationManager
import uy.kohesive.injekt.injectLazy
import java.text.NumberFormat
import java.util.regex.Pattern

/**
 * DownloadNotifier is used to show notifications when downloading one or multiple chapters.
 *
 * @param context context of application
 */
internal class DownloadNotifier(
    private val context: Context,
) {
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Notification builder.
     */
    private val notification by lazy {
        NotificationCompat
            .Builder(context, Notifications.CHANNEL_DOWNLOADER)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
    }

    /**
     * Status of download. Used for correct notification icon.
     */
    private var isDownloading = false

    /**
     * Updated when error is thrown
     */
    var errorThrown = false

    /**
     * Shows a notification from this builder.
     *
     * @param id the id of the notification.
     */
    private fun NotificationCompat.Builder.show(id: Int = Notifications.ID_DOWNLOAD_CHAPTER) {
        context.notificationManager.notify(id, build())
    }

    /**
     * Dismiss the downloader's notification. Downloader error notifications use a different id, so
     * those can only be dismissed by the user.
     */
    fun dismiss() {
        context.notificationManager.cancel(Notifications.ID_DOWNLOAD_CHAPTER)
    }

    private fun downloadTitle(download: Download, localized: Context): String {
        val title = download.manga.title
        val name = download.chapter.preferredChapterName(localized, download.manga, preferences)
        val prefix = "^${Pattern.quote(title)}[\\s]*[-]*[\\s]*".toRegex(RegexOption.IGNORE_CASE)
        val chapter = name.replaceFirst(prefix, "").ifBlank { name }
        return "$title - $chapter"
    }

    /** The expanded neon card owns its progress and controls; keep the system header native. */
    private fun NotificationCompat.Builder.decorateProgress(
        localized: Context,
        title: String,
        status: String,
        total: Int,
        completed: Int,
        download: Download?,
        hidden: Boolean,
        paused: Boolean = false,
    ) {
        setStyle(null)
        setCustomBigContentView(null)
        setCategory(NotificationCompat.CATEGORY_PROGRESS)
        setOnlyAlertOnce(true)
        if (!localized.resources.getBoolean(R.bool.v4_neon_mode)) return
        // Avoid the second, system-drawn progress bar and white action row.
        setProgress(0, 0, false)
        clearActions()
        val views = RemoteViews(localized.packageName, R.layout.mv_download_notification)
        views.setTextViewText(R.id.mv_notification_title, title)
        views.setTextViewText(R.id.mv_notification_status, status)
        val maximum = total.coerceAtLeast(1)
        val progress = completed.coerceIn(0, maximum)
        val percent = if (total > 0) {
            NumberFormat.getPercentInstance(localized.resources.configuration.locales[0])
                .format(progress.toDouble() / total)
        } else {
            localized.getString(R.string.mv_notification_waiting)
        }
        views.setProgressBar(R.id.mv_notification_progress, maximum, progress, total == 0 && !paused)
        views.setTextViewText(R.id.mv_notification_percent, percent)
        views.setViewVisibility(R.id.mv_notification_progress_row, if (paused) android.view.View.GONE else android.view.View.VISIBLE)
        if (!paused && total > 0 && !hidden) {
            setContentText(localized.getString(R.string.mv_notification_compact_status, status, percent))
        }
        val stats = if (hidden) null else download?.transferStats?.value
        val bytes = stats?.let {
            val received = Formatter.formatShortFileSize(localized, it.receivedBytes.coerceAtLeast(0L))
            val known = it.knownChapterBytes?.let { size -> Formatter.formatShortFileSize(localized, size) }
            if (known == null) localized.getString(R.string.mv_notification_received, received)
            else localized.getString(R.string.mv_notification_sizes, received, known)
        }.orEmpty()
        val speed = stats?.speedBytesPerSecond?.takeIf { it > 0L }?.let {
            localized.getString(R.string.mv_notification_speed, Formatter.formatShortFileSize(localized, it))
        }.orEmpty()
        views.setTextViewText(R.id.mv_notification_bytes, bytes)
        views.setTextViewText(R.id.mv_notification_speed, speed)
        views.setViewVisibility(R.id.mv_notification_bytes, if (bytes.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
        views.setViewVisibility(R.id.mv_notification_speed, if (speed.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
        views.setViewVisibility(R.id.mv_notification_stats, if (bytes.isEmpty() && speed.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
        val actionLabel = localized.getString(if (paused) R.string.resume else R.string.mv_notification_pause)
        views.setTextViewText(R.id.mv_notification_primary_label, actionLabel)
        views.setContentDescription(R.id.mv_notification_primary, actionLabel)
        views.setImageViewResource(R.id.mv_notification_primary_icon, if (paused) R.drawable.mv_notification_ic_play else R.drawable.mv_notification_ic_pause)
        views.setOnClickPendingIntent(
            R.id.mv_notification_primary,
            if (paused) NotificationReceiver.resumeDownloadsPendingBroadcast(localized)
            else NotificationReceiver.pauseDownloadsPendingBroadcast(localized),
        )
        // Manage/cancel opens the existing queue, rather than silently deleting all queued files.
        views.setTextViewText(R.id.mv_notification_manage_label, localized.getString(R.string.mv_notification_manage))
        views.setContentDescription(R.id.mv_notification_manage, localized.getString(R.string.mv_notification_manage))
        views.setOnClickPendingIntent(R.id.mv_notification_manage, NotificationHandler.openDownloadManagerPendingActivity(localized))
        setStyle(NotificationCompat.DecoratedCustomViewStyle())
        setCustomBigContentView(views)
        color = android.graphics.Color.rgb(25, 230, 255)
    }

    private fun NotificationCompat.Builder.clearProgressDecoration() {
        setCustomBigContentView(null)
        setStyle(null)
        setCategory(null)
        setOnlyAlertOnce(false)
    }

    @Synchronized
    fun setPlaceholder(download: Download?): NotificationCompat.Builder {
        val context = context.localeContext
        with(notification) {
            // Check if first call.
            if (!isDownloading) {
                setSmallIcon(android.R.drawable.stat_sys_download)
                setAutoCancel(false)
                clearActions()
                setOngoing(true)
                // Open download manager when clicked
                setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
                color = ContextCompat.getColor(context, R.color.primaryTachiyomi)
                isDownloading = true
                // Pause action
                addAction(
                    R.drawable.ic_pause_24dp,
                    context.getString(R.string.pause),
                    NotificationReceiver.pauseDownloadsPendingBroadcast(context),
                )
            }

            val hidden = preferences.hideNotificationContent()
            val status = context.getString(R.string.downloading)
            val title = if (download != null && !hidden) downloadTitle(download, context) else status
            setContentTitle(title)
            setContentText(if (download != null && !hidden) status else null)
            setProgress(0, 0, true)
            decorateProgress(context, title, status, 0, 0, download, hidden)
        }
        return notification
    }

    /**
     * Called when download progress changes.
     *
     * @param download download object containing download information.
     */
    @Synchronized
    fun onProgressChange(download: Download) {
        // Create notification
        with(notification) {
            // Check if first call.
            if (!isDownloading) {
                setSmallIcon(android.R.drawable.stat_sys_download)
                setAutoCancel(false)
                clearActions()
                setOngoing(true)
                // Open download manager when clicked
                color = ContextCompat.getColor(context, R.color.primaryTachiyomi)
                setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
                isDownloading = true
                // Pause action
                addAction(
                    R.drawable.ic_pause_24dp,
                    context.getString(R.string.pause),
                    NotificationReceiver.pauseDownloadsPendingBroadcast(context),
                )
            }

            val localized = context.localeContext
            // Snapshot the page list once: it can be absent or empty during a retry.
            val pages = download.pages
            val total = pages?.size ?: 0
            val completed = pages?.count { it.status == eu.kanade.tachiyomi.source.model.Page.State.READY } ?: 0
            val status = if (total > 0) localized.getString(R.string.downloading_progress, completed, total)
                else localized.getString(R.string.downloading)
            val hidden = preferences.hideNotificationContent()
            val title = if (hidden) status else downloadTitle(download, localized)
            setContentTitle(title)
            setContentText(if (hidden) null else status)
            setProgress(total, completed, total == 0)
            decorateProgress(localized, title, status, total, completed, download, hidden)
        }
        // Displays the progress bar on notification
        notification.show()
    }

    /**
     * Show notification when download is paused.
     */
    @Synchronized
    fun onDownloadPaused() {
        val context = context.localeContext
        with(notification) {
            clearProgressDecoration()
            setContentTitle(context.getString(R.string.paused))
            setContentText(context.getString(R.string.download_paused))
            setSmallIcon(R.drawable.ic_pause_24dp)
            setAutoCancel(false)
            setOngoing(false)
            setProgress(0, 0, false)
            color = ContextCompat.getColor(context, R.color.primaryTachiyomi)
            clearActions()
            // Open download manager when clicked
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            // Resume action
            addAction(
                R.drawable.ic_play_arrow_24dp,
                context.getString(R.string.resume),
                NotificationReceiver.resumeDownloadsPendingBroadcast(context),
            )
            // Clear action
            addAction(
                R.drawable.ic_close_24dp,
                context.getString(R.string.cancel_all),
                NotificationReceiver.clearDownloadsPendingBroadcast(context),
            )
            decorateProgress(context, context.getString(R.string.paused), context.getString(R.string.download_paused), 0, 0, null, true, paused = true)
            show()
        }

        // Reset initial values
        isDownloading = false
    }

    /**
     * Called when the downloader receives a warning.
     *
     * @param reason the text to show.
     */
    @Synchronized
    fun onWarning(reason: String) {
        val context = context.localeContext
        with(notification) {
            clearProgressDecoration()
            setContentTitle(context.getString(R.string.downloads))
            setContentText(reason)
            color = ContextCompat.getColor(context, R.color.primaryTachiyomi)
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setOngoing(false)
            setAutoCancel(true)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            setProgress(0, 0, false)
        }
        notification.show(Notifications.ID_DOWNLOAD_CHAPTER_ERROR)

        // Reset download information
        isDownloading = false
    }

    /**
     * Called when the downloader has too many downloads from one source.
     */
    fun massDownloadWarning() {
        val context = context.localeContext
        val notification =
            context
                .notificationBuilder(Notifications.CHANNEL_DOWNLOADER) {
                    setContentTitle(context.getString(R.string.warning))
                    setSmallIcon(R.drawable.ic_warning_white_24dp)
                    setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(context.getString(R.string.download_queue_size_warning)),
                    )
                    setContentIntent(
                        NotificationHandler.openUrl(
                            context,
                            LibraryUpdateNotifier.HELP_WARNING_URL,
                        ),
                    )
                    setTimeoutAfter(30000)
                }.build()

        context.notificationManager.notify(
            Notifications.ID_DOWNLOAD_SIZE_WARNING,
            notification,
        )
    }

    /**
     * Called when the downloader receives an error. It's shown as a separate notification to avoid
     * being overwritten.
     *
     * @param error string containing error information.
     * @param chapter string containing chapter title.
     */
    @Synchronized
    fun onError(
        error: String? = null,
        chapter: String? = null,
        mangaTitle: String? = null,
        customIntent: Intent? = null,
    ) {
        // Create notification
        val context = context.localeContext
        with(notification) {
            clearProgressDecoration()
            setContentTitle(
                mangaTitle?.plus(": $chapter") ?: context.getString(R.string.download_error),
            )
            setContentText(error ?: context.getString(R.string.could_not_download_unexpected_error))
            setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    error ?: context.getString(R.string.could_not_download_unexpected_error),
                ),
            )
            setSmallIcon(android.R.drawable.stat_sys_warning)
            setCategory(NotificationCompat.CATEGORY_ERROR)
            setOngoing(false)
            clearActions()
            setAutoCancel(true)
            if (customIntent != null) {
                setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        customIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            } else {
                setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            }
            color = ContextCompat.getColor(context, R.color.primaryTachiyomi)
            setProgress(0, 0, false)
        }
        notification.show(Notifications.ID_DOWNLOAD_CHAPTER_ERROR)

        // Reset download information
        errorThrown = true
        isDownloading = false
    }
}
