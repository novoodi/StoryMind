Floating pill badge for background AI task status. Renders nothing when `status="idle"`. Dot pulses during active states. Uses frosted-glass background for layering over content.

```jsx
// Shown top-right of editor toolbar
<StatusBadge status="analyzing" />
// → "살펴보는 중이에요" (blue, pulsing dot)

<StatusBadge status="warning" />
// → "확인이 필요해요" (red, pulsing dot)

<StatusBadge status="done" />
// → "완료됐어요" (green, solid dot)

<StatusBadge status="idle" />
// → renders nothing (null)
```

**Per spec:** never show a full-page spinner. Use this badge in the toolbar corner instead.
