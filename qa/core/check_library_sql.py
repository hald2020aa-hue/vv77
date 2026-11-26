#!/usr/bin/env python3
"""Exercise the actual Kotlin library SELECT using SQLite and synthetic fixtures."""
from pathlib import Path
import re
import sqlite3
from collections import Counter

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'app/src/main/java/eu/kanade/tachiyomi'
raw = (BASE / 'data/database/queries/RawQueries.kt').read_text()
query = raw.split('val libraryQuery =', 1)[1].split('"""')[1]
assert query.count("'N/A'), ' [.] '") == 2, 'both scanlator aggregates must use SQL string literals'
assert '"N/A"' not in query and '" [.] "' not in query
constants = {}
for alias in ('Manga', 'Chapter', 'MangaCategory', 'Track'):
    table = (BASE / f'data/database/tables/{alias}Table.kt').read_text()
    constants.update({f'{alias}.{k}': v for k, v in re.findall(r'const val (\w+) = "([^"]+)"', table)})
track_manager = (BASE / 'data/track/TrackManager.kt').read_text()
constants['TrackManager.ANILIST'] = re.search(r'const val ANILIST = (\d+)', track_manager).group(1)
query = re.sub(r'\$\{([^}]+)\}', lambda m: constants[m.group(1)], query)
old_query = query.replace("'N/A'), ' [.] '", '"N/A"), " [.] "')
con = sqlite3.connect(':memory:')
con.executescript('''
CREATE TABLE mangas(_id INTEGER PRIMARY KEY, favorite INTEGER NOT NULL, title TEXT NOT NULL, filtered_scanlators TEXT);
CREATE TABLE chapters(_id INTEGER PRIMARY KEY, manga_id INTEGER NOT NULL REFERENCES mangas(_id), read INTEGER NOT NULL, bookmark INTEGER NOT NULL, scanlator TEXT);
CREATE TABLE mangas_categories(_id INTEGER PRIMARY KEY, manga_id INTEGER NOT NULL, category_id INTEGER NOT NULL);
CREATE TABLE manga_sync(_id INTEGER PRIMARY KEY, manga_id INTEGER NOT NULL, sync_id INTEGER NOT NULL, score REAL NOT NULL, UNIQUE(manga_id,sync_id));
CREATE INDEX library_favorite_index ON mangas(favorite) WHERE favorite=1;
CREATE INDEX chapters_manga_id_index ON chapters(manga_id);
CREATE INDEX chapters_unread_by_manga_index ON chapters(manga_id,read) WHERE read=0;
CREATE INDEX chapters_read_by_scanlator_index ON chapters(manga_id,scanlator) WHERE read=1;
CREATE INDEX chapters_bookmarked_by_manga_index ON chapters(manga_id) WHERE bookmark=1;
CREATE INDEX mangas_categories_manga_id_index ON mangas_categories(manga_id);
INSERT INTO mangas VALUES (1,1,'Alpha','A'),(2,1,'Beta',NULL),(3,1,'Empty','A'),(4,0,'Hidden','A');
INSERT INTO chapters VALUES (1,1,0,1,NULL),(2,1,0,0,'A'),(3,1,1,1,NULL),(4,1,1,0,'B'),(5,2,0,0,'X'),(6,4,0,1,'Y');
INSERT INTO mangas_categories VALUES (1,1,10),(2,1,11);
''')
ani = int(constants['TrackManager.ANILIST'])
other = ani + 100
con.executemany('INSERT INTO manga_sync VALUES(?,?,?,?)', [(1,1,ani,80),(2,1,other,6),(3,2,other,0)])
# Explicitly enable legacy literal fallback for the comparison, when supported.
can_toggle = hasattr(con, 'setconfig') and hasattr(sqlite3, 'SQLITE_DBCONFIG_DQS_DML')
if can_toggle:
    con.setconfig(sqlite3.SQLITE_DBCONFIG_DQS_DML, True)
old_rows = con.execute(old_query).fetchall()
new_rows = con.execute(query).fetchall()
assert Counter(old_rows) == Counter(new_rows), 'library result changed'
print('PASS: legacy and corrected full SELECT return identical rows')
if can_toggle:
    con.setconfig(sqlite3.SQLITE_DBCONFIG_DQS_DML, False)
    assert Counter(con.execute(query).fetchall()) == Counter(new_rows)
    try:
        con.execute(old_query).fetchall()
    except sqlite3.OperationalError as exc:
        assert 'N/A' in str(exc) or ' [.] ' in str(exc), str(exc)
    else:
        raise AssertionError('legacy double-quoted SQL unexpectedly accepted in strict mode')
    print('PASS: corrected SELECT works with double-quoted string fallback disabled')
else:
    print('SKIP: SQLite strict mode API unavailable in this Python version')
con.row_factory = sqlite3.Row
rows = con.execute(query).fetchall()
assert len(rows) == 4 and {r['_id'] for r in rows} == {1,2,3}
a = [r for r in rows if r['_id'] == 1]
assert {r['category'] for r in a} == {10,11}
assert all(r['unread_count']==2 and r['read_count']==2 and r['bookmark_count']==2 and r['score']==7 for r in a)
assert all(set(r['unread'].split(' [.] '))=={'A','N/A'} and set(r['has_read'].split(' [.] '))=={'B','N/A'} for r in a)
b = next(r for r in rows if r['_id']==2)
assert b['unread_count']==1 and b['unread']=='' and b['category']==0 and b['score']==0
empty = next(r for r in rows if r['_id']==3)
assert empty['unread_count']==0 and empty['read_count']==0 and empty['bookmark_count']==0
print('PASS: categories, counts, missing scanlators, tracking score, empty works and favorites preserved')
# The fixture cannot reproduce Android's exact query planner; report rather than fail on automatic indexes.
plan = [r[3] for r in con.execute('EXPLAIN QUERY PLAN '+query)]
print('SQLite query plan automatic indexes:', [s for s in plan if 'AUTOMATIC' in s.upper()])
print('PASS: library SQL fixture; no Android runtime or real database benchmark')
