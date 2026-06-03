export interface RecoveryFixture {
  name: string;
  input: string;
  description: string;
  expectedRecoveredDefs: number;
  expectErrors: boolean;
}

export const RECOVERY_FIXTURES: RecoveryFixture[] = [
  {
    name: 'missing-closing-brace',
    input: `def entity artikl {
  description: "Test"
`,
    description: 'A def block missing its closing brace',
    expectedRecoveredDefs: 1,
    expectErrors: true,
  },
  {
    name: 'unterminated-string',
    input: `def entity foo {
  description: "Test
}`,
    description: 'String literal not terminated before newline',
    expectedRecoveredDefs: 1,
    expectErrors: true,
  },
  {
    name: 'missing-def-keyword',
    input: `entity artikl {
  description: "Test"
}`,
    description: 'def keyword omitted',
    expectedRecoveredDefs: 0,
    expectErrors: true,
  },
  {
    name: 'unknown-property-name',
    input: `def entity foo {
  descriptin: "Test"
}`,
    description: 'Typo in property name (descriptin is not a valid entity property)',
    expectedRecoveredDefs: 1,
    expectErrors: true,
  },
  {
    name: 'malformed-dotted-id',
    input: `def entity foo {
  nameAttribute: er..entity.bar
}`,
    description: 'Double dot in dotted identifier',
    expectedRecoveredDefs: 1,
    expectErrors: true,
  },
  {
    name: 'def-entity-no-name',
    input: `def entity {
  description: "Test"
}`,
    description: 'def entity with no name — recovery strategy synthesizes',
    expectedRecoveredDefs: 1,
    expectErrors: true,
  },
  {
    name: 'truncated-inline-column',
    input: `def table T { columns: [ def column `,
    description: 'truncated inline column — recovery strategy backs out',
    expectedRecoveredDefs: 1,
    expectErrors: true,
  },
];