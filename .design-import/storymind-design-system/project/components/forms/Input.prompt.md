Controlled text input with label, helper text, and error state. Border animates to brand blue on focus, red on error. Background shifts to `surface-warning` on error.

```jsx
// Default
<Input label="챕터 제목" placeholder="제목을 입력하세요" value={val} onChange={e => setVal(e.target.value)} />

// With error
<Input label="작가명" error="이미 사용 중인 이름이에요" value={val} />

// With icon
<Input
  placeholder="인물, 장소 검색..."
  icon={<SearchIcon />}
  size="md"
/>

// Disabled
<Input label="이메일" value="user@example.com" disabled />
```

**Sizes:** sm 36px · md 44px · lg 53px  
**States:** default · focused (blue border) · error (red border + bg) · disabled (50% opacity)
