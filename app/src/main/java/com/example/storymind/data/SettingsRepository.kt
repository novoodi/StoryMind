package com.example.storymind.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 앱 환경설정의 영속 저장소. SharedPreferences 기반인 이유가 둘이다:
 *
 * 1. 이전 설정 화면의 토글들은 `remember { mutableStateOf(...) }`로만 살아 있어서 탭 전환은
 *    물론 화면 재구성에서도 초기값으로 되돌아갔다 — 즉 눌러도 아무것도 안 바뀌는 것처럼
 *    보였다. 설정이 "설정"이려면 프로세스 사망을 넘어 남아야 한다.
 * 2. [autoAnalyze]는 [com.example.storymind.ui.StoryViewModel.saveAndIngest]가 저장 시점에
 *    **동기적으로** 읽어 인제스트를 걸지 말지 결정해야 한다. 비동기 전용인 DataStore로는
 *    그 지점에서 최신 값을 즉시 못 읽으므로, 값이 몇 개뿐인 여기선 SharedPreferences가 맞다.
 *
 * 각 설정은 디스크(apply, 베스트 에포트)와 인메모리 [StateFlow](동기 읽기·UI 관찰용)를 함께
 * 갱신한다 — 같은 프로세스 안의 읽기는 항상 일관되고, UI는 Flow로 관찰한다.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _autoAnalyze = MutableStateFlow(prefs.getBoolean(KEY_AUTO_ANALYZE, true))
    /** 저장 시 온디바이스 인제스트를 자동으로 돌릴지. 끄면 원고만 저장되고 위키/그래프는
     * 그대로 — 나중에 다시 켜거나 설정의 "AI 데이터 재구축"으로 채울 수 있다. */
    val autoAnalyze: StateFlow<Boolean> = _autoAnalyze

    private val _spellCheck = MutableStateFlow(prefs.getBoolean(KEY_SPELL_CHECK, true))
    /** 에디터 입력의 자동 교정(맞춤법) 사용 여부 — 에디터 텍스트필드의 IME 힌트로 전달된다. */
    val spellCheck: StateFlow<Boolean> = _spellCheck

    fun setAutoAnalyze(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ANALYZE, enabled).apply()
        _autoAnalyze.value = enabled
    }

    fun setSpellCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPELL_CHECK, enabled).apply()
        _spellCheck.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "storymind_settings"
        const val KEY_AUTO_ANALYZE = "auto_analyze"
        const val KEY_SPELL_CHECK = "spell_check"
    }
}
