import { useState } from 'react';
import type { GraphMetadata } from '@tatrman/lsp';

export interface GraphPickerProps {
  graphs: GraphMetadata[];
  onSelect: (graphUri: string) => void;
  onCreateNew: () => void;
}

const SCHEMA_COLORS: Record<string, string> = {
  er: 'bg-emerald-100 text-emerald-700',
  db: 'bg-blue-100 text-blue-700',
  map: 'bg-amber-100 text-amber-700',
  query: 'bg-purple-100 text-purple-700',
  cnc: 'bg-slate-100 text-slate-600',
};

export function GraphPicker({ graphs, onSelect, onCreateNew }: GraphPickerProps) {
  const [search, setSearch] = useState('');
  const [schemaFilter, setSchemaFilter] = useState<string | null>(null);

  const filtered = graphs.filter((g) => {
    const matchSearch = g.name.toLowerCase().includes(search.toLowerCase());
    const matchSchema = schemaFilter ? g.schema === schemaFilter : true;
    return matchSearch && matchSchema;
  });

  const schemas = [...new Set(graphs.map((g) => g.schema))];

  return (
    <div className="flex flex-col items-center justify-center flex-1 gap-6 p-8">
      <div className="w-full max-w-lg">
        <div className="bg-white border border-slate-300 rounded-xl shadow-lg p-6">
          <h2 className="text-xl font-bold text-gray-800 mb-1">Select a Graph</h2>
          <p className="text-gray-500 text-sm mb-4">
            {graphs.length} graph{graphs.length !== 1 ? 's' : ''} found in this project
          </p>

          <input
            type="text"
            placeholder="Search graphs…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm mb-3 focus:outline-none focus:ring-2 focus:ring-sky-400"
          />

          {schemas.length > 1 && (
            <div className="flex gap-1 mb-4 flex-wrap">
              <button
                onClick={() => setSchemaFilter(null)}
                className={`px-2 py-0.5 text-xs rounded border ${
                  schemaFilter === null
                    ? 'bg-sky-100 text-sky-700 border-sky-300'
                    : 'bg-slate-50 text-gray-500 border-slate-200 hover:bg-slate-100'
                }`}
              >
                All
              </button>
              {schemas.map((s) => (
                <button
                  key={s}
                  onClick={() => setSchemaFilter(s === schemaFilter ? null : s)}
                  className={`px-2 py-0.5 text-xs rounded border ${
                    schemaFilter === s
                      ? 'bg-sky-100 text-sky-700 border-sky-300'
                      : 'bg-slate-50 text-gray-500 border-slate-200 hover:bg-slate-100'
                  }`}
                >
                  {s}
                </button>
              ))}
            </div>
          )}

          <div className="flex flex-col gap-2 max-h-80 overflow-y-auto">
            {filtered.length === 0 ? (
              <p className="text-gray-400 text-sm text-center py-4">No graphs match your search</p>
            ) : (
              filtered.map((graph) => (
                <button
                  key={graph.uri}
                  onClick={() => onSelect(graph.uri)}
                  className="flex items-center justify-between px-4 py-3 border border-slate-200 rounded-lg hover:bg-sky-50 hover:border-sky-300 transition-colors text-left"
                >
                  <div className="flex flex-col gap-0.5">
                    <span className="text-sm font-medium text-gray-800">{graph.name}</span>
                    {graph.description && (
                      <span className="text-xs text-gray-400 line-clamp-1">{graph.description}</span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    {graph.tags.length > 0 && (
                      <span className="text-xs text-gray-400">{graph.tags.join(', ')}</span>
                    )}
                    <span className={`text-xs px-2 py-0.5 rounded font-medium ${SCHEMA_COLORS[graph.schema] ?? 'bg-slate-100 text-slate-600'}`}>
                      {graph.schema}
                    </span>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>

        <button
          onClick={onCreateNew}
          className="mt-4 w-full px-6 py-3 bg-sky-500 text-white rounded-lg hover:bg-sky-600 font-medium transition-colors"
        >
          + Create New Graph
        </button>
      </div>
    </div>
  );
}