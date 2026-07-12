// SPDX-License-Identifier: Apache-2.0
import * as path from 'node:path';
import * as glob from 'glob';
import Mocha from 'mocha';

export async function run(): Promise<void> {
  const mocha = new Mocha({
    ui: 'bdd',
    color: true,
    timeout: 30000,
  });

  const testsRoot = path.resolve(__dirname, '.');
  const files = await glob.glob('**/*.smoke.test.js', { cwd: testsRoot });

  for (const file of files) {
    mocha.addFile(path.resolve(testsRoot, file));
  }

  await new Promise<void>((resolve, reject) => {
    mocha.run((failures: number) => {
      if (failures > 0) reject(new Error(`${failures} test(s) failed.`));
      else resolve();
    });
  });
}
