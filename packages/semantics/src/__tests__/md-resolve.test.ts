import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { resolveMdRef, underlyingDomainOf } from '../md-resolve.js';

const MD = `model md
def domain CustomerCode { type: string }
def domain CustomerId { type: int }
def dimension Customer {
  key: code,
  attributes: [
    def attribute code { domain: md.CustomerCode },
    def attribute id { domain: md.CustomerId }
  ]
}
def map code_to_id { from: Customer.code, to: Customer.id }
`;

function table(): ProjectSymbolTable {
  const ast = parseString(MD, 'file:///m.ttrm').ast!;
  const t = new ProjectSymbolTable();
  t.upsertDocument('file:///m.ttrm', ast, 'md', '');
  return t;
}

describe('resolveMdRef — role-aware schema insertion', () => {
  const t = table();
  it('resolves a domain ref omitting the schema segment', () => {
    expect(resolveMdRef(t, 'md.CustomerCode', 'domain')?.qname).toBe('md.domain.CustomerCode');
  });
  it('resolves a dotted grain ref to a dimension attribute', () => {
    expect(resolveMdRef(t, 'Customer.code', 'grain')?.qname).toBe('md.dimension.Customer.code');
  });
  it('returns undefined for a dangling ref', () => {
    expect(resolveMdRef(t, 'md.Nope', 'domain')).toBeUndefined();
  });
});

describe('underlyingDomainOf — attribute→domain map sugar (design §5.3)', () => {
  const t = table();
  it('a domain ref resolves to itself', () => {
    expect(underlyingDomainOf(t, 'md.CustomerCode')?.qname).toBe('md.domain.CustomerCode');
  });
  it('an attribute ref (the sugar) lowers to its underlying domain', () => {
    // `map code_to_id { from: Customer.code }` — `Customer.code` lowers to its domain.
    expect(underlyingDomainOf(t, 'Customer.code')?.qname).toBe('md.domain.CustomerCode');
    expect(underlyingDomainOf(t, 'Customer.id')?.qname).toBe('md.domain.CustomerId');
  });
});
