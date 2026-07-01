/* @ds-bundle: {"format":3,"namespace":"StoryMindDesignSystem_7de6de","components":[{"name":"Button","sourcePath":"components/actions/Button.jsx"},{"name":"Badge","sourcePath":"components/data/Badge.jsx"},{"name":"Card","sourcePath":"components/data/Card.jsx"},{"name":"BottomSheet","sourcePath":"components/feedback/BottomSheet.jsx"},{"name":"StatusBadge","sourcePath":"components/feedback/StatusBadge.jsx"},{"name":"Input","sourcePath":"components/forms/Input.jsx"},{"name":"NavBar","sourcePath":"components/navigation/NavBar.jsx"}],"sourceHashes":{"components/actions/Button.jsx":"2c0aa16819a9","components/data/Badge.jsx":"95e125f4c57f","components/data/Card.jsx":"52ede7fabd90","components/feedback/BottomSheet.jsx":"26eda941187d","components/feedback/StatusBadge.jsx":"4b3d4e0a4982","components/forms/Input.jsx":"be42b98a2b7f","components/navigation/NavBar.jsx":"fe696d3b380a","ui_kits/novel_app/tweaks-panel.jsx":"6591467622ed"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.StoryMindDesignSystem_7de6de = window.StoryMindDesignSystem_7de6de || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// components/actions/Button.jsx
try { (() => {
const SIZES = {
  sm: {
    height: '36px',
    padding: '0 14px',
    fontSize: '13px',
    borderRadius: 'var(--radius-md)',
    gap: '5px'
  },
  md: {
    height: '44px',
    padding: '0 18px',
    fontSize: '15px',
    borderRadius: 'var(--radius-lg)',
    gap: '6px'
  },
  lg: {
    height: '53px',
    padding: '0 24px',
    fontSize: '16px',
    borderRadius: 'var(--radius-lg)',
    gap: '7px'
  }
};
const VARIANTS = {
  primary: {
    background: 'var(--color-brand)',
    color: '#fff',
    border: 'none'
  },
  secondary: {
    background: 'transparent',
    color: 'var(--color-brand)',
    border: '1.5px solid var(--color-brand)'
  },
  ghost: {
    background: 'transparent',
    color: 'var(--text-primary)',
    border: 'none'
  },
  danger: {
    background: 'var(--surface-warning)',
    color: 'var(--red-500)',
    border: 'none'
  },
  soft: {
    background: 'var(--color-brand-subtle)',
    color: 'var(--color-brand)',
    border: 'none'
  }
};
const Spinner = () => React.createElement('svg', {
  width: '16',
  height: '16',
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: '2.5',
  strokeLinecap: 'round',
  style: {
    animation: 'sm-spin 0.9s linear infinite',
    flexShrink: 0
  }
}, React.createElement('path', {
  d: 'M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83'
}));
function Button({
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
    onClick: disabled || loading ? undefined : onClick,
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
      cursor: disabled || loading ? 'not-allowed' : 'pointer',
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
      ...style
    },
    ...props
  }, loading ? React.createElement(Spinner) : icon, children ? React.createElement('span', null, children) : null, !loading ? iconAfter : null);
}
Object.assign(__ds_scope, { Button });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/actions/Button.jsx", error: String((e && e.message) || e) }); }

// components/data/Badge.jsx
try { (() => {
const TYPE_CONFIG = {
  character: {
    bg: 'var(--node-character-bg)',
    color: 'var(--node-character)',
    label: '인물'
  },
  place: {
    bg: 'var(--node-place-bg)',
    color: 'var(--node-place)',
    label: '장소'
  },
  item: {
    bg: 'var(--node-item-bg)',
    color: 'var(--node-item)',
    label: '소품'
  },
  event: {
    bg: 'var(--node-event-bg)',
    color: 'var(--node-event)',
    label: '사건'
  },
  orphan: {
    bg: 'var(--node-orphan-bg)',
    color: 'var(--node-orphan)',
    label: '미연결'
  },
  draft: {
    bg: 'var(--neutral-100)',
    color: 'var(--text-secondary)',
    label: '초고'
  },
  complete: {
    bg: 'var(--green-50)',
    color: 'var(--green-600)',
    label: '완성'
  },
  new: {
    bg: 'var(--blue-50)',
    color: 'var(--blue-600)',
    label: '신규'
  }
};
function Badge({
  type = 'draft',
  label,
  dot = false,
  size = 'sm',
  style = {},
  ...props
}) {
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
      ...style
    },
    ...props
  }, dot ? React.createElement('span', {
    style: {
      width: isLg ? '6px' : '5px',
      height: isLg ? '6px' : '5px',
      borderRadius: '50%',
      background: config.color,
      flexShrink: 0,
      animation: type === 'orphan' ? 'sm-pulse 1.5s ease-in-out infinite' : 'none'
    }
  }) : null, text);
}
Object.assign(__ds_scope, { Badge });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/data/Badge.jsx", error: String((e && e.message) || e) }); }

// components/data/Card.jsx
try { (() => {
function Card({
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
  const shadow = selected ? '0 0 0 2px var(--color-brand), var(--shadow-sm)' : hovered && isClickable ? 'var(--shadow-md)' : 'var(--shadow-card)';
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
      ...style
    },
    ...props
  }, /* Header row */
  title || badge || subtitle ? React.createElement('div', {
    style: {
      marginBottom: description || children || meta ? '8px' : 0
    }
  }, title || badge ? React.createElement('div', {
    style: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: '8px'
    }
  }, title ? React.createElement('span', {
    style: {
      fontWeight: '600',
      fontSize: '15px',
      color: 'var(--text-primary)',
      letterSpacing: 'var(--tracking-normal)',
      lineHeight: '1.4'
    }
  }, title) : null, badge || null) : null, subtitle ? React.createElement('p', {
    style: {
      fontSize: '13px',
      color: 'var(--text-secondary)',
      margin: title ? '3px 0 0' : '0',
      letterSpacing: 'var(--tracking-normal)',
      lineHeight: '1.5'
    }
  }, subtitle) : null) : null, /* Description */
  description ? React.createElement('p', {
    style: {
      fontSize: '13px',
      color: 'var(--text-tertiary)',
      margin: '0',
      lineHeight: '1.65',
      letterSpacing: 'var(--tracking-normal)'
    }
  }, description) : null, /* Slot */
  children || null, /* Meta footer */
  meta ? React.createElement('div', {
    style: {
      marginTop: '10px',
      paddingTop: '10px',
      borderTop: '1px solid var(--border-default)',
      fontSize: '12px',
      color: 'var(--text-tertiary)',
      letterSpacing: 'var(--tracking-normal)'
    }
  }, meta) : null);
}
Object.assign(__ds_scope, { Card });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/data/Card.jsx", error: String((e && e.message) || e) }); }

