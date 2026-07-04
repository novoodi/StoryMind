#!/usr/bin/env bash
#
# scripts/unmark-chapter-ingested.sh — flips one chapter back to "not analyzed" in the on-device
# Room DB so the app re-ingests it. Built for dogfooding recovery: a chapter whose ingest failed
# under the old parser bug (values like "3:00" / dialogue quotes got mangled) never produced a
# wiki, and now that the parser is fixed we want that single chapter re-run to prove the fix.
#
# Usage:
#   ./scripts/unmark-chapter-ingested.sh [CHAPTER]     # CHAPTER is the 1-based "N화" number
#   ./scripts/unmark-chapter-ingested.sh               # defaults to 2 (2화)
#
# Env overrides:
#   PACKAGE_NAME   — app id (default: com.example.storymind)
#   DB_NAME        — Room DB file name (default: storymind.db)
#   ANDROID_HOME / ANDROID_SDK_ROOT — searched for platform-tools/adb if adb isn't on PATH
#   ANDROID_SERIAL — picks a specific device when more than one is connected
#
# Requirements: adb, a DEBUGGABLE build installed (run-as only works on debuggable apps). The edit
# itself needs sqlite3 SOMEWHERE — either on the device (/system/bin/sqlite3, Android 9 / API 28+)
# or, when the device lacks it (common), a local Python 3 (its stdlib sqlite3) for the pull/push
# fallback below.
#
# What it does:
#   1. Resolves adb, confirms one device, maps the 1-based "N화" arg to the 0-based chapterIndex
#      primary key (chapterIndex = N-1). The `label` column can read "N장", so we identify strictly
#      by numeric chapterIndex.
#   2. force-stops the app — Room holds storymind.db open with WAL; writing underneath a live
#      connection risks lock contention and a torn read. force-stop closes it cleanly.
#   3. Applies the exact inverse of StoryDao.markIngested():
#         UPDATE chapters SET ingested=0, ingestEngine=NULL, ingestPromptVersion=NULL WHERE chapterIndex=<idx>
#      All three columns reset together (that is what StoryDao.resetIngestProvenance does) — leaving
#      ingestEngine/ingestPromptVersion set while ingested=0 is an inconsistent state.
#      • Fast path: run-as sqlite3 directly on the device.
#      • Fallback (no device sqlite3): pull the DB out of the app sandbox, edit it with local
#        Python, push it back via /data/local/tmp + run-as cp, then drop the stale -wal/-shm.
#
# Deliberately does NOT touch the derived tables (wiki_entries / graph_nodes / graph_edges /
# orphan_ids). Those have no per-chapter partitioning; the next ingest's commitIngest() re-merges
# this one chapter onto the existing accumulated snapshot, and user-dragged node coordinates are
# preserved by merge. Clearing them would wipe every already-ingested chapter's wiki/graph, and the
# other chapters would stay ingested=1 (this is not a full reset) so they'd never come back.
#
# After it runs: reopen the app. The editor shows a "분석 안 된 화 1개" banner (pendingAnalysisCount
# observes the chapters table live); tap "지금 분석" → analyzePending() → ReplayWorker resume picks
# the lowest pending chapter (this one) and ingests only it. The manuscript body is never touched.

set -euo pipefail

# ---- Configuration -------------------------------------------------------------------------
PACKAGE_NAME="${PACKAGE_NAME:-com.example.storymind}"
DB_NAME="${DB_NAME:-storymind.db}"
CHAPTER_HUMAN="${1:-2}"                       # 1-based "N화" as the writer sees it
# --------------------------------------------------------------------------------------------

log() { echo "[unmark-chapter] $*"; }
die() { echo "[unmark-chapter] ERROR: $*" >&2; exit 1; }

