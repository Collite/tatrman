// In-tab drill breadcrumb (P-2 — same on both faces). Root = the subject; each drill segment
// is a clickable crumb; clicking a crumb pops to that level, the last crumb is the current view.

import type { SubjectTab } from './types.js';

export function Breadcrumb({ tab, onDrillTo }: { tab: SubjectTab; onDrillTo: (index: number) => void }) {
  if (tab.drillPath.length === 0) return null; // no breadcrumb at the subject root
  return (
    <div data-testid="breadcrumb" style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '4px 12px', background: '#fff', borderBottom: '1px solid #EDF2F9', fontSize: 12.5 }}>
      <button data-testid="crumb-root" onClick={() => onDrillTo(-1)} style={crumbStyle(false)}>{tab.subject.label}</button>
      {tab.drillPath.map((seg, i) => {
        const last = i === tab.drillPath.length - 1;
        return (
          <span key={seg.id} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <span style={{ color: '#96989B' }}>▸</span>
            <button data-testid="crumb" data-current={last || undefined} onClick={() => onDrillTo(i)} style={crumbStyle(last)}>
              {seg.label}
            </button>
          </span>
        );
      })}
    </div>
  );
}

function crumbStyle(current: boolean): React.CSSProperties {
  return {
    border: 'none', background: 'transparent', cursor: 'pointer', font: 'inherit', fontSize: 12.5,
    color: current ? '#16283F' : '#4A75A8', fontWeight: current ? 'bold' : 'normal', padding: '0 2px',
  };
}
