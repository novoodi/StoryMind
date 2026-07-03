package com.example.storymind.data.backup

import java.io.File

/**
 * What a [BackupProbe] reports after opening a candidate backup with a real SQLite engine.
 * [userVersion]은 `PRAGMA user_version` — Room이 스키마 버전을 저장하는 곳이다.
 */
data class ProbeReport(
    val integrityOk: Boolean,
    val hasChaptersTable: Boolean,
    val userVersion: Int,
)

/**
 * 후보 백업 파일을 실제 SQLite 엔진으로 열어보는 심. 검증 로직([BackupValidator])과 분리한
 * 이유: Android의 `android.database.sqlite`는 JVM 유닛 테스트에서 못 쓰고, 테스트용 JDBC
 * 드라이버(sqlite-jdbc)는 기기에서 안 쓴다 — 같은 검증 순서를 두 환경에서 검증/실행하려면
 * "SQLite로 열어서 관찰한다"는 한 가지 동작만 갈아끼울 수 있어야 한다
 * ([com.example.storymind.data.db.StoryDatabase]의 JVM 마이그레이션 테스트가 JDBC로 DDL을
 * 검증하는 것과 같은 이유).
 *
 * 열기·질의 중 어떤 예외를 던져도 된다 — [BackupValidator]가 "손상된 파일"로 수렴시킨다.
 */
fun interface BackupProbe {
    fun probe(file: File): ProbeReport
}

/** 거부 사유. UI 문구를 여기 두는 이유: 검증기가 사유를 잃어버리고 Boolean만 돌려주면
 * "왜 안 되는지"를 사용자에게 보여주라는 요구(복원 안전장치 a)를 만족할 수 없다. */
enum class BackupRejection(val userMessage: String) {
    NOT_SQLITE("SQLite 데이터베이스 파일이 아니에요."),
    CORRUPT("파일이 손상되어 읽을 수 없어요."),
    MISSING_CHAPTERS("StoryMind 백업이 아니에요 — 원고 테이블이 없어요."),
    NEWER_SCHEMA("더 새로운 버전의 앱에서 만든 백업이에요 — 앱을 업데이트한 뒤 다시 시도해 주세요."),
}

sealed interface BackupValidation {
    data object Valid : BackupValidation
    data class Invalid(val rejection: BackupRejection) : BackupValidation
}

/**
 * 복원 후보 파일의 단계적 검증. **여기서 거부되면 기존 데이터는 아무것도 바뀌지 않는다** —
 * 복원 플로우는 이 검증을 통과한 파일만 스테이징을 유지한다.
 *
 * 검사 순서에 이유가 있다:
 * 1. **헤더 매직 바이트** — SQLite도 아닌 파일(빈 파일, 텍스트, 다른 포맷)을 엔진 열기 시도
 *    없이 걸러낸다. 헤더 검사 없이 바로 열면 엔진·드라이버마다 실패 방식이 달라(예외 vs
 *    빈 DB 취급) 거부 사유가 불안정해진다.
 * 2. **integrity_check + chapters 테이블** — 진짜 SQLite 엔진([BackupProbe])으로 열어 페이지
 *    수준 손상과 "SQLite는 맞지만 StoryMind 백업이 아님"을 구분해 알려준다.
 * 3. **스키마 버전 상한** — `user_version`이 현재 앱이 아는 버전보다 크면 거부한다. Room은
 *    다운그레이드 마이그레이션이 없으면 열기 시점에 throw하는데, 복원은 파일을 교체한 *뒤*
 *    다음 실행에서 DB를 여는 구조라 이 검사가 없으면 미래 버전 백업 복원 → 매 실행 크래시
 *    루프가 되고, 사용자가 되돌릴 UI 자체가 뜨지 않는다. 사전에 거부하는 것만이 안전하다.
 */
object BackupValidator {

    /** SQLite 파일의 첫 16바이트: ASCII "SQLite format 3" + 0x00 (sqlite.org/fileformat2.html).
     * 문자열 리터럴 대신 바이트로 두는 이유: 마지막 바이트가 NUL이라 문자열로 쓰면 소스
     * 파일/에디터가 이를 깨뜨리기 쉽다. */
    private val SQLITE_MAGIC: ByteArray = byteArrayOf(
        0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66, // "SQLite f"
        0x6F, 0x72, 0x6D, 0x61, 0x74, 0x20, 0x33, 0x00, // "ormat 3\0"
    )

    fun validate(file: File, currentSchemaVersion: Int, probe: BackupProbe): BackupValidation {
        if (!hasSqliteMagic(file)) return BackupValidation.Invalid(BackupRejection.NOT_SQLITE)

        val report = try {
            probe.probe(file)
        } catch (_: Exception) {
            // 헤더는 맞지만 엔진이 열기/질의에 실패 — 잘린 파일, 위조 헤더 등. 어떤 예외 타입이
            // 오는지는 엔진(Android vs JDBC)마다 다르므로 넓게 잡는 게 맞다.
            return BackupValidation.Invalid(BackupRejection.CORRUPT)
        }

        return when {
            !report.integrityOk -> BackupValidation.Invalid(BackupRejection.CORRUPT)
            !report.hasChaptersTable -> BackupValidation.Invalid(BackupRejection.MISSING_CHAPTERS)
            report.userVersion > currentSchemaVersion ->
                BackupValidation.Invalid(BackupRejection.NEWER_SCHEMA)
            else -> BackupValidation.Valid
        }
    }

    private fun hasSqliteMagic(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_MAGIC.size) return false
        val header = ByteArray(SQLITE_MAGIC.size)
        file.inputStream().use { input ->
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n < 0) return false
                read += n
            }
        }
        return header.contentEquals(SQLITE_MAGIC)
    }
}
