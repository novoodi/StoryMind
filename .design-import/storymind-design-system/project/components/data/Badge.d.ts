export interface BadgeProps {
  /** Semantic type — determines background color, text color, and default label */
  type?: 'character' | 'place' | 'item' | 'event' | 'orphan' | 'draft' | 'complete' | 'new';
  /** Override the auto-generated label text */
  label?: string;
  /** Prepend a small colored indicator dot (orphan dot pulses automatically) */
  dot?: boolean;
  size?: 'sm' | 'lg';
  style?: React.CSSProperties;
}