find_adb() {
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return
    fi
    local candidates=(
        "${ANDROID_HOME:-}/platform-tools/adb"
        "${ANDROID_SDK_ROOT:-}/platform-tools/adb"
        "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
        "${HOME:-}/Library/Android/sdk/platform-tools/adb"
        "${HOME:-}/Android/Sdk/platform-tools/adb"
    )
    local c
    for c in "${candidates[@]}"; do
        [ -n "$c" ] && [ -x "$c" ] && { echo "$c"; return; }
    done
    die "adb not found on PATH or in common Android SDK locations. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

find_python() {
    local c
    for c in python3 python py; do
        command -v "$c" >/dev/null 2>&1 && { echo "$c"; return; }
    done
    die "Device lacks sqlite3 and no local Python 3 was found for the pull/push fallback. Install Python or a device with sqlite3."
}

# Python one-liner program used by BOTH the local-edit and the verify step. Args: <db> <idx> <mode>.
# mode=edit → dump BEFORE, UPDATE, dump AFTER (journal_mode=DELETE first so the pulled file is fully
# self-contained when we push it back — Room flips it back to WAL on its next open). mode=verify →
# just dump the row states.
PY_PROG='
import sqlite3, sys
db, idx, mode = sys.argv[1], int(sys.argv[2]), sys.argv[3]
con = sqlite3.connect(db)
cur = con.cursor()
def dump(tag):
    print(f"[unmark-chapter] --- {tag} ---")
    for r in cur.execute("SELECT chapterIndex, ingested, ingestEngine, ingestPromptVersion FROM chapters ORDER BY chapterIndex;"):
        print("[unmark-chapter]  ", r)
if mode == "edit":
    con.execute("PRAGMA journal_mode=DELETE;")
    dump("BEFORE")
    cur.execute("UPDATE chapters SET ingested=0, ingestEngine=NULL, ingestPromptVersion=NULL WHERE chapterIndex=?;", (idx,))
    print(f"[unmark-chapter] rows updated: {cur.rowcount}")
    con.commit()
    dump("AFTER")
else:
    dump("VERIFY")
con.close()
'

# The 1-based "N화" the writer sees maps to the 0-based chapterIndex primary key: 2화 -> chapterIndex 1.
case "$CHAPTER_HUMAN" in
    ''|*[!0-9]*) die "CHAPTER must be a positive integer (the 1-based 화 number). Got: '$CHAPTER_HUMAN'." ;;
esac
[ "$CHAPTER_HUMAN" -ge 1 ] || die "CHAPTER must be >= 1 (1화 is the first chapter)."
IDX=$((CHAPTER_HUMAN - 1))

ADB="$(find_adb)"
log "Using adb: $ADB"

# 1. Confirm a device is connected ------------------------------------------------------------
DEVICE_COUNT="$("$ADB" devices | awk 'NR>1 && $2=="device"{c++} END{print c+0}')"
[ "$DEVICE_COUNT" -ge 1 ] || die "No authorized device found. Run '$ADB devices' and check USB debugging."
[ "$DEVICE_COUNT" -eq 1 ] || log "Warning: multiple devices connected; set ANDROID_SERIAL to pick one."

DB_PATH="databases/$DB_NAME"   # run-as cwd is the app data dir, so the DB is at this relative path
# SQL below is intentionally quote-free (no string literals) so a single layer of single-quotes
# carries it through Git-Bash to the device shell without nested-quote escaping.
SELECT_SQL="SELECT chapterIndex, ingested, ingestEngine, ingestPromptVersion FROM chapters ORDER BY chapterIndex;"
UPDATE_SQL="UPDATE chapters SET ingested=0, ingestEngine=NULL, ingestPromptVersion=NULL WHERE chapterIndex=$IDX;"

# Probe: does run-as work (debuggable build) and is sqlite3 present on the device?
PROBE="$("$ADB" shell "run-as $PACKAGE_NAME sh -c 'command -v sqlite3 >/dev/null 2>&1 && echo SQLITE_OK || echo SQLITE_MISSING'" 2>&1 | tr -d '\r' || true)"
case "$PROBE" in
    *SQLITE_OK*)      DEVICE_SQLITE=1 ;;
    *SQLITE_MISSING*) DEVICE_SQLITE=0 ;;
    *run-as*|*"not debuggable"*|*Package*)
        die "run-as failed — is a DEBUGGABLE build of $PACKAGE_NAME installed? (run-as won't touch release builds.) Raw: $PROBE" ;;
    *) die "Unexpected run-as probe result: '$PROBE'" ;;
