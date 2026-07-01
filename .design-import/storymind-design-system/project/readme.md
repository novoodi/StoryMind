# StoryMind Design System

AI 소설 집필 어시스턴트 · 작가를 위한 모바일 앱 디자인 시스템

---

## 제품 개요 (Product Overview)

StoryMind는 작가들이 기술적 복잡함을 느끼지 않고 오직 **글쓰기에만 몰입**할 수 있도록 돕는 모바일 앱입니다. 백그라운드에서 돌아가는 복잡한 AI 연산(설정 충돌 검사, 위키 자동 생성, 세컨드 브레인 그래프 등)을 사용자에게 완벽히 은닉합니다.

**핵심 철학:** Toss의 Simplism — 기능을 학습할 필요 없는, 인지적 부담이 없는 UX

### 제품 원칙 (Product Principles)

| 원칙 | 설명 |
| --- | --- |
| **One Thing** | 한 화면 = 하나의 핵심 메시지 + 단일 CTA |
| **Value First, Cost Later** | 설정 요구 전에 AI가 만든 위키/그래프 가치를 먼저 보여줌 |
| **Easy to Answer** | 경고 시 3초 이내 직관적 선택지 제공 |
| **No More Loading** | 뻔한 스피너 금지 — 상태표시줄로 조용히 알림 |

---

## 소스 (Sources)

이 디자인 시스템은 다음 소스를 기반으로 작성되었습니다:

- **제품 명세서:** 마스터 프롬프트 문서 (AI 소설 집필 어시스턴트 맞춤형 토스 스타일)
- **참고 디자인 시스템:** Toss Design System (TDS) 원칙 및 수치 제약
- **외부 Figma / GitHub:** 첨부된 파일 없음 (명세 기반 재구성)

---

## 콘텐츠 기초 (Content Fundamentals)

### 어조 (Tone of Voice)

- **친근하고 다정한 대화체** — 기계적 에러 메시지 절대 금지
- **공감 우선** — "앗," "잠깐만요!" 등 자연스러운 감탄사 허용
- **극한의 간결함** — 수식어 제거, 의미 없는 단어 뽑아내기 (잡초 뽑기)

### 표기 규칙

- **경어체:** 모든 문구는 "\~해요", "\~이에요" 종결 (해요체)
- **Zero Jargon:** 기술 용어 완전 배제

| 금지 표현 | 대체 표현 |
| --- | --- |
| Linting 중 | 이야기의 앞뒤를 맞추고 있어요 |
| Orphan Node | 아직 연결되지 않은 이야기예요 |
| AI Ingest | 설정집을 정리하고 있어요 |
| Error detected | 앗, 작은 문제를 발견했어요 |
| Analyzing | 살펴보는 중이에요 |

### 경고 문구 예시

> "앗, 주인공은 물 공포증이 있는데 다이빙을 하네요?"\
> "잠깐만요! 박민준이 2장에서 이미 자리를 떠났는데, 여기서 다시 등장하고 있어요."

### 선택지 레이블

- 기본 유지: **"그냥 둘래요"**
- 확인/수정: **"설정 확인하기"**
- 확인: **"확인했어요"**

---

## 시각 기초 (Visual Foundations)

### 컬러 시스템

- **브랜드 블루** `#1F4EF5` (Toss Blue) — 버튼, CTA, 활성 상태
- **텍스트** `#1A1B1E` — 블루 톤이 미세하게 가미된 다크 그레이. 순수 검정 `#000000` 절대 금지
- **배경** `#FFFFFF` / `#F8F9FC` — 깨끗하고 밝은 화면
- **경고** `#FFF0F0` bg + `#EF4444` 텍스트 — 파스텔톤, 위협적이지 않게

#### 세컨드 브레인 노드 색상

| 노드 유형 | 색상 | 배경 |
| --- | --- | --- |
| 인물 (Character) | `#1F4EF5` Blue | `#EBF0FF` |
| 장소 (Place) | `#22C55E` Green | `#F0FFF4` |
| 소품 (Item) | `#F59E0B` Amber | `#FFFBEB` |
| 사건 (Event) | `#8B5CF6` Purple | `#F5F3FF` |
| 미연결 (Orphan) | `#EF4444` Red ✦ blink | `#FFF0F0` |

### 타이포그래피

- **서체:** Pretendard (기하학적 고딕 산세리프, 한국어 최적화)
- **H1 / 챕터 제목:** 30px 이상, weight 700, tracking -0.03em
- **본문 (에디터):** 18px, weight 400, line-height 1.8
- **UI 레이블:** 14-15px, weight 500-600
- **캡션 / AI 상태:** 12px, weight 400-500

### 레이아웃 수치 (엄격히 준수)

| 항목 | 값 |
| --- | --- |
| 대시보드 좌우 패딩 | 15px |
| 에디터 좌우 패딩 | 22px |
| 상단 툴바 높이 | 42px |
| 하단 네비게이션 | 50px (아이콘 22×22px) |
| 주요 버튼 높이 | 53–55px |
| 최소 터치 타겟 | 44px |

