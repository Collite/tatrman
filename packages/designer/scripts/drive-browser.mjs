// Headless browser driver for the Designer — lets us load the app, capture
// console/page errors, and screenshot the actual Cytoscape rendering (which
// draws to <canvas>, so screenshots are the only way to "see" the graph).
//
// Usage:
//   node packages/designer/scripts/drive-browser.mjs [url] [graphName]
// Env:
//   PW_EXECUTABLE  override the chromium binary (default: cached chrome 147 shell)
//   OUT_DIR        screenshot output dir (default: /tmp/designer-shots)
//
// Requires the dev server already running (pnpm --filter @modeler/designer dev).

import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

const URL = process.argv[2] ?? 'http://localhost:5173/?demo=v1.1-mini';
const GRAPH = process.argv[3] ?? null; // graph name to open; null = first in picker
const OUT_DIR = process.env.OUT_DIR ?? '/tmp/designer-shots';
const EXECUTABLE =
  process.env.PW_EXECUTABLE ??
  join(
    homedir(),
    'Library/Caches/ms-playwright/chromium_headless_shell-1217',
    'chrome-headless-shell-mac-arm64/chrome-headless-shell',
  );

mkdirSync(OUT_DIR, { recursive: true });

const consoleMsgs = [];
const pageErrors = [];

const browser = await chromium.launch({ executablePath: EXECUTABLE, headless: true });
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });

page.on('console', (m) => consoleMsgs.push({ type: m.type(), text: m.text() }));
page.on('pageerror', (e) => pageErrors.push(String(e)));

const result = { url: URL, screenshots: [], errorBanner: null, clickedGraph: null };

try {
  await page.goto(URL, { waitUntil: 'networkidle', timeout: 30000 });

  // Wait for the app to settle into either the graph picker, a rendered graph,
  // or an error banner — whichever comes first.
  await page
    .waitForFunction(
      () =>
        document.querySelector('.bg-red-100.text-red-700') ||
        [...document.querySelectorAll('h2')].some((h) => h.textContent?.includes('Select a Graph')) ||
        document.querySelector('canvas'),
      { timeout: 20000 },
    )
    .catch(() => {});

  const shot1 = join(OUT_DIR, 'loaded.png');
  await page.screenshot({ path: shot1, fullPage: false });
  result.screenshots.push(shot1);

  // If the picker is up, open a graph so the canvas actually renders.
  const pickerVisible = await page
    .locator('h2', { hasText: 'Select a Graph' })
    .isVisible()
    .catch(() => false);

  if (pickerVisible) {
    const graphBtn = GRAPH
      ? page.locator('button', { hasText: GRAPH }).first()
      : page.locator('h2:has-text("Select a Graph")').locator('xpath=ancestor::div[1]//button').first();
    result.clickedGraph = GRAPH ?? '(first)';
    await graphBtn.click().catch((e) => pageErrors.push('graph click failed: ' + e));
    // Give Cytoscape time to lay out + paint.
    await page.waitForSelector('canvas', { timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(1500);
    const shot2 = join(OUT_DIR, 'graph.png');
    await page.screenshot({ path: shot2, fullPage: false });
    result.screenshots.push(shot2);
  }

  result.errorBanner = await page
    .locator('.bg-red-100.text-red-700')
    .first()
    .textContent()
    .catch(() => null);

  result.canvasCount = await page.locator('canvas').count();

  // --- Issue 1: edges present? (read via the dev cy handle) ---
  result.edgeCount = await page.evaluate(() => window.__cy?.edges().length ?? -1);
  result.nodeCount = await page.evaluate(() => window.__cy?.nodes().length ?? -1);

  // --- Issue 3: clicking a node populates the Inspector ---
  const firstNodePos = await page.evaluate(() => {
    const n = window.__cy?.nodes()[0];
    if (!n) return null;
    const p = n.renderedPosition();
    return { x: p.x, y: p.y, qname: n.data('qname') };
  });
  if (firstNodePos) {
    result.clickedNode = firstNodePos.qname;
    await page.mouse.click(firstNodePos.x, firstNodePos.y);
    await page.waitForTimeout(600);
    const shot3 = join(OUT_DIR, 'node-selected.png');
    await page.screenshot({ path: shot3 });
    result.screenshots.push(shot3);
    // Inspector panel text after selection (heading is "INSPECTOR").
    result.inspectorText = await page
      .evaluate(() => {
        const aside = document.querySelector('aside');
        return aside?.textContent?.replace(/\s+/g, ' ').trim().slice(0, 240) ?? null;
      })
      .catch(() => null);
  }

  // --- Issue 2: drag a node, click empty canvas, verify it stays moved ---
  const dragResult = await page.evaluate(async () => {
    const cy = window.__cy;
    if (!cy) return null;
    const n = cy.nodes()[0];
    const before = { ...n.position() };
    n.position({ x: before.x + 160, y: before.y + 120 });
    const moved = { ...n.position() };
    return { before, moved };
  });
  if (dragResult) {
    // Click empty canvas (corner) to deselect — this is what used to snap it back.
    await page.mouse.click(40, 750);
    await page.waitForTimeout(400);
    const after = await page.evaluate(() => {
      const n = window.__cy?.nodes()[0];
      return n ? { ...n.position() } : null;
    });
    result.drag = { ...dragResult, afterCanvasClick: after };
    result.dragStuck =
      !!after && Math.abs(after.x - dragResult.moved.x) < 1 && Math.abs(after.y - dragResult.moved.y) < 1;
  }
} finally {
  await browser.close();
}

const errorConsole = consoleMsgs.filter((m) => m.type === 'error');
console.log(
  JSON.stringify(
    {
      ...result,
      pageErrors,
      consoleErrorCount: errorConsole.length,
      consoleErrors: errorConsole.slice(0, 20),
    },
    null,
    2,
  ),
);
