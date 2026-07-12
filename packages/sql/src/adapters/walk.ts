// SPDX-License-Identifier: Apache-2.0
import { ErrorNode, ParserRuleContext, TerminalNode, type ParseTree, type Token } from 'antlr4ng';
import type { Span, SqlParseErrorSpan } from '../refmodel.js';

/** Concrete constructor of a generated ANTLR context class. */
export type Ctor<T> = new (...args: never[]) => T;

export function spanOfCtx(ctx: ParserRuleContext): Span {
  const start = ctx.start;
  if (!start) return { offset: 0, length: 0, line: 1, column: 0 };
  const stop = ctx.stop ?? start;
  return { offset: start.start, length: stop.stop - start.start + 1, line: start.line, column: start.column };
}

export function spanOfToken(t: Token): Span {
  return { offset: t.start, length: t.stop - t.start + 1, line: t.line, column: t.column };
}

/** All descendant contexts of a given type (self included), pre-order. */
export function descendants<T extends ParserRuleContext>(root: ParseTree, Ctor: Ctor<T>): T[] {
  const out: T[] = [];
  const visit = (n: ParseTree): void => {
    if (n instanceof (Ctor as Ctor<ParserRuleContext>)) out.push(n as unknown as T);
    if (n instanceof ParserRuleContext) {
      for (let i = 0; i < n.getChildCount(); i++) visit(n.getChild(i)!);
    }
  };
  visit(root);
  return out;
}

/** Direct children of a given type. */
export function directChildren<T extends ParserRuleContext>(node: ParserRuleContext, Ctor: Ctor<T>): T[] {
  const out: T[] = [];
  for (let i = 0; i < node.getChildCount(); i++) {
    const c = node.getChild(i);
    if (c instanceof (Ctor as Ctor<ParserRuleContext>)) out.push(c as unknown as T);
  }
  return out;
}

export function firstChild<T extends ParserRuleContext>(node: ParserRuleContext, Ctor: Ctor<T>): T | undefined {
  return directChildren(node, Ctor)[0];
}

/** All terminal-token leaves under a node, in order. */
export function terminals(root: ParseTree): TerminalNode[] {
  const out: TerminalNode[] = [];
  const visit = (n: ParseTree): void => {
    if (n instanceof TerminalNode) out.push(n);
    else if (n instanceof ParserRuleContext) {
      for (let i = 0; i < n.getChildCount(); i++) visit(n.getChild(i)!);
    }
  };
  visit(root);
  return out;
}

/** True if any ancestor of `node` is one of the given types. */
export function hasAncestor(node: ParserRuleContext, Ctors: Ctor<ParserRuleContext>[]): boolean {
  let p = node.parent;
  while (p) {
    if (Ctors.some((C) => p instanceof C)) return true;
    p = p.parent;
  }
  return false;
}

/** Strip a single matched pair of SQL identifier delimiters: `[..]`, `"..."`, or backticks. */
export function stripDelims(s: string): string {
  if (s.length >= 2) {
    const a = s[0];
    const b = s[s.length - 1];
    if ((a === '[' && b === ']') || (a === '"' && b === '"') || (a === '`' && b === '`')) {
      return s.slice(1, -1);
    }
  }
  return s;
}

/** Error-recovery nodes the parser inserted (best-effort; DESIGN §12.3). */
export function errorNodes(root: ParseTree): SqlParseErrorSpan[] {
  const out: SqlParseErrorSpan[] = [];
  const visit = (n: ParseTree): void => {
    if (n instanceof ErrorNode) {
      const t = n.symbol;
      out.push({
        message: `unexpected '${t.text ?? ''}'`,
        span: { offset: t.start, length: Math.max(0, t.stop - t.start + 1), line: t.line, column: t.column },
      });
    } else if (n instanceof ParserRuleContext) {
      for (let i = 0; i < n.getChildCount(); i++) visit(n.getChild(i)!);
    }
  };
  visit(root);
  return out;
}

/** A native SQL param sigil → its bare name, or null if the text isn't a param. */
export function paramName(text: string): string | null {
  if (/^@[A-Za-z_]\w*$/.test(text)) return text.slice(1); // tsql @p
  if (/^\$\d+$/.test(text)) return text.slice(1); // postgres positional $1
  if (/^:[A-Za-z_]\w*$/.test(text)) return text.slice(1); // postgres named :name
  return null;
}
