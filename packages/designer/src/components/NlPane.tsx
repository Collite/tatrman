// TODO: v1.4 — wire to LLM. See docs/plan/implementation-plan.md "v1.4".

interface NlPaneProps {
  open: boolean;
  onToggle: () => void;
}

export function NlPane({ open, onToggle }: NlPaneProps) {
  return (
    <div className={`border-t border-slate-300 bg-white transition-all ${open ? 'h-48' : 'h-10'}`}>
      <div className="flex items-center justify-between px-4 py-2 border-b border-slate-200">
        <span className="text-sm font-medium text-gray-600">Natural Language Query</span>
        <div className="flex items-center gap-2">
          <span className="text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded">Coming in v1.x</span>
          <button
            onClick={onToggle}
            className="text-sm text-gray-500 hover:text-gray-700"
          >
            {open ? '▼' : '▲'}
          </button>
        </div>
      </div>
      {open && (
        <div className="p-4">
          <input
            type="text"
            disabled
            placeholder="Natural language queries will be supported in v1.x"
            className="w-full px-3 py-2 border border-slate-300 rounded bg-slate-50 text-gray-500 cursor-not-allowed"
          />
        </div>
      )}
    </div>
  );
}