esac

log "Targeting ${CHAPTER_HUMAN}화 = chapterIndex $IDX."

# 2. force-stop so Room isn't holding the DB open (both paths write underneath it) ------------
log "force-stopping $PACKAGE_NAME so its WAL connection is closed before we write..."
"$ADB" shell "am force-stop $PACKAGE_NAME"

if [ "$DEVICE_SQLITE" -eq 1 ]; then
    # ---- Fast path: sqlite3 on the device ---------------------------------------------------
    log "Device has sqlite3. Current state:"
    "$ADB" shell "run-as $PACKAGE_NAME sqlite3 -header -column $DB_PATH '$SELECT_SQL'"
    log "Resetting ingest provenance for chapterIndex $IDX..."
    "$ADB" shell "run-as $PACKAGE_NAME sqlite3 $DB_PATH '$UPDATE_SQL'" || die "sqlite3 UPDATE failed."
    log "Done. State after the flip:"
    "$ADB" shell "run-as $PACKAGE_NAME sqlite3 -header -column $DB_PATH '$SELECT_SQL'"
else
    # ---- Fallback: pull → local Python edit → push back -------------------------------------
    log "Device has no sqlite3 — falling back to pull/edit/push with local Python."
    PY="$(find_python)"
    log "Using python: $PY"
    WORK="$(mktemp -d 2>/dev/null || mktemp -d -t unmark)"
    trap 'rm -rf "$WORK"' EXIT
    LOCAL_DB="$WORK/$DB_NAME"

    # exec-out streams raw bytes (no CRLF translation). Pull the main db; also pull a non-empty
    # -wal so Python replays any committed-but-not-checkpointed pages before we edit.
    "$ADB" exec-out run-as "$PACKAGE_NAME" cat "$DB_PATH" > "$LOCAL_DB"
    [ -s "$LOCAL_DB" ] || die "Pulled DB is empty — is databases/$DB_NAME present on the device?"
    "$ADB" exec-out run-as "$PACKAGE_NAME" sh -c "cat $DB_PATH-wal 2>/dev/null" > "$LOCAL_DB-wal" || true
    [ -s "$LOCAL_DB-wal" ] || rm -f "$LOCAL_DB-wal"

    log "Editing pulled DB..."
    "$PY" -c "$PY_PROG" "$LOCAL_DB" "$IDX" edit

    # Push back. //-prefixed device path defeats Git-Bash/MSYS's POSIX->Windows path conversion,
    # which otherwise rewrites "/data/local/tmp/..." into a bogus local path (as push-model.sh
    # documents for /sdcard). A real device shell collapses "//data" to "/data".
    STAGE="/data/local/tmp/${DB_NAME}.unmark"
    log "Pushing edited DB back via $STAGE ..."
    "$ADB" push "$LOCAL_DB" "/$STAGE" >/dev/null || die "adb push to staging failed."
    "$ADB" shell "run-as $PACKAGE_NAME cp $STAGE $DB_PATH" || die "run-as cp into the sandbox failed."
    # The old -wal/-shm salt no longer matches the freshly written main db; drop them so SQLite
    # doesn't try to replay a mismatched WAL. Room recreates both on its next open.
    "$ADB" shell "run-as $PACKAGE_NAME rm -f $DB_PATH-wal $DB_PATH-shm"
    "$ADB" shell "rm -f $STAGE"

    log "Verifying on-device result..."
    "$ADB" exec-out run-as "$PACKAGE_NAME" cat "$DB_PATH" > "$WORK/verify.db"
    "$PY" -c "$PY_PROG" "$WORK/verify.db" "$IDX" verify
fi

log "Now reopen the app → editor shows a '분석 안 된 화' banner → tap '지금 분석' to re-ingest ${CHAPTER_HUMAN}화 only."
