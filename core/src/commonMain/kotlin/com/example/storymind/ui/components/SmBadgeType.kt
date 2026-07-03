package com.example.storymind.ui.components

/**
 * 엔티티 타입(인물/장소/소품/사건 등)의 도메인 공용 enum. 이름은 UI 디자인 시스템
 * (Sm* 접두사)에서 왔지만 순수 Kotlin enum이라 ai/·data/에서도 공용으로 쓰인다 —
 * 그래서 Compose 렌더링(`SmBadge`, :app)과 분리되어 :core commonMain에 있다.
 * 패키지명은 KMP 이행 1단계의 "이동만, 리네임 없음" 원칙에 따라 유지했다.
 */
enum class SmBadgeType { Character, Place, Item, Event, Orphan, Draft, Complete, New }
