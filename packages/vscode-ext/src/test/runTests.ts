import * as path from 'node:path';
import { runTests } from '@vscode/test-electron';

async function main() {
  const extensionDevelopmentPath = path.resolve(__dirname, '../..');
  const extensionTestsPath = path.resolve(__dirname, './suite/index.js');

  await runTests({
    version: '1.96.0',
    extensionDevelopmentPath,
    extensionTestsPath,
    launchArgs: [path.resolve(__dirname, '../../../samples/v1-metadata')],
  });
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
