---
name: storymind-design
description: Use this skill to generate well-branded interfaces and assets for StoryMind — an AI-powered novel writing assistant app. Contains essential design guidelines, color tokens, typography (Pretendard), spacing rules, micro-interaction specs, and UI kit components for the mobile app.
user-invocable: true
---

Read the README.md file within this skill, and explore the other available files.

If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets out and create static HTML files for the user to view. Load `styles.css` for all design tokens.

Key design rules to always follow:
- Brand blue: `#1F4EF5` for all interactive elements and CTAs
- Text: `#1A1B1E` — NEVER pure black `#000000`
- Font: Pretendard (`@import` from jsDelivr CDN)
- Toolbar height: 42px · Nav height: 50px · Primary button: 53px
- Dashboard padding: 15px · Editor padding: 22px
- Warning UI: pastel `#FFF0F0` bg — never aggressive red
- Korean UX copy: zero jargon, friendly "~해요" tone
- Animations: 280ms ease-out for sheets/drawers, 200ms shake for warnings

If the user invokes this skill without any other guidance, ask them what they want to build or design, ask some questions, and act as an expert mobile product designer who outputs HTML artifacts or production React Native / SwiftUI code, depending on the need.
