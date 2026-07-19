// SPDX-License-Identifier: Apache-2.0
// DM-P4.S4: the live ttrp/* processing backend — the read+run client + the shape adapter + the
// ProcessingGraphSource/RunSource impls, behind the same interfaces the fixtures implement. `App`
// constructs these when a ttrp server (:9257) is configured; the fixtures remain the dev/test default.
export { TtrpLspClient, type RunResult, type PublishedDiagnostic } from './ws-client.js';
export { TtrpServerProcessingSource, TtrpServerRunSource, type TtrpReadRunClient } from './ttrp-source.js';
export { ttrpToProcessingGraph } from './to-processing-graph.js';
export type { GetGraphResult, GraphView, ContainerView, NodeView, EdgeView } from './types.js';
