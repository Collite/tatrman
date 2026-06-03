import { ConfigurationItem } from 'vscode-languageserver';

export interface CompletionConfig {
  autoImport: boolean;
  // Loaded and documented, but not yet consumed: the v1.1 reference completion
  // emits one item per symbol (no FQN/bare pair to disambiguate), so there is
  // nothing to preselect. Reserved for a future dual-item completion model —
  // see H3-symbols-settings.md DONE / review-056 F1.5.
  preselectFullyQualified: boolean;
}

const DEFAULT_CONFIG: CompletionConfig = {
  autoImport: true,
  preselectFullyQualified: false,
};

let cachedConfig: CompletionConfig = { ...DEFAULT_CONFIG };

export function getCompletionConfig(): CompletionConfig {
  return { ...cachedConfig };
}

export function invalidateCompletionConfig(): void {
  cachedConfig = { ...DEFAULT_CONFIG };
}

export async function fetchCompletionConfig(
  connection: { sendRequest: (method: string, params: unknown) => Promise<unknown[]> },
  section: string,
  defaultValue: boolean
): Promise<boolean> {
  try {
    const configs = await connection.sendRequest(
      'workspace/configuration',
      { items: [{ section }] as ConfigurationItem[] }
    ) as Array<boolean | string | undefined>;
    if (typeof configs[0] === 'boolean') {
      return configs[0];
    }
  } catch {
    // client doesn't support workspace/configuration — use default
  }
  return defaultValue;
}

export async function loadCompletionConfig(
  connection: { sendRequest: (method: string, params: unknown) => Promise<unknown[]> }
): Promise<CompletionConfig> {
  const autoImport = await fetchCompletionConfig(connection, 'modeler.completion.autoImport', DEFAULT_CONFIG.autoImport);
  const preselectFullyQualified = await fetchCompletionConfig(connection, 'modeler.completion.preselectFullyQualified', DEFAULT_CONFIG.preselectFullyQualified);
  cachedConfig = { autoImport, preselectFullyQualified };
  return cachedConfig;
}