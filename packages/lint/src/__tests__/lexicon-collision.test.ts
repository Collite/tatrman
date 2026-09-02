// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@tatrman/parser';
import { lintProj, lintOne, type ProjectFile } from './helpers.js';
import type { LintDiagnostic } from '../rule.js';

// MH T1 (mention homonymy) — `ttr/lexicon-form-collides-with-name`, contracts §2.
// The corpus is hartland's own collision: `er.entity.store` owns "Prodejna"/"Stores"
// through its labels, and the Stores-CHANNEL term (pinned to `er.entity.store_sales`)
// claims the same bare words.

const CODE = DiagnosticCode.LexiconFormCollidesWithName;

const ER_PARTIES = `model er

def entity store {
    description: "Stores — the six TN stores (Stores-channel dim)."
    labelPlural: "Stores"
    roles: [dimension]
    displayLabel: { en: "Store", cs: "Prodejna" }
    attributes: [
        def attribute store_id   { type: text },
        def attribute store_name { type: text }
    ]
}
`;

const ER_SALES = `model er

def entity store_sales {
    description: "Stores-channel sales lines."
    labelPlural: "Store Sales"
    roles: [fact]
    displayLabel: { en: "Store Sales", cs: "Tržby z prodejen" }
    attributes: [
        def attribute ext_sales_price { type: decimal }
    ]
}
`;

const LEX_CS = `model lexicon locale cs

def term store_channel_cs {
    description: "prodejny synonyma"
    for: er.entity.store_sales
    forms: ["prodejna", "kamenná prodejna", "obchod"]
}
`;

const LEX_EN = `model lexicon locale en

def term store_channel {
    description: "Stores channel synonyms"
    for: er.entity.store_sales
    forms: ["stores", "brick and mortar"]
}
`;

const FILES: ProjectFile[] = [
  { uri: 'file:///er/parties.ttrm', src: ER_PARTIES },
  { uri: 'file:///er/sales.ttrm', src: ER_SALES },
  { uri: 'file:///lexicon/cs/channels.ttrm', src: LEX_CS },
  { uri: 'file:///lexicon/en/channels.ttrm', src: LEX_EN },
];

function collisions(files: ProjectFile[]): LintDiagnostic[] {
  const byUri = lintProj(files);
  return [...byUri.values()].flat().filter((d) => d.code === CODE);
}

function withFile(uri: string, src: string): ProjectFile[] {
  return FILES.map((f) => (f.uri === uri ? { uri, src } : f));
}

