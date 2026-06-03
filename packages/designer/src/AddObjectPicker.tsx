import { useState, useEffect, useRef } from 'react';
import type { LspClient } from './lsp-client';

export interface AddObjectPickerProps {
  lspClient: LspClient;
  currentImports: string[];
  onSelect: (qname: string, autoImport: boolean) => void;
  onClose: () => void;
}

export function AddObjectPicker({ lspClient, currentImports, onSelect, onClose }: AddObjectPickerProps) {
  const [symbols, setSymbols] = useState<Array<{ qname: string; kind: string; name: string; packageName: string | null }>>([]);
  const [search, setSearch] = useState('');
  const [autoImport, setAutoImport] = useState(true);
  const [loading, setLoading] = useState(true);
  const overlayRef = useRef<HTMLDivElement>(null);

  const TOP_LEVEL_KINDS = new Set(['entity', 'table', 'view', 'index', 'constraint', 'procedure', 'function', 'role', 'model', 'er2db_entity', 'er2db_attribute', 'er2db_relation', 'er2cnc_role', 'cnc_role', 'search']);

  useEffect(() => {
    lspClient.listSymbols({ kinds: [...TOP_LEVEL_KINDS], limit: 1000 }).then((syms) => {
      setSymbols(syms);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [lspClient]);

  const filtered = symbols.filter((s) =>
    s.name.toLowerCase().includes(search.toLowerCase()) ||
    s.qname.toLowerCase().includes(search.toLowerCase())
  );

  const isOutOfScope = (pkg: string | null) => {
    if (!pkg) return false;
    return !currentImports.some((imp) => pkg === imp || pkg.startsWith(imp + '.'));
  };

  const handleSelect = (qname: string, pkg: string | null) => {
    const outScope = isOutOfScope(pkg);
    const effectiveAutoImport = outScope ? autoImport : true;
    onSelect(qname, effectiveAutoImport);
  };

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === overlayRef.current) onClose();
  };

  return (
    <div
      ref={overlayRef}
      onClick={handleOverlayClick}
      className="fixed inset-0 bg-black/30 flex items-center justify-center z-50"
      role="dialog"
      aria-modal="true"
      aria-label="Add object to graph"
    >
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
          <h2 className="text-lg font-semibold text-gray-800">Add Object</h2>
          <button
            onClick={onClose}
            className="px-3 py-1 text-sm text-gray-500 border border-slate-300 rounded hover:bg-slate-50"
          >
            Close
          </button>
        </div>

        <div className="p-4 border-b border-slate-100">
          <input
            type="text"
            placeholder="Search objects…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-sky-400"
            autoFocus
          />
          <div className="flex items-center gap-3 mt-3">
            <label className="flex items-center gap-2 text-sm text-gray-600">
              <input
                type="checkbox"
                checked={autoImport}
                onChange={(e) => setAutoImport(e.target.checked)}
                className="w-4 h-4"
              />
              Auto-import on select
            </label>
          </div>
        </div>

        <div className="max-h-80 overflow-y-auto">
          {loading ? (
            <p className="p-4 text-sm text-gray-400 text-center">Loading objects…</p>
          ) : filtered.length === 0 ? (
            <p className="p-4 text-sm text-gray-400 text-center">No objects match your search</p>
          ) : (
            <ul>
              {filtered.map((sym) => {
                const outScope = isOutOfScope(sym.packageName);
                return (
                  <li key={sym.qname}>
                    <button
                      onClick={() => handleSelect(sym.qname, sym.packageName)}
                      data-out-of-scope={outScope}
                      className="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-sky-50 transition-colors text-left"
                    >
                      <span className="flex-1 min-w-0">
                        <span className="block text-sm font-medium text-gray-800 truncate">{sym.name}</span>
                        <span className="block text-xs text-gray-400 truncate">{sym.qname}</span>
                      </span>
                      {outScope && (
                        <span className="text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded border border-amber-300 shrink-0">
                          not imported
                        </span>
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}