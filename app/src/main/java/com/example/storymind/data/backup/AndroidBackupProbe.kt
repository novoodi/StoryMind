package com.example.storymind.data.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * [BackupProbe]의 기기 구현 — 후보 파일을 **읽기 전용 + 별도 연결**로 연다. Room 인스턴스와
 * 완전히 무관한 연결이라 검증이 실사용 DB에 어떤 락도 걸지 않고, OPEN_READONLY라 검증 자체가
 * 후보 파일을 변형(-wal 생성 등)하지도 않는다.
 */
object AndroidBackupProbe : BackupProbe {
    override fun probe(file: File): ProbeReport =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val integrityOk = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            val hasChapters = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'chapters'",
                null,
            ).use { it.moveToFirst() }
            val userVersion = db.rawQuery("PRAGMA user_version", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            ProbeReport(integrityOk, hasChapters, userVersion)
        }
}