// components/feedback/BottomSheet.jsx
try { (() => {
function BottomSheet({
  isOpen = false,
  onClose,
  title,
  description,
  primaryAction,
  secondaryAction,
  children,
  style = {}
}) {
  const [mounted, setMounted] = React.useState(isOpen);
  const [visible, setVisible] = React.useState(false);
  React.useEffect(() => {
    if (isOpen) {
      setMounted(true);
      const raf = requestAnimationFrame(() => requestAnimationFrame(() => setVisible(true)));
      return () => cancelAnimationFrame(raf);
    } else {
      setVisible(false);
      const t = setTimeout(() => setMounted(false), 300);
      return () => clearTimeout(t);
    }
  }, [isOpen]);
  if (!mounted) return null;
  return React.createElement(React.Fragment, null, /* Backdrop */
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
      WebkitBackdropFilter: 'blur(4px)'
    }
  }), /* Sheet */
  React.createElement('div', {
    style: {
      position: 'absolute',
      bottom: 0,
      left: 0,
      right: 0,
      background: 'var(--surface-base)',
      borderRadius: 'var(--radius-xl) var(--radius-xl) 0 0',
      padding: '12px 20px 36px',
      boxShadow: 'var(--shadow-float)',
      zIndex: 101,
      transform: visible ? 'translateY(0)' : 'translateY(100%)',
      transition: 'transform 280ms var(--ease-decelerate)',
      fontFamily: 'var(--font-sans)',
      ...style
    }
  }, /* Handle */
  React.createElement('div', {
    style: {
      width: '36px',
      height: '4px',
      borderRadius: '2px',
      background: 'var(--neutral-300)',
      margin: '0 auto 20px'
    }
  }), title ? React.createElement('h3', {
    style: {
      fontSize: '17px',
      fontWeight: '700',
      color: 'var(--text-primary)',
      margin: '0 0 8px',
      letterSpacing: 'var(--tracking-normal)'
    }
  }, title) : null, description ? React.createElement('p', {
    style: {
      fontSize: '14px',
      color: 'var(--text-secondary)',
      margin: '0 0 20px',
      lineHeight: '1.65',
      letterSpacing: 'var(--tracking-normal)'
    }
  }, description) : null, children || null, primaryAction || secondaryAction ? React.createElement('div', {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: '10px',
      marginTop: children || description ? '20px' : '0'
    }
  }, primaryAction ? React.createElement('button', {
    onClick: primaryAction.onClick,
    style: {
      height: 'var(--button-lg)',
      width: '100%',
      background: 'var(--color-brand)',
      color: '#fff',
      border: 'none',
      borderRadius: 'var(--radius-lg)',
      fontFamily: 'var(--font-sans)',
      fontSize: '16px',
      fontWeight: '600',
      cursor: 'pointer',
      letterSpacing: 'var(--tracking-normal)'
    }
  }, primaryAction.label) : null, secondaryAction ? React.createElement('button', {
    onClick: secondaryAction.onClick,
    style: {
      height: 'var(--button-md)',
      width: '100%',
      background: 'var(--neutral-100)',
      color: 'var(--text-secondary)',
      border: 'none',
      borderRadius: 'var(--radius-lg)',
      fontFamily: 'var(--font-sans)',
      fontSize: '15px',
      fontWeight: '500',
      cursor: 'pointer',
      letterSpacing: 'var(--tracking-normal)'
    }
  }, secondaryAction.label) : null) : null));
}
Object.assign(__ds_scope, { BottomSheet });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/BottomSheet.jsx", error: String((e && e.message) || e) }); }

// components/feedback/StatusBadge.jsx
try { (() => {
const STATUS = {
  analyzing: {
    label: '살펴보는 중이에요',
    color: 'var(--color-brand)',
    pulse: true
  },
  syncing: {
    label: '저장하고 있어요',
    color: 'var(--color-brand)',
    pulse: true
  },
  warning: {
    label: '확인이 필요해요',
    color: 'var(--red-500)',
    pulse: true
  },
  done: {
    label: '완료됐어요',
    color: 'var(--green-600)',
    pulse: false
  },
  idle: {
    label: null,
    color: null,
    pulse: false
  }
};
function StatusBadge({
  status = 'idle',
  label,
  style = {}
}) {
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
      ...style
    }
  }, React.createElement('span', {
    style: {
      width: '6px',
      height: '6px',
      borderRadius: '50%',
      background: config.color,
      flexShrink: 0,
      animation: config.pulse ? 'sm-pulse 1.5s ease-in-out infinite' : 'none'
    }
  }), React.createElement('span', {
    style: {
      fontSize: '12px',
      fontWeight: '500',
      color: config.color,
      letterSpacing: 'var(--tracking-normal)',
      lineHeight: '1'
    }
  }, text));
}
Object.assign(__ds_scope, { StatusBadge });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/StatusBadge.jsx", error: String((e && e.message) || e) }); }

