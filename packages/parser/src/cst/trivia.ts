// SPDX-License-Identifier: Apache-2.0
import type { SourceLocation } from '../ast.js';

/**
 * Kind of a trivia token. `whitespace`/`newline` are reserved for a future
 * whitespace-on-channel mode (§design 10.1); today only comments are captured
 * because `WS` stays `-> skip` while comments route to `channel(HIDDEN)`.
 */
export type TriviaKind = 'line-comment' | 'block-comment' | 'whitespace' | 'newline';

export interface Trivia {
  kind: TriviaKind;
  /** Raw token text, e.g. `"// ttr-disable-next-line foo"` or a block comment. */
  text: string;
  /** ANTLR-style span (1-indexed line, 0-indexed col, exclusive `offsetEnd`). */
  source: SourceLocation;
}
