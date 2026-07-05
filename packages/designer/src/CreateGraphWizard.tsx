import { useReducer, useEffect, useState, useRef } from 'react';
import type { LspClient } from './lsp-client';
import type { PackageGraphResponse } from '@tatrman/lsp';
import type { WorkspaceEdit } from 'vscode-languageserver-types';

interface WizardState {
  step: 1 | 2 | 3 | 4 | 5;
  selectedPackages: string[];
  schema: 'er' | 'db';
  graphName: string;
  packages: Array<{ name: string; documentUris: string[] }>;
  dependencies: Array<{ from: string; to: string; citedBy: string[] }>;
  suggestedObjects: string[];
  objects: string[];
  description: string;
  tags: string[];
}

type WizardAction =
  | { type: 'NEXT' }
  | { type: 'BACK' }
  | { type: 'TOGGLE_PACKAGE'; name: string }
  | { type: 'ADD_ALL_TRANSITIVE' }
  | { type: 'SET_SCHEMA'; schema: 'er' | 'db' }
  | { type: 'SET_NAME'; name: string }
  | { type: 'TOGGLE_OBJECT'; qname: string }
  | { type: 'SET_OBJECTS'; qnames: string[]; selected: boolean }
  | { type: 'LOAD_PACKAGES'; pkgGraph: PackageGraphResponse }
  | { type: 'RESET' };

const MAX_STEP = 5 as const;

function reducer(state: WizardState, action: WizardAction): WizardState {
  switch (action.type) {
    case 'TOGGLE_PACKAGE': {
      const exists = state.selectedPackages.includes(action.name);
      return {
        ...state,
        selectedPackages: exists
          ? state.selectedPackages.filter((p) => p !== action.name)
          : [...state.selectedPackages, action.name],
      };
    }
    case 'ADD_ALL_TRANSITIVE': {
      const pkgSet = new Set(state.selectedPackages);
      const toVisit = [...state.selectedPackages];
      while (toVisit.length > 0) {
        const current = toVisit.pop()!;
        for (const dep of state.dependencies) {
          if (dep.from === current && !pkgSet.has(dep.to)) {
            pkgSet.add(dep.to);
            toVisit.push(dep.to);
          }
          if (dep.to === current && !pkgSet.has(dep.from)) {
            pkgSet.add(dep.from);
            toVisit.push(dep.from);
          }
        }
      }
      return { ...state, selectedPackages: [...pkgSet] };
    }
    case 'SET_SCHEMA':
      return { ...state, schema: action.schema };
    case 'SET_NAME':
      return { ...state, graphName: action.name };
    case 'TOGGLE_OBJECT': {
      const exists = state.objects.includes(action.qname);
      return {
        ...state,
        objects: exists
          ? state.objects.filter((o) => o !== action.qname)
          : [...state.objects, action.qname],
      };
    }
    case 'SET_OBJECTS': {
      const target = new Set(action.qnames);
      if (action.selected) {
        const merged = new Set([...state.objects, ...action.qnames]);
        return { ...state, objects: [...merged] };
      }
      return { ...state, objects: state.objects.filter((o) => !target.has(o)) };
    }
    case 'NEXT':
      return { ...state, step: Math.min(state.step + 1, MAX_STEP) as WizardState['step'] };
    case 'BACK':
      return { ...state, step: Math.max(state.step - 1, 1) as WizardState['step'] };
    case 'LOAD_PACKAGES':
      return {
        ...state,
        packages: action.pkgGraph.packages,
        dependencies: action.pkgGraph.dependencies,
      };
    case 'RESET':
      return { ...initialState, packages: state.packages, dependencies: state.dependencies };
    default:
      return state;
  }
}

const initialState: WizardState = {
  step: 1,
  selectedPackages: [],
  schema: 'er',
  graphName: '',
  packages: [],
  dependencies: [],
  suggestedObjects: [],
  objects: [],
  description: '',
  tags: [],
};

interface CreateGraphWizardProps {
  lspClient: LspClient;
  projectRoot: string;
  onComplete: (graphUri: string) => void;
  onCancel: () => void;
  onError?: (message: string) => void;
}

