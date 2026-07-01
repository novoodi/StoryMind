Elevated content card for wiki entries, timeline logs, and any grouped information unit. Hover lifts to `shadow-md`; selected state shows a 2px brand-blue ring.

```jsx
// Wiki entry card
<Card
  title="김지우"
  badge={<Badge type="character" />}
  subtitle="1장 주인공 · 26세 · 작가 지망생"
  description="빗소리를 좋아하고 물을 두려워한다. 카페 달빛의 단골이다."
  meta="마지막 등장: 3장"
  onClick={() => openWiki('김지우')}
/>

// Log card (no click)
<Card
  subtitle="이야기의 앞뒤를 맞추고 있어요"
  meta="방금 전"
/>

// Selected state
<Card title="1장 — 빗소리" selected />
```
