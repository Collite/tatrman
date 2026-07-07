// Copies the Gradle `installDist` tree of the TTR-P LSP into dist/server/ so a packaged
// .vsix ships a runnable server. extension.ts resolves dist/server/bin/<launcher>, and the
// Gradle launcher needs its sibling dist/server/lib — so the WHOLE install tree is copied,
// not just bin/. Run `./gradlew :packages:kotlin:ttrp-lsp:installDist` first.
const fs = require('fs');
const path = require('path');

const extRoot = path.resolve(__dirname, '..');
const installDir = path.resolve(extRoot, '..', 'kotlin', 'ttrp-lsp', 'build', 'install', 'ttrp-lsp');
const dest = path.join(extRoot, 'dist', 'server');

if (!fs.existsSync(installDir)) {
  console.error(
    `TTR-P server install not found at:\n  ${installDir}\n` +
      'Build it first:\n  ./gradlew :packages:kotlin:ttrp-lsp:installDist',
  );
  process.exit(1);
}

fs.rmSync(dest, { recursive: true, force: true });
fs.mkdirSync(path.dirname(dest), { recursive: true });
fs.cpSync(installDir, dest, { recursive: true });
console.log(`Bundled TTR-P server: ${installDir} -> ${dest}`);
