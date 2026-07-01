export interface StatusBadgeProps {
  /** AI processing state — drives color and default label text */
  status?: 'analyzing' | 'syncing' | 'warning' | 'done' | 'idle';
  /** Override the auto-generated label */
  label?: string;
  style?: React.CSSProperties;
}
