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

  // Validate-what-you-apply (C4-d): a candidate is not a standalone program — it is text spliced at
  // the cursor. Validate the DOCUMENT as it would read after Apply, resolved against the open doc's
  // own project/world (pass `uri`). Validating the candidate alone (no uri) resolved it against the
  // server's CWD with no world → every candidate drew TTRP-WLD-001 and the loop could never pass.
  const baseText = editor.document.getText();
  const insertOffset = editor.document.offsetAt(position);
  const splice = (candidate: string): string =>
    baseText.slice(0, insertOffset) + candidate + '\n' + baseText.slice(insertOffset);

  const outcome = await runAssistLoop({
    context: JSON.stringify(ac.bundle),
    request,
    model,
    validate: (candidate) => lspValidate(client, uri, splice(candidate), dialect),
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
    content: splice(outcome.candidate), // show the document as it will read after Apply, not the bare fragment
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
    await dumpCandidateIfConfigured(outcome.candidate);
  }
}

// Eval-baseline support (T7.2.7): when `ttrp.assist.dumpCandidatesDir` is set, also write the
// Applied candidate to <dir>/<corpus-id>.ttrp — the shape `ttrp eval --candidates <dir>` expects
// (see ttrp-conform/src/test/eval/corpus.toml's `id` field). Asks for the corpus id per-candidate
// rather than inferring it from the request text: the corpus format is Kotlin-side and this host
// stays decoupled from it, per the C4-d-ii boundary (LLM/host vs compiler/toolchain).
async function dumpCandidateIfConfigured(candidate: string): Promise<void> {
  const dir = vscode.workspace.getConfiguration('ttrp.assist').get<string>('dumpCandidatesDir')?.trim();
  if (!dir) return;
  const id = await vscode.window.showInputBox({
    prompt: 'Eval corpus entry id for this candidate (e.g. eval-003) — leave empty to skip the dump',
  });
  if (!id) return;
  const dest = vscode.Uri.joinPath(vscode.Uri.file(dir), `${id}.ttrp`);
  await vscode.workspace.fs.writeFile(dest, new TextEncoder().encode(candidate));
  void vscode.window.showInformationMessage(`TTR-P assist: candidate dumped to ${dest.fsPath}`);
}

function createModel(endpoint: string, model: string, apiKey: string | undefined): ModelProvider {
  if (endpoint.startsWith('mock:')) {
    // Deterministic offline provider for the Extension Dev Host demo (no network, no key): emits a
    // real TTR-P fragment for the crunch container — first with `==` (trips TTRP-EQ-001), repaired to
    // `=` once that diagnostic is fed back. Proves generate → validate → repair → Apply end-to-end
    // against the LIVE checker in the `demo/` workspace (its output is valid TTR-P, unlike a prose
    // sentence, which fails to parse). Assumes the cursor sits in a canonical `ttrp` container where
    // `sales` is in scope (the crunch container of `demo/hero.ttrp`).
    return {
      complete: async (prompt) =>
        prompt.includes('TTRP-EQ-001')
          ? 'hot = filter(sales, amount = 100000)'
          : 'hot = filter(sales, amount == 100000)',
    };
  }
  // OpenAI-compatible chat-completions provider (non-streaming): `Bearer <key>` auth on a
  // `/v1/chat/completions` endpoint, a `{messages}` body, reads `choices[].message.content`. Works
  // against any OpenAI-shaped gateway; the model tag is gateway-specific (some map deployment
  // aliases). Legacy `{completion,text,choices[].text}` shapes stay readable as a fallback.
  const bearer = apiKey?.trim(); // a pasted key with a trailing newline/space is a common 401 cause — trim it
  return {
    complete: async (prompt) => {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'content-type': 'application/json', ...(bearer ? { authorization: `Bearer ${bearer}` } : {}) },
        body: JSON.stringify({ model, messages: [{ role: 'user', content: prompt }], temperature: 0, stream: false }),
      });
      if (!res.ok) {
        throw new Error(`assist endpoint HTTP ${res.status}: ${await res.text().catch(() => '')}`);
      }
      const json = (await res.json()) as {
        completion?: string;
        text?: string;
        choices?: { text?: string; message?: { content?: string } }[];
      };
      return String(
        json.choices?.[0]?.message?.content ?? json.choices?.[0]?.text ?? json.completion ?? json.text ?? '',
      );
    },
  };
}

async function lspValidate(
  client: LanguageClient,
  uri: string,
  source: string,
  dialect: string,
): Promise<AssistDiagnostic[]> {
  // Pass `uri` so the server resolves the candidate against the open document's project/world
  // (walk-up to modeler.toml), and `source` as the spliced whole-document text to check.
  const res = await client.sendRequest<{
    diagnostics: { code: string; severity: string; message: string; suggestedAlternative?: string }[];
  }>('ttrp/validate', { uri, source, dialect });
  return (res.diagnostics ?? [])
    .filter((d) => d.severity.toLowerCase() === 'error')
    .map((d) => ({ id: d.code, message: d.message, suggestion: d.suggestedAlternative }));
}
