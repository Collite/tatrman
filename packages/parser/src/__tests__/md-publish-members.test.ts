// SPDX-License-Identifier: Apache-2.0
// MD dot-path — TTR-M `publish: members` domain clause (contracts §1.4).
// The typed `publishMembers` flag surfaces on `MdDomainDef`; default (clause absent) = undefined
// (not published). Companion Kotlin grammar-level coverage: ttr-parser PublishMembersParseSpec.
//
// TDD: RED until S0-B adds `PUBLISH`/`publishProperty` to TTR.g4 and fills the flag in walker.ts.
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type { MdDomainDef, Definition } from '../ast.js';

const MODEL = `model md
def domain CustCode { type: string, publish: members }
def domain Money { type: decimal }
`;

const { ast, errors } = parseString(MODEL, 'file:///publish-members.ttrm');
const defs: Definition[] = ast?.definitions ?? [];
const byName = (name: string) => defs.find((d) => d.name === name) as MdDomainDef | undefined;

describe('MD `publish: members` domain clause', () => {
  it('parses with no parse errors', () => {
    expect(errors.filter((e) => e.code === 'ttr/parse-error')).toEqual([]);
  });

  it('surfaces publishMembers=true on the published domain', () => {
    expect(byName('CustCode')?.publishMembers).toBe(true);
  });

  it('leaves publishMembers falsy on an unpublished domain (default: not published)', () => {
    expect(byName('Money')?.publishMembers ?? false).toBe(false);
  });
});
