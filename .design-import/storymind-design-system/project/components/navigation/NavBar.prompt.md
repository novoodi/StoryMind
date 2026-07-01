Fixed bottom tab bar for the main app navigation. 50px tall (spec-mandated), 22×22px Lucide-style icons, brand blue active state.

```jsx
const [tab, setTab] = React.useState('editor');

<NavBar activeTab={tab} onTabChange={setTab} />
```

**Tabs:** editor · brain · wiki · settings  
**Height:** 50px fixed + `env(safe-area-inset-bottom)` for iPhone home bar  
**Active:** brand blue `#1F4EF5`, weight 600 label  
**Inactive:** `--text-tertiary` `#9EA3B3`, weight 400 label