// components/forms/Input.jsx
try { (() => {
const HEIGHTS = {
  sm: '36px',
  md: '44px',
  lg: '53px'
};
function Input({
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
  const borderColor = error ? 'var(--red-400)' : focused ? 'var(--color-brand)' : 'var(--border-default)';
  const bg = disabled ? 'var(--neutral-100)' : error ? 'var(--surface-warning)' : 'var(--surface-base)';
  return React.createElement('div', {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: '6px',
      fontFamily: 'var(--font-sans)',
      ...style
    }
  }, label ? React.createElement('label', {
    style: {
      fontSize: '14px',
      fontWeight: '500',
      color: error ? 'var(--red-500)' : 'var(--text-primary)',
      letterSpacing: 'var(--tracking-normal)',
      lineHeight: '1'
    }
  }, label) : null, React.createElement('div', {
    style: {
      position: 'relative',
      display: 'flex',
      alignItems: 'center'
    }
  }, icon ? React.createElement('span', {
    style: {
      position: 'absolute',
      left: '12px',
      zIndex: 1,
      color: focused ? 'var(--color-brand)' : 'var(--text-tertiary)',
      display: 'flex',
      alignItems: 'center',
      pointerEvents: 'none',
      transition: 'color 150ms'
    }
  }, icon) : null, React.createElement('input', {
    type,
    value,
    defaultValue,
    onChange,
    disabled,
    readOnly,
    placeholder,
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
      ...inputStyle
    },
    ...props
  }), iconAfter ? React.createElement('span', {
    style: {
      position: 'absolute',
      right: '12px',
      color: 'var(--text-tertiary)',
      display: 'flex',
      alignItems: 'center',
      pointerEvents: 'none'
    }
  }, iconAfter) : null), helper || error ? React.createElement('span', {
    style: {
      fontSize: '12px',
      color: error ? 'var(--red-500)' : 'var(--text-tertiary)',
      letterSpacing: 'var(--tracking-normal)'
    }
  }, error || helper) : null);
}
Object.assign(__ds_scope, { Input });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Input.jsx", error: String((e && e.message) || e) }); }

// components/navigation/NavBar.jsx
try { (() => {
/* ── Inline SVG icons (Lucide-style, 1.8px stroke, round caps) ── */
function EditIcon() {
  return React.createElement('svg', {
    width: '22',
    height: '22',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: '1.8',
    strokeLinecap: 'round',
    strokeLinejoin: 'round'
  }, React.createElement('path', {
    d: 'M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7'
  }), React.createElement('path', {
    d: 'M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z'
  }));
}
function BrainIcon() {
  return React.createElement('svg', {
    width: '22',
    height: '22',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: '1.8',
    strokeLinecap: 'round',
    strokeLinejoin: 'round'
  }, React.createElement('circle', {
    cx: '12',
    cy: '12',
    r: '3'
  }), React.createElement('line', {
    x1: '12',
    y1: '2',
    x2: '12',
    y2: '9'
  }), React.createElement('line', {
    x1: '12',
    y1: '15',
    x2: '12',
    y2: '22'
  }), React.createElement('line', {
    x1: '4.22',
    y1: '4.22',
    x2: '9.17',
    y2: '9.17'
  }), React.createElement('line', {
    x1: '14.83',
    y1: '14.83',
    x2: '19.78',
    y2: '19.78'
  }), React.createElement('line', {
    x1: '2',
    y1: '12',
    x2: '9',
    y2: '12'
  }), React.createElement('line', {
    x1: '15',
    y1: '12',
    x2: '22',
    y2: '12'
  }), React.createElement('line', {
    x1: '4.22',
    y1: '19.78',
    x2: '9.17',
    y2: '14.83'
  }), React.createElement('line', {
    x1: '14.83',
    y1: '9.17',
    x2: '19.78',
    y2: '4.22'
  }));
}
function BookIcon() {
  return React.createElement('svg', {
    width: '22',
    height: '22',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: '1.8',
    strokeLinecap: 'round',
    strokeLinejoin: 'round'
  }, React.createElement('path', {
    d: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20'
  }), React.createElement('path', {
    d: 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'
  }));
}
function GearIcon() {
  return React.createElement('svg', {
    width: '22',
    height: '22',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: '1.8',
    strokeLinecap: 'round',
    strokeLinejoin: 'round'
  }, React.createElement('circle', {
    cx: '12',
    cy: '12',
    r: '3'
  }), React.createElement('path', {
    d: 'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z'
  }));
}
const TABS = [{
  id: 'editor',
  Icon: EditIcon,
  label: '에디터'
}, {
  id: 'brain',
  Icon: BrainIcon,
  label: '브레인'
}, {
  id: 'wiki',
  Icon: BookIcon,
  label: '위키'
}, {
  id: 'settings',
  Icon: GearIcon,
  label: '설정'
}];
function NavBar({
  activeTab = 'editor',
  onTabChange,
  style = {}
}) {
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
      ...style
    }
  }, ...TABS.map(({
    id,
    Icon,
    label
  }) => {
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
        fontFamily: 'var(--font-sans)'
      }
    }, React.createElement(Icon), React.createElement('span', {
      style: {
        fontSize: '10px',
        fontWeight: active ? '600' : '400',
        letterSpacing: 'var(--tracking-normal)',
        lineHeight: '1'
      }
    }, label));
  }));
}
Object.assign(__ds_scope, { NavBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/NavBar.jsx", error: String((e && e.message) || e) }); }

// ui_kits/novel_app/tweaks-panel.jsx
try { (() => {
// @ds-adherence-ignore -- omelette starter scaffold (raw elements/hex/px by design)

/* BEGIN USAGE */
// tweaks-panel.jsx
// Reusable Tweaks shell + form-control helpers.
// Exports (to window): useTweaks, TweaksPanel, TweakSection, TweakRow, TweakSlider,
//   TweakToggle, TweakRadio, TweakSelect, TweakText, TweakNumber, TweakColor, TweakButton.
//
// Owns the host protocol (listens for __activate_edit_mode / __deactivate_edit_mode,
// posts __edit_mode_available / __edit_mode_set_keys / __edit_mode_dismissed) so
// individual prototypes don't re-roll it. Ships a consistent set of controls so you
// don't hand-draw <input type="range">, segmented radios, steppers, etc.
//
// Usage (in an HTML file that loads React + Babel):
//
//   const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
//     "primaryColor": "#D97757",
//     "palette": ["#D97757", "#29261b", "#f6f4ef"],
//     "fontSize": 16,
//     "density": "regular",
//     "dark": false
//   }/*EDITMODE-END*/;
//
//   function App() {
//     const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);
//     return (
//       <div style={{ fontSize: t.fontSize, color: t.primaryColor }}>
//         Hello
//         <TweaksPanel>
//           <TweakSection label="Typography" />
//           <TweakSlider label="Font size" value={t.fontSize} min={10} max={32} unit="px"
//                        onChange={(v) => setTweak('fontSize', v)} />
//           <TweakRadio  label="Density" value={t.density}
//                        options={['compact', 'regular', 'comfy']}
//                        onChange={(v) => setTweak('density', v)} />
//           <TweakSection label="Theme" />
//           <TweakColor  label="Primary" value={t.primaryColor}
//                        options={['#D97757', '#2A6FDB', '#1F8A5B', '#7A5AE0']}
//                        onChange={(v) => setTweak('primaryColor', v)} />
//           <TweakColor  label="Palette" value={t.palette}
//                        options={[['#D97757', '#29261b', '#f6f4ef'],
//                                  ['#475569', '#0f172a', '#f1f5f9']]}
//                        onChange={(v) => setTweak('palette', v)} />
//           <TweakToggle label="Dark mode" value={t.dark}
//                        onChange={(v) => setTweak('dark', v)} />
//         </TweaksPanel>
//       </div>
//     );
//   }
//
// TweakRadio is the segmented control for 2–3 short options (auto-falls-back to
// TweakSelect past ~16/~10 chars per label); reach for TweakSelect directly when
// options are many or long. For color tweaks always curate 3-4 options rather than
// a free picker; an option can also be a whole 2–5 color palette (the stored value
// is the array). The Tweak* controls are a floor, not a ceiling — build custom
// controls inside the panel if a tweak calls for UI they don't cover.
/* END USAGE */
// ─────────────────────────────────────────────────────────────────────────────

const __TWEAKS_STYLE = `
  .twk-panel{position:fixed;right:16px;bottom:16px;z-index:2147483646;width:280px;
    max-height:calc(100vh - 32px);display:flex;flex-direction:column;
    transform:scale(var(--dc-inv-zoom,1));transform-origin:bottom right;
    background:rgba(250,249,247,.78);color:#29261b;
    -webkit-backdrop-filter:blur(24px) saturate(160%);backdrop-filter:blur(24px) saturate(160%);
    border:.5px solid rgba(255,255,255,.6);border-radius:14px;
    box-shadow:0 1px 0 rgba(255,255,255,.5) inset,0 12px 40px rgba(0,0,0,.18);
    font:11.5px/1.4 ui-sans-serif,system-ui,-apple-system,sans-serif;overflow:hidden}
  .twk-hd{display:flex;align-items:center;justify-content:space-between;
    padding:10px 8px 10px 14px;cursor:move;user-select:none}
  .twk-hd b{font-size:12px;font-weight:600;letter-spacing:.01em}
  .twk-x{appearance:none;border:0;background:transparent;color:rgba(41,38,27,.55);
    width:22px;height:22px;border-radius:6px;cursor:default;font-size:13px;line-height:1}
  .twk-x:hover{background:rgba(0,0,0,.06);color:#29261b}
  .twk-body{padding:2px 14px 14px;display:flex;flex-direction:column;gap:10px;
    overflow-y:auto;overflow-x:hidden;min-height:0;
    scrollbar-width:thin;scrollbar-color:rgba(0,0,0,.15) transparent}
  .twk-body::-webkit-scrollbar{width:8px}
  .twk-body::-webkit-scrollbar-track{background:transparent;margin:2px}
  .twk-body::-webkit-scrollbar-thumb{background:rgba(0,0,0,.15);border-radius:4px;
    border:2px solid transparent;background-clip:content-box}
  .twk-body::-webkit-scrollbar-thumb:hover{background:rgba(0,0,0,.25);
    border:2px solid transparent;background-clip:content-box}
  .twk-row{display:flex;flex-direction:column;gap:5px}
  .twk-row-h{flex-direction:row;align-items:center;justify-content:space-between;gap:10px}
  .twk-lbl{display:flex;justify-content:space-between;align-items:baseline;
    color:rgba(41,38,27,.72)}
  .twk-lbl>span:first-child{font-weight:500}
  .twk-val{color:rgba(41,38,27,.5);font-variant-numeric:tabular-nums}

  .twk-sect{font-size:10px;font-weight:600;letter-spacing:.06em;text-transform:uppercase;
    color:rgba(41,38,27,.45);padding:10px 0 0}
  .twk-sect:first-child{padding-top:0}

  .twk-field{appearance:none;box-sizing:border-box;width:100%;min-width:0;height:26px;padding:0 8px;
    border:.5px solid rgba(0,0,0,.1);border-radius:7px;
    background:rgba(255,255,255,.6);color:inherit;font:inherit;outline:none}
  .twk-field:focus{border-color:rgba(0,0,0,.25);background:rgba(255,255,255,.85)}
  select.twk-field{padding-right:22px;
    background-image:url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='10' height='6' viewBox='0 0 10 6'><path fill='rgba(0,0,0,.5)' d='M0 0h10L5 6z'/></svg>");
    background-repeat:no-repeat;background-position:right 8px center}

  .twk-slider{appearance:none;-webkit-appearance:none;width:100%;height:4px;margin:6px 0;
    border-radius:999px;background:rgba(0,0,0,.12);outline:none}
  .twk-slider::-webkit-slider-thumb{-webkit-appearance:none;appearance:none;
    width:14px;height:14px;border-radius:50%;background:#fff;
    border:.5px solid rgba(0,0,0,.12);box-shadow:0 1px 3px rgba(0,0,0,.2);cursor:default}
  .twk-slider::-moz-range-thumb{width:14px;height:14px;border-radius:50%;
    background:#fff;border:.5px solid rgba(0,0,0,.12);box-shadow:0 1px 3px rgba(0,0,0,.2);cursor:default}

  .twk-seg{position:relative;display:flex;padding:2px;border-radius:8px;
    background:rgba(0,0,0,.06);user-select:none}
  .twk-seg-thumb{position:absolute;top:2px;bottom:2px;border-radius:6px;
    background:rgba(255,255,255,.9);box-shadow:0 1px 2px rgba(0,0,0,.12);
    transition:left .15s cubic-bezier(.3,.7,.4,1),width .15s}
  .twk-seg.dragging .twk-seg-thumb{transition:none}
  .twk-seg button{appearance:none;position:relative;z-index:1;flex:1;border:0;
    background:transparent;color:inherit;font:inherit;font-weight:500;min-height:22px;
    border-radius:6px;cursor:default;padding:4px 6px;line-height:1.2;
    overflow-wrap:anywhere}

  .twk-toggle{position:relative;width:32px;height:18px;border:0;border-radius:999px;
    background:rgba(0,0,0,.15);transition:background .15s;cursor:default;padding:0}
  .twk-toggle[data-on="1"]{background:#34c759}
  .twk-toggle i{position:absolute;top:2px;left:2px;width:14px;height:14px;border-radius:50%;
    background:#fff;box-shadow:0 1px 2px rgba(0,0,0,.25);transition:transform .15s}
  .twk-toggle[data-on="1"] i{transform:translateX(14px)}

  .twk-num{display:flex;align-items:center;box-sizing:border-box;min-width:0;height:26px;padding:0 0 0 8px;
    border:.5px solid rgba(0,0,0,.1);border-radius:7px;background:rgba(255,255,255,.6)}
  .twk-num-lbl{font-weight:500;color:rgba(41,38,27,.6);cursor:ew-resize;
    user-select:none;padding-right:8px}
  .twk-num input{flex:1;min-width:0;height:100%;border:0;background:transparent;
    font:inherit;font-variant-numeric:tabular-nums;text-align:right;padding:0 8px 0 0;
    outline:none;color:inherit;-moz-appearance:textfield}
  .twk-num input::-webkit-inner-spin-button,.twk-num input::-webkit-outer-spin-button{
    -webkit-appearance:none;margin:0}
  .twk-num-unit{padding-right:8px;color:rgba(41,38,27,.45)}

  .twk-btn{appearance:none;height:26px;padding:0 12px;border:0;border-radius:7px;
    background:rgba(0,0,0,.78);color:#fff;font:inherit;font-weight:500;cursor:default}
  .twk-btn:hover{background:rgba(0,0,0,.88)}
  .twk-btn.secondary{background:rgba(0,0,0,.06);color:inherit}
  .twk-btn.secondary:hover{background:rgba(0,0,0,.1)}

  .twk-swatch{appearance:none;-webkit-appearance:none;width:56px;height:22px;
    border:.5px solid rgba(0,0,0,.1);border-radius:6px;padding:0;cursor:default;
    background:transparent;flex-shrink:0}
  .twk-swatch::-webkit-color-swatch-wrapper{padding:0}
  .twk-swatch::-webkit-color-swatch{border:0;border-radius:5.5px}
  .twk-swatch::-moz-color-swatch{border:0;border-radius:5.5px}

  .twk-chips{display:flex;gap:6px}
  .twk-chip{position:relative;appearance:none;flex:1;min-width:0;height:46px;
    padding:0;border:0;border-radius:6px;overflow:hidden;cursor:default;
    box-shadow:0 0 0 .5px rgba(0,0,0,.12),0 1px 2px rgba(0,0,0,.06);
    transition:transform .12s cubic-bezier(.3,.7,.4,1),box-shadow .12s}
  .twk-chip:hover{transform:translateY(-1px);
    box-shadow:0 0 0 .5px rgba(0,0,0,.18),0 4px 10px rgba(0,0,0,.12)}
  .twk-chip[data-on="1"]{box-shadow:0 0 0 1.5px rgba(0,0,0,.85),
    0 2px 6px rgba(0,0,0,.15)}
  .twk-chip>span{position:absolute;top:0;bottom:0;right:0;width:34%;
    display:flex;flex-direction:column;box-shadow:-1px 0 0 rgba(0,0,0,.1)}
  .twk-chip>span>i{flex:1;box-shadow:0 -1px 0 rgba(0,0,0,.1)}
  .twk-chip>span>i:first-child{box-shadow:none}
  .twk-chip svg{position:absolute;top:6px;left:6px;width:13px;height:13px;
    filter:drop-shadow(0 1px 1px rgba(0,0,0,.3))}
`;

// ── useTweaks ───────────────────────────────────────────────────────────────
// Single source of truth for tweak values. setTweak persists via the host
// (__edit_mode_set_keys → host rewrites the EDITMODE block on disk).
function useTweaks(defaults) {
  const [values, setValues] = React.useState(defaults);
  // Accepts either setTweak('key', value) or setTweak({ key: value, ... }) so a
  // useState-style call doesn't write a "[object Object]" key into the persisted
  // JSON block.
  const setTweak = React.useCallback((keyOrEdits, val) => {
    const edits = typeof keyOrEdits === 'object' && keyOrEdits !== null ? keyOrEdits : {
      [keyOrEdits]: val
    };
    setValues(prev => ({
      ...prev,
      ...edits
    }));
    window.parent.postMessage({
      type: '__edit_mode_set_keys',
      edits
    }, '*');
    // Same-window signal so in-page listeners (deck-stage rail thumbnails)
    // can react — the parent message only reaches the host, not peers.
    window.dispatchEvent(new CustomEvent('tweakchange', {
      detail: edits
    }));
  }, []);
  return [values, setTweak];
}

// ── TweaksPanel ─────────────────────────────────────────────────────────────
// Floating shell. Registers the protocol listener BEFORE announcing
// availability — if the announce ran first, the host's activate could land
// before our handler exists and the toolbar toggle would silently no-op.
// The close button posts __edit_mode_dismissed so the host's toolbar toggle
// flips off in lockstep; the host echoes __deactivate_edit_mode back which
// is what actually hides the panel.
function TweaksPanel({
  title = 'Tweaks',
  children
}) {
  const [open, setOpen] = React.useState(false);
  const dragRef = React.useRef(null);
  const offsetRef = React.useRef({
    x: 16,
    y: 16
  });
  const PAD = 16;
  const clampToViewport = React.useCallback(() => {
    const panel = dragRef.current;
    if (!panel) return;
    const w = panel.offsetWidth,
      h = panel.offsetHeight;
    const maxRight = Math.max(PAD, window.innerWidth - w - PAD);
    const maxBottom = Math.max(PAD, window.innerHeight - h - PAD);
    offsetRef.current = {
      x: Math.min(maxRight, Math.max(PAD, offsetRef.current.x)),
      y: Math.min(maxBottom, Math.max(PAD, offsetRef.current.y))
    };
    panel.style.right = offsetRef.current.x + 'px';
    panel.style.bottom = offsetRef.current.y + 'px';
  }, []);
  React.useEffect(() => {
    if (!open) return;
    clampToViewport();
    if (typeof ResizeObserver === 'undefined') {
      window.addEventListener('resize', clampToViewport);
      return () => window.removeEventListener('resize', clampToViewport);
    }
    const ro = new ResizeObserver(clampToViewport);
    ro.observe(document.documentElement);
    return () => ro.disconnect();
  }, [open, clampToViewport]);
  React.useEffect(() => {
    const onMsg = e => {
      const t = e?.data?.type;
      if (t === '__activate_edit_mode') setOpen(true);else if (t === '__deactivate_edit_mode') setOpen(false);
    };
    window.addEventListener('message', onMsg);
    window.parent.postMessage({
      type: '__edit_mode_available'
    }, '*');
    return () => window.removeEventListener('message', onMsg);
  }, []);
  const dismiss = () => {
    setOpen(false);
    window.parent.postMessage({
      type: '__edit_mode_dismissed'
    }, '*');
  };
  const onDragStart = e => {
    const panel = dragRef.current;
    if (!panel) return;
    const r = panel.getBoundingClientRect();
    const sx = e.clientX,
      sy = e.clientY;
    const startRight = window.innerWidth - r.right;
    const startBottom = window.innerHeight - r.bottom;
    const move = ev => {
      offsetRef.current = {
        x: startRight - (ev.clientX - sx),
        y: startBottom - (ev.clientY - sy)
      };
      clampToViewport();
    };
    const up = () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  };
  if (!open) return null;
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("style", null, __TWEAKS_STYLE), /*#__PURE__*/React.createElement("div", {
    ref: dragRef,
    className: "twk-panel",
    "data-omelette-chrome": "",
    style: {
      right: offsetRef.current.x,
      bottom: offsetRef.current.y
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "twk-hd",
    onMouseDown: onDragStart
  }, /*#__PURE__*/React.createElement("b", null, title), /*#__PURE__*/React.createElement("button", {
    className: "twk-x",
    "aria-label": "Close tweaks",
    onMouseDown: e => e.stopPropagation(),
    onClick: dismiss
  }, "\u2715")), /*#__PURE__*/React.createElement("div", {
    className: "twk-body"
  }, children)));
}

// ── Layout helpers ──────────────────────────────────────────────────────────

function TweakSection({
  label,
  children
}) {
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "twk-sect"
  }, label), children);
}
function TweakRow({
  label,
  value,
  children,
  inline = false
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: inline ? 'twk-row twk-row-h' : 'twk-row'
  }, /*#__PURE__*/React.createElement("div", {
    className: "twk-lbl"
  }, /*#__PURE__*/React.createElement("span", null, label), value != null && /*#__PURE__*/React.createElement("span", {
    className: "twk-val"
  }, value)), children);
}

