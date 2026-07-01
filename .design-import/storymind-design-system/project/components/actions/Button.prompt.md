Primary action button for StoryMind. Use for all tappable CTAs: primary (brand blue fill), secondary (outlined), ghost (text-only), danger (pastel red), soft (tinted blue).

```jsx
// Primary CTA — 53px tall per spec
<Button variant="primary" size="lg" fullWidth>소설 쓰기 시작하기</Button>

// Secondary (outlined)
<Button variant="secondary" size="md">설정 확인하기</Button>

// Ghost (low emphasis)
<Button variant="ghost" size="sm">그냥 둘래요</Button>

// Danger action (pastel red — never aggressive)
<Button variant="danger" size="md">설정 초기화</Button>

// Loading state
<Button variant="primary" size="lg" loading>저장 중이에요</Button>
```

**Props:** variant · size · disabled · loading · fullWidth · icon · iconAfter  
**Sizes:** sm 36px · md 44px · lg 53px (spec CTA)  
**Press:** scale(0.97) at 120ms — built in, no extra code needed
