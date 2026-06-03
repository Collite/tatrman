import * as vscode from 'vscode';
import { LanguageClient, TransportKind, NodeModule } from 'vscode-languageclient';

export function activate(context: vscode.ExtensionContext) {
  // Resolve the LSP server bundle from @modeler/lsp's workspace location.
  // The bundle externalizes @modeler/parser, @modeler/semantics, @modeler/edit
  // (see packages/lsp/package.json's `bundle-stdio` script), so it must be
  // launched from a directory where Node's module resolution can find those
  // workspace deps — i.e. node_modules/@modeler/lsp/dist/, not a copy.
  const serverPath = require.resolve('@modeler/lsp/server-stdio');

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
