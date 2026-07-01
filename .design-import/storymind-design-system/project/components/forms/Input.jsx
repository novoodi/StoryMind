import React from 'react';

const HEIGHTS = { sm: '36px', md: '44px', lg: '53px' };

export function Input({
  label,
  placeholder = '',
  value,
  defaultValue,
  onChange,
  type = 'text',
  helper,
  error,
  disabled = false,
  readOnly = false,
  icon = null,
  iconAfter = null,
  size = 'md',
  style = {},
  inputStyle = {},
  ...props
}) {
  const [focused, setFocused] = React.useState(false);

  const borderColor = error
    ? 'var(--red-400)'
    : focused
    ? 'var(--color-brand)'
    : 'var(--border-default)';

  const bg = disabled
    ? 'var(--neutral-100)'
    : error
    ? 'var(--surface-warning)'
    : 'var(--surface-base)';

  return React.createElement('div', {
    style: { display: 'flex', flexDirection: 'column', gap: '6px', fontFamily: 'var(--font-sans)', ...style },
  },
    label ? React.createElement('label', {
      style: {
        fontSize: '14px', fontWeight: '500',
        color: error ? 'var(--red-500)' : 'var(--text-primary)',
        letterSpacing: 'var(--tracking-normal)', lineHeight: '1',
      },
    }, label) : null,

    React.createElement('div', { style: { position: 'relative', display: 'flex', alignItems: 'center' } },
      icon ? React.createElement('span', {
        style: {
          position: 'absolute', left: '12px', zIndex: 1,
          color: focused ? 'var(--color-brand)' : 'var(--text-tertiary)',
          display: 'flex', alignItems: 'center', pointerEvents: 'none',
          transition: 'color 150ms',
        },
      }, icon) : null,

      React.createElement('input', {
        type, value, defaultValue, onChange, disabled, readOnly, placeholder,
        onFocus: () => setFocused(true),
        onBlur: () => setFocused(false),
        style: {
          width: '100%',
          height: HEIGHTS[size] || HEIGHTS.md,
          padding: icon ? '0 12px 0 40px' : iconAfter ? '0 40px 0 14px' : '0 14px',
          fontFamily: 'var(--font-sans)',
          fontSize: size === 'lg' ? '16px' : '15px',
          color: 'var(--text-primary)',
          background: bg,
          border: `1.5px solid ${borderColor}`,
          borderRadius: 'var(--radius-md)',
          outline: 'none',
          transition: 'border-color 150ms var(--ease-standard), background 150ms',
          boxSizing: 'border-box',
          opacity: disabled ? 0.5 : 1,
          letterSpacing: 'var(--tracking-normal)',
          ...inputStyle,
        },
        ...props,
      }),

      iconAfter ? React.createElement('span', {
        style: {
          position: 'absolute', right: '12px',
          color: 'var(--text-tertiary)', display: 'flex', alignItems: 'center', pointerEvents: 'none',
        },
      }, iconAfter) : null,
    ),

    (helper || error) ? React.createElement('span', {
      style: {
        fontSize: '12px',
        color: error ? 'var(--red-500)' : 'var(--text-tertiary)',
        letterSpacing: 'var(--tracking-normal)',
      },
    }, error || helper) : null,
  );
}