function slugify(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function StepDots({ current }: { current: number }) {
  return (
    <div className="flex gap-2 justify-center mb-6">
      {Array.from({ length: 5 }, (_, i) => (
        <span
          key={i}
          data-testid={`dot-${i + 1}`}
          className={`w-2.5 h-2.5 rounded-full transition-colors ${
            i + 1 === current ? 'bg-sky-500' : 'bg-slate-300'
          }`}
        />
      ))}
    </div>
  );
}

function Step1({ state, dispatch }: { state: WizardState; dispatch: React.Dispatch<WizardAction> }) {
  return (
    <div>
      <h3 className="text-lg font-semibold mb-1">Step 1 — Select Packages</h3>
      <div className="flex flex-col gap-2 max-h-60 overflow-y-auto border rounded p-2">
        {state.packages.map((pkg) => {
          const checked = state.selectedPackages.includes(pkg.name);
          return (
            <label key={pkg.name} className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={checked}
                onChange={() => dispatch({ type: 'TOGGLE_PACKAGE', name: pkg.name })}
                className="w-4 h-4"
              />
              <span className="text-sm">{pkg.name}</span>
              <span className="text-xs text-gray-400 ml-auto">{pkg.documentUris.length} file(s)</span>
            </label>
          );
        })}
      </div>
    </div>
  );
}

// Lazy-load cytoscape once for the embedded dependency mini-graph.
const cyDepGraphReady = import('cytoscape').then((mod) => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return ((mod as any).default ?? mod) as (opts: unknown) => CyInstance;
});

interface CyInstance {
  on: (event: string, selector: string, handler: (evt: { target: { id: () => string } }) => void) => void;
  destroy: () => void;
}

function PackageDepGraph({
  packages,
  dependencies,
  selected,
  onToggle,
}: {
  packages: Array<{ name: string }>;
  dependencies: Array<{ from: string; to: string }>;
  selected: string[];
  onToggle: (name: string) => void;
}) {
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const onToggleRef = useRef(onToggle);
  useEffect(() => { onToggleRef.current = onToggle; }, [onToggle]);

  useEffect(() => {
    if (!container) return;
    let cy: CyInstance | null = null;
    let disposed = false;
    const selectedSet = new Set(selected);
    cyDepGraphReady.then((cytoscape) => {
      if (disposed || !container) return;
      cy = cytoscape({
        container,
        elements: [
          ...packages.map((p) => ({
            data: { id: p.name, label: p.name },
            classes: selectedSet.has(p.name) ? 'selected' : '',
          })),
          ...dependencies
            .filter((d) => packages.some((p) => p.name === d.from) && packages.some((p) => p.name === d.to))
            .map((d) => ({ data: { id: `${d.from}->${d.to}`, source: d.from, target: d.to } })),
        ],
        style: [
          { selector: 'node', style: { 'background-color': '#cbd5e1', label: 'data(label)', 'font-size': 8, color: '#475569', 'text-valign': 'center', 'text-halign': 'center', width: 14, height: 14 } },
          { selector: 'node.selected', style: { 'background-color': '#0ea5e9', color: '#fff' } },
          { selector: 'edge', style: { width: 1, 'line-color': '#94a3b8', 'target-arrow-color': '#94a3b8', 'target-arrow-shape': 'triangle', 'curve-style': 'bezier' } },
        ],
        layout: { name: 'circle' },
      });
      cy.on('tap', 'node', (evt) => onToggleRef.current(evt.target.id()));
    });
    return () => {
      disposed = true;
      cy?.destroy();
    };
  }, [container, packages, dependencies, selected]);

  return (
    <div
      ref={setContainer}
      data-testid="package-dep-graph"
      aria-label="Package dependency graph"
      className="w-full h-44 border rounded bg-slate-50"
    />
  );
}

