/**
 * @startingPoint section="Actions" subtitle="Primary, secondary, ghost, danger · sm / md / lg" viewport="700x220"
 */
export interface ButtonProps {
  /** Visual style of the button */
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'soft';
  /** Height tier — lg (53px) is the spec-mandated primary CTA height */
  size?: 'sm' | 'md' | 'lg';
  /** Disables interaction and fades to 38% opacity */
  disabled?: boolean;
  /** Replaces content with a spinner */
  loading?: boolean;
  /** Stretch button to 100% container width */
  fullWidth?: boolean;
  /** Leading icon node (hidden when loading) */
  icon?: React.ReactNode;
  /** Trailing icon node (hidden when loading) */
  iconAfter?: React.ReactNode;
  onClick?: () => void;
  children?: React.ReactNode;
  style?: React.CSSProperties;
  type?: 'button' | 'submit' | 'reset';
}
