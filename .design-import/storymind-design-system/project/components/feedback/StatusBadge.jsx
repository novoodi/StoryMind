import React from 'react';

const STATUS = {
  analyzing: { label: '살펴보는 중이에요', color: 'var(--color-brand)',  pulse: true },
  syncing:   { label: '저장하고 있어요',   color: 'var(--color-brand)',  pulse: true },
  warning:   { label: '확인이 필요해요',   color: 'var(--red-500)',      pulse: true },
  done:      { label: '완료됐어요',        color: 'var(--green-600)',    pulse: false },
  idle:      { label: null,               color: null,                  pulse: false },
};

export function StatusBadge({ status = 'idle', label, style = {} }) {
  const config = STATUS[status] || STATUS.idle;
  const text = label != null ? label : config.label;

  if (!text) return null;

  return React.createElement('div', {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: '6px',
      padding: '4px 10px 4px 8px',
      borderRadius: 'var(--radius-full)',
      background: 'rgba(248, 249, 252, 0.92)',
      backdropFilter: 'blur(var(--blur-sm))',
      WebkitBackdropFilter: 'blur(var(--blur-sm))',
      border: '1px solid var(--border-default)',
      boxShadow: 'var(--shadow-sm)',
      fontFamily: 'var(--font-sans)',
      ...style,
    },
  },
    React.createElement('span', {
      style: {
        width: '6px', height: '6px', borderRadius: '50%',
        background: config.color,
        flexShrink: 0,
        animation: config.pulse ? 'sm-pulse 1.5s ease-in-out infinite' : 'none',
      },
    }),
    React.createElement('span', {
      style: {
        fontSize: '12px', fontWeight: '500',
        color: config.color,
        letterSpacing: 'var(--tracking-normal)',
        lineHeight: '1',
      },
    }, text),
  );
}
