import React from 'react';

/* ── Inline SVG icons (Lucide-style, 1.8px stroke, round caps) ── */
function EditIcon() {
  return React.createElement('svg', { width: '22', height: '22', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: '1.8', strokeLinecap: 'round', strokeLinejoin: 'round' },
    React.createElement('path', { d: 'M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7' }),
    React.createElement('path', { d: 'M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z' }),
  );
}

function BrainIcon() {
  return React.createElement('svg', { width: '22', height: '22', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: '1.8', strokeLinecap: 'round', strokeLinejoin: 'round' },
    React.createElement('circle', { cx: '12', cy: '12', r: '3' }),
    React.createElement('line', { x1: '12', y1: '2', x2: '12', y2: '9' }),
    React.createElement('line', { x1: '12', y1: '15', x2: '12', y2: '22' }),
    React.createElement('line', { x1: '4.22', y1: '4.22', x2: '9.17', y2: '9.17' }),
    React.createElement('line', { x1: '14.83', y1: '14.83', x2: '19.78', y2: '19.78' }),
    React.createElement('line', { x1: '2', y1: '12', x2: '9', y2: '12' }),
    React.createElement('line', { x1: '15', y1: '12', x2: '22', y2: '12' }),
    React.createElement('line', { x1: '4.22', y1: '19.78', x2: '9.17', y2: '14.83' }),
    React.createElement('line', { x1: '14.83', y1: '9.17', x2: '19.78', y2: '4.22' }),
  );
}

function BookIcon() {
  return React.createElement('svg', { width: '22', height: '22', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: '1.8', strokeLinecap: 'round', strokeLinejoin: 'round' },
    React.createElement('path', { d: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20' }),
    React.createElement('path', { d: 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z' }),
  );
}

function GearIcon() {
  return React.createElement('svg', { width: '22', height: '22', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: '1.8', strokeLinecap: 'round', strokeLinejoin: 'round' },
    React.createElement('circle', { cx: '12', cy: '12', r: '3' }),
    React.createElement('path', { d: 'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z' }),
  );
}

const TABS = [
  { id: 'editor',   Icon: EditIcon,  label: '에디터' },
  { id: 'brain',    Icon: BrainIcon, label: '브레인' },
  { id: 'wiki',     Icon: BookIcon,  label: '위키' },
  { id: 'settings', Icon: GearIcon,  label: '설정' },
];

export function NavBar({ activeTab = 'editor', onTabChange, style = {} }) {
  return React.createElement('nav', {
    'aria-label': '주 내비게이션',
    style: {
      height: 'var(--layout-nav)',
      background: 'var(--surface-base)',
      borderTop: '1px solid var(--border-default)',
      display: 'flex',
      alignItems: 'stretch',
      paddingBottom: 'env(safe-area-inset-bottom, 0px)',
      flexShrink: 0,
      ...style,
    },
  },
    ...TABS.map(({ id, Icon, label }) => {
      const active = activeTab === id;
      return React.createElement('button', {
        key: id,
        onClick: () => onTabChange && onTabChange(id),
        'aria-current': active ? 'page' : undefined,
        style: {
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '3px',
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          color: active ? 'var(--color-brand)' : 'var(--text-tertiary)',
          padding: '0',
          transition: 'color 150ms var(--ease-standard)',
          WebkitTapHighlightColor: 'transparent',
          fontFamily: 'var(--font-sans)',
        },
      },
        React.createElement(Icon),
        React.createElement('span', {
          style: {
            fontSize: '10px',
            fontWeight: active ? '600' : '400',
            letterSpacing: 'var(--tracking-normal)',
            lineHeight: '1',
          },
        }, label),
      );
    }),
  );
}
