// The catalog spine (A-1 γ primary) — search-first, grouped by kind (Schemas / Cubes /
// Concepts / Programs). Selecting an entry opens (or focuses) its subject tab. Empty
// workspace shows a hint, never a dead spine.

import { useMemo, useState } from 'react';
import type { CatalogGroup, CatalogItem } from './types.js';
import { filterCatalog } from './catalog.js';

export function CatalogSpine({ groups, onOpen }: { groups: CatalogGroup[]; onOpen: (item: CatalogItem) => void }) {
  const [query, setQuery] = useState('');
  const filtered = useMemo(() => filterCatalog(groups, query), [groups, query]);

  return (
    <nav data-testid="catalog-spine" style={{ width: 220, flex: '0 0 auto', background: '#fbfdff', borderRight: '1px solid #CBD8E6', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div style={{ padding: 10 }}>
        <input
          data-testid="catalog-search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search catalog…"
          style={{ width: '100%', padding: '5px 8px', border: '1px solid #CBD8E6', borderRadius: 6, font: 'inherit', fontSize: 12.5 }}
        />
      </div>
      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 12 }}>
        {groups.length === 0 ? (
          <div data-testid="catalog-empty" style={{ padding: '8px 14px', fontSize: 12, color: '#96989B' }}>No objects yet.</div>
        ) : filtered.length === 0 ? (
          <div data-testid="catalog-no-match" style={{ padding: '8px 14px', fontSize: 12, color: '#96989B' }}>No matches.</div>
        ) : (
          filtered.map((group) => (
            <div key={group.kind} data-testid="catalog-group" data-kind={group.kind}>
              <h3 style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '.06em', color: '#96989B', padding: '8px 14px 4px' }}>{group.label}</h3>
              {group.items.map((item) => (
                <button
                  key={item.ref}
                  data-testid="catalog-item"
                  data-ref={item.ref}
                  onClick={() => onOpen(item)}
                  style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none', background: 'transparent', cursor: 'pointer', font: 'inherit', fontSize: 12.5, color: '#16283F', padding: '4px 14px' }}
                >
                  {item.label}
                </button>
              ))}
            </div>
          ))
        )}
      </div>
    </nav>
  );
}
