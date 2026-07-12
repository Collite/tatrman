// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { ROOT, current, enter, exit, popTo } from '../view-stack.js';

/** Two-level canvas navigation (T5.3.4): enter pushes, exit/breadcrumb pop. */
describe('view stack', () => {
  it('starts at the program (orchestration) canvas', () => {
    expect(current(ROOT)).toBe('program');
  });

  it('enter pushes a container; exit pops', () => {
    const inside = enter(ROOT, 'crunch');
    expect(current(inside)).toBe('crunch');
    expect(current(exit(inside))).toBe('program');
  });

  it('exit at root is a no-op', () => {
    expect(exit(ROOT)).toBe(ROOT);
  });

  it('breadcrumb pop-to depth', () => {
    const deep = enter(enter(ROOT, 'crunch'), 'crunch/frag');
    expect(deep.stack).toEqual(['program', 'crunch', 'crunch/frag']);
    expect(popTo(deep, 0).stack).toEqual(['program']);
    expect(popTo(deep, 1).stack).toEqual(['program', 'crunch']);
  });
});
