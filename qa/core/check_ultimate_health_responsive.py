#!/usr/bin/env python3
"""Source/resource regression checks; Android lifecycle and rendering require device tests."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[2]
kt = root / 'app/src/main/java/eu/kanade/tachiyomi'
activity = (kt / 'ui/source/health/SourceHealthActivity.kt').read_text()
rules = (kt / 'ui/source/health/SourceHealthRules.kt').read_text()
grid = (kt / 'widget/AutofitRecyclerView.kt').read_text()
count = 0
def verify(ok, name):
    global count
    assert ok, name
    count += 1
    print('PASS:', name)
for method, end in [('scanAll', 'scanOne'), ('scanOne', 'scanDeep'), ('scanDeep', 'deepProbe')]:
    body = activity.split('fun ' + method + '(', 1)[1].split('fun ' + end + '(', 1)[0]
    verify('if (_scanning.value) return' in body and 'launchScan {' in body, method + ' shares scan lifecycle gate')
verify('finally {' in activity and '_scanning.value = false' in activity and 'base.copy(deepStatus = DeepProbeStatus.NOT_CHECKED)' in activity, 'scan cleanup clears unfinished status')
verify('previous[source.id]?.takeIf { it.baseUrl == source.baseUrl }' in activity, 'refresh retains results only for matching source identity and address')
verify('async(Dispatchers.IO)' in activity and '}.awaitAll()' in activity and 'val scanLimit = Semaphore(4)' in activity, 'parallel base probes publish results under concurrency limit')
verify('val request = Request.Builder()' in activity.split('private fun probe(',1)[1].split('} catch',1)[0].split('return try {',1)[1], 'invalid request construction handled by probe')
verify('getPopularManga(1)' in activity and 'getChapterList(details)' in activity and 'getPageList(firstChapter)' in activity, 'existing deep source functions preserved')
verify('sourceResponseRank(it.status, it.elapsedMs)' in activity and 'status == ProbeStatus.AVAILABLE' in rules, 'response sorting excludes failed or unknown responses')
verify('items(visibleSources, key = { it.id })' in activity, 'filtered rows keep stable identity')
verify('neonContentWidthPx(outerWidth, paddingLeft, paddingRight)' in grid and 'usableWidth != lastSpanContentWidth' in grid, 'actual viewport and small changes drive grid')
verify('if (usableWidth <= 0) return' in grid and 'if (isComputingLayout)' in grid, 'guard invalid width and active layout')
verify('layoutManager === anchorManager' in grid and 'first in 0 until (adapter?.itemCount ?: 0)' in grid and 'scrollToPositionWithOffset(first, top - paddingTop)' in grid, 'anchor is validated before scroll restoration')
refs = set(re.findall(r'R\.string\.(ultimate_health_\w+)', activity))
for lang in ('values', 'values-ar'):
    res = root / 'app/src/main/res' / lang
    entries = ET.parse(res / 'ultimate_source_health.xml').getroot()
    keys = [el.attrib['name'] for el in entries]
    allkeys = [el.attrib.get('name') for path in res.glob('*.xml') for el in ET.parse(path).getroot() if el.tag == 'string']
    verify(refs == set(keys) and all(allkeys.count(key) == 1 for key in keys), lang + ' health string references and uniqueness')
print(f'{count}/{count} static checks passed; not Android compilation or device testing')
