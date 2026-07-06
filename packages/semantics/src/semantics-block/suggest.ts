// Nearest-match over a closed vocabulary (Levenshtein ≤ maxDistance). Used by the
// TTR-SEM-200/201/202 diagnostics to offer "did you mean …" on an unknown key /
// role / kind. Kept local to the semantics-block module — no TS-side edit-distance
// helper existed to reuse (the lint "suggestion" fixes are a different concept).

/** Levenshtein edit distance between `a` and `b` (classic DP). */
export function editDistance(a: string, b: string): number {
  const m = a.length;
  const n = b.length;
  if (m === 0) return n;
  if (n === 0) return m;
  let prev = Array.from({ length: n + 1 }, (_, j) => j);
  let curr = new Array<number>(n + 1);
  for (let i = 1; i <= m; i++) {
    curr[0] = i;
    for (let j = 1; j <= n; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      curr[j] = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost);
    }
    [prev, curr] = [curr, prev];
  }
  return prev[n];
}

/**
 * The single closest candidate within `maxDistance` (default 2), or undefined if
 * none is close enough. Ties break to the first candidate in `candidates` order
 * (the vocabulary's declaration order), keeping suggestions deterministic.
 */
export function nearestMatch(
  input: string,
  candidates: ReadonlyArray<string>,
  maxDistance = 2,
): string | undefined {
  let best: string | undefined;
  let bestDist = maxDistance + 1;
  for (const c of candidates) {
    const d = editDistance(input, c);
    if (d < bestDist) {
      best = c;
      bestDist = d;
    }
  }
  return bestDist <= maxDistance ? best : undefined;
}
