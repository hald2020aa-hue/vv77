#!/usr/bin/env python3
"""Exercise actual schema declarations and v21 SQL with SQLite. Optional real DB is copied read-only."""
import argparse
import pathlib
import re
import sqlite3

ROOT = pathlib.Path(__file__).resolve().parents[2]
TABLES = ROOT / 'app/src/main/java/eu/kanade/tachiyomi/data/database/tables'
NAMES = ['MangaTable', 'ChapterTable', 'TrackTable', 'CategoryTable', 'MangaCategoryTable', 'HistoryTable']
texts = {n: (TABLES / (n + '.kt')).read_text() for n in NAMES}
constants = {n: dict(re.findall(r'const val (\w+) = "([^"]+)"', t)) for n, t in texts.items()}

def expand(s, name):
    def substitute(m):
        key = m[1] or m[2]
        owner, key = key.split('.') if '.' in key else (name, key)
        return expand(constants[owner][key], owner)
    return re.sub(r'\$\{([\w.]+)\}|\$(\w+)', substitute, s)

def schema(name):
    return expand(re.search(r'val createTableQuery: String\s*get\(\) =\s*"""(.*?)"""', texts[name], re.S)[1], name)

migration = expand(re.search(r'val addStorageManualType: String\s*get\(\) = "([^"]+)"', texts['MangaTable'])[1], 'MangaTable')
assert migration == 'ALTER TABLE mangas ADD COLUMN storage_manual_type TEXT'
callback = (TABLES.parent / 'DbOpenCallback.kt').read_text()
assert 'DATABASE_VERSION = 21' in callback
assert re.search(r'if \(oldVersion < 21\) \{\s*db.execSQL\(MangaTable.addStorageManualType\)\s*\}', callback)

def quote(name):
    return '"' + name.replace('"', '""') + '"'

def capture(db):
    tables = db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall()
    result = {}
    for (table,) in tables:
        cols = [r[1] for r in db.execute('PRAGMA table_info(' + quote(table) + ')') if r[1] != 'storage_manual_type']
        # Compare all rows as multisets, including binary/NULL data, without emitting them.
        from collections import Counter
        result[table] = (cols, Counter(db.execute('SELECT ' + ','.join(map(quote, cols)) + ' FROM ' + quote(table))))
    return result

def validate(db):
    assert db.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
    assert db.execute('PRAGMA foreign_key_check').fetchall() == []
    assert db.execute('PRAGMA user_version').fetchone()[0] == 20
    before = capture(db)
    indexes = db.execute("SELECT name,sql FROM sqlite_master WHERE type='index' ORDER BY name").fetchall()
    db.execute(migration)
    db.execute('PRAGMA user_version=21')
    assert capture(db) == before
    assert db.execute("SELECT COUNT(*) FROM mangas WHERE storage_manual_type IS NOT NULL").fetchone()[0] == 0
    assert db.execute("SELECT name,sql FROM sqlite_master WHERE type='index' ORDER BY name").fetchall() == indexes
    assert db.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
    assert db.execute('PRAGMA foreign_key_check').fetchall() == []
    print('PASS: v20 -> v21: all existing table rows, columns and indexes preserved; integrity and foreign keys OK')

parser = argparse.ArgumentParser()
parser.add_argument('--real-db', type=pathlib.Path, help='Optional local v20 database; opened read-only and copied to memory')
args = parser.parse_args()
db = sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys=ON')
if args.real_db:
    with sqlite3.connect(args.real_db.resolve().as_uri() + '?mode=ro', uri=True) as original:
        original.backup(db)
    validate(db)
    print('PASS: real database copy only; original not modified')
else:
    for name in NAMES:
        sql = schema(name)
        if name == 'MangaTable':
            sql = re.sub(r',\s*storage_manual_type TEXT', '', sql)
        db.execute(sql)
    create_callback = callback.split("override fun onCreate", 1)[1].split("override fun onUpgrade", 1)[0]
    for owner, key in re.findall(r"execSQL\((\w+)\.(create\w+IndexQuery)\)", create_callback):
        expr = re.search(r'val ' + key + r': String\s*get\(\) =\s*((?:"[^"]*"\s*\+\s*)*"[^"]*")', texts[owner])[1]
        db.execute(expand("".join(re.findall(r'"([^"]*)"', expr)), owner))
    # Populate every existing table with non-empty, mutually valid sample data.
    for name in NAMES:
        table = constants[name]['TABLE']
        cols = list(db.execute('PRAGMA table_info(' + quote(table) + ')'))
        values = ['sample' if r[2].upper() == 'TEXT' else 1 for r in cols]
        db.execute('INSERT INTO ' + quote(table) + ' VALUES (' + ','.join('?' for _ in cols) + ')', values)
    db.execute('PRAGMA user_version=20')
    db.commit()
    validate(db)
    db.execute("UPDATE mangas SET storage_manual_type='MANHWA' WHERE _id=1")
    db.execute("UPDATE mangas SET genre='manga', title='refreshed' WHERE _id=1")
    db.execute("UPDATE mangas SET storage_manual_type='MANHUA' WHERE _id=1 AND storage_manual_type IS NULL")
    assert db.execute('SELECT storage_manual_type FROM mangas WHERE _id=1').fetchone() == ('MANHWA',)
    db.execute('UPDATE mangas SET storage_manual_type=NULL WHERE _id=1')
    assert db.execute('SELECT storage_manual_type FROM mangas WHERE _id=1').fetchone() == (None,)
    fresh = sqlite3.connect(':memory:')
    fresh.execute(schema('MangaTable'))
    assert db.execute('PRAGMA table_info(mangas)').fetchall() == fresh.execute('PRAGMA table_info(mangas)').fetchall()
    print('PASS: manual choice survives metadata update and restore merge; reset and fresh schema match')
    print('Fixture data only; Android migration and backup runtime not tested.')
