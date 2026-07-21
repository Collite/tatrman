// The text drawer (A-3 β) — v1 is the READ-ONLY half: the textual property panel escalates
// to a full-text PEEK of the node's source (from the LSP document + sourceLocation). An edit
// gesture routes to DS-EDIT-001 (peek + open-in-IDE handoff) — there is NO editable surface
// in this phase (the embedded editor is DS-P6, gated on edit mode).

import { useEffect, useState } from 'react';
import { DIAGNOSTICS } from '@tatrman/canvas-core';
import type { LineageRootRef } from '@tatrman/lsp';

export interface DrawerNode {
  qname: string;
  kind: string;
  label: string;
  description?: string;
  sourceText?: string;
  sourceUri?: string;
  sourceLine?: number;
  /** the lineage entry root (contracts §5); present ⇒ the Lineage affordance is enabled. */
  lineageRoot?: LineageRootRef;
  /** the ObjectKind to root lineage at (member's semantic kind, else the node kind). */
  rootKind?: string;
}

// lineage roots from a column/attribute/measure/calc row (contracts §4.2 any-root rule).
const LINEAGE_ROOTABLE = new Set(['column', 'attribute', 'measure', 'calc']);

export function TextDrawer({
  open, node, onOpenInIde, onClose, onOpenLineage, memberLineageCapable = true, editEnabled = false, onSaveEdit,
}: {
  open: boolean;
  node: DrawerNode | null;
  onOpenInIde: (uri: string, line?: number) => void;
  onClose: () => void;
  /** root a lineage perspective at this object (detail-panel entry point, S2.T5 / W2).
   *  The 4th arg carries the entry's LineageRootRef (`kind:'member'` for a member). */
  onOpenLineage?: (qname: string, kind: string, label: string, root?: LineageRootRef) => void;
  /** false on backends that can't resolve members (WS/Veles) ⇒ A1-CAP-001 visible degrade. */
  memberLineageCapable?: boolean;
  /** edit-mode gate (PL G-4). ON ⇒ Edit escalates peek → embedded editor (A-3 β). */
  editEnabled?: boolean;
  /** save routes through the ONE WorkspaceEdit path (applyGraphEdit-class seam) — never a 2nd write. */
  onSaveEdit?: (node: DrawerNode, newText: string) => void;
}) {
  const [peeking, setPeeking] = useState(false);
  const [editHint, setEditHint] = useState(false);
  const [editing, setEditing] = useState<string | null>(null); // the editor buffer (null = not editing)

  // reset the editor buffer whenever the drawer's subject changes — otherwise an in-flight edit of
  // node A would be saved onto node B when the selection changes (cross-node write contamination).
  const qname = node?.qname;
  useEffect(() => { setEditing(null); setEditHint(false); }, [qname]);

  if (!open || !node) return null;

  const editText = DIAGNOSTICS['DS-EDIT-001'].text();

  return (
    <aside data-testid="text-drawer" style={{ width: 320, flex: '0 0 auto', background: '#fff', borderLeft: '1px solid #CBD8E6', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div style={{ padding: '10px 14px', borderBottom: '1px solid #EDF2F9', display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <span style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '.06em', color: '#96989B' }}>{node.kind}</span>
        <span data-testid="drawer-label" style={{ fontSize: 15, fontWeight: 'bold', color: '#16283F' }}>{node.label}</span>
        <button data-testid="drawer-close" onClick={onClose} style={{ marginLeft: 'auto', border: 'none', background: 'transparent', cursor: 'pointer', color: '#96989B' }}>×</button>
      </div>

      {/* property panel (textual, C1-d) */}
      <div data-testid="property-panel" style={{ padding: '10px 14px', fontSize: 12.5, borderBottom: '1px solid #EDF2F9' }}>
        <div style={{ color: '#4A4B4D' }}>{node.description ?? <span style={{ color: '#96989B' }}>No description.</span>}</div>
        {/* Lineage affordance (W2, contracts §5): enabled iff a lineageRoot resolved (or a
            rootable kind). On a backend that can't resolve members it degrades VISIBLY —
            disabled + A1-CAP-001 reason, never absent (DS-P4 auto-activation / honest degrade). */}
        {onOpenLineage && (node.lineageRoot || LINEAGE_ROOTABLE.has(node.kind)) && (
          memberLineageCapable ? (
            <button
              data-testid="open-lineage"
              onClick={() => onOpenLineage(node.qname, node.rootKind ?? node.kind, node.label, node.lineageRoot)}
              style={{ marginTop: 8, padding: '4px 10px', border: '1px solid #CBD8E6', borderRadius: 6, background: '#f5f8fc', cursor: 'pointer', font: 'inherit', fontSize: 12, color: '#33506e' }}
              title="Trace this value's lineage across faces"
            >
              ⧉ Trace lineage
            </button>
          ) : (
            <div style={{ marginTop: 8 }}>
              <button
                data-testid="open-lineage"
                disabled
                aria-disabled="true"
                style={{ padding: '4px 10px', border: '1px solid #CBD8E6', borderRadius: 6, background: '#f0f2f5', cursor: 'not-allowed', font: 'inherit', fontSize: 12, color: '#96989B' }}
                title="Member lineage needs the Worker backend"
              >
                ⧉ Trace lineage
              </button>
              <div data-testid="a1-cap-001" style={{ marginTop: 4, fontSize: 11, color: '#8a6a10' }}>
                A1-CAP-001 · member lineage is available on the Worker backend only
              </div>
            </div>
          )
        )}
      </div>

      {/* escalation → read-only peek of the source text */}
      {!peeking ? (
        <button data-testid="peek-escalate" onClick={() => setPeeking(true)} style={{ margin: 12, padding: '6px 10px', border: '1px solid #CBD8E6', borderRadius: 6, background: '#f5f8fc', cursor: 'pointer', font: 'inherit', fontSize: 12.5, color: '#16283F' }}>
          Peek source ▾
        </button>
      ) : (
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, padding: 12, gap: 8 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {editing === null && <span data-testid="peek-readonly-badge" title={editEnabled ? 'peek' : 'read-only'}>{editEnabled ? '✎ editable' : '🔒 read-only'}</span>}
            {editing === null && (
              <button
                data-testid="drawer-edit"
                onClick={() => { if (editEnabled) setEditing(node.sourceText ?? ''); else setEditHint(true); }}
                style={{ marginLeft: 'auto', border: '1px solid #CBD8E6', borderRadius: 6, background: '#fff', cursor: 'pointer', font: 'inherit', fontSize: 12, padding: '3px 8px' }}
              >
                Edit
              </button>
            )}
            {node.sourceUri && editing === null && (
              <button data-testid="open-in-ide" onClick={() => onOpenInIde(node.sourceUri!, node.sourceLine)} style={{ border: '1px solid #CBD8E6', borderRadius: 6, background: '#fff', cursor: 'pointer', font: 'inherit', fontSize: 12, padding: '3px 8px', color: '#4A75A8' }}>Open in IDE</button>
            )}
          </div>

          {editing === null ? (
            /* the peek is a <pre> — NOT an editable surface (read-only half, DS-P2) */
            <pre data-testid="peek-source" style={{ flex: 1, overflow: 'auto', margin: 0, padding: 8, background: '#fbfcfe', border: '1px solid #EDF2F9', borderRadius: 6, fontFamily: 'Consolas, monospace', fontSize: 11.5, color: '#16283F', whiteSpace: 'pre-wrap' }}>
              {node.sourceText ?? '// source unavailable'}
            </pre>
          ) : (
            /* the embedded editor (A-3 β, edit-gated) — a save routes through the ONE WorkspaceEdit path */
            <>
              <textarea
                data-testid="drawer-editor"
                value={editing}
                onChange={(e) => setEditing(e.target.value)}
                spellCheck={false}
                style={{ flex: 1, resize: 'none', padding: 8, background: '#fff', border: '1px solid #5B7EA6', borderRadius: 6, fontFamily: 'Consolas, monospace', fontSize: 11.5, color: '#16283F' }}
              />
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button data-testid="drawer-cancel" onClick={() => setEditing(null)} style={{ border: '1px solid #CBD8E6', borderRadius: 6, background: '#fff', cursor: 'pointer', font: 'inherit', fontSize: 12, padding: '3px 10px' }}>Cancel</button>
                <button
                  data-testid="drawer-save"
                  disabled={editing === (node.sourceText ?? '')}
                  onClick={() => { onSaveEdit?.(node, editing); setEditing(null); }}
                  title={editing === (node.sourceText ?? '') ? 'No changes to save' : 'Save'}
                  style={{ border: '1px solid #3E7D4E', borderRadius: 6, background: editing === (node.sourceText ?? '') ? '#A9C6B1' : '#3E7D4E', color: '#fff', cursor: editing === (node.sourceText ?? '') ? 'not-allowed' : 'pointer', font: 'inherit', fontSize: 12, padding: '3px 10px' }}
                >
                  Save
                </button>
              </div>
            </>
          )}

          {editHint && !editEnabled && (
            <div data-testid="ds-edit-001" style={{ fontSize: 12, color: '#33506e', background: '#EDF2F9', border: '1px solid #5B7EA6', borderRadius: 6, padding: '6px 10px' }}>
              {editText}
              {node.sourceUri && (
                <button data-testid="edit-open-in-ide" onClick={() => onOpenInIde(node.sourceUri!, node.sourceLine)} style={{ marginLeft: 8, border: 'none', background: 'transparent', cursor: 'pointer', color: '#4A75A8', textDecoration: 'underline', font: 'inherit' }}>open in IDE</button>
              )}
            </div>
          )}
        </div>
      )}
    </aside>
  );
}
