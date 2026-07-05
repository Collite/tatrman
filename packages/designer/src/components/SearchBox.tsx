import { useState } from 'react';
import type { SearchHit } from '../data/model-data-source';

/** Read-only model search (WS mode). Worker mode's only search UI today lives inside
 *  the edit-only AddObjectPicker; this is the standalone read-only affordance the M3
 *  DONE bar requires. Input → dataSource.search → hit list → click selects a node. */
export function SearchBox({
  onSearch,
  onSelectHit,
}: {
  onSearch: (query: string) => Promise<SearchHit[]>;
  onSelectHit: (qname: string) => void;
}) {
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<SearchHit[]>([]);
  const [open, setOpen] = useState(false);

  const run = async (q: string) => {
    setQuery(q);
    if (q.trim() === '') {
      setHits([]);
      setOpen(false);
      return;
    }
    const results = await onSearch(q.trim());
    setHits(results);
    setOpen(true);
  };

  return (
    <div className="relative">
      <input
        type="search"
        value={query}
        placeholder="Search model…"
        aria-label="Search model"
        onChange={(e) => void run(e.target.value)}
        className="px-3 py-1.5 text-sm border border-slate-300 rounded-md w-64 focus:outline-none focus:ring-2 focus:ring-sky-400"
      />
      {open && hits.length > 0 && (
        <ul
          role="listbox"
          className="absolute z-20 mt-1 w-80 max-h-72 overflow-auto bg-white border border-slate-300 rounded-md shadow-lg"
        >
          {hits.map((hit) => (
            <li key={hit.qname} role="option" aria-selected={false}>
              <button
                type="button"
                onClick={() => {
                  onSelectHit(hit.qname);
                  setOpen(false);
                }}
                className="w-full text-left px-3 py-2 hover:bg-sky-50 text-sm"
              >
                <span className="font-mono text-slate-800">{hit.qname}</span>
                <span className="ml-2 text-xs text-slate-400">{hit.matchedField}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {open && hits.length === 0 && (
        <div className="absolute z-20 mt-1 w-80 bg-white border border-slate-300 rounded-md shadow-lg px-3 py-2 text-sm text-slate-400">
          No matches
        </div>
      )}
    </div>
  );
}
