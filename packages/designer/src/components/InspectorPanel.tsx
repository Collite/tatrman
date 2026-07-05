import React, { useState } from 'react';
import type { PerKindData } from '@tatrman/lsp';

interface InspectorPanelProps {
  selectedSymbol: { qname: string } | null;
  symbolDetails: Record<string, {
    qname: string;
    kind: string;
    label: string;
    description: string | null;
    tags: string[];
    sourceUri: string;
    sourceLine: number;
    perKindData: PerKindData;
    referencedBy: Array<{ qname: string; sourceUri: string; sourceLine: number }>;
  }>;
  onSelect: (qname: string) => void;
}

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs text-gray-500 uppercase tracking-wide">{label}</label>
      <div className="mt-1">{children}</div>
    </div>
  );
}

function Tags({ tags }: { tags: string[] }) {
  if (tags.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-1">
      {tags.map((tag) => (
        <span key={tag} className="inline-block px-1.5 py-0.5 bg-sky-100 text-sky-700 text-xs rounded font-medium">
          {tag}
        </span>
      ))}
    </div>
  );
}

function PerKindTable({ perKindData }: { perKindData: PerKindData }) {
  if (perKindData.kind === 'table' || perKindData.kind === 'view') {
    return (
      <table className="w-full text-xs border-collapse">
        <thead>
          <tr className="border-b border-gray-200">
            <th className="text-left pb-1 pr-2 text-gray-500 font-medium">Name</th>
            <th className="text-left pb-1 pr-2 text-gray-500 font-medium">Type</th>
            <th className="text-center pb-1 px-1 text-gray-500 font-medium">Key</th>
            <th className="text-center pb-1 pl-1 text-gray-500 font-medium">Null</th>
          </tr>
        </thead>
        <tbody>
          {perKindData.columns.map((col) => (
            <tr key={col.qname} className="border-b border-gray-100 last:border-0">
              <td className="py-0.5 pr-2 font-medium text-gray-800">
                {col.isNameAttribute ? <span className="text-amber-600 mr-1" title="Name attribute">★</span> : null}
                {col.isCodeAttribute ? <span className="text-slate-500 mr-1" title="Code attribute">#</span> : null}
                {col.name}
              </td>
              <td className="py-0.5 pr-2 text-gray-600 font-mono">{col.type ?? '—'}</td>
              <td className="py-0.5 text-center">{col.isKey ? <span className="text-sky-600">✓</span> : null}</td>
              <td className="py-0.5 text-center">{col.optional ? <span className="text-slate-400">○</span> : null}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  }
  if (perKindData.kind === 'entity') {
    return (
      <table className="w-full text-xs border-collapse">
        <thead>
          <tr className="border-b border-gray-200">
            <th className="text-left pb-1 pr-2 text-gray-500 font-medium">Name</th>
            <th className="text-left pb-1 pr-2 text-gray-500 font-medium">Type</th>
            <th className="text-center pb-1 px-1 text-gray-500 font-medium">Key</th>
            <th className="text-center pb-1 pl-1 text-gray-500 font-medium">Null</th>
          </tr>
        </thead>
        <tbody>
          {perKindData.attributes.map((attr) => (
            <tr key={attr.qname} className="border-b border-gray-100 last:border-0">
              <td className="py-0.5 pr-2 font-medium text-gray-800">
                {attr.isNameAttribute ? <span className="text-amber-600 mr-1" title="Name attribute">★</span> : null}
                {attr.isCodeAttribute ? <span className="text-slate-500 mr-1" title="Code attribute">#</span> : null}
                {attr.name}
              </td>
              <td className="py-0.5 pr-2 text-gray-600 font-mono">{attr.type ?? '—'}</td>
              <td className="py-0.5 text-center">{attr.isKey ? <span className="text-sky-600">✓</span> : null}</td>
              <td className="py-0.5 text-center">{attr.optional ? <span className="text-slate-400">○</span> : null}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  }
  if (perKindData.kind === 'fk') {
    return (
      <p className="text-xs text-gray-600">
        References <span className="font-mono font-medium">{perKindData.fromQname}</span>
        {' → '}
        <span className="font-mono font-medium">{perKindData.toQname}</span>
      </p>
    );
  }
  if (perKindData.kind === 'relation') {
    return (
      <p className="text-xs text-gray-600">
        <span className="font-mono font-medium">{perKindData.fromQname}</span>
        {` [${perKindData.fromCardinality ?? '?'}–${perKindData.toCardinality ?? '?'}] `}
        <span className="font-mono font-medium">{perKindData.toQname}</span>
      </p>
    );
  }
  if (perKindData.kind === 'role') {
    return (
      <p className="text-xs text-gray-600">
        Labels: {Object.entries(perKindData.labelByLanguage).map(([lang, lbl]) => `${lang}: ${lbl}`).join(', ')}
      </p>
    );
  }
  return <p className="text-xs text-gray-400 italic">No detail available.</p>;
}

function ReferencedBy({ items, onSelect }: {
  items: Array<{ qname: string; sourceUri: string; sourceLine: number }>;
  onSelect: (qname: string) => void;
}) {
  if (items.length === 0) return <p className="text-xs text-gray-400">None</p>;
  return (
    <ul className="space-y-1">
      {items.map((item) => (
        <li key={item.qname}>
          <button
            type="button"
            onClick={() => onSelect(item.qname)}
            className="block w-full text-left text-xs hover:bg-sky-50 px-1 py-0.5 rounded"
          >
            <span className="font-mono text-sky-600">{item.qname}</span>
            <span className="text-gray-400 ml-1">:{item.sourceLine}</span>
          </button>
        </li>
      ))}
    </ul>
  );
}

export function InspectorPanel({ selectedSymbol, symbolDetails, onSelect }: InspectorPanelProps) {
  const detail = selectedSymbol ? symbolDetails[selectedSymbol.qname] : null;
  const [copiedToast, setCopiedToast] = useState(false);

  return (
    <aside className="w-80 bg-white border-l border-slate-300 overflow-y-auto">
      <div className="p-4">
        <h2 className="text-sm font-semibold text-gray-600 uppercase tracking-wide mb-4">Inspector</h2>
        {detail ? (
          <div className="space-y-4">
            <Section label="Kind">
              <span className="inline-block px-2 py-0.5 bg-slate-100 text-slate-700 text-sm rounded font-medium">
                {detail.kind}
              </span>
            </Section>

            <Section label="Name">
              <p className="text-sm font-semibold text-gray-800">{detail.label}</p>
            </Section>

            <Section label="QName">
              <p className="text-xs text-gray-600 font-mono break-all">{detail.qname}</p>
            </Section>

            {detail.description && (
              <Section label="Description">
                <p className="text-sm text-gray-700 leading-relaxed">{detail.description}</p>
              </Section>
            )}

            {detail.tags.length > 0 && (
              <Section label="Tags">
                <Tags tags={detail.tags} />
              </Section>
            )}

            <Section label="Source">
              <button
                type="button"
                onClick={() => {
                  const text = `${detail.sourceUri.replace(/^file:\/\//, '')}:${detail.sourceLine}`;
                  navigator.clipboard.writeText(text);
                  setCopiedToast(true);
                  setTimeout(() => setCopiedToast(false), 1200);
                }}
                className="text-xs text-sky-600 hover:text-sky-700 font-mono underline-offset-2 hover:underline"
              >
                {detail.sourceUri.split('/').pop()}
                <span className="text-gray-400 ml-1">:{detail.sourceLine}</span>
              </button>
              {copiedToast && (
                <p className="text-xs text-emerald-600 mt-1" role="status">Copied</p>
              )}
            </Section>

            <Section label="Detail">
              <PerKindTable perKindData={detail.perKindData} />
            </Section>

            {detail.referencedBy.length > 0 && (
              <Section label="Referenced By">
                <ReferencedBy items={detail.referencedBy} onSelect={onSelect} />
              </Section>
            )}
          </div>
        ) : (
          <p className="text-sm text-gray-500">Select a node to see its details.</p>
        )}
      </div>
    </aside>
  );
}