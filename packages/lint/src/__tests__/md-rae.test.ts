import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@tatrman/parser';
import { lintProj, lintDocInProject, type ProjectFile } from './helpers.js';

// Phase 4 (4C) — end-to-end MD models derived from the RAE cost-allocation
// examples (design §12.4). Each clean model validates with no md/* errors; a
// seeded variant raises the expected code. Runs both document-scoped (logical)
// and project-scoped (binding) rules across the project.

function diags(files: ProjectFile[]) {
  const out = [];
  for (const f of files) out.push(...lintDocInProject(files, f.uri));
  out.push(...[...lintProj(files).values()].flat());
  return out;
}
/** md/* diagnostics at error severity (warnings like cross-file table-map-no-binding are allowed). */
function mdErrors(files: ProjectFile[]): string[] {
  return diags(files)
    .filter((d) => d.code.startsWith('md/') && d.severity === 'error')
    .map((d) => d.code);
}
function mdCodes(files: ProjectFile[]): string[] {
  return diags(files)
    .filter((d) => d.code.startsWith('md/'))
    .map((d) => d.code);
}

// ---- 4C1: costCenterTransactions (wide) ----
const CCT_LOGICAL = `model md
def domain AccountCode { type: string }
def domain CostCenterCode { type: string }
def domain Day { type: date }
def domain Money { type: decimal }
def dimension Account { key: code, attributes: [def attribute code { domain: md.AccountCode }] }
def dimension CostCenter { key: code, attributes: [def attribute code { domain: md.CostCenterCode }] }
def dimension Time { key: day, attributes: [def attribute day { domain: md.Day }] }
def measure amount { domain: md.Money, aggregation: sum }
def cubelet costCenterTransactions {
  grain: [Account.code, CostCenter.code, Time.day],
  measures: [amount]
}`;
const CCT_WIDE = `model binding
def md2db_cubelet cct_w {
  cubelet: md.costCenterTransactions, target: db.dbo.CCT, shape: wide,
  attributes: { Account.code: ACC, CostCenter.code: CC, Time.day: DT },
  measures: { amount: AMT }
}`;

// ---- 4C2: otherDrivers (long) ----
const DRV_LOGICAL = `model md
def domain CostCenterCode { type: string }
def domain Day { type: date }
def domain Money { type: decimal }
def dimension CostCenter { key: code, attributes: [def attribute code { domain: md.CostCenterCode }] }
def dimension Time { key: day, attributes: [def attribute day { domain: md.Day }] }
def measure fte { domain: md.Money, aggregation: sum }
def measure m2 { domain: md.Money, aggregation: sum }
def cubelet otherDrivers { grain: [CostCenter.code, Time.day], measures: [fte, m2] }`;
const DRV_LONG = `model binding
def md2db_cubelet od_long {
  cubelet: md.otherDrivers, target: db.dbo.DRV,
  shape: { long: { codeColumn: DRIVER, valueColumn: VAL } },
  attributes: { CostCenter.code: CC, Time.day: DT },
  measures: { fte: { code: FTE }, m2: { code: M2 } }
}`;

// ---- 4C3: costCenterM2 (map-mediated store + invalidate journaling) ----
const M2_LOGICAL = `model md
def domain BuildingCode { type: string }
def domain CostCenterCode { type: string }
def domain Day { type: date }
def domain Money { type: decimal }
def dimension Building { key: code, attributes: [def attribute code { domain: md.BuildingCode }] }
def dimension Time { key: day, attributes: [def attribute day { domain: md.Day }] }
def map cc_to_building { from: md.CostCenterCode, to: md.BuildingCode, cardinality: { from: "N", to: "1" } }
def measure m2 { domain: md.Money, aggregation: sum }
def cubelet costCenterM2 { grain: [Building.code, Time.day], measures: [m2] }`;
const M2_BINDING = `model binding
def md2db_map cc_building { map: md.cc_to_building, target: db.dbo.CCB, columns: { CostCenterCode: CC, BuildingCode: BLD } }
def md2db_cubelet ccm2 {
  cubelet: md.costCenterM2, target: db.dbo.CCM2, shape: wide,
  attributes: { Building.code: { via: md.cc_to_building, from: { table: db.dbo.CCB, column: BLD } }, Time.day: DT },
  measures: { m2: M2 },
  journaling: { invalidate: { validColumn: VALID_TO } }
}`;

// ---- 4C4: Calendar (calc maps + hierarchy inference) ----
// Hierarchy steps realised by the catalog: Day→Month (monthOfDate, extraction)
// and Month→Quarter (quarterOfMonth, rollup). Year is a separate extraction
// (yearOfDate, Day→Year) — not a hierarchy step, since the v1 floor has no
// Quarter→Year rollup.
const CALENDAR = `model md
def domain Day { type: date }
def domain Month { type: int, kind: calc, restrict: { range: 1..12 } }
def domain Quarter { type: int, kind: calc, restrict: { range: 1..4 } }
def domain Year { type: int, kind: calc }
def dimension Time {
  key: day,
  attributes: [
    def attribute day { domain: md.Day },
    def attribute month { domain: md.Month },
    def attribute quarter { domain: md.Quarter },
    def attribute year { domain: md.Year }
  ],
  hierarchies: [calendar]
}
def map day_to_month { from: md.Day, to: md.Month, calc: monthOfDate }
def map month_to_qtr { from: md.Month, to: md.Quarter, calc: quarterOfMonth }
def map day_to_year { from: md.Day, to: md.Year, calc: yearOfDate }
def hierarchy calendar { dimension: md.Time, levels: [day, month, quarter] }`;

const files = (logical: string, binding?: string): ProjectFile[] =>
  binding
    ? [{ uri: 'file:///m.ttrm', src: logical }, { uri: 'file:///b.ttrm', src: binding }]
    : [{ uri: 'file:///m.ttrm', src: logical }];

describe('Phase 4 (4C) — RAE end-to-end MD fixtures', () => {
  it('costCenterTransactions (wide) validates clean', () => {
    expect(mdErrors(files(CCT_LOGICAL, CCT_WIDE))).toEqual([]);
  });

  it('otherDrivers (long) validates clean', () => {
    expect(mdErrors(files(DRV_LOGICAL, DRV_LONG))).toEqual([]);
  });

  it('costCenterM2 (map-mediated + invalidate journaling) validates clean', () => {
    expect(mdErrors(files(M2_LOGICAL, M2_BINDING))).toEqual([]);
  });

  it('calendar (calc-map hierarchy) validates clean — no errors or warnings', () => {
    // Pure logical model: hierarchy inference + catalog type-checks, no bindings.
    expect(mdCodes(files(CALENDAR))).toEqual([]);
  });

  it('seeded: a calendar missing the Month→Quarter map → md/no-hierarchy-step', () => {
    const broken = CALENDAR.replace(
      'def map month_to_qtr { from: md.Month, to: md.Quarter, calc: quarterOfMonth }\n',
      ''
    );
    expect(mdCodes(files(broken))).toContain(DiagnosticCode.MdNoHierarchyStep);
  });

  it('seeded: a wide binding missing a grain attribute → md/cubelet-grain-uncovered', () => {
    const broken = CCT_WIDE.replace(', Time.day: DT', '');
    expect(mdCodes(files(CCT_LOGICAL, broken))).toContain(DiagnosticCode.MdCubeletGrainUncovered);
  });
});
