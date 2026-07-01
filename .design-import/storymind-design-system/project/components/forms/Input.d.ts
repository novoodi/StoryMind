export interface InputProps {
  label?: string;
  placeholder?: string;
  value?: string;
  defaultValue?: string;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
  type?: string;
  /** Helper text shown below the field */
  helper?: string;
  /** Error message — overrides helper, turns border/label red */
  error?: string;
  disabled?: boolean;
  readOnly?: boolean;
  /** Leading icon (SVG node) */
  icon?: React.ReactNode;
  /** Trailing icon (SVG node) */
  iconAfter?: React.ReactNode;
  size?: 'sm' | 'md' | 'lg';
  style?: React.CSSProperties;
  inputStyle?: React.CSSProperties;
}
