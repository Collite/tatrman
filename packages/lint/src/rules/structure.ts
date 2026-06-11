import { DiagnosticCode } from '@modeler/parser';
import type { Rule } from '../rule.js';

// Ported from Validator.validateDocument (the structural + reference-within-def
// checks). Each condition is its own rule id; several share the
// `required-property-missing` code (design §5.5).

const entityNoAttributes: Rule = {
  id: 'entity-no-attributes',
  code: DiagnosticCode.RequiredPropertyMissing,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'An entity must declare at least one attribute.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind === 'entity' && (!def.attributes || def.attributes.length === 0)) {
        ctx.report({ source: def.source, message: 'Entity must have at least one attribute' });
      }
    }
  },
};

const tableNoColumns: Rule = {
  id: 'table-no-columns',
  code: DiagnosticCode.RequiredPropertyMissing,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'A table must declare at least one column.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind === 'table' && (!def.columns || def.columns.length === 0)) {
        ctx.report({ source: def.source, message: 'Table must have at least one column' });
      }
    }
  },
};

const columnMissingType: Rule = {
  id: 'column-missing-type',
  code: DiagnosticCode.RequiredPropertyMissing,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'A column must declare a type.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind === 'column' && !def.type) {
        ctx.report({ source: def.source, message: 'Column must have a type' });
      }
    }
  },
};

const attributeMissingType: Rule = {
  id: 'attribute-missing-type',
  code: DiagnosticCode.RequiredPropertyMissing,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'An attribute must declare a type.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind === 'attribute' && !def.type) {
        ctx.report({ source: def.source, message: 'Attribute must have a type' });
      }
    }
  },
};

const missingDescription: Rule = {
  id: 'missing-description',
  code: DiagnosticCode.RequiredPropertyMissing,
  category: 'style',
  scope: 'document',
  // Natural severity is warning; the `recommended` preset sets it to `off`
  // (design §6.3). The old `requireDescriptions` flag is gone — config decides.
  defaultSeverity: 'warning',
  docs: 'Definitions should carry a description.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (!('description' in def && def.description)) {
        ctx.report({ source: def.source, message: 'Definition should have a description' });
      }
    }
  },
};

const entityAttributeNotFound: Rule = {
  id: 'entity-attribute-not-found',
  code: DiagnosticCode.EntityAttributeNotFound,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'An entity’s nameAttribute / codeAttribute must name an existing attribute.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'entity' || !def.attributes) continue;
      if (def.nameAttribute) {
        const last = def.nameAttribute.parts[def.nameAttribute.parts.length - 1];
        if (!def.attributes.some((a) => a.name === last)) {
          ctx.report({
            source: def.nameAttribute.source,
            message: `nameAttribute '${def.nameAttribute.path}' not found on entity '${def.name}'`,
          });
        }
      }
      if (def.codeAttribute) {
        const last = def.codeAttribute.parts[def.codeAttribute.parts.length - 1];
        if (!def.attributes.some((a) => a.name === last)) {
          ctx.report({
            source: def.codeAttribute.source,
            message: `codeAttribute '${def.codeAttribute.path}' not found on entity '${def.name}'`,
          });
        }
      }
    }
  },
};

const primaryKeyColumnNotFound: Rule = {
  id: 'primary-key-column-not-found',
  code: DiagnosticCode.PrimaryKeyColumnNotFound,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'Every primaryKey column must exist on the table.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'table' || !def.primaryKey) continue;
      for (const pkCol of def.primaryKey) {
        if (!def.columns?.some((c) => c.name === pkCol)) {
          ctx.report({
            source: def.source,
            message: `Primary key column '${pkCol}' not found on table '${def.name}'`,
          });
        }
      }
    }
  },
};

export const STRUCTURE_RULES: Rule[] = [
  entityNoAttributes,
  tableNoColumns,
  columnMissingType,
  attributeMissingType,
  missingDescription,
  entityAttributeNotFound,
  primaryKeyColumnNotFound,
];
