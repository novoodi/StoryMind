import React from 'react';

export function BottomSheet({
  isOpen = false,
  onClose,
  title,
  description,
  primaryAction,
  secondaryAction,
  children,
  style = {},
}) {
  const [mounted, setMounted] = React.useState(isOpen);
  const [visible, setVisible] = React.useState(false);

  React.useEffect(() => {
    if (isOpen) {
      setMounted(true);
      const raf = requestAnimationFrame(() =>
        requestAnimationFrame(() => setVisible(true))
      );
      return () => cancelAnimationFrame(raf);
    } else {
      setVisible(false);
      const t = setTimeout(() => setMounted(false), 300);
      return () => clearTimeout(t);
    }
  }, [isOpen]);

  if (!mounted) return null;

  return React.createElement(React.Fragment, null,
    /* Backdrop */
    React.createElement('div', {
      onClick: onClose,
      style: {
        position: 'absolute',
        inset: 0,
        background: 'rgba(26, 27, 30, 0.4)',
        opacity: visible ? 1 : 0,
        transition: 'opacity 280ms var(--ease-standard)',
        zIndex: 100,
        backdropFilter: 'blur(4px)',
        WebkitBackdropFilter: 'blur(4px)',
      },
    }),

    /* Sheet */
    React.createElement('div', {
      style: {
        position: 'absolute',
        bottom: 0, left: 0, right: 0,
        background: 'var(--surface-base)',
        borderRadius: 'var(--radius-xl) var(--radius-xl) 0 0',
        padding: '12px 20px 36px',
        boxShadow: 'var(--shadow-float)',
        zIndex: 101,
        transform: visible ? 'translateY(0)' : 'translateY(100%)',
        transition: 'transform 280ms var(--ease-decelerate)',
        fontFamily: 'var(--font-sans)',
        ...style,
      },
    },
      /* Handle */
      React.createElement('div', {
        style: {
          width: '36px', height: '4px', borderRadius: '2px',
          background: 'var(--neutral-300)', margin: '0 auto 20px',
        },
      }),

      title ? React.createElement('h3', {
        style: {
          fontSize: '17px', fontWeight: '700', color: 'var(--text-primary)',
          margin: '0 0 8px', letterSpacing: 'var(--tracking-normal)',
        },
      }, title) : null,

      description ? React.createElement('p', {
        style: {
          fontSize: '14px', color: 'var(--text-secondary)',
          margin: '0 0 20px', lineHeight: '1.65',
          letterSpacing: 'var(--tracking-normal)',
        },
      }, description) : null,

      children || null,

      (primaryAction || secondaryAction) ? React.createElement('div', {
        style: {
          display: 'flex', flexDirection: 'column', gap: '10px',
          marginTop: (children || description) ? '20px' : '0',
        },
      },
        primaryAction ? React.createElement('button', {
          onClick: primaryAction.onClick,
          style: {
            height: 'var(--button-lg)', width: '100%',
            background: 'var(--color-brand)', color: '#fff',
            border: 'none', borderRadius: 'var(--radius-lg)',
            fontFamily: 'var(--font-sans)', fontSize: '16px', fontWeight: '600',
            cursor: 'pointer', letterSpacing: 'var(--tracking-normal)',
          },
        }, primaryAction.label) : null,

        secondaryAction ? React.createElement('button', {
          onClick: secondaryAction.onClick,
          style: {
            height: 'var(--button-md)', width: '100%',
            background: 'var(--neutral-100)', color: 'var(--text-secondary)',
            border: 'none', borderRadius: 'var(--radius-lg)',
            fontFamily: 'var(--font-sans)', fontSize: '15px', fontWeight: '500',
            cursor: 'pointer', letterSpacing: 'var(--tracking-normal)',
          },
        }, secondaryAction.label) : null,
      ) : null,
    ),
  );
}
