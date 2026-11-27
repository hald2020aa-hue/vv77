#!/usr/bin/env python3
"""Source and XML regression checks; Android build/rendering are separate."""
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path(__file__).resolve().parents[2]
k=root/'app/src/main/java/eu/kanade/tachiyomi'
r=root/'app/src/main/res'
d=root/'app/src/dev/res'
A='{http://schemas.android.com/apk/res/android}'
def read(p): return (k/p).read_text()
def check(label, condition):
    assert condition, label
    print('PASS:',label)
h=read('ui/manga/MangaHeaderHolder.kt')
for n in ('summaryWhitespace','plainMarkwon','imageMarkwon'):
    check(n+' initialized before holder constructor calls description', h.index('private val '+n)<h.index('    init {'))
check('Both description modes remain available', 'if (adapter.preferences.renderDescriptionImages().get()) imageMarkwon else plainMarkwon' in h)
check('No regression of MaterialButton crash', 'startReadingButton.applyNeonGradient(' in h and 'startReadingButton.background =' not in h)
n=read('data/download/DownloadNotifier.kt')
check('Null and empty page lists are safe', 'download.pages!!' not in n and 'val pages = download.pages' in n and 'total == 0' in n)
check('Privacy applies to custom bytes and titles', 'if (hidden) null else download?.transferStats?.value' in n and 'if (download != null && !hidden)' in n and 'if (hidden) status else downloadTitle' in n)
check('Native pause and resume actions kept', all(x in n for x in ('pauseDownloadsPendingBroadcast','resumeDownloadsPendingBroadcast','clearDownloadsPendingBroadcast','openDownloadManagerPendingActivity')))
for method in ('onDownloadPaused','onWarning','onError'):
    check(method+' clears stale custom notification', 'clearProgressDecoration()' in n.split('fun '+method,1)[1].split('with(notification) {',1)[1].split('setContentTitle',1)[0])
check('Only known total is shown', 'knownChapterBytes?.let' in n and 'mv_notification_received' in n)
check('Safe progress for empty download', 'if (pages.isEmpty()) return 0' in read('data/download/model/Download.kt'))
layout=ET.parse(r/'layout/mv_download_notification.xml').getroot()
check('RemoteViews supported widget types', all(e.tag in {'LinearLayout','TextView','ProgressBar','ImageView'} for e in layout.iter()))
ids={e.get(A+'id') for e in layout.iter()}
check('Notification binding IDs present', all('@+id/mv_notification_'+s in ids for s in ('title','status','progress','percent','bytes')))
check('Arabic and default notification string keys match', {e.get('name') for e in ET.parse(r/'values/mv_download_notification.xml').getroot()}=={e.get('name') for e in ET.parse(r/'values-ar/mv_download_notification.xml').getroot()})
item=read('ui/source/browse/BrowseSourceItem.kt')
check('Uniform two-line title panel in comfortable grid', 'binding.title.setLines(2)' in item and 'mv_browse_title_panel' in item)
check('Other grid and list modes preserved', all(s in item for s in ('LAYOUT_COMPACT_GRID','R.layout.manga_list_item','"16:9"','"15:22"')))
c=read('ui/source/browse/BrowseSourceController.kt')
check('All sources use shared responsive grid', 'presenter.source.name' in c and 'setGridSize(preferences)' in c and 'mv_browse_background' in c and 'dockHeight + 16.dpToPx' in c)
check('Stale covers cleared', 'binding.coverThumbnail.setImageDrawable(null)' in read('ui/source/browse/BrowseSourceGridHolder.kt'))
for p in list(r.glob('drawable/mv_browse*.xml'))+list(r.glob('drawable/mv_notification*.xml'))+[d/'layout/browse_source_controller.xml',d/'color/mv_browse_button_fill.xml']:
    ET.parse(p)
print('PASS: modified XML parses')
# Premium notification: verify only one progress track and one action row in the neon path.
body=n.split('private fun NotificationCompat.Builder.decorateProgress(',1)[1].split('private fun NotificationCompat.Builder.clearProgressDecoration()',1)[0]
check('Non-neon retains its original native actions and progress', body.index('if (!localized.resources.getBoolean(R.bool.v4_neon_mode)) return') < body.index('setProgress(0, 0, false)') < body.index('clearActions()'))
check('Only one custom progress bar', len(list(layout.iter('ProgressBar')))==1 and body.count('setProgressBar(')==1)
check('Page counts are bounded and empty chapters remain indeterminate', 'total.coerceAtLeast(1)' in body and 'completed.coerceIn(0, maximum)' in body and 'total == 0 && !paused' in body)
check('Compact notification preserves privacy', 'if (!paused && total > 0 && !hidden)' in body)
check('Speed is taken from measured transfer stats, not device traffic', 'stats?.speedBytesPerSecond?.takeIf { it > 0L }' in body)
check('Pause and resume use existing immutable pending broadcasts', 'if (paused) NotificationReceiver.resumeDownloadsPendingBroadcast(localized)' in body and 'else NotificationReceiver.pauseDownloadsPendingBroadcast(localized)' in body)
check('Cancel management opens existing queue instead of deleting it directly', 'setOnClickPendingIntent(R.id.mv_notification_manage, NotificationHandler.openDownloadManagerPendingActivity(localized))' in body and 'clearDownloadsPendingBroadcast' not in body)
check('Paused notification uses same card with progress row hidden', 'paused = true' in n.split('fun onDownloadPaused()',1)[1].split('fun onWarning',1)[0] and 'mv_notification_progress_row, if (paused) android.view.View.GONE' in body)
check('Arabic layout follows locale and progress track stays left-to-right', layout.get(A+'layoutDirection')=='locale' and next(layout.iter('ProgressBar')).get(A+'layoutDirection')=='ltr')
check('Progress bar disables native tint to retain the gradient', next(layout.iter('ProgressBar')).get(A+'progressTint')=='@null')
progress=ET.parse(r/'drawable/mv_notification_progress.xml').getroot()
check('Fill is a scaled cyan-purple-magenta gradient', progress.find('./item/scale/shape/gradient') is not None and progress.find('./item/scale').get(A+'scaleWidth')=='100%')
for name in ('primary','manage'):
    btn=next(e for e in layout.iter() if e.get(A+'id')=='@+id/mv_notification_'+name)
    check(name+' action has at least 48dp touch height and a real pending intent', btn.get(A+'minHeight')=='48dp' and 'R.id.mv_notification_'+name in body and btn.get(A+'clickable')=='true')
print('Static assertions only; no Android SDK/emulator rendering in this environment.')