### 배경 & 카드

- **배경:** 순백 또는 `#F8F9FC` — 이미지·그라디언트 없음, 텍스처 없음
- **카드:** 흰 배경 + `box-shadow: 0 1px 4px rgba(26,27,30,.06), 0 0 0 1px rgba(26,27,30,.05)` + `border-radius: 14px`
- **테두리:** 1px `#E5E7EF` — 강조 필요 시만 사용

### 모서리 반경

- xs 4px · sm 6px · md 10px · lg 14px · xl 18px · full 9999px

### 그림자

얕고 부드러운 그림자. 블루 톤의 어두운 기반색(`rgba(26,27,30,…)`)으로 자연스럽게.

### 애니메이션

| 상황 | 수치 |
| --- | --- |
| 버튼 press scale | scale(0.97), 120ms ease |
| 드로어 slide-in | 280ms ease-out |
| 바텀 시트 | 280ms cubic-bezier(0,0,0.2,1) |
| 경고 shake | 200ms ease-out |
| 고립 노드 점멸 | 1.5s infinite pulse |
| 일반 전환 | 150-200ms ease-standard |

### hover / press / disabled

- hover: `filter: brightness(0.94)`
- press: `transform: scale(0.97)`, 120ms
- disabled: `opacity: 0.38`

### 투명도 & blur

- 플로팅 상태 표시줄: `background: rgba(248,249,252,0.92)` + `backdrop-filter: blur(8px)`
- 바텀 시트 오버레이: `rgba(26,27,30,0.4)` + `backdrop-filter: blur(4px)`

---

## 아이코노그래피 (Iconography)

- **아이콘 시스템:** Lucide Icons (line style, 1.8px stroke, round caps/joins)
- **크기:** NavBar 22×22px, 툴바 20×20px, 인라인 16×16px
- **색상:** 활성 `#1F4EF5`, 비활성 `#9EA3B3`
- **Emoji:** 사용 안 함 (Zero Jargon 원칙)
- **커스텀 SVG:** 세컨드 브레인 노드 그래프, 앱 아이콘

### 로고 파일

| 파일 | 용도 |
| --- | --- |
| `assets/logo.svg` | 아이콘 마크 (앱 아이콘, favicon) |
| `assets/wordmark.svg` | 전체 워드마크 (온보딩, 마케팅) |

---

## 파일 구조 (File Index)

```
styles.css                     ← 글로벌 CSS 진입점 (import only)
tokens/
  fonts.css                    ← Pretendard @import + body reset
  colors.css                   ← 컬러 팔레트 + 시맨틱 토큰
  typography.css               ← 서체·크기·굵기·줄간격
  spacing.css                  ← 간격·레이아웃 수치·radius
  effects.css                  ← 그림자·이징·duration·keyframes
assets/
  logo.svg                     ← 앱 아이콘 마크
  wordmark.svg                 ← 전체 워드마크
guidelines/                    ← 디자인 시스템 탭 카드 (specimen)
components/
  actions/   Button            ← CTA, 주요 액션 버튼
  data/      Badge, Card       ← 노드 뱃지, 위키 카드
  forms/     Input             ← 텍스트 입력
  navigation/NavBar            ← 하단 탭 바
  feedback/  BottomSheet,      ← 경고 시트, AI 상태 표시
             StatusBadge
ui_kits/
  novel_app/index.html         ← 풀 인터랙티브 모바일 프로토타입
readme.md                      ← 이 파일
SKILL.md                       ← Claude Code 스킬 정의
```

---

## 컴포넌트 목록 (Components)

| 컴포넌트 | 위치 | 설명 |
| --- | --- | --- |
| `Button` | `components/actions/` | Primary/Secondary/Ghost/Danger · sm/md/lg |
| `Badge` | `components/data/` | 노드 유형 뱃지 (인물/장소/소품/사건/미연결) |
| `Card` | `components/data/` | 위키 항목 카드, 타임라인 로그 카드 |
| `Input` | `components/forms/` | 텍스트 입력, 레이블, 에러 상태 |
| `NavBar` | `components/navigation/` | 4탭 하단 네비게이션 바 |
| `BottomSheet` | `components/feedback/` | 경고/액션 바텀 시트 |
| `StatusBadge` | `components/feedback/` | AI 처리 상태 표시 배지 |

---

## 주의사항 (Caveats)

1. **Pretendard 폰트:** jsDelivr CDN으로 로드됩니다. 프로덕션 배포 시 라이선스를 확인하고 서체 파일을 직접 호스팅하는 것을 권장합니다. ([GitHub: orioncactus/pretendard](https://github.com/orioncactus/pretendard))
2. **아이콘:** Lucide Icons은 CDN 참조 방식입니다. 프로덕션에서는 SVG 파일을 `assets/icons/`에 복사하세요.
3. **세컨드 브레인 그래프:** UI 키트의 그래프 시각화는 프로토타입입니다. 실 데이터는 D3.js 또는 네이티브 그래프 라이브러리와 연동이 필요합니다.
