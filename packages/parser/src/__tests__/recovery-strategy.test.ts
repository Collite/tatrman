// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { RecoveryReportingStrategy } from '../recovery.js';

function fakeRecognizer(line: number, column: number, text: string, start = 0, stop = text.length - 1) {
  return {
    getCurrentToken: () => ({ line, column, text, start, stop }),
  } as unknown as import('antlr4ng').Parser;
}

describe('RecoveryReportingStrategy', () => {
  it('recover() appends one event with the current token line/column', () => {
    const s = new RecoveryReportingStrategy();
    const proto = Object.getPrototypeOf(Object.getPrototypeOf(s));
    const superRecover = proto.recover;
    proto.recover = () => {};
    try {
      s.recover(fakeRecognizer(5, 12, '{'), {} as never);
    } finally {
      proto.recover = superRecover;
    }
    expect(s.recoveryEvents).toHaveLength(1);
    expect(s.recoveryEvents[0].line).toBe(5);
    expect(s.recoveryEvents[0].column).toBe(12);
    expect(s.recoveryEvents[0].description).toContain("'{'");
  });

  it('recoverInline() appends one event tagged "inline"', () => {
    const s = new RecoveryReportingStrategy();
    const proto = Object.getPrototypeOf(Object.getPrototypeOf(s));
    const superRI = proto.recoverInline;
    proto.recoverInline = () => ({ line: 0, column: 0, text: '', start: 0, stop: 0 } as never);
    try {
      s.recoverInline(fakeRecognizer(8, 3, 'foo'));
    } finally {
      proto.recoverInline = superRI;
    }
    expect(s.recoveryEvents).toHaveLength(1);
    expect(s.recoveryEvents[0].description).toMatch(/skipped token/);
  });
});