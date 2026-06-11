import { CommonTokenStream, Token } from 'antlr4ng';
import { TTRLexer } from '../generated/TTRLexer.js';
import type { Document, SourceLocation } from '../ast.js';
import type { Trivia, TriviaKind } from './trivia.js';

/**
 * A node enriched in place with leading/trailing comment trivia. Every AST node
 * that carries a `source` is structurally a `TriviaCarrier`.
 */
interface TriviaCarrier {
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

/**
 * Attaches leading/trailing comment trivia to AST nodes in-place. Pure w.r.t.
 * the semantic AST shape — only the optional trivia fields are populated.
 *
 * Comments live on the hidden channel (`TTR.g4`: `LINE_COMMENT`/`BLOCK_COMMENT
 * -> channel(HIDDEN)`); whitespace is still `-> skip` and never reaches here.
 *
 * Attachment rule (one owner per comment, never duplicated):
 *  - trailing: a comment on the same line as, and to the right of, a node's last
 *    significant token attaches to the innermost node ending at that token;
 *  - leading: otherwise it attaches to the outermost node whose first
 *    significant token follows the comment;
 *  - fallback (e.g. a comment before a closing `}` or at EOF): the innermost
 *    node that contains the comment, as trailing trivia.
 */
export function attachTrivia(ast: Document, tokenStream: CommonTokenStream): void {
  tokenStream.fill();
  const tokens = tokenStream.getTokens();

  const comments = tokens.filter(
    (t) => t.type === TTRLexer.LINE_COMMENT || t.type === TTRLexer.BLOCK_COMMENT
  );
  if (comments.length === 0) return;

  const significant = tokens
    .filter((t) => t.channel === Token.DEFAULT_CHANNEL && t.type !== Token.EOF)
    .sort((a, b) => a.start - b.start);

  const nodes: TriviaCarrier[] = [];
  collectNodes(ast, nodes);

  // innermost node starting at a given offset (smallest span wins): a leading
  // comment belongs to the most specific node it precedes, never the enclosing
  // container (which can share the same start offset since comments are hidden).
  const startMap = new Map<number, TriviaCarrier>();
  // innermost node ending at a given offset (smallest span wins)
  const endMap = new Map<number, TriviaCarrier>();
  for (const n of nodes) {
    const { offsetStart, offsetEnd } = n.source;
    const span = offsetEnd - offsetStart;
    const curStart = startMap.get(offsetStart);
    if (!curStart || span < curStart.source.offsetEnd - curStart.source.offsetStart) {
      startMap.set(offsetStart, n);
    }
    const curEnd = endMap.get(offsetEnd);
    if (!curEnd || span < curEnd.source.offsetEnd - curEnd.source.offsetStart) {
      endMap.set(offsetEnd, n);
    }
  }

  for (const comment of comments) {
    const trivia = toTrivia(comment, ast.source.file);
    const prevSig = lastBefore(significant, comment.start);
    const nextSig = firstAfter(significant, comment.stop);

    // trailing: comment trails a node on the same physical line
    if (prevSig && prevSig.line === comment.line) {
      const owner = endMap.get(prevSig.stop + 1);
      if (owner) {
        (owner.trailingTrivia ??= []).push(trivia);
        continue;
      }
    }

    // leading: comment precedes a node that starts at the next significant token
    if (nextSig) {
      const owner = startMap.get(nextSig.start);
      if (owner) {
        (owner.leadingTrivia ??= []).push(trivia);
        continue;
      }
    }

    // fallback: dangling comment (before a closer, or at EOF) → innermost container
    const container = innermostContaining(nodes, comment.start, comment.stop + 1) ?? (ast as TriviaCarrier);
    (container.trailingTrivia ??= []).push(trivia);
  }
}

function toTrivia(token: Token, file: string): Trivia {
  const kind: TriviaKind = token.type === TTRLexer.BLOCK_COMMENT ? 'block-comment' : 'line-comment';
  const text = token.text ?? '';
  const lines = text.split('\n');
  const endLine = token.line + lines.length - 1;
  const endColumn =
    lines.length === 1 ? token.column + text.length : lines[lines.length - 1].length;
  const source: SourceLocation = {
    file,
    line: token.line,
    column: token.column,
    endLine,
    endColumn,
    offsetStart: token.start,
    offsetEnd: token.stop + 1,
  };
  return { kind, text, source };
}

function collectNodes(value: unknown, out: TriviaCarrier[]): void {
  if (value === null || typeof value !== 'object') return;
  if (Array.isArray(value)) {
    for (const item of value) collectNodes(item, out);
    return;
  }
  const obj = value as Record<string, unknown>;
  if (isNode(obj)) out.push(obj as unknown as TriviaCarrier);
  for (const key in obj) {
    if (key === 'source' || key === 'leadingTrivia' || key === 'trailingTrivia') continue;
    collectNodes(obj[key], out);
  }
}

function isNode(o: Record<string, unknown>): boolean {
  const s = o.source as Record<string, unknown> | undefined;
  return (
    !!s &&
    typeof s === 'object' &&
    typeof s.offsetStart === 'number' &&
    typeof s.offsetEnd === 'number'
  );
}

/** Last significant token whose stop precedes `offset` (the comment's start). */
function lastBefore(tokens: Token[], offset: number): Token | undefined {
  let found: Token | undefined;
  for (const t of tokens) {
    if (t.stop < offset) found = t;
    else break;
  }
  return found;
}

/** First significant token whose start follows `offset` (the comment's stop). */
function firstAfter(tokens: Token[], offset: number): Token | undefined {
  for (const t of tokens) {
    if (t.start > offset) return t;
  }
  return undefined;
}

function innermostContaining(
  nodes: TriviaCarrier[],
  start: number,
  end: number
): TriviaCarrier | undefined {
  let best: TriviaCarrier | undefined;
  for (const n of nodes) {
    const { offsetStart, offsetEnd } = n.source;
    if (offsetStart <= start && end <= offsetEnd) {
      if (!best || offsetEnd - offsetStart < best.source.offsetEnd - best.source.offsetStart) {
        best = n;
      }
    }
  }
  return best;
}
