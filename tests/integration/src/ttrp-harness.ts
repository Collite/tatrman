// Harness for driving the REAL Kotlin TTR-P LSP over real stdio — the exact transport
// + launcher VS Code's Executable server options use. NOT @vscode/test-electron (heavy;
// tests VS Code more than us). This is the contract the Stage-4.3 extension client relies on.
//
// CI ordering: `./gradlew :packages:kotlin:ttrp-lsp:installDist` MUST run before this
// suite (the launcher is a build output). The beforeAll guard fails loudly with the fix.

import { type ChildProcess, spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import {
  createProtocolConnection,
  StreamMessageReader,
  StreamMessageWriter,
  type ProtocolConnection,
} from 'vscode-languageserver-protocol/node';

export const SERVER_BIN = fileURLToPath(
  new URL(
    '../../../packages/kotlin/ttrp-lsp/build/install/ttrp-lsp/bin/ttrp-lsp' +
      (process.platform === 'win32' ? '.bat' : ''),
    import.meta.url,
  ),
);

export interface TtrpServer {
  proc: ChildProcess;
  conn: ProtocolConnection;
  stop(): Promise<void>;
}

export function startTtrpServer(): TtrpServer {
  const proc = spawn(SERVER_BIN, [], { stdio: ['pipe', 'pipe', 'inherit'] });
  const conn = createProtocolConnection(
    new StreamMessageReader(proc.stdout!),
    new StreamMessageWriter(proc.stdin!),
  );
  conn.listen();
  return {
    proc,
    conn,
    async stop() {
      conn.dispose();
      if (!proc.killed) proc.kill();
    },
  };
}