// ── Controls ────────────────────────────────────────────────────────────────

function TweakSlider({
  label,
  value,
  min = 0,
  max = 100,
  step = 1,
  unit = '',
  onChange
}) {
  return /*#__PURE__*/React.createElement(TweakRow, {
    label: label,
    value: `${value}${unit}`
  }, /*#__PURE__*/React.createElement("input", {
    type: "range",
    className: "twk-slider",
    min: min,
    max: max,
    step: step,
    value: value,
    onChange: e => onChange(Number(e.target.value))
  }));
}
function TweakToggle({
  label,
  value,
  onChange
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "twk-row twk-row-h"
  }, /*#__PURE__*/React.createElement("div", {
    className: "twk-lbl"
  }, /*#__PURE__*/React.createElement("span", null, label)), /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "twk-toggle",
    "data-on": value ? '1' : '0',
    role: "switch",
    "aria-checked": !!value,
    onClick: () => onChange(!value)
  }, /*#__PURE__*/React.createElement("i", null)));
}
function TweakRadio({
  label,
  value,
  options,
  onChange
}) {
  const trackRef = React.useRef(null);
  const [dragging, setDragging] = React.useState(false);
  // The active value is read by pointer-move handlers attached for the lifetime
  // of a drag — ref it so a stale closure doesn't fire onChange for every move.
  const valueRef = React.useRef(value);
  valueRef.current = value;

  // Segments wrap mid-word once per-segment width runs out. The track is
  // ~248px (280 panel − 28 body pad − 4 seg pad), each button loses 12px
  // to its own padding, and 11.5px system-ui averages ~6.3px/char — so 2
  // options fit ~16 chars each, 3 fit ~10. Past that (or >3 options), fall
  // back to a dropdown rather than wrap.
  const labelLen = o => String(typeof o === 'object' ? o.label : o).length;
  const maxLen = options.reduce((m, o) => Math.max(m, labelLen(o)), 0);
  const fitsAsSegments = maxLen <= ({
    2: 16,
    3: 10
  }[options.length] ?? 0);
  if (!fitsAsSegments) {
    // <select> emits strings — map back to the original option value so the
    // fallback stays type-preserving (numbers, booleans) like the segment path.
    const resolve = s => {
      const m = options.find(o => String(typeof o === 'object' ? o.value : o) === s);
      return m === undefined ? s : typeof m === 'object' ? m.value : m;
    };
    return /*#__PURE__*/React.createElement(TweakSelect, {
      label: label,
      value: value,
      options: options,
      onChange: s => onChange(resolve(s))
    });
  }
  const opts = options.map(o => typeof o === 'object' ? o : {
    value: o,
    label: o
  });
  const idx = Math.max(0, opts.findIndex(o => o.value === value));
  const n = opts.length;
  const segAt = clientX => {
    const r = trackRef.current.getBoundingClientRect();
    const inner = r.width - 4;
    const i = Math.floor((clientX - r.left - 2) / inner * n);
    return opts[Math.max(0, Math.min(n - 1, i))].value;
  };
  const onPointerDown = e => {
    setDragging(true);
    const v0 = segAt(e.clientX);
    if (v0 !== valueRef.current) onChange(v0);
    const move = ev => {
      if (!trackRef.current) return;
      const v = segAt(ev.clientX);
      if (v !== valueRef.current) onChange(v);
    };
    const up = () => {
      setDragging(false);
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };
  return /*#__PURE__*/React.createElement(TweakRow, {
    label: label
  }, /*#__PURE__*/React.createElement("div", {
    ref: trackRef,
    role: "radiogroup",
    onPointerDown: onPointerDown,
    className: dragging ? 'twk-seg dragging' : 'twk-seg'
  }, /*#__PURE__*/React.createElement("div", {
    className: "twk-seg-thumb",
    style: {
      left: `calc(2px + ${idx} * (100% - 4px) / ${n})`,
      width: `calc((100% - 4px) / ${n})`
    }
  }), opts.map(o => /*#__PURE__*/React.createElement("button", {
    key: o.value,
    type: "button",
    role: "radio",
    "aria-checked": o.value === value
  }, o.label))));
}
function TweakSelect({
  label,
  value,
  options,
  onChange
}) {
  return /*#__PURE__*/React.createElement(TweakRow, {
    label: label
  }, /*#__PURE__*/React.createElement("select", {
    className: "twk-field",
    value: value,
    onChange: e => onChange(e.target.value)
  }, options.map(o => {
    const v = typeof o === 'object' ? o.value : o;
    const l = typeof o === 'object' ? o.label : o;
    return /*#__PURE__*/React.createElement("option", {
      key: v,
      value: v
    }, l);
  })));
}
function TweakText({
  label,
  value,
  placeholder,
  onChange
}) {
  return /*#__PURE__*/React.createElement(TweakRow, {
    label: label
  }, /*#__PURE__*/React.createElement("input", {
    className: "twk-field",
    type: "text",
    value: value,
    placeholder: placeholder,
    onChange: e => onChange(e.target.value)
  }));
}
function TweakNumber({
  label,
  value,
  min,
  max,
  step = 1,
  unit = '',
  onChange
}) {
  const clamp = n => {
    if (min != null && n < min) return min;
    if (max != null && n > max) return max;
    return n;
  };
  const startRef = React.useRef({
    x: 0,
    val: 0
  });
  const onScrubStart = e => {
    e.preventDefault();
    startRef.current = {
      x: e.clientX,
      val: value
    };
    const decimals = (String(step).split('.')[1] || '').length;
    const move = ev => {
      const dx = ev.clientX - startRef.current.x;
      const raw = startRef.current.val + dx * step;
      const snapped = Math.round(raw / step) * step;
      onChange(clamp(Number(snapped.toFixed(decimals))));
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "twk-num"
  }, /*#__PURE__*/React.createElement("span", {
    className: "twk-num-lbl",
    onPointerDown: onScrubStart
  }, label), /*#__PURE__*/React.createElement("input", {
    type: "number",
    value: value,
    min: min,
    max: max,
    step: step,
    onChange: e => onChange(clamp(Number(e.target.value)))
  }), unit && /*#__PURE__*/React.createElement("span", {
    className: "twk-num-unit"
  }, unit));
}

// Relative-luminance contrast pick — checkmarks drawn over a swatch need to
// read on both #111 and #fafafa without per-option configuration. Hex input
// only (#rgb / #rrggbb); named or rgb()/hsl() colors fall through to "light".
function __twkIsLight(hex) {
  const h = String(hex).replace('#', '');
  const x = h.length === 3 ? h.replace(/./g, c => c + c) : h.padEnd(6, '0');
  const n = parseInt(x.slice(0, 6), 16);
  if (Number.isNaN(n)) return true;
  const r = n >> 16 & 255,
    g = n >> 8 & 255,
    b = n & 255;
  return r * 299 + g * 587 + b * 114 > 148000;
}
const __TwkCheck = ({
  light
}) => /*#__PURE__*/React.createElement("svg", {
  viewBox: "0 0 14 14",
  "aria-hidden": "true"
}, /*#__PURE__*/React.createElement("path", {
  d: "M3 7.2 5.8 10 11 4.2",
  fill: "none",
  strokeWidth: "2.2",
  strokeLinecap: "round",
  strokeLinejoin: "round",
  stroke: light ? 'rgba(0,0,0,.78)' : '#fff'
}));

// TweakColor — curated color/palette picker. Each option is either a single
// hex string or an array of 1-5 hex strings; the card adapts — a lone color
// renders solid, a palette renders colors[0] as the hero (left ~2/3) with the
// rest stacked in a sharp column on the right. onChange emits the
// option in the shape it was passed (string stays string, array stays array).
// Without options it falls back to the native color input for back-compat.
function TweakColor({
  label,
  value,
  options,
  onChange
}) {
  if (!options || !options.length) {
    return /*#__PURE__*/React.createElement("div", {
      className: "twk-row twk-row-h"
    }, /*#__PURE__*/React.createElement("div", {
      className: "twk-lbl"
    }, /*#__PURE__*/React.createElement("span", null, label)), /*#__PURE__*/React.createElement("input", {
      type: "color",
      className: "twk-swatch",
      value: value,
      onChange: e => onChange(e.target.value)
    }));
  }
  // Native <input type=color> emits lowercase hex per the HTML spec, so
  // compare case-insensitively. String() guards JSON.stringify(undefined),
  // which returns the primitive undefined (no .toLowerCase).
  const key = o => String(JSON.stringify(o)).toLowerCase();
  const cur = key(value);
  return /*#__PURE__*/React.createElement(TweakRow, {
    label: label
  }, /*#__PURE__*/React.createElement("div", {
    className: "twk-chips",
    role: "radiogroup"
  }, options.map((o, i) => {
    const colors = Array.isArray(o) ? o : [o];
    const [hero, ...rest] = colors;
    const sup = rest.slice(0, 4);
    const on = key(o) === cur;
    return /*#__PURE__*/React.createElement("button", {
      key: i,
      type: "button",
      className: "twk-chip",
      role: "radio",
      "aria-checked": on,
      "data-on": on ? '1' : '0',
      "aria-label": colors.join(', '),
      title: colors.join(' · '),
      style: {
        background: hero
      },
      onClick: () => onChange(o)
    }, sup.length > 0 && /*#__PURE__*/React.createElement("span", null, sup.map((c, j) => /*#__PURE__*/React.createElement("i", {
      key: j,
      style: {
        background: c
      }
    }))), on && /*#__PURE__*/React.createElement(__TwkCheck, {
      light: __twkIsLight(hero)
    }));
  })));
}
function TweakButton({
  label,
  onClick,
  secondary = false
}) {
  return /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: secondary ? 'twk-btn secondary' : 'twk-btn',
    onClick: onClick
  }, label);
}
Object.assign(window, {
  useTweaks,
  TweaksPanel,
  TweakSection,
  TweakRow,
  TweakSlider,
  TweakToggle,
  TweakRadio,
  TweakSelect,
  TweakText,
  TweakNumber,
  TweakColor,
  TweakButton
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/novel_app/tweaks-panel.jsx", error: String((e && e.message) || e) }); }

__ds_ns.Button = __ds_scope.Button;

__ds_ns.Badge = __ds_scope.Badge;

__ds_ns.Card = __ds_scope.Card;

__ds_ns.BottomSheet = __ds_scope.BottomSheet;

__ds_ns.StatusBadge = __ds_scope.StatusBadge;

__ds_ns.Input = __ds_scope.Input;

__ds_ns.NavBar = __ds_scope.NavBar;

})();
