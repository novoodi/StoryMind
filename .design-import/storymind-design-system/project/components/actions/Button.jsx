import React from 'react';

const SIZES = {
  sm: { height: '36px', padding: '0 14px', fontSize: '13px', borderRadius: 'var(--radius-md)', gap: '5px' },
  md: { height: '44px', padding: '0 18px', fontSize: '15px', borderRadius: 'var(--radius-lg)', gap: '6px' },
  lg: { height: '53px', padding: '0 24px', fontSize: '16px', borderRadius: 'var(--radius-lg)', gap: '7px' },
};

const VARIANTS = {
  primary:   { background: 'var(--color-brand)',        color: '#fff',                      border: 'none' },
  secondary: { background: 'transparent',               color: 'var(--color-brand)',         border: '1.5px solid var(--color-brand)' },
  ghost:     { background: 'transparent',               color: 'var(--text-primary)',        border: 'none' },
  danger:    { background: 'var(--surface-warning)',    color: 'var(--red-500)',             border: 'none' },
  soft:      { background: 'var(--color-brand-subtle)', color: 'var(--color-brand)',         border: 'none' },
};

const Spinner = () => (
  React.createElement('svg', {
    width: '16', height: '16', viewBox: '0 0 24 24', fill: 'none',
    stroke: 'currentColor', strokeWidth: '2.5', strokeLinecap: 'round',
    style: { animation: 'sm-spin 0.9s linear infinite', flexShrink: 0 },
  },
    React.createElement('path', { d: 'M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83' })
  )
);

export function Button({
  variant = 'primary',
  size = 'md',
  disabled = false,
  loading = false,
  fullWidth = false,
  icon = null,
  iconAfter = null,
  children,
  onClick,
  style = {},
  type = 'button',
  ...props
}) {
  const [active, setActive] = React.useState(false);
  const sizeStyle = SIZES[size] || SIZES.md;
  const variantStyle = VARIANTS[variant] || VARIANTS.primary;

  return React.createElement('button', {
    type,
    disabled: disabled || loading,
    onClick: (disabled || loading) ? undefined : onClick,
    onMouseDown: () => setActive(true),
    onMouseUp: () => setActive(false),
    onMouseLeave: () => setActive(false),
    onTouchStart: () => setActive(true),
    onTouchEnd: () => setActive(false),
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontFamily: 'var(--font-sans)',
      fontWeight: '600',
      letterSpacing: 'var(--tracking-normal)',
      cursor: (disabled || loading) ? 'not-allowed' : 'pointer',
      userSelect: 'none',
      WebkitTapHighlightColor: 'transparent',
      width: fullWidth ? '100%' : 'auto',
      opacity: disabled ? 0.38 : 1,
      transform: active && !disabled ? 'scale(0.97)' : 'scale(1)',
      transition: 'transform 120ms var(--ease-standard), opacity 120ms, filter 120ms',
      filter: 'none',
      textDecoration: 'none',
      ...sizeStyle,
      ...variantStyle,
      ...style,
    },
    ...props,
  },
    loading ? React.createElement(Spinner) : icon,
    children ? React.createElement('span', null, children) : null,
    !loading ? iconAfter : null,
  );
}
