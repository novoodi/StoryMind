/**
 * @startingPoint section="Navigation" subtitle="4-tab bottom nav bar · 50px · spec-mandated height" viewport="390x50"
 */
export interface NavBarProps {
  /** Currently active tab */
  activeTab?: 'editor' | 'brain' | 'wiki' | 'settings';
  /** Called with the tab id when user taps */
  onTabChange?: (tab: 'editor' | 'brain' | 'wiki' | 'settings') => void;
  style?: React.CSSProperties;
}
