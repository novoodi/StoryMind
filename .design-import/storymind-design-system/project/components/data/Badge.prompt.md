Pill-shaped semantic badge for Second Brain node types and content status. Color is derived automatically from `type`; use `label` to override text.

```jsx
// Node type badges
<Badge type="character" />            // 인물 (blue)
<Badge type="place" />                // 장소 (green)
<Badge type="item" />                 // 소품 (amber)
<Badge type="event" />                // 사건 (purple)
<Badge type="orphan" dot />           // 미연결 (red, dot pulses)

// Status badges
<Badge type="draft" />                // 초고 (gray)
<Badge type="complete" />             // 완성 (green)

// Custom label
<Badge type="character" label="주인공" size="lg" />
```

**Key:** orphan dot automatically pulses via `sm-pulse` keyframe — do not add extra animation.
