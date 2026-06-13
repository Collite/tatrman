import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { LanguageClient, TransportKind, NodeModule } from 'vscode-languageclient';

// Resolve the LSP server entry point.
//
// In a packaged .vsix the extension is self-contained: `just vscode` bundles a
// fully-inlined ESM server (all @modeler/* + antlr4ng + vscode-languageserver
// folded in) to `dist/server/server-stdio.mjs`. There is no node_modules tree to
// resolve against, so we point Node straight at that file.
//
// During F5 development that bundle doesn't exist; fall back to resolving the
// LSP from its workspace location, where its externalized deps (parser,
// semantics, edit, antlr4ng) are reachable via node_modules symlinks.
function resolveServerPath(context: vscode.ExtensionContext): string {
  const bundled = context.asAbsolutePath(path.join('dist', 'server', 'server-stdio.mjs'));
  if (fs.existsSync(bundled)) return bundled;
  return require.resolve('@modeler/lsp/server-stdio');
}

export function activate(context: vscode.ExtensionContext) {
  const serverPath = resolveServerPath(context);

  const serverModule: NodeModule = {
    module: serverPath,
    transport: TransportKind.stdio,
  };

  const serverOptions = {
    run: serverModule,
    debug: serverModule,
  };

  const client = new LanguageClient('ttr', 'TTR Language Server', serverOptions, {
    documentSelector: [
      { scheme: 'file', language: 'ttr' },
      { scheme: 'file', language: 'ttrg' },
    ],
    outputChannelName: 'TTR Language Server',
    synchronize: {
      // Notify the server when `.ttr` files change on disk outside the editor
      // (external edits, git operations, create/delete) so the whole-project
      // symbol table stays current for cross-file reference resolution.
      fileEvents: vscode.workspace.createFileSystemWatcher('**/*.ttr'),
    },
  });

  context.subscriptions.push(
    vscode.commands.registerCommand('modeler.openInDesigner', () => {
      vscode.window.showInformationMessage('Designer integration will be wired in Phase 3.');
    }),
    // Invoked by the "N references" code lens.
    vscode.commands.registerCommand('modeler.showReferences', (qname?: string) => {
      vscode.window.showInformationMessage(
        `Use "Find All References" (Shift+F12) on ${qname ?? 'the symbol'} to list its references.`,
      );
    }),
    // Invoked by the "N files in package" code lens.
    vscode.commands.registerCommand('modeler.listPackageFiles', async (pkg?: string) => {
      if (!pkg) return;
      const uris = await vscode.workspace.findFiles('**/*.ttr');
      const inPkg: string[] = [];
      for (const uri of uris) {
        const text = (await vscode.workspace.openTextDocument(uri)).getText();
        if (new RegExp(`(^|\\n)package\\s+${pkg.replace(/[.]/g, '\\.')}(\\s|$)`).test(text)) {
          inPkg.push(vscode.workspace.asRelativePath(uri));
        }
      }
      const pick = await vscode.window.showQuickPick(inPkg.length ? inPkg : ['(no files found)'], {
        title: `Files in package ${pkg}`,
      });
      if (pick && inPkg.includes(pick)) {
        const match = uris.find((u) => vscode.workspace.asRelativePath(u) === pick);
        if (match) void vscode.window.showTextDocument(match);
      }
    }),
  );

  client.start();
}

export function deactivate(): Thenable<void> | undefined {
  return undefined;
}
