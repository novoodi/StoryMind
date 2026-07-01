import React from 'react';

export function Card({
  title,
  subtitle,
  badge,
  description,
  meta,
  onClick,
  selected = false,
  padding,
  children,
  style = {},
  ...props
}) {
  const [hovered, setHovered] = React.useState(false);
  const isClickable = typeof onClick === 'function';

  const shadow = selected
    ? '0 0 0 2px var(--color-brand), var(--shadow-sm)'
    : hovered && isClickable
    ? 'var(--shadow-md)'
    : 'var(--shadow-card)';

  return React.createElement('div', {
    role: isClickable ? 'button' : undefined,
    tabIndex: isClickable ? 0 : undefined,
    onClick,
    onMouseEnter: isClickable ? () => setHovered(true) : undefined,
    onMouseLeave: isClickable ? () => setHovered(false) : undefined,
    style: {
      background: 'var(--surface-card)',
      borderRadius: 'var(--radius-lg)',
      padding: padding != null ? padding : '16px',
      boxShadow: shadow,
      cursor: isClickable ? 'pointer' : 'default',
      transition: 'box-shadow 200ms var(--ease-standard), transform 150ms var(--ease-standard)',
      transform: hovered && isClickable ? 'translateY(-1px)' : 'none',
      fontFamily: 'var(--font-sans)',
      ...style,
    },
    ...props,
  },
    /* Header row */
    (title || badge || subtitle) ? React.createElement('div', {
      style: { marginBottom: (description || children || meta) ? '8px' : 0 },
    },
      (title || badge) ? React.createElement('div', {
        style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' },
      },
        title ? React.createElement('span', {
          style: {
            fontWeight: '600', fontSize: '15px',
            color: 'var(--text-primary)', letterSpacing: 'var(--tracking-normal)', lineHeight: '1.4',
          },
        }, title) : null,
        badge || null,
      ) : null,
      subtitle ? React.createElement('p', {
        style: {
          fontSize: '13px', color: 'var(--text-secondary)',
          margin: title ? '3px 0 0' : '0',
          letterSpacing: 'var(--tracking-normal)', lineHeight: '1.5',
        },
      }, subtitle) : null,
    ) : null,
    /* Description */
    description ? React.createElement('p', {
      style: {
        fontSize: '13px', color: 'var(--text-tertiary)',
        margin: '0', lineHeight: '1.65', letterSpacing: 'var(--tracking-normal)',
      },
    }, description) : null,
    /* Slot */
    children || null,
    /* Meta footer */
    meta ? React.createElement('div', {
      style: {
        marginTop: '10px', paddingTop: '10px',
        borderTop: '1px solid var(--border-default)',
        fontSize: '12px', color: 'var(--text-tertiary)',
        letterSpacing: 'var(--tracking-normal)',
      },
    }, meta) : null,
  );
}
