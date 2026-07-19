// SPDX-License-Identifier: Apache-2.0
// The skin render contract (contracts §1 / DS D-1, E-1 β) — the plugin API. Pure TS,
// React-free (PF wall): the actual node renderer is an OPAQUE token here (the shell package
// injects a React component); canvas-core only sees, validates, and routes it.

import type {
  CanvasEdge, CanvasNode, CanvasPort, CanvasGraph, NodeBaseState, SchemaCode,
} from './types.js';
import type { Theme } from './tokens.js';

// ---- ids (contracts §1; pinned roster, additive growth only) ----
export type SkinId = string;

/** The v1 roster ids (contracts §1 / §1.4). Do not invent ids. */
export const KNOWN_SKIN_IDS = [
  'stage', 'script', // processing
  'db.table-classic', 'er.crow', 'md.star-glyph', 'md.er-dialect', 'cnc.bubbles', 'cnc.cards', // modeling
] as const;

// ---- presentation parameter surface ----
export type Orientation = 'LR' | 'TD';
export type Positions = Record<string, { x: number; y: number }>;
export interface NodeSize { width: number; height: number }

export interface LayoutParams {
  nodeSpacing?: number; // ELK elk.spacing.nodeNode
  layerSpacing?: number; // ELK elk.layered.spacing.nodeNodeBetweenLayers
  /** skin-declared custom layout (star-glyph orbit, cnc bubbles) — bypasses ELK (DS-P3) */
  custom?: (graph: CanvasGraph, sizeOf: (n: CanvasNode) => NodeSize) => Positions;
}

export interface CanvasStyle {
  background: string;
  density?: 'compact' | 'comfortable';
  grid?: 'dots' | 'lines' | 'none';
}

export interface EdgeStyleSpec {
  stroke: string;
  width: number;
  dash?: string; // dashed ⇒ control (D-4)
  marker?: 'arrow' | 'none';
}

export type PortShape = 'circle' | 'square' | 'triangle' | 'diamond';
export interface PortGeometrySpec {
  shape: PortShape;
  /** placement is skin (geometry); VISIBILITY is base (D-2) — a skin cannot hide a port */
  placement: 'flow-in' | 'flow-out' | 'cross-in' | 'cross-out';
  size?: number;
}

export interface ContainerRegionSpec {
  headerHeight?: number;
  ghostContent?: boolean; // D-3 β ghosted contents hint
  drillAffordance?: boolean; // ⌕ open
}

export interface RenderContext {
  skin: SkinDefinition;
  theme: Theme;
}

// ---- anchors (contracts §1.1) — node-local points the app draws base chrome at ----
export type AnchorAlign = 'tl' | 'tr' | 'bl' | 'br' | 'edge';
export interface AnchorPoint { x: number; y: number; align: AnchorAlign }

export interface AnchorDeclaration {
  status?: AnchorPoint; // required unless the skin claims `status`
  diagnostics?: AnchorPoint; // required unless the skin claims `diagnostics`
  chrome: AnchorPoint; // selection / read-only / orphan cluster — ALWAYS required (never claimable)
  previewChip?: AnchorPoint; // display nodes only
}

/** Opaque renderer token — the shell injects a React component; canvas-core never renders it. */
export type SkinNodeRenderer = unknown;

/** What a skin's renderNode receives (contracts §1.1). Anchors are RESOLVED by the kernel
 *  from `skin.declareAnchors(size)` (pull model — registration-checkable, unlike a push
 *  callback). React-free shape; the designer's component consumes it. */
export interface NodeRenderProps {
  node: CanvasNode;
  state: NodeBaseState;
  anchors: AnchorDeclaration;
  theme: Theme;
}

// ---- the skin definition (contracts §1) ----
export interface SkinDefinition {
  id: SkinId;
  face: 'processing' | 'modeling';
  modelKind?: SchemaCode; // modeling skins are kind-scoped (E-2)
  displayName: string;
  description: string;

  flow: { orientation: Orientation; layout: LayoutParams }; // D-4: data on flow axis
  canvas: CanvasStyle;
  edgeStyle: (edge: CanvasEdge, ctx: RenderContext) => EdgeStyleSpec; // inherits D-4 β
  portGeometry: (port: CanvasPort, node: CanvasNode) => PortGeometrySpec; // shape+placement only

  /** node-local anchor points for base chrome, given a node's size. Called at registration
   *  (probe) to validate presence, and by the kernel at render to place chrome. */
  declareAnchors: (size: NodeSize) => AnchorDeclaration;

  renderNode: SkinNodeRenderer; // opaque React token (injected by the shell)
  containerStyle: ContainerRegionSpec; // D-3 β

  /** per-slot claims (§1.2). ONLY status/diagnostics are claimable. Absent ⇒ base draws. */
  claims?: { status?: true; diagnostics?: true };
}

// ---- the slot / claim policy (D-1) ----
export const CLAIMABLE_SLOTS = ['status', 'diagnostics'] as const;
export const NEVER_CLAIMABLE_SLOTS = ['selection', 'focus', 'readOnly', 'derived', 'orphanedLayout'] as const;
export type ClaimableSlot = (typeof CLAIMABLE_SLOTS)[number];
