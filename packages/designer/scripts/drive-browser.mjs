// Headless browser driver for the Designer — loads the app, classifies which
// screen it settled on, captures console/page errors, counts what React Flow
// actually rendered, and screenshots each step.
//
// Usage:
//   node packages/designer/scripts/drive-browser.mjs [url] [subject]
// Env:
//   PW_EXECUTABLE  override the chromium binary (default: newest cached shell,
//                  else the system Google Chrome)
//   OUT_DIR        screenshot output dir (default: /tmp/designer-shots)
//   SETTLE_MS      how long to wait for the app to leave "Loading…" (default 25000)
//
// Requires the dev server already running (pnpm --filter @tatrman/designer dev)
// AND the workspace deps built (pnpm --filter "@tatrman/designer^..." build) —
// without the build, Vite dies at dep-scan and every run here just times out.
//
// Exit code is 1 when the app failed to reach a usable state, so this is usable
// as a smoke check and not only as a reporting tool.
//
// NOTE: the canvas is React Flow (`@xyflow/react`), not Cytoscape. Nodes are DOM
// elements (`.react-flow__node`), NOT a <canvas> bitmap, so counts/positions are
// read from the DOM — there is no `window.__cy` handle and no canvas to wait for.

import { chromium } from 'playwright-core';
import { mkdirSync, existsSync, readdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

const URL = process.argv[2] ?? 'http://localhost:5173/?demo=v1.1-mini';
const SUBJECT = process.argv[3] ?? null; // catalog subject to open; null = first in the rail
const OUT_DIR = process.env.OUT_DIR ?? '/tmp/designer-shots';
const SETTLE_MS = Number(process.env.SETTLE_MS ?? 25000);

/** Newest cached playwright shell, else system Chrome. The old hardcoded 1217 path
 *  silently broke whenever the cache rolled forward. */
function resolveExecutable() {
  if (process.env.PW_EXECUTABLE) return process.env.PW_EXECUTABLE;
  const cache = join(homedir(), 'Library/Caches/ms-playwright');
  if (existsSync(cache)) {
    const shells = readdirSync(cache)
      .filter((d) => d.startsWith('chromium_headless_shell-'))
      .sort((a, b) => Number(b.split('-')[1]) - Number(a.split('-')[1]));
    for (const s of shells) {
      for (const arch of ['mac-arm64', 'mac-x64', 'linux64']) {
        const p = join(cache, s, `chrome-headless-shell-${arch}`, 'chrome-headless-shell');
        if (existsSync(p)) return p;
      }
    }
  }
  const sys = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
  if (existsSync(sys)) return sys;
  throw new Error('No chromium found — set PW_EXECUTABLE or run `npx playwright install chromium`.');
}

mkdirSync(OUT_DIR, { recursive: true });

const consoleMsgs = [];
const pageErrors = [];
const failedRequests = [];

const browser = await chromium.launch({ executablePath: resolveExecutable(), headless: true });
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });

page.on('console', (m) => consoleMsgs.push({ type: m.type(), text: m.text().slice(0, 300) }));
page.on('pageerror', (e) => pageErrors.push(String(e).slice(0, 400)));
page.on('requestfailed', (r) =>
  failedRequests.push({ url: r.url().slice(0, 200), error: r.failure()?.errorText ?? null }),
);

/** Which screen is the app on? Mirrors the render branches in App.tsx's WorkerStudio. */
const classify = () =>
  page.evaluate(() => {
    const has = (sel) => !!document.querySelector(sel);
    const text = document.body.innerText ?? '';
    if (has('[data-testid="studio-header"]') || has('[data-testid="shell-frame"]')) return 'studio';
    if ([...document.querySelectorAll('h2')].some((h) => h.textContent?.includes('Invalid backend selection')))
      return 'backend-error';
    if (/Failed to load (demo|project)/.test(text)) return 'load-error';
    if (text.includes('Open a local project folder')) return 'splash-idle';
    if (text.includes('Loading')) return 'loading';
    return 'unknown';
  });

const shoot = async (name) => {
  const p = join(OUT_DIR, `${name}.png`);
  await page.screenshot({ path: p });
  result.screenshots.push(p);
  return p;
};

const result = { url: URL, screenshots: [], state: null, openedSubject: null };

try {
  await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 30000 });

  // Settle: wait until the app leaves the transient "Loading…" splash. Anything
  // else — studio, either error screen, or the idle splash — is a terminal state.
  await page
    .waitForFunction(
      () => {
        const has = (sel) => !!document.querySelector(sel);
        const text = document.body.innerText ?? '';
        return (
          has('[data-testid="studio-header"]') ||
          has('[data-testid="shell-frame"]') ||
          /Invalid backend selection|Failed to load (demo|project)|Open a local project folder/.test(text)
        );
      },
      { timeout: SETTLE_MS },
    )
    .catch(() => {});

  result.state = await classify();
  await shoot('loaded');

  if (result.state === 'studio') {
    // Open a subject so the canvas actually mounts. Subjects are the catalog
    // spine's schema entries (`catalog-item`) — NOT the `file-rail-item` list,
    // which only opens source text and leaves the canvas pane empty.
    const rail = page.locator('[data-testid="catalog-item"]');
    if (await rail.first().isVisible().catch(() => false)) {
      const target = SUBJECT ? page.locator('[data-testid="catalog-item"]', { hasText: SUBJECT }).first() : rail.first();
      result.openedSubject = SUBJECT ?? '(first)';
      await target.click().catch((e) => pageErrors.push('subject click failed: ' + e));
      await page.waitForSelector('[data-testid="canvas-kernel"]', { timeout: 10000 }).catch(() => {});
      await page.waitForTimeout(1200); // let ELK finish and RF paint
      await shoot('graph');
    }

    // React Flow renders DOM, so the graph is directly countable — no bitmap probing.
    Object.assign(result, await page.evaluate(() => ({
      canvasMounted: !!document.querySelector('[data-testid="canvas-kernel"]'),
      skin: document.querySelector('[data-testid="canvas-kernel"]')?.getAttribute('data-skin') ?? null,
      nodeCount: document.querySelectorAll('.react-flow__node').length,
      edgeCount: document.querySelectorAll('.react-flow__edge').length,
      unknownSkin: !!document.querySelector('[data-testid="unknown-skin"]'),
      noTabHint: !!document.querySelector('[data-testid="shell-no-tab"]'),
    })));

    // Click the first node and read back what the shell put in the side panel.
    const first = page.locator('.react-flow__node').first();
    if (await first.isVisible().catch(() => false)) {
      result.clickedNode = await first.getAttribute('data-id');
      await first.click().catch((e) => pageErrors.push('node click failed: ' + e));
      await page.waitForTimeout(500);
      await shoot('node-selected');
      result.selectedInDom = await page.evaluate(
        () => document.querySelectorAll('.react-flow__node.selected').length,
      );
    }
  }
} finally {
  await browser.close();
}

const errorConsole = consoleMsgs.filter((m) => m.type === 'error');
const ok = result.state === 'studio' && pageErrors.length === 0 && errorConsole.length === 0;

console.log(
  JSON.stringify(
    { ...result, ok, pageErrors, failedRequests: failedRequests.slice(0, 10), consoleErrorCount: errorConsole.length, consoleErrors: errorConsole.slice(0, 20) },
    null,
    2,
  ),
);

if (!ok) process.exitCode = 1;