function Step2({ state, dispatch }: { state: WizardState; dispatch: React.Dispatch<WizardAction> }) {
  const selectedSet = new Set(state.selectedPackages);
  return (
    <div>
      <h3 className="text-lg font-semibold mb-1">Step 2 — Review Dependencies</h3>
      <p className="text-sm text-gray-500 mb-3">
        Selected packages are highlighted. Click a package to add or remove it.
      </p>
      <PackageDepGraph
        packages={state.packages}
        dependencies={state.dependencies}
        selected={state.selectedPackages}
        onToggle={(name) => dispatch({ type: 'TOGGLE_PACKAGE', name })}
      />
      <div className="flex flex-wrap gap-1.5 mt-3">
        {state.packages.map((pkg) => {
          const sel = selectedSet.has(pkg.name);
          return (
            <button
              key={pkg.name}
              aria-pressed={sel}
              onClick={() => dispatch({ type: 'TOGGLE_PACKAGE', name: pkg.name })}
              className={`px-2 py-0.5 text-xs rounded border ${
                sel ? 'bg-sky-100 text-sky-700 border-sky-300' : 'bg-slate-50 text-gray-500 border-slate-200 hover:bg-slate-100'
              }`}
            >
              {pkg.name}
            </button>
          );
        })}
      </div>
      <p className="text-xs text-gray-400 mt-2">{state.selectedPackages.length} package(s) selected</p>
      <div className="flex gap-2 mt-4">
        <button
          onClick={() => dispatch({ type: 'ADD_ALL_TRANSITIVE' })}
          className="px-3 py-1.5 text-sm border border-slate-300 rounded hover:bg-slate-50"
        >
          Add all transitive
        </button>
        <button
          onClick={() => dispatch({ type: 'NEXT' })}
          className="px-3 py-1.5 text-sm border border-slate-300 rounded hover:bg-slate-50"
        >
          Continue with current selection
        </button>
      </div>
    </div>
  );
}