describe('MH T1 — lexicon-form-collides-with-name (contracts §2)', () => {
  it('A — the hartland pair: two collisions, one per locale, naming the label anchor', () => {
    const diags = collisions(FILES);
    expect(diags).toHaveLength(2);

    const cs = diags.find((d) => d.source.file === 'file:///lexicon/cs/channels.ttrm');
    expect(cs).toBeDefined();
    expect(cs!.severity).toBe('warning');
    expect(cs!.ruleId).toBe('lexicon-form-collides-with-name');
    expect(cs!.message).toBe(
      'term "store_channel_cs" form "prodejna" (for: er.entity.store_sales) collides with the ' +
        'displayLabel.cs anchor "Prodejna" of er.entity.store; the name-owner binds the bare form — ' +
        'drop it from the term, or keep it deliberately (an MH resolver decides by slot) and suppress this rule'
    );

    const en = diags.find((d) => d.source.file === 'file:///lexicon/en/channels.ttrm');
    expect(en).toBeDefined();
    expect(en!.message).toBe(
      'term "store_channel" form "stores" (for: er.entity.store_sales) collides with the ' +
        'labelPlural anchor "Stores" of er.entity.store; the name-owner binds the bare form — ' +
        'drop it from the term, or keep it deliberately (an MH resolver decides by slot) and suppress this rule'
    );
  });

  it('A — the diagnostic points at the term that declared the form', () => {
    const cs = collisions(FILES).find((d) => d.source.file === 'file:///lexicon/cs/channels.ttrm')!;
    // `def term store_channel_cs {` — line 3 of LEX_CS (1-indexed).
    expect(cs.source.line).toBe(3);
  });

  it('B — a term whose form is its OWN target’s label is redundant, not a collision', () => {
    const selfTarget = `model lexicon locale cs

def term store_cs {
    for: er.entity.store
    forms: ["prodejna"]
}
`;
    const files = withFile('file:///lexicon/cs/channels.ttrm', selfTarget).filter(
      (f) => f.uri !== 'file:///lexicon/en/channels.ttrm'
    );
    expect(collisions(files)).toHaveLength(0);
  });

  it('C — term vs term: two terms of different targets sharing a form, one diagnostic each', () => {
    const csWeb = `model lexicon locale cs

def term store_channel_cs {
    for: er.entity.store_sales
    forms: ["web"]
}
`;
    const enWeb = `model lexicon locale en

def term store_dim {
    for: er.entity.store
    forms: ["web"]
}
`;
    const files = [
      FILES[0],
      FILES[1],
      { uri: 'file:///lexicon/cs/channels.ttrm', src: csWeb },
      { uri: 'file:///lexicon/en/channels.ttrm', src: enWeb },
    ];
    const diags = collisions(files);
    expect(diags).toHaveLength(2);
    expect(diags.every((d) => d.message.includes('declared form anchor "web"'))).toBe(true);
    expect(diags.some((d) => d.message.includes('of er.entity.store;'))).toBe(true);
    expect(diags.some((d) => d.message.includes('of er.entity.store_sales;'))).toBe(true);
  });

  it('D — the suppression directive silences one term, and is not reported unused', () => {
    const suppressed = `model lexicon locale cs

def term store_channel_cs {
    for: er.entity.store_sales
    // ttr-disable-next-line lexicon-form-collides-with-name
    forms: ["prodejna", "kamenná prodejna", "obchod"]
}
`;
    const files = withFile('file:///lexicon/cs/channels.ttrm', suppressed);
    const byUri = lintProj(files);
    const all = [...byUri.values()].flat();
    const diags = all.filter((d) => d.code === CODE);
    expect(diags).toHaveLength(1);
    expect(diags[0].source.file).toBe('file:///lexicon/en/channels.ttrm');

    // The document pass must not call a project-rule directive unused (contracts §2.4).
    const docDiags = lintOne('file:///lexicon/cs/channels.ttrm', suppressed);
    expect(docDiags.map((d) => d.code)).not.toContain('ttrlint/unused-suppression');
  });

  it('D2 — a STALE project-rule suppression is reported once, by the pass that can tell', () => {
    // review-087 F4. `lintDocument` skips project-rule ids (it cannot know), and until now
    // `lintProject` reported no unused directives at all — so a directive whose collision had
    // been fixed sat there forever, claiming an exception nobody needed.
    const noCollision = `model lexicon locale cs

def term store_channel_cs {
    for: er.entity.store_sales
    // ttr-disable-next-line lexicon-form-collides-with-name
    forms: ["kamenná prodejna", "obchod"]
}
`;
    const files = withFile('file:///lexicon/cs/channels.ttrm', noCollision).filter(
      (f) => f.uri !== 'file:///lexicon/en/channels.ttrm'
    );
    const byUri = lintProj(files);
    const cs = byUri.get('file:///lexicon/cs/channels.ttrm') ?? [];

    expect(cs.filter((d) => d.code === CODE)).toHaveLength(0);
    const unused = cs.filter((d) => d.code === 'ttrlint/unused-suppression');
    expect(unused).toHaveLength(1);
    expect(unused[0].message).toContain('lexicon-form-collides-with-name');

    // …and a directive that DID suppress something stays silent (case D's other half).
    const suppressed = `model lexicon locale cs

def term store_channel_cs {
    for: er.entity.store_sales
    // ttr-disable-next-line lexicon-form-collides-with-name
    forms: ["prodejna", "kamenná prodejna"]
}
`;
    const used = lintProj(withFile('file:///lexicon/cs/channels.ttrm', suppressed))
      .get('file:///lexicon/cs/channels.ttrm')!
      .filter((d) => d.code === 'ttrlint/unused-suppression');
    expect(used).toHaveLength(0);
  });

  it('a directive naming a DOCUMENT rule is still the document pass\'s business, not this one', () => {
    // The project pass must not report a bare or document-scoped directive: its suppression
    // index is a different instance from `lintDocument`'s and would call a used one unused.
    const docRule = `model lexicon locale cs

// ttr-disable-next-line lexicon-duplicate-form
def term store_channel_cs { for: er.entity.store_sales, forms: ["kamenná prodejna"] }
`;
    const files = withFile('file:///lexicon/cs/channels.ttrm', docRule).filter(
      (f) => f.uri !== 'file:///lexicon/en/channels.ttrm'
    );
    const cs = lintProj(files).get('file:///lexicon/cs/channels.ttrm') ?? [];
    expect(cs.filter((d) => d.code === 'ttrlint/unused-suppression')).toHaveLength(0);
  });

  it('E — the key is the resolver’s FOLD, so diacritics do not hide a collision', () => {
    const erFold = `model er

def entity vyrobni_linka {
    displayLabel: { cs: "Výroba" }
    attributes: [def attribute id { type: int }]
}
`;
    const lexFold = `model lexicon locale cs

def term vyroba_alias {
    for: er.entity.store_sales
    forms: ["vyroba"]
}
`;
    const files = [
      { uri: 'file:///er/parties.ttrm', src: erFold },
      FILES[1],
      { uri: 'file:///lexicon/cs/channels.ttrm', src: lexFold },
    ];
    const diags = collisions(files);
    expect(diags).toHaveLength(1);
    expect(diags[0].message).toContain('displayLabel.cs anchor "Výroba" of er.entity.vyrobni_linka');
  });

  it('an attribute display label is an anchor too', () => {
    const erAttr = `model er

def entity item {
    attributes: [
        def attribute category { type: text, displayLabel: { cs: "Kategorie" } }
    ]
}
`;
    const lexAttr = `model lexicon locale cs

def term category_channel { for: er.entity.store_sales, forms: ["kategorie"] }
`;
    const diags = collisions([
      { uri: 'file:///er/parties.ttrm', src: erAttr },
      FILES[1],
      { uri: 'file:///lexicon/cs/channels.ttrm', src: lexAttr },
    ]);
    expect(diags).toHaveLength(1);
    expect(diags[0].message).toContain(
      'attribute.displayLabel.cs anchor "Kategorie" of er.entity.item.category'
    );
  });

  it('the entity local name is an anchor (the compiler’s METADATA layer has no name row — TS is the superset)', () => {
    const lexName = `model lexicon locale en

def term store_word { for: er.entity.store_sales, forms: ["store"] }
`;
    const diags = collisions([
      FILES[0],
      FILES[1],
      { uri: 'file:///lexicon/en/channels.ttrm', src: lexName },
    ]);
    // "store" folds onto BOTH the local name `store` and `displayLabel.en: "Store"`,
    // and each colliding anchor is its own diagnostic (contracts §2.3).
    expect(diags).toHaveLength(2);
    expect(diags.map((d) => d.message).join('\n')).toContain('name anchor "store" of er.entity.store');
    expect(diags.map((d) => d.message).join('\n')).toContain('displayLabel.en anchor "Store" of er.entity.store');
    // Name anchors sort before declared forms; ties break on the ref string.
    expect(diags[0].message).toContain('name anchor');
  });

  it('a clean estate raises nothing', () => {
    const clean = `model lexicon locale cs

def term store_channel_cs { for: er.entity.store_sales, forms: ["kamenná prodejna", "obchod"] }
`;
    const files = withFile('file:///lexicon/cs/channels.ttrm', clean).filter(
      (f) => f.uri !== 'file:///lexicon/en/channels.ttrm'
    );
    expect(collisions(files)).toHaveLength(0);
  });
});
