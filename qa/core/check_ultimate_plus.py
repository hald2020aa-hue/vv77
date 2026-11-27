#!/usr/bin/env python3
"""Static integration checks; not a substitute for Android compilation."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
KT=ROOT/'app/src/main/java/eu/kanade/tachiyomi'
RES=ROOT/'app/src/main/res'
reader=(KT/'ui/reader/ReaderActivity.kt').read_text()
popup=(KT/'ui/reader/NeonReaderTranslationMenu.kt').read_text()
details=(KT/'ui/manga/MangaDetailsController.kt').read_text()
store=(KT/'ui/reader/plus/PlusReaderStore.kt').read_text()
alternatives=(KT/'ui/source/plus/PlusAlternativesController.kt').read_text()
counts=0
def check(label, value):
    global counts
    assert value, label
    counts+=1
    print('PASS:',label)
android='{http://schemas.android.com/apk/res/android}'
for names,xml,activity in [
    (['toggle_page_mark','page_marks','reading_stats','detect_mode'],'reader.xml',reader),
    (['find_alternatives','find_related'],'manga_details.xml',details),
]:
    items=[x.attrib.get(android+'id') for x in ET.parse(RES/'menu'/xml).getroot().iter('item')]
    for name in names:
        id='action_mv_plus_'+name
        check(id+' registered once',items.count('@+id/'+id)==1)
        check(id+' routed',activity.count('R.id.'+id+' ->')==1)
        if xml=='reader.xml': check(id+' present in neon popup','R.id.'+id+' to ' in popup)
base=ET.parse(RES/'values/mv_ultimate_plus.xml').getroot()
ar=ET.parse(RES/'values-ar/mv_ultimate_plus.xml').getroot()
check('Arabic and English resource names agree',{x.attrib['name'] for x in base}=={x.attrib['name'] for x in ar})
check('reader uses existing navigation','moveToPageIndex(it)' in reader)
check('reader records only active ready page','status == Page.State.READY' in reader and 'plusForeground' in reader)
check('private-mode guard present','isIncognitoModeForSource(it.source)' in reader and 'if (plusPrivateMode()) return' in reader)
check('page observer cancelled','plusPageJob?.cancel()' in reader)
check('private local store','Context.MODE_PRIVATE' in store and 'mv_plus_reader_local_v1' in store)
check('no source or manga writes in store',all(x not in store for x in ['DatabaseHelper','DownloadManager','deleteManga','deleteChapter','HttpSource']))
check('alternate search excludes original source','super.getEnabledSources().filterNot { it.id == excludedSourceId }' in alternatives)
check('alternative search opens existing controller','PlusAlternativesController(title, presenter.manga.source)' in details)
check('webtoon suggestion uses loaded view and explicit confirmation','image.sWidth, image.sHeight' in reader and '.setPositiveButton(R.string.mv_plus_switch_webtoon)' in reader)
check('translation original action preserved','R.id.action_translate_chapter -> askChapterTranslationLanguage()' in reader and 'R.id.action_translation_stop to' in popup)
print(f'{counts}/{counts} Ultimate Plus static checks PASS')
