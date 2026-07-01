Slide-up overlay sheet for AI conflict warnings and contextual actions. Animates in at 280ms ease-decelerate (per spec). Pair with the editor's `sm-shake` animation on the content area when showing a warning.

```jsx
<BottomSheet
  isOpen={showWarning}
  onClose={() => setShowWarning(false)}
  title="잠깐만요!"
  description="박민준이 2장에서 이미 자리를 떠났는데, 여기서 다시 등장하고 있어요."
  primaryAction={{ label: '설정 확인하기', onClick: () => openWiki() }}
  secondaryAction={{ label: '그냥 둘래요', onClick: () => setShowWarning(false) }}
/>
```

**Animation:** overlay fades in, sheet slides up — all at 280ms  
**Position:** `absolute` within a `position:relative` parent (the phone frame)  
**Backdrop:** `rgba(26,27,30,0.4)` + `blur(4px)` — per spec
