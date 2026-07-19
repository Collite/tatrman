// SPDX-License-Identifier: Apache-2.0
// A fake ModelDataSource for the shell suites (DM-P2.S3). The shell consumes ModelDataSource
// (contracts §1), never an LspClient — so the ported DS shell tests drive it through this fake,
// which defaults to the full Worker-like capability profile (edit/perspectives/rich) and lets each
// test override the reads + capabilities it exercises.

import { vi } from 'vitest';
import type { GetGraphResponse, BindingMapData } from '@tatrman/lsp';
import type { ModelDataSource, DataSourceCapabilities } from '../../data/model-data-source.js';

const FULL_CAPS: DataSourceCapabilities = {
  edit: true, modelKinds: ['db', 'er', 'md', 'cnc'], bindings: true, perspectives: true,
  layoutPersist: 'in-file', graphShape: 'rich',
};

export interface FakeDataSourceOverrides {
  getGraph?: GetGraphResponse | null;
  getBindings?: BindingMapData;
  getSymbolDetail?: unknown;
  capabilities?: Partial<DataSourceCapabilities>;
}

export function fakeDataSource(o: FakeDataSourceOverrides = {}): ModelDataSource {
  return {
    capabilities: { ...FULL_CAPS, ...(o.capabilities ?? {}) },
    getModelIndex: vi.fn().mockResolvedValue({ packages: [], schemas: [], areas: [], counts: { objects: 0, schemas: 0, areas: 0 }, modelVersion: '' }),
    getModelGraph: vi.fn().mockResolvedValue({ nodes: [], edges: [] }),
    getGraph: vi.fn().mockResolvedValue(o.getGraph ?? null),
    getObject: vi.fn().mockResolvedValue({ object: { qname: '', kind: '', label: '', schema: '', pkg: '' }, sourceLocation: '', references: [] }),
    search: vi.fn().mockResolvedValue([]),
    onModelChanged: vi.fn().mockReturnValue({ dispose() {} }),
    getBindings: vi.fn().mockResolvedValue(o.getBindings ?? { entities: [], attributes: [], queries: [] }),
    getSymbolDetail: vi.fn().mockResolvedValue(o.getSymbolDetail ?? null),
  } as unknown as ModelDataSource;
}
