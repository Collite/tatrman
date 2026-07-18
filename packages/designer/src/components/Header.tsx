import { useRef } from 'react';
import type { DisplayMode } from '@tatrman/lsp';
import { loadProjectViaUpload, type ProjectFiles } from '../fs/file-system';

interface HeaderProps {
  graphName: string | null;
  missingObjectsCount: number;
  displayMode: DisplayMode;
  projectUri: string | null;
  transportKind: 'node' | 'browser' | null;
  onFileLoad: (files: ProjectFiles) => void;
  onDisplayModeChange: (mode: DisplayMode) => void;
  onToggleNlPane: () => void;
  onDirPick: () => void;
  onBack: () => void;
  onOpenFile: () => void;
  onDownloadLayout?: () => void;
}

// FO-21 (FO-P0.S2.T4): this is the Studio Viewer header — render + view/prefs
// surfaces only (display mode, Export Layout). The edit affordances that used to
// live here ("+ Add object", the click-through to the remove-stale drawer) moved
// to `tatrman-platform`'s authoring extension; they re-enter via the extension
// surface (FO contracts §2, wired in FO-P0.S4). The stale-objects badge stays as
// a read-only indicator — viewing which members are gone is a read concern.
export function Header({
  graphName,
  missingObjectsCount,
  displayMode,
  projectUri: _projectUri,
  transportKind,
  onFileLoad,
  onDisplayModeChange,
  onToggleNlPane,
  onDirPick,
  onBack,
  onOpenFile,
  onDownloadLayout,
}: HeaderProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const hasGraph = graphName !== null;

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const input = e.target;
    if (!input.files?.length) return;
    const files = await loadProjectViaUpload(input);
    onFileLoad(files);
    input.value = '';
  };

  const webkitDirProps = { webkitdirectory: '' } as React.InputHTMLAttributes<HTMLInputElement>;

  return (
    <header className="bg-white border-b border-slate-300 px-4 py-2 flex items-center justify-between">
      <div className="flex items-center gap-3">
        {hasGraph && (
          <button
            onClick={onBack}
            className="px-2 py-1 text-sm text-gray-600 border border-slate-300 rounded hover:bg-slate-50"
            title="Back to graph picker"
          >
            ←
          </button>
        )}
        <h1 className="text-lg font-semibold text-gray-800">
          {hasGraph ? graphName : 'TTR Modeler Designer'}
        </h1>
        {hasGraph && missingObjectsCount > 0 && (
          <span
            className="text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded border border-amber-300"
            title={`${missingObjectsCount} object(s) no longer exist in the project`}
          >
            {missingObjectsCount} stale
          </span>
        )}
      </div>
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-1 border border-slate-300 rounded">
          {(['just-names', 'with-types', 'with-constraints'] as const).map((mode) => (
            <button
              key={mode}
              onClick={() => onDisplayModeChange(mode)}
              className={`px-3 py-1 text-sm ${displayMode === mode ? 'text-sky-500 font-medium' : 'text-gray-500'} ${!hasGraph ? 'opacity-50 cursor-not-allowed' : 'hover:bg-slate-50'}`}
              disabled={!hasGraph}
            >
              {mode.replace('-', ' ')}
            </button>
          ))}
        </div>
        <button
          onClick={onToggleNlPane}
          className="px-3 py-2 text-sm text-gray-600 border border-slate-300 rounded hover:bg-slate-50"
          title="Toggle natural language pane"
        >
          NL
        </button>
        <button
          onClick={onOpenFile}
          className="px-4 py-2 text-sm bg-slate-100 text-gray-700 border border-slate-300 rounded hover:bg-slate-200 transition-colors"
          title="Open a .ttrg file"
        >
          Open .ttrg…
        </button>
        <input
          type="file"
          ref={fileInputRef}
          onChange={handleFileChange}
          accept=".ttrm,.ttrg,.toml"
          multiple
          className="hidden"
          {...webkitDirProps}
        />
        <button
          onClick={() => fileInputRef.current?.click()}
          className="px-4 py-2 bg-sky-500 text-white rounded hover:bg-sky-600 transition-colors"
        >
          Load Project Folder
        </button>
        <button
          onClick={onDirPick}
          className="px-4 py-2 bg-slate-100 text-gray-700 border border-slate-300 rounded hover:bg-slate-200 transition-colors"
          title="Open project folder (requires browser File System Access API support)"
        >
          Open Folder
        </button>
        {transportKind === 'browser' && onDownloadLayout && (
          <button
            onClick={onDownloadLayout}
            className="px-4 py-2 bg-slate-100 text-gray-700 border border-slate-300 rounded hover:bg-slate-200 transition-colors"
            title="Export layout JSON"
          >
            Export Layout
          </button>
        )}
      </div>
    </header>
  );
}