/**
 * @startingPoint section="Feedback" subtitle="Slide-up conflict warning sheet · 280ms ease-out" viewport="390x500"
 */
export interface BottomSheetProps {
  isOpen?: boolean;
  onClose?: () => void;
  /** Bold 17px heading */
  title?: string;
  /** 14px body copy — use friendly 해요체, zero jargon */
  description?: string;
  /** Primary CTA — 53px, brand blue */
  primaryAction?: { label: string; onClick: () => void };
  /** Secondary CTA — 44px, neutral gray */
  secondaryAction?: { label: string; onClick: () => void };
  children?: React.ReactNode;
  style?: React.CSSProperties;
}
