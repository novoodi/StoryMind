/**
 * @startingPoint section="Data" subtitle="Wiki entry card · timeline log · any content unit" viewport="700x200"
 */
export interface CardProps {
  /** Primary label — 15px semibold */
  title?: React.ReactNode;
  /** Secondary line — 13px, text-secondary */
  subtitle?: string;
  /** Badge slotted top-right (use Badge component) */
  badge?: React.ReactNode;
  /** Body copy — 13px, text-tertiary */
  description?: string;
  /** Footer content, separated by a hairline */
  meta?: React.ReactNode;
  /** Makes the card clickable with hover-lift */
  onClick?: () => void;
  /** Renders a 2px brand-blue ring around the card */
  selected?: boolean;
  /** Override default 16px padding */
  padding?: string | number;
  children?: React.ReactNode;
  style?: React.CSSProperties;
}
