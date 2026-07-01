import React from 'react';

const TYPE_CONFIG = {
  character: { bg: 'var(--node-character-bg)', color: 'var(--node-character)', label: '인물' },
  place:     { bg: 'var(--node-place-bg)',     color: 'var(--node-place)',     label: '장소' },
  item:      { bg: 'var(--node-item-bg)',       color: 'var(--node-item)',      label: '소품' },
  event:     { bg: 'var(--node-event-bg)',      color: 'var(--node-event)',     label: '사건' },
  orphan:    { bg: 'var(--node-orphan-bg)',     color: 'var(--node-orphan)',    label: '미연결' },
  draft:     { bg: 'var(--neutral-100)',        color: 'var(--text-secondary)', label: '초고' },
  complete:  { bg: 'var(--green-50)',           color: 'var(--green-600)',      label: '완성' },
  new:       { bg: 'var(--blue-50)',            color: 'var(--blue-600)',       label: '신규' },
};

export function Badge({ type = 'draft', label, dot = false, size = 'sm', style = {}, ...props }) {
  const config = TYPE_CONFIG[type] || TYPE_CONFIG.draft;
  const text = label != null ? label : config.label;
  const isLg = size === 'lg';

  return React.createElement('span', {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: '4px',
      padding: isLg ? '4px 10px' : '3px 8px',
      borderRadius: 'var(--radius-full)',
      fontSize: isLg ? '12px' : '11px',
      fontWeight: '600',
      fontFamily: 'var(--font-sans)',
      letterSpacing: 'var(--tracking-normal)',
      background: config.bg,
      color: config.color,
      lineHeight: '1.3',
      whiteSpace: 'nowrap',
      ...style,
    },
    ...props,
  },
    dot ? React.createElement('span', {
      style: {
        width: isLg ? '6px' : '5px',
        height: isLg ? '6px' : '5px',
        borderRadius: '50%',
        background: config.color,
        flexShrink: 0,
        animation: type === 'orphan' ? 'sm-pulse 1.5s ease-in-out infinite' : 'none',
      },
    }) : null,
    text,
  );
}
