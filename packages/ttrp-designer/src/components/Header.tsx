import type { SkinId } from '../skins/index.js';

export interface HeaderProps {
  breadcrumb: string[];
  skin: SkinId;
  onNavigate(depth: number): void;
  onSkinChange(skin: SkinId): void;
}

/** Breadcrumb (two-level nav) + per-canvas skin selector (C1-b-iii). */
export function Header({ breadcrumb, skin, onNavigate, onSkinChange }: HeaderProps) {
  return (
    <div className="flex items-center gap-2 border-b border-slate-300 px-3 py-2 text-sm">
      <nav className="flex items-center gap-1">
        {breadcrumb.map((key, i) => (
          <span key={key} className="flex items-center gap-1">
            {i > 0 && <span className="text-slate-400">/</span>}
            <button className="hover:underline" onClick={() => onNavigate(i)}>
              {key}
            </button>
          </span>
        ))}
      </nav>
      <div className="ml-auto flex items-center gap-1">
        <label htmlFor="skin">skin</label>
        <select id="skin" value={skin} onChange={(e) => onSkinChange(e.target.value as SkinId)}>
          <option value="alteryx-knime">Alteryx / KNIME</option>
          <option value="enso">Enso</option>
        </select>
      </div>
    </div>
  );
}