function Step3({ state, dispatch, lspClient }: { state: WizardState; dispatch: React.Dispatch<WizardAction>; lspClient: LspClient | null }) {
  const [symbols, setSymbols] = useState<Array<{ qname: string; kind: string; name: string }>>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!lspClient || state.selectedPackages.length === 0) return;
    if (!lspClient.listSymbols) return;
    setLoading(true);
    lspClient.listSymbols({ limit: 1000 }).then((syms) => {
      setSymbols(syms);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [lspClient, state.selectedPackages]);

  const selectedPkgs = state.selectedPackages;
  const ownerPackage = (qname: string): string | null => {
    // longest selected package that prefixes this qname (handles nested names)
    let best: string | null = null;
    for (const pkg of selectedPkgs) {
      if ((qname === pkg || qname.startsWith(pkg + '.')) && (best === null || pkg.length > best.length)) {
        best = pkg;
      }
    }
    return best;
  };

  const relevantSymbols = symbols.filter((s) => ownerPackage(s.qname) !== null);
  const objectSet = new Set(state.objects);

  // package -> kind -> symbols
  const grouped = new Map<string, Map<string, Array<{ qname: string; kind: string; name: string }>>>();
  for (const sym of relevantSymbols) {
    const pkg = ownerPackage(sym.qname)!;
    if (!grouped.has(pkg)) grouped.set(pkg, new Map());
    const byKind = grouped.get(pkg)!;
    if (!byKind.has(sym.kind)) byKind.set(sym.kind, []);
    byKind.get(sym.kind)!.push(sym);
  }

  const allSelected = (qnames: string[]) => qnames.length > 0 && qnames.every((q) => objectSet.has(q));

  return (
    <div>
      <h3 className="text-lg font-semibold mb-1">Step 3 — Select Objects</h3>
      <p className="text-sm text-gray-500 mb-4">
        Choose the specific objects to include. {state.objects.length} of {relevantSymbols.length} selected.
      </p>
      {loading ? (
        <p className="text-sm text-gray-400">Loading objects…</p>
      ) : (
        <div className="flex flex-col gap-3 max-h-60 overflow-y-auto border rounded p-2">
          {[...grouped.entries()].map(([pkg, byKind]) => {
            const pkgQnames = [...byKind.values()].flat().map((s) => s.qname);
            const pkgAll = allSelected(pkgQnames);
            return (
              <div key={pkg}>
                <div className="flex items-center gap-2 mb-1">
                  <input
                    type="checkbox"
                    aria-label={`select all in ${pkg}`}
                    checked={pkgAll}
                    onChange={() => dispatch({ type: 'SET_OBJECTS', qnames: pkgQnames, selected: !pkgAll })}
                    className="w-4 h-4"
                  />
                  <span className="text-sm font-semibold text-gray-700">{pkg}</span>
                  <span className="text-xs text-gray-400">{pkgQnames.length}</span>
                </div>
                {[...byKind.entries()].map(([kind, syms]) => {
                  const kindQnames = syms.map((s) => s.qname);
                  const kindAll = allSelected(kindQnames);
                  return (
                    <div key={kind} className="ml-4 mb-1">
                      <div className="flex items-center gap-2">
                        <span className="text-xs uppercase tracking-wide text-gray-400">{kind}</span>
                        <button
                          onClick={() => dispatch({ type: 'SET_OBJECTS', qnames: kindQnames, selected: !kindAll })}
                          className="text-xs text-sky-600 hover:underline"
                        >
                          {kindAll ? 'clear' : 'all'}
                        </button>
                      </div>
                      {syms.map((sym) => (
                        <label key={sym.qname} className="flex items-center gap-2 cursor-pointer text-sm py-0.5 ml-2">
                          <input
                            type="checkbox"
                            checked={objectSet.has(sym.qname)}
                            onChange={() => dispatch({ type: 'TOGGLE_OBJECT', qname: sym.qname })}
                            className="w-4 h-4"
                          />
                          <span className="font-medium text-sm">{sym.name}</span>
                          <span className="text-xs text-gray-400 ml-auto">{sym.qname}</span>
                        </label>
                      ))}
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function Step4({ state, dispatch }: { state: WizardState; dispatch: React.Dispatch<WizardAction> }) {
  return (
    <div>
      <h3 className="text-lg font-semibold mb-1">Step 4 — Choose Schema</h3>
      <p className="text-sm text-gray-500 mb-4">Select the schema kind for this graph.</p>
      <div className="flex flex-col gap-2">
        {(['er', 'db'] as const).map((schema) => (
          <label key={schema} className="flex items-center gap-2 border rounded p-3 cursor-pointer hover:bg-slate-50">
            <input
              type="radio"
              name="schema"
              value={schema}
              checked={state.schema === schema}
              onChange={() => dispatch({ type: 'SET_SCHEMA', schema })}
              className="w-4 h-4"
            />
            <span className="font-medium">{schema.toUpperCase()}</span>
            <span className="text-sm text-gray-500 ml-2">
              {schema === 'er' ? 'Entity-Relationship model' : 'Database / Dimensional model'}
            </span>
          </label>
        ))}
      </div>
    </div>
  );
}

function Step5({ state, dispatch, projectRoot }: { state: WizardState; dispatch: React.Dispatch<WizardAction>; projectRoot: string }) {
  const suggestedFilename = `${slugify(state.graphName || 'untitled')}.ttrg`;
  const suggestedUri = `${projectRoot.replace(/\/$/, '')}/graphs/${suggestedFilename}`;

  return (
    <div>
      <h3 className="text-lg font-semibold mb-1">Step 5 — Name &amp; Save</h3>
      <p className="text-sm text-gray-500 mb-4">Give your graph a name and save it.</p>
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1" htmlFor="graph-name">Graph Name</label>
          <input
            id="graph-name"
            type="text"
            role="textbox"
            value={state.graphName}
            onChange={(e) => dispatch({ type: 'SET_NAME', name: e.target.value })}
            placeholder="e.g. billing_overview"
            className="w-full px-3 py-2 border border-slate-300 rounded text-sm"
          />
          {state.graphName && !/^[A-Za-z_][A-Za-z0-9_]*$/.test(state.graphName) && (
            <p className="text-xs text-red-500 mt-1">
              Name must be a valid identifier: letters, digits, underscore; no spaces
            </p>
          )}
          {state.graphName && /^[A-Za-z_][A-Za-z0-9_]*$/.test(state.graphName) && (
            <p className="text-xs text-gray-400 mt-1">
              Suggested file: <span data-testid="suggested-filename" className="font-mono not-italic">{suggestedFilename}</span>
            </p>
          )}
        </div>
        <div className="bg-slate-50 border rounded p-3 text-sm space-y-1">
          <p><span className="text-gray-500">Schema:</span> <strong>{state.schema.toUpperCase()}</strong></p>
          <p><span className="text-gray-500">Packages:</span> <strong>{state.selectedPackages.length}</strong></p>
          <p><span className="text-gray-500">Objects:</span> <strong>{state.objects.length}</strong></p>
          <p><span className="text-gray-500">File:</span> <span className="font-mono text-xs">{suggestedUri}</span></p>
        </div>
      </div>
    </div>
  );
}

export function CreateGraphWizard({ lspClient, projectRoot, onComplete, onCancel, onError = () => {} }: CreateGraphWizardProps) {
  const [state, dispatch] = useReducer(reducer, initialState);

  useEffect(() => {
    lspClient.getPackageGraph().then((pkgGraph) => {
      dispatch({ type: 'LOAD_PACKAGES', pkgGraph });
    });
  }, [lspClient]);

  const IDENT_REGEX = /^[A-Za-z_][A-Za-z0-9_]*$/;

  const canNext = () => {
    if (state.step === 1) return state.selectedPackages.length > 0;
    if (state.step === 3) return state.objects.length > 0;
    if (state.step === 5) return IDENT_REGEX.test(state.graphName);
    return true;
  };

  const handleSave = async () => {
    const name = state.graphName || 'Untitled';
    const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    const suggestedFilename = `${slug}.ttrg`;
    const suggestedUri = `${projectRoot.replace(/\/$/, '')}/graphs/${suggestedFilename}`;
    let edit: WorkspaceEdit;
    try {
      edit = await lspClient.createGraph({
        uri: suggestedUri,
        name,
        schema: state.schema,
        packages: state.selectedPackages,
        objects: state.objects,
      });
    } catch (err) {
      onError(`Failed to create graph: ${err}`);
      return;
    }
    const changes = edit?.documentChanges;
    if (!changes?.length) {
      onError('Failed to create graph: no document changes returned');
      return;
    }
    const createOp = changes.find((c) => 'kind' in c && (c as { kind: string }).kind === 'create') as { kind: 'create'; uri: string } | undefined;
    const textEdits = changes.filter((c) => 'edits' in c);
    const newText = (textEdits[0] as { edits: Array<{ newText: string }> } | undefined)?.edits?.[0]?.newText ?? '';
    if (createOp) {
      try {
        await lspClient.openDocument(createOp.uri, newText);
      } catch (err) {
        onError(`Failed to open graph file: ${err}`);
        return;
      }
    }
    onComplete(suggestedUri);
  };

  const stepLabels = ['Packages', 'Dependencies', 'Objects', 'Schema', 'Name & Save'];

  return (
    <div className="flex flex-col items-center justify-center flex-1 gap-6 p-8 bg-gray-50">
      <div className="w-full max-w-lg">
        <div className="bg-white border border-slate-300 rounded-xl shadow-lg p-6">
          <StepDots current={state.step} />

          <div className="min-h-[280px]">
            {state.step === 1 && <Step1 state={state} dispatch={dispatch} />}
            {state.step === 2 && <Step2 state={state} dispatch={dispatch} />}
            {state.step === 3 && <Step3 state={state} dispatch={dispatch} lspClient={lspClient} />}
            {state.step === 4 && <Step4 state={state} dispatch={dispatch} />}
            {state.step === 5 && <Step5 state={state} dispatch={dispatch} projectRoot={projectRoot} />}
          </div>
        </div>

        <div className="flex items-center justify-between mt-4">
          <div className="flex gap-2">
            <button
              onClick={() => dispatch({ type: 'BACK' })}
              disabled={state.step === 1}
              className="px-4 py-2 text-sm border border-slate-300 rounded hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              Back
            </button>
            <button
              onClick={onCancel}
              className="px-4 py-2 text-sm border border-slate-300 rounded hover:bg-slate-50"
            >
              Cancel
            </button>
          </div>

          <div className="flex gap-2">
            {state.step < MAX_STEP ? (
              <button
                onClick={() => dispatch({ type: 'NEXT' })}
                disabled={!canNext()}
                className="px-4 py-2 text-sm bg-sky-500 text-white rounded hover:bg-sky-600 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Next
              </button>
            ) : (
              <button
                onClick={handleSave}
                disabled={state.step === 5 ? !canNext() : !state.graphName.trim()}
                className="px-4 py-2 text-sm bg-emerald-500 text-white rounded hover:bg-emerald-600 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Save
              </button>
            )}
          </div>
        </div>

        <div className="mt-3 text-center text-xs text-gray-400">
          {stepLabels[state.step - 1]}
        </div>
      </div>
    </div>
  );
}