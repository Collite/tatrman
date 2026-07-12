// SPDX-License-Identifier: Apache-2.0
import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';
import { runAssistLoop, ModelProvider } from './assistLoop';
import { AssistDiagnostic } from './prompt';

// The reference assist host (C4-d-ii = γ): the LLM call lives ENTIRELY here (endpoint/model from
// settings, API key from SecretStorage) — the Kotlin toolchain only contributes ttrp/authoringContext
// + ttrp/validate, never a model dependency or a secret. Generated text is NEVER applied silently
// (C4-d-iii): a diff + explicit modal Apply/Discard gates every edit; on repair-exhaustion, nothing.

export function registerAssistCommands(
  context: vscode.ExtensionContext,
  getClient: () => LanguageClient | undefined,
): void {
  context.subscriptions.push(
    vscode.commands.registerCommand('ttrp.assist.setApiKey', () => setApiKey(context)),
    vscode.commands.registerCommand('ttrp.assist.generate', () => generate(context, getClient())),
  );
}

async function setApiKey(context: vscode.ExtensionContext): Promise<void> {
  const key = await vscode.window.showInputBox({
    prompt: 'Model API key — stored in VS Code SecretStorage, never in settings and never sent to the LSP.',
    password: true,
  });
  if (key) {
    await context.secrets.store('ttrp.assist.apiKey', key);
    void vscode.window.showInformationMessage('TTR-P assist: API key stored.');
  }
}

interface Bundle {
  scope?: { insertionTarget?: { dialect?: string } };
}

async function generate(context: vscode.ExtensionContext, client: LanguageClient | undefined): Promise<void> {
  const editor = vscode.window.activeTextEditor;
  if (!editor || !client) {
    void vscode.window.showWarningMessage('TTR-P: open a .ttrp file first.');
    return;
  }
  const uri = editor.document.uri.toString();
  const position = editor.selection.active; // the host declaring the insertion target (C4-d-i γ).

  const ac = await client.sendRequest<{ bundle: Bundle }>('ttrp/authoringContext', { uri, position });
  const dialect = ac.bundle.scope?.insertionTarget?.dialect ?? 'ttrp';

  const request = await vscode.window.showInputBox({ prompt: `Describe the ${dialect} to generate` });
  if (!request) return;

  const cfg = vscode.workspace.getConfiguration('ttrp.assist');
  const endpoint = (cfg.get<string>('endpoint') ?? 'mock:').trim();
  const modelId = cfg.get<string>('model') ?? 'mock';
  const maxRepairs = cfg.get<number>('maxRepairs') ?? 3;
  const apiKey = await context.secrets.get('ttrp.assist.apiKey');
  const model = createModel(endpoint, modelId, apiKey);

  const outcome = await runAssistLoop({
    context: JSON.stringify(ac.bundle),
    request,
    model,
    validate: (candidate) => lspValidate(client, candidate, dialect),
    maxRepairs,
  });

  if (!outcome.ok) {
    void vscode.window.showErrorMessage(
      `TTR-P assist: no valid candidate after ${outcome.attempts} attempt(s): ` +
        outcome.diagnostics.map((d) => d.id).join(', '),
    );
    return; // exit gate (C4-d-iii): present NOTHING.
  }

  const proposed = await vscode.workspace.openTextDocument({
    content: outcome.candidate,
    language: editor.document.languageId,
  });
  await vscode.commands.executeCommand('vscode.diff', editor.document.uri, proposed.uri, 'TTR-P Assist: current ↔ proposed');
  const pick = await vscode.window.showInformationMessage(
    'Apply the generated TTR-P?',
    { modal: true },
    'Apply',
    'Discard',
  );
  if (pick === 'Apply') {
    const edit = new vscode.WorkspaceEdit();
    edit.insert(editor.document.uri, position, outcome.candidate + '\n');
    await vscode.workspace.applyEdit(edit);
  }
}

function createModel(endpoint: string, model: string, apiKey: string | undefined): ModelProvider {
  if (endpoint.startsWith('mock:')) {
    // Deterministic offline provider for the Extension Dev Host demo (no network, no key): a first
    // draft with `==` (invalid), repaired to `=` once the EQ-001 diagnostic is fed back — proving
    // the generate → validate → repair loop end-to-end.
    return {
      complete: async (prompt) =>
        prompt.includes('TTRP-EQ-001')
          ? "Keep the rows where status = 'open'."
          : "Keep the rows where status == 'open'.",
    };
  }
  return {
    complete: async (prompt) => {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'content-type': 'application/json', ...(apiKey ? { authorization: `Bearer ${apiKey}` } : {}) },
        body: JSON.stringify({ model, prompt }),
      });
      const json = (await res.json()) as { completion?: string; text?: string; choices?: { text?: string }[] };
      return String(json.completion ?? json.text ?? json.choices?.[0]?.text ?? '');
    },
  };
}

async function lspValidate(client: LanguageClient, source: string, dialect: string): Promise<AssistDiagnostic[]> {
  const res = await client.sendRequest<{
    diagnostics: { code: string; severity: string; message: string; suggestedAlternative?: string }[];
  }>('ttrp/validate', { source, dialect });
  return (res.diagnostics ?? [])
    .filter((d) => d.severity.toLowerCase() === 'error')
    .map((d) => ({ id: d.code, message: d.message, suggestion: d.suggestedAlternative }));
}
