from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
p = root / 'app/src/main/java/eu/kanade/tachiyomi'
metrics = (p / 'ui/library/insights/LibraryInsightMetrics.kt').read_text()
activity = (p / 'ui/library/insights/LibraryInsightsActivity.kt').read_text()
storage = (p / 'data/download/StorageManager.kt').read_text()
controller = (p / 'ui/home/StorageController.kt').read_text()
files = [root / 'app/src/main/res' / folder / 'ultimate_insights.xml' for folder in ('values', 'values-ar')]
strings = [{node.attrib['name']:node.text for node in ET.parse(f).getroot()} for f in files]
assert strings[0].keys() == strings[1].keys()
assert all(re.findall(r'%\d+\$[ds]', strings[0][name]) == re.findall(r'%\d+\$[ds]', strings[1][name]) for name in strings[0])
assert set(re.findall(r'R\.string\.(ultimate_\w+)', activity+controller)) == strings[0].keys()
assert 'distinctBy { it.id }' in metrics and 'readFraction = if (read + unread > 0)' in metrics
assert 'throw cancelled' in activity
assert 'calculateStorageBreakdown(works.map' in storage
assert 'data.breakdown.types.forEach' in controller and 'data.breakdown.readBytes' in controller
assert 'if (showBreakdown)' in controller
print('PASS: XML, bilingual format arguments, references, snapshot aggregation, cancellation, reading deduplication and UI wiring')
